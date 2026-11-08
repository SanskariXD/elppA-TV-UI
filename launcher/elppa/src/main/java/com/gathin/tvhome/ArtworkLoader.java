package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Two bounded workers; 16 MiB decoded cache, 48 MiB private disk cache, no hidden-Home polling. */
final class ArtworkLoader {
  private static final long DISK_LIMIT = 48L * 1024 * 1024, MAX_FILE = 8L * 1024 * 1024;
  private final Context context;
  private final File directory;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final AtomicInteger generation = new AtomicInteger();
  private final ThreadPoolExecutor workers =
      new ThreadPoolExecutor(
          2,
          2,
          20,
          TimeUnit.SECONDS,
          new LinkedBlockingQueue<>(24),
          new ThreadPoolExecutor.DiscardOldestPolicy());
  private final LruCache<String, Bitmap> memory =
      new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
          return value.getAllocationByteCount();
        }
      };

  ArtworkLoader(Context context) {
    this.context = context.getApplicationContext();
    directory = new File(context.getCacheDir(), "media_art");
    directory.mkdirs();
    workers.allowCoreThreadTimeOut(true);
  }

  void load(ImageView target, String uri, int width, int height) {
    load(target, uri, width, height, false, false);
  }

  interface HeroListener {
    void ready(Bitmap frost);
  }

  private HeroListener heroListener;
  private Bitmap currentFrost;

  void setHeroListener(HeroListener listener) {
    heroListener = listener;
  }

  private String displayedHeroKey;
  private volatile boolean clearPending;
  private volatile String heroQuality = "No featured image loaded";

  void clearHero(ImageView target) {
    target.setTag(null);
    target.setImageDrawable(null);
    displayedHeroKey = null;
    currentFrost = null;
    heroQuality = "No featured image loaded";
    if (heroListener != null) heroListener.ready(null);
  }

  String heroQuality() {
    return heroQuality;
  }

  void loadHero(ImageView target, String thumbnail, String poster, boolean motion) {
    final String key = "hero-v4:" + thumbnail + "|" + poster;
    if (key.equals(displayedHeroKey) && target.getDrawable() != null) {
      target.setTag(key);
      if (heroListener != null) heroListener.ready(currentFrost);
      return;
    }
    target.setTag(key);
    int ticket = generation.get();
    final int canvasWidth = Math.max(960, target.getWidth()),
        canvasHeight = Math.max(540, target.getHeight());
    workers.execute(
        () -> {
          if (ticket != generation.get()) return;
          // Use the best app-published source by dimensions, retaining its original URL and link.
          String best = thumbnail;
          int[] chosen = dimensions(thumbnail);
          if (!poster.equals(thumbnail)) {
            int[] alternative = dimensions(poster);
            if (ArtworkSizing.heroScore(alternative[0], alternative[1])
                > ArtworkSizing.heroScore(chosen[0], chosen[1])) {
              best = poster;
              chosen = alternative;
            }
          }
          Bitmap bitmap = memory.get("hero:" + best);
          if (bitmap == null) bitmap = read(best, 1920, 1080);
          if (bitmap == null) {
            best = best.equals(thumbnail) ? poster : thumbnail;
            chosen = dimensions(best);
            bitmap = memory.get("hero:" + best);
            if (bitmap == null) bitmap = read(best, 1920, 1080);
          }
          if (ticket != generation.get()) return;
          if (bitmap != null) memory.put("hero:" + best, bitmap);
          final Bitmap result = bitmap;
          final Bitmap frost = FrostedPlayDrawable.sample(bitmap, canvasWidth, canvasHeight);
          final String quality =
              bitmap == null
                  ? "Image unavailable"
                  : "Source "
                      + chosen[0]
                      + " × "
                      + chosen[1]
                      + " · decoded "
                      + bitmap.getWidth()
                      + " × "
                      + bitmap.getHeight()
                      + (chosen[0] < 1280 || chosen[1] < 720 ? " · Small source image" : "");
          main.post(
              () -> {
                if (ticket != generation.get() || !key.equals(target.getTag())) return;
                currentFrost = frost;
                if (heroListener != null) heroListener.ready(frost);
                heroQuality = quality;
                displayedHeroKey = result == null ? null : key;
                if (result == null) target.setImageDrawable(null);
                else
                  bind(
                      target,
                      result,
                      motion && android.animation.ValueAnimator.areAnimatorsEnabled());
              });
        });
  }

  private int[] dimensions(String address) {
    if (address == null || address.isEmpty()) return new int[] {0, 0};
    try {
      Uri uri = Uri.parse(address);
      InputStream input;
      if ("https".equals(uri.getScheme())) {
        File file = cachedFile(address);
        if (file == null) return new int[] {0, 0};
        input = new FileInputStream(file);
      } else if ("content".equals(uri.getScheme()) || "android.resource".equals(uri.getScheme())) {
        input = context.getContentResolver().openInputStream(uri);
      } else return new int[] {0, 0};
      try (InputStream close = input) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(close, null, options);
        return new int[] {options.outWidth, options.outHeight};
      }
    } catch (Exception ignored) {
      return new int[] {0, 0};
    }
  }

  private void load(
      ImageView target, String uri, int width, int height, boolean retain, boolean motion) {
    target.setTag(uri + "@" + width + "x" + height);
    String key = String.valueOf(target.getTag());
    if (!retain || uri == null || uri.isEmpty()) target.setImageDrawable(null);
    if (uri == null || uri.isEmpty()) return;
    Bitmap cached = memory.get(key);
    if (cached != null) {
      bind(target, cached, motion);
      return;
    }
    int ticket = generation.get();
    workers.execute(
        () -> {
          if (ticket != generation.get()) return;
          Bitmap bitmap = read(uri, width, height);
          if (ticket != generation.get()) return;
          if (bitmap != null) memory.put(key, bitmap);
          main.post(
              () -> {
                if (ticket == generation.get() && key.equals(target.getTag())) {
                  if (bitmap == null) target.setImageDrawable(null);
                  else bind(target, bitmap, motion);
                }
              });
        });
  }

  private void bind(ImageView target, Bitmap bitmap, boolean motion) {
    Drawable old = target.getDrawable();
    if (old instanceof TransitionDrawable) {
      TransitionDrawable transition = (TransitionDrawable) old;
      old = transition.getDrawable(transition.getNumberOfLayers() - 1);
    }
    if (motion && old != null) {
      TransitionDrawable transition =
          new TransitionDrawable(new Drawable[] {old, drawable(bitmap)});
      transition.setCrossFadeEnabled(true);
      target.setImageDrawable(transition);
      transition.startTransition(250);
    } else target.setImageDrawable(drawable(bitmap));
  }

  private Drawable drawable(Bitmap bitmap) {
    BitmapDrawable drawable = new BitmapDrawable(context.getResources(), bitmap);
    drawable.setFilterBitmap(true);
    drawable.setDither(true);
    return drawable;
  }

  private Bitmap read(String address, int width, int height) {
    try {
      Uri uri = Uri.parse(address);
      if ("https".equals(uri.getScheme())) {
        File file = cachedFile(address);
        if (file == null) return null;
        try (InputStream first = new FileInputStream(file)) {
          BitmapFactory.Options options = bounds(first, width, height);
          if (options == null) return null;
          try (InputStream second = new FileInputStream(file)) {
            return decode(second, options, width, height);
          }
        }
      }
      if (!"content".equals(uri.getScheme()) && !"android.resource".equals(uri.getScheme()))
        return null;
      try (InputStream first = context.getContentResolver().openInputStream(uri)) {
        BitmapFactory.Options options = bounds(first, width, height);
        if (options == null) return null;
        try (InputStream second = context.getContentResolver().openInputStream(uri)) {
          return decode(second, options, width, height);
        }
      }
    } catch (Exception | OutOfMemoryError ignored) {
      return null;
    }
  }

  private static BitmapFactory.Options bounds(InputStream input, int width, int height) {
    if (input == null) return null;
    BitmapFactory.Options o = new BitmapFactory.Options();
    o.inJustDecodeBounds = true;
    BitmapFactory.decodeStream(input, null, o);
    if (o.outWidth <= 0 || o.outHeight <= 0 || (long) o.outWidth * o.outHeight > 40_000_000L)
      return null;
    int limitW = Math.min(1920, Math.max(96, width)), limitH = Math.min(1080, Math.max(54, height));
    o.inJustDecodeBounds = false;
    o.inSampleSize = ArtworkSizing.sample(o.outWidth, o.outHeight, limitW, limitH);
    o.inScaled = false;
    o.inPreferredConfig = Bitmap.Config.ARGB_8888;
    return o;
  }

  private static Bitmap decode(
      InputStream input, BitmapFactory.Options options, int width, int height) {
    Bitmap decoded = BitmapFactory.decodeStream(input, null, options);
    if (decoded == null) return null;
    float ratio =
        Math.min(
            1f,
            Math.min(
                Math.min(width, 1920f) / decoded.getWidth(),
                Math.min(height, 1080f) / decoded.getHeight()));
    if (ratio >= 1) return decoded;
    Bitmap resized =
        Bitmap.createScaledBitmap(
            decoded,
            Math.max(1, Math.round(decoded.getWidth() * ratio)),
            Math.max(1, Math.round(decoded.getHeight() * ratio)),
            true);
    if (resized != decoded) decoded.recycle();
    return resized;
  }

  private synchronized File cachedFile(String address) throws Exception {
    clearDiskIfRequested();
    StringBuilder hash = new StringBuilder();
    for (byte b :
        MessageDigest.getInstance("SHA-256")
            .digest(address.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
      hash.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    File target = new File(directory, hash.toString());
    if (target.isFile() && target.length() > 0) {
      target.setLastModified(System.currentTimeMillis());
      return target;
    }
    URL url = new URL(address);
    HttpURLConnection connection = null;
    try {
      for (int redirects = 0; redirects < 4; redirects++) {
        if (!"https".equals(url.getProtocol())) return null;
        connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "image/*");
        int status = connection.getResponseCode();
        if (status >= 300 && status < 400) {
          String location = connection.getHeaderField("Location");
          if (location == null) return null;
          url = new URL(url, location);
          connection.disconnect();
          continue;
        }
        if (status != 200 || connection.getContentLengthLong() > MAX_FILE) return null;
        File temp = File.createTempFile("art-", ".tmp", directory);
        try {
          try (InputStream input = connection.getInputStream();
              FileOutputStream out = new FileOutputStream(temp)) {
            byte[] buffer = new byte[16384];
            long total = 0;
            int n;
            while ((n = input.read(buffer)) != -1) {
              total += n;
              if (total > MAX_FILE || Thread.currentThread().isInterrupted()) return null;
              out.write(buffer, 0, n);
            }
          }
          if (!temp.renameTo(target)) return null;
        } finally {
          temp.delete();
        }
        prune();
        return target;
      }
      return null;
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private void prune() {
    File[] files = directory.listFiles();
    if (files == null) return;
    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
    long total = 0;
    for (File f : files) total += f.length();
    for (File f : files)
      if (total > DISK_LIMIT) {
        total -= f.length();
        f.delete();
      }
  }

  void pause() {
    generation.incrementAndGet();
    workers.getQueue().clear();
    main.removeCallbacksAndMessages(null);
  }

  void trim() {
    memory.evictAll();
  }

  void clear() {
    pause();
    displayedHeroKey = null;
    currentFrost = null;
    heroQuality = "Artwork cache cleared";
    trim();
    clearPending = true;
    workers.execute(this::clearDiskIfRequested);
  }

  private synchronized void clearDiskIfRequested() {
    if (!clearPending) return;
    File[] files = directory.listFiles();
    if (files != null) for (File f : files) f.delete();
    clearPending = false;
  }

  void close() {
    pause();
    workers.shutdownNow();
    trim();
  }
}
