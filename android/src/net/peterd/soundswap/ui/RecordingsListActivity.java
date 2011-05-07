package net.peterd.soundswap.ui;

import java.util.Collection;

import net.peterd.soundswap.Preferences;
import net.peterd.soundswap.R;
import net.peterd.soundswap.Recording;
import net.peterd.soundswap.syncadapter.SyncService;
import android.accounts.Account;
import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

public class RecordingsListActivity extends ListActivity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    Account account = new Preferences(this).getAccount();
    if (account == null) {
      throw new IllegalStateException("no account.");
    }
    Collection<Recording> recordings = Recording.getRecordings(this, account);
    Recording[] recordingsArr =
        recordings.toArray(new Recording[recordings.size()]);

    setListAdapter(new ArrayAdapter<Recording>(this,
        android.R.layout.activity_list_item,
        android.R.id.text1,
        recordingsArr));
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
      MenuInflater inflater = getMenuInflater();
      inflater.inflate(R.menu.main_menu, menu);
      return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
      // Handle item selection
      switch (item.getItemId()) {
        case R.id.menu_recordings:
          startActivity(new Intent(this, RecordingsListActivity.class));
          return true;
        case R.id.menu_record:
          startActivity(new Intent(this, RecordActivity.class));
          return true;
        case R.id.menu_sync:
          startService(new Intent(this, SyncService.class));
          return true;
        default:
          return super.onOptionsItemSelected(item);
      }
  }

  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    Recording recording = (Recording) getListView().getItemAtPosition(position);
    Intent intent = new Intent(this, ReviewActivity.class);
    intent.putExtra(ReviewActivity.RECORDING_KEY_EXTRA, recording.getKey());
    startActivity(intent);
  }
}
