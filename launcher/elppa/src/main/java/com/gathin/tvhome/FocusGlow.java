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

/** Diffuse focus light, without a sharp outline, a blur pass or a rendering layer. */
final class FocusGlow extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  private final RectF rect = new RectF();
  private final float radius, unit;
  private final boolean focused;
  private final int surface;
  private Shader light;

  FocusGlow(float radius, float unit, boolean focused, int surface) {
    this.radius = radius;
    this.unit = unit;
    this.focused = focused;
    this.surface = surface;
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    light =
        new LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            new int[] {0xFFBED9DA, 0xFF78A7AA, 0xFFA6A5BE, 0xFFC19B8E},
            null,
            Shader.TileMode.CLAMP);
  }

  @Override
  public void draw(Canvas canvas) {
    rect.set(getBounds());
    if (focused) {
      paint.setStyle(Paint.Style.STROKE);
      paint.setShader(light);
      // Nested broad, low-opacity strokes form a soft falloff. Deliberately no final 1px rim.
      for (int i = 12; i >= 1; i--) {
        paint.setAlpha(4 + (12 - i) / 3);
        paint.setStrokeWidth((2 + i * 2) * unit);
        canvas.drawRoundRect(rect, radius, radius, paint);
      }
    }
    paint.setShader(null);
    paint.setStyle(Paint.Style.FILL);
    paint.setColor(surface);
    canvas.drawRoundRect(rect, radius, radius, paint);
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
