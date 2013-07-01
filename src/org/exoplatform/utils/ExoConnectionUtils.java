package org.exoplatform.utils;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

//import org.apache.commons.codec.binary.Base64;
import android.util.Log;
import org.apache.http.*;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.exoplatform.singleton.AccountSetting;
import org.exoplatform.singleton.DocumentHelper;
import org.exoplatform.singleton.ServerSettingHelper;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

//interact with server
public class ExoConnectionUtils {

  public static final int         LOGIN_WRONG              = 0;

  public static final int         LOGIN_SUSCESS            = 1;

  public static final int         LOGIN_UNAUTHORIZED       = 2;

  public static final int         LOGIN_INVALID            = 3;

  public static final int         LOGIN_FAILED             = 4;

  public static final int         LOGIN_INCOMPATIBLE       = 5;

  // Default connection and socket timeout of 60 seconds. Tweak to taste.
  private static final int        SOCKET_OPERATION_TIMEOUT = 30 * 1000;

  public static DefaultHttpClient httpClient;

  public static CookieStore       cookiesStore;

  public static final int         SIGNUP_OK                = 10;

  /* internal server problem, strange response status code */
  public static final int         SIGNUP_INVALID           = 11;

  /* domain for the email is invalid, such as gmail, yahoo ... */
  public static final int         SIGNUP_WRONG_DOMAIN      = 12;

  /* an account already exists for this email */
  public static final int         SIGNUP_ACCOUNT_EXISTS    = 13;

  public static final int         SIGNIN_OK                = 20;

  public static final int         SIGNIN_INVALID           = 21;

  public static final int         SIGNIN_NO_ACCOUNT        = 22;

  public static final int         SIGNIN_NO_TENANT_FOR_EMAIL  = 23;

  public static final int         SIGNIN_SERVER_NOT_AVAILABLE = 24;



  private static final String    TAG                       = "ExoConnectionUtils";

  /*
   * Check mobile network and wireless status
   */
  public static boolean isNetworkAvailableExt(Context paramContext) {
    ConnectivityManager localConnectivityManager = (ConnectivityManager) paramContext.getSystemService("connectivity");
    if (localConnectivityManager == null) {
      return false;
    }
    while (true) {
      //
      NetworkInfo localNetworkInfo = localConnectivityManager.getActiveNetworkInfo();
      if ((localNetworkInfo == null)
          || (localNetworkInfo.getState() != NetworkInfo.State.CONNECTED))
        return false;
      if (localNetworkInfo.getType() == 1) {
        return true;
      }
      if (localNetworkInfo.getType() == 0) {
        return true;
      }
      return true;
    }
  }

  // Convert stream to String
  public static String convertStreamToString(InputStream is) {
    BufferedReader reader = new BufferedReader(new InputStreamReader(is));
    StringBuilder sb = new StringBuilder();

    String line = null;
    try {
      while ((line = reader.readLine()) != null) {
        sb.append(line + "\n");
      }
    } catch (IOException e) {
      return null;
    } finally {
      try {
        is.close();
      } catch (IOException e) {
        return null;
      }
    }
    return sb.toString();
  }

  /*
   * check session timeout
   */

  public static int checkTimeout(String url) {
    HttpGet httpGet = new HttpGet(url);
    try {
      if (httpClient == null) {
        httpClient = initHttpClient();
      }
      HttpResponse response = httpClient.execute(httpGet);
      int statusCode = checkPlatformRespose(response);
      if (statusCode == LOGIN_SUSCESS) {
        return LOGIN_SUSCESS;
      } else {
        String username = AccountSetting.getInstance().getUsername();
        String password = AccountSetting.getInstance().getPassword();
        StringBuilder buffer = new StringBuilder(username);
        buffer.append(":");
        buffer.append(password);
        httpGet.setHeader("Authorization",
                          "Basic " + Base64.encodeBytes(buffer.toString().getBytes()));
        response = httpClient.execute(httpGet);
        cookiesStore = httpClient.getCookieStore();
        AccountSetting.getInstance().cookiesList = getCookieList(cookiesStore);
        return checkPlatformRespose(response);
      }

    } catch (IOException e) {
      return LOGIN_WRONG;
    }

  }

