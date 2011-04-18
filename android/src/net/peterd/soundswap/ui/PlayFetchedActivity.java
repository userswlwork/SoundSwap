package net.peterd.soundswap.ui;

import java.io.File;

import net.peterd.soundswap.R;
import net.peterd.soundswap.Util;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class PlayFetchedActivity extends Activity {

  public static final String FILENAME_EXTRA = "filename";

  private File mFetchedFile;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.play_fetched);

    Button playButton = (Button) findViewById(R.id.play);
    playButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Util.play(PlayFetchedActivity.this, mFetchedFile);
      }
    });

    final Activity activity = this;
    Button recordNewButton = (Button) findViewById(R.id.record_new);
    recordNewButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        startActivity(new Intent(activity, RecordActivity.class));
      }
    });

    Button fetchSoundButton = (Button) findViewById(R.id.fetch_sound);
    fetchSoundButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        startActivity(new Intent(activity, FetchActivity.class));
      }
    });
  }

  @Override
  public void onResume() {
    super.onResume();

    Intent intent = getIntent();
    String filename = intent.getStringExtra(FILENAME_EXTRA);

    if (filename == null) {
      throw new IllegalArgumentException("Launch intent must include a "
          + "filename to review.");
    }

    mFetchedFile = new File(filename);
    if (!mFetchedFile.exists()) {
      throw new IllegalArgumentException("File '" + filename + "' does not "
          + "exist.");
    } else {
      long fileLength = mFetchedFile.length();
      Log.i("MOO", "File '" + filename + "' exists and has length "
          + fileLength);
    }
  }
}
