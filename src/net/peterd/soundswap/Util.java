package net.peterd.soundswap;

import java.io.File;

import android.content.Context;
import android.media.MediaRecorder;

import com.google.android.maps.GeoPoint;

public class Util {

  public static final String FILE_EXTENSION = "3gpp";
  public static final int FILE_TYPE = MediaRecorder.OutputFormat.THREE_GPP;

  public static String getFullFilename(Context context,
      long timeMillis,
      GeoPoint geoPoint) {
    File cacheDir = context.getCacheDir();
    cacheDir.mkdirs();

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
