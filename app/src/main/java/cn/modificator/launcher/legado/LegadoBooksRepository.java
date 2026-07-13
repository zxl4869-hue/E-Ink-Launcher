package cn.modificator.launcher.legado;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

class LegadoBooksRepository {
  private static final String MODERN_READ_ACTIVITY =
      "io.legado.app.ui.book.read.ReadBookActivity";
  private static final String LEGACY_READ_ACTIVITY =
      "com.kunfei.bookshelf.view.activity.ReadBookActivity";

  private static final String[] LEGADO_PACKAGES = {
      "io.legado.app.release",
      "io.legado.app",
      "io.legado.play.release",
      "io.legado.app.releaseS",
      "com.legado.app.release",
      "com.gedoor.monkeybook",
      "com.gedoor.monkeybook.debug"
  };

  private static final String[] PROVIDER_AUTHORITIES = {
      "io.legado.app.release.readerProvider",
      "io.legado.app.readerProvider",
      "io.legado.play.release.readerProvider",
      "io.legado.app.releaseS.readerProvider",
      "com.legado.app.release.readerProvider"
  };

  private static final int[] WEB_PORTS = {1122, 1234};
  private static final String BOOKSHELF_PATH = "/getBookshelf";
  private static final String CACHE_PREFS = "legado_books_cache";
  private static final String KEY_CACHE_JSON = "books_json";
  private static final String KEY_CACHE_UPDATED_AT = "updated_at";
  private static final int TIMEOUT_MS = 1600;

  static class Result {
    final List<LegadoBook> books;
    final String error;
    final boolean fromCache;
    final long cacheUpdatedAt;
    final String source;

    Result(List<LegadoBook> books, String error) {
      this(books, error, false, 0L, "");
    }

    Result(List<LegadoBook> books, String error, boolean fromCache,
        long cacheUpdatedAt, String source) {
      this.books = books;
      this.error = error;
      this.fromCache = fromCache;
      this.cacheUpdatedAt = cacheUpdatedAt;
      this.source = source;
    }
  }

  Result loadRecentBooks(Context context) {
    Exception lastError = null;

    try {
      List<LegadoBook> books = loadFromProvider(context.getContentResolver());
      saveCache(context, books);
      return new Result(sortAndLimit(books), null, false, System.currentTimeMillis(), "provider");
    } catch (Exception providerError) {
      lastError = providerError;
    }

    try {
      List<LegadoBook> books = loadFromWeb();
      saveCache(context, books);
      return new Result(sortAndLimit(books), null, false, System.currentTimeMillis(), "web");
    } catch (Exception webError) {
      lastError = webError;
    }

    Result cached = loadCachedResult(context);
    if (cached != null) return cached;

    String message = "请在阅读开启 Web 服务，或授予书架接口权限";
    if (lastError != null && !TextUtils.isEmpty(lastError.getMessage())) {
      message += "：" + lastError.getMessage();
    }
    return new Result(Collections.<LegadoBook>emptyList(), message);
  }

  static String installedPackage(Context context) {
    PackageManager pm = context.getPackageManager();
    for (String packageName : LEGADO_PACKAGES) {
      if (isInstalled(pm, packageName)) return packageName;
    }
    return LEGADO_PACKAGES[0];
  }

  static String readActivityForPackage(String packageName) {
    return isLegacyPackage(packageName) ? LEGACY_READ_ACTIVITY : MODERN_READ_ACTIVITY;
  }

  static boolean isLegacyPackage(String packageName) {
    return packageName != null && packageName.startsWith("com.gedoor.monkeybook");
  }

  private static boolean isInstalled(PackageManager pm, String packageName) {
    try {
      pm.getPackageInfo(packageName, 0);
      return true;
    } catch (PackageManager.NameNotFoundException e) {
      return false;
    }
  }

