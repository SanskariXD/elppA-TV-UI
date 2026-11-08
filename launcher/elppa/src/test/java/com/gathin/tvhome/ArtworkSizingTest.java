package com.gathin.tvhome;

import static org.junit.Assert.*;

import org.junit.Test;

public class ArtworkSizingTest {
  @Test
  public void choosesDetailedLandscapeOverSmallThumbnail() {
    assertTrue(ArtworkSizing.heroScore(1920, 1080) > ArtworkSizing.heroScore(320, 180));
  }

  @Test
  public void avoidsCroppingTallPosterWhenLandscapeExists() {
    assertTrue(ArtworkSizing.heroScore(1280, 720) > ArtworkSizing.heroScore(2000, 3000));
  }

  @Test
  public void avoidsUndersamplingIntermediateResolution() {
    assertEquals(1, ArtworkSizing.sample(2560, 1440, 1920, 1080));
    assertEquals(2, ArtworkSizing.sample(3840, 2160, 1920, 1080));
  }

  @Test
  public void boundsExtremeAspectAllocation() {
    int sample = ArtworkSizing.sample(1000, 40000, 320, 180);
    assertTrue((1000L / sample) * (40000 / sample) <= 8_000_000L);
  }

  @Test
  public void rejectsInvalidAndHugeSources() {
    assertEquals(-1, ArtworkSizing.heroScore(0, 0));
    assertEquals(-1, ArtworkSizing.heroScore(20000, 20000));
  }
}
