package cn.modificator.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用配置管理类。
 * 统一管理 SharedPreferences 的读写，缓存常用配置值。
 * 偏好键（KEY_*）集中定义于此类。
 */
public class Config {

  // ---- 偏好键常量 ----
  public static final String KEY_COL_NUM = "colNumKey";
  public static final String KEY_ROW_NUM = "rowNumKey";
  public static final String KEY_PORTRAIT_COL_NUM = "portraitColNumKey";
  public static final String KEY_PORTRAIT_ROW_NUM = "portraitRowNumKey";
  public static final String KEY_LANDSCAPE_COL_NUM = "landscapeColNumKey";
  public static final String KEY_LANDSCAPE_ROW_NUM = "landscapeRowNumKey";
  public static final String KEY_APP_NAME_LINES = "appNameShowLines";
  public static final String KEY_HIDE_APPS = "hideAppsKey";
  public static final String KEY_FAVORITE_APPS = "favoriteAppsKey";
  public static final String KEY_FONT_SIZE = "launcherFontSize";
  public static final String KEY_HIDE_DIVIDER = "launcherHideDivider";
  public static final String KEY_SHOW_STATUS_BAR = "launcherShowStatusBar";
  public static final String KEY_DESKTOP_WALLPAPER_DIR = "desktopWallpaperDir";
  public static final String KEY_SHOW_CUSTOM_ICON = "launcherShowCustomIcon";
  public static final String KEY_ICON_THEME_ID = "launcherIconThemeId";
  public static final String KEY_ICON_AUTO_REPORT_MISSING = "launcherIconAutoReportMissing";
  public static final String KEY_SORT_MODE = "launcherSortMode";
  public static final String KEY_ORIENTATION_MODE = "launcherOrientationMode";
  public static final String KEY_STANDBY_LOCK_TEXT = "standbyLockText";
  public static final String KEY_STANDBY_WALLPAPER_ENABLED = "standbyWallpaperEnabled";
  public static final String KEY_STANDBY_RANDOM_WALLPAPER_ENABLED =
      "standbyRandomWallpaperEnabled";
  public static final String KEY_STANDBY_RANDOM_WALLPAPER_DIR = "standbyRandomWallpaperDir";
  public static final String KEY_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES =
      "standbyRandomWallpaperIntervalMinutes";
  private static final String KEY_STANDBY_RANDOM_WALLPAPER_LAST_BUCKET =
      "standbyRandomWallpaperLastBucket";
  private static final String KEY_STANDBY_RANDOM_WALLPAPER_LAST_PATH =
      "standbyRandomWallpaperLastPath";
  private static final String KEY_LAST_WALLPAPER_UPDATE_MINUTE = "lastWallpaperUpdateMinute";
  public static final String KEY_STANDBY_FULL_REFRESH = "standbyFullRefresh";
  public static final String KEY_STANDBY_REFRESH_MODE = "standbyRefreshMode";
  private static final String KEY_STANDBY_REFRESH_COUNT = "standbyRefreshCount";
  public static final String KEY_STANDBY_CLOCK_STYLE = "standbyClockStyle";
  public static final String KEY_STANDBY_DEBUG_DUMP_ENABLED = "standbyDebugDumpEnabled";
  public static final String KEY_BOOK_COVER_ENABLED = "bookCoverEnabled";
  public static final String KEY_BOOK_COVER_RANDOM = "bookCoverRandom";
  public static final String KEY_BOOK_COVER_OVERLAY_DIR = "bookCoverOverlayDir";
  public static final String KEY_WEREAD_SYNC_ENABLED = "wereadSyncEnabled";
  public static final String KEY_WEREAD_API_KEY = "wereadApiKey";
  public static final String KEY_WEREAD_SYNC_INTERVAL_MINUTES = "wereadSyncIntervalMinutes";
  public static final String KEY_WEREAD_WALLPAPER_ENABLED = "wereadWallpaperEnabled";
  public static final String KEY_WEREAD_WALLPAPER_DIR = "wereadWallpaperDir";
  public static final String KEY_WEREAD_WALLPAPER_INTERVAL_MINUTES =
      "wereadWallpaperIntervalMinutes";

  public static final int ORIENTATION_AUTO = 0;
  public static final int ORIENTATION_PORTRAIT = 1;
  public static final int ORIENTATION_LANDSCAPE = 2;
  public static final int ORIENTATION_REVERSE_PORTRAIT = 3;
  public static final int ORIENTATION_REVERSE_LANDSCAPE = 4;
  public static final int STANDBY_CLOCK_STYLE_CLASSIC = 0;
  public static final int STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE = 1;
  public static final int STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT = 2;
  public static final int STANDBY_CLOCK_STYLE_CLASSIC_HOLLOW = 3;
  public static final int STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE = 4;

