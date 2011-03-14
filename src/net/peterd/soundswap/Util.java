package net.peterd.soundswap;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.MediaPlayer;
import android.os.Environment;
import android.telephony.TelephonyManager;
import android.util.Log;

public class Util {

  private static final AtomicReference<String> mDeviceId =
      new AtomicReference<String>(null);

  private static final String HOST = "http://10.1.10.12:8080";
  public static final String FORM_REDIRECT_URL = HOST + "/upload/form_redirect";
  public static final String FETCH_SOUND_URL = HOST + "/sound";
  public static final String DEVICE_ID_URI_KEY = "device_id";

  public static final String TEMP_DIR = "temp";
  public static final String FETCHED_DIR = "fetched";
  public static final String RECORDED_DIR = "recorded";

  public static final String RECORDING_FILE_EXTENSION = "wav";
  public static final int RECORDING_SAMPLE_RATE = 22050;
  public static final int RECORDING_CHANNEL = AudioFormat.CHANNEL_CONFIGURATION_MONO;
  public static final int RECORDING_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

  public static File getFilesDir(String subDir) {
    File externalStorage = Environment.getExternalStorageDirectory();
    externalStorage =
        new File(externalStorage.getAbsolutePath() +
            "/SoundSwap" +
            "/" + subDir);
    externalStorage.mkdirs();
    return externalStorage;
  }

  public static String getRecordedFile(Context context,
      long timeMillis,
      int latitudeE6,
      int longitudeE6) {
    File cacheDir = getFilesDir(RECORDED_DIR);
    String filename = getFilename(context,
        System.currentTimeMillis(),
        latitudeE6,
        longitudeE6);
    String fullFilename = cacheDir.getAbsolutePath() + "/" + filename;
    return fullFilename;
  }

  private static String getFilename(Context context,
      long timeMillis,
      int latitudeE6,
      int longitudeE6) {
    String deviceId = getDeviceId(context);
    return new StringBuilder()
        .append(deviceId).append("_")
        .append(timeMillis).append("_")
        .append(latitudeE6).append("_")
        .append(longitudeE6)
        .append(".").append(RECORDING_FILE_EXTENSION)
        .toString();
  }

  public static String getDeviceId(Context context) {
    String deviceId = mDeviceId.get();
    if (deviceId == null) {
      TelephonyManager manager = (TelephonyManager) context.getSystemService(
          Context.TELEPHONY_SERVICE);
      deviceId = manager.getDeviceId();
      if (deviceId == null) {
        deviceId = "null";
      }
      mDeviceId.set(deviceId);
    }
    return deviceId;
  }

  public static File getFetchedFilename(long timeMillis) {
    return new File(new StringBuilder()
        .append(getFilesDir(FETCHED_DIR))
        .append("/")
        .append(timeMillis)
        .append(".")
        .append(RECORDING_FILE_EXTENSION)
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
