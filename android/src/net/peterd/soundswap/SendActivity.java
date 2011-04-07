package net.peterd.soundswap;

import java.io.File;
import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class SendActivity extends Activity {

  public static final String FILENAME_EXTRA = "filename";

  private File mCompressedFile;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.compress);
  }

  @Override
  public void onResume() {
    super.onResume();

    Intent intent = getIntent();
    String filename = intent.getStringExtra(FILENAME_EXTRA);

    if (filename == null) {
      throw new IllegalArgumentException("Launch intent must include a " +
          "filename to review.");
    }

    mCompressedFile = new File(filename);
    if (!mCompressedFile.exists()) {
      throw new IllegalArgumentException("File '" + filename + "' does not " +
          "exist.");
    } else {
      long fileLength = mCompressedFile.length();
      Log.i("MOO", "Recorded file '" + filename + "' exists and has length " +
          fileLength);
    }

    ProgressDialog dialog = new ProgressDialog(this);
    final Sender sender = new Sender(dialog, this);

    dialog.setMessage(getString(R.string.sending));
    dialog.setProgress(0);
    dialog.setIndeterminate(false);
    dialog.setCancelable(true);
    dialog.setButton(getString(R.string.cancel),
        new DialogInterface.OnClickListener() {

          @Override
          public void onClick(DialogInterface dialog, int which) {
            sender.cancel(true);
            finish();
          }
        });
    dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {

          @Override
          public void onCancel(DialogInterface dialog) {
            sender.cancel(true);
            finish();
          }
        });
    dialog.show();

    sender.execute(mCompressedFile);
  }

  protected void onUploadSuccess(Uri continueUri) {
    Intent intent = new Intent(this, FetchActivity.class);
    intent.putExtra(FetchActivity.FETCH_URI_EXTRA, continueUri.toString());
    startActivity(intent);
  }

  private static class Sender extends AsyncTask<File, Double, Uri> {

    private static final String HEADER_LOCATION = "Location";

    private final ProgressDialog mDialog;
    private final SendActivity mActivity;
    private final AtomicBoolean mCancel = new AtomicBoolean(false);

    public Sender(ProgressDialog dialog, SendActivity activity) {
      mDialog = dialog;
      mActivity = activity;
    }

    @Override
    protected void onCancelled() {
      mCancel.set(true);
    }

    @Override
    protected Uri doInBackground(File... files) {
      if (files.length != 1) {
        throw new IllegalArgumentException("Must specify exactly one file " +
            "to compress.");
      }

      File inputF = files[0];
      if (!inputF.exists()) {
        throw new IllegalArgumentException("File " + inputF.getAbsolutePath() +
            " does not exist.");
      }

      DefaultHttpClient client = new DefaultHttpClient();
      client.setRedirectHandler(new RedirectHandler() {
            @Override
            public boolean isRedirectRequested(HttpResponse response,
                HttpContext context) {
              // Disable following redirects
              return false;
            }

            @Override
            public URI getLocationURI(HttpResponse response,
                HttpContext context) throws ProtocolException {
              return null;
            }
          });

      Uri continueUri = null;
      try {
        // Get the upload redirect Uri.
        String uploadUri = null;
        try {
          HttpGet getUploadUrl = new HttpGet(Util.FORM_REDIRECT_URL);
          HttpResponse response = client.execute(getUploadUrl);
          Header[] headers = response.getHeaders(HEADER_LOCATION);
          if (headers == null || headers.length != 1) {
            throw new IllegalStateException(
            "Did not return a redirect location.");
          }
          uploadUri = headers[0].getValue();
          Log.i("MOO", "Got upload redirect location " + uploadUri);
        } catch (Exception e) {
          Log.e("MOO", "Failed to fetch redirect upload uri.", e);
          return null;
        }

        // Upload the file.
        try {
          FileBody body = new FileBody(inputF);

          MultipartEntity entity = new MultipartEntity();
          entity.addPart("bin", body);

          HttpPost postUpload = new HttpPost(uploadUri);
          postUpload.setEntity(entity);
          HttpResponse response = client.execute(postUpload);

          Log.i("MOO", "Uploaded; response status line: " +
              response.getStatusLine());

          Header[] headers = response.getHeaders(HEADER_LOCATION);
          if (headers == null || headers.length != 1) {
            throw new IllegalStateException(
                "Did not return a redirect location.");
          }
          continueUri = Uri.parse(headers[0].getValue());
        } catch (Exception e) {
          Log.e("MOO", "Failed to upload file to server.", e);
          return null;
        }
      } finally {
        try {
          client.getConnectionManager().shutdown();
        } catch (Exception ignore) {
          // Meh
        }
      }
      return continueUri;
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
    protected void onPostExecute(Uri continueUri) {
      Log.i("MOO", "Got continue uri " + continueUri);
      mDialog.dismiss();
      mActivity.onUploadSuccess(continueUri);
    }
  }
}
