package cn.modificator.launcher.reading;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cn.modificator.launcher.Config;

public final class WeReadReadingSource implements ReadingProgressSource {
  private static final String GATEWAY_URL = "https://i.weread.qq.com/api/agent/gateway";
  private static final String SKILL_VERSION = "1.0.3";
  private static final String PREFS = "weread_progress_cache";
  private static final String KEY_CACHE_JSON = "items_json";
  private static final String KEY_CACHE_UPDATED_AT = "updated_at";
  private static final int CONNECT_TIMEOUT_MS = 10000;
  private static final int READ_TIMEOUT_MS = 20000;
  private static final String[] WEREAD_PACKAGES = {
      "com.tencent.weread.eink",
      "com.tencent.weread"
  };

  @Override
  public ReadingProgressResult loadRecent(Context context, int limit) {
    return loadRecent(context, limit, false);
  }

  ReadingProgressResult loadRecent(Context context, int limit, boolean forceRemote) {
    Context appContext = context.getApplicationContext();
    Config config = new Config(appContext);
    if (!config.isWereadSyncEnabled()) {
      return new ReadingProgressResult(Collections.<ReadingProgressItem>emptyList(), null);
    }
    String apiKey = config.getWereadApiKey();
    if (TextUtils.isEmpty(apiKey)) {
      return new ReadingProgressResult(Collections.<ReadingProgressItem>emptyList(),
          "请在阅读同步设置里填写微信读书 Skill Key");
    }

    long intervalMs = Math.max(1, config.getWereadSyncIntervalMinutes()) * 60L * 1000L;
    ReadingProgressResult cached = loadCachedResult(appContext, null);
    long now = System.currentTimeMillis();
    if (!forceRemote && cached != null && now - cached.cacheUpdatedAt < intervalMs) {
      return cached;
    }

    try {
      List<ReadingProgressItem> items = fetchRecent(appContext, apiKey, limit);
      saveCache(appContext, items);
      return new ReadingProgressResult(items, null, false, now, "weread");
    } catch (Exception e) {
      String message = e.getMessage() == null ? e.toString() : e.getMessage();
      ReadingProgressResult stale = loadCachedResult(appContext, "微信读书同步失败：" + message);
      if (stale != null) return stale;
      return new ReadingProgressResult(Collections.<ReadingProgressItem>emptyList(),
          "微信读书同步失败：" + message);
    }
  }

  public static void clearCache(Context context) {
    if (context == null) return;
    context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply();
  }

  private List<ReadingProgressItem> fetchRecent(Context context, String apiKey, int limit)
      throws IOException, JSONException {
    JSONObject shelf = callGateway(apiKey, "/shelf/sync", null);
    JSONArray books = shelf.optJSONArray("books");
    List<BookCandidate> candidates = new ArrayList<>();
    if (books != null) {
      for (int i = 0; i < books.length(); i++) {
        JSONObject obj = books.optJSONObject(i);
        if (obj == null) continue;
        String bookId = obj.optString("bookId", "");
        if (TextUtils.isEmpty(bookId)) continue;
        long readUpdateTime = optLong(obj, "readUpdateTime");
        if (readUpdateTime <= 0) continue;
        candidates.add(new BookCandidate(obj, readUpdateTime));
      }
    }
    Collections.sort(candidates, new Comparator<BookCandidate>() {
      @Override
      public int compare(BookCandidate a, BookCandidate b) {
        return Long.compare(b.readUpdateTime, a.readUpdateTime);
      }
    });

    int max = limit <= 0 ? candidates.size() : Math.min(limit, candidates.size());
    String openPackageName = installedWereadPackage(context);
    List<ReadingProgressItem> items = new ArrayList<>();
    for (int i = 0; i < max; i++) {
      BookCandidate candidate = candidates.get(i);
      JSONObject progress = callGateway(apiKey, "/book/getprogress",
          new JSONObject().put("bookId", candidate.bookId()));
      items.add(normalize(candidate.book, progress, openPackageName));
    }
    return items;
  }

  private ReadingProgressItem normalize(JSONObject shelfBook, JSONObject progress,
      String openPackageName) {
    JSONObject progressBook = progress == null ? null : progress.optJSONObject("book");
    int percent = clampPercent(progressBook == null ? 0 : progressBook.optInt("progress", 0));
    long updateTime = progressBook == null ? 0L : optLong(progressBook, "updateTime");
    long recordReadingTime = progressBook == null ? 0L : optLong(progressBook, "recordReadingTime");
    long readingTime = progressBook == null ? 0L : optLong(progressBook, "readingTime");
    long totalSeconds = recordReadingTime > 0 ? recordReadingTime : Math.max(0L, readingTime);
    int chapterIdx = progressBook == null ? -1 : progressBook.optInt("chapterIdx", -1);
    int chapterUid = progressBook == null ? 0 : progressBook.optInt("chapterUid", 0);

    ReadingProgressItem item = new ReadingProgressItem();
    item.source = ReadingProgressItem.SOURCE_WEREAD;
    item.sourceLabel = "微信读书";
    item.id = shelfBook.optString("bookId", "");
    item.title = shelfBook.optString("title", "");
    item.author = shelfBook.optString("author", "");
    item.subtitle = chapterSubtitle(chapterIdx, chapterUid);
    item.progressPercent = percent;
    item.lastReadTimeSeconds = updateTime > 0 ? updateTime : optLong(shelfBook, "readUpdateTime");
    item.totalReadingSeconds = totalSeconds;
    item.finished = percent >= 100 || shelfBook.optInt("finishReading", 0) == 1;
    item.coverUrl = shelfBook.optString("cover", "");
    item.openPackageName = openPackageName;
    item.openRef = wereadReadingUri(item.id);
    return item;
  }

