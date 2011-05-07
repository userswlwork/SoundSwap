package net.peterd.soundswap;

import static net.peterd.soundswap.Constants.TAG;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import android.accounts.Account;
import android.content.Context;
import android.util.Log;

public class Recording {

  private final Account mAccount;
  private final long mTimestamp;
  private int mLatE6;
  private int mLonE6;
  private File[] mFileParts;
  private File mDirectory;

  private boolean mInitialized;

  private Recording(Context context, Account account, long timestamp) {
    mAccount = account;
    mTimestamp = timestamp;
    mDirectory = constructTempDirectory();
  }

  private Recording(Context context, Account account,
      long timestamp,
      int latE6,
      int lonE6) {
    mAccount = account;
    mTimestamp = timestamp;
    mLatE6 = latE6;
    mLonE6 = lonE6;

    mDirectory = constructFinalDirectory(latE6, lonE6);
  }

  private File constructTempDirectory() {
    return new File(Util.getFilesDir(mAccount, Util.TEMP_DIR),
        Long.toString(mTimestamp));
  }

  private File constructFinalDirectory(int latE6, int lonE6) {
    return new File(Util.getFilesDir(mAccount, Util.RECORDED_DIR),
        mTimestamp + "_" + latE6 + "_" + lonE6);
  }

  public void init() {
    if (!mDirectory.exists()) {
      mDirectory.mkdirs();
    }
    mFileParts = mDirectory.listFiles();
    mInitialized = true;
  }

  public static Recording newTempRecording(Context context,
      Account account,
      long timestamp) {
    return new Recording(context, account, timestamp);
  }

  public static Collection<Recording> getRecordings(Context context,
      Account account) {
    File recordingsDir = Util.getFilesDir(account, Util.RECORDED_DIR);
    if (!recordingsDir.exists()) {
      return Collections.emptyList();
    }

    List<Recording> recordings = new ArrayList<Recording>();
    for (File recordingDir : recordingsDir.listFiles()) {
      recordings.add(fromRecordedDirectory(context, account, recordingDir));
    }
    return recordings;
  }

  public String getKey() {
    checkInitialized();
    return mDirectory.getName();
  }

  /**
   * @param context
   * @param account
   * @param key
   * @return the existing Recording with the given key, or {@code null} if there
   *         is no recording with that key
   */
  public static Recording getRecording(Context context,
      Account account,
      String key) {
    for (Recording recording : getRecordings(context, account)) {
      if (recording.getKey().equals(key)) {
        return recording;
      }
    }
    return null;
  }

  private static Recording fromRecordedDirectory(Context context,
      Account account,
      File recordingDir) {
    String recordingPathName = recordingDir.getName();
    String[] parts = recordingPathName.split("_");
    Long timestamp = Long.parseLong(parts[0]);
    Integer latE6 = Integer.parseInt(parts[1]);
    Integer lonE6 = Integer.parseInt(parts[2]);
    Recording recording = new Recording(context,
        account,
        timestamp,
        latE6,
        lonE6);
    recording.init();
    return recording;
  }

  public boolean moveToRecordedDirectory(int latE6, int lonE6) {
    checkInitialized();

    mLatE6 = latE6;
    mLonE6 = lonE6;

    File finalDirectory = constructFinalDirectory(latE6, lonE6);
    if (finalDirectory.exists()) {
      Log.e(TAG, "Moving to final directory '" +
          finalDirectory.getAbsolutePath() + "' failed; already exists.");
      return false;
    }
    if (!mDirectory.renameTo(finalDirectory)) {
      Log.e(TAG, "Moving to final directory '" +
          finalDirectory.getAbsolutePath() + "' failed; could not rename.");
      return false;
    }
    mDirectory = finalDirectory;
    init();
    return true;
  }

  public File[] getFileParts() {
    checkInitialized();
    return mFileParts;
  }

  public File addFilePart(String extension) {
    checkInitialized();
    return new File(mDirectory,
        Integer.toString(mFileParts.length) + "." + extension);
  }

  public long getTimestamp() {
    checkInitialized();
    return mTimestamp;
  }

  public int getLatE6() {
    checkInitialized();
    return mLatE6;
  }

  public int getLonE6() {
    checkInitialized();
    return mLonE6;
  }

  public boolean delete() {
    checkInitialized();
    if (mDirectory.delete()) {
      mInitialized = false;
      return true;
    } else {
      return false;
    }
  }

  private void checkInitialized() {
    if (!mInitialized) {
      throw new IllegalStateException("Recording not initialized.");
    }
  }
}
