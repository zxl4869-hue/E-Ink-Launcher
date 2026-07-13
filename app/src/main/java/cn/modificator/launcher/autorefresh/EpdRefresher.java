package cn.modificator.launcher.autorefresh;

import android.graphics.Bitmap;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import java.lang.reflect.Method;

public class EpdRefresher {

  private static final String TAG = EpdRefresher.class.getSimpleName();
  private static final String EPD_SERVICE = "epd";
  private static final String EPD_INTERFACE = "com.hmct.epd.IEpdManager";
  private static final int TRANSACTION_ADD_BITMAP_WITH_STRING = 2;
  // Vendor AIDL spelling is "Exteral".
  private static final int TRANSACTION_UPDATE_EXTERNAL_PAPER = 18;
  private static final int TRANSACTION_FORCE_CLEAR = 21;
  private static final int PRIORITY_KEYGUARD = 0;
  private static final int EPD_MODE_DEFAULT = 0;
  private static final int MIN_SHOW_TIME_DEFAULT = 0;

  private EpdRefresher() {
  }

  public static boolean updateExternalPaper() {
    return transactNoArgs(TRANSACTION_UPDATE_EXTERNAL_PAPER, "updateExteralPaper");
  }

  public static boolean forceClear() {
    return transactNoArgs(TRANSACTION_FORCE_CLEAR, "forceClear");
  }

  public static boolean publishKeyguardBitmap(Bitmap bitmap, String lockText) {
    if (bitmap == null || bitmap.isRecycled()) {
      Log.w(TAG, "EPD addBitmapToPresentationDisplayWithString skipped: bitmap is null");
      return false;
    }

    IBinder binder = getService(EPD_SERVICE);
    if (binder == null) {
      Log.w(TAG, "EPD service not found");
      return false;
    }

    Parcel data = Parcel.obtain();
    Parcel reply = Parcel.obtain();
    try {
      data.writeInterfaceToken(EPD_INTERFACE);
      data.writeInt(1);
      bitmap.writeToParcel(data, 0);
      data.writeInt(PRIORITY_KEYGUARD);
      data.writeInt(EPD_MODE_DEFAULT);
      data.writeInt(MIN_SHOW_TIME_DEFAULT);
      data.writeInt(1);
      data.writeString(lockText == null ? "" : lockText);

      boolean transacted = binder.transact(TRANSACTION_ADD_BITMAP_WITH_STRING, data, reply, 0);
      if (!transacted) {
        Log.w(TAG, "EPD addBitmapToPresentationDisplayWithString transact returned false");
        return false;
      }
      reply.readException();
      Log.i(TAG, "EPD addBitmapToPresentationDisplayWithString succeeded");
      return true;
    } catch (Throwable e) {
      Log.w(TAG, "EPD addBitmapToPresentationDisplayWithString failed", e);
      return false;
    } finally {
      reply.recycle();
      data.recycle();
    }
  }

  private static boolean transactNoArgs(int transactionCode, String operationName) {
    IBinder binder = getService(EPD_SERVICE);
    if (binder == null) {
      Log.w(TAG, "EPD service not found");
      return false;
    }

    Parcel data = Parcel.obtain();
    Parcel reply = Parcel.obtain();
    try {
      data.writeInterfaceToken(EPD_INTERFACE);
      boolean transacted = binder.transact(transactionCode, data, reply, 0);
      if (!transacted) {
        Log.w(TAG, "EPD " + operationName + " transact returned false");
        return false;
      }
      reply.readException();
      Log.i(TAG, "EPD " + operationName + " succeeded");
      return true;
    } catch (Throwable e) {
      Log.w(TAG, "EPD " + operationName + " failed", e);
      return false;
    } finally {
      reply.recycle();
      data.recycle();
    }
  }

  private static IBinder getService(String serviceName) {
    try {
      Class<?> serviceManager = Class.forName("android.os.ServiceManager");
      Method getService = serviceManager.getDeclaredMethod("getService", String.class);
      return (IBinder) getService.invoke(null, serviceName);
    } catch (Throwable e) {
      Log.w(TAG, "Failed to get service: " + serviceName, e);
      return null;
    }
  }
}
