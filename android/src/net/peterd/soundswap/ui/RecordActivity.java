package net.peterd.soundswap.ui;

import java.io.File;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.R;
import net.peterd.soundswap.soundservice.RecordService;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class RecordActivity extends AuthenticatedActivity {

  private final Object mServiceLock = new Object();
  private RecordService.RecordBinder mBinder;

  private Button mStartRecordingButton;
  private Button mStopRecordingButton;

  private final ServiceConnection mServiceConnection = new ServiceConnection() {

    @Override
    public void onServiceConnected(ComponentName component, IBinder binder) {
      synchronized (mServiceLock) {
        mBinder = (RecordService.RecordBinder) binder;
        if (mBinder.getService().isRecording()) {
          setViewToRecording();
        } else {
          mStartRecordingButton.setEnabled(true);
        }
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName component) {
      synchronized (mServiceLock) {
        mBinder = null;
        Toast.makeText(RecordActivity.this,
            getString(R.string.error_recording_failed),
            Toast.LENGTH_LONG).show();
        finish();
      }
    }
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.record);

    mStartRecordingButton = (Button) findViewById(R.id.record_start);
    mStartRecordingButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            startRecording();
          }
        });

    mStopRecordingButton = (Button) findViewById(R.id.record_stop);
    mStopRecordingButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View arg0) {
            stopRecording();
          }
        });
    mStopRecordingButton.setVisibility(View.GONE);

    startService(new Intent(this, RecordService.class));
  }

  @Override
  public void onStart() {
    super.onStart();
    mStartRecordingButton.setEnabled(false);

    LocationManager locationManager =
        (LocationManager) getSystemService(LOCATION_SERVICE);
    String bestProvider = locationManager.getBestProvider(
        Constants.LOCATION_CRITERIA,
        true);
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
              }).show();
    }

    bindService(new Intent(this, RecordService.class),
        mServiceConnection,
        Context.BIND_AUTO_CREATE);
  }

  @Override
  public void onStop() {
    super.onPause();
    unbindService(mServiceConnection);
  }

  private void startRecording() {
    mStartRecordingButton.setEnabled(false);
    synchronized (mServiceLock) {
      mBinder.getService().startRecording();
    }
    setViewToRecording();
  }

  private void setViewToRecording() {
    mStartRecordingButton.setVisibility(View.GONE);
    mStopRecordingButton.setVisibility(View.VISIBLE);
  }

  private void stopRecording() {
    Runnable callback = new Runnable() {
      @Override
      public void run() {
        File file = mBinder.getService().getRecordedFile();
        Intent intent = new Intent(RecordActivity.this, ReviewActivity.class);
        intent.putExtra(ReviewActivity.FILENAME_EXTRA, file.getAbsolutePath());
        startActivity(intent);
      }
    };

    synchronized (mServiceLock) {
      mBinder.getService().stopRecording(callback);
    }
    mStartRecordingButton.setEnabled(true);

    stopService(new Intent(this, RecordService.class));
  }
}