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

/** Static corner light and a fine directional reflection, present even on resting app tiles. */
final class CardReflection extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
  private final Path clip = new Path();
  private final RectF rect = new RectF();
  private final RectF edge = new RectF();
  private final float radius;
  private final boolean focused;
  private Shader upperCorner, lowerCorner, edgeLight;
  private float edgeWidth;

  CardReflection(float radius) {
    this(radius, false);
  }

  CardReflection(float radius, boolean focused) {
    this.radius = radius;
    this.focused = focused;
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    rect.set(bounds);
    clip.reset();
    clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
    // About 0.6 logical px: an interrupted reflection, never a uniform focus border.
    edgeWidth = Math.max(.5f, radius * .043f);
    edge.set(rect);
    edge.inset(edgeWidth / 2, edgeWidth / 2);
    upperCorner =
        new RadialGradient(
            bounds.left + bounds.width() * .02f,
            bounds.top - bounds.height() * .22f,
            Math.max(1, bounds.width() * .66f),
            new int[] {focused ? 0x46F4F2ED : 0x35F4F2ED, 0x0BF4F2ED, 0x00F4F2ED},
            new float[] {0, .5f, 1},
            Shader.TileMode.CLAMP);
    lowerCorner =
        new RadialGradient(
            bounds.right + bounds.width() * .03f,
            bounds.bottom + bounds.height() * .12f,
            Math.max(1, bounds.width() * .44f),
            new int[] {focused ? 0x20D1D9DD : 0x14D1D9DD, 0x00D1D9DD},
            null,
            Shader.TileMode.CLAMP);
    edgeLight =
        new LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            new int[] {focused ? 0x85F1F2EE : 0x66F1F2EE, 0x14E8EAE9, 0x00E8EAE9, 0x24C9D3D9},
            new float[] {0, .32f, .72f, 1},
            Shader.TileMode.CLAMP);
  }

  @Override
  public void draw(Canvas canvas) {
    int saved = canvas.save();
    canvas.clipPath(clip);
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(upperCorner);
    canvas.drawRect(rect, paint);
    paint.setShader(lowerCorner);
    canvas.drawRect(rect, paint);
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(edgeWidth);
    paint.setShader(edgeLight);
    float edgeRadius = Math.max(0, radius - edgeWidth / 2);
    canvas.drawRoundRect(edge, edgeRadius, edgeRadius, paint);
    paint.setShader(null);
    paint.setStyle(Paint.Style.FILL);
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
