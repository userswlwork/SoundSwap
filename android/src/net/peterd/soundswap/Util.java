package net.peterd.soundswap;

import java.io.File;
import java.io.IOException;

import android.accounts.Account;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.os.Environment;
import android.util.Log;

public class Util {

  public static final String APPENGINE_DOMAIN = "sound-swap.appspot.com";
  public static final String HOST = "http://" + APPENGINE_DOMAIN;
  public static final String HOST_SECURE = "https://" + APPENGINE_DOMAIN;
  public static final String FORM_REDIRECT_URL = HOST + "/api/sound/upload_form_redirect";
  public static final String FETCH_SOUND_URL = HOST + "/api/sound";
  public static final String LIST_SOUNDS_URL = HOST + "/api/sound/list";

  public static final String TEMP_DIR = "temp";
  public static final String FETCHED_DIR = "fetched";
  public static final String RECORDED_DIR = "recorded";

  public static final String RECORDING_FILE_EXTENSION = "wav";
  public static final int RECORDING_SAMPLE_RATE = 22050;
  public static final int RECORDING_CHANNEL = AudioFormat.CHANNEL_CONFIGURATION_MONO;
  public static final int RECORDING_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  public static File getFilesDir(Account account, String subDir) {
    File externalStorage = Environment.getExternalStorageDirectory();
    externalStorage =
        new File(externalStorage.getAbsolutePath() +
            "/SoundSwap" +
            "/" + account.name +
            "/" + subDir);
    externalStorage.mkdirs();
    return externalStorage;
  }

  public static File[] getRecordedFiles(Account account, Context context) {
    File filesDir = getFilesDir(account, RECORDED_DIR);
    return filesDir.listFiles();
  }

  public static String getRecordedFile(Context context,
      Account account,
      long timeMillis,
      int latitudeE6,
      int longitudeE6) {
    File cacheDir = getFilesDir(account, RECORDED_DIR);
    String filename = getFilename(context,
        System.currentTimeMillis(),
        latitudeE6,
        longitudeE6);
    String fullFilename = cacheDir.getAbsolutePath() + "/" + filename;
    return fullFilename;
  }

  public static File getRecordedFile(Account account, String filename) {
    File cacheDir = getFilesDir(account, RECORDED_DIR);
    return new File(cacheDir.getAbsolutePath() + "/" + filename);
  }

  private static String getFilename(Context context,
      long timeMillis,
      int latitudeE6,
      int longitudeE6) {
    return new StringBuilder()
        .append(timeMillis).append("_")
        .append(latitudeE6).append("_")
        .append(longitudeE6)
        .append(".").append(RECORDING_FILE_EXTENSION)
        .toString();
  }

  /**
   * Get a full, absolute filename for the given fetched filename.
   * @param filename
   * @return
   */
  public static File getFetchedFilename(Account account, String filename) {
    return new File(new StringBuilder()
        .append(getFilesDir(account, FETCHED_DIR))
        .append("/")
        .append(filename)
        .toString());
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