  static JSONObject callGateway(String apiKey, String apiName, JSONObject params)
      throws IOException, JSONException {
    JSONObject body = new JSONObject();
    body.put("api_name", apiName);
    body.put("skill_version", SKILL_VERSION);
    if (params != null) {
      JSONArray names = params.names();
      if (names != null) {
        for (int i = 0; i < names.length(); i++) {
          String name = names.optString(i);
          body.put(name, params.opt(name));
        }
      }
    }

    HttpURLConnection connection = (HttpURLConnection) new URL(GATEWAY_URL).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
    connection.setReadTimeout(READ_TIMEOUT_MS);
    connection.setUseCaches(false);
    connection.setDoOutput(true);
    connection.setRequestProperty("Authorization", "Bearer " + apiKey);
    connection.setRequestProperty("Content-Type", "application/json");
    byte[] payload = body.toString().getBytes("UTF-8");
    connection.setFixedLengthStreamingMode(payload.length);
    OutputStream out = connection.getOutputStream();
    try {
      out.write(payload);
      out.flush();
    } finally {
      out.close();
    }

    try {
      int code = connection.getResponseCode();
      String response = readResponse(connection, code);
      JSONObject json = new JSONObject(response);
      int errCode = json.optInt("errcode", 0);
      if (code < 200 || code >= 300 || errCode != 0) {
        String message = json.optString("errmsg", "");
        if (TextUtils.isEmpty(message)) message = "HTTP " + code;
        throw new IOException(message);
      }
      return json;
    } finally {
      connection.disconnect();
    }
  }

  private static String readResponse(HttpURLConnection connection, int code) throws IOException {
    InputStream raw = code >= 200 && code < 300
        ? connection.getInputStream()
        : connection.getErrorStream();
    if (raw == null) raw = connection.getInputStream();
    InputStream in = new BufferedInputStream(raw);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try {
      byte[] buffer = new byte[4096];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      return out.toString("UTF-8");
    } finally {
      in.close();
    }
  }

  private void saveCache(Context context, List<ReadingProgressItem> items) {
    JSONArray array = new JSONArray();
    if (items != null) {
      for (ReadingProgressItem item : items) {
        array.put(item.toJson());
      }
    }
    prefs(context).edit()
        .putString(KEY_CACHE_JSON, array.toString())
        .putLong(KEY_CACHE_UPDATED_AT, System.currentTimeMillis())
        .apply();
  }

  private ReadingProgressResult loadCachedResult(Context context, String error) {
    SharedPreferences preferences = prefs(context);
    String json = preferences.getString(KEY_CACHE_JSON, "");
    if (TextUtils.isEmpty(json)) return null;
    try {
      JSONArray array = new JSONArray(json);
      List<ReadingProgressItem> items = new ArrayList<>();
      for (int i = 0; i < array.length(); i++) {
        ReadingProgressItem item = ReadingProgressItem.fromJson(array.optJSONObject(i));
        if (item != null && !TextUtils.isEmpty(item.id)) items.add(item);
      }
      if (items.isEmpty()) return null;
      return new ReadingProgressResult(items, error, true,
          preferences.getLong(KEY_CACHE_UPDATED_AT, 0L), "weread-cache");
    } catch (JSONException e) {
      return null;
    }
  }

  private SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private String installedWereadPackage(Context context) {
    PackageManager pm = context.getPackageManager();
    for (String packageName : WEREAD_PACKAGES) {
      try {
        pm.getPackageInfo(packageName, 0);
        return packageName;
      } catch (PackageManager.NameNotFoundException ignored) {
      }
    }
    return WEREAD_PACKAGES[0];
  }

  private String chapterSubtitle(int chapterIdx, int chapterUid) {
    if (chapterIdx >= 0) return "第 " + (chapterIdx + 1) + " 章";
    if (chapterUid > 0) return "章节 " + chapterUid;
    return "最近阅读";
  }

  private String wereadReadingUri(String bookId) {
    if (TextUtils.isEmpty(bookId)) return "";
    return "weread://reading?bId=" + bookId;
  }

  private static int clampPercent(int value) {
    return Math.max(0, Math.min(100, value));
  }

  private static long optLong(JSONObject obj, String key) {
    if (obj == null || !obj.has(key) || obj.isNull(key)) return 0L;
    Object value = obj.opt(key);
    if (value instanceof Number) return ((Number) value).longValue();
    try {
      return Long.parseLong(String.valueOf(value));
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private static final class BookCandidate {
    final JSONObject book;
    final long readUpdateTime;

    BookCandidate(JSONObject book, long readUpdateTime) {
      this.book = book;
      this.readUpdateTime = readUpdateTime;
    }

    String bookId() {
      return book.optString("bookId", "");
    }
  }
}
