package cn.modificator.launcher.reading;

import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

public final class ReadingProgressItem {
  public static final String SOURCE_LEGADO = "legado";
  public static final String SOURCE_WEREAD = "weread";

  public String source;
  public String sourceLabel;
  public String id;
  public String title;
  public String author;
  public String subtitle;
  public int progressPercent;
  public long lastReadTimeSeconds;
  public long totalReadingSeconds;
  public boolean finished;
  public String coverUrl;
  public String openPackageName;
  public String openRef;

  public String title() {
    return TextUtils.isEmpty(title) ? "未命名书籍" : title;
  }

  public String subtitle() {
    return TextUtils.isEmpty(subtitle) ? sourceLabel() : subtitle;
  }

  public String sourceLabel() {
    return TextUtils.isEmpty(sourceLabel) ? source : sourceLabel;
  }

  public int progressPercent() {
    return Math.max(0, Math.min(100, progressPercent));
  }

  public JSONObject toJson() {
    JSONObject obj = new JSONObject();
    try {
      obj.put("source", safe(source));
      obj.put("sourceLabel", safe(sourceLabel));
      obj.put("id", safe(id));
      obj.put("title", safe(title));
      obj.put("author", safe(author));
      obj.put("subtitle", safe(subtitle));
      obj.put("progressPercent", progressPercent());
      obj.put("lastReadTimeSeconds", lastReadTimeSeconds);
      obj.put("totalReadingSeconds", totalReadingSeconds);
      obj.put("finished", finished);
      obj.put("coverUrl", safe(coverUrl));
      obj.put("openPackageName", safe(openPackageName));
      obj.put("openRef", safe(openRef));
    } catch (JSONException ignored) {
    }
    return obj;
  }

  public static ReadingProgressItem fromJson(JSONObject obj) {
    if (obj == null) return null;
    ReadingProgressItem item = new ReadingProgressItem();
    item.source = obj.optString("source", "");
    item.sourceLabel = obj.optString("sourceLabel", "");
    item.id = obj.optString("id", "");
    item.title = obj.optString("title", "");
    item.author = obj.optString("author", "");
    item.subtitle = obj.optString("subtitle", "");
    item.progressPercent = obj.optInt("progressPercent", 0);
    item.lastReadTimeSeconds = obj.optLong("lastReadTimeSeconds", 0L);
    item.totalReadingSeconds = obj.optLong("totalReadingSeconds", 0L);
    item.finished = obj.optBoolean("finished", false);
    item.coverUrl = obj.optString("coverUrl", "");
    item.openPackageName = obj.optString("openPackageName", "");
    item.openRef = obj.optString("openRef", "");
    return item;
  }

  private static String safe(String value) {
    return value == null ? "" : value;
  }
}
