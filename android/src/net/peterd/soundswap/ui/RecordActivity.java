package net.peterd.soundswap.ui;

import java.io.File;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.R;
import net.peterd.soundswap.soundservice.RecordService;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.widget.Button;

public class RecordActivity extends AuthenticatedActivity {

  private final Object mServiceLock = new Object();
  private RecordService.RecordBinder mBinder;
  private Button mStartRecordingButton;

  private final ServiceConnection mServiceConnection = new ServiceConnection() {

    @Override
    public void onServiceConnected(ComponentName component, IBinder binder) {
      synchronized (mServiceLock) {
        mBinder = (RecordService.RecordBinder) binder;
        mStartRecordingButton.setEnabled(true);
        if (mBinder.getService().isRecording()) {
          showRecordingDialog();
        }
      }
    }

    @Override
    public void onServiceDisconnected(ComponentName component) {
      synchronized (mServiceLock) {
        mBinder = null;
        mStartRecordingButton.setEnabled(false);
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
            try {
              startRecording();
            } catch (RemoteException e) {
              // TODO(peterdolan): handle with UI messaging
              throw new RuntimeException(e);
            }
          }
        });
    mStartRecordingButton.setEnabled(false);
  }

  @Override
  public void onResume() {
    super.onResume();

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
      return;
    }

    bindService(new Intent(this, RecordService.class),
        mServiceConnection,
        Context.BIND_AUTO_CREATE);
  }

  @Override
  public void onPause() {
    super.onPause();
    unbindService(mServiceConnection);
  }

  private void startRecording() throws RemoteException {
    mStartRecordingButton.setEnabled(false);
    synchronized (mServiceLock) {
      mBinder.getService().startRecording();
    }
    showRecordingDialog();
  }

  private void showRecordingDialog() {
    ProgressDialog recordingDialog = new ProgressDialog(this);
    recordingDialog.setCancelable(false);
    recordingDialog.setMessage(getString(R.string.recording));
    recordingDialog.setButton(getString(R.string.record_stop),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            try {
              stopRecording();
            } catch (RemoteException e) {
              // TODO(peterdolan): handle this with UI messaging
              throw new RuntimeException(e);
            }
          }
        });
    recordingDialog.show();
  }

  private void stopRecording() throws RemoteException {
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
  }
}