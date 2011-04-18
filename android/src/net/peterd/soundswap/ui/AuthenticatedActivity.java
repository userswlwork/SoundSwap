package net.peterd.soundswap.ui;

import net.peterd.soundswap.Preferences;
import android.accounts.Account;
import android.app.Activity;

public abstract class AuthenticatedActivity extends Activity {

  private Preferences mPreferences;

  @Override
  public void onResume() {
    super.onResume();
    mPreferences = new Preferences(this);
    if (getAccount() == null) {
      // start authentication activity.
    }
  }

  protected Account getAccount() {
    return mPreferences.getAccount();
  }
}
