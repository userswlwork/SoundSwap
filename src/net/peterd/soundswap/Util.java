package net.peterd.soundswap;

import java.io.File;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Environment;

import com.google.android.maps.GeoPoint;

public class Util {

  public static final String FILE_EXTENSION = "mp4";
  public static final int FILE_TYPE = MediaRecorder.OutputFormat.MPEG_4;

  public static File getFileDirectory() {
    File extDir = Environment.getExternalStorageDirectory();
    File fileDir = new File(extDir + "/SoundSwap");
    fileDir.mkdirs();
    return fileDir;
  }

  public static String getFullFilename(Context context,
      long timeMillis,
      GeoPoint geoPoint) {
    File cacheDir = getFileDirectory();
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
    builder.append(".").append(FILE_EXTENSION);
    return builder.toString();
  }
}