  public static final int STANDBY_REFRESH_MODE_SILENT = 0;
  public static final int STANDBY_REFRESH_MODE_BLACK_FLASH = 1;
  public static final int STANDBY_REFRESH_MODE_HYBRID_3 = 2;
  public static final int STANDBY_REFRESH_MODE_HYBRID_5 = 3;
  public static final String ICON_THEME_BUILTIN = "builtin";
  public static final String ICON_THEME_LOCAL = "local";

  // ---- 默认值 ----
  private static final int DEFAULT_COL_NUM = 4;
  private static final int DEFAULT_ROW_NUM = 4;
  private static final int DEFAULT_LANDSCAPE_COL_NUM = 5;
  private static final int DEFAULT_LANDSCAPE_ROW_NUM = 2;
  private static final int MIN_GRID_NUM = 2;
  private static final int MAX_GRID_NUM = 10;
  private static final float DEFAULT_FONT_SIZE = 14f;
  private static final int DEFAULT_APP_NAME_LINES = Integer.MAX_VALUE;
  private static final boolean DEFAULT_HIDE_DIVIDER = true;
  private static final boolean DEFAULT_SHOW_STATUS_BAR = true;
  private static final String DEFAULT_DESKTOP_WALLPAPER_DIR =
      android.os.Environment.getExternalStoragePublicDirectory(
          android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
          + "/eink-launcher-desktop-wallpaper";
  private static final boolean DEFAULT_SHOW_CUSTOM_ICON = false;
  private static final boolean DEFAULT_ICON_AUTO_REPORT_MISSING = true;
  private static final int DEFAULT_SORT_MODE = 0;
  private static final int DEFAULT_ORIENTATION_MODE = ORIENTATION_AUTO;
  private static final String DEFAULT_STANDBY_LOCK_TEXT = "已锁屏";
  private static final boolean DEFAULT_STANDBY_WALLPAPER_ENABLED = true;
  private static final boolean DEFAULT_STANDBY_RANDOM_WALLPAPER_ENABLED = false;
  private static final int DEFAULT_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES = 60;
  private static final int MIN_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES = 1;
  private static final int MAX_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES = 24 * 60;
  private static final String DEFAULT_STANDBY_RANDOM_WALLPAPER_DIR =
      android.os.Environment.getExternalStoragePublicDirectory(
          android.os.Environment.DIRECTORY_DOWNLOADS).getAbsolutePath()
          + "/eink-wallpapers";
  private static final boolean DEFAULT_STANDBY_FULL_REFRESH = true;
  private static final int DEFAULT_STANDBY_REFRESH_MODE = STANDBY_REFRESH_MODE_SILENT;
  private static final int DEFAULT_STANDBY_CLOCK_STYLE = STANDBY_CLOCK_STYLE_CLASSIC;
  private static final boolean DEFAULT_STANDBY_DEBUG_DUMP_ENABLED = false;
  private static final boolean DEFAULT_BOOK_COVER_ENABLED = false;
  private static final boolean DEFAULT_BOOK_COVER_RANDOM = true;
  private static final String DEFAULT_BOOK_COVER_OVERLAY_DIR =
      android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
          + "/Pictures/eink-overlay";
  private static final boolean DEFAULT_WEREAD_SYNC_ENABLED = false;
  private static final int DEFAULT_WEREAD_SYNC_INTERVAL_MINUTES = 60;
  private static final boolean DEFAULT_WEREAD_WALLPAPER_ENABLED = false;
  private static final int DEFAULT_WEREAD_WALLPAPER_INTERVAL_MINUTES = 60;
  private static final int MIN_WEREAD_WALLPAPER_INTERVAL_MINUTES = 1;
  private static final int MAX_WEREAD_WALLPAPER_INTERVAL_MINUTES = 24 * 60;
  private static final String DEFAULT_WEREAD_WALLPAPER_DIR =
      android.os.Environment.getExternalStoragePublicDirectory(
          android.os.Environment.DIRECTORY_PICTURES).getAbsolutePath()
          + "/eink-weread-wallpapers";
  private static final int MIN_WEREAD_SYNC_INTERVAL_MINUTES = 15;
  private static final int MAX_WEREAD_SYNC_INTERVAL_MINUTES = 24 * 60;

  private static final String PREFS_FILE = "launcherPropertyFile";

  private final SharedPreferences prefs;

  // ---- 缓存字段 ----
  private int colNum = -1;
  private int rowNum = -1;
  private int portraitColNum = -1;
  private int portraitRowNum = -1;
  private int landscapeColNum = -1;
  private int landscapeRowNum = -1;
  private float fontSize = -1;
  private int appNameLines = -1;
  private boolean hideDivider;
  private boolean showStatusBar;
  private boolean showCustomIcon;
  private String iconThemeId;
  private boolean iconAutoReportMissing;
  private boolean standbyWallpaperEnabled;
  private boolean standbyRandomWallpaperEnabled;
  private int standbyRandomWallpaperIntervalMinutes = -1;
  private boolean standbyFullRefresh;
  private int standbyRefreshMode = -1;
  private int standbyClockStyle = -1;
  private boolean standbyDebugDumpEnabled;
  private boolean bookCoverEnabled;
  private boolean bookCoverRandom;
  private boolean wereadSyncEnabled;
  private boolean wereadWallpaperEnabled;
  private int wereadSyncIntervalMinutes = -1;
  private int wereadWallpaperIntervalMinutes = -1;
  private int sortMode = -1;
  private int orientationMode = -1;
  private String standbyLockText;
  private final Set<String> hideApps = new HashSet<>();
  private boolean hideAppsLoaded = false;
  private final List<String> favoriteApps = new ArrayList<>();
  private boolean favoriteAppsLoaded = false;

  public Config(Context context) {
    this.prefs = context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE);
    migrateOrientationGridSettings();
    // 预加载布尔配置
    this.hideDivider = prefs.getBoolean(KEY_HIDE_DIVIDER, DEFAULT_HIDE_DIVIDER);
    this.showStatusBar = prefs.getBoolean(KEY_SHOW_STATUS_BAR, DEFAULT_SHOW_STATUS_BAR);
    this.showCustomIcon = prefs.getBoolean(KEY_SHOW_CUSTOM_ICON, DEFAULT_SHOW_CUSTOM_ICON);
    this.iconAutoReportMissing =
        prefs.getBoolean(KEY_ICON_AUTO_REPORT_MISSING, DEFAULT_ICON_AUTO_REPORT_MISSING);
    this.standbyWallpaperEnabled = prefs.getBoolean(KEY_STANDBY_WALLPAPER_ENABLED, DEFAULT_STANDBY_WALLPAPER_ENABLED);
    this.standbyRandomWallpaperEnabled = prefs.getBoolean(KEY_STANDBY_RANDOM_WALLPAPER_ENABLED,
        DEFAULT_STANDBY_RANDOM_WALLPAPER_ENABLED);
    this.standbyFullRefresh = prefs.getBoolean(KEY_STANDBY_FULL_REFRESH, DEFAULT_STANDBY_FULL_REFRESH);
    this.standbyDebugDumpEnabled = prefs.getBoolean(KEY_STANDBY_DEBUG_DUMP_ENABLED,
        DEFAULT_STANDBY_DEBUG_DUMP_ENABLED);
    this.bookCoverEnabled = prefs.getBoolean(KEY_BOOK_COVER_ENABLED, DEFAULT_BOOK_COVER_ENABLED);
    this.bookCoverRandom = prefs.getBoolean(KEY_BOOK_COVER_RANDOM, DEFAULT_BOOK_COVER_RANDOM);
    this.wereadSyncEnabled = prefs.getBoolean(KEY_WEREAD_SYNC_ENABLED,
        DEFAULT_WEREAD_SYNC_ENABLED);
    this.wereadWallpaperEnabled = prefs.getBoolean(KEY_WEREAD_WALLPAPER_ENABLED,
        DEFAULT_WEREAD_WALLPAPER_ENABLED);
    this.appNameLines = prefs.getInt(KEY_APP_NAME_LINES, DEFAULT_APP_NAME_LINES);
  }

