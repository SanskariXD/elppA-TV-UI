package com.gathin.tvhome;

/** Pure sizing policy: prefer real landscape detail; never manufacture larger source images. */
final class ArtworkSizing {
  private ArtworkSizing() {}

  static long heroScore(int width, int height) {
    if (width <= 0 || height <= 0 || (long) width * height > 40_000_000L) return -1;
    long usableWidth = Math.min(width, (long) height * 16 / 9);
    // A landscape image is compositionally safer than cropping a tall poster to fill a TV.
    return (width >= height * 1.25 ? 1_000_000L : 0) + usableWidth;
  }

  static int sample(int width, int height, int targetWidth, int targetHeight) {
    int sample = 1;
    // Decode above the final bounded size, then resize once. Avoid a power-of-two undershoot.
    while (width / (sample * 2) >= targetWidth && height / (sample * 2) >= targetHeight)
      sample *= 2;
    // Tall/wide outliers must not turn a small tile into a huge bitmap allocation.
    while ((long) (width / sample) * (height / sample) > 8_000_000L) sample *= 2;
    return sample;
  }
}
