package net.peterd.soundswap;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class ReviewActivity extends Activity {

  public static final String FILENAME_EXTRA = "filename";

  private File mRecordedFile;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.review);

    Button playButton = (Button) findViewById(R.id.play);
    playButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            play();
          }
        });

    Button acceptButton = (Button) findViewById(R.id.accept);
    acceptButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            accept();
          }
        });

    Button retryButton = (Button) findViewById(R.id.record_retry);
    retryButton.setOnClickListener(new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            deleteAndRecord();
          }
        });
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
  }

  private boolean play() {
    String fileName = mRecordedFile.getAbsolutePath();

    final MediaPlayer player = new MediaPlayer();

    final ProgressDialog playingDialog = new ProgressDialog(this);
    playingDialog.setCancelable(true);
    playingDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
          @Override
          public void onCancel(DialogInterface dialog) {
            player.stop();
          }
        });

    MediaPlayer.OnCompletionListener finishedPlayingListener =
        new MediaPlayer.OnCompletionListener() {
              @Override
              public void onCompletion(MediaPlayer mp) {
                playingDialog.dismiss();
                mp.reset();
                mp.release();
              }
            };
    player.setOnCompletionListener(finishedPlayingListener);

    player.setOnErrorListener(new MediaPlayer.OnErrorListener() {
          @Override
          public boolean onError(MediaPlayer mp, int what, int extra) {
            new AlertDialog.Builder(ReviewActivity.this)
                .setCancelable(true)
                .setMessage(R.string.error_playing)
                .show();
            return false;
          }
        });

    try {
      player.setDataSource(fileName);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (IllegalStateException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      Log.e("MOO", "Failed to set datasource to file at location '" + fileName +
          "'.");
      return false;
    }

    try {
      player.prepare();
    } catch (IllegalStateException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      Log.e("MOO", "Failed to prepare to play file at location '" + fileName +
          "'.");
      return false;
    }

    player.start();
    playingDialog.show();

    return true;
  }

  private void deleteAndRecord() {
    mRecordedFile.delete();
    startActivity(new Intent(this, RecordActivity.class));
    finish();
  }

  private void accept() {
    Intent intent = new Intent(this, CompressActivity.class);
    intent.putExtra(CompressActivity.FILENAME_EXTRA,
        mRecordedFile.getAbsolutePath());
    startActivity(intent);
  }
}