  // ---- 列数 ----

  public int getColNum() {
    return getColNum(false);
  }

  public void setColNum(int colNum) {
    setColNum(false, colNum);
  }

  public int getColNum(boolean landscape) {
    if (landscape) {
      if (landscapeColNum == -1) {
        landscapeColNum = prefs.getInt(KEY_LANDSCAPE_COL_NUM, DEFAULT_LANDSCAPE_COL_NUM);
      }
      return landscapeColNum;
    }
    if (portraitColNum == -1) {
      portraitColNum = prefs.getInt(KEY_PORTRAIT_COL_NUM, DEFAULT_COL_NUM);
    }
    return portraitColNum;
  }

  public void setColNum(boolean landscape, int colNum) {
    colNum = clampGridNum(colNum);
    if (landscape) {
      if (this.landscapeColNum == colNum) return;
      this.landscapeColNum = colNum;
      prefs.edit().putInt(KEY_LANDSCAPE_COL_NUM, colNum).apply();
      return;
    }
    if (this.portraitColNum == colNum) return;
    this.portraitColNum = colNum;
    prefs.edit().putInt(KEY_PORTRAIT_COL_NUM, colNum).apply();
  }

  // ---- 行数 ----

  public int getRowNum() {
    return getRowNum(false);
  }

  public void setRowNum(int rowNum) {
    setRowNum(false, rowNum);
  }

  public int getRowNum(boolean landscape) {
    if (landscape) {
      if (landscapeRowNum == -1) {
        landscapeRowNum = prefs.getInt(KEY_LANDSCAPE_ROW_NUM, DEFAULT_LANDSCAPE_ROW_NUM);
      }
      return landscapeRowNum;
    }
    if (portraitRowNum == -1) {
      portraitRowNum = prefs.getInt(KEY_PORTRAIT_ROW_NUM, DEFAULT_ROW_NUM);
    }
    return portraitRowNum;
  }

