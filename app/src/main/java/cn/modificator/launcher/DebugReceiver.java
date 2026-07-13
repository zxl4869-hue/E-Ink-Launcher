package cn.modificator.launcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

import cn.modificator.launcher.autorefresh.EpdHelper;
import cn.modificator.launcher.autorefresh.EpdRefresher;

/**
 * ADB 手动调试接收器。
 *
 * 支持命令：
 *   adb shell am broadcast -a cn.modificator.launcher.debug.RENDER       [-n pkg/.DebugReceiver]
 *   adb shell am broadcast -a cn.modificator.launcher.debug.SAVE_ONLY     [-n pkg/.DebugReceiver]
 *   adb shell am broadcast -a cn.modificator.launcher.debug.PUBLISH       [-n pkg/.DebugReceiver]
 *   adb shell am broadcast -a cn.modificator.launcher.debug.FORCE_CLEAR   [-n pkg/.DebugReceiver]
 *   adb shell am broadcast -a cn.modificator.launcher.debug.FULL_CYCLE    [-n pkg/.DebugReceiver]
 *   adb shell am broadcast -a cn.modificator.launcher.debug.CHECK_STATE   [-n pkg/.DebugReceiver]
 *   adb shell am broadcast -a cn.modificator.launcher.debug.EPD_SET_MODE  [-n pkg/.DebugReceiver] --es pkg target.package --ei mode 514
 *   adb shell am broadcast -a cn.modificator.launcher.debug.EPD_ADD_MODE  [-n pkg/.DebugReceiver] --es pkg target.package --ei mode 514
 *   adb shell am broadcast -a cn.modificator.launcher.debug.EPD_UPDATE_MODE [-n pkg/.DebugReceiver] --ei mode 514
 *   adb shell am broadcast -a cn.modificator.launcher.debug.EPD_CHECK      [-n pkg/.DebugReceiver]
 *
 * 所有日志 TAG = "DebugReceiver"
 */
public class DebugReceiver extends BroadcastReceiver {

  private static final String TAG = "DebugReceiver";
  private static final String PREFIX = "cn.modificator.launcher.debug.";

  public static final String ACTION_RENDER      = PREFIX + "RENDER";
  public static final String ACTION_SAVE_ONLY   = PREFIX + "SAVE_ONLY";
  public static final String ACTION_PUBLISH     = PREFIX + "PUBLISH";
  public static final String ACTION_FORCE_CLEAR = PREFIX + "FORCE_CLEAR";
  public static final String ACTION_FULL_CYCLE  = PREFIX + "FULL_CYCLE";
  public static final String ACTION_CHECK_STATE = PREFIX + "CHECK_STATE";
  public static final String ACTION_EPD_SET_MODE = PREFIX + "EPD_SET_MODE";
  public static final String ACTION_EPD_ADD_MODE = PREFIX + "EPD_ADD_MODE";
  public static final String ACTION_EPD_UPDATE_MODE = PREFIX + "EPD_UPDATE_MODE";
  public static final String ACTION_EPD_CHECK = PREFIX + "EPD_CHECK";

  private static final String EXTRA_PACKAGE = "pkg";
  private static final String EXTRA_PACKAGE_ALT = "package";
  private static final String EXTRA_MODE = "mode";

