package cn.modificator.launcher.model;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

import cn.modificator.launcher.Config;
import cn.modificator.launcher.Launcher;
import cn.modificator.launcher.R;

public class ForceRotationService extends Service {
  public static final String EXTRA_ORIENTATION_MODE = "orientationMode";

  private static final String CHANNEL_ID = "Force Rotation";
  private static final String CHANNEL_NAME = "Force Rotation";
  private static final int NOTIFICATION_ID = 3001;

  private WindowManager windowManager;
  private View overlayView;

  public static void start(Context context, int mode) {
    Intent intent = new Intent(context, ForceRotationService.class);
    intent.putExtra(EXTRA_ORIENTATION_MODE, mode);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      context.startForegroundService(intent);
    } else {
      context.startService(intent);
    }
  }

  public static void stop(Context context) {
    context.stopService(new Intent(context, ForceRotationService.class));
  }

  public static boolean canDrawOverlays(Context context) {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
  }

  public static Intent overlayPermissionIntent(Context context) {
    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + context.getPackageName()));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    return intent;
  }

  @Override
  public void onCreate() {
    super.onCreate();
    windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    startForeground(NOTIFICATION_ID, buildNotification());
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    int mode = intent != null
        ? intent.getIntExtra(EXTRA_ORIENTATION_MODE, new Config(this).getOrientationMode())
        : new Config(this).getOrientationMode();
    if (mode == Config.ORIENTATION_AUTO) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (!canDrawOverlays(this)) {
      stopSelf();
      return START_NOT_STICKY;
    }
    showOrUpdateOverlay(mode);
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    removeOverlay();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private Notification buildNotification() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
      if (nm != null) {
        nm.createNotificationChannel(new NotificationChannel(
            CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW));
      }
    }
    Intent launchIntent = new Intent(this, Launcher.class);
    launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    PendingIntent pendingIntent = PendingIntent.getActivity(
        this, 3001, launchIntent, PendingIntent.FLAG_IMMUTABLE);

    Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ? new Notification.Builder(this, CHANNEL_ID)
        : new Notification.Builder(this);
    return builder
        .setWhen(System.currentTimeMillis())
        .setContentTitle("全局强制屏幕方向")
        .setContentText("正在用悬浮窗保持指定方向")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setContentIntent(pendingIntent)
        .build();
  }

  private void showOrUpdateOverlay(int mode) {
    if (windowManager == null) return;
    WindowManager.LayoutParams params = buildLayoutParams(mode);
    if (overlayView == null) {
      overlayView = new View(this);
      overlayView.setBackgroundColor(0x00000000);
      windowManager.addView(overlayView, params);
    } else {
      windowManager.updateViewLayout(overlayView, params);
    }
  }

  private WindowManager.LayoutParams buildLayoutParams(int mode) {
    int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        : WindowManager.LayoutParams.TYPE_PHONE;
    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
        1, 1, type, overlayFlags(), PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.START | Gravity.TOP;
    params.x = 0;
    params.y = 0;
    params.alpha = 0f;
    params.screenOrientation = toRequestedOrientation(mode);
    return params;
  }

  private int overlayFlags() {
    return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
  }

  private int toRequestedOrientation(int mode) {
    switch (mode) {
      case Config.ORIENTATION_PORTRAIT:
        return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
      case Config.ORIENTATION_LANDSCAPE:
        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
      case Config.ORIENTATION_REVERSE_PORTRAIT:
        return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
      case Config.ORIENTATION_REVERSE_LANDSCAPE:
        return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
      case Config.ORIENTATION_AUTO:
      default:
        return ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR;
    }
  }

  private void removeOverlay() {
    if (windowManager != null && overlayView != null) {
      try {
        windowManager.removeView(overlayView);
      } catch (RuntimeException ignored) {
      }
      overlayView = null;
    }
  }
}
