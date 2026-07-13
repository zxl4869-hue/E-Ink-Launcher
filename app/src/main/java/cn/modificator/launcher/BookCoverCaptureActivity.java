package cn.modificator.launcher;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import cn.modificator.launcher.autorefresh.AutoRefreshSettings;
import cn.modificator.launcher.model.AdminReceiver;

/**
 * Transparent Activity that requests MediaProjection permission,
 * captures screen, composites overlay, pushes to EPD, then locks.
 * Token is released immediately after capture to avoid persistent icon.
 */
public class BookCoverCaptureActivity extends Activity {

  private static final String TAG = "BookCoverCapture";
  private static final int REQUEST_MEDIA_PROJECTION = 14001;
  private final Handler handler = new Handler(Looper.getMainLooper());

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    applyImmersiveMode();
    Log.i(TAG, "BookCoverCaptureActivity started");

    MediaProjectionManager mpm = (MediaProjectionManager)
        getSystemService(MEDIA_PROJECTION_SERVICE);
    startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
  }

  @Override
  protected void onResume() {
    super.onResume();
    applyImmersiveMode();
  }

  private void applyImmersiveMode() {
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    View decor = getWindow().getDecorView();
    decor.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode != REQUEST_MEDIA_PROJECTION) {
      finish();
      return;
    }

    if (resultCode != RESULT_OK || data == null) {
      Log.w(TAG, "Permission denied, finishing");
      finish();
      return;
    }

    Log.i(TAG, "Permission granted, waiting for bars to hide then capturing");
    applyImmersiveMode();
    ScreenCaptureManager.getInstance().initForCapture(this, resultCode, data);

    // Delay capture to let status bar / nav bar finish hiding
    handler.postDelayed(new Runnable() {
      @Override
      public void run() {
        ScreenCaptureManager.getInstance().captureScreen(
            new ScreenCaptureManager.CaptureCallback() {
              @Override
              public void onCaptured(Bitmap screenshot) {
                Config config = new Config(BookCoverCaptureActivity.this);
                Bitmap composited = compositeBookCover(screenshot, config);
                if (composited != null) {
                  StandbyWallpaperUpdater.update(BookCoverCaptureActivity.this, composited,
                      config.getStandbyLockText(), true, true);
                  composited.recycle();
                }
                if (screenshot != null) screenshot.recycle();
                ScreenCaptureManager.getInstance().release();
                lockNow();
                finish();
              }

              @Override
              public void onError(String error) {
                Log.w(TAG, "Capture failed: " + error);
                ScreenCaptureManager.getInstance().release();
                finish();
              }
            });
      }
    }, 400L);
  }

  private Bitmap compositeBookCover(Bitmap screenshot, Config config) {
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

  private void lockNow() {
    try {
      DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
      ComponentName admin = new ComponentName(this, AdminReceiver.class);
      if (dpm != null && dpm.isAdminActive(admin)) {
        dpm.lockNow();
        Log.i(TAG, "lockNow succeeded");
      }
    } catch (Exception e) {
      Log.w(TAG, "lockNow failed", e);
    }
  }
}
