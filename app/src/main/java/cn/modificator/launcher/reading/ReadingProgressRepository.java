package cn.modificator.launcher.reading;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import cn.modificator.launcher.Config;
import cn.modificator.launcher.legado.LegadoReadingSource;

public final class ReadingProgressRepository {
  private static final int DEFAULT_SOURCE_LIMIT = 6;
  private static final String PREFS = "reading_progress_state";
  private static final String KEY_NEEDS_REFRESH = "needs_refresh";
  private static final String KEY_REFRESH_REASON = "refresh_reason";
  private static final String KEY_REFRESH_MARKED_AT = "refresh_marked_at";
  private static final String[] READER_PACKAGES = {
      "io.legado.app.release",
      "io.legado.app",
      "io.legado.play.release",
      "io.legado.app.releaseS",
      "com.legado.app.release",
      "com.gedoor.monkeybook",
      "com.gedoor.monkeybook.debug",
      "com.tencent.weread.eink",
      "com.tencent.weread"
  };

  public ReadingProgressResult loadRecent(Context context) {
    return loadRecent(context, DEFAULT_SOURCE_LIMIT);
  }

  public ReadingProgressResult refreshNow(Context context) {
    return refreshNow(context, DEFAULT_SOURCE_LIMIT);
  }

  public ReadingProgressResult refreshNow(Context context, int limitPerSource) {
    ReadingProgressResult result = loadRecent(context, limitPerSource, true);
    if (result.hasItems()) {
      clearRefreshNeeded(context);
    }
    return result;
  }

  public ReadingProgressResult loadRecent(Context context, int limitPerSource) {
    boolean forceRemote = isRefreshNeeded(context);
    ReadingProgressResult result = loadRecent(context, limitPerSource, forceRemote);
    if (forceRemote && result.hasItems()) {
      clearRefreshNeeded(context);
    }
    return result;
  }

  private ReadingProgressResult loadRecent(Context context, int limitPerSource,
      boolean forceRemote) {
    List<ReadingProgressItem> items = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    boolean anyCache = false;
    long latestCache = 0L;

    List<ReadingProgressSource> sources = new ArrayList<>();
    sources.add(new LegadoReadingSource());
    Config config = new Config(context);
    if (config.isWereadSyncEnabled() && !TextUtils.isEmpty(config.getWereadApiKey())) {
      sources.add(new WeReadReadingSource());
    }

    for (ReadingProgressSource source : sources) {
      try {
        ReadingProgressResult result;
        if (source instanceof WeReadReadingSource) {
          result = ((WeReadReadingSource) source).loadRecent(context, limitPerSource, forceRemote);
        } else {
          result = source.loadRecent(context, limitPerSource);
        }
        if (result == null) continue;
        items.addAll(result.items);
        if (!TextUtils.isEmpty(result.error)) errors.add(result.error);
        anyCache |= result.fromCache;
        latestCache = Math.max(latestCache, result.cacheUpdatedAt);
      } catch (Exception e) {
        String message = e.getMessage();
        errors.add(TextUtils.isEmpty(message) ? e.toString() : message);
      }
    }

    sortAndDeduplicate(items);
    return new ReadingProgressResult(items, joinErrors(errors), anyCache, latestCache, "merged");
  }

  public static void clearRemoteCache(Context context) {
    WeReadReadingSource.clearCache(context);
  }

  public static void markRefreshNeeded(Context context, String reason) {
    if (context == null) return;
    prefs(context).edit()
        .putBoolean(KEY_NEEDS_REFRESH, true)
        .putString(KEY_REFRESH_REASON, reason == null ? "" : reason)
        .putLong(KEY_REFRESH_MARKED_AT, System.currentTimeMillis())
        .apply();
  }

  public static boolean isRefreshNeeded(Context context) {
    return context != null && prefs(context).getBoolean(KEY_NEEDS_REFRESH, false);
  }

  public static void clearRefreshNeeded(Context context) {
    if (context == null) return;
    prefs(context).edit()
        .remove(KEY_NEEDS_REFRESH)
        .remove(KEY_REFRESH_REASON)
        .remove(KEY_REFRESH_MARKED_AT)
        .apply();
  }

  public static boolean isKnownReaderPackage(String packageName) {
    if (TextUtils.isEmpty(packageName)) return false;
    for (String readerPackage : READER_PACKAGES) {
      if (packageName.equals(readerPackage)) return true;
    }
    return false;
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }

  private static void sortAndDeduplicate(List<ReadingProgressItem> items) {
    Collections.sort(items, new Comparator<ReadingProgressItem>() {
      @Override
      public int compare(ReadingProgressItem a, ReadingProgressItem b) {
        return Long.compare(b.lastReadTimeSeconds, a.lastReadTimeSeconds);
      }
    });
    Set<String> seen = new LinkedHashSet<>();
    for (int i = 0; i < items.size(); i++) {
      ReadingProgressItem item = items.get(i);
      String key = item.source + ":" + item.id;
      if (seen.contains(key)) {
        items.remove(i);
        i--;
      } else {
        seen.add(key);
      }
    }
  }

  private static String joinErrors(List<String> errors) {
    StringBuilder builder = new StringBuilder();
    for (String error : errors) {
      if (TextUtils.isEmpty(error)) continue;
      if (builder.length() > 0) builder.append("；");
      builder.append(error);
    }
    return builder.length() == 0 ? null : builder.toString();
  }
}
