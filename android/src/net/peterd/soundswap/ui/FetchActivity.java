package net.peterd.soundswap.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.R;
import net.peterd.soundswap.Util;
import net.peterd.soundswap.client.AuthenticatedHttpClient;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;

import android.accounts.Account;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class FetchActivity extends AuthenticatedActivity {

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.fetch);
  }

  @Override
  public void onResume() {
    super.onResume();

    ProgressDialog dialog = new ProgressDialog(this);
    final Fetcher fetcher = new Fetcher(this,
        getAccount(),
        new AuthenticatedHttpClient(this, null),
        dialog);

    dialog.setMessage(getString(R.string.fetching));
    dialog.setProgress(0);
    dialog.setIndeterminate(false);
    dialog.setCancelable(true);
    dialog.setButton(getString(R.string.cancel),
        new DialogInterface.OnClickListener() {

          @Override
          public void onClick(DialogInterface dialog, int which) {
            fetcher.cancel(true);
            finish();
          }
        });
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

      @Override
      public void onCancel(DialogInterface dialog) {
        fetcher.cancel(true);
        finish();
      }
    });
    dialog.show();

    fetcher.execute(Uri.parse(Constants.FETCH_SOUND_URL));
  }

  private static class Fetcher extends AsyncTask<Uri, Double, File> {

    private final Context mActivity;
    private final Account mAccount;
    private final AuthenticatedHttpClient mClient;
    private final ProgressDialog mDialog;
    private final AtomicBoolean mCancel = new AtomicBoolean(false);

    public Fetcher(Context activity,
        Account account,
        AuthenticatedHttpClient client,
        ProgressDialog dialog) {
      mActivity = activity;
      mAccount = account;
      mClient = client;
      mDialog = dialog;
    }

    @Override
    protected void onCancelled() {
      mCancel.set(true);
    }

    @Override
    protected File doInBackground(Uri... uris) {
      if (uris.length != 1) {
        throw new IllegalArgumentException("Must specify exactly one uri "
            + "to fetch from.");
      }

      Uri inputUri = uris[0];

      final AtomicReference<String> filename = new AtomicReference<String>(null);
      ResponseHandler<byte[]> handler = new ResponseHandler<byte[]>() {
        public byte[] handleResponse(HttpResponse response)
            throws ClientProtocolException, IOException {
          Header[] filenames = response.getHeaders("X-SoundSwap-Filename");
          if (filenames != null && filenames.length == 1) {
            filename.set(filenames[0].getValue());
            Log.i("MOO", "Fetched filename: " + filename.get());
          }

          HttpEntity entity = response.getEntity();
          if (entity != null) {
            return EntityUtils.toByteArray(entity);
          } else {
            return null;
          }
        }
      };

      File file;
      try {
        HttpGet getSound = new HttpGet(inputUri.toString());
        byte[] data = mClient.request(getSound, handler);
        file = Util.getFetchedFilename(mAccount, filename.get());

        if (data != null) {
          Log.i("MOO", "Downloaded " + data.length + " bytes.");
          FileOutputStream fos = new FileOutputStream(file);
          fos.write(data);
          fos.close();
        }
      } catch (Exception e) {
        Log.e("MOO", "Failed to fetch sound.", e);
        return null;
      }

      return file;
    }

    @Override
    protected void onProgressUpdate(Double... progress) {
      if (progress.length != 1) {
        throw new IllegalArgumentException(
            "Must be exactly one progress value.");
      }
      mDialog.setProgress((int) (10000 * progress[0]));
    }

    @Override
    protected void onPostExecute(File file) {
      mDialog.dismiss();

      Intent intent = new Intent(mActivity, PlayFetchedActivity.class);
      intent.putExtra(PlayFetchedActivity.FILENAME_EXTRA,
          file.getAbsolutePath());
      mActivity.startActivity(intent);
    }
  }
}
