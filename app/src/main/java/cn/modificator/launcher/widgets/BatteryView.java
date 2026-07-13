package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 墨水屏电池图案。用填充比例表达电量，避免窄边栏里出现文字换行。
 */
public class BatteryView extends View {

  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path boltPath = new Path();

  private int maxProgress = 100;
  private int progress = 0;
  private boolean charging;
  private int foregroundColor = 0xff000000;

  public BatteryView(Context context) {
    super(context);
    init();
  }

  public BatteryView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public BatteryView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    paint.setColor(foregroundColor);
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    float w = getWidth();
    float h = getHeight();
    boolean horizontal = w >= h;
    float stroke = Math.max(2f, Math.min(w, h) / 11f);
    paint.setColor(foregroundColor);
    paint.setStrokeWidth(stroke);
    paint.setStyle(Paint.Style.STROKE);

    RectF body;
    RectF nub;
    if (horizontal) {
      float nubW = Math.max(3f, w * 0.12f);
      body = new RectF(stroke, stroke, w - nubW - stroke, h - stroke);
      nub = new RectF(w - nubW, h * 0.34f, w - stroke, h * 0.66f);
    } else {
      float nubH = Math.max(3f, h * 0.12f);
      body = new RectF(stroke, nubH + stroke, w - stroke, h - stroke);
      nub = new RectF(w * 0.34f, stroke, w * 0.66f, nubH + stroke);
    }
    float radius = Math.min(body.width(), body.height()) * 0.25f;
    canvas.drawRoundRect(body, radius, radius, paint);
    paint.setStyle(Paint.Style.FILL);
    canvas.drawRoundRect(nub, radius / 2f, radius / 2f, paint);

    float ratio = Math.max(0f, Math.min(1f, progress * 1f / Math.max(1, maxProgress)));
    if (horizontal) {
      float inset = stroke * 1.7f;
      float fillW = Math.max(0f, (body.width() - inset * 2f) * ratio);
      RectF fill = new RectF(body.left + inset, body.top + inset,
          body.left + inset + fillW, body.bottom - inset);
      canvas.drawRoundRect(fill, radius * 0.8f, radius * 0.8f, paint);
    } else {
      float inset = stroke * 1.7f;
      float fillH = Math.max(0f, (body.height() - inset * 2f) * ratio);
      RectF fill = new RectF(body.left + inset, body.bottom - inset - fillH,
          body.right - inset, body.bottom - inset);
      canvas.drawRoundRect(fill, radius * 0.8f, radius * 0.8f, paint);
    }

    if (charging) {
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(foregroundColor == 0xff000000 ? 0xffffffff : 0xff000000);
      boltPath.reset();
      if (horizontal) {
        float cx = body.centerX();
        float cy = body.centerY();
        float scale = Math.min(body.width(), body.height()) * 0.36f;
        boltPath.moveTo(cx - scale * 0.2f, cy - scale * 0.8f);
        boltPath.lineTo(cx - scale * 0.8f, cy + scale * 0.05f);
        boltPath.lineTo(cx - scale * 0.25f, cy + scale * 0.05f);
        boltPath.lineTo(cx - scale * 0.55f, cy + scale * 0.9f);
        boltPath.lineTo(cx + scale * 0.55f, cy - scale * 0.05f);
        boltPath.lineTo(cx + scale * 0.15f, cy - scale * 0.05f);
        boltPath.close();
      } else {
        float cx = body.centerX();
        float cy = body.centerY();
        float scale = Math.min(body.width(), body.height()) * 0.36f;
        boltPath.moveTo(cx - scale * 0.18f, cy - scale * 0.85f);
        boltPath.lineTo(cx - scale * 0.76f, cy + scale * 0.05f);
        boltPath.lineTo(cx - scale * 0.18f, cy + scale * 0.05f);
        boltPath.lineTo(cx - scale * 0.46f, cy + scale * 0.88f);
        boltPath.lineTo(cx + scale * 0.52f, cy - scale * 0.05f);
        boltPath.lineTo(cx + scale * 0.12f, cy - scale * 0.05f);
        boltPath.close();
      }
      canvas.drawPath(boltPath, paint);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Math.max(1f, Math.min(w, h) / 13f));
      paint.setColor(foregroundColor);
      canvas.drawPath(boltPath, paint);
      paint.setColor(foregroundColor);
    }
  }

  public void setForegroundColor(int color) {
    if (foregroundColor == color) return;
    foregroundColor = color;
    invalidate();
  }

  public void setMaxProgress(int maxProgress) {
    this.maxProgress = maxProgress;
    invalidate();
  }

  public void setProgress(int progress) {
    this.progress = progress;
    invalidate();
  }

  public void setCharging(boolean charging) {
    this.charging = charging;
    invalidate();
  }
}
