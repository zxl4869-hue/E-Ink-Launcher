package cn.modificator.launcher.autorefresh;

import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * EPD display mode control via Binder.
 * <p>
 * Uses transaction 26 ({@code addAppMode}) to register a per-app EPD
 * mode, then transaction 16 ({@code updateEpdMode}) to apply the mode
 * to the current foreground package immediately.
 */
public class EpdHelper {

  private static final String TAG = EpdHelper.class.getSimpleName();
  private static final String EPD_SERVICE = "epd";
  private static final String EPD_INTERFACE = "com.hmct.epd.IEpdManager";

  /** Transaction 16 = updateEpdMode(int mode). Applies to the current foreground package. */
  private static final int TRANSACTION_UPDATE_EPD_MODE = 16;
  /** Transaction 26 = addAppMode(String packageName, int modeIndex). */
  private static final int TRANSACTION_ADD_APP_MODE = 26;

  /**
   * Display-mode indices used by setEpdDisplayMode (transaction 11). That
   * transaction is system-app only, so these are kept for reference.
   * <ul>
   *   <li>1 = DU  — fast, light ghosting</li>
   *   <li>2 = GC16 — full refresh per frame, no ghosting</li>
   *   <li>3 = A2  — very fast, some ghosting</li>
   * </ul>
   */
  public static final int MODE_DU = 1;
  public static final int MODE_GC16 = 2;
  public static final int MODE_A2 = 3;

  /** Raw per-app modes used by addAppMode/updateEpdMode. */
  public static final int APP_MODE_TYPING = 0x201;
  public static final int APP_MODE_GC16 = 0x202;
  public static final int APP_MODE_A2 = 0x203;

  private static IBinder cachedBinder;

  private EpdHelper() {
  }

  /**
   * Compatibility wrapper for the old index-based API. It can only affect
   * the current foreground package because updateEpdMode is foreground-based.
   */
  public static boolean setDisplayMode(int modeIndex) {
    return updateForegroundMode(toRawAppMode(modeIndex));
  }

  /**
   * Register a per-app EPD mode for any target package. This is the useful
   * non-system path: transaction 11 is blocked, while addAppMode is not.
   */
  public static boolean setAppMode(String packageName, int rawMode) {
    if (packageName == null || packageName.length() == 0) {
      Log.w(TAG, "addAppMode skipped: empty package name");
      return false;
    }
    int modeIndex = toAddAppModeIndex(rawMode);
    IBinder binder = getService();
    if (binder == null) return false;
    Parcel data = Parcel.obtain();
    Parcel reply = Parcel.obtain();
    try {
      data.writeInterfaceToken(EPD_INTERFACE);
      data.writeString(packageName);
      data.writeInt(modeIndex);
      boolean transacted = binder.transact(TRANSACTION_ADD_APP_MODE, data, reply, 0);
      if (!transacted) {
        Log.w(TAG, "addAppMode(" + packageName + ", raw=" + rawMode
            + ", index=" + modeIndex + ") transact returned false");
        return false;
      }
      reply.readException();
      Log.i(TAG, "addAppMode(" + packageName + ", raw=" + rawMode
          + ", index=" + modeIndex + ") succeeded");
      return true;
    } catch (Throwable e) {
      Log.w(TAG, "addAppMode(" + packageName + ", raw=" + rawMode
          + ", index=" + modeIndex + ") failed", e);
      return false;
    } finally {
      data.recycle();
      reply.recycle();
    }
  }

  /**
   * Apply a raw mode to the current foreground package immediately. The
   * service chooses mLastTopPackage, not the Binder caller's package.
   */
  public static boolean updateForegroundMode(int rawMode) {
    IBinder binder = getService();
    if (binder == null) return false;
    Parcel data = Parcel.obtain();
    Parcel reply = Parcel.obtain();
    try {
      data.writeInterfaceToken(EPD_INTERFACE);
      data.writeInt(rawMode);
      boolean transacted = binder.transact(TRANSACTION_UPDATE_EPD_MODE, data, reply, 0);
      if (!transacted) {
        Log.w(TAG, "updateEpdMode(" + rawMode + ") transact returned false");
        return false;
      }
      reply.readException();
      Log.i(TAG, "updateEpdMode(" + rawMode + ") succeeded");
      return true;
    } catch (Throwable e) {
      Log.w(TAG, "updateEpdMode(" + rawMode + ") failed", e);
      return false;
    } finally {
      data.recycle();
      reply.recycle();
    }
  }

  /**
   * Register the package mode and also update the active foreground mode.
   * Either success path is useful: addAppMode persists across resume, while
   * updateEpdMode gives immediate visual feedback.
   */
  public static boolean setForegroundAppMode(String packageName, int rawMode) {
    boolean registered = setAppMode(packageName, rawMode);
    boolean updated = updateForegroundMode(rawMode);
    return registered || updated;
  }

  public static int readCurrentMode(int fallback) {
    String value = readSystemProperty("sys.hmct.epd_mode");
    if (value == null || value.length() == 0) return fallback;
    try {
      if (value.startsWith("0x") || value.startsWith("0X")) {
        return Integer.parseInt(value.substring(2), 16);
      }
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      Log.w(TAG, "Unable to parse sys.hmct.epd_mode=" + value);
      return fallback;
    }
  }

  private static int toRawAppMode(int modeIndex) {
    if (modeIndex == MODE_GC16) return APP_MODE_GC16;
    if (modeIndex == MODE_A2) return APP_MODE_A2;
    return APP_MODE_TYPING;
  }

  private static int toAddAppModeIndex(int rawMode) {
    if (rawMode >= APP_MODE_TYPING && rawMode <= 0x206) {
      return rawMode - 0x200;
    }
    if (rawMode >= 1 && rawMode <= 6) {
      return rawMode;
    }
    return MODE_DU;
  }

  private static String readSystemProperty(String key) {
    try {
      Class<?> systemProperties = Class.forName("android.os.SystemProperties");
      Method get = systemProperties.getDeclaredMethod("get", String.class, String.class);
      return (String) get.invoke(null, key, "");
    } catch (Throwable e) {
      Log.w(TAG, "Failed to read system property: " + key, e);
      return "";
    }
  }

  private static IBinder getService() {
    if (cachedBinder != null && cachedBinder.isBinderAlive()) return cachedBinder;
    try {
      Class<?> serviceManager = Class.forName("android.os.ServiceManager");
      Method getService = serviceManager.getDeclaredMethod("getService", String.class);
      cachedBinder = (IBinder) getService.invoke(null, EPD_SERVICE);
      return cachedBinder;
    } catch (Throwable e) {
      Log.w(TAG, "Failed to get epd service", e);
      return null;
    }
  }
}
