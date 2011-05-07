package net.peterd.soundswap.syncadapter;

import static net.peterd.soundswap.Constants.TAG;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.Recording;
import net.peterd.soundswap.client.AuthenticatedHttpClient;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.util.EntityUtils;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/**
 * SyncAdapter implementation for syncing sample SyncAdapter contacts to the
 * platform ContactOperations provider.
 */
public class SyncAdapter extends AbstractThreadedSyncAdapter {

  private final Context mContext;
  private final AuthenticatedHttpClient mFileListClient;
  private final Sender mSender;

  public SyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
    mContext = context;
    mFileListClient = new AuthenticatedHttpClient(context, null);
    mSender = new Sender(mFileListClient);
  }

  @Override
  public void onPerformSync(Account account,
      Bundle extras,
      String authority,
      ContentProviderClient provider,
      SyncResult syncResult) {
    Log.i(TAG, "Synchronizing account '" + account + "'.");

    Set<String> existingRecordingKeys = getExistingRecordingKeys();
    if (existingRecordingKeys == null) {
      return;
    }

    for (Recording recording : Recording.getRecordings(mContext, account)) {
      if (!existingRecordingKeys.contains(recording.getKey())) {
        uploadRecording(recording);
      }
    }
  }

  private Set<String> getExistingRecordingKeys() {
    Log.i(TAG, "Getting list of registered recording keys.");

    HttpGet get = new HttpGet(Constants.LIST_SOUNDS_URL);
    Set<String> existingRecordingTimestamps = mFileListClient.request(get,
        new ResponseHandler<Set<String>>() {
            @Override
            public Set<String> handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
              HttpEntity entity = response.getEntity();
              if (response.getStatusLine().getStatusCode() == 200 &&
                  entity != null) {
                String contents = EntityUtils.toString(entity);
                String[] lines = contents.split("\n");

                Log.d(TAG, "List of uploaded recording timestamps: " +
                    Arrays.toString(lines));

                Set<String> set = new HashSet<String>();
                for (String line : lines) {
                  set.add(line);
                }
                return set;
              } else {
                Log.e(TAG, "List recordings failed.");
                return null;
              }
            }
          });
    return existingRecordingTimestamps;
  }

  private void uploadRecording(Recording recording) {
    Log.i(TAG, "Uploading recording with key " + recording.getKey());

    if (!mSender.createRecording(recording)) {
      Log.e(TAG, "Failed to create record on server for Recording with " +
          "key " + recording.getKey());
      return;
    }

    for (File file : recording.getFileParts()) {
      if (!mSender.addFilePart(recording, file)) {
        Log.e(TAG, "Failed to upload file " + file.getAbsolutePath() +
            " as part of recording with key " + recording.getKey());
          break;
        }
      }
  }

  private static class Sender {

    private static final String HEADER_LOCATION = "Location";

    private final AuthenticatedHttpClient mClient;

    public Sender(AuthenticatedHttpClient client) {
      mClient = client;
    }

    public boolean createRecording(final Recording recording) {
      HttpPost post = new HttpPost(
          Constants.HOST +
          "/api/sound/new?client_key=" +
          Uri.encode(recording.getKey()));
      return mClient.request(post, new ResponseHandler<Boolean>() {
            @Override
            public Boolean handleResponse(HttpResponse response) {
              return response.getStatusLine().getStatusCode() == 200;
            }
          });
    }

    public boolean addFilePart(final Recording recording,
        final File filePart) {
      if (!filePart.exists()) {
        throw new IllegalArgumentException("File " + filePart.getAbsolutePath()
            + " does not exist.");
      }

      String url = Uri.parse(Constants.FORM_REDIRECT_URL).buildUpon()
          .appendQueryParameter("client_key", recording.getKey())
          .build()
          .toString();

      // Get the upload redirect Uri.
      String uploadUri = mClient.request(
          new HttpGet(url),
          new ResponseHandler<String>() {
            @Override
            public String handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
              Header[] headers = response.getHeaders(HEADER_LOCATION);
              if (headers == null || headers.length != 1) {
                return null;
              } else {
                String redirect = headers[0].getValue();
                Log.d(TAG, "Get upload redirect location '" +
                    redirect + "'.");
                return redirect;
              }
            }
          });
      if (uploadUri == null) {
        return false;
      }

      // Upload the file.
      FileBody body = new FileBody(filePart);
      MultipartEntity entity = new MultipartEntity();
      entity.addPart("bin", body);
      HttpPost postUpload = new HttpPost(uploadUri);
      postUpload.setEntity(entity);

      Log.d(TAG, "Uploading '" + filePart + "'.");
      return mClient.request(postUpload, new ResponseHandler<Boolean>() {
            @Override
            public Boolean handleResponse(HttpResponse response)
                throws ClientProtocolException, IOException {
              int status = response.getStatusLine().getStatusCode();
              if (status == 200 || status == 302) {
                return true;
              } else {
                Log.e(TAG, "Failed to upload file; response code " +
                    status + "; " + response.getStatusLine().getReasonPhrase());
                return false;
              }
            }
          });
    }
  }
}
