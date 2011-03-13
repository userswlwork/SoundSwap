package net.peterd.soundswap;

import java.io.File;

import android.media.AudioFormat;
import android.os.Environment;

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
}