  Bitmap loadCover(LegadoBook book) {
    String url = coverUrl(book);
    if (TextUtils.isEmpty(url)) return null;
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setConnectTimeout(TIMEOUT_MS);
      conn.setReadTimeout(TIMEOUT_MS);
      conn.setUseCaches(false);
      if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
      InputStream in = conn.getInputStream();
      try {
        return BitmapFactory.decodeStream(in);
      } finally {
        in.close();
      }
    } catch (Exception ignored) {
      return null;
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private String coverUrl(LegadoBook book) {
    if (!TextUtils.isEmpty(book.coverUrl)) {
      if (book.coverUrl.startsWith("http://") || book.coverUrl.startsWith("https://")) {
        return book.coverUrl;
      }
      if (!TextUtils.isEmpty(book.apiBaseUrl)) {
        return book.apiBaseUrl + "/cover?path=" + encode(book.coverUrl);
      }
    }
    if (!TextUtils.isEmpty(book.coverPath) && !TextUtils.isEmpty(book.apiBaseUrl)) {
      return book.apiBaseUrl + "/cover?path=" + encode(book.coverPath);
    }
    return null;
  }

  private List<LegadoBook> loadFromWeb() throws IOException, JSONException {
    Exception lastError = null;
    List<LegadoBook> emptyResult = null;
    for (int port : WEB_PORTS) {
      String baseUrl = "http://127.0.0.1:" + port;
      try {
        String body = get(baseUrl + BOOKSHELF_PATH);
        List<LegadoBook> books = parseBooksJson(body, baseUrl);
        if (!books.isEmpty()) return books;
        if (emptyResult == null) emptyResult = books;
      } catch (Exception e) {
        lastError = e;
      }
    }
    if (emptyResult != null) return emptyResult;
    if (lastError instanceof IOException) throw (IOException) lastError;
    if (lastError instanceof JSONException) throw (JSONException) lastError;
    throw new IOException("Web service unavailable");
  }

  private String get(String url) throws IOException {
    HttpURLConnection conn = null;
    try {
      conn = (HttpURLConnection) new URL(url).openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(TIMEOUT_MS);
      conn.setReadTimeout(TIMEOUT_MS);
      conn.setUseCaches(false);
      int code = conn.getResponseCode();
      if (code != HttpURLConnection.HTTP_OK) {
        throw new IOException("HTTP " + code);
      }
      InputStream in = conn.getInputStream();
      try {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
          out.write(buffer, 0, read);
        }
        return out.toString("UTF-8");
      } finally {
        in.close();
      }
    } finally {
      if (conn != null) conn.disconnect();
    }
  }

  private List<LegadoBook> loadFromProvider(ContentResolver resolver) {
    RuntimeException lastError = null;
    List<LegadoBook> emptyResult = null;
    for (String authority : PROVIDER_AUTHORITIES) {
      Uri uri = Uri.parse("content://" + authority + "/books/query");
      try {
        List<LegadoBook> books = loadFromProviderUri(resolver, uri);
        if (!books.isEmpty()) return books;
        if (emptyResult == null) emptyResult = books;
      } catch (RuntimeException e) {
        lastError = e;
      }
    }
    if (emptyResult != null) return emptyResult;
    if (lastError != null) throw lastError;
    throw new IllegalStateException("readerProvider unavailable");
  }

  private List<LegadoBook> loadFromProviderUri(ContentResolver resolver, Uri uri) {
    List<LegadoBook> books = new ArrayList<>();
    Cursor cursor = resolver.query(uri, null, null, null, null);
    if (cursor == null) throw new IllegalStateException("provider not found: " + uri.getAuthority());
    boolean hadRows = false;
    try {
      while (cursor.moveToNext()) {
        hadRows = true;
        String resultJson = getString(cursor, "result");
        if (!TextUtils.isEmpty(resultJson)) {
          books.addAll(parseBooksJsonUnchecked(resultJson, ""));
          continue;
        }
        books.add(fromCursor(cursor));
      }
    } finally {
      cursor.close();
    }
    if (!hadRows) throw new IllegalStateException("provider returned no rows");
    return books;
  }

  private LegadoBook fromCursor(Cursor cursor) {
    LegadoBook book = new LegadoBook();
    book.name = getString(cursor, "name", "bookName");
    book.author = getString(cursor, "author", "bookAuthor");
    book.bookUrl = getString(cursor, "bookUrl", "url", "noteUrl");
    book.coverUrl = getString(cursor, "customCoverUrl", "coverUrl", "cover", "customCoverPath");
    book.coverPath = getString(cursor, "coverPath", "customCoverPath");
    book.durChapterTitle = getString(cursor, "durChapterTitle", "chapterTitle", "durChapterName");
    book.latestChapterTitle = getString(cursor, "latestChapterTitle", "latestChapter", "lastChapterName");
    book.durChapterIndex = getInt(cursor, "durChapterIndex", "chapterIndex", "durChapter");
    book.durChapterPos = getInt(cursor, "durChapterPos", "chapterPos", "durChapterPage");
    book.durChapterTime = getLong(cursor, "durChapterTime", "finalDate", "lastReadTime");
    book.totalChapterNum = getInt(cursor, "totalChapterNum", "chapterTotal", "chapterListSize");
    return book;
  }

