package cn.modificator.launcher.reading;

import android.content.Context;

public interface ReadingProgressSource {
  ReadingProgressResult loadRecent(Context context, int limit);
}