  public void setRowNum(boolean landscape, int rowNum) {
    rowNum = clampGridNum(rowNum);
    if (landscape) {
      if (this.landscapeRowNum == rowNum) return;
      this.landscapeRowNum = rowNum;
      prefs.edit().putInt(KEY_LANDSCAPE_ROW_NUM, rowNum).apply();
      return;
    }
    if (this.portraitRowNum == rowNum) return;
    this.portraitRowNum = rowNum;
    prefs.edit().putInt(KEY_PORTRAIT_ROW_NUM, rowNum).apply();
  }

  public int[] getGridSize(boolean landscape) {
    return new int[]{getColNum(landscape), getRowNum(landscape)};
  }

  private void migrateOrientationGridSettings() {
    boolean hasPortraitCol = prefs.contains(KEY_PORTRAIT_COL_NUM);
    boolean hasPortraitRow = prefs.contains(KEY_PORTRAIT_ROW_NUM);
    boolean hasLandscapeCol = prefs.contains(KEY_LANDSCAPE_COL_NUM);
    boolean hasLandscapeRow = prefs.contains(KEY_LANDSCAPE_ROW_NUM);
    if (hasPortraitCol && hasPortraitRow && hasLandscapeCol && hasLandscapeRow) {
      return;
    }

    SharedPreferences.Editor editor = prefs.edit();
    int legacyCol = prefs.getInt(KEY_COL_NUM, DEFAULT_COL_NUM);
    int legacyRow = prefs.getInt(KEY_ROW_NUM, DEFAULT_ROW_NUM);
    boolean hasLegacyCol = prefs.contains(KEY_COL_NUM);
    boolean hasLegacyRow = prefs.contains(KEY_ROW_NUM);

    if (!hasPortraitCol) {
      editor.putInt(KEY_PORTRAIT_COL_NUM,
          clampGridNum(hasLegacyCol ? legacyCol : DEFAULT_COL_NUM));
    }
    if (!hasPortraitRow) {
      editor.putInt(KEY_PORTRAIT_ROW_NUM,
          clampGridNum(hasLegacyRow ? legacyRow : DEFAULT_ROW_NUM));
    }
    if (!hasLandscapeCol) {
      editor.putInt(KEY_LANDSCAPE_COL_NUM,
          clampGridNum(hasLegacyCol ? legacyCol : DEFAULT_LANDSCAPE_COL_NUM));
    }
    if (!hasLandscapeRow) {
      editor.putInt(KEY_LANDSCAPE_ROW_NUM,
          clampGridNum(hasLegacyRow ? legacyRow : DEFAULT_LANDSCAPE_ROW_NUM));
    }
    editor.apply();
  }

  private static int clampGridNum(int value) {
    if (value < MIN_GRID_NUM) return MIN_GRID_NUM;
    if (value > MAX_GRID_NUM) return MAX_GRID_NUM;
    return value;
  }

  // ---- 隐藏应用 ----

  public void addHideApp(String packageName) {
    ensureHideAppsLoaded();
    hideApps.add(packageName);
    prefs.edit().putStringSet(KEY_HIDE_APPS, hideApps).apply();
  }

  public void removeHideApp(String packageName) {
    ensureHideAppsLoaded();
    hideApps.remove(packageName);
    prefs.edit().putStringSet(KEY_HIDE_APPS, hideApps).apply();
  }

  public void setHideApps(Set<String> hideApps) {
    this.hideApps.clear();
    this.hideApps.addAll(hideApps);
    this.hideAppsLoaded = true;
    prefs.edit().putStringSet(KEY_HIDE_APPS, this.hideApps).apply();
  }

  public Set<String> getHideApps() {
    ensureHideAppsLoaded();
    return hideApps;
  }

  private void ensureHideAppsLoaded() {
    if (!hideAppsLoaded) {
      hideApps.addAll(prefs.getStringSet(KEY_HIDE_APPS, new HashSet<String>()));
      hideAppsLoaded = true;
    }
  }

  // ---- 主页常用应用 ----

  public List<String> getFavoriteApps() {
    ensureFavoriteAppsLoaded();
    return new ArrayList<>(favoriteApps);
  }

  public void setFavoriteApps(List<String> packages) {
    favoriteApps.clear();
    if (packages != null) {
      LinkedHashSet<String> deduped = new LinkedHashSet<>();
      for (String pkg : packages) {
        if (pkg == null) continue;
        String trimmed = pkg.trim();
        if (trimmed.length() > 0) deduped.add(trimmed);
      }
      favoriteApps.addAll(deduped);
    }
    favoriteAppsLoaded = true;
    prefs.edit().putString(KEY_FAVORITE_APPS, joinPackages(favoriteApps)).apply();
  }

  public boolean isFavoriteApp(String packageName) {
    if (packageName == null) return false;
    ensureFavoriteAppsLoaded();
    return favoriteApps.contains(packageName);
  }

