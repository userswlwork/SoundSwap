package net.peterd.soundswap;

import java.io.File;

import android.accounts.Account;
import android.content.Context;
import android.os.Environment;

public class Util {

  public static final String TEMP_DIR = "temp";
  public static final String FETCHED_DIR = "fetched";
  public static final String RECORDED_DIR = "recorded";

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
      int longitudeE6,
      int filePart) {
    File cacheDir = getFilesDir(account, RECORDED_DIR);
    String filename = getFilename(context,
        System.currentTimeMillis(),
        latitudeE6,
        longitudeE6,
        filePart);
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
      int longitudeE6,
      int filePart) {
    return new StringBuilder()
        .append(timeMillis).append("_")
        .append(latitudeE6).append("_")
        .append(longitudeE6).append("_")
        .append(filePart)
        .append(".").append(Constants.RECORDING_FILE_EXTENSION)
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
}
