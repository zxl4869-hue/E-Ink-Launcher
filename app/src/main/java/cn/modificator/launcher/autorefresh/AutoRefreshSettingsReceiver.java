package cn.modificator.launcher.autorefresh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

public class AutoRefreshSettingsReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    if (intent == null || intent.getAction() == null) {
      return;
    }
    String action = intent.getAction();
    if (AutoRefreshSettings.ACTION_SET_KEYCODE.equals(action)) {
      int keyCode = intent.getIntExtra(AutoRefreshSettings.EXTRA_KEYCODE, 0);
      AutoRefreshSettings.setRefreshKeyCode(context, keyCode);
      AutoRefreshSettings.setCaptureNextKey(context, false);
      Toast.makeText(context, "刷新按键 keycode: " + keyCode, Toast.LENGTH_SHORT).show();
    } else if (AutoRefreshSettings.ACTION_SET_INTERVAL.equals(action)) {
      int interval = intent.getIntExtra(AutoRefreshSettings.EXTRA_INTERVAL, 5);
      AutoRefreshSettings.setInterval(context, interval);
      Toast.makeText(context, "自动刷新间隔: " + interval, Toast.LENGTH_SHORT).show();
    } else if (AutoRefreshSettings.ACTION_SET_ENABLED.equals(action)) {
      boolean enabled = intent.getBooleanExtra(AutoRefreshSettings.EXTRA_ENABLED, false);
      AutoRefreshSettings.setEnabled(context, enabled);
      Toast.makeText(context, enabled ? "自动刷新已开启" : "自动刷新已关闭", Toast.LENGTH_SHORT).show();
    } else if (AutoRefreshSettings.ACTION_FORCE_REFRESH.equals(action)) {
      boolean refreshed = EpdRefresher.forceClear();
      Toast.makeText(context, refreshed ? "已触发全局刷新" : "全局刷新失败", Toast.LENGTH_SHORT).show();
    }
  }
}
