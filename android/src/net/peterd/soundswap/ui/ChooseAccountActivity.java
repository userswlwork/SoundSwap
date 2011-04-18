package net.peterd.soundswap.ui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.Preferences;
import net.peterd.soundswap.R;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class ChooseAccountActivity extends ListActivity {

  private Preferences mPreferences;
  private AccountManager mAccountManager;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mPreferences = new Preferences(this);
    mAccountManager = AccountManager.get(getApplicationContext());
    List<Account> accounts = new ArrayList<Account>();
    Collections.addAll(accounts,
        mAccountManager.getAccountsByType(Constants.ACCOUNT_TYPE));

    Account account = mPreferences.getAccount();
    if (account != null && accounts.contains(account)) {
      proceed();
    } else {
      setListAdapter(new ArrayAdapter<Account>(this,
          R.layout.account_list_item, accounts));
    }
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    Account account = (Account) getListView().getItemAtPosition(position);

    // TODO: set the account only after we get confirmation that we are
    // authorized to use the account.
    mPreferences.putAccount(account);
    mAccountManager.getAuthToken(account,
        Constants.AUTH_TOKEN_TYPE,
        true,
        new GetAuthTokenCallback(),
        null);
  }

  private void proceed() {
    startActivity(new Intent(this, RecordActivity.class));
    finish();
  }

  private class GetAuthTokenCallback implements AccountManagerCallback<Bundle> {
    public void run(AccountManagerFuture<Bundle> result) {
      Bundle bundle;
      try {
        bundle = result.getResult();
        Intent intent = (Intent) bundle.get(AccountManager.KEY_INTENT);
        if (intent != null) {
          startActivity(intent);
          // TODO: handle account result, and proceed or re-prompt
        } else {
          proceed();
        }
      } catch (OperationCanceledException e) {
        Log.e(Constants.TAG, "Failed to get authentication.", e);
      } catch (AuthenticatorException e) {
        Log.e(Constants.TAG, "Failed to get authentication.", e);
      } catch (IOException e) {
        Log.e(Constants.TAG, "Failed to get authentication.", e);
      }
    }
  };
}
