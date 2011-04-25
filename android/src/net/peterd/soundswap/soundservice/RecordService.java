package net.peterd.soundswap.soundservice;

import static net.peterd.soundswap.Constants.TAG;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.Preferences;
import net.peterd.soundswap.R;
import net.peterd.soundswap.Util;
import net.peterd.soundswap.ui.RecordActivity;
import android.accounts.Account;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

public class RecordService extends Service implements LocationListener {

  private Preferences mPreferences;

  ProgressDialog mWaitingForLocationDialog;
  LocationManager mLocationManager;

  private Location mLatestLocation;

  private Recorder mRecorder;
  private File mAudioFile;
  private long mAudioFileStartTimeMs;

  private boolean mIsRecording = false;

  private final Executor mExecutor = Executors.newSingleThreadExecutor();

  @Override
  public void onCreate() {
    super.onCreate();
    mPreferences = new Preferences(this);
    if (getAccount() == null) {
      throw new IllegalStateException("Cannot start recording service " +
          "without a registered account.");
    }
    mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
  }

  @Override
  public IBinder onBind(Intent intent) {
    String bestProvider = mLocationManager.getBestProvider(
        Constants.LOCATION_CRITERIA, true);
    if (bestProvider == null) {
      throw new IllegalStateException("No location providers available.");
    }

    // Get the latest location
    Location location = mLocationManager.getLastKnownLocation(bestProvider);
    if (location != null) {
      onLocationChanged(location);
    }

    // Start getting newer locations
    mLocationManager.requestLocationUpdates(bestProvider, 0, 0, this);

    return new RecordBinder();
  }

  @Override
  public boolean onUnbind(Intent intent) {
    mLocationManager.removeUpdates(this);
    return true;
  }

  private Account getAccount() {
    return mPreferences.getAccount();
  }

  private static final int ONGOING_RECORDING = 0;

  /**
   * Begin recording audio to a temporary file for a certain amount of time,
   * associated with a location.
   *
   * @return {@code true} if the recording started successfully, {@code false}
   *         otherwise
   */
  public synchronized boolean startRecording() {
    Log.i(Constants.TAG, "Starting recording.");
    try {
      mAudioFile = File.createTempFile("audio", "."
          + Constants.RECORDING_FILE_EXTENSION,
          Util.getFilesDir(getAccount(), Util.TEMP_DIR));
    } catch (IOException e) {
      Log.e(TAG, "Failed to create temporary file.", e);
      return false;
    }

    mRecorder = new Recorder(mAudioFile);
    mExecutor.execute(mRecorder);
    mAudioFileStartTimeMs = System.currentTimeMillis();
    mIsRecording = true;

    Notification notification = new Notification(R.drawable.icon,
        getText(R.string.recording),
        System.currentTimeMillis());
    Intent notificationIntent = new Intent(this, RecordActivity.class);
    PendingIntent pendingIntent = PendingIntent.getActivity(this,
        0,
        notificationIntent,
        0);
    notification.setLatestEventInfo(this, getText(R.string.recording),
        getText(R.string.recording),
        pendingIntent);
    startForeground(ONGOING_RECORDING, notification);

    Log.i(Constants.TAG, "Started recording.");
    return true;
  }

  /**
   * Stop the current recording, and move the temporary file to one that has
   * encoded the time, and location.
   */
  public void stopRecording(Runnable callback) {
    Log.i(Constants.TAG, "Stopping recording.");

    if (mRecorder == null) {
      throw new IllegalStateException("Haven't started recording yet.");
    }

    CountDownLatch latch = new CountDownLatch(1);
    mRecorder.stopRecording(latch);
    try {
      latch.await();
    } catch (InterruptedException e) {
      // Eh.
    }
    mRecorder = null;
    Log.i(Constants.TAG, "Stopped recording.");

    if (mLatestLocation == null) {
      // Wait for location.
      // TODO(peterdolan): Need to figure out how to wait for a location.
      Log.e(Constants.TAG, "Didn't have a location by the time we stopped " +
          "recording; dropping file.");
    } else {
      Log.i(Constants.TAG, "Renaming temporary file.");
      renameTempFile();
      Log.i(Constants.TAG, "Renamed temporary file.");
    }

    stopForeground(true);
    callback.run();
  }

  public synchronized boolean isRecording() {
    return mIsRecording;
  }

  public synchronized long getRecordingLengthMs() {
    return System.currentTimeMillis() - mAudioFileStartTimeMs;
  }

  public synchronized long getRecordingSizeBytes() {
    return 0;
  }

  public synchronized File getRecordedFile() {
    return mAudioFile;
  }

  private static class Recorder implements Runnable {

    private final AtomicReference<CountDownLatch> mStopLatch =
        new AtomicReference<CountDownLatch>();
    private final File mOutputFile;

    public Recorder(File outputFile) {
      mOutputFile = outputFile;
    }

