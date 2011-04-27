package net.peterd.soundswap.ui;

import java.io.File;

import net.peterd.soundswap.Preferences;
import net.peterd.soundswap.R;
import net.peterd.soundswap.Util;
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
    File[] files = Util.getRecordedFiles(account, this);
    setListAdapter(new ArrayAdapter<File>(this,
        android.R.layout.activity_list_item,
        android.R.id.text1,
        files));
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
    File file = (File) getListView().getItemAtPosition(position);
    Intent intent = new Intent(this, ReviewActivity.class);
    intent.putExtra(ReviewActivity.FILENAMES_EXTRA, file.getAbsolutePath());
    startActivity(intent);
  }
}