  public void addFavoriteApp(String packageName) {
    if (packageName == null || packageName.trim().length() == 0) return;
    ensureFavoriteAppsLoaded();
    String trimmed = packageName.trim();
    favoriteApps.remove(trimmed);
    favoriteApps.add(0, trimmed);
    prefs.edit().putString(KEY_FAVORITE_APPS, joinPackages(favoriteApps)).apply();
  }

  public void removeFavoriteApp(String packageName) {
    if (packageName == null) return;
    ensureFavoriteAppsLoaded();
    if (favoriteApps.remove(packageName)) {
      prefs.edit().putString(KEY_FAVORITE_APPS, joinPackages(favoriteApps)).apply();
    }
  }

  private void ensureFavoriteAppsLoaded() {
    if (favoriteAppsLoaded) return;
    favoriteApps.clear();
    String raw = prefs.getString(KEY_FAVORITE_APPS, "");
    if (raw != null && raw.trim().length() > 0) {
      String[] parts = raw.split("[,;\\n\\s]+");
      LinkedHashSet<String> deduped = new LinkedHashSet<>();
      for (String part : parts) {
        if (part == null) continue;
        String trimmed = part.trim();
        if (trimmed.length() > 0) deduped.add(trimmed);
      }
      favoriteApps.addAll(deduped);
    }
    favoriteAppsLoaded = true;
  }

  private String joinPackages(List<String> packages) {
    StringBuilder builder = new StringBuilder();
    if (packages != null) {
      for (String pkg : packages) {
        if (pkg == null || pkg.trim().length() == 0) continue;
        if (builder.length() > 0) builder.append(',');
        builder.append(pkg.trim());
      }
    }
    return builder.toString();
  }

  // ---- 字体大小 ----

  public float getFontSize() {
    if (fontSize < 0) {
      fontSize = prefs.getFloat(KEY_FONT_SIZE, DEFAULT_FONT_SIZE);
    }
    return fontSize;
  }

  public void setFontSize(float fontSize) {
    this.fontSize = fontSize;
    prefs.edit().putFloat(KEY_FONT_SIZE, fontSize).apply();
  }

  // ---- 分隔线 ----

  public boolean isHideDivider() {
    return hideDivider;
  }

  public void setHideDivider(boolean hide) {
    this.hideDivider = hide;
    prefs.edit().putBoolean(KEY_HIDE_DIVIDER, hide).apply();
  }

  // ---- 状态栏 ----

  public boolean isShowStatusBar() {
    return showStatusBar;
  }

  public void setShowStatusBar(boolean show) {
    this.showStatusBar = show;
    prefs.edit().putBoolean(KEY_SHOW_STATUS_BAR, show).apply();
  }

  public String getDesktopWallpaperDir() {
    return prefs.getString(KEY_DESKTOP_WALLPAPER_DIR, DEFAULT_DESKTOP_WALLPAPER_DIR);
  }

  public void setDesktopWallpaperDir(String dir) {
    if (dir == null || dir.trim().length() == 0) return;
    prefs.edit().putString(KEY_DESKTOP_WALLPAPER_DIR, dir.trim()).apply();
  }

  // ---- 图标风格 ----

  public String getIconThemeId() {
    if (iconThemeId == null) {
      if (prefs.contains(KEY_ICON_THEME_ID)) {
        iconThemeId = prefs.getString(KEY_ICON_THEME_ID, ICON_THEME_BUILTIN);
      } else {
        iconThemeId = showCustomIcon ? ICON_THEME_LOCAL : ICON_THEME_BUILTIN;
      }
      if (iconThemeId == null || iconThemeId.trim().length() == 0) {
        iconThemeId = ICON_THEME_BUILTIN;
      }
    }
    return iconThemeId;
  }

  public void setIconThemeId(String themeId) {
    if (themeId == null || themeId.trim().length() == 0) {
      themeId = ICON_THEME_BUILTIN;
    }
    iconThemeId = themeId.trim();
    showCustomIcon = ICON_THEME_LOCAL.equals(iconThemeId);
    prefs.edit()
        .putString(KEY_ICON_THEME_ID, iconThemeId)
        .putBoolean(KEY_SHOW_CUSTOM_ICON, showCustomIcon)
        .apply();
  }

  public boolean isIconAutoReportMissing() {
    return iconAutoReportMissing;
  }

  public void setIconAutoReportMissing(boolean enabled) {
    iconAutoReportMissing = enabled;
    prefs.edit().putBoolean(KEY_ICON_AUTO_REPORT_MISSING, enabled).apply();
  }

  // ---- 自定义图标（旧设置兼容：开 = 选择本地图标风格） ----

  public boolean isShowCustomIcon() {
    return ICON_THEME_LOCAL.equals(getIconThemeId());
  }

  public void setShowCustomIcon(boolean show) {
    setIconThemeId(show ? ICON_THEME_LOCAL : ICON_THEME_BUILTIN);
  }

  // ---- 应用名行数 ----

  public int getAppNameLines() {
    return appNameLines;
  }

