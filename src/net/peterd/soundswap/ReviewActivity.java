package net.peterd.soundswap;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
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
    ProgressDialog playingDialog = new ProgressDialog(this);
    playingDialog.show();

    Thread playingThread = new Thread(new Player(mRecordedFile, playingDialog));
    playingThread.start();

    return true;
  }

  private static class Player implements Runnable {

    private final File mAudioFile;
    private final Dialog mPlayingDialog;

    public Player(File audioFile, Dialog playingDialog) {
      mAudioFile = audioFile;
      mPlayingDialog = playingDialog;
    }

    @Override
    public void run() {
      Log.i("MOO", "Playing back file " + mAudioFile.getAbsolutePath());

      // Get the length of the audio stored in the file (16 bit so 2 bytes per short)
      // and create a short array to store the recorded audio.
      assert Util.RECORDING_ENCODING == AudioFormat.ENCODING_PCM_16BIT;
      assert mAudioFile.length() < Integer.MAX_VALUE;

      try {
        // Create a DataInputStream to read the audio data back from the saved file.
        InputStream is = new FileInputStream(mAudioFile);
        BufferedInputStream bis = new BufferedInputStream(is);
        DataInputStream dis = new DataInputStream(bis);

        // Close the input streams.
        dis.close();

        int bufferSize = AudioRecord.getMinBufferSize(Util.RECORDING_SAMPLE_RATE,
            Util.RECORDING_CHANNEL,
            Util.RECORDING_ENCODING);

        // Create a new AudioTrack object using the same parameters as the AudioRecord
        // object used to create the file.
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC,
            Util.RECORDING_SAMPLE_RATE,
            Util.RECORDING_CHANNEL,
            Util.RECORDING_ENCODING,
            bufferSize,
            AudioTrack.MODE_STREAM);

        // Start playback
        audioTrack.play();

        short[] audio = new short[bufferSize];

        while (dis.available() > 0) {
          // Read the file into the music array.
          int i = 0;
          while (dis.available() > 0 && i < bufferSize) {
            audio[i] = dis.readShort();
            i++;
          }

          // Write the music buffer to the AudioTrack object
          audioTrack.write(audio, 0, i);
        }

      } catch (Throwable t) {
        Log.e("AudioTrack", "Playback Failed", t);
      }
      mPlayingDialog.dismiss();
    }
  }

  private void deleteAndRecord() {
    mRecordedFile.delete();
    startActivity(new Intent(this, RecordActivity.class));
    finish();
  }
}
