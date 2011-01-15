package net.peterd.soundswap;

import java.io.File;

import android.content.Context;
import android.media.AudioFormat;
import android.os.Environment;

import com.google.android.maps.GeoPoint;

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

  public static String getFullFilename(Context context,
      long timeMillis,
      GeoPoint geoPoint) {
    File cacheDir = getFilesDir();
    String filename = getFilename(System.currentTimeMillis(), geoPoint);
    String fullFilename =
        cacheDir.getAbsolutePath() + "/" + filename;
    return fullFilename;
  }

  private static String getFilename(long timeMillis, GeoPoint geoPoint) {
    StringBuilder builder = new StringBuilder();
    builder.append(timeMillis).append("_");
    builder.append(geoPoint.getLatitudeE6()).append("_");
    builder.append(geoPoint.getLongitudeE6());
    builder.append(".").append(RECORDING_FILE_EXTENSION);
    return builder.toString();
  }
}