  public void setAppNameLines(int lines) {
    this.appNameLines = lines;
    prefs.edit().putInt(KEY_APP_NAME_LINES, lines).apply();
  }

  // ---- 排序方式 ----

  public int getSortMode() {
    if (sortMode == -1) {
      sortMode = prefs.getInt(KEY_SORT_MODE, DEFAULT_SORT_MODE);
    }
    return sortMode;
  }

  public void setSortMode(int mode) {
    if (this.sortMode == mode) return;
    this.sortMode = mode;
    prefs.edit().putInt(KEY_SORT_MODE, mode).apply();
  }

  // ---- 屏幕方向 ----

  public int getOrientationMode() {
    if (orientationMode == -1) {
      orientationMode = prefs.getInt(KEY_ORIENTATION_MODE, DEFAULT_ORIENTATION_MODE);
    }
    return orientationMode;
  }

  public void setOrientationMode(int mode) {
    if (this.orientationMode == mode) return;
    this.orientationMode = mode;
    prefs.edit().putInt(KEY_ORIENTATION_MODE, mode).apply();
  }

  // ---- 待机锁屏壁纸开关 ----

  public boolean isStandbyWallpaperEnabled() {
    return standbyWallpaperEnabled;
  }

  public boolean isStandbyRefreshEnabled() {
    return standbyWallpaperEnabled || standbyRandomWallpaperEnabled || wereadWallpaperEnabled;
  }

  public void setStandbyWallpaperEnabled(boolean enabled) {
    this.standbyWallpaperEnabled = enabled;
    prefs.edit().putBoolean(KEY_STANDBY_WALLPAPER_ENABLED, enabled).apply();
  }

  public boolean isStandbyRandomWallpaperEnabled() {
    return standbyRandomWallpaperEnabled;
  }

  public void setStandbyRandomWallpaperEnabled(boolean enabled) {
    this.standbyRandomWallpaperEnabled = enabled;
    prefs.edit().putBoolean(KEY_STANDBY_RANDOM_WALLPAPER_ENABLED, enabled).apply();
  }

  public String getStandbyRandomWallpaperDir() {
    return prefs.getString(KEY_STANDBY_RANDOM_WALLPAPER_DIR,
        DEFAULT_STANDBY_RANDOM_WALLPAPER_DIR);
  }

  public void setStandbyRandomWallpaperDir(String dir) {
    if (dir == null || dir.trim().length() == 0) return;
    prefs.edit().putString(KEY_STANDBY_RANDOM_WALLPAPER_DIR, dir.trim()).apply();
  }

  public int getStandbyRandomWallpaperIntervalMinutes() {
    if (standbyRandomWallpaperIntervalMinutes == -1) {
      standbyRandomWallpaperIntervalMinutes = clampStandbyRandomWallpaperInterval(
          prefs.getInt(KEY_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES,
              DEFAULT_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES));
    }
    return standbyRandomWallpaperIntervalMinutes;
  }

  public void setStandbyRandomWallpaperIntervalMinutes(int minutes) {
    minutes = clampStandbyRandomWallpaperInterval(minutes);
    if (standbyRandomWallpaperIntervalMinutes == minutes) return;
    standbyRandomWallpaperIntervalMinutes = minutes;
    prefs.edit().putInt(KEY_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES, minutes).apply();
  }

  public long getStandbyRandomWallpaperLastBucket() {
    return prefs.getLong(KEY_STANDBY_RANDOM_WALLPAPER_LAST_BUCKET, Long.MIN_VALUE);
  }

  public String getStandbyRandomWallpaperLastPath() {
    return prefs.getString(KEY_STANDBY_RANDOM_WALLPAPER_LAST_PATH, "");
  }

  public void setStandbyRandomWallpaperLastSelection(long bucket, String path) {
    prefs.edit()
        .putLong(KEY_STANDBY_RANDOM_WALLPAPER_LAST_BUCKET, bucket)
        .putString(KEY_STANDBY_RANDOM_WALLPAPER_LAST_PATH, path == null ? "" : path)
        .apply();
  }

  private static int clampStandbyRandomWallpaperInterval(int minutes) {
    if (minutes < MIN_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES) {
      return MIN_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES;
    }
    if (minutes > MAX_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES) {
      return MAX_STANDBY_RANDOM_WALLPAPER_INTERVAL_MINUTES;
    }
    return minutes;
  }

  // ---- 待机全刷新开关 ----

  public boolean isStandbyFullRefresh() {
    return standbyFullRefresh;
  }

  public void setStandbyFullRefresh(boolean fullRefresh) {
    this.standbyFullRefresh = fullRefresh;
    prefs.edit().putBoolean(KEY_STANDBY_FULL_REFRESH, fullRefresh).apply();
  }

  // ---- 待机刷新方式 ----

  public int getStandbyRefreshMode() {
    if (standbyRefreshMode == -1) {
      standbyRefreshMode = clampRefreshMode(
          prefs.getInt(KEY_STANDBY_REFRESH_MODE, DEFAULT_STANDBY_REFRESH_MODE));
    }
    return standbyRefreshMode;
  }

