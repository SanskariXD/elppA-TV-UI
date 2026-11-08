package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Small static light falloff. No blur pass, bitmap, timer or software rendering layer. */
final class FocusGlow extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF rect = new RectF();
  private final float radius, unit;
  private final boolean focused;
  private Shader edge;

  FocusGlow(float radius, float unit, boolean focused) {
    this.radius = radius;
    this.unit = unit;
    this.focused = focused;
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    edge =
        new LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            new int[] {0xFFE3F5F5, 0xFF8BB8BD, 0xFFA7A4C4, 0xFFCCA99D},
            null,
            Shader.TileMode.CLAMP);
  }

  @Override
  public void draw(Canvas canvas) {
    rect.set(getBounds());
    paint.setShader(null);
    if (focused) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(0xFFB9DADB);
      for (int i = 8; i >= 1; i--) {
        paint.setAlpha(2 + (8 - i) * 2);
        paint.setStrokeWidth(i * 2 * unit);
        canvas.drawRoundRect(rect, radius, radius, paint);
      }
    }
    paint.setShader(null);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(ElppaUi.SURFACE);
    canvas.drawRoundRect(rect, radius, radius, paint);
    if (focused) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setAlpha(230);
      paint.setStrokeWidth(Math.max(1, unit));
      paint.setShader(edge);
      canvas.drawRoundRect(rect, radius, radius, paint);
      paint.setShader(null);
    }
  }

  @Override
  public void setAlpha(int alpha) {}

  @Override
  public void setColorFilter(ColorFilter filter) {}

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
