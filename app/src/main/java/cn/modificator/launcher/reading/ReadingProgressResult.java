package cn.modificator.launcher.reading;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ReadingProgressResult {
  public final List<ReadingProgressItem> items;
  public final String error;
  public final boolean fromCache;
  public final long cacheUpdatedAt;
  public final String source;

  public ReadingProgressResult(List<ReadingProgressItem> items, String error) {
    this(items, error, false, 0L, "");
  }

  public ReadingProgressResult(List<ReadingProgressItem> items, String error,
      boolean fromCache, long cacheUpdatedAt, String source) {
    this.items = items == null
        ? Collections.<ReadingProgressItem>emptyList()
        : Collections.unmodifiableList(new ArrayList<>(items));
    this.error = error;
    this.fromCache = fromCache;
    this.cacheUpdatedAt = cacheUpdatedAt;
    this.source = source == null ? "" : source;
  }

  public boolean hasItems() {
    return !items.isEmpty();
  }
}
