package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Small corner reflections on app cards, including resting cards. No perimeter or glow. */
final class CardReflection extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  private final Path clip = new Path();
  private final RectF rect = new RectF();
  private final float radius;
  private Shader upperCorner, lowerCorner;

  CardReflection(float radius) {
    this.radius = radius;
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    rect.set(bounds);
    clip.reset();
    clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
    upperCorner =
        new RadialGradient(
            bounds.left + bounds.width() * .04f,
            bounds.top - bounds.height() * .20f,
            Math.max(1, bounds.width() * .48f),
            new int[] {0x28F2F1EC, 0x0AF2F1EC, 0x00F2F1EC},
            new float[] {0, .45f, 1},
            Shader.TileMode.CLAMP);
    lowerCorner =
        new RadialGradient(
            bounds.right + bounds.width() * .04f,
            bounds.bottom + bounds.height() * .15f,
            Math.max(1, bounds.width() * .33f),
            new int[] {0x12CBC7C4, 0x00CBC7C4},
            null,
            Shader.TileMode.CLAMP);
  }

  @Override
  public void draw(Canvas canvas) {
    int saved = canvas.save();
    canvas.clipPath(clip);
    paint.setShader(upperCorner);
    canvas.drawRect(rect, paint);
    paint.setShader(lowerCorner);
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