  private List<LegadoBook> parseBooksJsonUnchecked(String json, String apiBaseUrl) {
    try {
      return parseBooksJson(json, apiBaseUrl);
    } catch (JSONException e) {
      throw new IllegalStateException(e);
    }
  }

  private List<LegadoBook> parseBooksJson(String json, String apiBaseUrl) throws JSONException {
    List<LegadoBook> books = new ArrayList<>();
    JSONArray array;
    String trimmed = json == null ? "" : json.trim();
    if (trimmed.length() == 0) throw new JSONException("empty response");

    if (trimmed.startsWith("[")) {
      array = new JSONArray(trimmed);
    } else {
      JSONObject root = new JSONObject(trimmed);
      if (root.has("isSuccess") && !root.optBoolean("isSuccess", true)) {
        String error = root.optString("errorMsg", "");
        if (error.contains("还没有添加") || error.contains("书架为空")) {
          return books;
        }
        throw new JSONException(TextUtils.isEmpty(error) ? "bookshelf api failed" : error);
      }
      array = root.optJSONArray("data");
      if (array == null) array = root.optJSONArray("books");
      if (array == null) array = root.optJSONArray("bookShelf");
      if (array == null) throw new JSONException("No books array");
    }

    for (int i = 0; i < array.length(); i++) {
      JSONObject obj = array.optJSONObject(i);
      if (obj == null) continue;
      LegadoBook book = fromJson(obj, apiBaseUrl);
      if (!TextUtils.isEmpty(book.name) || !TextUtils.isEmpty(book.bookUrl)) {
        books.add(book);
      }
    }
    return books;
  }

  private LegadoBook fromJson(JSONObject obj, String apiBaseUrl) {
    JSONObject info = obj.optJSONObject("bookInfoBean");
    LegadoBook book = new LegadoBook();
    book.apiBaseUrl = apiBaseUrl;
    book.name = firstNonEmpty(
        optString(obj, "name", "bookName"),
        optString(info, "name", "bookName"));
    book.author = firstNonEmpty(
        optString(obj, "author", "bookAuthor"),
        optString(info, "author", "bookAuthor"));
    book.bookUrl = firstNonEmpty(
        optString(obj, "bookUrl", "url", "noteUrl"),
        optString(info, "bookUrl", "url", "noteUrl"));
    book.coverUrl = firstNonEmpty(
        optString(obj, "customCoverUrl", "coverUrl", "cover", "customCoverPath"),
        optString(info, "customCoverUrl", "coverUrl", "cover", "customCoverPath"));
    book.coverPath = firstNonEmpty(
        optString(obj, "coverPath", "customCoverPath"),
        optString(info, "coverPath", "customCoverPath"));
    book.durChapterTitle = firstNonEmpty(
        optString(obj, "durChapterTitle", "chapterTitle", "durChapterName"),
        optString(info, "durChapterTitle", "chapterTitle", "durChapterName"));
    book.latestChapterTitle = firstNonEmpty(
        optString(obj, "latestChapterTitle", "latestChapter", "lastChapterName"),
        optString(info, "latestChapterTitle", "latestChapter", "lastChapterName"));
    book.durChapterIndex = optInt(obj, 0, "durChapterIndex", "chapterIndex", "durChapter");
    book.durChapterPos = optInt(obj, 0, "durChapterPos", "chapterPos", "durChapterPage");
    book.durChapterTime = optLong(obj, 0L, "durChapterTime", "finalDate", "lastReadTime");
    book.totalChapterNum = optInt(obj, 0, "totalChapterNum", "chapterTotal", "chapterListSize");
    return book;
  }