  public static DefaultHttpClient initHttpClient() {
    HttpParams httpParameters = new BasicHttpParams();
    HttpConnectionParams.setConnectionTimeout(httpParameters, SOCKET_OPERATION_TIMEOUT);
    HttpConnectionParams.setSoTimeout(httpParameters, SOCKET_OPERATION_TIMEOUT);
    HttpConnectionParams.setTcpNoDelay(httpParameters, true);

    return new DefaultHttpClient(httpParameters);
  }

  public static HttpResponse getRequestResponse(String strUrlRequest) throws IOException {
    HttpGet httpGet = new HttpGet(strUrlRequest);
    if (httpClient == null) {
      httpClient = initHttpClient();
    }

    HttpResponse response = httpClient.execute(httpGet);
    return response;
  }

  // Get input stream from url
  public static InputStream sendRequest(HttpResponse response) {
    try {
      HttpEntity entity;
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode >= HttpStatus.SC_OK && statusCode < HttpStatus.SC_MULTIPLE_CHOICES) {
        entity = response.getEntity();
        if (entity != null) {
          return entity.getContent();
        }
      } else {
        return null;
      }

    } catch (IOException e) {
      return null;
    }
    return null;
  }

  /*
   * Get response from platform url
   */

  public static HttpResponse getPlatformResponse(String username,
                                                 String password,
                                                 String strUrlRequest) throws IOException {
    if (httpClient == null) {
      httpClient = initHttpClient();
    }
    StringBuilder buffer = new StringBuilder(username);
    buffer.append(":");
    buffer.append(password);
    HttpGet httpGet = new HttpGet(strUrlRequest);
    httpGet.setHeader("Authorization", "Basic " + Base64.encodeBytes(buffer.toString().getBytes()));
    HttpResponse response = httpClient.execute(httpGet);
    cookiesStore = httpClient.getCookieStore();
    AccountSetting.getInstance().cookiesList = getCookieList(cookiesStore);

    return response;

  }

  /* for signup: validate email according rules of eXo cloud */
  public static boolean validateEmail(String aEmailAddress) {
    if (aEmailAddress == null) return false;
    boolean result = true;
    if (!hasNameAndDomain(aEmailAddress)) {
      result = false;
    }
    return result;
  }

  private static boolean hasNameAndDomain(String aEmailAddress) {
    String[] tokens = aEmailAddress.split("@");
    return tokens.length == 2 && tokens[0].trim().length() > 0 && tokens[1].trim().length() > 0
        && tokens[1].split("\\.").length > 1;
  }

  /**
   * Make a Sign up request to eXo cloud
   *
   * @param email
   */
  public static HttpResponse makeCloudSignUpRequest(String email) throws IOException {
    if (httpClient == null) {
      httpClient = initHttpClient();
    }

    HttpPost httpPost = new HttpPost("http://cloud-workspaces.com/rest/cloud-admin/cloudworkspaces/tenant-service/signup");
    List<NameValuePair> requestParameters = new ArrayList<NameValuePair>(1);
    requestParameters.add(new BasicNameValuePair("user-mail", email));
    httpPost.setEntity(new UrlEncodedFormEntity(requestParameters));
    return httpClient.execute(httpPost);
  }

  public static int checkSignUpResponse(HttpResponse response) {
    int statusCode = response.getStatusLine().getStatusCode();
    /* code 309 */
    if (statusCode == ExoConstants.UNKNOWN) {

      Log.i(TAG, "Location header: " + response.getLastHeader("Location").getValue());

      if (response.getLastHeader("Location").getValue().contains("tryagain.jsp"))
        return ExoConnectionUtils.SIGNUP_WRONG_DOMAIN;
      else return ExoConnectionUtils.SIGNUP_ACCOUNT_EXISTS;
    }

    if (statusCode != HttpStatus.SC_OK)
      return ExoConnectionUtils.SIGNUP_INVALID;

    /* code 200 */
    return ExoConnectionUtils.SIGNUP_OK;
  }

  public static HttpResponse requestTenantForEmail(String email) throws IOException {
    return getRequestResponse("http://cloud-workspaces.com/rest/cloud-admin/cloudworkspaces/tenant-service/usermailinfo/" + email);
  }

  public static String[] checkRequestTenant(HttpResponse response) {

    String[] results = new String[2];
    if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) return null;

    try {
      String result   = getPLFStream(response);
      Log.i(TAG, "result of request: " + result);
      JSONObject json = (JSONObject) JSONValue.parse(result);
      results[0]      = json.get(ExoConstants.USERNAME).toString();
      results[1]      = json.get(ExoConstants.TENANT).toString();
      Log.i(TAG, "user:   " + results[0]);
      Log.i(TAG, "tenant: " + results[1]);
      return results;
    } catch (RuntimeException e) {
      return null;
    }
  }


  public static boolean requestAccountExistsForUser(String user, String tenant) {
    try {
      HttpResponse response = getRequestResponse("http://cloud-workspaces.com/rest/cloud-admin/cloudworkspaces/tenant-service/isuserexist/"
          + tenant + "/" + user);
      if (response.getStatusLine().getStatusCode() != HttpStatus.SC_OK) return false;
      return convertStreamToString(response.getEntity().getContent())
          .replace("\n", "").replace("\r", "").replace("\r\n", "")
          .equalsIgnoreCase("true");
    } catch (IOException e) {
      return false;
    }
  }

  /*
   * Checking the response status code
   */

  public static int checkPlatformRespose(HttpResponse response) {
    int statusCode = response.getStatusLine().getStatusCode();
    if (statusCode >= HttpStatus.SC_OK && statusCode < HttpStatus.SC_MULTIPLE_CHOICES) {
      return LOGIN_SUSCESS;
    } else if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
      return LOGIN_UNAUTHORIZED;
    } else if (statusCode == HttpStatus.SC_NOT_FOUND) {
      return LOGIN_INVALID;
    } else
      return LOGIN_FAILED;

  }

  // get input stream from URL without authentication
  public static InputStream sendRequestWithoutAuthen(HttpResponse response) {
    InputStream ipstr = null;
    try {
      HttpEntity entity;
      entity = response.getEntity();
      if (entity != null) {
        ipstr = entity.getContent();
      }
    } catch (ClientProtocolException e) {
      e.getMessage();
    } catch (IOException e) {
      e.getMessage();
    }
    return ipstr;
  }

  // Get string input stream from URL
  public static String sendRequestAndReturnString(HttpResponse response) {
    return convertStreamToString(sendRequest(response));
  }

  // get the JSONObject string of PLF
  private static String getPLFStream(HttpResponse response) {
    return convertStreamToString(sendRequestWithoutAuthen(response));
  }

  /*
   * Check the version of PLF is mobile compatible or not
   */
  public static boolean checkPLFVersion(HttpResponse response) {
    try {

      String result = getPLFStream(response);
      JSONObject json = (JSONObject) JSONValue.parse(result);

      String isComplicant = json.get(ExoConstants.IS_MOBILE_COMPLIANT).toString();
      if ("true".equalsIgnoreCase(isComplicant)) {
        String editionObject = json.get(ExoConstants.PLATFORM_EDITION).toString();
        ServerSettingHelper.getInstance().setServerEdition(editionObject);
        String verObject = json.get(ExoConstants.PLATFORM_VERSION).toString();
        ServerSettingHelper.getInstance().setServerVersion(verObject);

        /*
         * Get repository name
         */
        String repository = ExoConstants.DOCUMENT_REPOSITORY;
        if (json.containsKey(ExoConstants.PLATFORM_CURRENT_REPO_NAME)) {
          repository = json.get(ExoConstants.PLATFORM_CURRENT_REPO_NAME).toString();
          if (repository == null || "".equals(repository.trim())) {
            repository = ExoConstants.DOCUMENT_REPOSITORY;
          }
        }
        DocumentHelper.getInstance().repository = repository;
        return true;
      } else
        return false;
    } catch (RuntimeException e) {
      return false;
    }

  }

  public static ArrayList<String> getCookieList(CookieStore cookieStore) {
    ArrayList<String> cookieList = new ArrayList<String>();
    List<Cookie> cookies = cookieStore.getCookies();
    String strCookie = "";
    if (!cookies.isEmpty()) {
      for (int i = 0; i < cookies.size(); i++) {
        strCookie = cookies.get(i).getName().toString() + "="
            + cookies.get(i).getValue().toString();
        cookieList.add(strCookie);
      }
    }
    return cookieList;
  }

  public static void setCookieStore(CookieStore cookieStore, ArrayList<String> list) {
    cookieStore = new BasicCookieStore();
    for (String cookieStr : list) {
      String[] keyValue = cookieStr.split("=");
      String key = keyValue[0];
      String value = "";
      if (keyValue.length > 1)
        value = keyValue[1];
      cookieStore.addCookie(new BasicClientCookie(key, value));
    }
  }

}