  public void setStandbyRefreshMode(int mode) {
    mode = clampRefreshMode(mode);
    if (this.standbyRefreshMode == mode) return;
    this.standbyRefreshMode = mode;
    prefs.edit().putInt(KEY_STANDBY_REFRESH_MODE, mode).apply();
  }

  private static int clampRefreshMode(int mode) {
    if (mode < STANDBY_REFRESH_MODE_SILENT || mode > STANDBY_REFRESH_MODE_HYBRID_5) {
      return DEFAULT_STANDBY_REFRESH_MODE;
    }
    return mode;
  }

  public String getStandbyRefreshModeLabel() {
    switch (getStandbyRefreshMode()) {
      case STANDBY_REFRESH_MODE_SILENT:       return "仅静默刷新";
      case STANDBY_REFRESH_MODE_BLACK_FLASH:  return "仅黑闪刷新";
      case STANDBY_REFRESH_MODE_HYBRID_3:     return "3次静默+1次黑闪";
      case STANDBY_REFRESH_MODE_HYBRID_5:     return "5次静默+1次黑闪";
      default:                                return "未知";
    }
  }

  public int getHybridThreshold() {
    switch (getStandbyRefreshMode()) {
      case STANDBY_REFRESH_MODE_HYBRID_3: return 3;
      case STANDBY_REFRESH_MODE_HYBRID_5: return 5;
      default:                            return Integer.MAX_VALUE;
    }
  }

  // 刷新计数器（用于混合模式）
  public int getStandyRefreshCount() {
    return prefs.getInt(KEY_STANDBY_REFRESH_COUNT, 0);
  }

  public int incrementStandyRefreshCount() {
    int count = getStandyRefreshCount() + 1;
    prefs.edit().putInt(KEY_STANDBY_REFRESH_COUNT, count).apply();
    return count;
  }

  public void resetStandyRefreshCount() {
    prefs.edit().putInt(KEY_STANDBY_REFRESH_COUNT, 0).apply();
  }

  public boolean isStandbyDebugDumpEnabled() {
    return standbyDebugDumpEnabled;
  }

  public void setStandbyDebugDumpEnabled(boolean enabled) {
    this.standbyDebugDumpEnabled = enabled;
    prefs.edit().putBoolean(KEY_STANDBY_DEBUG_DUMP_ENABLED, enabled).apply();
  }

  public int getStandbyClockStyle() {
    if (standbyClockStyle == -1) {
      standbyClockStyle = clampStandbyClockStyle(
          prefs.getInt(KEY_STANDBY_CLOCK_STYLE, DEFAULT_STANDBY_CLOCK_STYLE));
    }
    return standbyClockStyle;
  }

  public void setStandbyClockStyle(int style) {
    style = clampStandbyClockStyle(style);
    if (standbyClockStyle == style) return;
    standbyClockStyle = style;
    prefs.edit().putInt(KEY_STANDBY_CLOCK_STYLE, style).apply();
  }

  private static int clampStandbyClockStyle(int style) {
    if (style < STANDBY_CLOCK_STYLE_CLASSIC
        || style > STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE) {
      return DEFAULT_STANDBY_CLOCK_STYLE;
    }
    return style;
  }

  // ---- 透明书封 ----

  public boolean isBookCoverEnabled() {
    return bookCoverEnabled;
  }

  public void setBookCoverEnabled(boolean enabled) {
    this.bookCoverEnabled = enabled;
    prefs.edit().putBoolean(KEY_BOOK_COVER_ENABLED, enabled).apply();
  }

  public boolean isBookCoverRandom() {
    return bookCoverRandom;
  }

  public void setBookCoverRandom(boolean random) {
    this.bookCoverRandom = random;
    prefs.edit().putBoolean(KEY_BOOK_COVER_RANDOM, random).apply();
  }

  public String getBookCoverOverlayDir() {
    return prefs.getString(KEY_BOOK_COVER_OVERLAY_DIR, DEFAULT_BOOK_COVER_OVERLAY_DIR);
  }

  public void setBookCoverOverlayDir(String dir) {
    prefs.edit().putString(KEY_BOOK_COVER_OVERLAY_DIR, dir).apply();
  }

  // ---- 微信读书同步 ----

  public boolean isWereadSyncEnabled() {
    return wereadSyncEnabled;
  }

  public void setWereadSyncEnabled(boolean enabled) {
    wereadSyncEnabled = enabled;
    prefs.edit().putBoolean(KEY_WEREAD_SYNC_ENABLED, enabled).apply();
  }

  public String getWereadApiKey() {
    String key = prefs.getString(KEY_WEREAD_API_KEY, "");
    return key == null ? "" : key.trim();
  }

  public void setWereadApiKey(String key) {
    prefs.edit().putString(KEY_WEREAD_API_KEY, key == null ? "" : key.trim()).apply();
  }

