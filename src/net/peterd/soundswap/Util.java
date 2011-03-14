package net.peterd.soundswap;

import java.io.File;
import java.io.IOException;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;

public class Util {

  public static final String RECORDING_FILE_EXTENSION = "wav";
  public static final int RECORDING_SAMPLE_RATE = 22050;
  public static final int RECORDING_CHANNEL = AudioFormat.CHANNEL_CONFIGURATION_MONO;
  public static final int RECORDING_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  public static File getFilesDir() {
    File externalStorage = Environment.getExternalStorageDirectory();
    externalStorage =
        new File(externalStorage.getAbsolutePath() + "/SoundSwap");
    externalStorage.mkdirs();
    return externalStorage;
  }

  public static String getFullFilename(long timeMillis,
      int latitudeE6,
      int longitudeE6) {
    File cacheDir = getFilesDir();
    String filename =
        getFilename(System.currentTimeMillis(), latitudeE6, longitudeE6);
    String fullFilename =
        cacheDir.getAbsolutePath() + "/" + filename;
    return fullFilename;
  }

  private static String getFilename(long timeMillis,
      int latitudeE6,
      int longitudeE6) {
    StringBuilder builder = new StringBuilder();
    builder.append(timeMillis).append("_");
    builder.append(latitudeE6).append("_");
    builder.append(longitudeE6);
    builder.append(".").append(RECORDING_FILE_EXTENSION);
    return builder.toString();
  }

  public static boolean play(final Context context, File file) {
    String fileName = file.getAbsolutePath();

    final MediaPlayer player = new MediaPlayer();

    final ProgressDialog playingDialog = new ProgressDialog(context);
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
            new AlertDialog.Builder(context)
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
}
