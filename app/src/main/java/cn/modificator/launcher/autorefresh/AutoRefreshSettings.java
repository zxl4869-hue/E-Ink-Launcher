package cn.modificator.launcher.autorefresh;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.provider.Settings;
import android.text.TextUtils;

public class AutoRefreshSettings {

  public static final String ACTION_SET_KEYCODE =
      "cn.modificator.launcher.autorefresh.SET_KEYCODE";
  public static final String ACTION_SET_INTERVAL =
      "cn.modificator.launcher.autorefresh.SET_INTERVAL";
  public static final String ACTION_SET_ENABLED =
      "cn.modificator.launcher.autorefresh.SET_ENABLED";
  public static final String ACTION_FORCE_REFRESH =
      "cn.modificator.launcher.autorefresh.FORCE_REFRESH";
  public static final String EXTRA_KEYCODE = "keycode";
  public static final String EXTRA_INTERVAL = "interval";
  public static final String EXTRA_ENABLED = "enabled";

  private static final String PREFS = "auto_refresh_settings";
  private static final String KEY_ENABLED = "enabled";
  private static final String KEY_INTERVAL = "interval";
  private static final String KEY_TAP_ENABLED = "tap_enabled";
  private static final String KEY_TAP_INTERVAL = "tap_interval";
  private static final String KEY_REFRESH_KEYCODE = "refresh_keycode";
  private static final String KEY_CAPTURE_NEXT_KEY = "capture_next_key";
  private static final String KEY_DELAY = "delay";
  private static final String KEY_SOS_FRAME_REFRESH_ENABLED = "sos_frame_refresh_enabled";
  private static final String KEY_SHAKE_REFRESH_ENABLED = "shake_refresh_enabled";
  private static final String KEY_SHAKE_ACTION_MODE = "shake_action_mode";
  private static final String KEY_SHAKE_REFRESH_COUNT = "shake_refresh_count";
  private static final String KEY_SHAKE_REFRESH_AMPLITUDE = "shake_refresh_amplitude";
  private static final int DEFAULT_REFRESH_KEYCODE = 419;
  public static final int SHAKE_ACTION_OFF = 0;
  public static final int SHAKE_ACTION_REFRESH = 1;
  public static final int SHAKE_ACTION_PAGE_TURN = 2;
  public static final int SHAKE_AMPLITUDE_SMALL = 0;
  public static final int SHAKE_AMPLITUDE_MEDIUM = 1;
  public static final int SHAKE_AMPLITUDE_LARGE = 2;

  private static final int[] INTERVALS = {1, 2, 3, 5, 8, 10, 15, 20};
  private static final int[] DELAYS = {0, 200, 400, 600, 800, 1000, 1500, 2000};

  private AutoRefreshSettings() {
  }

  public static boolean isEnabled(Context context) {
    return prefs(context).getBoolean(KEY_ENABLED, false);
  }

  public static void setEnabled(Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
  }

  public static int getInterval(Context context) {
    return prefs(context).getInt(KEY_INTERVAL, 5);
  }

  public static void setInterval(Context context, int interval) {
    prefs(context).edit().putInt(KEY_INTERVAL, sanitizeInterval(interval)).apply();
  }

  public static int nextInterval(Context context) {
    int current = getInterval(context);
    for (int i = 0; i < INTERVALS.length; i++) {
      if (INTERVALS[i] == current) {
        return INTERVALS[(i + 1) % INTERVALS.length];
      }
    }
    return INTERVALS[0];
  }

  public static boolean isTapEnabled(Context context) {
    return prefs(context).getBoolean(KEY_TAP_ENABLED, false);
  }

