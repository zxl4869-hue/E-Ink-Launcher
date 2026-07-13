package cn.modificator.launcher.autorefresh;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Map;

import cn.modificator.launcher.Config;
import cn.modificator.launcher.OverlayImageLoader;
import cn.modificator.launcher.ScreenCaptureManager;
import cn.modificator.launcher.StandbyMinuteRefreshReceiver;
import cn.modificator.launcher.StandbyWallpaperUpdater;
import cn.modificator.launcher.model.AdminReceiver;

public class AutoRefreshAccessibilityService extends AccessibilityService
    implements SensorEventListener, SharedPreferences.OnSharedPreferenceChangeListener {

  private static final String TAG = AutoRefreshAccessibilityService.class.getSimpleName();
  private static final long MIN_REFRESH_INTERVAL_MS = 800;
  private static final long CONTENT_CHANGE_DEBOUNCE_MS = 900;
  private static final long KEY_CONTENT_SKIP_WINDOW_MS = 2500;
  private static final long WINDOW_CHANGE_GRACE_MS = 600;
  private static final long LONG_PRESS_MS = 800;
  private static final long TILT_SEQUENCE_TIMEOUT_MS = 2000;
  private static final long INJECTED_VOLUME_IGNORE_MS = 1500;

  private int actionCount;
  private long lastRefreshUptime;
  private long lastContentChangeTime;
  private long lastWindowChangeTime;
  private long skipContentChangeUntil;
  private long volumeDownDownTime;
  private long sosDownTime;
  private boolean warnedMissingKeyCode;

  /** Whether EPD is currently in frame-by-frame full refresh mode (GC16). */
  private static volatile boolean isFrameRefreshMode;
  private final Object frameRefreshLock = new Object();
  private final Map<String, Integer> frameRefreshPreviousModes = new HashMap<String, Integer>();
  private String frameRefreshActivePackage = "";
  private String lastPackageName = "";
  private Handler handler;
  private Runnable pendingRefresh;
  private boolean userPresentRegistered;
  private SensorManager sensorManager;
  private Sensor motionSensor;
  private boolean motionSensorRegistered;
  private final DirectionalTiltDetector tiltDetector = new DirectionalTiltDetector();
  private int pendingTiltDirection;
  private int pendingTiltCount;
  private long lastTiltSequenceUptime;
  private long ignoreInjectedVolumeUntil;

  private final BroadcastReceiver userPresentReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      Config config = new Config(context);
      if (config.isStandbyRefreshEnabled()) {
        boolean showTime = config.isStandbyWallpaperEnabled();
        Log.i(TAG, "ACTION_USER_PRESENT: restoring standby + resuming refresh showTime="
            + showTime);
        StandbyWallpaperUpdater.update(context, null, config.getStandbyLockText(),
            config.isStandbyFullRefresh(), showTime);
        StandbyMinuteRefreshReceiver.scheduleNextForMode(context);
      } else {
        Log.i(TAG, "ACTION_USER_PRESENT: standby disabled, clearing EPD");
        StandbyWallpaperUpdater.update(context, null, config.getStandbyLockText(),
            false, false);
      }
    }
  };

  @Override
  protected void onServiceConnected() {
    super.onServiceConnected();
    Log.i(TAG, "Auto refresh accessibility service connected");
    handler = new Handler(Looper.getMainLooper());
    ScreenCaptureManager.getInstance().init(this);
    AccessibilityServiceInfo info = getServiceInfo();
    if (info != null) {
      info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
      info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
      info.flags |= AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
      setServiceInfo(info);
    }
    registerUserPresentReceiver();
    AutoRefreshSettings.registerPreferenceListener(this, this);
    updateMotionSensorRegistration();
  }

  private void registerUserPresentReceiver() {
    if (userPresentRegistered) return;
    IntentFilter filter = new IntentFilter(Intent.ACTION_USER_PRESENT);
    registerReceiver(userPresentReceiver, filter);
    userPresentRegistered = true;
    Log.i(TAG, "Registered ACTION_USER_PRESENT receiver");
  }

  @Override
  public boolean onKeyEvent(KeyEvent event) {
    if (event == null) return false;

    int keyCode = event.getKeyCode();
    int action = event.getAction();
    boolean volumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP
        || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN;
    if (volumeKey && SystemClock.uptimeMillis() <= ignoreInjectedVolumeUntil) {
      return false;
    }

    // Track volume down press time for long-press detection
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && action == KeyEvent.ACTION_DOWN) {
      volumeDownDownTime = SystemClock.uptimeMillis();
    }
    // Track SOS key (419) press time for long-press detection
    if (keyCode == 419 && action == KeyEvent.ACTION_DOWN) {
      sosDownTime = SystemClock.uptimeMillis();
    }
    if (volumeKey && action == KeyEvent.ACTION_DOWN) {
      ignoreContentChangesFromKey();
    }

    if (action != KeyEvent.ACTION_UP) {
      return false;
    }

    if (AutoRefreshSettings.shouldCaptureNextKey(this)
        && keyCode != KeyEvent.KEYCODE_VOLUME_UP
        && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
        && keyCode != 419) {
      AutoRefreshSettings.setRefreshKeyCode(this, keyCode);
      AutoRefreshSettings.setCaptureNextKey(this, false);
      Toast.makeText(this, "已记录刷新按键 keycode: " + keyCode, Toast.LENGTH_SHORT).show();
      return false;
    }
    // SOS key long-press: toggle frame-by-frame full refresh mode
    if (keyCode == 419 && sosDownTime > 0) {
      long duration = SystemClock.uptimeMillis() - sosDownTime;
      sosDownTime = 0;
      if (duration >= LONG_PRESS_MS) {
        // Always allow toggling off when currently active, so user can
        // disable the mode even after the setting has been turned off.
        if (AutoRefreshSettings.isSosFrameRefreshEnabled(this) || isFrameRefreshMode) {
          toggleFrameRefreshMode();
        }
        return false;
      }
      // Short press SOS: do nothing special (pass through)
      return false;
    }

    if (!volumeKey) {
      return false;
    }
    if (!AutoRefreshSettings.isEnabled(this)) {
      return false;
    }
    if (isOwnPackage(currentPackageName())) {
      return false;
    }

    // Long-press volume down (hold then lift) → book cover lock
    if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && volumeDownDownTime > 0) {
      long duration = SystemClock.uptimeMillis() - volumeDownDownTime;
      volumeDownDownTime = 0;
      if (duration >= LONG_PRESS_MS) {
        Log.i(TAG, "Long-press volume down: " + duration + "ms → book cover lock");
        triggerBookCoverLock();
        return false;
      }
      // Short press: fall through to normal counting
    }

    // When in frame-by-frame full refresh mode, each frame is already
    // fully refreshed by the display controller — skip forced refreshes.
    if (isFrameRefreshMode) {
      return false;
    }

    ignoreContentChangesFromKey();
    incrementActionCount(AutoRefreshSettings.getInterval(this));
    return false;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event == null) return;
    int eventType = event.getEventType();

    if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
      String pkg = event.getPackageName() != null ? event.getPackageName().toString() : "";
      if (!lastPackageName.equals(pkg)) {
        actionCount = 0;
        lastPackageName = pkg;
        skipContentChangeUntil = 0;
      }
      lastWindowChangeTime = SystemClock.uptimeMillis();
      if (isFrameRefreshMode && pkg.length() > 0 && !isOwnPackage(pkg)) {
        applyFrameRefreshMode(pkg, false);
      }
      return;
    }

    if (!AutoRefreshSettings.isEnabled(this) || !AutoRefreshSettings.isTapEnabled(this)
        || isFrameRefreshMode) {
      return;
    }

    if (!isContentRefreshEvent(eventType)) {
      return;
    }

    String pkg = event.getPackageName() != null ? event.getPackageName().toString() : lastPackageName;
    if (isOwnPackage(pkg)) {
      return;
    }

    long now = SystemClock.uptimeMillis();
    if (now - lastContentChangeTime < CONTENT_CHANGE_DEBOUNCE_MS) {
      return;
    }

    if (now <= skipContentChangeUntil) {
      lastContentChangeTime = now;
      return;
    }

    if (lastWindowChangeTime > 0 && now - lastWindowChangeTime < WINDOW_CHANGE_GRACE_MS) {
      return;
    }

    if (!isMeaningfulContentChange(event)) {
      return;
    }

    lastContentChangeTime = now;
    incrementActionCount(AutoRefreshSettings.getTapInterval(this));
  }

  @Override
  public void onInterrupt() {
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
    resetTiltDetection();
    updateMotionSensorRegistration();
  }

  @Override
  public void onSensorChanged(SensorEvent event) {
    if (event == null || event.sensor == null || motionSensor == null
        || event.sensor.getType() != motionSensor.getType()) return;
    int mode = AutoRefreshSettings.getShakeActionMode(this);
    if (mode == AutoRefreshSettings.SHAKE_ACTION_OFF) return;

    int rotation = getDisplayRotation();
    int direction;
    if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
      direction = tiltDetector.addGyroscopeSample(event.timestamp,
          event.values[0], event.values[1], rotation,
          AutoRefreshSettings.getGyroscopeThresholdDps(this));
    } else {
      direction = tiltDetector.addAccelerometerSample(event.timestamp,
          event.values[0], event.values[1], event.values[2], rotation,
          AutoRefreshSettings.getGyroscopeThresholdDps(this));
    }
    if (direction == DirectionalTiltDetector.NONE) return;

    int requiredCount = AutoRefreshSettings.getShakeRefreshCount(this);
    long now = SystemClock.uptimeMillis();
    if (requiredCount > 1) {
      if (direction != pendingTiltDirection
          || now - lastTiltSequenceUptime > TILT_SEQUENCE_TIMEOUT_MS) {
        pendingTiltDirection = direction;
        pendingTiltCount = 1;
      } else {
        pendingTiltCount++;
      }
      lastTiltSequenceUptime = now;
      if (pendingTiltCount < requiredCount) {
        Log.i(TAG, "Directional flick accepted, waiting for "
            + (requiredCount - pendingTiltCount) + " more");
        return;
      }
    }
    resetTiltSequence();
    handleDirectionalTilt(mode, direction);
  }

  @Override
  public void onAccuracyChanged(Sensor sensor, int accuracy) {
  }

  @Override
  public void onDestroy() {
    AutoRefreshSettings.unregisterPreferenceListener(this, this);
    unregisterMotionSensor();
    if (userPresentRegistered) {
      safeUnregisterReceiver(userPresentReceiver);
      userPresentRegistered = false;
    }
    if (isFrameRefreshMode) {
      restoreFrameRefreshModes();
    }
    if (handler != null) {
      handler.removeCallbacksAndMessages(null);
    }
    super.onDestroy();
  }

  private void updateMotionSensorRegistration() {
    if (AutoRefreshSettings.getShakeActionMode(this)
        == AutoRefreshSettings.SHAKE_ACTION_OFF) {
      unregisterMotionSensor();
      return;
    }
    if (sensorManager == null) {
      sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
    }
    if (sensorManager == null) return;
    Sensor preferred = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    if (preferred == null) {
      preferred = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }
    if (preferred == null) {
      unregisterMotionSensor();
      return;
    }
    if (motionSensorRegistered && motionSensor != null
        && motionSensor.getType() == preferred.getType()) return;
    unregisterMotionSensor();
    motionSensor = preferred;
    motionSensorRegistered = sensorManager.registerListener(
        this, motionSensor, 50_000);
    Log.i(TAG, "Directional tilt sensor registered=" + motionSensorRegistered
        + ", type=" + motionSensor.getType());
  }

  private void unregisterMotionSensor() {
    if (sensorManager != null && motionSensorRegistered) {
      sensorManager.unregisterListener(this);
    }
    motionSensorRegistered = false;
    motionSensor = null;
    resetTiltDetection();
  }

  private void resetTiltDetection() {
    tiltDetector.reset();
    resetTiltSequence();
  }

  private void resetTiltSequence() {
    pendingTiltDirection = DirectionalTiltDetector.NONE;
    pendingTiltCount = 0;
    lastTiltSequenceUptime = 0;
  }

  private int getDisplayRotation() {
    WindowManager windowManager =
        (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    if (windowManager == null || windowManager.getDefaultDisplay() == null) {
      return Surface.ROTATION_0;
    }
    return windowManager.getDefaultDisplay().getRotation();
  }

  private void handleDirectionalTilt(int mode, int direction) {
    String action = direction == DirectionalTiltDetector.FORWARD ? "next" : "previous";
    if (mode == AutoRefreshSettings.SHAKE_ACTION_PAGE_TURN) {
      int keyCode = direction == DirectionalTiltDetector.FORWARD
          ? KeyEvent.KEYCODE_VOLUME_DOWN : KeyEvent.KEYCODE_VOLUME_UP;
      ignoreInjectedVolumeUntil = SystemClock.uptimeMillis() + INJECTED_VOLUME_IGNORE_MS;
      Log.i(TAG, "Directional flick " + action + ", injecting keycode=" + keyCode);
      sendRefreshKeyEvent(keyCode);
      return;
    }
    Log.i(TAG, "Directional flick " + action + ", triggering full refresh");
    triggerRefresh();
  }

  private void safeUnregisterReceiver(BroadcastReceiver receiver) {
    try {
      unregisterReceiver(receiver);
    } catch (IllegalArgumentException ignored) {
    }
  }

  private void triggerRefresh() {
    if (isFrameRefreshMode) return;
    if (pendingRefresh != null) {
      handler.removeCallbacks(pendingRefresh);
    }
    int delayMs = AutoRefreshSettings.getDelay(this);
    pendingRefresh = new Runnable() {
      @Override
      public void run() {
        pendingRefresh = null;
        if (isFrameRefreshMode) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastRefreshUptime < MIN_REFRESH_INTERVAL_MS) {
          return;
        }
        lastRefreshUptime = now;

        if (EpdRefresher.forceClear()) {
          return;
        }

        int refreshKeyCode = AutoRefreshSettings.getRefreshKeyCode(AutoRefreshAccessibilityService.this);
        if (refreshKeyCode <= 0) {
          if (!warnedMissingKeyCode) {
            warnedMissingKeyCode = true;
            Toast.makeText(AutoRefreshAccessibilityService.this, "未设置刷新按键 keycode", Toast.LENGTH_SHORT).show();
          }
          return;
        }
        sendRefreshKeyEvent(refreshKeyCode);
      }
    };
    handler.postDelayed(pendingRefresh, delayMs);
  }

  private void incrementActionCount(int interval) {
    actionCount++;
    if (actionCount >= Math.max(1, interval)) {
      actionCount = 0;
      triggerRefresh();
    }
  }

  private void ignoreContentChangesFromKey() {
    skipContentChangeUntil = SystemClock.uptimeMillis() + KEY_CONTENT_SKIP_WINDOW_MS;
  }

  private boolean isContentRefreshEvent(int eventType) {
    return eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        || eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED;
  }

  private boolean isMeaningfulContentChange(AccessibilityEvent event) {
    AccessibilityNodeInfo source = null;
    try {
      source = event.getSource();
      if (source == null) {
        return true;
      }
      Rect r = new Rect();
      source.getBoundsInScreen(r);
      return r.width() >= 100 && r.height() >= 100;
    } catch (Throwable ignored) {
      return true;
    } finally {
      if (source != null) {
        source.recycle();
      }
    }
  }

  private boolean isOwnPackage(String packageName) {
    if (packageName == null || packageName.length() == 0) return false;
    String own = getPackageName();
    return packageName.equals(own)
        || packageName.equals("cn.modificator.launcher")
        || packageName.startsWith("cn.modificator.launcher.");
  }

  private String currentPackageName() {
    String packageName = lastPackageName;
    AccessibilityNodeInfo root = getRootInActiveWindow();
    if (root != null) {
      CharSequence rootPackage = root.getPackageName();
      if (rootPackage != null) {
        packageName = rootPackage.toString();
      }
      root.recycle();
    }
    return packageName;
  }

  private void toggleFrameRefreshMode() {
    if (isFrameRefreshMode) {
      new Thread(new Runnable() {
        @Override
        public void run() {
          if (restoreFrameRefreshModes()) {
            isFrameRefreshMode = false;
            Log.i(TAG, "Frame-by-frame full refresh mode disabled");
            showToastOnMain("逐帧全刷模式已关闭");
          } else {
            Log.w(TAG, "Failed to disable frame refresh mode");
            showToastOnMain("逐帧全刷切换失败");
          }
        }
      }, "EpdModeRestore").start();
      return;
    }

    String packageName = currentPackageName();
    if (packageName == null || packageName.length() == 0) {
      showToastOnMain("无法识别当前应用");
      return;
    }

    applyFrameRefreshMode(packageName, true);
  }

  private void applyFrameRefreshMode(final String packageName, final boolean announce) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        if (applyFrameRefreshModeNow(packageName)) {
          isFrameRefreshMode = true;
          Log.i(TAG, "Frame-by-frame full refresh mode enabled for " + packageName);
          if (announce) {
            showToastOnMain("逐帧全刷模式已开启");
          }
        } else {
          Log.w(TAG, "Failed to enable frame refresh mode for " + packageName);
          if (announce) {
            showToastOnMain("逐帧全刷切换失败");
          }
        }
      }
    }, "EpdModeApply").start();
  }

  private boolean applyFrameRefreshModeNow(String packageName) {
    if (packageName == null || packageName.length() == 0) return false;

    synchronized (frameRefreshLock) {
      if (packageName.equals(frameRefreshActivePackage)
          && frameRefreshPreviousModes.containsKey(packageName)) {
        return true;
      }
      if (!frameRefreshPreviousModes.containsKey(packageName)) {
        frameRefreshPreviousModes.put(packageName,
            EpdHelper.readCurrentMode(EpdHelper.APP_MODE_TYPING));
      }
    }

    boolean ok = EpdHelper.setForegroundAppMode(packageName, EpdHelper.APP_MODE_GC16);
    if (ok) {
      synchronized (frameRefreshLock) {
        frameRefreshActivePackage = packageName;
      }
    } else {
      synchronized (frameRefreshLock) {
        frameRefreshPreviousModes.remove(packageName);
      }
    }
    return ok;
  }

  private boolean restoreFrameRefreshModes() {
    Map<String, Integer> modesToRestore;
    String activePackage;
    synchronized (frameRefreshLock) {
      modesToRestore = new HashMap<String, Integer>(frameRefreshPreviousModes);
      activePackage = frameRefreshActivePackage;
    }

    boolean ok = false;
    for (Map.Entry<String, Integer> entry : modesToRestore.entrySet()) {
      ok = EpdHelper.setAppMode(entry.getKey(), entry.getValue()) || ok;
    }

    String currentPackage = currentPackageName();
    Integer currentMode = modesToRestore.get(currentPackage);
    if (currentMode == null && activePackage != null && activePackage.length() > 0) {
      currentMode = modesToRestore.get(activePackage);
    }
    if (currentMode == null) {
      currentMode = EpdHelper.APP_MODE_TYPING;
    }
    ok = EpdHelper.updateForegroundMode(currentMode) || ok;

    if (ok) {
      synchronized (frameRefreshLock) {
        frameRefreshPreviousModes.clear();
        frameRefreshActivePackage = "";
      }
    }
    return ok;
  }

  private void showToastOnMain(final String text) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(AutoRefreshAccessibilityService.this, text, Toast.LENGTH_SHORT).show();
      }
    });
  }

  private void triggerBookCoverLock() {
    Config config = new Config(this);
    if (!config.isBookCoverEnabled()) {
      Log.w(TAG, "triggerBookCoverLock skipped: book cover not enabled");
      return;
    }
    Log.i(TAG, "triggerBookCoverLock: launching BookCoverCaptureActivity");
    StandbyMinuteRefreshReceiver.cancel(this);
    Intent intent = new Intent(this, cn.modificator.launcher.BookCoverCaptureActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    startActivity(intent);
  }

  private static Bitmap compositeBookCover(AutoRefreshAccessibilityService service,
                                           Bitmap screenshot, Config config) {
    if (screenshot == null) return null;
    try {
      Bitmap result = Bitmap.createBitmap(screenshot.getWidth(), screenshot.getHeight(),
          Bitmap.Config.RGB_565);
      Canvas canvas = new Canvas(result);
      canvas.drawColor(Color.WHITE);
      canvas.drawBitmap(screenshot, 0, 0, null);

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
      Log.w(TAG, "compositeBookCover failed", e);
      return null;
    }
  }

  private void sendRefreshKeyEvent(final int keyCode) {
    new Thread(new Runnable() {
      @Override
      public void run() {
        runInputKeyEvent(keyCode);
      }
    }, "AutoRefreshKeyEvent").start();
  }

  private boolean runInputKeyEvent(int keyCode) {
    return (keyCode == 419 && runCommand(new String[]{"input", "keyevent", "SOS"}))
        || runCommand(new String[]{"input", "keyevent", String.valueOf(keyCode)})
        || runCommand(new String[]{"su", "-c", "input keyevent " + keyCode});
  }

  private boolean runCommand(String[] command) {
    Process process = null;
    try {
      process = Runtime.getRuntime().exec(command);
      return process.waitFor() == 0;
    } catch (Exception e) {
      return false;
    } finally {
      if (process != null) {
        process.destroy();
      }
    }
  }
}