  private void saveCache(Context context, List<LegadoBook> books) {
    if (context == null || books == null || books.isEmpty()) return;
    JSONArray array = new JSONArray();
    for (LegadoBook book : books) {
      array.put(toJson(book));
    }
    context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CACHE_JSON, array.toString())
        .putLong(KEY_CACHE_UPDATED_AT, System.currentTimeMillis())
        .apply();
  }

  private Result loadCachedResult(Context context) {
    if (context == null) return null;
    String json = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_CACHE_JSON, "");
    if (TextUtils.isEmpty(json)) return null;
    long updatedAt = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        .getLong(KEY_CACHE_UPDATED_AT, 0L);
    try {
      List<LegadoBook> books = parseBooksJson(json, "");
      if (books.isEmpty()) return null;
      return new Result(sortAndLimit(books), null, true, updatedAt, "cache");
    } catch (JSONException e) {
      return null;
    }
  }

  private JSONObject toJson(LegadoBook book) {
    JSONObject obj = new JSONObject();
    try {
      obj.put("name", safe(book.name));
      obj.put("author", safe(book.author));
      obj.put("bookUrl", safe(book.bookUrl));
      obj.put("coverUrl", safe(book.coverUrl));
      obj.put("coverPath", safe(book.coverPath));
      obj.put("apiBaseUrl", safe(book.apiBaseUrl));
      obj.put("durChapterTitle", safe(book.durChapterTitle));
      obj.put("latestChapterTitle", safe(book.latestChapterTitle));
      obj.put("durChapterIndex", book.durChapterIndex);
      obj.put("durChapterPos", book.durChapterPos);
      obj.put("durChapterTime", book.durChapterTime);
      obj.put("totalChapterNum", book.totalChapterNum);
    } catch (JSONException ignored) {
    }
    return obj;
  }

  private List<LegadoBook> sortAndLimit(List<LegadoBook> books) {
    Collections.sort(books, new Comparator<LegadoBook>() {
      @Override
      public int compare(LegadoBook a, LegadoBook b) {
        return Long.compare(b.durChapterTime, a.durChapterTime);
      }
    });
    return books;
  }

  private static String optString(JSONObject obj, String... keys) {
    if (obj == null || keys == null) return "";
    for (String key : keys) {
      if (obj.has(key) && !obj.isNull(key)) {
        String value = obj.optString(key, "");
        if (!TextUtils.isEmpty(value)) return value;
      }
    }
    return "";
  }

  private static int optInt(JSONObject obj, int defaultValue, String... keys) {
    if (obj == null || keys == null) return defaultValue;
    for (String key : keys) {
      if (!obj.has(key) || obj.isNull(key)) continue;
      Object value = obj.opt(key);
      if (value instanceof Number) return ((Number) value).intValue();
      try {
        return Integer.parseInt(String.valueOf(value));
      } catch (Exception ignored) {
      }
    }
    return defaultValue;
  }

  private static long optLong(JSONObject obj, long defaultValue, String... keys) {
    if (obj == null || keys == null) return defaultValue;
    for (String key : keys) {
      if (!obj.has(key) || obj.isNull(key)) continue;
      Object value = obj.opt(key);
      if (value instanceof Number) return ((Number) value).longValue();
      try {
        return Long.parseLong(String.valueOf(value));
      } catch (Exception ignored) {
      }
    }
    return defaultValue;
  }

  private static String getString(Cursor cursor, String... keys) {
    if (cursor == null || keys == null) return "";
    for (String key : keys) {
      int index = cursor.getColumnIndex(key);
      if (index < 0 || cursor.isNull(index)) continue;
      String value = cursor.getString(index);
      if (!TextUtils.isEmpty(value)) return value;
    }
    return "";
  }

  private static int getInt(Cursor cursor, String... keys) {
    if (cursor == null || keys == null) return 0;
    for (String key : keys) {
      int index = cursor.getColumnIndex(key);
      if (index < 0 || cursor.isNull(index)) continue;
      try {
        return cursor.getInt(index);
      } catch (Exception ignored) {
      }
    }
    return 0;
  }

  private static long getLong(Cursor cursor, String... keys) {
    if (cursor == null || keys == null) return 0L;
    for (String key : keys) {
      int index = cursor.getColumnIndex(key);
      if (index < 0 || cursor.isNull(index)) continue;
      try {
        return cursor.getLong(index);
      } catch (Exception ignored) {
      }
    }
    return 0L;
  }

  private static String firstNonEmpty(String first, String second) {
    return TextUtils.isEmpty(first) ? safe(second) : first;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }

  private static String encode(String value) {
    try {
      return URLEncoder.encode(value, "UTF-8");
    } catch (Exception ignored) {
      return value;
    }
  }
}
