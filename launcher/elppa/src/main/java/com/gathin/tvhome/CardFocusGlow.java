package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** A small neutral halo behind a focused app tile; the opaque card covers the centre. */
final class CardFocusGlow extends Drawable {
  private static final int[] LAYER_ALPHA = {1, 1, 2, 2, 3, 4, 5, 6};
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final RectF[] layers = new RectF[LAYER_ALPHA.length];
  private final float[] radii = new float[LAYER_ALPHA.length];
  private final float radius, inset, focusScale, lift;
  private int alpha = 255;

  CardFocusGlow(float radius, float inset) {
    this(radius, inset, 1f, 0);
  }

  CardFocusGlow(float radius, float inset, float focusScale, float lift) {
    this.focusScale = focusScale;
    this.lift = lift;
    this.radius = radius;
    this.inset = inset;
    for (int i = 0; i < layers.length; i++) layers[i] = new RectF();
    paint.setColor(0xFFBAC8CF);
  }

  @Override
  protected void onBoundsChange(Rect bounds) {
    // Eight filled layers feather across roughly four logical pixels. There is no stroke,
    // bitmap blur or animation work. Place on an unclipped wrapper, not the clipped card.
    float spread = Math.max(1, radius * .28f);
    for (int i = 0; i < layers.length; i++) {
      float expansion = spread * (layers.length - i) / layers.length;
      layers[i].set(bounds);
      layers[i].inset(
          inset - expansion - bounds.width() * (focusScale - 1) / 2,
          inset - expansion - bounds.height() * (focusScale - 1) / 2);
      layers[i].offset(0, lift);
      radii[i] = Math.max(0, radius * focusScale + expansion);
    }
  }

  @Override
  public void draw(Canvas canvas) {
    for (int i = 0; i < layers.length; i++) {
      paint.setAlpha(LAYER_ALPHA[i] * alpha / 255);
      canvas.drawRoundRect(layers[i], radii[i], radii[i], paint);
    }
  }

  @Override
  public void setAlpha(int alpha) {
    this.alpha = alpha;
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
