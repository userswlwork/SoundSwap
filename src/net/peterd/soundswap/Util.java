package net.peterd.soundswap;

import java.io.File;

import android.content.Context;

import com.google.android.maps.GeoPoint;

public class Util {

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
    builder.append(".mp4");
    return builder.toString();
  }
}
