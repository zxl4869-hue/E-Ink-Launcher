package cn.modificator.launcher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import cn.modificator.launcher.legado.LegadoHomeController;
import cn.modificator.launcher.model.AdminReceiver;
import cn.modificator.launcher.widgets.BatteryView;
import cn.modificator.launcher.widgets.StandbyFlipClockView;

public class StandbyActivity extends Activity {
  private static final String TAG = "StandbyActivity";
  public static final String EXTRA_LOCK_AFTER_RENDER =
      "cn.modificator.launcher.extra.LOCK_AFTER_RENDER";

  private static final int REQUEST_DEVICE_ADMIN = 12001;
  private static final int REQUEST_STORAGE = 12002;
  private static final long LOCK_DELAY_MS = 1300L;
  private static final long LOCK_AFTER_REFRESH_DELAY_MS = 250L;
  private static final int LOCKSCREEN_WALLPAPER_BASE_WIDTH = 480;
  private static final int LOCKSCREEN_WALLPAPER_BASE_HEIGHT = 656;
  private static final String LOCKSCREEN_WALLPAPER_DIR = ".lockscreen_wallpaper";
  private static final String LOCKSCREEN_WALLPAPER_FILE = "screenoff.png";
  private static final String LOCKSCREEN_WALLPAPER_BACKUP_FILE = "screenoff.png.bak";

  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Calendar calendar = Calendar.getInstance();
  private final LegadoHomeController legadoHomeController = new LegadoHomeController();

  private TextView timeText;
  private TextView dateText;
  private TextView lunarText;
  private TextView batteryText;
  private TextView batteryStateText;
  private TextView modeText;
  private BatteryView batteryView;
  private StandbyFlipClockView flipClockView;
  private DevicePolicyManager policyManager;
  private boolean lockAfterRender;
  private boolean batteryRegistered;
  private boolean timeRegistered;
  private boolean flipClockStyle;