    @Override
    public void run() {
      Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

      try {
        RandomAccessFile output = new RandomAccessFile(mOutputFile, "rw");

        short bSamples = Constants.RECORDING_ENCODING ==
            AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;

        // Write file header.
        try {
          output.setLength(0); // Set file length to 0, to prevent unexpected
                               // behavior in case the file already existed
          output.writeBytes("RIFF");
          output.writeInt(0); // Final file size not known yet, write 0
          output.writeBytes("WAVE");
          output.writeBytes("fmt ");
          output.writeInt(Integer.reverseBytes(16)); // Sub-chunk size, 16 for
                                                     // PCM
          output.writeShort(Short.reverseBytes((short) 1)); // AudioFormat, 1
                                                            // for PCM
          output.writeShort(Short.reverseBytes((short) 1)); // Number of
                                                            // channels, 1 for
                                                            // mono
          // Sample rate
          output.writeInt(Integer.reverseBytes(Constants.RECORDING_SAMPLE_RATE));
          output.writeInt(Integer.reverseBytes(Constants.RECORDING_SAMPLE_RATE
              * bSamples / 8)); // Byte rate,
                                // SampleRate*NumberOfChannels*BitsPerSample/8
          output.writeShort(Short.reverseBytes((short) (bSamples / 8))); // Block
                                                                         // align,
                                                                         // NumberOfChannels*BitsPerSample/8
          output.writeShort(Short.reverseBytes(bSamples)); // Bits per sample
          output.writeBytes("data");
          output.writeInt(0); // Data chunk size not known yet, write 0
        } catch (IOException e) {
          Log.e(TAG, "Failed to write wav file header.", e);
          return;
        }

        int bufferSize = AudioRecord.getMinBufferSize(
            Constants.RECORDING_SAMPLE_RATE,
            Constants.RECORDING_CHANNEL,
            Constants.RECORDING_ENCODING);

        AudioRecord audioRecord = new AudioRecord(
            MediaRecorder.AudioSource.MIC,
            Constants.RECORDING_SAMPLE_RATE,
            Constants.RECORDING_CHANNEL,
            Constants.RECORDING_ENCODING,
            bufferSize);

        short[] buffer = new short[bufferSize];

        audioRecord.startRecording();

        int payloadSize = 0;
        while (mStopLatch.get() == null) {
          int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);

          if (bufferReadResult == AudioRecord.ERROR_INVALID_OPERATION) {
            throw new IllegalStateException("Invalid audiorecord read "
                + "operation.");
          } else if (bufferReadResult == AudioRecord.ERROR_BAD_VALUE) {
            throw new IllegalStateException("Audiorecord read " + "bad value.");
          }

          for (int i = 0; i < bufferReadResult; i++) {
            try {
              output.writeShort(Short.reverseBytes(buffer[i]));
            } catch (IOException e) {
              Log.e(TAG, "Could not write to output stream.");
            }
          }

          payloadSize += bufferReadResult * (bSamples / 8);
        }

        audioRecord.stop();

        // Update wav file header and close it.

        try {
          output.seek(4); // Write size to RIFF header
          output.writeInt(Integer.reverseBytes(36 + payloadSize));
          output.seek(40); // Write size to Subchunk2Size field
          output.writeInt(Integer.reverseBytes(payloadSize));
          output.close();
        } catch (IOException e) {
          Log.e(TAG, "Could not close output file.");
        }
      } catch (FileNotFoundException e) {
        // Won't happen; we created the file just now.
        throw new RuntimeException(e);
      } finally {
        mStopLatch.get().countDown();
      }
    }

    public void stopRecording(CountDownLatch latch) {
      mStopLatch.set(latch);
    }
  }

  /**
   * Rename the temporary audio file and switch to the "review and send"
   * Activity.
   */
  private void renameTempFile() {
    if (mLatestLocation == null) {
      throw new IllegalStateException("Trying to rename temp file without a "
          + "location.");
    }

    String finalFilename = Util.getRecordedFile(this,
        getAccount(),
        mAudioFileStartTimeMs,
        (int) (mLatestLocation.getLatitude() * 1E6),
        (int) (mLatestLocation.getLongitude() * 1E6));
    Log.i(TAG, "Renaming '" + mAudioFile.getAbsolutePath() + "' to '"
        + finalFilename + "'.");
    File renamedFile = new File(finalFilename);
    boolean renamed = mAudioFile.renameTo(renamedFile);
    if (!renamed) {
      throw new IllegalStateException("Could not rename file from '"
          + mAudioFile.getAbsolutePath() + "' to '" + finalFilename + "'.");
    }
    mAudioFile = renamedFile;
  }

  @Override
  public void onLocationChanged(Location location) {
    mLatestLocation = location;
  }

  @Override
  public void onProviderDisabled(String provider) {
    // Nothing to see here
  }

  @Override
  public void onProviderEnabled(String provider) {
    // Nothing to see here
  }

  @Override
  public void onStatusChanged(String provider, int status, Bundle extras) {
    // Nothing to see here
  }

  public class RecordBinder extends Binder {
    public RecordService getService() {
      return RecordService.this;
    }
  }
}
