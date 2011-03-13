package net.peterd.soundswap;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class SendActivity extends Activity {

  public static final String FILENAME_EXTRA = "filename";

  private File mCompressedFile;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.compress);
  }

  @Override
  public void onResume() {
    super.onResume();

    Intent intent = getIntent();
    String filename = intent.getStringExtra(FILENAME_EXTRA);

    if (filename == null) {
      throw new IllegalArgumentException("Launch intent must include a " +
          "filename to review.");
    }

    mCompressedFile = new File(filename);
    if (!mCompressedFile.exists()) {
      throw new IllegalArgumentException("File '" + filename + "' does not " +
          "exist.");
    } else {
      long fileLength = mCompressedFile.length();
      Log.i("MOO", "Recorded file '" + filename + "' exists and has length " +
          fileLength);
    }

    ProgressDialog dialog = new ProgressDialog(this);
    final Sender sender = new Sender(dialog);

    dialog.setMessage(getString(R.string.sending));
    dialog.setProgress(0);
    dialog.setIndeterminate(false);
    dialog.setCancelable(true);
    dialog.setButton(getString(R.string.cancel),
        new DialogInterface.OnClickListener() {

          @Override
          public void onClick(DialogInterface dialog, int which) {
            sender.cancel(true);
            finish();
          }
        });
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

          @Override
          public void onCancel(DialogInterface dialog) {
            sender.cancel(true);
            finish();
          }
        });
    dialog.show();

    sender.execute(mCompressedFile);
  }

  private static class Sender extends AsyncTask<File, Double, File> {

    private final ProgressDialog mDialog;
    private final AtomicBoolean mCancel = new AtomicBoolean(false);

    public Sender(ProgressDialog dialog) {
      mDialog = dialog;
    }

    @Override
    protected void onCancelled() {
      mCancel.set(true);
    }

    @Override
    protected File doInBackground(File... files) {
      if (files.length != 1) {
        throw new IllegalArgumentException("Must specify exactly one file " +
            "to compress.");
      }

      File inputF = files[0];
      if (!inputF.exists()) {
        throw new IllegalArgumentException("File " + inputF.getAbsolutePath() +
            " does not exist.");
      }

      return null;
    }

    @Override
    protected void onProgressUpdate(Double... progress) {
      if (progress.length != 1) {
        throw new IllegalArgumentException(
            "Must be exactly one progress value.");
      }
      mDialog.setProgress((int) (10000 * progress[0]));
    }

    @Override
    protected void onPostExecute(File file) {
      mDialog.dismiss();
    }
  }
}
