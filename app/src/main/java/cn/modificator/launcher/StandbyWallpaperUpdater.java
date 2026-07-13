package cn.modificator.launcher;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.PowerManager;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import cn.modificator.launcher.autorefresh.EpdRefresher;
import cn.modificator.launcher.reading.WeReadWallpaperQuoteSource;
import cn.modificator.launcher.widgets.StandbyFlipClockView;

public final class StandbyWallpaperUpdater {
  private static final String TAG = "StandbyWallpaper";
  private static final int WALLPAPER_WIDTH = 480;
  private static final int WALLPAPER_HEIGHT = 720;
  private static final String WALLPAPER_DIR = ".lockscreen_wallpaper";
  private static final String WALLPAPER_FILE = "screenoff.png";
  private static final String WALLPAPER_BACKUP_FILE = "screenoff.png.bak";
  private static final String STATE_PREFS = "standby_wallpaper_state";
  private static final String KEY_LAST_VENDOR_LOCK_TEXT = "lastVendorLockText";
  private static final String ACTION_FORCE_CLEAR = "com.hmct.action.forceclear";
  private static final String WEREAD_WALLPAPER_PREFIX = "weread-";
  private static final int WEREAD_DAILY_WALLPAPER_COUNT = 6;

  /** 当前锁屏是否为书封模式（长按音量减触发），true 时禁止时钟后台刷新 */
  public static volatile boolean bookCoverActive;

  private static Bitmap cachedWallpaper;
  private static int cachedWallpaperHash;
  private static final Object randomWallpaperCacheLock = new Object();
  private static Bitmap cachedRandomWallpaper;
  private static String cachedRandomWallpaperPath;
  private static long cachedRandomWallpaperModified;
  private static int cachedRandomWallpaperWidth;
  private static int cachedRandomWallpaperHeight;

  private StandbyWallpaperUpdater() {
  }

