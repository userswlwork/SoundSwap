package net.peterd.soundswap.ui;

import java.io.File;

import net.peterd.soundswap.R;
import net.peterd.soundswap.Util;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

public class ReviewActivity extends AuthenticatedActivity {

  public static final String FILENAME_EXTRA = "filename";

  private File mRecordedFile;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.review);

    Button playButton = (Button) findViewById(R.id.play);
    playButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        Util.play(ReviewActivity.this, mRecordedFile);
      }
    });

    Button acceptButton = (Button) findViewById(R.id.accept);
    acceptButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        accept();
      }
    });

    Button retryButton = (Button) findViewById(R.id.record_retry);
    retryButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        deleteAndRecord();
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

    mRecordedFile = new File(filename);
    if (!mRecordedFile.exists()) {
      throw new IllegalArgumentException("File '" + filename + "' does not "
          + "exist.");
    } else {
      long fileLength = mRecordedFile.length();
      Log.i("MOO", "Recorded file '" + filename + "' exists and has length "
          + fileLength);
    }
  }

  private void deleteAndRecord() {
    mRecordedFile.delete();
    startActivity(new Intent(this, RecordActivity.class));
    finish();
  }

  private void accept() {
    startActivity(new Intent(this, ChooseAccountActivity.class));
  }
}
