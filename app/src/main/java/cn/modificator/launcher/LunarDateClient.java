package cn.modificator.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;

final class LunarDateClient {
  private static final String TAG = "LunarDateClient";
  private static final String PREFS = "lunarDateCache";
  private static final String KEY_DATE = "date";
  private static final String KEY_TEXT = "text";
  private static final String KEY_UPDATED_AT = "updatedAt";
  private static final String API_URL =
      "https://data.weather.gov.hk/weatherAPI/opendata/lunardate.php?date=";
  private static final int CONNECT_TIMEOUT_MS = 6000;
  private static final int READ_TIMEOUT_MS = 8000;

  private LunarDateClient() {
  }

  static String getCached(Context context, String solarDate) {
    if (context == null || TextUtils.isEmpty(solarDate)) return null;
    SharedPreferences prefs = prefs(context);
    if (!solarDate.equals(prefs.getString(KEY_DATE, ""))) return null;
    String text = prefs.getString(KEY_TEXT, "");
    return TextUtils.isEmpty(text) ? null : text;
  }

  static String fetchAndCache(Context context, String solarDate) throws IOException {
    if (context == null || TextUtils.isEmpty(solarDate)) {
      throw new IOException("missing date");
    }
    String body = readUrl(API_URL + URLEncoder.encode(solarDate, "UTF-8"));
    JSONObject json;
    try {
      json = new JSONObject(body);
    } catch (Exception e) {
      throw new IOException("invalid lunar json", e);
    }
    String lunarDate = json.optString("LunarDate", "");
    if (TextUtils.isEmpty(lunarDate)) {
      throw new IOException("missing LunarDate");
    }
    String text = formatLunarDate(lunarDate);
    prefs(context).edit()
        .putString(KEY_DATE, solarDate)
        .putString(KEY_TEXT, text)
        .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
        .apply();
    Log.i(TAG, "fetched " + solarDate + " -> " + text);
    return text;
  }

  private static String formatLunarDate(String raw) {
    String text = raw == null ? "" : raw.trim();
    text = text.replace("農曆", "")
        .replace("农历", "")
        .replace("閏", "闰")
        .replace("臘", "腊")
        .trim();
    if (TextUtils.isEmpty(text)) return "";
    return "农历" + text;
  }

  private static String readUrl(String url) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setInstanceFollowRedirects(true);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("User-Agent", "E-Ink-Launcher");
    try {
      int code = connection.getResponseCode();
      if (code < 200 || code >= 300) {
        throw new IOException("HTTP " + code);
      }
      InputStream in = new BufferedInputStream(connection.getInputStream());
      try {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
      } finally {
        try {
          in.close();
        } catch (IOException ignored) {
        }
      }
    } finally {
      connection.disconnect();
    }
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}
