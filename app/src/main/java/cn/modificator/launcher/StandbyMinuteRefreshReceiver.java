package cn.modificator.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.util.Calendar;

public class StandbyMinuteRefreshReceiver extends BroadcastReceiver {
  private static final String TAG = "StandbyMinuteRefresh";
  private static final String ACTION_REFRESH =
      "cn.modificator.launcher.action.STANDBY_MINUTE_REFRESH";
  private static final long MINUTE_MS = 60 * 1000L;
  private static final long INITIAL_DELAY_MS = 1000L;
  private static final long RETRY_INTERVAL_MS = 5000L;
  private static final long CLEANUP_DELAY_MS = 1000L;

  private static volatile boolean updating;

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null || !ACTION_REFRESH.equals(intent.getAction())) return;

    final Context appContext = context.getApplicationContext();
    Config config = new Config(appContext);

    if (!config.isStandbyRefreshEnabled()) {
      PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
      if (pm != null && isScreenOff(pm)) {
        final PendingResult pendingResult = goAsync();
        new Thread(new Runnable() {
          @Override
          public void run() {
            handleCleanup(appContext, pm, new Config(appContext));
            pendingResult.finish();
          }
        }, "EInkLauncher:StandbyCleanup").start();
      } else {
        scheduleRetry(appContext);
      }
      return;
    }

    // 书封激活时（长按音量减触发）不需要分钟刷新，取消定时器省电
    if (StandbyWallpaperUpdater.bookCoverActive) {
      Log.i(TAG, "book cover active, cancelling standby refresh alarms");
      cancel(appContext);
      return;
    }

    long nowMinute = System.currentTimeMillis() / 60000L;
    boolean firstRun = config.getLastWallpaperUpdateMinute() == 0;
    PowerManager pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);

    if (!firstRun && nowMinute == config.getLastWallpaperUpdateMinute()) {
      scheduleNextForMode(appContext, config);
      return;
    }

    if (pm != null && !isScreenOff(pm)) {
      if (firstRun) {
        scheduleRetry(appContext);
      } else {
        scheduleNextForMode(appContext, config);
      }
      return;
    }

    if (updating) {
      scheduleNextForMode(appContext, config);
      return;
    }

    final long updateMinute = nowMinute;
    final PendingResult pendingResult = goAsync();
    new Thread(new Runnable() {
      @Override
      public void run() {
        updating = true;
        try {
          Config updateConfig = new Config(appContext);
          doUpdate(appContext, updateConfig,
              updateConfig.isStandbyWallpaperEnabled(), updateMinute);
        } finally {
          updating = false;
        }
        scheduleNextForMode(appContext);
        pendingResult.finish();
      }
    }, "EInkLauncher:StandbyMinuteRefresh").start();
  }

  private void handleCleanup(Context context, PowerManager pm, Config config) {
    PowerManager.WakeLock wakeLock = null;
    try {
      if (pm != null) {
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            "EInkLauncher:StandbyCleanup");
        wakeLock.acquire(15000L);
      }
      Thread.sleep(CLEANUP_DELAY_MS);
      StandbyWallpaperUpdater.update(context, null,
          config.getStandbyLockText(), true, false);
      Log.i(TAG, "cleanup done, cancelling schedule");
    } catch (Exception e) {
      Log.w(TAG, "cleanup failed", e);
    } finally {
      if (wakeLock != null && wakeLock.isHeld()) {
        wakeLock.release();
      }
    }
    cancel(context);
  }

  private void doUpdate(Context context, Config config, boolean showTime, long nowMinute) {
    PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    PowerManager.WakeLock wakeLock = null;
    try {
      if (pm != null) {
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
            "EInkLauncher:StandbyMinuteRefresh");
        wakeLock.acquire(config.isWereadWallpaperEnabled() ? 60000L : 15000L);
      }
      String lockText = config.getStandbyLockText();
      boolean fullRefresh = config.isStandbyFullRefresh();
      StandbyWallpaperUpdater.update(context, null, lockText, fullRefresh, showTime);
      config.setLastWallpaperUpdateMinute(nowMinute);
    } catch (Exception e) {
      Log.w(TAG, "update failed", e);
    } finally {
      if (wakeLock != null && wakeLock.isHeld()) {
        wakeLock.release();
      }
    }
  }

  public static void scheduleNext(Context context) {
    long now = System.currentTimeMillis();
    long triggerAt = ((now / MINUTE_MS) + 1L) * MINUTE_MS;
    scheduleAt(context, triggerAt);
  }

  public static void scheduleNextForMode(Context context) {
    scheduleNextForMode(context, new Config(context));
  }

  private static void scheduleNextForMode(Context context, Config config) {
    if (config == null || !config.isStandbyRefreshEnabled()) {
      cancel(context);
      return;
    }
    if (config.isWereadWallpaperEnabled()) {
      scheduleNextWereadWallpaper(context, config);
      return;
    }
    if (config.isStandbyWallpaperEnabled()) {
      scheduleNext(context);
      return;
    }
    scheduleNextRandomWallpaper(context, config);
  }

  private static void scheduleNextRandomWallpaper(Context context, Config config) {
    long now = System.currentTimeMillis();
    long intervalMs = Math.max(1L, config.getStandbyRandomWallpaperIntervalMinutes()) * MINUTE_MS;
    long triggerAt = ((now / intervalMs) + 1L) * intervalMs;
    if (triggerAt - now < INITIAL_DELAY_MS) {
      triggerAt = now + INITIAL_DELAY_MS;
    }
    scheduleAt(context, triggerAt);
  }

  private static void scheduleNextWereadWallpaper(Context context, Config config) {
    long now = System.currentTimeMillis();
    long lastMinute = config.getLastWallpaperUpdateMinute();
    if (lastMinute <= 0 || !isSameLocalDay(lastMinute * MINUTE_MS, now)) {
      scheduleAt(context, now + INITIAL_DELAY_MS);
      return;
    }
    long intervalMs = Math.max(1L, config.getWereadWallpaperIntervalMinutes()) * MINUTE_MS;
    long triggerAt = ((now / intervalMs) + 1L) * intervalMs;
    if (triggerAt - now < INITIAL_DELAY_MS) {
      triggerAt = now + INITIAL_DELAY_MS;
    }
    scheduleAt(context, triggerAt);
  }

  private static boolean isSameLocalDay(long leftMs, long rightMs) {
    Calendar left = Calendar.getInstance();
    Calendar right = Calendar.getInstance();
    left.setTimeInMillis(leftMs);
    right.setTimeInMillis(rightMs);
    return left.get(Calendar.YEAR) == right.get(Calendar.YEAR)
        && left.get(Calendar.DAY_OF_YEAR) == right.get(Calendar.DAY_OF_YEAR);
  }

  public static void scheduleSoon(Context context) {
    long triggerAt = System.currentTimeMillis() + INITIAL_DELAY_MS;
    scheduleAt(context, triggerAt);
  }

  public static void scheduleRetry(Context context) {
    long triggerAt = System.currentTimeMillis() + RETRY_INTERVAL_MS;
    scheduleAt(context, triggerAt);
  }

  private static void scheduleAt(Context context, long triggerAt) {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (alarmManager == null) return;
    PendingIntent pendingIntent = pendingIntent(context);
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      if (alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
      } else {
        alarmManager.setAlarmClock(
            new AlarmManager.AlarmClockInfo(triggerAt, pendingIntent), pendingIntent);
      }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
      alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    } else {
      alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }
    Log.i(TAG, "scheduled refresh at " + triggerAt);
  }

  public static void cancel(Context context) {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (alarmManager != null) {
      alarmManager.cancel(pendingIntent(context));
    }
  }

  private static PendingIntent pendingIntent(Context context) {
    Intent intent = new Intent(context, StandbyMinuteRefreshReceiver.class);
    intent.setAction(ACTION_REFRESH);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    return PendingIntent.getBroadcast(context, 0, intent, flags);
  }

  private static boolean isScreenOff(PowerManager pm) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
      return !pm.isInteractive();
    }
    return !pm.isScreenOn();
  }
}
