package net.peterd.soundswap;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class CompressActivity extends Activity {

  public static final String FILENAME_EXTRA = "filename";

  private File mRecordedFile;

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

    mRecordedFile = new File(filename);
    if (!mRecordedFile.exists()) {
      throw new IllegalArgumentException("File '" + filename + "' does not " +
          "exist.");
    } else {
      long fileLength = mRecordedFile.length();
      Log.i("MOO", "Recorded file '" + filename + "' exists and has length " +
          fileLength);
    }

    ProgressDialog dialog = new ProgressDialog(this);
    final Compressor compressor = new Compressor(dialog);

    dialog.setMessage(getString(R.string.compressing));
    dialog.setProgress(0);
    dialog.setIndeterminate(false);
    dialog.setCancelable(true);
    dialog.setButton(getString(R.string.cancel),
        new DialogInterface.OnClickListener() {

          @Override
          public void onClick(DialogInterface dialog, int which) {
            compressor.cancel(true);
            finish();
          }
        });
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

          @Override
          public void onCancel(DialogInterface dialog) {
            compressor.cancel(true);
            finish();
          }
        });
    dialog.show();

    compressor.execute(mRecordedFile);
  }

  private static class Compressor extends AsyncTask<File, Double, File> {

    private final ProgressDialog mDialog;
    private final AtomicBoolean mCancel = new AtomicBoolean(false);

    public Compressor(ProgressDialog dialog) {
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

      File outputF = new File(inputF.getAbsoluteFile() + ".zip");
      if (outputF.exists()) {
        Log.e("MOO", "Output file already exists");
        return null;
      }
      try {
        if (!outputF.createNewFile()) {
          Log.e("MOO", "Failed to create file " + outputF.getAbsolutePath());
          return null;
        }
      } catch (IOException e) {
        Log.e("MOO", "Failed to create file " + outputF.getAbsolutePath());
        return null;
      }

      ZipOutputStream zos;
      FileInputStream fis;

      try {
        zos = new ZipOutputStream(new FileOutputStream(outputF));
        fis = new FileInputStream(inputF);
      } catch (FileNotFoundException e) {
        Log.e("MOO", "Failed to begin reading or writing data to zip.", e);
        return null;
      }

      long inputSize = inputF.length();
      try {
        zos.putNextEntry(new ZipEntry(inputF.getName()));

        int readBytes = 0;
        int totalReadBytes = 0;
        byte[] buffer = new byte[1024];
        while (!mCancel.get() &&
            (readBytes = fis.read(buffer, 0, buffer.length)) > 0) {
          zos.write(buffer, 0, readBytes);
          totalReadBytes += readBytes;
          publishProgress(((double) totalReadBytes) / inputSize);
        }

        zos.closeEntry();
        zos.close();

        fis.close();

        if (mCancel.get()) {
          outputF.delete();
        }
      } catch (IOException e) {
        Log.e("MOO", "Error zipping data.", e);
        return null;
      } finally {
        try {
          zos.close();
          fis.close();
        } catch (IOException e) {
          Log.e("MOO", "Uncrecoverable IOError while closing file streams.", e);
          return null;
        }
      }

      return outputF;
    }

    @Override
    protected void onProgressUpdate(Double... progress) {
      if (progress.length != 1) {
        throw new IllegalArgumentException(
            "Must be exactly one progress value.");
      }
      mDialog.setProgress((int) (10000 * progress[0]));
    }
  }
}
