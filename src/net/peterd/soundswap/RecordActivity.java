package net.peterd.soundswap;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.maps.GeoPoint;

public class RecordActivity extends Activity implements LocationListener {

  ProgressDialog mWaitingForLocationDialog;
  LocationManager mLocationManager;

  private Location mLatestLocation;

  private Recorder mRecorder;
  private Thread mRecorderThread;
  private File mTempAudioFile;
  private long mTempAudioFileStartTimeMs;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.record);

    final Button startRecording = (Button) findViewById(R.id.record_start);
    startRecording.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            startRecording();
          }
        });
  }

  @Override
  public void onResume() {
    super.onResume();

    mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

    Criteria criteria = new Criteria();
    criteria.setAccuracy(Criteria.ACCURACY_COARSE);
    criteria.setAltitudeRequired(false);
    criteria.setBearingRequired(false);
    criteria.setCostAllowed(true);
    criteria.setPowerRequirement(Criteria.NO_REQUIREMENT);
    criteria.setSpeedRequired(false);

    String bestProvider = mLocationManager.getBestProvider(criteria, true);
    if (bestProvider == null) {
      new AlertDialog.Builder(this)
          .setMessage(R.string.location_device_error)
          .setCancelable(true)
          .setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                  finish();
                }
              })
          .setPositiveButton(R.string.disappointed_but_okay,
              new DialogInterface.OnClickListener() {
                  @Override
                  public void onClick(DialogInterface dialog, int which) {
                    finish();
                  }
                })
          .show();
      return;
    }

    // Get the latest location
    Location location = mLocationManager.getLastKnownLocation(bestProvider);
    if (location != null) {
      onLocationChanged(location);
    }

    // Start getting newer locations
    mLocationManager.requestLocationUpdates(bestProvider, 0, 0, this);
  }

  @Override
  public void onPause() {
    super.onPause();
    mLocationManager.removeUpdates(this);
  }

  /**
   * Begin recording audio to a temporary file for a certain amount of time,
   * associated with a location.
   *
   * @return {@code true} if the recording started successfully, {@code false}
   *         otherwise
   */
  private boolean startRecording() {
    try {
      mTempAudioFile = File.createTempFile("audio",
          "." + Util.RECORDING_FILE_EXTENSION,
          Util.getFilesDir());
    } catch (IOException e1) {
      Log.e("MOO", "Failed to create temporary file.", e1);
      return false;
    }

    mRecorder = new Recorder(mTempAudioFile);
    mRecorderThread = new Thread(mRecorder);

    ProgressDialog recordingDialog = new ProgressDialog(this);
    recordingDialog.setCancelable(false);
    recordingDialog.setMessage(getString(R.string.recording));
    recordingDialog.setButton(getString(R.string.record_stop),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            stopRecording();
          }
        });

    mTempAudioFileStartTimeMs = System.currentTimeMillis();
    mRecorderThread.start();
    recordingDialog.show();
    return true;
  }

  private static class Recorder implements Runnable {

    private final AtomicBoolean mIsRecording = new AtomicBoolean(false);
    private final File mOutputFile;

    public Recorder(File outputFile) {
      mOutputFile = outputFile;
    }

    @Override
    public void run() {
      try {
        OutputStream os = new FileOutputStream(mOutputFile);
        BufferedOutputStream bos = new BufferedOutputStream(os);
        DataOutputStream dos = new DataOutputStream(bos);

        int bufferSize = AudioRecord.getMinBufferSize(Util.RECORDING_SAMPLE_RATE,
            Util.RECORDING_CHANNEL,
            Util.RECORDING_ENCODING);
        Log.i("MOO", "Recording buffer size: " + bufferSize);

        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
            Util.RECORDING_SAMPLE_RATE,
            Util.RECORDING_CHANNEL,
            Util.RECORDING_ENCODING,
            bufferSize);

        short[] buffer = new short[bufferSize];
        audioRecord.startRecording();

        while (mIsRecording.get()) {
          int bufferReadResult = audioRecord.read(buffer, 0, bufferSize);
          for (int i = 0; i < bufferReadResult; i++) {
            try {
              dos.writeShort(buffer[i]);
            } catch (IOException e) {
              Log.e("MOO", "Could not write to output stream.");
            }
          }
        }

        audioRecord.stop();
        try {
          dos.close();
        } catch (IOException e) {
          Log.e("MOO", "Could not close output file.");
        }
      } catch (FileNotFoundException e) {
        // Won't happen; we created the file just now.
        throw new RuntimeException(e);
      }
    }

    public void stopRecording() {
      mIsRecording.set(false);
    }
  }

  /**
   * Stop the current recording, and move the temporary file to one that has
   * encoded the time, and location.
   */
  private void stopRecording() {
    if (mRecorder == null) {
      throw new IllegalStateException("Haven't started recording yet.");
    }

    mRecorder.stopRecording();
    try {
      mRecorderThread.join();
    } catch (InterruptedException e) {
      // Meh.
    }
    mRecorder = null;

    if (mLatestLocation == null) {
      mWaitingForLocationDialog = new ProgressDialog(this);
      mWaitingForLocationDialog.setCancelable(true);
      mWaitingForLocationDialog.setOnCancelListener(
          new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                  deleteTempFile();
                }
              });
      mWaitingForLocationDialog.setMessage(
          getString(R.string.waiting_for_location));
      mWaitingForLocationDialog.show();
    } else {
      renameTempFile();
    }
  }

  private void deleteTempFile() {
    mTempAudioFile.delete();
  }

  /**
   * Rename the temporary audio file and switch to the "review and send"
   * Activity.
   */
  private void renameTempFile() {
    if (mLatestLocation == null) {
      throw new IllegalStateException("Trying to rename temp file without a " +
          "location.");
    }

    GeoPoint point = new GeoPoint((int) (mLatestLocation.getLatitude() * 1E6),
        (int) (mLatestLocation.getLongitude() * 1E6));

    String finalFilename =
      Util.getFullFilename(this, mTempAudioFileStartTimeMs, point);
    Log.i("MOO", "Renaming '" + mTempAudioFile.getAbsolutePath() + "' to '" +
        finalFilename + "'.");
    boolean renamed = mTempAudioFile.renameTo(new File(finalFilename));
    if (!renamed) {
      throw new IllegalStateException("Could not rename file from '" +
          mTempAudioFile.getAbsolutePath() + "' to '" + finalFilename + "'.");
    }

    Intent reviewIntent = new Intent(this, ReviewActivity.class);
    reviewIntent.putExtra(ReviewActivity.FILENAME_EXTRA, finalFilename);
    startActivity(reviewIntent);
    finish();
  }

  @Override
  public void onLocationChanged(Location location) {
    mLatestLocation = location;
    if (mWaitingForLocationDialog != null) {
      mWaitingForLocationDialog.dismiss();
      mWaitingForLocationDialog = null;
      renameTempFile();
    }
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
}