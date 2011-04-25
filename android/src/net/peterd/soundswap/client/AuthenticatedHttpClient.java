package net.peterd.soundswap.client;

import static net.peterd.soundswap.Constants.TAG;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.Preferences;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.RedirectHandler;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

public class AuthenticatedHttpClient {

  /**
   * The name of the cookie that AppEngine uses to supply its authentication
   * cookie values.
   */
  private static final String APPENGINE_AUTH_COOKIE_NAME = "ACSID";

  private final Context mContext;
  private final Preferences mPreferences;
  private final DefaultHttpClient mClient;

  public AuthenticatedHttpClient(Context context,
      RedirectHandler redirectHandler) {
    mContext = context;
    mPreferences = new Preferences(context);

    DefaultHttpClient client = new DefaultHttpClient();
    ClientConnectionManager mgr = client.getConnectionManager();
    HttpParams params = client.getParams();
    mClient = new DefaultHttpClient(
        new ThreadSafeClientConnManager(params, mgr.getSchemeRegistry()),
        params);
    mClient.getParams().setBooleanParameter(
        ClientPNames.HANDLE_REDIRECTS, false);
  }

  public <T> T request(HttpUriRequest request, ResponseHandler<T> responseProcessor) {
    Log.i(Constants.TAG, "Request (" + request.getURI() + ")");
    if (!isAuthenticated()) {
      authenticate();
    }
    return doRequest(responseProcessor, request);
  }

  private void authenticate() {
    Log.d(Constants.TAG, "Authenticating");

    AccountManager accountManager = AccountManager.get(mContext);
    Account account = mPreferences.getAccount();
    if (account == null) {
      throw new IllegalStateException("Cannot request an authenticated Uri " +
          "before choosing an account.");
    }

    GetAuthTokenCallback callback = new GetAuthTokenCallback();
    AccountManagerFuture<Bundle> future = accountManager.getAuthToken(account,
        Constants.AUTH_TOKEN_TYPE,
        true,  /* Notify auth failure */
        callback,
        null  /* Call back on the current thread, not a custom handler */);

    try {
      Log.d(Constants.TAG, "Waiting for authentication.");
      future.getResult();
      if (!callback.hasRun()) {
        Log.d(Constants.TAG, "Manually running get auth token callback.");
        callback.run(future);
      }
      Log.d(Constants.TAG, "Authenticated client.");
    } catch (AuthenticatorException e) {
      Log.e(TAG, "Error while updating authentication cookie.", e);
    } catch (OperationCanceledException e) {
      Log.e(TAG, "Error while updating authentication cookie.", e);
    } catch (IOException e) {
      Log.e(TAG, "Error while updating authentication cookie.", e);
    }
  }

  private <T> T doRequest(ResponseHandler<T> responseHandler,
      HttpUriRequest request) {
    try {
      Log.d(Constants.TAG, "Doing request (" + request.getURI() + ")");
      return mClient.execute(request, responseHandler);
    } catch (ClientProtocolException e) {
      Log.e(TAG, "Failed to execute request.", e);
      return null;
    } catch (IOException e) {
      Log.e(TAG, "Failed to execute request.", e);
      return null;
    } catch (RuntimeException e) {
      Log.e(TAG, "Failed to execute request.", e);
      throw e;
    }
  }

  private class GetAuthTokenCallback implements AccountManagerCallback<Bundle> {

    private boolean mHasRun = false;

    public void run(AccountManagerFuture<Bundle> result) {
      mHasRun = true;
      Bundle bundle;
      try {
        bundle = result.getResult();
        String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        if (authToken != null) {
          authenticate(authToken);
        } else {
          Log.e(Constants.TAG, "Failed to get authentication token; failing " +
              "authentication.");
        }
      } catch (OperationCanceledException e) {
        throw new RuntimeException("Failed to get authentication token.", e);
      } catch (AuthenticatorException e) {
        throw new RuntimeException("Failed to get authentication token.", e);
      } catch (IOException e) {
        throw new RuntimeException("Failed to get authentication token.", e);
      }
    }

    public boolean hasRun() {
      return mHasRun;
    }

    private boolean authenticate(String authToken) {
      Log.d(Constants.TAG, "Authenticating with token " + authToken);
      Uri requestUri = Uri.parse(Constants.HOST + "/_ah/login").buildUpon()
          .appendQueryParameter("continue", Constants.HOST)
          .appendQueryParameter("auth", authToken)
          .build();

      HttpGet method = new HttpGet(requestUri.toString());
      try {
        Log.d(Constants.TAG, "Requesting " + method.getURI());
        boolean authenticated =
            mClient.execute(method, new ResponseHandler<Boolean>() {
                  @Override
                  public Boolean handleResponse(HttpResponse response)
                      throws ClientProtocolException, IOException {
                    Log.d(Constants.TAG, "Response: " + response.getStatusLine().getStatusCode() + "; " + Arrays.toString(response.getAllHeaders()));
                    return isAuthenticated();
                  }
                });
        Log.d(Constants.TAG, "Authenticated: " + authenticated);
        return authenticated;
      } catch (ClientProtocolException e) {
        Log.e(TAG, "Failed to authenticate due to network error.", e);
        return false;
      } catch (IOException e) {
        Log.e(TAG, "Failed to authenticate due to network error.", e);
        return false;
      }
    }
  }

  public Collection<Cookie> getCookies(Uri uri) {
    HttpGet method = new HttpGet(uri.toString());
    try {
      mClient.execute(method);
      return mClient.getCookieStore().getCookies();
    } catch (ClientProtocolException e) {
      Log.e(TAG, "Failed to request uri " + uri.toString(), e);
    } catch (IOException e) {
      Log.e(TAG, "Failed to request uri " + uri.toString(), e);
    } catch (RuntimeException e) {
      Log.e(TAG, "Failed to request uri " + uri.toString(), e);
      method.abort();
      throw e;
    }
    return null;
  }

  private boolean isAuthenticated() {
    Collection<Cookie> cookies = mClient.getCookieStore().getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (APPENGINE_AUTH_COOKIE_NAME.equals(cookie.getName())) {
          return true;
        }
      }
    }
    return false;
  }
}
