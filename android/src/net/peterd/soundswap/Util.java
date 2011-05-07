package net.peterd.soundswap;

import java.io.File;

import android.accounts.Account;
import android.os.Environment;

public class Util {

  public static final String RECORDED_DIR = "recorded";
  public static final String TEMP_DIR = "temp";
  public static final String FETCHED_DIR = "fetched";

  private static final int version = 1;

  public static File getFilesDir(Account account, String subDir) {
    File externalStorage = Environment.getExternalStorageDirectory();
    externalStorage =
        new File(externalStorage.getAbsolutePath() +
            "/SoundSwap" +
            "/" + account.name +
            "/" + Integer.toString(version) +
            "/" + subDir);
    externalStorage.mkdirs();
    return externalStorage;
  }
}
