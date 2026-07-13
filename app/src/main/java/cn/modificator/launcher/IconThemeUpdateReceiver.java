package cn.modificator.launcher;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Hourly background check for the currently selected remote icon theme.
 */
public class IconThemeUpdateReceiver extends BroadcastReceiver {

  private static final String TAG = "IconThemeUpdateReceiver";
  private static final String ACTION_CHECK_ICON_THEME =
      "cn.modificator.launcher.action.CHECK_ICON_THEME";
  private static final long CHECK_INTERVAL_MS = 60L * 60L * 1000L;

  public static void schedule(Context context) {
    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    if (alarmManager == null) return;
    PendingIntent pendingIntent = createPendingIntent(context);
    long firstAt = SystemClock.elapsedRealtime() + CHECK_INTERVAL_MS;
    alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, firstAt,
        CHECK_INTERVAL_MS, pendingIntent);
  }

  private static PendingIntent createPendingIntent(Context context) {
    Intent intent = new Intent(context, IconThemeUpdateReceiver.class);
    intent.setAction(ACTION_CHECK_ICON_THEME);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      flags |= PendingIntent.FLAG_IMMUTABLE;
    }
    return PendingIntent.getBroadcast(context, 23041, intent, flags);
  }

  @Override
  public void onReceive(final Context context, Intent intent) {
    if (intent == null || !ACTION_CHECK_ICON_THEME.equals(intent.getAction())) return;
    final PendingResult pendingResult = goAsync();
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Config config = new Config(context);
          IconThemeClient.checkForUpdates(context.getApplicationContext(),
              IconThemeClient.queryLauncherApps(context),
              config.getIconThemeId(), false);
        } catch (Exception e) {
          Log.w(TAG, "Scheduled icon theme check failed", e);
        } finally {
          pendingResult.finish();
        }
      }
    }, "IconThemeUpdateReceiver").start();
  }
}
