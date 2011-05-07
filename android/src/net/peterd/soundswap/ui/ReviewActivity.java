package net.peterd.soundswap.ui;

import static net.peterd.soundswap.Constants.TAG;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import net.peterd.soundswap.R;
import net.peterd.soundswap.Recording;
import net.peterd.soundswap.syncadapter.SyncService;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class ReviewActivity extends AuthenticatedActivity {

  public static final String RECORDING_KEY_EXTRA = "recording_key";

  private Recording mRecording;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.review);

    Button playButton = (Button) findViewById(R.id.play);
    playButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        playFileList();
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
    String recordingKey = intent.getStringExtra(RECORDING_KEY_EXTRA);

    if (recordingKey == null) {
      throw new IllegalArgumentException("Launch intent must include a "
          + "recording key to review.");
    }

    mRecording = Recording.getRecording(this,
        getAccount(),
        recordingKey);
    if (mRecording == null) {
      throw new IllegalArgumentException("Recording key '" + recordingKey +
          "' did not match a recording.");
    }
  }

  private void deleteAndRecord() {
    mRecording.delete();
    startActivity(new Intent(this, RecordActivity.class));
    finish();
  }

  private void accept() {
    // Start synchronization
    startService(new Intent(this, SyncService.class));

    // Go to the list of sounds
    startActivity(new Intent(this, RecordingsListActivity.class));
  }

  private boolean playFileList() {
    final Iterator<File> fileIterable =
        Arrays.asList(mRecording.getFileParts()).iterator();

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
                if (fileIterable.hasNext()) {
                  File file = fileIterable.next();
                  player.reset();
                  startPlayingFile(file, player);
                } else {
                  playingDialog.dismiss();
                  mp.reset();
                  mp.release();
                }
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


    File file = fileIterable.next();

    if (startPlayingFile(file, player)) {
      playingDialog.show();
      return true;
    } else {
      return false;
    }
  }

  private boolean startPlayingFile(File file, MediaPlayer player) {
    try {
      player.setDataSource(file.getAbsolutePath());
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (IllegalStateException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      Log.e(TAG, "Failed to set datasource to file at location '" +
          file.getAbsolutePath() + "'.");
      return false;
    }

    try {
      player.prepare();
    } catch (IllegalStateException e) {
      throw new RuntimeException(e);
    } catch (IOException e) {
      Log.e(TAG, "Failed to prepare to play file at location '" +
          file.getAbsolutePath() + "'.");
      return false;
    }

    player.start();
    return true;
  }
}
