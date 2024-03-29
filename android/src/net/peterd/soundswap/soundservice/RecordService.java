package net.peterd.soundswap.soundservice;

import static net.peterd.soundswap.Constants.TAG;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.Preferences;
import net.peterd.soundswap.R;
import net.peterd.soundswap.Recording;
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
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public class RecordService extends Service implements LocationListener {

  private static final int ONGOING_RECORDING = 1;

  private Preferences mPreferences;

  ProgressDialog mWaitingForLocationDialog;
  LocationManager mLocationManager;

  private Location mLatestLocation;
  private Recorder mRecorder;
  private Recording mRecording;

  private final Executor mExecutor = Executors.newSingleThreadExecutor();

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    // We want this service to continue running until it is explicitly
    // stopped, so return sticky.
    return START_STICKY;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    Log.d(TAG, "RecordService#onCreate()");
    mPreferences = new Preferences(this);
    if (getAccount() == null) {
      throw new IllegalStateException("Cannot start recording service " +
          "without a registered account.");
    }
    mLocationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    Log.d(TAG, "RecordService#onDestroy()");
    if (isRecording()) {
      Log.e(TAG, "Destroying record service while recording.");
      stopRecording(null);
    }
  }

  @Override
  public IBinder onBind(Intent intent) {
    Log.d(TAG, "RecordService#onBind()");

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

  private Account getAccount() {
    return mPreferences.getAccount();
  }

  /**
   * Begin recording audio to a temporary file for a certain amount of time,
   * associated with a location.
   *
   * @return {@code true} if the recording started successfully, {@code false}
   *         otherwise
   */
  public synchronized boolean startRecording() {
    if (isRecording()) {
      throw new IllegalStateException("Already recording, cannot start again.");
    }

    Log.i(Constants.TAG, "Starting recording.");
    mRecorder = new Recorder(getBaseContext(), getAccount());
    mExecutor.execute(mRecorder);

    Notification notification = new Notification(R.drawable.icon,
        getText(R.string.recording),
        System.currentTimeMillis());
    PendingIntent pendingIntent = PendingIntent.getActivity(this,
        0,
        new Intent(this, RecordActivity.class),
        0);
    notification.setLatestEventInfo(this,
        getText(R.string.recording),
        getText(R.string.recording),
        pendingIntent);
    startForeground(ONGOING_RECORDING, notification);

    Log.i(Constants.TAG, "Started recording.");
    return true;
  }

  /**
   * Stop the current recording, and move the temporary file to one that has
   * encoded the time, and location.
   *
   * @param callback a {@link Runnable} to be run when the service has finished
   *                 stopping the recording, or {@code null} if no notification
   *                 is necessary.
   */
  public synchronized void stopRecording(Runnable callback) {
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
    Log.i(Constants.TAG, "Stopped recording.");

    if (mLatestLocation == null) {
      // TODO(peterdolan): wait for location, and if canceled, maintain file
      // with null location.
      Log.e(Constants.TAG, "Didn't have a location by the time we stopped " +
          "recording; dropping file.");
    } else {
      Log.i(Constants.TAG, "Renaming temporary files.");
      if (mRecorder.finalize(mLatestLocation)) {
        Log.i(Constants.TAG, "Renamed temporary files.");
      } else {
        // TODO(peterdolan): handle this failure
        Log.e(Constants.TAG, "Failed to rename temporary files.");
      }
    }
    mRecording = mRecorder.getFinalRecording();
    mRecorder = null;
    mLocationManager.removeUpdates(this);

    stopForeground(true);
    if (callback != null) {
      callback.run();
    }
  }

  public synchronized boolean isRecording() {
    Log.d(TAG, "RecordService#isRecording(); " +
        Boolean.toString(mRecorder != null));
    return mRecorder != null;
  }

  public synchronized long getRecordingLengthMs() {
    return 0;
  }

  public synchronized long getRecordingSizeBytes() {
    return 0;
  }

  public synchronized Recording getRecording() {
    return mRecording;
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
