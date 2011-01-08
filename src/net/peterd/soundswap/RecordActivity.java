package net.peterd.soundswap;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.google.android.maps.GeoPoint;

public class RecordActivity extends Activity implements LocationListener {

  ProgressDialog mWaitingForLocationDialog;
  LocationManager mLocationManager;

  private static final double RECORD_DURATION_SECS = 2;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);
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

    Location location = mLocationManager.getLastKnownLocation(bestProvider);
    if (location == null) {
      mLocationManager.requestLocationUpdates(bestProvider, 0, 0, this);
      Log.i("MOO", "requested location updates.");

      mWaitingForLocationDialog =
        ProgressDialog.show(this, null, getString(R.string.waiting_for_location), true);
      mWaitingForLocationDialog.setCancelable(true);
      mWaitingForLocationDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
        @Override
        public void onCancel(DialogInterface dialog) {
          finish();
        }
      });
    } else {
      Log.i("MOO", "already had a location.");
      onLocationChanged(location);
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    mLocationManager.removeUpdates(this);
  }

  /**
   * Record audio to a temporary file for a certain amount of time, associated
   * with a location.
   *
   * @param lengthSecs the amount of time to record, in seconds
   * @param location the location that's being recorded
   * @return {@code true} if the recording completed successfully, {@code false}
   *         otherwise
   */
  private boolean record(double lengthSecs, Location location) {
    GeoPoint point = new GeoPoint((int) (location.getLatitude() * 1E6),
        (int) (location.getLongitude() * 1E6));

    MediaRecorder recorder = new MediaRecorder();
    recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
    recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);

    String outputFilename =
        Util.getFullFilename(this, System.currentTimeMillis(), point);
    recorder.setOutputFile(outputFilename);
    Log.i("MOO", "recording to '" + outputFilename + "'.");
    try {
      recorder.prepare();

      recorder.start();

      Thread.sleep((long) (lengthSecs * 1000));

      recorder.stop();
      recorder.reset();   // You can reuse the object by going back to setAudioSource() step
      recorder.release(); // Now the object cannot be reused

      return true;
    } catch (IllegalStateException e) {
      // TODO:handle error and display message to user
      e.printStackTrace();
    } catch (IOException e) {
      // TODO: handle error and display message to user
      e.printStackTrace();
    } catch (InterruptedException e) {
      // Meh.
    }
    return false;
  }

  @Override
  public void onLocationChanged(final Location location) {
    Log.i("MOO", "received location.");

    if (mWaitingForLocationDialog != null) {
      mWaitingForLocationDialog.dismiss();
    }
    mLocationManager.removeUpdates(this);

    final ProgressDialog progress = new ProgressDialog(this);

    final AsyncTask<Void, Integer, Boolean> task =
        new AsyncTask<Void, Integer, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
              return record(RECORD_DURATION_SECS, location);
            }

            @Override
            protected void onProgressUpdate(Integer... progress) {
              // Nothing to see here.
            }

            @Override
            protected void onPostExecute(Boolean success) {
              progress.dismiss();

              TextView view = (TextView) findViewById(R.id.main_text_view);
              File cacheDir = getCacheDir();
              String[] files = cacheDir.list();

              StringBuilder rep = new StringBuilder();
              for (int i = 0; i < files.length; ++i) {
                rep.append(files[i]).append("\n");
              }
              view.setText(rep);
            }
          };

    progress.setCancelable(true);
    progress.setOnCancelListener(new DialogInterface.OnCancelListener() {

          @Override
          public void onCancel(DialogInterface dialog) {
            task.cancel(true);
          }
        });
    progress.setIndeterminate(true);
    progress.setMessage(getString(R.string.recording));
    progress.show();

    task.execute();
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