  private static Bitmap getSystemWallpaper(Context context) {
    try {
      WallpaperManager wm = WallpaperManager.getInstance(context);
      boolean isLive = wm.getWallpaperInfo() != null;
      if (isLive) {
        int currentHash = wm.getWallpaperInfo().hashCode();
        if (cachedWallpaper != null && !cachedWallpaper.isRecycled()
            && cachedWallpaperHash == currentHash) {
          return cachedWallpaper;
        }
        Drawable drawable = wm.getDrawable();
        if (drawable == null) return null;
        Bitmap bitmap = Bitmap.createBitmap(WALLPAPER_WIDTH, WALLPAPER_HEIGHT, Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
        drawable.draw(canvas);
        if (cachedWallpaper != null && !cachedWallpaper.isRecycled()) {
          cachedWallpaper.recycle();
        }
        cachedWallpaper = bitmap;
        cachedWallpaperHash = currentHash;
        Log.i(TAG, "Loaded live wallpaper");
        return cachedWallpaper;
      }
      Drawable drawable = wm.getDrawable();
      if (drawable == null) return null;
      cachedWallpaper = null;
      Bitmap bitmap = Bitmap.createBitmap(WALLPAPER_WIDTH, WALLPAPER_HEIGHT, Bitmap.Config.RGB_565);
      Canvas canvas = new Canvas(bitmap);
      drawable.setBounds(0, 0, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
      drawable.draw(canvas);
      return bitmap;
    } catch (Exception e) {
      Log.w(TAG, "Failed to get system wallpaper", e);
      return null;
    }
  }

  private static Bitmap getStandbyBaseWallpaper(Context context, Config config,
                                                int targetWidth, int targetHeight) {
    if (config != null && config.isStandbyRandomWallpaperEnabled()) {
      return loadRandomWallpaperBitmap(config, targetWidth, targetHeight);
    }

    Bitmap systemWallpaper = getSystemWallpaper(context);
    if (systemWallpaper == null) return null;
    Bitmap bitmap = drawBitmapCover(systemWallpaper, targetWidth, targetHeight);
    if (systemWallpaper != cachedWallpaper) systemWallpaper.recycle();
    return bitmap;
  }

  private static Bitmap loadRandomWallpaperBitmap(Config config, int targetWidth,
                                                  int targetHeight) {
    if (config == null || !config.isStandbyRandomWallpaperEnabled()) return null;
    File dir = new File(config.getStandbyRandomWallpaperDir());
    List<File> images = new ArrayList<>();
    collectImageFiles(dir, images);
    if (images.isEmpty()) {
      Log.i(TAG, "Random wallpaper folder empty: " + dir.getAbsolutePath());
      return null;
    }

    long intervalMs = Math.max(1L, config.getStandbyRandomWallpaperIntervalMinutes())
        * 60L * 1000L;
    long timeBucket = System.currentTimeMillis() / intervalMs;
    File selected = chooseRandomWallpaper(config, images, timeBucket);
    Bitmap cached = copyCachedRandomWallpaper(selected, targetWidth, targetHeight);
    if (cached != null) {
      return cached;
    }

    Bitmap source = null;
    try {
      source = BitmapFactory.decodeFile(selected.getAbsolutePath());
      if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) {
        Log.w(TAG, "Unable to decode random wallpaper: " + selected.getAbsolutePath());
        return null;
      }
      Bitmap bitmap = drawBitmapCover(source, targetWidth, targetHeight);
      rememberRandomWallpaper(selected, bitmap);
      Log.i(TAG, "Random wallpaper selected " + selected.getAbsolutePath()
          + " -> " + targetWidth + "x" + targetHeight);
      return bitmap;
    } catch (Exception e) {
      Log.w(TAG, "Failed to load random wallpaper: " + selected.getAbsolutePath(), e);
      return null;
    } finally {
      if (source != null && !source.isRecycled()) source.recycle();
    }
  }

  private static Bitmap copyCachedRandomWallpaper(File selected, int targetWidth, int targetHeight) {
    if (selected == null) return null;
    synchronized (randomWallpaperCacheLock) {
      if (cachedRandomWallpaper == null || cachedRandomWallpaper.isRecycled()) return null;
      if (cachedRandomWallpaperPath == null
          || !cachedRandomWallpaperPath.equals(selected.getAbsolutePath())
          || cachedRandomWallpaperModified != selected.lastModified()
          || cachedRandomWallpaperWidth != targetWidth
          || cachedRandomWallpaperHeight != targetHeight) {
        return null;
      }
      try {
        Bitmap copy = cachedRandomWallpaper.copy(Bitmap.Config.RGB_565, true);
        if (copy != null) {
          Log.i(TAG, "Random wallpaper cache hit " + cachedRandomWallpaperPath
              + " -> " + targetWidth + "x" + targetHeight);
        }
        return copy;
      } catch (Exception e) {
        Log.w(TAG, "Unable to copy random wallpaper cache", e);
        return null;
      }
    }
  }

  private static void rememberRandomWallpaper(File selected, Bitmap bitmap) {
    if (selected == null || bitmap == null || bitmap.isRecycled()) return;
    synchronized (randomWallpaperCacheLock) {
      if (cachedRandomWallpaper != null && !cachedRandomWallpaper.isRecycled()) {
        cachedRandomWallpaper.recycle();
      }
      cachedRandomWallpaper = bitmap.copy(Bitmap.Config.RGB_565, false);
      cachedRandomWallpaperPath = selected.getAbsolutePath();
      cachedRandomWallpaperModified = selected.lastModified();
      cachedRandomWallpaperWidth = bitmap.getWidth();
      cachedRandomWallpaperHeight = bitmap.getHeight();
    }
  }

  private static void collectImageFiles(File dir, List<File> images) {
    if (dir == null || images == null || !dir.isDirectory()) return;
    File[] files = dir.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file == null || file.isHidden()) continue;
      if (file.isDirectory()) {
        collectImageFiles(file, images);
      } else if (file.isFile() && isImageFile(file)) {
        images.add(file);
      }
    }
  }

  private static boolean isImageFile(File file) {
    String name = file == null ? "" : file.getName().toLowerCase(Locale.US);
    return name.endsWith(".jpg")
        || name.endsWith(".jpeg")
        || name.endsWith(".png")
        || name.endsWith(".webp");
  }

  private static Bitmap drawBitmapCover(Bitmap source, int targetWidth, int targetHeight) {
    Bitmap target = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565);
    Canvas canvas = new Canvas(target);
    canvas.drawColor(Color.WHITE);
    if (source == null || source.getWidth() <= 0 || source.getHeight() <= 0) return target;

    float scale = Math.max(targetWidth / (float) source.getWidth(),
        targetHeight / (float) source.getHeight());
    float scaledWidth = source.getWidth() * scale;
    float scaledHeight = source.getHeight() * scale;
    float left = (targetWidth - scaledWidth) / 2f;
    float top = (targetHeight - scaledHeight) / 2f;
    Rect src = new Rect(0, 0, source.getWidth(), source.getHeight());
    RectF dst = new RectF(left, top, left + scaledWidth, top + scaledHeight);
    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    canvas.drawBitmap(source, src, dst, paint);
    return target;
  }

  public static boolean update(Context context, Bitmap source, String lockText, boolean commit,
                        boolean showTime) {
    Config config = new Config(context);
    int clockStyle = config.getStandbyClockStyle();
    boolean generatedWereadWallpaper = source == null && config.isWereadWallpaperEnabled();
    boolean generatedClockWallpaper = source == null && showTime;
    String vendorLockText = resolveVendorLockText(config, lockText, generatedClockWallpaper,
        generatedWereadWallpaper, clockStyle);
    Bitmap wallpaper;
    if (source != null) {
      wallpaper = createWallpaper(source);
    } else if (generatedWereadWallpaper) {
      wallpaper = renderWereadWallpaper(context, config);
    } else if (showTime) {
      wallpaper = renderClockWallpaper(context, config);
    } else {
      wallpaper = getStandbyBaseWallpaper(context, config, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
      if (wallpaper == null) {
        wallpaper = Bitmap.createBitmap(WALLPAPER_WIDTH, WALLPAPER_HEIGHT, Bitmap.Config.RGB_565);
        new Canvas(wallpaper).drawColor(Color.WHITE);
      }
    }
    try {
      // 1. 写文件
      saveLockscreenWallpaper(wallpaper);

      // 2. 正常刷新只触发厂商重新合成 screenoff.png，避免经 Binder 传 bitmap 导致显示质量下降。
      if (commit && isScreenOff(context)) {
        requestVendorRefresh(context);
      }

      Log.i(TAG, "updated fullRefresh=" + commit + " showTime=" + showTime
          + " weread=" + generatedWereadWallpaper + " style=" + clockStyle
          + " screenOff=" + isScreenOff(context)
          + " lockText=\"" + vendorLockText + "\" epdPublished=false");
      return true;
    } finally {
      wallpaper.recycle();
    }
  }

  public static void requestVendorRefresh(Context context) {
    try {
      if (EpdRefresher.updateExternalPaper()) {
        Log.i(TAG, "updateExteralPaper OK");
        return;
      }
    } catch (Throwable e) {
      Log.w(TAG, "Unable to call EPD updateExteralPaper", e);
    }
    try {
      if (EpdRefresher.forceClear()) {
        Log.i(TAG, "forceClear OK");
        return;
      }
    } catch (Throwable e) {
      Log.w(TAG, "Unable to call EPD forceClear", e);
    }
    try {
      context.sendBroadcast(new Intent(ACTION_FORCE_CLEAR));
      Log.i(TAG, "sent " + ACTION_FORCE_CLEAR);
    } catch (Exception e) {
      Log.w(TAG, "Unable to send " + ACTION_FORCE_CLEAR, e);
    }
  }

  /** 仅在设置变更时调用：横屏时钟时清空锁屏文字，经典/镂空保留用户设置 */
  public static void applyLockText(Context context) {
    Config config = new Config(context);
    int clockStyle = config.getStandbyClockStyle();
    boolean generatedClockWallpaper = config.isStandbyWallpaperEnabled();
    boolean generatedWereadWallpaper = config.isWereadWallpaperEnabled();
    String text = resolveVendorLockText(config, config.getStandbyLockText(),
        generatedClockWallpaper, generatedWereadWallpaper, clockStyle);
    SharedPreferences state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    String lastText = state.getString(KEY_LAST_VENDOR_LOCK_TEXT, null);
    if (text.equals(lastText)) {
      Log.i(TAG, "lock text unchanged, skip bitmap publish: \"" + text + "\" style="
          + clockStyle);
      return;
    }

    Bitmap wallpaper = loadSavedLockscreenWallpaper();
    if (wallpaper == null) {
      if (generatedWereadWallpaper) {
        wallpaper = renderWereadWallpaper(context, config);
      } else {
        wallpaper = generatedClockWallpaper
            ? renderClockWallpaper(context, config)
            : getStandbyBaseWallpaper(context, config, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
      }
    }
    if (wallpaper == null) {
      wallpaper = Bitmap.createBitmap(WALLPAPER_WIDTH, WALLPAPER_HEIGHT, Bitmap.Config.RGB_565);
      new Canvas(wallpaper).drawColor(Color.WHITE);
    }
    try {
      boolean published = publishViaEpdManager(context, wallpaper, text);
      if (published) {
        state.edit().putString(KEY_LAST_VENDOR_LOCK_TEXT, text).apply();
      }
      Log.i(TAG, "lock text: \"" + text + "\" style=" + clockStyle
          + " epdPublished=" + published);
    } finally {
      wallpaper.recycle();
    }
  }

  public static boolean isScreenOff(Context context) {
    try {
      PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
      if (pm == null) return false;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
        return !pm.isInteractive();
      }
      return !pm.isScreenOn();
    } catch (Exception e) {
      Log.w(TAG, "Unable to check screen state", e);
      return false;
    }
  }

  private static File chooseRandomWallpaper(Config config, List<File> images, long timeBucket) {
    String lastPath = config.getStandbyRandomWallpaperLastPath();
    if (timeBucket == config.getStandbyRandomWallpaperLastBucket()
        && lastPath != null && lastPath.length() > 0) {
      for (File image : images) {
        if (lastPath.equals(image.getAbsolutePath())) {
          return image;
        }
      }
    }

    List<File> candidates = new ArrayList<>(images);
    if (candidates.size() > 1 && lastPath != null && lastPath.length() > 0) {
      for (int i = candidates.size() - 1; i >= 0; i--) {
        if (lastPath.equals(candidates.get(i).getAbsolutePath())) {
          candidates.remove(i);
          break;
        }
      }
    }
    File selected = candidates.get(new Random(System.nanoTime()).nextInt(candidates.size()));
    config.setStandbyRandomWallpaperLastSelection(timeBucket, selected.getAbsolutePath());
    Log.i(TAG, "Random wallpaper bucket=" + timeBucket + " selected="
        + selected.getAbsolutePath());
    return selected;
  }

  static Bitmap renderClockWallpaper(Context context) {
    return renderClockWallpaper(context, new Config(context));
  }

  private static Bitmap renderClockWallpaper(Context context, Config config) {
    int clockStyle = config.getStandbyClockStyle();
    Calendar calendar = Calendar.getInstance();
    boolean is24Hour = DateFormat.is24HourFormat(context);
    String timePattern = is24Hour ? "HH:mm" : "hh:mm";
    String time = new SimpleDateFormat(timePattern, Locale.getDefault()).format(calendar.getTime());
    String date = new SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(calendar.getTime());
    if (isLandscapeClockStyle(clockStyle)) {
      return renderLandscapeClockWallpaper(config, time, date,
          clockStyle == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT,
          clockStyle == Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE);
    }
    String period = Utils.getAMPMCNString(calendar.get(Calendar.HOUR), calendar.get(Calendar.AM_PM));

    Bitmap wallpaperBg = getStandbyBaseWallpaper(context, config, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);

    Bitmap bitmap;
    Canvas canvas;
    if (wallpaperBg != null) {
      bitmap = wallpaperBg;
      canvas = new Canvas(bitmap);
    } else {
      bitmap = Bitmap.createBitmap(WALLPAPER_WIDTH, WALLPAPER_HEIGHT, Bitmap.Config.RGB_565);
      canvas = new Canvas(bitmap);
      canvas.drawColor(Color.WHITE);
    }

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setTextAlign(Paint.Align.CENTER);

    paint.setTextSize(86f);
    paint.setFakeBoldText(true);
    if (clockStyle == Config.STANDBY_CLOCK_STYLE_CLASSIC_HOLLOW) {
      drawHollowCenteredText(canvas, paint, time, 160f, 8f, 3f);
    } else {
      drawOutlinedCenteredText(canvas, paint, time, 160f, 6f);
    }

    paint.setFakeBoldText(false);
    paint.setTextSize(25f);
    if (clockStyle == Config.STANDBY_CLOCK_STYLE_CLASSIC_HOLLOW) {
      drawHollowCenteredText(canvas, paint, date, 232f, 3.2f, 0.9f);
    } else {
      drawOutlinedCenteredText(canvas, paint, date, 232f, 3f);
    }

    paint.setTextSize(20f);
    if (clockStyle == Config.STANDBY_CLOCK_STYLE_CLASSIC_HOLLOW) {
      drawHollowCenteredText(canvas, paint, period, 272f, 3f, 0.8f);
    } else {
      drawOutlinedCenteredText(canvas, paint, period, 272f, 3f);
    }
    return bitmap;
  }

  private static boolean isLandscapeClockStyle(int style) {
    return style == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE
        || style == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT
        || style == Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE;
  }

  private static boolean shouldSuppressVendorLockText(boolean generatedClockWallpaper,
                                                      boolean generatedWereadWallpaper,
                                                      int clockStyle) {
    if (generatedWereadWallpaper) return true;
    if (!generatedClockWallpaper) return false;
    return clockStyle == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE
        || clockStyle == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT
        || clockStyle == Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE;
  }

  private static String resolveVendorLockText(Config config, String lockText,
                                              boolean generatedClockWallpaper,
                                              boolean generatedWereadWallpaper,
                                              int clockStyle) {
    if (shouldSuppressVendorLockText(generatedClockWallpaper, generatedWereadWallpaper,
        clockStyle)) {
      return "";
    }
    String text = lockText;
    if (text == null && config != null) {
      text = config.getStandbyLockText();
    }
    return text == null ? "" : text.trim();
  }

  private static boolean publishViaEpdManager(Context context, Bitmap bitmap, String lockText) {
    try {
      boolean published = EpdRefresher.publishKeyguardBitmap(bitmap, lockText);
      if (published) {
        Log.i(TAG, "published via EPD manager lockText=\"" + lockText + "\"");
      }
      return published;
    } catch (Throwable e) {
      Log.w(TAG, "Unable to publish via EPD manager", e);
      return false;
    }
  }

  private static Bitmap renderLandscapeClockWallpaper(Config config, String time, String date,
                                                      boolean lightStyle,
                                                      boolean minimalStyle) {
    int landscapeWidth = Math.max(WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
    int landscapeHeight = Math.min(WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
    Bitmap landscape = Bitmap.createBitmap(landscapeWidth, landscapeHeight, Bitmap.Config.RGB_565);
    StandbyFlipClockView.draw(new Canvas(landscape), landscapeWidth, landscapeHeight, time, date,
        lightStyle, minimalStyle, true);

    Bitmap target = Bitmap.createBitmap(WALLPAPER_WIDTH, WALLPAPER_HEIGHT, Bitmap.Config.RGB_565);
    Canvas canvas = new Canvas(target);
    canvas.drawColor(Color.WHITE);
    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    canvas.translate(WALLPAPER_WIDTH, 0);
    canvas.rotate(90f);
    canvas.drawBitmap(landscape, 0, 0, paint);
    landscape.recycle();
    return target;
  }

  private static Bitmap renderWereadWallpaper(Context context, Config config) {
    boolean allowRemote = Looper.myLooper() != Looper.getMainLooper();
    Bitmap generated = loadWereadGeneratedWallpaper(context, config, allowRemote);
    if (generated != null) return generated;
    WeReadWallpaperQuoteSource.Quote quote = WeReadWallpaperQuoteSource.getDailyQuote(context,
        config, allowRemote);
    return renderWereadQuoteWallpaper(quote, 0);
  }

  private static Bitmap loadWereadGeneratedWallpaper(Context context, Config config,
      boolean allowRemote) {
    if (config == null) config = new Config(context);
    File dir = new File(config.getWereadWallpaperDir());
    if (!dir.exists() && !dir.mkdirs()) {
      Log.w(TAG, "Unable to create WeRead wallpaper dir: " + dir);
      return null;
    }
    if (!dir.isDirectory()) return null;

    String dayKey = todayFileKey();
    List<File> files = listWereadWallpaperFiles(dir, dayKey);
    if (files.size() < WEREAD_DAILY_WALLPAPER_COUNT && allowRemote) {
      generateWereadWallpaperFiles(context, config, dir, dayKey);
      files = listWereadWallpaperFiles(dir, dayKey);
    }
    if (files.isEmpty()) return null;

    File selected = selectWereadWallpaperFile(files, config);
    Bitmap bitmap = BitmapFactory.decodeFile(selected.getAbsolutePath());
    if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
      return null;
    }
    if (bitmap.getWidth() == WALLPAPER_WIDTH && bitmap.getHeight() == WALLPAPER_HEIGHT) {
      return bitmap;
    }
    Bitmap fitted = drawBitmapCover(bitmap, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
    bitmap.recycle();
    return fitted;
  }

  private static void generateWereadWallpaperFiles(Context context, Config config, File dir,
      String dayKey) {
    cleanupOldWereadWallpaperFiles(dir, dayKey);
    List<WeReadWallpaperQuoteSource.Quote> quotes = WeReadWallpaperQuoteSource.getDailyQuotes(
        context, config, true, WEREAD_DAILY_WALLPAPER_COUNT);
    if (quotes.isEmpty()) {
      quotes = new ArrayList<>();
      quotes.add(WeReadWallpaperQuoteSource.getDailyQuote(context, config, false));
    }
    int count = WEREAD_DAILY_WALLPAPER_COUNT;
    for (int i = 0; i < count; i++) {
      Bitmap bitmap = renderWereadQuoteWallpaper(quotes.get(i % quotes.size()), i);
      File file = new File(dir, WEREAD_WALLPAPER_PREFIX + dayKey + "-"
          + String.format(Locale.US, "%02d", i + 1) + ".png");
      FileOutputStream out = null;
      try {
        out = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        out.flush();
        Log.i(TAG, "Saved WeRead wallpaper " + file.getAbsolutePath());
      } catch (Exception e) {
        Log.w(TAG, "Unable to save WeRead wallpaper: " + file, e);
      } finally {
        closeQuietly(out);
        bitmap.recycle();
      }
    }
  }

  private static List<File> listWereadWallpaperFiles(File dir, String dayKey) {
    List<File> files = new ArrayList<>();
    File[] listed = dir == null ? null : dir.listFiles();
    if (listed == null) return files;
    String prefix = WEREAD_WALLPAPER_PREFIX + dayKey + "-";
    for (File file : listed) {
      if (file != null && file.isFile() && file.getName().startsWith(prefix)
          && file.getName().toLowerCase(Locale.US).endsWith(".png")) {
        files.add(file);
      }
    }
    return files;
  }

  private static File selectWereadWallpaperFile(List<File> files, Config config) {
    if (files.size() == 1) return files.get(0);
    long intervalMs = Math.max(1L, config.getWereadWallpaperIntervalMinutes()) * 60L * 1000L;
    long bucket = System.currentTimeMillis() / intervalMs;
    int index = new Random(bucket * 1103515245L + todayFileKey().hashCode()).nextInt(files.size());
    return files.get(index);
  }

  private static void cleanupOldWereadWallpaperFiles(File dir, String todayKey) {
    File[] files = dir == null ? null : dir.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file == null || !file.isFile()) continue;
      String name = file.getName();
      if (name.startsWith(WEREAD_WALLPAPER_PREFIX)
          && name.endsWith(".png")
          && !name.startsWith(WEREAD_WALLPAPER_PREFIX + todayKey + "-")) {
        if (!file.delete()) {
          Log.w(TAG, "Unable to delete old WeRead wallpaper: " + file);
        }
      }
    }
  }

  private static String todayFileKey() {
    return new SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().getTime());
  }

  private static Bitmap renderWereadQuoteWallpaper(WeReadWallpaperQuoteSource.Quote quote,
      int variant) {
    Bitmap bitmap = Bitmap.createBitmap(WALLPAPER_WIDTH, WALLPAPER_HEIGHT, Bitmap.Config.RGB_565);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.rgb(238, 236, 228));
    drawEInkTexture(canvas, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);

    Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    linePaint.setColor(Color.BLACK);
    linePaint.setStrokeWidth(2f);

    TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    titlePaint.setColor(Color.BLACK);
    titlePaint.setTextSize(34f);
    titlePaint.setTypeface(Typeface.DEFAULT_BOLD);

    TextPaint smallPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    smallPaint.setColor(Color.rgb(45, 45, 45));
    smallPaint.setTextSize(17f);

    String dateTop = new SimpleDateFormat("M月d日", Locale.getDefault())
        .format(Calendar.getInstance().getTime());

    canvas.drawText("书摘", 48f, 58f, titlePaint);
    smallPaint.setTextAlign(Paint.Align.RIGHT);
    canvas.drawText(dateTop, WALLPAPER_WIDTH - 45f, 58f, smallPaint);
    smallPaint.setTextAlign(Paint.Align.LEFT);
    canvas.drawLine(40f, 88f, WALLPAPER_WIDTH - 40f, 88f, linePaint);

    Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    accent.setColor(variant % 2 == 0 ? Color.rgb(125, 40, 28) : Color.rgb(70, 70, 70));
    accent.setStyle(Paint.Style.STROKE);
    accent.setStrokeWidth(7f);
    drawQuoteCorner(canvas, accent, 68f, 124f, true);
    drawQuoteCorner(canvas, accent, WALLPAPER_WIDTH - 72f, 472f, false);

    TextPaint quotePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    quotePaint.setColor(Color.BLACK);
    quotePaint.setTextSize(bestQuoteTextSize(quote == null ? "" : quote.text));
    quotePaint.setTypeface(Typeface.DEFAULT);
    String quoteText = quote == null || TextUtils.isEmpty(quote.text)
        ? "今日还没有同步到微信读书划线。"
        : quote.text;
    drawStaticText(canvas, quoteText, 88f, 132f, WALLPAPER_WIDTH - 154, quotePaint, 1.18f);

    canvas.drawLine(58f, 508f, WALLPAPER_WIDTH - 58f, 508f, linePaint);
    TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    metaPaint.setColor(Color.BLACK);
    metaPaint.setTextSize(18f);
    metaPaint.setTypeface(Typeface.DEFAULT_BOLD);
    drawStaticText(canvas, formatQuoteSource(quote), 58f, 538f,
        WALLPAPER_WIDTH - 116, metaPaint, 1.12f);

    TextPaint footerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    footerPaint.setColor(Color.rgb(75, 75, 75));
    footerPaint.setTextSize(17f);
    String date = new SimpleDateFormat("yyyy.MM.dd", Locale.getDefault())
        .format(Calendar.getInstance().getTime());
    canvas.drawText("微信读书 · " + date, 58f, 650f, footerPaint);
    return bitmap;
  }

  private static float bestQuoteTextSize(String text) {
    int length = text == null ? 0 : text.length();
    if (length > 145) return 24f;
    if (length > 110) return 26f;
    if (length > 75) return 28f;
    return 30f;
  }

  private static void drawEInkTexture(Canvas canvas, int width, int height) {
    Paint paint = new Paint();
    for (int y = 0; y < height; y += 3) {
      for (int x = (y % 2); x < width; x += 6) {
        int gray = 220 + ((x * 31 + y * 17) & 15);
        paint.setColor(Color.rgb(gray, gray, gray));
        canvas.drawPoint(x, y, paint);
      }
    }
  }

  private static void drawQuoteCorner(Canvas canvas, Paint paint, float x, float y,
      boolean topLeft) {
    float size = 30f;
    if (topLeft) {
      canvas.drawLine(x, y, x + size, y, paint);
      canvas.drawLine(x, y, x, y + size, paint);
    } else {
      canvas.drawLine(x - size, y, x, y, paint);
      canvas.drawLine(x, y - size, x, y, paint);
    }
  }

  private static void drawStaticText(Canvas canvas, String text, float left, float top,
      int width, TextPaint paint, float spacingMultiplier) {
    if (TextUtils.isEmpty(text) || width <= 0) return;
    StaticLayout layout = new StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL,
        spacingMultiplier, 0f, false);
    canvas.save();
    canvas.translate(left, top);
    layout.draw(canvas);
    canvas.restore();
  }

  private static String formatQuoteSource(WeReadWallpaperQuoteSource.Quote quote) {
    if (quote == null) return "—《微信读书》";
    String title = TextUtils.isEmpty(quote.title) ? "微信读书" : quote.title;
    if (TextUtils.isEmpty(quote.author)) {
      return "—《" + title + "》";
    }
    return "—《" + title + "》 · " + quote.author;
  }

  private static void drawOutlinedCenteredText(Canvas canvas, Paint paint, String text,
                                               float centerY, float strokeWidth) {
    Paint.FontMetrics metrics = paint.getFontMetrics();
    float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(strokeWidth);
    paint.setColor(Color.WHITE);
    canvas.drawText(text, WALLPAPER_WIDTH / 2f, baseline, paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setStrokeWidth(0f);
    paint.setColor(Color.BLACK);
    canvas.drawText(text, WALLPAPER_WIDTH / 2f, baseline, paint);
  }

  private static void drawHollowCenteredText(Canvas canvas, Paint paint, String text,
                                             float centerY, float outerStrokeWidth,
                                             float innerStrokeWidth) {
    Paint.FontMetrics metrics = paint.getFontMetrics();
    float baseline = centerY - (metrics.ascent + metrics.descent) / 2f;
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(outerStrokeWidth);
    paint.setColor(Color.WHITE);
    canvas.drawText(text, WALLPAPER_WIDTH / 2f, baseline, paint);
    paint.setStrokeWidth(innerStrokeWidth);
    paint.setColor(Color.BLACK);
    canvas.drawText(text, WALLPAPER_WIDTH / 2f, baseline, paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setStrokeWidth(0f);
  }

  private static Bitmap createWallpaper(Bitmap source) {
    return drawBitmapCover(source, WALLPAPER_WIDTH, WALLPAPER_HEIGHT);
  }

  private static Bitmap loadSavedLockscreenWallpaper() {
    try {
      File target = new File(new File(
          Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
          WALLPAPER_DIR), WALLPAPER_FILE);
      if (!target.exists() || !target.isFile()) return null;
      Bitmap bitmap = BitmapFactory.decodeFile(target.getAbsolutePath());
      if (bitmap == null || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
        return null;
      }
      return bitmap;
    } catch (Exception e) {
      Log.w(TAG, "Failed to load saved lockscreen wallpaper", e);
      return null;
    }
  }

  static void saveLockscreenWallpaper(Bitmap wallpaper) {
    FileOutputStream out = null;
    try {
      File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
          WALLPAPER_DIR);
      if (!dir.exists() && !dir.mkdirs()) {
        Log.w(TAG, "Unable to create dir: " + dir);
        return;
      }

      File target = new File(dir, WALLPAPER_FILE);
      File backup = new File(dir, WALLPAPER_BACKUP_FILE);
      if (target.exists() && !backup.exists()) {
        copyFile(target, backup);
      }

      File tmp = new File(dir, WALLPAPER_FILE + ".tmp");
      out = new FileOutputStream(tmp);
      wallpaper.compress(Bitmap.CompressFormat.PNG, 100, out);
      out.flush();
      closeQuietly(out);
      out = null;

      if (target.exists() && !target.delete()) {
        Log.w(TAG, "Unable to delete old wallpaper: " + target);
      }
      if (!tmp.renameTo(target)) {
        Log.w(TAG, "Unable to replace wallpaper: " + target);
      } else {
        Log.i(TAG, "Saved " + target + " " + wallpaper.getWidth() + "x" + wallpaper.getHeight());
      }
    } catch (Exception e) {
      Log.w(TAG, "Failed to save wallpaper", e);
    } finally {
      closeQuietly(out);
    }
  }

  private static void copyFile(File source, File target) throws IOException {
    FileInputStream in = null;
    FileOutputStream out = null;
    try {
      in = new FileInputStream(source);
      out = new FileOutputStream(target);
      byte[] buffer = new byte[8192];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      out.flush();
    } finally {
      closeQuietly(in);
      closeQuietly(out);
    }
  }

  private static void closeQuietly(java.io.Closeable closeable) {
    if (closeable == null) return;
    try {
      closeable.close();
    } catch (IOException ignored) {
    }
  }
}
