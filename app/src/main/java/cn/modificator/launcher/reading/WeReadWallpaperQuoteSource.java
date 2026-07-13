package cn.modificator.launcher.reading;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import cn.modificator.launcher.Config;

public final class WeReadWallpaperQuoteSource {
  private static final String PREFS = "weread_wallpaper_quote_cache";
  private static final String KEY_QUOTE_JSON = "quote_json";
  private static final String KEY_QUOTES_JSON = "quotes_json";
  private static final int MAX_BOOK_CANDIDATES = 4;
  private static final int MAX_QUOTE_CANDIDATES = 24;

  private WeReadWallpaperQuoteSource() {
  }

  public static Quote getDailyQuote(Context context, Config config, boolean allowRemote) {
    List<Quote> quotes = getDailyQuotes(context, config, allowRemote, 1);
    return quotes.isEmpty() ? fallbackQuote() : quotes.get(0);
  }

  public static List<Quote> getDailyQuotes(Context context, Config config, boolean allowRemote,
      int count) {
    if (context == null) return Collections.singletonList(fallbackQuote());
    Context appContext = context.getApplicationContext();
    long today = localDayBucket();
    List<Quote> cached = loadCachedQuotes(appContext);
    if (!allowRemote && !cached.isEmpty()) {
      return limitQuotes(cached, count);
    }
    if (!cached.isEmpty() && cached.get(0).dayBucket == today
        && (!allowRemote || cached.size() >= Math.max(1, count))) {
      return limitQuotes(cached, count);
    }
    if (!allowRemote) {
      return cached.isEmpty()
          ? Collections.singletonList(fallbackQuote())
          : limitQuotes(cached, count);
    }

    if (config == null) config = new Config(appContext);
    if (!config.isWereadSyncEnabled() || TextUtils.isEmpty(config.getWereadApiKey())) {
      return cached.isEmpty()
          ? Collections.singletonList(missingSkillQuote())
          : limitQuotes(cached, count);
    }

    try {
      List<Quote> quotes = fetchSquareHighlights(config.getWereadApiKey());
      if (!quotes.isEmpty()) {
        int index = stableDailyIndex(today, config.getWereadApiKey(), quotes.size());
        List<Quote> selected = selectDailyQuotes(quotes, index, Math.max(1, count));
        long now = System.currentTimeMillis();
        for (Quote quote : selected) {
          quote.dayBucket = today;
          quote.updatedAt = now;
        }
        saveCachedQuotes(appContext, selected);
        return selected;
      }
    } catch (Exception ignored) {
    }
    return cached.isEmpty()
        ? Collections.singletonList(fallbackQuote())
        : limitQuotes(cached, count);
  }

  public static void clearCache(Context context) {
    if (context == null) return;
    context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit()
        .clear()
        .apply();
  }

  private static List<Quote> fetchSquareHighlights(String apiKey)
      throws JSONException, java.io.IOException {
    JSONObject shelf = WeReadReadingSource.callGateway(apiKey, "/shelf/sync", null);
    JSONArray books = shelf.optJSONArray("books");
    List<BookCandidate> candidates = new ArrayList<>();
    if (books != null) {
      for (int i = 0; i < books.length(); i++) {
        JSONObject obj = books.optJSONObject(i);
        if (obj == null) continue;
        String bookId = obj.optString("bookId", "");
        if (TextUtils.isEmpty(bookId)) continue;
        long sort = optLong(obj, "readUpdateTime");
        candidates.add(new BookCandidate(bookId, obj.optString("title", ""),
            obj.optString("author", ""), sort));
      }
    }
    Collections.sort(candidates, new Comparator<BookCandidate>() {
      @Override
      public int compare(BookCandidate a, BookCandidate b) {
        return Long.compare(b.sort, a.sort);
      }
    });

    List<Quote> quotes = new ArrayList<>();
    int max = Math.min(MAX_BOOK_CANDIDATES, candidates.size());
    for (int i = 0; i < max && quotes.size() < MAX_QUOTE_CANDIDATES; i++) {
      collectBestBookmarks(apiKey, candidates.get(i), quotes);
    }
    if (quotes.isEmpty()) {
      for (int i = 0; i < max && quotes.size() < MAX_QUOTE_CANDIDATES; i++) {
        collectPersonalBookmarks(apiKey, candidates.get(i), quotes);
      }
    }
    return quotes;
  }

  private static void collectBestBookmarks(String apiKey, BookCandidate book, List<Quote> quotes)
      throws JSONException, java.io.IOException {
    JSONObject params = new JSONObject().put("bookId", book.bookId);
    JSONObject response = WeReadReadingSource.callGateway(apiKey, "/book/bestbookmarks", params);
    JSONArray items = response.optJSONArray("items");
    if (items == null) return;
    for (int i = 0; i < items.length() && quotes.size() < MAX_QUOTE_CANDIDATES; i++) {
      JSONObject item = items.optJSONObject(i);
      if (item == null) continue;
      Quote quote = quoteFromText(item.optString("markText", ""), book);
      if (quote != null) quotes.add(quote);
    }
  }

  private static void collectPersonalBookmarks(String apiKey, BookCandidate book,
      List<Quote> quotes) throws JSONException, java.io.IOException {
    JSONObject params = new JSONObject().put("bookId", book.bookId);
    JSONObject response = WeReadReadingSource.callGateway(apiKey, "/book/bookmarklist", params);
    JSONArray items = response.optJSONArray("updated");
    if (items == null) return;
    for (int i = 0; i < items.length() && quotes.size() < MAX_QUOTE_CANDIDATES; i++) {
      JSONObject item = items.optJSONObject(i);
      if (item == null) continue;
      Quote quote = quoteFromText(item.optString("markText", ""), book);
      if (quote != null) quotes.add(quote);
    }
  }

