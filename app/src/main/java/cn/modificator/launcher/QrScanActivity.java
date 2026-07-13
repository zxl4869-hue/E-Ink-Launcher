package cn.modificator.launcher;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class QrScanActivity extends Activity implements Camera.PreviewCallback {
  public static final String EXTRA_RESULT = "qr_content";

  private static final int REQUEST_CAMERA = 16001;
  private static final long EDGE_RENDER_INTERVAL_MS = 180L;
  private static final int EDGE_TARGET_SHORT_SIDE = 360;
  private static final int EDGE_THRESHOLD = 42;

  private ImageView edgePreview;
  private SurfaceTexture previewTexture;
  private Camera camera;
  private boolean decoding;
  private boolean renderingEdge;
  private boolean finished;
  private int displayOrientationDegrees;
  private long lastEdgeRenderAt;
  private final MultiFormatReader reader = new MultiFormatReader();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setupReader();
    setupView();
    if (hasCameraPermission()) {
      startCamera();
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
    } else {
      finishWithError("缺少相机权限");
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (hasCameraPermission()) {
      startCamera();
    }
  }

  @Override
  protected void onPause() {
    releaseCamera();
    super.onPause();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQUEST_CAMERA) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        startCamera();
      } else {
        finishWithError("相机权限未允许");
      }
    }
  }

  @Override
  public void onPreviewFrame(final byte[] data, final Camera camera) {
    if (finished || data == null || camera == null) return;
    Camera.Size size;
    try {
      size = camera.getParameters().getPreviewSize();
    } catch (Exception e) {
      return;
    }
    if (size == null) return;
    final int width = size.width;
    final int height = size.height;
    long now = System.currentTimeMillis();
    if (!renderingEdge && now - lastEdgeRenderAt >= EDGE_RENDER_INTERVAL_MS) {
      renderingEdge = true;
      lastEdgeRenderAt = now;
      final byte[] frame = data.clone();
      new Thread(new Runnable() {
        @Override
        public void run() {
          final Bitmap edge = createEdgeBitmap(frame, width, height);
          if (edge == null || finished) {
            renderingEdge = false;
            return;
          }
          runOnUiThread(new Runnable() {
            @Override
            public void run() {
              if (!finished && edgePreview != null) {
                edgePreview.setImageBitmap(edge);
              } else {
                edge.recycle();
              }
              renderingEdge = false;
            }
          });
        }
      }, "QrEdgeRender").start();
    }

    if (decoding) return;
    decoding = true;
    final byte[] decodeFrame = data.clone();
    new Thread(new Runnable() {
      @Override
      public void run() {
        final String text = decode(decodeFrame, width, height);
        if (text == null) {
          decoding = false;
          return;
        }
        runOnUiThread(new Runnable() {
          @Override
          public void run() {
            finishWithResult(text);
          }
        });
      }
    }, "QrDecode").start();
  }

  private void setupReader() {
    Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
    hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
    hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
    reader.setHints(hints);
  }

  private void setupView() {
    FrameLayout root = new FrameLayout(this);
    root.setBackgroundColor(Color.WHITE);
    edgePreview = new ImageView(this);
    edgePreview.setBackgroundColor(Color.WHITE);
    edgePreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
    root.addView(edgePreview, new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

    TextView hint = new TextView(this);
    hint.setText("线稿取景中，对准二维码");
    hint.setTextColor(Color.BLACK);
    hint.setTextSize(20);
    hint.setGravity(Gravity.CENTER);
    hint.setBackgroundColor(Color.WHITE);
    hint.setIncludeFontPadding(false);
    FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, Utils.dp2Px(this, 56));
    hintParams.gravity = Gravity.BOTTOM;
    root.addView(hint, hintParams);
    setContentView(root);
  }

  private boolean hasCameraPermission() {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.M
        || checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
  }

  private void startCamera() {
    if (camera != null || !hasCameraPermission() || finished) return;
    try {
      camera = Camera.open();
      Camera.Parameters params = camera.getParameters();
      Camera.Size previewSize = choosePreviewSize(params.getSupportedPreviewSizes());
      if (previewSize != null) {
        params.setPreviewSize(previewSize.width, previewSize.height);
      }
      List<String> focusModes = params.getSupportedFocusModes();
      if (focusModes != null) {
        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
          params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
          params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }
      }
      camera.setParameters(params);
      displayOrientationDegrees = getDisplayOrientation();
      camera.setDisplayOrientation(displayOrientationDegrees);
      previewTexture = new SurfaceTexture(10);
      camera.setPreviewTexture(previewTexture);
      camera.setPreviewCallback(this);
      camera.startPreview();
    } catch (Exception e) {
      releaseCamera();
      finishWithError("无法打开相机：" + e.getMessage());
    }
  }

  private Bitmap createEdgeBitmap(byte[] data, int width, int height) {
    if (data == null || width < 3 || height < 3) return null;
    int sample = Math.max(1, Math.min(width, height) / EDGE_TARGET_SHORT_SIDE);
    int outWidth = Math.max(1, width / sample);
    int outHeight = Math.max(1, height / sample);
    int[] pixels = new int[outWidth * outHeight];
    for (int y = 0; y < outHeight; y++) {
      int sy = Math.min(height - 2, Math.max(1, y * sample));
      for (int x = 0; x < outWidth; x++) {
        int sx = Math.min(width - 2, Math.max(1, x * sample));
        int center = data[sy * width + sx] & 0xff;
        int gx = Math.abs((data[sy * width + sx + 1] & 0xff)
            - (data[sy * width + sx - 1] & 0xff));
        int gy = Math.abs((data[(sy + 1) * width + sx] & 0xff)
            - (data[(sy - 1) * width + sx] & 0xff));
        int gradient = gx + gy;
        boolean edge = gradient > EDGE_THRESHOLD || (gradient > EDGE_THRESHOLD / 2 && center < 150);
        pixels[y * outWidth + x] = edge ? Color.BLACK : Color.WHITE;
      }
    }
    return createOrientedBitmap(pixels, outWidth, outHeight);
  }

  private Bitmap createOrientedBitmap(int[] pixels, int width, int height) {
    if (displayOrientationDegrees == 90) {
      int[] rotated = new int[pixels.length];
      int rotatedWidth = height;
      int rotatedHeight = width;
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int dstX = height - 1 - y;
          int dstY = x;
          rotated[dstY * rotatedWidth + dstX] = pixels[y * width + x];
        }
      }
      return Bitmap.createBitmap(rotated, rotatedWidth, rotatedHeight, Bitmap.Config.RGB_565);
    }
    if (displayOrientationDegrees == 270) {
      int[] rotated = new int[pixels.length];
      int rotatedWidth = height;
      int rotatedHeight = width;
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int dstX = y;
          int dstY = width - 1 - x;
          rotated[dstY * rotatedWidth + dstX] = pixels[y * width + x];
        }
      }
      return Bitmap.createBitmap(rotated, rotatedWidth, rotatedHeight, Bitmap.Config.RGB_565);
    }
    if (displayOrientationDegrees == 180) {
      int[] rotated = new int[pixels.length];
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int dstX = width - 1 - x;
          int dstY = height - 1 - y;
          rotated[dstY * width + dstX] = pixels[y * width + x];
        }
      }
      return Bitmap.createBitmap(rotated, width, height, Bitmap.Config.RGB_565);
    }
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565);
  }

  private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
    if (sizes == null || sizes.isEmpty()) return null;
    Camera.Size best = sizes.get(0);
    int bestScore = Integer.MAX_VALUE;
    for (Camera.Size size : sizes) {
      int pixels = size.width * size.height;
      int score = Math.abs(pixels - 640 * 480);
      if (score < bestScore) {
        bestScore = score;
        best = size;
      }
    }
    return best;
  }

  private int getDisplayOrientation() {
    Camera.CameraInfo info = new Camera.CameraInfo();
    Camera.getCameraInfo(0, info);
    int rotation = getWindowManager().getDefaultDisplay().getRotation();
    int degrees;
    switch (rotation) {
      case 1:
        degrees = 90;
        break;
      case 2:
        degrees = 180;
        break;
      case 3:
        degrees = 270;
        break;
      case 0:
      default:
        degrees = 0;
        break;
    }
    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
      return (360 - ((info.orientation + degrees) % 360)) % 360;
    }
    return (info.orientation - degrees + 360) % 360;
  }

  private String decode(byte[] data, int width, int height) {
    String result = decodeSource(data, width, height);
    if (result != null) return result;
    return decodeSource(rotateYPlaneClockwise(data, width, height), height, width);
  }

  private String decodeSource(byte[] data, int width, int height) {
    try {
      PlanarYUVLuminanceSource source =
          new PlanarYUVLuminanceSource(data, width, height, 0, 0, width, height, false);
      BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
      Result result = reader.decodeWithState(bitmap);
      return result == null ? null : result.getText();
    } catch (NotFoundException e) {
      return null;
    } catch (Exception e) {
      return null;
    } finally {
      reader.reset();
    }
  }

  private byte[] rotateYPlaneClockwise(byte[] data, int width, int height) {
    byte[] rotated = new byte[width * height];
    int index = 0;
    for (int x = 0; x < width; x++) {
      for (int y = height - 1; y >= 0; y--) {
        rotated[index++] = data[y * width + x];
      }
    }
    return rotated;
  }

  private void finishWithResult(String text) {
    if (finished) return;
    finished = true;
    releaseCamera();
    Intent data = new Intent();
    data.putExtra(EXTRA_RESULT, text);
    setResult(RESULT_OK, data);
    finish();
  }

  private void finishWithError(String message) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    setResult(RESULT_CANCELED);
    finish();
  }

  private void releaseCamera() {
    if (camera == null) return;
    try {
      camera.setPreviewCallback(null);
      camera.stopPreview();
    } catch (Exception ignored) {
    }
    try {
      camera.release();
    } catch (Exception ignored) {
    }
    camera = null;
    if (previewTexture != null) {
      try {
        previewTexture.release();
      } catch (Exception ignored) {
      }
      previewTexture = null;
    }
    decoding = false;
    renderingEdge = false;
  }
}
