package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A quiet, fixed reflection above card artwork. Shaders are prepared once per size. */
final class CardReflection extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  private final Path clip = new Path();
  private final RectF rect = new RectF();
  private final float radius;
  private Shader sheen, spot;

  CardReflection(float radius) {
    this.radius = radius;
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    rect.set(bounds);
    clip.reset();
    clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
    sheen =
        new LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            new int[] {0x32FFFFFF, 0x09FFFFFF, 0x00FFFFFF, 0x08000000},
            new float[] {0, .35f, .55f, 1},
            Shader.TileMode.CLAMP);
    spot =
        new RadialGradient(
            bounds.left + bounds.width() * .2f,
            bounds.top - bounds.height() * .35f,
            Math.max(1, bounds.width() * .7f),
            new int[] {0x30E8F2F0, 0x00E8F2F0},
            null,
            Shader.TileMode.CLAMP);
  }

  @Override
  public void draw(Canvas canvas) {
    int saved = canvas.save();
    canvas.clipPath(clip);
    paint.setShader(sheen);
    canvas.drawRect(rect, paint);
    paint.setShader(spot);
    canvas.drawRect(rect, paint);
    paint.setShader(null);
    canvas.restoreToCount(saved);
  }

  @Override
  public void setAlpha(int alpha) {
    paint.setAlpha(alpha);
    invalidateSelf();
  }

  @Override
  public void setColorFilter(ColorFilter filter) {
    paint.setColorFilter(filter);
    invalidateSelf();
  }

  @Override
  public int getOpacity() {
    return PixelFormat.TRANSLUCENT;
  }
}