  private static Quote quoteFromText(String text, BookCandidate book) {
    String normalized = normalizeText(text);
    if (TextUtils.isEmpty(normalized)) return null;
    Quote quote = new Quote();
    quote.text = normalized;
    quote.title = TextUtils.isEmpty(book.title) ? "微信读书" : book.title;
    quote.author = book.author == null ? "" : book.author;
    quote.source = "微信读书广场划线";
    return quote;
  }

  private static String normalizeText(String text) {
    if (text == null) return "";
    String normalized = text.replace('\r', ' ')
        .replace('\n', ' ')
        .replace('\t', ' ')
        .trim();
    while (normalized.contains("  ")) {
      normalized = normalized.replace("  ", " ");
    }
    if (normalized.length() > 180) {
      normalized = normalized.substring(0, 180).trim() + "……";
    }
    return normalized;
  }

  private static int stableDailyIndex(long dayBucket, String apiKey, int size) {
    if (size <= 0) return 0;
    long seed = dayBucket * 1103515245L + (apiKey == null ? 0 : apiKey.hashCode());
    long mod = seed % size;
    if (mod < 0) mod += size;
    return (int) mod;
  }

  private static Quote missingSkillQuote() {
    Quote quote = new Quote();
    quote.text = "请先在阅读同步中开启微信读书同步，并填写微信读书 Skill Key。";
    quote.title = "微信读书";
    quote.author = "";
    quote.source = "未配置";
    quote.dayBucket = localDayBucket();
    quote.updatedAt = System.currentTimeMillis();
    return quote;
  }

  private static Quote fallbackQuote() {
    Quote quote = new Quote();
    quote.text = "今日还没有同步到微信读书划线。";
    quote.title = "微信读书";
    quote.author = "";
    quote.source = "本地占位";
    quote.dayBucket = localDayBucket();
    quote.updatedAt = System.currentTimeMillis();
    return quote;
  }

  private static List<Quote> loadCachedQuotes(Context context) {
    String json = prefs(context).getString(KEY_QUOTES_JSON, "");
    if (!TextUtils.isEmpty(json)) {
      try {
        JSONArray array = new JSONArray(json);
        List<Quote> quotes = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
          Quote quote = Quote.fromJson(array.optJSONObject(i));
          if (quote != null) quotes.add(quote);
        }
        if (!quotes.isEmpty()) return quotes;
      } catch (JSONException ignored) {
      }
    }
    json = prefs(context).getString(KEY_QUOTE_JSON, "");
    if (TextUtils.isEmpty(json)) return Collections.emptyList();
    try {
      Quote quote = Quote.fromJson(new JSONObject(json));
      return quote == null ? Collections.<Quote>emptyList() : Collections.singletonList(quote);
    } catch (JSONException e) {
      return Collections.emptyList();
    }
  }

  private static void saveCachedQuotes(Context context, List<Quote> quotes) {
    if (quotes == null || quotes.isEmpty()) return;
    JSONArray array = new JSONArray();
    for (Quote quote : quotes) {
      if (quote != null && !TextUtils.isEmpty(quote.text)) array.put(quote.toJson());
    }
    if (array.length() == 0) return;
    prefs(context).edit()
        .putString(KEY_QUOTES_JSON, array.toString())
        .putString(KEY_QUOTE_JSON, array.optJSONObject(0).toString())
        .apply();
  }

  private static List<Quote> selectDailyQuotes(List<Quote> quotes, int start, int count) {
    List<Quote> selected = new ArrayList<>();
    if (quotes == null || quotes.isEmpty()) return selected;
    int max = Math.min(count, quotes.size());
    for (int i = 0; i < max; i++) {
      selected.add(quotes.get((start + i) % quotes.size()));
    }
    return selected;
  }

  private static List<Quote> limitQuotes(List<Quote> quotes, int count) {
    if (quotes == null || quotes.isEmpty()) return Collections.emptyList();
    int max = Math.min(Math.max(1, count), quotes.size());
    return new ArrayList<>(quotes.subList(0, max));
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private static long localDayBucket() {
    Calendar calendar = Calendar.getInstance();
    return calendar.get(Calendar.YEAR) * 1000L + calendar.get(Calendar.DAY_OF_YEAR);
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
    final String bookId;
    final String title;
    final String author;
    final long sort;

    BookCandidate(String bookId, String title, String author, long sort) {
      this.bookId = bookId;
      this.title = title;
      this.author = author;
      this.sort = sort;
    }
  }

  public static final class Quote {
    public String text;
    public String title;
    public String author;
    public String source;
    public long dayBucket;
    public long updatedAt;

    JSONObject toJson() {
      JSONObject obj = new JSONObject();
      try {
        obj.put("text", text == null ? "" : text);
        obj.put("title", title == null ? "" : title);
        obj.put("author", author == null ? "" : author);
        obj.put("source", source == null ? "" : source);
        obj.put("dayBucket", dayBucket);
        obj.put("updatedAt", updatedAt);
      } catch (JSONException ignored) {
      }
      return obj;
    }

    static Quote fromJson(JSONObject obj) {
      if (obj == null) return null;
      Quote quote = new Quote();
      quote.text = obj.optString("text", "");
      quote.title = obj.optString("title", "");
      quote.author = obj.optString("author", "");
      quote.source = obj.optString("source", "");
      quote.dayBucket = obj.optLong("dayBucket", 0L);
      quote.updatedAt = obj.optLong("updatedAt", 0L);
      return TextUtils.isEmpty(quote.text) ? null : quote;
    }
  }
}
