package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.TextView;

public class OutlinedTextView extends TextView {
  private final float outlineStrokeWidth;

  public OutlinedTextView(Context context) {
    this(context, null);
  }

  public OutlinedTextView(Context context, AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public OutlinedTextView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    outlineStrokeWidth = Math.max(2f, getResources().getDisplayMetrics().density * 2f);
  }

  @Override
  protected void onDraw(android.graphics.Canvas canvas) {
    int fillColor = getCurrentTextColor();
    Paint paint = getPaint();
    Paint.Style oldStyle = paint.getStyle();
    float oldStrokeWidth = paint.getStrokeWidth();

    setTextColor(Color.WHITE);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(outlineStrokeWidth);
    super.onDraw(canvas);

    setTextColor(fillColor);
    paint.setStyle(Paint.Style.FILL);
    paint.setStrokeWidth(0f);
    super.onDraw(canvas);

    paint.setStyle(oldStyle);
    paint.setStrokeWidth(oldStrokeWidth);
  }
}
