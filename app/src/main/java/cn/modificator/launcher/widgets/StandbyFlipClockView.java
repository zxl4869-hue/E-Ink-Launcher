package cn.modificator.launcher.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

public class StandbyFlipClockView extends View {
  private String time = "00:00";
  private String date = "";
  private boolean lightStyle;
  private boolean minimalStyle;

  public StandbyFlipClockView(Context context) {
    super(context);
  }

  public StandbyFlipClockView(Context context, AttributeSet attrs) {
    super(context, attrs);
  }

  public StandbyFlipClockView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
  }

  public void setTimeDate(String time, String date) {
    this.time = time == null ? "00:00" : time;
    this.date = date == null ? "" : date;
    invalidate();
  }

  public void setLightStyle(boolean lightStyle) {
    this.lightStyle = lightStyle;
    invalidate();
  }

  public void setMinimalStyle(boolean minimalStyle) {
    this.minimalStyle = minimalStyle;
    invalidate();
  }

  @Override
  protected void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    draw(canvas, getWidth(), getHeight(), time, date, lightStyle, minimalStyle);
  }

  public static void draw(Canvas canvas, int width, int height, String time, String date) {
    draw(canvas, width, height, time, date, false);
  }

  public static void draw(Canvas canvas, int width, int height, String time, String date,
                          boolean lightStyle) {
    draw(canvas, width, height, time, date, lightStyle, false);
  }

  public static void draw(Canvas canvas, int width, int height, String time, String date,
                          boolean lightStyle, boolean minimalStyle) {
    draw(canvas, width, height, time, date, lightStyle, minimalStyle, true);
  }

  public static void draw(Canvas canvas, int width, int height, String time, String date,
                          boolean lightStyle, boolean minimalStyle, boolean clearBackground) {
    if (canvas == null || width <= 0 || height <= 0) return;
    if (minimalStyle) {
      drawMinimalClock(canvas, width, height, time, date, clearBackground);
      return;
    }
    if (clearBackground) canvas.drawColor(Color.WHITE);
    String digits = normalizeDigits(time);
    float min = Math.min(width, height);
    boolean landscape = width >= height;
    float leftMargin = landscape ? width * 0.035f : width * 0.07f;
    float rightReserve = landscape ? leftMargin : width * 0.07f;
    float bottomReserve = landscape ? height * 0.18f : height * 0.12f;
    float topReserve = landscape ? height * 0.11f : height * 0.22f;
    float gap = Math.max(4f, width * (landscape ? 0.008f : 0.009f));
    float centerGap = Math.max(gap * 3.2f, min * (landscape ? 0.052f : 0.06f));
    float contentWidth = width - leftMargin - rightReserve;
    float cardWidth = (contentWidth - gap * 2f - centerGap) / 4f;
    float availableHeight = height - topReserve - bottomReserve;
    float cardHeight = Math.min(availableHeight * (landscape ? 0.94f : 0.68f),
        cardWidth * (landscape ? 1.72f : 1.42f));
    float cardTop = topReserve + Math.max(0f, (availableHeight - cardHeight)
        * (landscape ? 0.20f : 0.30f));
    float left = leftMargin;
    float radius = Math.max(6f, min * 0.016f);

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    paint.setTextAlign(Paint.Align.CENTER);

    RectF card = new RectF();
    Rect textBounds = new Rect();
    for (int i = 0; i < 4; i++) {
      float cardLeft = left + i * (cardWidth + gap);
      if (i >= 2) cardLeft += centerGap - gap;
      card.set(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(lightStyle ? Color.WHITE : Color.rgb(30, 33, 37));
      canvas.drawRoundRect(card, radius, radius, paint);
      if (lightStyle) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, min * 0.006f));
        paint.setColor(Color.BLACK);
        canvas.drawRoundRect(card, radius, radius, paint);
        paint.setStyle(Paint.Style.FILL);
      }

      float textSize = cardHeight * 0.86f;
      paint.setTextSize(textSize);
      String digit = digits.substring(i, i + 1);
      paint.getTextBounds(digit, 0, digit.length(), textBounds);
      while ((textBounds.width() > cardWidth * 0.78f || textBounds.height() > cardHeight * 0.86f)
          && textSize > 10f) {
        textSize -= 2f;
        paint.setTextSize(textSize);
        paint.getTextBounds(digit, 0, digit.length(), textBounds);
      }
      Paint.FontMetrics fm = paint.getFontMetrics();
      float baseline = card.centerY() - (fm.ascent + fm.descent) / 2f;
      paint.setColor(lightStyle ? Color.BLACK : Color.rgb(235, 239, 229));
      canvas.drawText(digit, card.centerX(), baseline, paint);

      paint.setColor(lightStyle ? Color.rgb(120, 120, 120) : Color.rgb(78, 84, 88));
      paint.setStrokeWidth(Math.max(1f, min * 0.004f));
      float lineY = card.centerY();
      canvas.drawLine(card.left + radius, lineY, card.right - radius, lineY, paint);
    }

    paint.setStyle(Paint.Style.FILL);
    paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    paint.setTextSize(cardHeight * 0.52f);
    paint.setColor(lightStyle ? Color.BLACK : Color.rgb(30, 33, 37));
    Paint.FontMetrics colonFm = paint.getFontMetrics();
    float colonBaseline = cardTop + cardHeight / 2f - (colonFm.ascent + colonFm.descent) / 2f;
    canvas.drawText(":", left + contentWidth / 2f, colonBaseline, paint);

    paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
    paint.setStyle(Paint.Style.FILL);
    paint.setTextSize(Math.max(20f, min * (landscape ? 0.052f : 0.035f)));
    paint.setColor(Color.rgb(38, 43, 39));
    Paint.FontMetrics dateFm = paint.getFontMetrics();
    float dateCenterY;
    if (landscape) {
      float lowerBlankBottom = height - Math.max(44f, min * 0.095f);
      dateCenterY = cardTop + cardHeight + Math.max(30f, min * 0.072f);
      dateCenterY = Math.min(lowerBlankBottom, dateCenterY);
    } else {
      dateCenterY = height - Math.max(19f, min * 0.045f);
    }
    float dateBaseline = dateCenterY - (dateFm.ascent + dateFm.descent) / 2f;
    canvas.drawText(date == null ? "" : date, left + contentWidth / 2f, dateBaseline, paint);
  }

  private static void drawMinimalClock(Canvas canvas, int width, int height, String time,
                                       String date) {
    drawMinimalClock(canvas, width, height, time, date, true);
  }

  private static void drawMinimalClock(Canvas canvas, int width, int height, String time,
                                       String date, boolean clearBackground) {
    if (clearBackground) canvas.drawColor(Color.WHITE);
    String digits = normalizeDigits(time);
    String text = digits.substring(0, 2) + ":" + digits.substring(2, 4);
    float min = Math.min(width, height);
    boolean landscape = width >= height;

    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
    paint.setTextAlign(Paint.Align.CENTER);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(Color.BLACK);

    Rect bounds = new Rect();
    float maxWidth = width * (landscape ? 0.9f : 0.82f);
    float maxHeight = height * (landscape ? 0.58f : 0.48f);
    float textSize = height * (landscape ? 0.58f : 0.34f);
    paint.setTextSize(textSize);
    paint.getTextBounds(text, 0, text.length(), bounds);
    while ((bounds.width() > maxWidth || bounds.height() > maxHeight) && textSize > 32f) {
      textSize -= 3f;
      paint.setTextSize(textSize);
      paint.getTextBounds(text, 0, text.length(), bounds);
    }

    Paint.FontMetrics timeFm = paint.getFontMetrics();
    float timeCenterY = landscape ? height * 0.43f : height * 0.48f;
    float timeBaseline = timeCenterY - (timeFm.ascent + timeFm.descent) / 2f;
    canvas.drawText(text, width / 2f, timeBaseline, paint);

    paint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
    paint.setTextSize(Math.max(20f, min * (landscape ? 0.052f : 0.035f)));
    paint.setColor(Color.rgb(38, 43, 39));
    Paint.FontMetrics dateFm = paint.getFontMetrics();
    float dateCenterY = landscape ? height * 0.78f : height - Math.max(19f, min * 0.045f);
    float dateBaseline = dateCenterY - (dateFm.ascent + dateFm.descent) / 2f;
    canvas.drawText(date == null ? "" : date, width / 2f, dateBaseline, paint);
  }

  private static String normalizeDigits(String time) {
    String raw = time == null ? "" : time.replaceAll("\\D", "");
    if (raw.length() >= 4) return raw.substring(0, 4);
    StringBuilder builder = new StringBuilder(raw);
    while (builder.length() < 4) builder.insert(0, '0');
    return builder.toString();
  }
}
