package cn.modificator.launcher;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class App extends Application {
  private static final String TAG = "EInkLauncherApp";
  private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent == null || !Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) return;
      Config config = new Config(context);
      if (!config.isStandbyRefreshEnabled() || StandbyWallpaperUpdater.bookCoverActive) return;
      config.setLastWallpaperUpdateMinute(0);
      StandbyMinuteRefreshReceiver.scheduleSoon(context);
      Log.i(TAG, "screen off detected, scheduled standby refresh");
    }
  };

  @Override
  public void onCreate() {
    super.onCreate();
    CrashCapture.getInstance().init(this, 1, Launcher.class);
    Utils.registerReceiverCompat(this, screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
  }


}