  private static final String WALLPAPER_DIR  = ".lockscreen_wallpaper";
  private static final String WALLPAPER_FILE = "screenoff.png";

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null) return;
    String action = intent.getAction();
    if (action == null) return;

    Log.i(TAG, "══════════════════════════════════════");
    Log.i(TAG, "收到命令: " + action);
    Log.i(TAG, "══════════════════════════════════════");

    final Context app = context.getApplicationContext();

    switch (action) {
      case ACTION_RENDER:
        handleRender(app);
        break;
      case ACTION_SAVE_ONLY:
        handleSaveOnly(app);
        break;
      case ACTION_PUBLISH:
        handlePublish(app);
        break;
      case ACTION_FORCE_CLEAR:
        handleForceClear(app);
        break;
      case ACTION_FULL_CYCLE:
        handleFullCycle(app);
        break;
      case ACTION_CHECK_STATE:
        handleCheckState(app);
        break;
      case ACTION_EPD_SET_MODE:
        handleEpdSetMode(app, intent);
        break;
      case ACTION_EPD_ADD_MODE:
        handleEpdAddMode(app, intent);
        break;
      case ACTION_EPD_UPDATE_MODE:
        handleEpdUpdateMode(app, intent);
        break;
      case ACTION_EPD_CHECK:
        handleEpdCheck(app);
        break;
      default:
        Log.w(TAG, "未知命令: " + action);
        break;
    }

    Log.i(TAG, "命令执行完毕: " + action);
  }

  // ── EPD_SET_MODE: addAppMode(pkg, rawMode) + updateEpdMode(rawMode) ──

  private void handleEpdSetMode(Context app, Intent intent) {
    String packageName = readPackageExtra(intent);
    int mode = readModeExtra(intent, EpdHelper.APP_MODE_GC16);
    if (packageName.length() == 0) {
      Log.e(TAG, "缺少目标包名。用法: --es pkg com.example.reader --ei mode 514");
      setResultData("missing pkg");
      return;
    }

    logStep("1/3", "当前 EPD raw mode");
    Log.i(TAG, "  sys.hmct.epd_mode=" + EpdHelper.readCurrentMode(EpdHelper.APP_MODE_TYPING));

    logStep("2/3", "注册 per-app 模式");
    boolean addOk = EpdHelper.setAppMode(packageName, mode);
    Log.i(TAG, "  addAppMode(" + packageName + ", " + mode + ")=" + addOk);

    logStep("3/3", "立即应用到当前前台 App");
    boolean updateOk = EpdHelper.updateForegroundMode(mode);
    Log.i(TAG, "  updateEpdMode(" + mode + ")=" + updateOk);
    Log.i(TAG, "  注意: updateEpdMode 影响系统记录的 mLastTopPackage，不一定是上面的 pkg。测试时让阅读 App 保持前台。");
    setResultData("add=" + addOk + ", update=" + updateOk);
  }

  // ── EPD_ADD_MODE: addAppMode(pkg, rawMode) only ───────────────────────

  private void handleEpdAddMode(Context app, Intent intent) {
    String packageName = readPackageExtra(intent);
    int mode = readModeExtra(intent, EpdHelper.APP_MODE_GC16);
    if (packageName.length() == 0) {
      Log.e(TAG, "缺少目标包名。用法: --es pkg com.example.reader --ei mode 514");
      setResultData("missing pkg");
      return;
    }

    boolean ok = EpdHelper.setAppMode(packageName, mode);
    Log.i(TAG, "addAppMode(" + packageName + ", " + mode + ")=" + ok);
    setResultData("add=" + ok);
  }

  // ── EPD_UPDATE_MODE: updateEpdMode(rawMode) only ──────────────────────

  private void handleEpdUpdateMode(Context app, Intent intent) {
    int mode = readModeExtra(intent, EpdHelper.APP_MODE_GC16);
    boolean ok = EpdHelper.updateForegroundMode(mode);
    Log.i(TAG, "updateEpdMode(" + mode + ")=" + ok);
    Log.i(TAG, "注意: updateEpdMode 只影响当前前台 App / mLastTopPackage。");
    setResultData("update=" + ok);
  }

  // ── EPD_CHECK: print current app-visible EPD state ────────────────────

  private void handleEpdCheck(Context app) {
    Log.i(TAG, "═══ EPD 调试状态 ═══");
    Log.i(TAG, "  package=" + app.getPackageName());
    Log.i(TAG, "  sys.hmct.epd_mode=" + EpdHelper.readCurrentMode(EpdHelper.APP_MODE_TYPING));
    Log.i(TAG, "═══ ADB 调试命令 ═══");
    Log.i(TAG, "  PKG=" + app.getPackageName());
    Log.i(TAG, "  设置当前阅读 App 为 GC16:");
    Log.i(TAG, "    adb shell am broadcast -n " + app.getPackageName()
        + "/cn.modificator.launcher.DebugReceiver -a " + ACTION_EPD_SET_MODE
        + " --es pkg <reader.package> --ei mode 514");
    Log.i(TAG, "  仅立即更新当前前台 App:");
    Log.i(TAG, "    adb shell am broadcast -n " + app.getPackageName()
        + "/cn.modificator.launcher.DebugReceiver -a " + ACTION_EPD_UPDATE_MODE
        + " --ei mode 514");
    Log.i(TAG, "  恢复常规模式:");
    Log.i(TAG, "    adb shell am broadcast -n " + app.getPackageName()
        + "/cn.modificator.launcher.DebugReceiver -a " + ACTION_EPD_SET_MODE
        + " --es pkg <reader.package> --ei mode 513");
  }

  // ── RENDER: 生成时钟图 + 写文件 ──────────────────────────

  private void handleRender(Context app) {
    logStep("1/3", "生成时钟图...");
    long t0 = System.currentTimeMillis();
    Bitmap bitmap = StandbyWallpaperUpdater.renderClockWallpaper(app);
    long t1 = System.currentTimeMillis();
    if (bitmap == null) {
      Log.e(TAG, "  失败: renderClockWallpaper 返回 null");
      return;
    }
    Log.i(TAG, "  完成: " + bitmap.getWidth() + "x" + bitmap.getHeight()
        + " (耗时 " + (t1 - t0) + "ms)");

    logStep("2/3", "写入本地文件...");
    File dir = new File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES), WALLPAPER_DIR);
    File target = new File(dir, WALLPAPER_FILE);
    saveBitmap(bitmap, target);
    Log.i(TAG, "  已写入: " + target.getAbsolutePath()
        + " (" + target.length() + " bytes)");

    logStep("3/3", "配置信息:");
    Config config = new Config(app);
    Log.i(TAG, "  clockStyle=" + styleName(config.getStandbyClockStyle()));
    Log.i(TAG, "  standbyWallpaperEnabled=" + config.isStandbyWallpaperEnabled());
    Log.i(TAG, "  standbyRandomWallpaperEnabled=" + config.isStandbyRandomWallpaperEnabled());
    Log.i(TAG, "  bookCoverEnabled=" + config.isBookCoverEnabled());
    Log.i(TAG, "  standbyFullRefresh=" + config.isStandbyFullRefresh());
    Log.i(TAG, "  lockText=" + config.getStandbyLockText());
    Log.i(TAG, "  lastWallpaperUpdateMinute=" + config.getLastWallpaperUpdateMinute());

    bitmap.recycle();
  }

  // ── SAVE_ONLY: 只写文件（不重新渲染，用已存在的 Bitmap 或重新渲染） ──

  private void handleSaveOnly(Context app) {
    logStep("1/2", "重新渲染时钟图...");
    Bitmap bitmap = StandbyWallpaperUpdater.renderClockWallpaper(app);
    if (bitmap == null) {
      Log.e(TAG, "  失败: renderClockWallpaper 返回 null");
      return;
    }
    Log.i(TAG, "  生成: " + bitmap.getWidth() + "x" + bitmap.getHeight());

    logStep("2/2", "写入文件 (不提交 EPD, 不刷屏)...");
    File dir = new File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES), WALLPAPER_DIR);
    File target = new File(dir, WALLPAPER_FILE);
    saveBitmap(bitmap, target);
    Log.i(TAG, "  已写入: " + target.getAbsolutePath()
        + " (" + target.length() + " bytes)");
    Log.i(TAG, "  提示: 文件已更新，开关屏或执行 FORCE_CLEAR 看效果");

    bitmap.recycle();
  }

  // ── PUBLISH: 读取 screenoff.png 并触发外屏刷新 ────────────────

  private void handlePublish(Context app) {
    logStep("1/2", "读取本地文件...");
    File dir = new File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES), WALLPAPER_DIR);
    File file = new File(dir, WALLPAPER_FILE);

    if (!file.exists()) {
      Log.e(TAG, "  文件不存在: " + file.getAbsolutePath());
      Log.i(TAG, "  正在重新生成...");
      handleFullCycle(app);
      return;
    }
    Log.i(TAG, "  文件: " + file.getAbsolutePath() + " (" + file.length() + " bytes)");

    logStep("2/2", "触发外屏合成刷新...");
    StandbyWallpaperUpdater.requestVendorRefresh(app);
  }

  // ── FORCE_CLEAR: 触发外屏刷新 ──────────────────────────────

  private void handleForceClear(Context app) {
    logStep("1/2", "检查屏幕状态...");
    boolean screenOff = StandbyWallpaperUpdater.isScreenOff(app);
    Log.i(TAG, "  isScreenOff=" + screenOff);

    logStep("2/2", "触发外屏合成刷新...");
    StandbyWallpaperUpdater.requestVendorRefresh(app);

    if (!screenOff) {
      Log.i(TAG, "  注意: 屏幕亮着，全刷效果可能看不到。试试按电源键熄屏。");
    }
  }

  // ── FULL_CYCLE: 完整流程：渲染 + 写文件 + 全刷 ────────

  private void handleFullCycle(Context app) {
    Config config = new Config(app);

    logStep("1/4", "当前配置:");
    Log.i(TAG, "  clockStyle=" + styleName(config.getStandbyClockStyle()));
    Log.i(TAG, "  standbyWallpaperEnabled=" + config.isStandbyWallpaperEnabled());
    Log.i(TAG, "  standbyFullRefresh=" + config.isStandbyFullRefresh());
    Log.i(TAG, "  standbyRandomWallpaperEnabled=" + config.isStandbyRandomWallpaperEnabled());

    logStep("2/4", "renderClockWallpaper...");
    long t0 = System.currentTimeMillis();
    Bitmap bitmap = StandbyWallpaperUpdater.renderClockWallpaper(app);
    long t1 = System.currentTimeMillis();
    if (bitmap == null) {
      Log.e(TAG, "  失败: renderClockWallpaper 返回 null");
      return;
    }
    Log.i(TAG, "  完成: " + bitmap.getWidth() + "x" + bitmap.getHeight()
        + " (耗时 " + (t1 - t0) + "ms)");

    logStep("3/4", "saveLockscreenWallpaper...");
    File dir = new File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES), WALLPAPER_DIR);
    File target = new File(dir, WALLPAPER_FILE);
    saveBitmap(bitmap, target);
    Log.i(TAG, "  已写入: " + target.getAbsolutePath()
        + " (" + target.length() + " bytes)");

    logStep("4/4", "触发外屏合成刷新...");
    StandbyWallpaperUpdater.requestVendorRefresh(app);

    bitmap.recycle();
  }

  // ── CHECK_STATE: 打印当前所有状态 ────────────────────────

  private void handleCheckState(Context app) {
    Config config = new Config(app);

    Log.i(TAG, "═══ 设备状态 ═══");
    PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
    if (pm != null) {
      boolean interactive = pm.isInteractive();
      Log.i(TAG, "  isInteractive=" + interactive + " → 屏幕"
          + (interactive ? "亮着" : "已熄"));
    }

    Log.i(TAG, "═══ 配置 ═══");
    Log.i(TAG, "  standbyWallpaperEnabled=" + config.isStandbyWallpaperEnabled());
    Log.i(TAG, "  clockStyle=" + config.getStandbyClockStyle()
        + " (" + styleName(config.getStandbyClockStyle()) + ")");
    Log.i(TAG, "  standbyFullRefresh=" + config.isStandbyFullRefresh());
    Log.i(TAG, "  standbyRandomWallpaperEnabled=" + config.isStandbyRandomWallpaperEnabled());
    Log.i(TAG, "  standbyRandomWallpaperIntervalMinutes="
        + config.getStandbyRandomWallpaperIntervalMinutes());
    Log.i(TAG, "  bookCoverEnabled=" + config.isBookCoverEnabled());
    Log.i(TAG, "  lockText=" + config.getStandbyLockText());
    Log.i(TAG, "  lastWallpaperUpdateMinute=" + config.getLastWallpaperUpdateMinute());
    Log.i(TAG, "  standbyDebugDumpEnabled=" + config.isStandbyDebugDumpEnabled());

    Log.i(TAG, "═══ 文件状态 ═══");
    File dir = new File(Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_PICTURES), WALLPAPER_DIR);
    File file = new File(dir, WALLPAPER_FILE);
    Log.i(TAG, "  目标: " + file.getAbsolutePath());
    Log.i(TAG, "  存在: " + file.exists());
    if (file.exists()) {
      Log.i(TAG, "  大小: " + file.length() + " bytes");
      Log.i(TAG, "  最后修改: " + new java.util.Date(file.lastModified()));

      // 尝试解码看看是不是合法的图
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
      Log.i(TAG, "  图片尺寸: " + opts.outWidth + "x" + opts.outHeight);
    }

    Log.i(TAG, "═══ EPD 服务 ═══");
    Object epd = app.getSystemService("epd");
    Log.i(TAG, "  getSystemService(epd)=" + (epd != null ? "可用" : "不可用"));
    if (epd != null) {
      Log.i(TAG, "  类名: " + epd.getClass().getName());
    }

    Log.i(TAG, "═══ 建议命令 ═══");
    Log.i(TAG, "  RENDER:        adb shell am broadcast -a cn.modificator.launcher.debug.RENDER");
    Log.i(TAG, "  SAVE_ONLY:     adb shell am broadcast -a cn.modificator.launcher.debug.SAVE_ONLY");
    Log.i(TAG, "  PUBLISH:       adb shell am broadcast -a cn.modificator.launcher.debug.PUBLISH");
    Log.i(TAG, "  FORCE_CLEAR:   adb shell am broadcast -a cn.modificator.launcher.debug.FORCE_CLEAR");
    Log.i(TAG, "  FULL_CYCLE:    adb shell am broadcast -a cn.modificator.launcher.debug.FULL_CYCLE");
    Log.i(TAG, "  CHECK_STATE:   adb shell am broadcast -a cn.modificator.launcher.debug.CHECK_STATE");
  }

  // ── 工具方法 ────────────────────────────────────────────

  private void logStep(String step, String desc) {
    Log.i(TAG, "── " + step + " " + desc);
  }

  private String readPackageExtra(Intent intent) {
    String packageName = intent.getStringExtra(EXTRA_PACKAGE);
    if (packageName == null || packageName.length() == 0) {
      packageName = intent.getStringExtra(EXTRA_PACKAGE_ALT);
    }
    return packageName == null ? "" : packageName.trim();
  }

  private int readModeExtra(Intent intent, int fallback) {
    if (intent.hasExtra(EXTRA_MODE)) {
      return intent.getIntExtra(EXTRA_MODE, fallback);
    }
    return fallback;
  }

  private void saveBitmap(Bitmap bitmap, File target) {
    java.io.FileOutputStream out = null;
    try {
      target.getParentFile().mkdirs();
      File tmp = new File(target.getParentFile(), target.getName() + ".tmp");
      out = new java.io.FileOutputStream(tmp);
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
      out.flush();
      out.close();
      out = null;
      if (target.exists()) target.delete();
      tmp.renameTo(target);
    } catch (Exception e) {
      Log.e(TAG, "saveBitmap 失败", e);
    } finally {
      if (out != null) {
        try { out.close(); } catch (Exception ignored) {}
      }
    }
  }

  private String styleName(int style) {
    switch (style) {
      case Config.STANDBY_CLOCK_STYLE_CLASSIC:               return "经典";
      case Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE:        return "横屏翻页钟";
      case Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT:  return "横屏翻页钟(浅色)";
      case Config.STANDBY_CLOCK_STYLE_CLASSIC_HOLLOW:        return "经典镂空";
      case Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE:     return "极简横屏";
      default:                                               return "未知(" + style + ")";
    }
  }
}
