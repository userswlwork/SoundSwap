package net.peterd.soundswap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class FetchActivity extends Activity {

  public static final String FETCH_URI_EXTRA = "fetch_uri";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.fetch);
  }

  @Override
  public void onResume() {
    super.onResume();

    Intent intent = getIntent();

    Uri fetchUri;
    if (intent.hasExtra(FETCH_URI_EXTRA)) {
      fetchUri = Uri.parse(intent.getStringExtra(FETCH_URI_EXTRA));
    } else {
      fetchUri = getFetchSoundUri();
    }

    ProgressDialog dialog = new ProgressDialog(this);
    final Fetcher fetcher = new Fetcher(this, dialog);

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

    fetcher.execute(fetchUri);
  }

  private Uri getFetchSoundUri() {
    return Uri.parse(Util.FETCH_SOUND_URL).buildUpon()
        .appendQueryParameter(Util.DEVICE_ID_URI_KEY, Util.getDeviceId(this))
        .build();
  }

  private static class Fetcher extends AsyncTask<Uri, Double, File> {

    private final Activity mActivity;
    private final ProgressDialog mDialog;
    private final AtomicBoolean mCancel = new AtomicBoolean(false);

    public Fetcher(Activity activity, ProgressDialog dialog) {
      mActivity = activity;
      mDialog = dialog;
    }

    @Override
    protected void onCancelled() {
      mCancel.set(true);
    }

    @Override
    protected File doInBackground(Uri... uris) {
      if (uris.length != 1) {
            throw new IllegalArgumentException("Must specify exactly one uri " +
                "to fetch from.");
      }

      Uri inputUri = uris[0];
      DefaultHttpClient client = new DefaultHttpClient();

      ResponseHandler<byte[]> handler = new ResponseHandler<byte[]>() {
            public byte[] handleResponse(HttpResponse response)
                  throws ClientProtocolException, IOException {
              HttpEntity entity = response.getEntity();
              if (entity != null) {
                return EntityUtils.toByteArray(entity);
              } else {
                return null;
              }
            }
          };

      File file = Util.getFetchedFilename(System.currentTimeMillis());

      try {
        HttpGet getSound = new HttpGet(inputUri.toString());
        byte[] response = client.execute(getSound, handler);
        Log.i("MOO", "Downloaded " + response.length + " bytes.");

        FileOutputStream fos = new FileOutputStream(file);
        fos.write(response);
        fos.close();
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
