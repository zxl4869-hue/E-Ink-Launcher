package cn.modificator.launcher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.nio.ByteBuffer;

public class ScreenCaptureManager {

  private static final String TAG = "ScreenCaptureManager";
  private static final String PREFS = "screen_capture_prefs";
  private static final String KEY_TOKEN_GRANTED = "token_granted";

  private static ScreenCaptureManager instance;

  private MediaProjectionManager projectionManager;
  private MediaProjection mediaProjection;
  private boolean tokenGranted;
  private int displayWidth;
  private int displayHeight;
  private int displayDpi;

  public interface CaptureCallback {
    void onCaptured(Bitmap bitmap);
    void onError(String error);
  }

  private ScreenCaptureManager() {}

  public static synchronized ScreenCaptureManager getInstance() {
    if (instance == null) {
      instance = new ScreenCaptureManager();
    }
    return instance;
  }

  public void init(Context context) {
    projectionManager = (MediaProjectionManager)
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    tokenGranted = false;

    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();
    DisplayMetrics metrics = new DisplayMetrics();
    display.getRealMetrics(metrics);
    displayWidth = metrics.widthPixels;
    displayHeight = metrics.heightPixels;
    displayDpi = metrics.densityDpi;
    Log.i(TAG, "init display=" + displayWidth + "x" + displayHeight + " dpi=" + displayDpi);
  }

  /** Create token from onActivityResult data — used by BookCoverCaptureActivity. */
  public void initForCapture(Context context, int resultCode, Intent data) {
    if (projectionManager == null) {
      projectionManager = (MediaProjectionManager)
          context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }
    if (resultCode == Activity.RESULT_OK && data != null) {
      mediaProjection = projectionManager.getMediaProjection(resultCode, data);
      tokenGranted = true;

      WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
      Display display = wm.getDefaultDisplay();
      DisplayMetrics metrics = new DisplayMetrics();
      display.getRealMetrics(metrics);
      displayWidth = metrics.widthPixels;
      displayHeight = metrics.heightPixels;
      displayDpi = metrics.densityDpi;

      Log.i(TAG, "initForCapture display=" + displayWidth + "x" + displayHeight);
    }
  }

  public boolean isTokenGranted() {
    return tokenGranted && mediaProjection != null;
  }

  public boolean isReady() {
    return mediaProjection != null;
  }

  public void requestPermission(Activity activity, int requestCode) {
    if (projectionManager == null) {
      projectionManager = (MediaProjectionManager)
          activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }
    Intent intent = projectionManager.createScreenCaptureIntent();
    activity.startActivityForResult(intent, requestCode);
    Log.i(TAG, "requestPermission launched");
  }

  public void onPermissionResult(Context context, int resultCode, Intent data) {
    if (resultCode == Activity.RESULT_OK && data != null) {
      mediaProjection = projectionManager.getMediaProjection(resultCode, data);
      tokenGranted = true;
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
          .edit().putBoolean(KEY_TOKEN_GRANTED, true).apply();
      Log.i(TAG, "MediaProjection permission granted, token=" + (mediaProjection != null));
    } else {
      tokenGranted = false;
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
          .edit().putBoolean(KEY_TOKEN_GRANTED, false).apply();
      Log.w(TAG, "MediaProjection permission denied");
    }
  }

  public void captureScreen(final CaptureCallback callback) {
    if (mediaProjection == null) {
      Log.w(TAG, "captureScreen: no token");
      if (callback != null) {
        callback.onError("MediaProjection token not available");
      }
      return;
    }

    Log.i(TAG, "captureScreen starting " + displayWidth + "x" + displayHeight);
    final ImageReader imageReader = ImageReader.newInstance(
        displayWidth, displayHeight, PixelFormat.RGBA_8888, 1);

    final VirtualDisplay virtualDisplay = mediaProjection.createVirtualDisplay(
        "ScreenCapture",
        displayWidth, displayHeight, displayDpi,
        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
        imageReader.getSurface(), null, null);

    imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
      @Override
      public void onImageAvailable(ImageReader reader) {
        Image image = null;
        Bitmap bitmap = null;
        try {
          image = reader.acquireLatestImage();
          if (image != null) {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * displayWidth;

            bitmap = Bitmap.createBitmap(
                displayWidth + rowPadding / pixelStride,
                displayHeight, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);

            if (rowPadding > 0) {
              bitmap = Bitmap.createBitmap(bitmap, 0, 0, displayWidth, displayHeight);
            }

            Log.i(TAG, "captureScreen success " + bitmap.getWidth() + "x" + bitmap.getHeight());
            if (callback != null) {
              callback.onCaptured(bitmap);
            }
          } else {
            Log.w(TAG, "captureScreen: null image");
            if (callback != null) {
              callback.onError("null image");
            }
          }
        } catch (Exception e) {
          Log.e(TAG, "Error capturing screen", e);
          if (callback != null) {
            callback.onError(e.getMessage());
          }
        } finally {
          if (image != null) {
            image.close();
          }
          imageReader.close();
          virtualDisplay.release();
        }
      }
    }, new Handler(Looper.getMainLooper()));
  }

  public void release() {
    if (mediaProjection != null) {
      mediaProjection.stop();
      mediaProjection = null;
    }
  }
}
