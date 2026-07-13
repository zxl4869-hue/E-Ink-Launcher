package cn.modificator.launcher.model;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import cn.modificator.launcher.Config;

public class IconCache {

  private static final String ICON_DIR = "E-Ink Launcher" + File.separator + "icon";
  private static final String CACHE_FILE = ".icon_cache";
  private static final String TAG = "IconCache";

  private final Context appContext;
  private final Map<String, Drawable> drawableCache = new HashMap<>();
  private final Map<String, CharSequence> labelCache = new HashMap<>();
  private final Map<String, File> customIconMap = new HashMap<>();
  private boolean dirty = true;
  private String loadedThemeId = "";
  private long cachedDirMtime = -1;

  private Runnable onScanCompleteListener;

  public IconCache(Context context) {
    appContext = context.getApplicationContext();
  }

  public void setOnScanCompleteListener(Runnable listener) {
    this.onScanCompleteListener = listener;
  }

  public void markDirty() {
    dirty = true;
  }

  /** 加载图标映射。切换主题时必须同步完成，否则桌面会先用空映射重绑。 */
  public boolean refreshIcons(String themeId) {
    String normalizedThemeId = TextUtils.isEmpty(themeId) ? Config.ICON_THEME_BUILTIN : themeId;
    if (Config.ICON_THEME_BUILTIN.equals(normalizedThemeId)) {
      boolean changed = dirty || !Config.ICON_THEME_BUILTIN.equals(loadedThemeId) || !customIconMap.isEmpty();
      customIconMap.clear();
      loadedThemeId = normalizedThemeId;
      cachedDirMtime = -1;
      dirty = false;
      return changed;
    }

    File root;
    if (Config.ICON_THEME_LOCAL.equals(normalizedThemeId)) {
      root = getLocalIconDirectory();
      if (!root.exists()) {
        try { root.mkdirs(); } catch (Exception ignored) {}
      }
    } else {
      root = getThemeDirectory(appContext, normalizedThemeId);
      if (!root.exists()) {
        try { root.mkdirs(); } catch (Exception ignored) {}
      }
    }

    long dirMtime = root.exists() ? root.lastModified() : -1;
    if (!dirty && normalizedThemeId.equals(loadedThemeId) && dirMtime == cachedDirMtime) {
      return false;
    }

    customIconMap.clear();
    loadedThemeId = normalizedThemeId;
    Log.d(TAG, "scan theme=" + normalizedThemeId + " dir=" + root.getAbsolutePath()
        + " exists=" + root.exists());
    scanIconDirectory(root, customIconMap);
    Log.d(TAG, "scan done: " + customIconMap.size() + " icon keys loaded");
    writeCache(root);
    cachedDirMtime = root.exists() ? root.lastModified() : -1;
    dirty = false;
    return true;
  }

  // ---- Cache I/O ----

  private File cacheFile(File root) {
    return new File(root, CACHE_FILE);
  }

  private boolean loadFromCache(File root) {
    File cf = cacheFile(root);
    if (!cf.exists()) return false;

    try (BufferedReader r = new BufferedReader(new FileReader(cf))) {
      String line = r.readLine();
      if (line == null) return false;
      try {
        cachedDirMtime = Long.parseLong(line);
      } catch (NumberFormatException e) {
        return false;
      }

      while ((line = r.readLine()) != null) {
        int tab = line.indexOf('\t');
        if (tab < 0) continue;
        String key = line.substring(0, tab);
        String filename = line.substring(tab + 1);
        File iconFile = new File(root, filename);
        if (iconFile.exists()) {
          putIconFile(key, iconFile);
        }
      }
      return true;
    } catch (IOException e) {
      Log.w(TAG, "Failed to read icon cache", e);
      return false;
    }
  }

  private void writeCache(File root) {
    File cf = cacheFile(root);
    try (BufferedWriter w = new BufferedWriter(new FileWriter(cf))) {
      w.write(String.valueOf(root.lastModified()));
      w.newLine();
      for (Map.Entry<String, File> e : customIconMap.entrySet()) {
        String key = e.getKey();
        // Only write un-normalized keys (those without leading/trimmed variants)
        if (key.equals(IconCategoryResolver.normalizeIconKey(key))) {
          w.write(key);
          w.write('\t');
          w.write(e.getValue().getName());
          w.newLine();
        }
      }
    } catch (IOException e) {
      Log.w(TAG, "Failed to write icon cache", e);
    }
  }

  private void scanIconDirectory(File root, Map<String, File> target) {
    File[] files = root.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file == null || !file.isFile()) continue;
      String name = file.getName();
      if (name.startsWith(".")) continue; // skip hidden files like .icon_cache
      int dot = name.lastIndexOf('.');
      String key = dot > 0 ? name.substring(0, dot) : name;
      putIconFile(target, key, file);
    }
  }

  // ---- Put icon (used during scan and cache load) ----

  private void putIconFile(String key, File file) {
    putIconFile(customIconMap, key, file);
  }

  private void putIconFile(Map<String, File> target, String key, File file) {
    if (TextUtils.isEmpty(key) || file == null) return;
    target.put(key, file);
    String normalized = IconCategoryResolver.normalizeIconKey(key);
    if (!TextUtils.isEmpty(normalized)) {
      target.put(normalized, file);
    }
  }

  // ---- Directory paths ----

  public static File getLocalIconDirectory() {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
      return new File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), ICON_DIR);
    }
    return new File(Environment.getExternalStorageDirectory(), ICON_DIR);
  }

  public static File getThemeDirectory(Context context, String themeId) {
    File base = context.getExternalFilesDir("icons");
    if (base == null) {
      base = new File(context.getFilesDir(), "icons");
    }
    return new File(new File(base, "themes"), safeDirectoryName(themeId));
  }

  private static String safeDirectoryName(String name) {
    String normalized = IconCategoryResolver.normalizeIconKey(name);
    if (TextUtils.isEmpty(normalized)) return Config.ICON_THEME_BUILTIN;
    return normalized.replace('/', '_').replace('\\', '_').replace(':', '_');
  }

  // ---- Lookup ----

  public File getThemeIcon(String key) {
    return customIconMap.get(IconCategoryResolver.normalizeIconKey(key));
  }

  public File getCustomIcon(String packageName) {
    return getThemeIcon(packageName);
  }

  public Map<String, File> getCustomIconMap() {
    return Collections.unmodifiableMap(customIconMap);
  }

  // ---- Drawable / Label cache ----

  public Drawable getIcon(String packageName, ResolveInfo info, PackageManager pm) {
    Drawable cached = drawableCache.get(packageName);
    if (cached == null) {
      cached = info.loadIcon(pm);
      drawableCache.put(packageName, cached);
    }
    return cached;
  }

  public CharSequence getLabel(String packageName, ResolveInfo info, PackageManager pm) {
    CharSequence cached = labelCache.get(packageName);
    if (cached == null) {
      cached = info.loadLabel(pm);
      labelCache.put(packageName, cached);
    }
    return cached;
  }

  public void clearAppCache() {
    drawableCache.clear();
    labelCache.clear();
  }
}