  public int getWereadSyncIntervalMinutes() {
    if (wereadSyncIntervalMinutes == -1) {
      wereadSyncIntervalMinutes = clampWereadSyncInterval(
          prefs.getInt(KEY_WEREAD_SYNC_INTERVAL_MINUTES,
              DEFAULT_WEREAD_SYNC_INTERVAL_MINUTES));
    }
    return wereadSyncIntervalMinutes;
  }

  public void setWereadSyncIntervalMinutes(int minutes) {
    minutes = clampWereadSyncInterval(minutes);
    if (wereadSyncIntervalMinutes == minutes) return;
    wereadSyncIntervalMinutes = minutes;
    prefs.edit().putInt(KEY_WEREAD_SYNC_INTERVAL_MINUTES, minutes).apply();
  }

  public boolean isWereadWallpaperEnabled() {
    return wereadWallpaperEnabled;
  }

  public void setWereadWallpaperEnabled(boolean enabled) {
    wereadWallpaperEnabled = enabled;
    prefs.edit().putBoolean(KEY_WEREAD_WALLPAPER_ENABLED, enabled).apply();
  }

  public String getWereadWallpaperDir() {
    return prefs.getString(KEY_WEREAD_WALLPAPER_DIR, DEFAULT_WEREAD_WALLPAPER_DIR);
  }

  public void setWereadWallpaperDir(String dir) {
    if (dir == null || dir.trim().length() == 0) return;
    prefs.edit().putString(KEY_WEREAD_WALLPAPER_DIR, dir.trim()).apply();
  }

  public int getWereadWallpaperIntervalMinutes() {
    if (wereadWallpaperIntervalMinutes == -1) {
      wereadWallpaperIntervalMinutes = clampWereadWallpaperInterval(
          prefs.getInt(KEY_WEREAD_WALLPAPER_INTERVAL_MINUTES,
              DEFAULT_WEREAD_WALLPAPER_INTERVAL_MINUTES));
    }
    return wereadWallpaperIntervalMinutes;
  }

  public void setWereadWallpaperIntervalMinutes(int minutes) {
    minutes = clampWereadWallpaperInterval(minutes);
    if (wereadWallpaperIntervalMinutes == minutes) return;
    wereadWallpaperIntervalMinutes = minutes;
    prefs.edit().putInt(KEY_WEREAD_WALLPAPER_INTERVAL_MINUTES, minutes).apply();
  }

  private static int clampWereadWallpaperInterval(int minutes) {
    if (minutes < MIN_WEREAD_WALLPAPER_INTERVAL_MINUTES) {
      return MIN_WEREAD_WALLPAPER_INTERVAL_MINUTES;
    }
    if (minutes > MAX_WEREAD_WALLPAPER_INTERVAL_MINUTES) {
      return MAX_WEREAD_WALLPAPER_INTERVAL_MINUTES;
    }
    return minutes;
  }

  private static int clampWereadSyncInterval(int minutes) {
    if (minutes < MIN_WEREAD_SYNC_INTERVAL_MINUTES) return MIN_WEREAD_SYNC_INTERVAL_MINUTES;
    if (minutes > MAX_WEREAD_SYNC_INTERVAL_MINUTES) return MAX_WEREAD_SYNC_INTERVAL_MINUTES;
    return minutes;
  }

  // ---- 壁纸上次更新分钟 ----

  public long getLastWallpaperUpdateMinute() {
    return prefs.getLong(KEY_LAST_WALLPAPER_UPDATE_MINUTE, 0);
  }

  public void setLastWallpaperUpdateMinute(long minute) {
    prefs.edit().putLong(KEY_LAST_WALLPAPER_UPDATE_MINUTE, minute).apply();
  }

  // ---- 待机锁屏底部文字 ----

  public String getStandbyLockText() {
    if (standbyLockText == null) {
      standbyLockText = prefs.getString(KEY_STANDBY_LOCK_TEXT, DEFAULT_STANDBY_LOCK_TEXT);
    }
    if (standbyLockText == null || standbyLockText.trim().length() == 0
        || isLegacyStandbyLockText(standbyLockText.trim())) {
      standbyLockText = DEFAULT_STANDBY_LOCK_TEXT;
      prefs.edit().putString(KEY_STANDBY_LOCK_TEXT, standbyLockText).apply();
      return DEFAULT_STANDBY_LOCK_TEXT;
    }
    return standbyLockText.trim();
  }

  public void setStandbyLockText(String text) {
    if (text == null || text.trim().length() == 0) {
      text = DEFAULT_STANDBY_LOCK_TEXT;
    }
    standbyLockText = text.trim();
    prefs.edit().putString(KEY_STANDBY_LOCK_TEXT, standbyLockText).apply();
  }

  private static boolean isLegacyStandbyLockText(String text) {
    return "已锁".equals(text)
        || text.contains("cn.modificator.launcher")
        || text.contains("launcher.debug");
  }
}
