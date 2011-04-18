package net.peterd.soundswap.client;

import static net.peterd.soundswap.Constants.TAG;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import net.peterd.soundswap.Constants;
import net.peterd.soundswap.Preferences;
import net.peterd.soundswap.Util;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
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
import org.apache.http.protocol.HttpContext;

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
  private final RedirectHandler mRedirectHandler;

  private final RedirectHandler mAuthenticationRedirectHandler =
      new RedirectHandler() {
          @Override
          public boolean isRedirectRequested(HttpResponse response,
              HttpContext context) {
            // Disable following redirects
            return false;
          }

          @Override
          public URI getLocationURI(HttpResponse response, HttpContext context)
              throws ProtocolException {
            return null;
          }
        };

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
    mRedirectHandler = redirectHandler == null ?
        mClient.getRedirectHandler() : redirectHandler;
  }

  public <T> T request(HttpUriRequest request, ResponseHandler<T> responseProcessor) {
    String authCookie = mPreferences.getAuthCookie();
    if (authCookie == null) {
      requestAuthCookie();
      authCookie = mPreferences.getAuthCookie();
    }
    return doRequest(responseProcessor, request, authCookie);
  }

  private void requestAuthCookie() {
    AccountManager accountManager = AccountManager.get(mContext);
    Account account = mPreferences.getAccount();
    if (account == null) {
      throw new IllegalStateException("Cannot request an authenticated Uri " +
          "before choosing an account.");
    }

    CountDownLatch latch = new CountDownLatch(1);
    accountManager.getAuthToken(account,
        Constants.AUTH_TOKEN_TYPE,
        true,  /* Notify auth failure */
        new GetAuthTokenCallback(latch),
        null  /* Call back on the current thread, not a custom handler */);

    try {
      latch.await();
    } catch (InterruptedException e) {
      Log.e(TAG, "Interrupted while updating authentication cookie.", e);
    }
  }

  private <T> T doRequest(ResponseHandler<T> responseHandler,
      HttpUriRequest request,
      String authCookie) {
    try {
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

    private final CountDownLatch mLatch;

    public GetAuthTokenCallback(CountDownLatch latch) {
      mLatch = latch;
    }

    public void run(AccountManagerFuture<Bundle> result) {
      Bundle bundle;
      try {
        bundle = result.getResult();

        String authToken = bundle.getString(AccountManager.KEY_AUTHTOKEN);
        if (authToken != null) {
          authenticate(authToken);
        }
      } catch (OperationCanceledException e) {
        throw new RuntimeException("Failed to get authentication token.", e);
      } catch (AuthenticatorException e) {
        throw new RuntimeException("Failed to get authentication token.", e);
      } catch (IOException e) {
        throw new RuntimeException("Failed to get authentication token.", e);
      } finally {
        mLatch.countDown();
      }
    }

    private boolean authenticate(String authToken) {
      Uri requestUri = new Uri.Builder()
          .scheme("https")
          .authority(Util.APPENGINE_DOMAIN)
          .appendEncodedPath("_ah")
          .appendEncodedPath("login")
          .appendQueryParameter("continue", "http://localhost/")
          .appendQueryParameter("auth", authToken)
          .build();

      HttpGet method = new HttpGet(requestUri.toString());
      try {
        mClient.setRedirectHandler(mAuthenticationRedirectHandler);
        boolean authenticated =
            mClient.execute(method, new ResponseHandler<Boolean>() {
                  @Override
                  public Boolean handleResponse(HttpResponse response)
                      throws ClientProtocolException, IOException {
                    return isAuthenticated();
                  }
                });
        mClient.setRedirectHandler(mRedirectHandler);
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