  public static void setTapEnabled(Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_TAP_ENABLED, enabled).apply();
  }

  public static int getTapInterval(Context context) {
    return prefs(context).getInt(KEY_TAP_INTERVAL, 5);
  }

  public static void setTapInterval(Context context, int interval) {
    prefs(context).edit().putInt(KEY_TAP_INTERVAL, sanitizeInterval(interval)).apply();
  }

  public static int nextTapInterval(Context context) {
    int current = getTapInterval(context);
    for (int i = 0; i < INTERVALS.length; i++) {
      if (INTERVALS[i] == current) {
        return INTERVALS[(i + 1) % INTERVALS.length];
      }
    }
    return INTERVALS[0];
  }

  public static int getRefreshKeyCode(Context context) {
    return prefs(context).getInt(KEY_REFRESH_KEYCODE, DEFAULT_REFRESH_KEYCODE);
  }

  public static void setRefreshKeyCode(Context context, int keyCode) {
    if (keyCode > 0) {
      prefs(context).edit().putInt(KEY_REFRESH_KEYCODE, keyCode).apply();
    }
  }

  public static boolean shouldCaptureNextKey(Context context) {
    return prefs(context).getBoolean(KEY_CAPTURE_NEXT_KEY, false);
  }

  public static void setCaptureNextKey(Context context, boolean capture) {
    prefs(context).edit().putBoolean(KEY_CAPTURE_NEXT_KEY, capture).apply();
  }

  public static int getDelay(Context context) {
    return prefs(context).getInt(KEY_DELAY, 0);
  }

  public static void setDelay(Context context, int delay) {
    prefs(context).edit().putInt(KEY_DELAY, delay).apply();
  }

  public static int nextDelay(Context context) {
    int current = getDelay(context);
    for (int i = 0; i < DELAYS.length; i++) {
      if (DELAYS[i] == current) {
        return DELAYS[(i + 1) % DELAYS.length];
      }
    }
    return DELAYS[0];
  }

  public static boolean isSosFrameRefreshEnabled(Context context) {
    return prefs(context).getBoolean(KEY_SOS_FRAME_REFRESH_ENABLED, false);
  }

  public static void setSosFrameRefreshEnabled(Context context, boolean enabled) {
    prefs(context).edit().putBoolean(KEY_SOS_FRAME_REFRESH_ENABLED, enabled).apply();
  }

  public static boolean isShakeRefreshEnabled(Context context) {
    return getShakeActionMode(context) == SHAKE_ACTION_REFRESH;
  }

  public static void setShakeRefreshEnabled(Context context, boolean enabled) {
    setShakeActionMode(context, enabled ? SHAKE_ACTION_REFRESH : SHAKE_ACTION_OFF);
  }

  public static int getShakeActionMode(Context context) {
    SharedPreferences preferences = prefs(context);
    if (!preferences.contains(KEY_SHAKE_ACTION_MODE)) {
      return preferences.getBoolean(KEY_SHAKE_REFRESH_ENABLED, false)
          ? SHAKE_ACTION_REFRESH : SHAKE_ACTION_OFF;
    }
    int mode = preferences.getInt(KEY_SHAKE_ACTION_MODE, SHAKE_ACTION_OFF);
    return Math.max(SHAKE_ACTION_OFF, Math.min(SHAKE_ACTION_PAGE_TURN, mode));
  }

  public static void setShakeActionMode(Context context, int mode) {
    int sanitized = Math.max(SHAKE_ACTION_OFF, Math.min(SHAKE_ACTION_PAGE_TURN, mode));
    prefs(context).edit()
        .putInt(KEY_SHAKE_ACTION_MODE, sanitized)
        .putBoolean(KEY_SHAKE_REFRESH_ENABLED, sanitized == SHAKE_ACTION_REFRESH)
        .apply();
  }

  public static int nextShakeActionMode(Context context) {
    return (getShakeActionMode(context) + 1) % 3;
  }

  public static int getShakeRefreshCount(Context context) {
    return Math.max(1, Math.min(2, prefs(context).getInt(KEY_SHAKE_REFRESH_COUNT, 1)));
  }

  public static void setShakeRefreshCount(Context context, int count) {
    prefs(context).edit().putInt(KEY_SHAKE_REFRESH_COUNT, Math.max(1, Math.min(2, count))).apply();
  }

  public static int nextShakeRefreshCount(Context context) {
    return getShakeRefreshCount(context) == 1 ? 2 : 1;
  }

  public static int getShakeRefreshAmplitude(Context context) {
    int amplitude = prefs(context).getInt(
        KEY_SHAKE_REFRESH_AMPLITUDE, SHAKE_AMPLITUDE_MEDIUM);
    return Math.max(SHAKE_AMPLITUDE_SMALL, Math.min(SHAKE_AMPLITUDE_LARGE, amplitude));
  }

  public static void setShakeRefreshAmplitude(Context context, int amplitude) {
    prefs(context).edit().putInt(KEY_SHAKE_REFRESH_AMPLITUDE,
        Math.max(SHAKE_AMPLITUDE_SMALL, Math.min(SHAKE_AMPLITUDE_LARGE, amplitude))).apply();
  }

  public static int nextShakeRefreshAmplitude(Context context) {
    return (getShakeRefreshAmplitude(context) + 1) % 3;
  }

  static float getGyroscopeThresholdDps(Context context) {
    switch (getShakeRefreshAmplitude(context)) {
      case SHAKE_AMPLITUDE_SMALL:
        return 210f;
      case SHAKE_AMPLITUDE_LARGE:
        return 330f;
      default:
        return 270f;
    }
  }

  public static boolean hasMotionSensor(Context context) {
    SensorManager sensorManager =
        (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    return sensorManager != null
        && (sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
        || sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null);
  }

  public static boolean hasAccelerometer(Context context) {
    return hasMotionSensor(context);
  }

  static void registerPreferenceListener(Context context,
      SharedPreferences.OnSharedPreferenceChangeListener listener) {
    prefs(context).registerOnSharedPreferenceChangeListener(listener);
  }

  static void unregisterPreferenceListener(Context context,
      SharedPreferences.OnSharedPreferenceChangeListener listener) {
    prefs(context).unregisterOnSharedPreferenceChangeListener(listener);
  }

  public static boolean isAccessibilityServiceEnabled(Context context) {
    String enabledServices = Settings.Secure.getString(
        context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    if (TextUtils.isEmpty(enabledServices)) {
      return false;
    }
    String expected = new ComponentName(context, AutoRefreshAccessibilityService.class)
        .flattenToString();
    String expectedShort = new ComponentName(context, AutoRefreshAccessibilityService.class)
        .flattenToShortString();
    String[] services = enabledServices.split(":");
    for (String service : services) {
      if (expected.equalsIgnoreCase(service) || expectedShort.equalsIgnoreCase(service)) {
        return true;
      }
    }
    return false;
  }

  private static int sanitizeInterval(int interval) {
    if (interval <= 0) {
      return 1;
    }
    if (interval > 999) {
      return 999;
    }
    return interval;
  }

  private static SharedPreferences prefs(Context context) {
    return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
  }
}
