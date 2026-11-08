package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Cached tiny backdrop sample with real CPU frost, prepared once per hero on an artwork worker. */
final class FrostedPlayDrawable extends Drawable {
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF rect = new RectF();
  private final RectF edge = new RectF();
  private final Path clip = new Path();
  private final Bitmap frost;
  private final boolean focused, reduced;
  private Shader sheen, edgeLight;
  private float radius, edgeWidth;
  private int alpha = 255;

  FrostedPlayDrawable(Bitmap frost, boolean focused, boolean reduced) {
    this.frost = frost;
    this.focused = focused;
    this.reduced = reduced;
  }

  @Override
  protected void onBoundsChange(android.graphics.Rect bounds) {
    rect.set(bounds);
    radius = rect.height() / 2;
    clip.reset();
    clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
    edgeWidth = Math.max(.5f, rect.height() / 52f);
    edge.set(rect);
    edge.inset(edgeWidth / 2, edgeWidth / 2);
    sheen =
        new LinearGradient(
            0,
            bounds.top,
            0,
            bounds.bottom,
            new int[] {focused ? 0x30FFF8ED : 0x20FFF8ED, 0x06FFF8ED, 0x10D8CEC0},
            new float[] {0, .58f, 1},
            Shader.TileMode.CLAMP);
    edgeLight =
        new LinearGradient(
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom,
            new int[] {focused ? 0x70EEE5D5 : 0x56EEE5D5, 0x16E2D7C7, 0x30E2D7C7},
            new float[] {0, .65f, 1},
            Shader.TileMode.CLAMP);
  }

  @Override
  public void draw(Canvas canvas) {
    int saved = canvas.save();
    canvas.clipPath(clip);
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(null);
    if (reduced) {
      // Deliberately opaque only when the user requests reduced transparency.
      color(focused ? 0xFF69645E : 0xFF544F49);
      canvas.drawRect(rect, paint);
    } else {
      // No opaque undercoat: both the cached frost and warm neutral tint retain the hero.
      if (frost != null && !frost.isRecycled()) {
        color(focused ? 0xAAFFFFFF : 0x96FFFFFF);
        canvas.drawBitmap(frost, null, rect, paint);
      }
      color(focused ? 0x997C7469 : 0x7A746D64);
      canvas.drawRect(rect, paint);
      color(0xFFFFFFFF);
      paint.setShader(sheen);
      canvas.drawRect(rect, paint);
      paint.setShader(edgeLight);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(edgeWidth);
      canvas.drawRoundRect(edge, radius - edgeWidth / 2, radius - edgeWidth / 2, paint);
      paint.setShader(null);
      paint.setStyle(Paint.Style.FILL);
    }
    canvas.restoreToCount(saved);
  }

  private void color(int color) {
    paint.setColor(color);
    paint.setAlpha((color >>> 24) * alpha / 255);
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

  static Bitmap sample(Bitmap source, int canvasWidth, int canvasHeight) {
    if (source == null || canvasWidth <= 0 || canvasHeight <= 0) return null;
    // Reference button: x50 y220, 88x36 on centred 960x540 canvas, matching Activity layout.
    float unit = Math.min(canvasWidth / 960f, canvasHeight / 540f);
    float x = (canvasWidth - 960 * unit) / 2 + 50 * unit,
        y = (canvasHeight - 540 * unit) / 2 + 220 * unit;
    float zoom =
        Math.max(
            canvasWidth / (float) source.getWidth(), canvasHeight / (float) source.getHeight());
    float cropX = (source.getWidth() * zoom - canvasWidth) / 2,
        cropY = (source.getHeight() * zoom - canvasHeight) / 2;
    android.graphics.Matrix matrix = new android.graphics.Matrix();
    matrix.setScale(zoom * 64 / (88 * unit), zoom * 32 / (36 * unit));
    matrix.postTranslate((-cropX - x) * 64 / (88 * unit), (-cropY - y) * 32 / (36 * unit));
    Bitmap sample = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888);
    new Canvas(sample).drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
    int[] pixels = new int[64 * 32];
    sample.getPixels(pixels, 0, 64, 0, 0, 64, 32);
    // Two separable box passes, 2048 pixels. No full-screen blur or per-frame work.
    for (int pass = 0; pass < 2; pass++) {
      int[] horizontal = new int[pixels.length];
      for (int yy = 0; yy < 32; yy++)
        for (int xx = 0; xx < 64; xx++) {
          int r = 0, g = 0, b = 0;
          for (int k = -4; k <= 4; k++) {
            int c = pixels[yy * 64 + Math.max(0, Math.min(63, xx + k))];
            r += (c >> 16) & 255;
            g += (c >> 8) & 255;
            b += c & 255;
          }
          horizontal[yy * 64 + xx] = 0xFF000000 | ((r / 9) << 16) | ((g / 9) << 8) | (b / 9);
        }
      for (int yy = 0; yy < 32; yy++)
        for (int xx = 0; xx < 64; xx++) {
          int r = 0, g = 0, b = 0;
          for (int k = -4; k <= 4; k++) {
            int c = horizontal[Math.max(0, Math.min(31, yy + k)) * 64 + xx];
            r += (c >> 16) & 255;
            g += (c >> 8) & 255;
            b += c & 255;
          }
          pixels[yy * 64 + xx] = 0xFF000000 | ((r / 9) << 16) | ((g / 9) << 8) | (b / 9);
        }
    }
    sample.setPixels(pixels, 0, 64, 0, 0, 64, 32);
    return sample;
  }
}
