package cn.modificator.launcher.legado;

import android.content.Context;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import cn.modificator.launcher.reading.ReadingProgressItem;
import cn.modificator.launcher.reading.ReadingProgressResult;
import cn.modificator.launcher.reading.ReadingProgressSource;

public final class LegadoReadingSource implements ReadingProgressSource {
  @Override
  public ReadingProgressResult loadRecent(Context context, int limit) {
    LegadoBooksRepository.Result result =
        new LegadoBooksRepository().loadRecentBooks(context.getApplicationContext());
    List<ReadingProgressItem> items = new ArrayList<>();
    int count = 0;
    for (LegadoBook book : result.books) {
      if (limit > 0 && count >= limit) break;
      items.add(fromBook(context, book));
      count++;
    }
    return new ReadingProgressResult(items, result.error, result.fromCache,
        result.cacheUpdatedAt, "legado");
  }

  private ReadingProgressItem fromBook(Context context, LegadoBook book) {
    ReadingProgressItem item = new ReadingProgressItem();
    item.source = ReadingProgressItem.SOURCE_LEGADO;
    item.sourceLabel = "阅读";
    item.id = firstNonEmpty(book.bookUrl, book.name);
    item.title = book.title();
    item.author = book.author;
    item.subtitle = book.chapterText();
    item.progressPercent = book.progressPercent();
    item.lastReadTimeSeconds = normalizeTimestampSeconds(book.durChapterTime);
    item.totalReadingSeconds = 0L;
    item.finished = item.progressPercent >= 100;
    item.coverUrl = firstNonEmpty(book.coverUrl, book.coverPath);
    item.openPackageName = LegadoBooksRepository.installedPackage(context);
    item.openRef = book.bookUrl;
    return item;
  }

  private long normalizeTimestampSeconds(long value) {
    if (value > 100000000000L) return value / 1000L;
    return Math.max(0L, value);
  }

  private String firstNonEmpty(String first, String second) {
    if (!TextUtils.isEmpty(first)) return first;
    return second == null ? "" : second;
  }
}
