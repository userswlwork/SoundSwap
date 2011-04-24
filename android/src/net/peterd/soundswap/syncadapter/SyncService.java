package net.peterd.soundswap.syncadapter;

import net.peterd.soundswap.Preferences;
import net.peterd.soundswap.R;
import android.accounts.Account;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

/**
 * Service to handle Account sync. This is invoked with an intent with action
 * ACTION_AUTHENTICATOR_INTENT. It instantiates the syncadapter and returns its
 * IBinder.
 */
public class SyncService extends Service {

    private static final Object sSyncAdapterLock = new Object();
    private static SyncAdapter sSyncAdapter = null;

    @Override
    public void onCreate() {
      super.onCreate();
      synchronized (sSyncAdapterLock) {
        if (sSyncAdapter == null) {
            sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
        }
      }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
      super.onStartCommand(intent, flags, startId);
      final Preferences preferences = new Preferences(this);
      new Thread(new Runnable() {
            public void run() {
              Account account = preferences.getAccount();
              if (account != null) {
                synchronized (sSyncAdapterLock) {
                  sSyncAdapter.onPerformSync(account,
                      null,
                      null,
                      null,
                      null);
                }
              }
            }
          });
      Toast.makeText(this, R.string.synchronizing, Toast.LENGTH_LONG).show();
      return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }
}
