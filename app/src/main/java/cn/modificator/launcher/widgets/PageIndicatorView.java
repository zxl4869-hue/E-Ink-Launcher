package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

public class PageIndicatorView extends View {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private int total = 1;
  private int current = 0;
  private int color = 0xff000000;

  public PageIndicatorView(Context context) {
    super(context);
    init();
  }

  public PageIndicatorView(Context context, AttributeSet attrs) {
    super(context, attrs);
    init();
  }

  public PageIndicatorView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    init();
  }

  private void init() {
    paint.setColor(color);
    paint.setStyle(Paint.Style.FILL);
  }

  public void setPages(int total, int current) {
    this.total = Math.max(1, total);
    this.current = Math.max(0, Math.min(this.total - 1, current));
    invalidate();
  }

  public void setColor(int color) {
    if (this.color == color) return;
    this.color = color;
    paint.setColor(color);
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    boolean horizontal = getWidth() >= getHeight();
    int drawCount = Math.max(1, Math.min(5, total));
    int drawCurrent = current;
    if (total > drawCount) {
      drawCurrent = Math.round(current * 1f * (drawCount - 1) / Math.max(1, total - 1));
    }
    float gap = 9f;
    float normal = 2.6f;
    float selected = 4.8f;
    float length = (drawCount - 1) * gap;
    float centerX = getWidth() / 2f;
    float centerY = getHeight() / 2f;
    paint.setColor(color);
    for (int i = 0; i < drawCount; i++) {
      float radius = i == drawCurrent ? selected : normal;
      float x = horizontal ? centerX - length / 2f + i * gap : centerX;
      float y = horizontal ? centerY : centerY - length / 2f + i * gap;
      canvas.drawCircle(x, y, radius, paint);
    }
  }
}
