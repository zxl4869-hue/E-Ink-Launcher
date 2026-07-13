package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.widget.ProgressBar;

public class ReadingProgressBar extends ProgressBar {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();

  public ReadingProgressBar(Context context) {
    super(context);
    init();
  }

  public ReadingProgressBar(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public ReadingProgressBar(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    paint.setColor(0xff000000);
    setIndeterminate(false);
    setWillNotDraw(false);
  }

  @Override
  protected synchronized void onDraw(Canvas canvas) {
    int width = getWidth() - getPaddingLeft() - getPaddingRight();
    int height = getHeight() - getPaddingTop() - getPaddingBottom();
    if (width <= 0 || height <= 0) return;

    float stroke = dp(1.2f);
    float trackHeight = Math.min(dp(8), Math.max(dp(6), height - stroke));
    float left = getPaddingLeft() + stroke / 2f;
    float right = getPaddingLeft() + width - stroke / 2f;
    float centerY = getPaddingTop() + height / 2f;
    float top = centerY - trackHeight / 2f;
    float bottom = centerY + trackHeight / 2f;
    float radius = trackHeight / 2f;

    paint.setStyle(Paint.Style.FILL);
    paint.setColor(0xffffffff);
    rect.set(left, top, right, bottom);
    canvas.drawRoundRect(rect, radius, radius, paint);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(stroke);
    paint.setColor(0xff000000);
    canvas.drawRoundRect(rect, radius, radius, paint);

    int max = Math.max(1, getMax());
    float ratio = Math.max(0f, Math.min(1f, getProgress() * 1f / max));
    if (ratio <= 0f) return;

    float inset = stroke / 2f;
    float innerLeft = left + inset;
    float innerRight = right - inset;
    float innerWidth = Math.max(0f, innerRight - innerLeft);
    float progressRight = innerLeft + innerWidth * ratio;
    if (progressRight - innerLeft < dp(3f)) {
      progressRight = Math.min(innerRight, innerLeft + dp(3f));
    }
    float innerTop = top + inset;
    float innerBottom = bottom - inset;
    float innerRadius = Math.max(0f, (innerBottom - innerTop) / 2f);
    rect.set(innerLeft, innerTop, progressRight, innerBottom);
    paint.setStyle(Paint.Style.FILL);
    canvas.drawRoundRect(rect, innerRadius, innerRadius, paint);

    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(stroke);
    paint.setColor(0xff000000);
    rect.set(left, top, right, bottom);
    canvas.drawRoundRect(rect, radius, radius, paint);
  }

  private float dp(float value) {
    return value * getResources().getDisplayMetrics().density;
  }
}
