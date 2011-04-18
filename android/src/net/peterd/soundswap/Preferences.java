package net.peterd.soundswap;

import android.accounts.Account;
import android.content.Context;
import android.content.SharedPreferences;

public class Preferences {

  private static final String NAME = "net.peterd.soundswap";

  private final SharedPreferences mPreferences;

  private static final String KEY_AUTH_COOKIE = "appengine_auth_cookie";
  private static final String KEY_ACCOUNT_NAME = "account_name";
  private static final String KEY_ACCOUNT_TYPE = "account_type";

  public Preferences(Context context) {
    mPreferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE);
  }

  public synchronized void putAuthCookie(String cookieValue) {
    mPreferences.edit().putString(KEY_AUTH_COOKIE, cookieValue);
  }

  public synchronized String getAuthCookie() {
    return mPreferences.getString(KEY_AUTH_COOKIE, null);
  }

  public synchronized void putAccount(Account account) {
    mPreferences.edit()
        .putString(KEY_ACCOUNT_NAME, account.name)
        .putString(KEY_ACCOUNT_TYPE, account.type)
        .commit();
  }

  public synchronized Account getAccount() {
    if (mPreferences.contains(KEY_ACCOUNT_NAME)) {
      return new Account(mPreferences.getString(KEY_ACCOUNT_NAME, null),
          mPreferences.getString(KEY_ACCOUNT_TYPE, null));
    } else {
      return null;
    }
  }
}