  private final BroadcastReceiver timeReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateTime();
    }
  };

  private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      updateBattery(intent);
    }
  };

  public static Intent previewIntent(Context context) {
    return new Intent(context, StandbyActivity.class);
  }

  public static Intent lockIntent(Context context) {
    Intent intent = new Intent(context, StandbyActivity.class);
    intent.putExtra(EXTRA_LOCK_AFTER_RENDER, true);
    return intent;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    lockAfterRender = getIntent().getBooleanExtra(EXTRA_LOCK_AFTER_RENDER, false);
    policyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    setContentView(R.layout.activity_standby);
    applySystemUiVisibility();
    initViews();
    updateTime();
    updateBattery(getBatteryStatus());
    legadoHomeController.refresh(this);

    if (lockAfterRender) {
      if (hasStoragePermission()) {
        scheduleLock();
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
      } else {
        scheduleLock();
      }
    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (intent != null && intent.getBooleanExtra(EXTRA_LOCK_AFTER_RENDER, false)) {
      lockAfterRender = true;
      if (modeText != null) {
        modeText.setText("待机锁屏");
      }
      if (hasStoragePermission()) {
        scheduleLock();
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_STORAGE);
      } else {
        scheduleLock();
      }
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    applySystemUiVisibility();
    registerReceivers();
    updateTime();
    updateBattery(getBatteryStatus());
  }

  @Override
  protected void onPause() {
    super.onPause();
    unregisterReceivers();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    handler.removeCallbacksAndMessages(null);
    unregisterReceivers();
  }

  private void initViews() {
    timeText = findViewById(R.id.standbyTime);
    dateText = findViewById(R.id.standbyDate);
    lunarText = findViewById(R.id.standbyLunar);
    batteryText = findViewById(R.id.standbyBatteryLevel);
    batteryStateText = findViewById(R.id.standbyBatteryState);
    batteryView = findViewById(R.id.standbyBatteryIcon);
    modeText = findViewById(R.id.standbyMode);
    Config config = new Config(this);
    int clockStyle = config.getStandbyClockStyle();
    flipClockStyle = !config.isWereadWallpaperEnabled() && isFlipClockStyle(clockStyle);
    if (flipClockStyle) {
      installFlipClockView(clockStyle);
    }
    if (modeText != null) {
      modeText.setText(lockAfterRender ? "待机锁屏" : "待机页预览");
    }
  }

  private void installFlipClockView(int clockStyle) {
    LinearLayout root = findViewById(R.id.standbyRoot);
    if (root == null) return;
    root.removeAllViews();
    root.setOrientation(LinearLayout.VERTICAL);
    root.setGravity(android.view.Gravity.CENTER);
    int horizontalPadding = dp(16);
    int verticalPadding = dp(12);
    root.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
    flipClockView = new StandbyFlipClockView(this);
    flipClockView.setLightStyle(clockStyle == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT);
    flipClockView.setMinimalStyle(clockStyle == Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE);
    root.addView(flipClockView, new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    timeText = null;
    dateText = null;
    lunarText = null;
    batteryText = null;
    batteryStateText = null;
    batteryView = null;
    modeText = null;
  }

  private boolean isFlipClockStyle(int style) {
    return style == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE
        || style == Config.STANDBY_CLOCK_STYLE_FLIP_LANDSCAPE_LIGHT
        || style == Config.STANDBY_CLOCK_STYLE_MINIMAL_LANDSCAPE;
  }

  private void updateTime() {
    calendar.setTimeInMillis(System.currentTimeMillis());
    boolean is24Hour = DateFormat.is24HourFormat(this);
    String timePattern = is24Hour ? "HH:mm" : "hh:mm";
    String time = new SimpleDateFormat(timePattern, Locale.getDefault()).format(calendar.getTime());
    String date = new SimpleDateFormat("M月d日 EEEE", Locale.getDefault()).format(calendar.getTime());
    if (timeText != null) timeText.setText(time);
    if (dateText != null) dateText.setText(date);
    if (lunarText != null) {
      lunarText.setText(Utils.getAMPMCNString(calendar.get(Calendar.HOUR),
          calendar.get(Calendar.AM_PM)));
    }
    if (flipClockView != null) {
      flipClockView.setTimeDate(time, date);
    }
  }

  private Intent getBatteryStatus() {
    return registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
  }

  private void updateBattery(Intent intent) {
    if (intent == null) return;
    int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
    int level = (rawLevel >= 0 && scale > 0) ? rawLevel * 100 / scale : -1;
    boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING
        || status == BatteryManager.BATTERY_STATUS_FULL;

    if (batteryView != null) {
      batteryView.setProgress(level);
      batteryView.setCharging(charging);
    }
    if (batteryText != null) {
      batteryText.setText(level >= 0 ? level + "%" : "--");
    }
    if (batteryStateText != null) {
      batteryStateText.setText(getBatteryStateText(status));
    }
  }

  private String getBatteryStateText(int status) {
    switch (status) {
      case BatteryManager.BATTERY_STATUS_CHARGING:
        return "充电中";
      case BatteryManager.BATTERY_STATUS_FULL:
        return "已充满";
      case BatteryManager.BATTERY_STATUS_DISCHARGING:
        return "使用电池";
      case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
        return "未充电";
      case BatteryManager.BATTERY_STATUS_UNKNOWN:
      default:
        return "电量状态";
    }
  }

  private void scheduleLock() {
    handler.removeCallbacksAndMessages(null);
    Config config = new Config(this);
    if (!config.isWereadWallpaperEnabled()
        && !config.isStandbyWallpaperEnabled()
        && config.isBookCoverEnabled()
        && ScreenCaptureManager.getInstance().isReady()) {
      scheduleBookCoverLock(config);
    } else {
      scheduleNormalLock();
    }
  }

  private void scheduleBookCoverLock(final Config config) {
    final View root = findViewById(R.id.standbyRoot);
    if (root != null) root.setVisibility(View.INVISIBLE);
    // Make window translucent so capture sees activity behind
    getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
    getWindow().getDecorView().setBackgroundColor(Color.TRANSPARENT);

    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        ScreenCaptureManager.getInstance().captureScreen(
            new ScreenCaptureManager.CaptureCallback() {
              @Override
              public void onCaptured(Bitmap screenshot) {
                Bitmap composited = compositeOverlay(StandbyActivity.this, screenshot);
                if (composited != null) {
                  StandbyWallpaperUpdater.update(StandbyActivity.this, composited,
                      config.getStandbyLockText(), true, true);
                  composited.recycle();
                }
                if (screenshot != null) screenshot.recycle();
                StandbyMinuteRefreshReceiver.scheduleNextForMode(StandbyActivity.this);
                handler.postDelayed(new Runnable() {
                  @Override
                  public void run() {
                    lockNow();
                  }
                }, LOCK_AFTER_REFRESH_DELAY_MS);
              }

              @Override
              public void onError(String error) {
                Log.w(TAG, "Book cover capture failed: " + error);
                if (root != null) root.setVisibility(View.VISIBLE);
                publishStandbyPaper();
                StandbyMinuteRefreshReceiver.scheduleNextForMode(StandbyActivity.this);
                handler.postDelayed(new Runnable() {
                  @Override
                  public void run() {
                    lockNow();
                  }
                }, LOCK_AFTER_REFRESH_DELAY_MS);
              }
            });
      }
    }, 500L);
  }

  private void scheduleNormalLock() {
    Config config = new Config(this);
    if (config.isWereadWallpaperEnabled()) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          publishStandbyPaper();
          StandbyMinuteRefreshReceiver.scheduleNextForMode(StandbyActivity.this);
          handler.postDelayed(new Runnable() {
            @Override
            public void run() {
              lockNow();
            }
          }, LOCK_AFTER_REFRESH_DELAY_MS);
        }
      }, "WereadStandbyActivityLock").start();
      return;
    }
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        publishStandbyPaper();
        StandbyMinuteRefreshReceiver.scheduleNextForMode(StandbyActivity.this);
        handler.postDelayed(new Runnable() {
          @Override
          public void run() {
            lockNow();
          }
        }, LOCK_AFTER_REFRESH_DELAY_MS);
      }
    }, LOCK_DELAY_MS);
  }

  private static Bitmap compositeOverlay(Context context, Bitmap screenshot) {
    if (screenshot == null) return null;
    try {
      Bitmap result = Bitmap.createBitmap(screenshot.getWidth(), screenshot.getHeight(),
          Bitmap.Config.RGB_565);
      Canvas canvas = new Canvas(result);
      canvas.drawColor(Color.WHITE);
      canvas.drawBitmap(screenshot, 0, 0, null);

      Config config = new Config(context);
      String overlayDir = config.getBookCoverOverlayDir();
      Bitmap overlay = OverlayImageLoader.loadOverlay(overlayDir, config.isBookCoverRandom());
      if (overlay != null) {
        android.graphics.RectF dst = new android.graphics.RectF(0, 0,
            result.getWidth(), result.getHeight());
        canvas.drawBitmap(overlay, null, dst, null);
        overlay.recycle();
      }

      return result;
    } catch (Exception e) {
      Log.w(TAG, "compositeOverlay failed", e);
      return null;
    }
  }

  private void publishStandbyPaper() {
    Config config = new Config(this);
    boolean showTime = config.isStandbyWallpaperEnabled();
    boolean published = StandbyWallpaperUpdater.update(this, null,
        config.getStandbyLockText(), true, showTime);
    config.setLastWallpaperUpdateMinute(System.currentTimeMillis() / 60000L);
    Log.i(TAG, "publishStandbyPaper showTime=" + showTime + " published=" + published
        + " style=" + config.getStandbyClockStyle());
  }

  private int dp(float value) {
    return Math.round(value * getResources().getDisplayMetrics().density);
  }

  private Bitmap drawViewBitmap(View view) {
    Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.RGB_565);
    Canvas canvas = new Canvas(bitmap);
    canvas.drawColor(Color.WHITE);
    view.draw(canvas);
    return bitmap;
  }

  private boolean hasStoragePermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        || checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        == PackageManager.PERMISSION_GRANTED;
  }

  private void saveLockscreenWallpaper(Bitmap source) {
    Bitmap wallpaper = createLockscreenWallpaper(source);
    FileOutputStream out = null;
    try {
      File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
          LOCKSCREEN_WALLPAPER_DIR);
      if (!dir.exists() && !dir.mkdirs()) {
        Log.w(TAG, "Unable to create lockscreen wallpaper dir: " + dir);
        return;
      }

      File target = new File(dir, LOCKSCREEN_WALLPAPER_FILE);
      File backup = new File(dir, LOCKSCREEN_WALLPAPER_BACKUP_FILE);
      if (target.exists() && !backup.exists()) {
        copyFile(target, backup);
      }

      File tmp = new File(dir, LOCKSCREEN_WALLPAPER_FILE + ".tmp");
      out = new FileOutputStream(tmp);
      wallpaper.compress(Bitmap.CompressFormat.JPEG, 92, out);
      out.flush();
      closeQuietly(out);
      out = null;

      if (target.exists() && !target.delete()) {
        Log.w(TAG, "Unable to delete old lockscreen wallpaper: " + target);
      }
      if (!tmp.renameTo(target)) {
        Log.w(TAG, "Unable to replace lockscreen wallpaper: " + target);
        return;
      }
      Log.i(TAG, "Saved lockscreen wallpaper: " + target + " "
          + wallpaper.getWidth() + "x" + wallpaper.getHeight());
    } catch (Exception e) {
      Log.w(TAG, "Failed to save lockscreen wallpaper", e);
    } finally {
      closeQuietly(out);
      wallpaper.recycle();
    }
  }

  private Bitmap createLockscreenWallpaper(Bitmap source) {
    int targetWidth = source.getWidth();
    int targetHeight = Math.round(targetWidth
        * LOCKSCREEN_WALLPAPER_BASE_HEIGHT / (float) LOCKSCREEN_WALLPAPER_BASE_WIDTH);
    if (targetHeight <= 0 || targetHeight > source.getHeight()) {
      targetHeight = Math.min(source.getHeight(), LOCKSCREEN_WALLPAPER_BASE_HEIGHT);
    }
    Bitmap target = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.RGB_565);
    Canvas canvas = new Canvas(target);
    canvas.drawColor(Color.WHITE);
    Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    Rect src = new Rect(0, 0, source.getWidth(), source.getHeight());
    RectF dst = new RectF(0, 0, targetWidth, targetHeight);
    canvas.drawBitmap(source, src, dst, paint);
    return target;
  }

  private void copyFile(File source, File target) throws IOException {
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

  private void closeQuietly(java.io.Closeable closeable) {
    if (closeable == null) return;
    try {
      closeable.close();
    } catch (IOException ignored) {
    }
  }

  private boolean publishViaEpdManager(Bitmap bitmap) {
    Object manager = getSystemService("epd");
    if (manager != null && tryPublishWithManager(manager, bitmap)) {
      return true;
    }
    Object service = getEpdBinderService();
    return service != null && tryPublishWithBinderService(service, bitmap);
  }

  private Object getEpdBinderService() {
    try {
      Class<?> serviceManager = Class.forName("android.os.ServiceManager");
      Method getService = serviceManager.getDeclaredMethod("getService", String.class);
      IBinder binder = (IBinder) getService.invoke(null, "epd");
      if (binder == null) return null;
      Class<?> stub = Class.forName("com.hmct.epd.IEpdManager$Stub");
      Method asInterface = stub.getDeclaredMethod("asInterface", IBinder.class);
      return asInterface.invoke(null, binder);
    } catch (Exception e) {
      Log.w(TAG, "Unable to get epd binder service", e);
      return null;
    }
  }

  private boolean tryPublishWithManager(Object manager, Bitmap bitmap) {
    boolean added = tryInvoke(manager, "addBitmapOnExternal",
        new Class<?>[]{Bitmap.class, int.class, int.class, int.class, boolean.class, String.class},
        new Object[]{bitmap, 0, 0, 0, true, getPackageName()});
    if (!added) {
      added = tryInvoke(manager, "addBitmapOnExternal",
          new Class<?>[]{Bitmap.class, int.class, int.class, int.class, boolean.class},
          new Object[]{bitmap, 0, 0, 0, true});
    }
    if (!added) {
      added = tryInvoke(manager, "addBitmapOnExternal",
          new Class<?>[]{Bitmap.class, int.class, int.class, int.class},
          new Object[]{bitmap, 0, 0, 0});
    }
    if (!added) {
      added = tryInvoke(manager, "addBitmapOnExternal",
          new Class<?>[]{Bitmap.class, int.class},
          new Object[]{bitmap, 0});
    }
    if (added) {
      tryInvoke(manager, "updateExteralPaper", new Class<?>[]{}, new Object[]{});
      tryInvoke(manager, "commitBitmapForThirdApp", new Class<?>[]{}, new Object[]{});
    }
    return added;
  }

  private boolean tryPublishWithBinderService(Object service, Bitmap bitmap) {
    boolean added = tryInvoke(service, "addBitmapToPresentationDisplayWithString",
        new Class<?>[]{Bitmap.class, int.class, int.class, int.class, boolean.class, String.class},
        new Object[]{bitmap, 0, 0, 0, true, getPackageName()});
    if (!added) {
      added = tryInvoke(service, "addBitmapToPresentationDisplay",
          new Class<?>[]{Bitmap.class, int.class, int.class, int.class, boolean.class},
          new Object[]{bitmap, 0, 0, 0, true});
    }
    if (added) {
      tryInvoke(service, "updateExteralPaper", new Class<?>[]{}, new Object[]{});
      tryInvoke(service, "commitBitmapForThirdApp", new Class<?>[]{}, new Object[]{});
    }
    return added;
  }

  private boolean tryInvoke(Object target, String methodName, Class<?>[] parameterTypes,
                            Object[] args) {
    try {
      Method method = target.getClass().getMethod(methodName, parameterTypes);
      method.invoke(target, args);
      Log.i(TAG, "epd " + methodName + " succeeded on " + target.getClass().getName());
      return true;
    } catch (NoSuchMethodException ignored) {
      return false;
    } catch (Exception e) {
      Log.w(TAG, "epd " + methodName + " failed on " + target.getClass().getName(), e);
      return false;
    }
  }

  private void lockNow() {
    try {
      ComponentName admin = new ComponentName(this, AdminReceiver.class);
      if (policyManager != null && policyManager.isAdminActive(admin)) {
        policyManager.lockNow();
      } else {
        requestDeviceAdmin(admin);
      }
    } catch (Exception e) {
      showDeviceAdminDialog();
    }
  }

  private void requestDeviceAdmin(ComponentName admin) {
    Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin);
    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "允许桌面显示待机页后自动锁屏");
    try {
      startActivityForResult(intent, REQUEST_DEVICE_ADMIN);
    } catch (Exception e) {
      showDeviceAdminDialog();
    }
  }

  private void showDeviceAdminDialog() {
    new AlertDialog.Builder(this)
        .setTitle(R.string.launch_failed)
        .setMessage(R.string.launch_devicemanager_failed)
        .setPositiveButton(R.string.launch_devicemanager, new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            try {
              Intent intent = Intent.parseUri(
                  "intent:#Intent;component=com.android.settings/.DeviceAdminSettings;end",
                  Intent.URI_INTENT_SCHEME);
              intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              startActivity(intent);
            } catch (Exception e) {
              Toast.makeText(StandbyActivity.this, R.string.launch_failed, Toast.LENGTH_SHORT).show();
            }
          }
        })
        .setNegativeButton(R.string.dialog_cancel, null)
        .show();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_DEVICE_ADMIN && resultCode == RESULT_OK) {
      scheduleLock();
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_STORAGE && lockAfterRender) {
      scheduleLock();
    }
  }

  private void registerReceivers() {
    if (!batteryRegistered) {
      Utils.registerReceiverCompat(this, batteryReceiver,
          new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
      batteryRegistered = true;
    }
    if (!timeRegistered) {
      Utils.registerReceiverCompat(this, timeReceiver, new IntentFilter(Intent.ACTION_TIME_TICK));
      timeRegistered = true;
    }
  }

  private void unregisterReceivers() {
    if (batteryRegistered) {
      safeUnregisterReceiver(batteryReceiver);
      batteryRegistered = false;
    }
    if (timeRegistered) {
      safeUnregisterReceiver(timeReceiver);
      timeRegistered = false;
    }
  }

  private void safeUnregisterReceiver(BroadcastReceiver receiver) {
    try {
      unregisterReceiver(receiver);
    } catch (IllegalArgumentException ignored) {
    }
  }

  private void applySystemUiVisibility() {
    View decor = getWindow().getDecorView();
    decor.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }
}
