package com.gathin.tvhome;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class MediaSelectionTest {
  private static final long NOW = 200 * MediaSelection.DAY;

  private MediaEntry item(
      String id, String app, int type, long engaged, long pos, long duration, boolean preview) {
    return new MediaEntry(
        id,
        app,
        "A title",
        "Episode 2",
        "https://image",
        "https://wide",
        "app://title",
        type,
        3,
        engaged,
        pos,
        duration,
        preview);
  }

  @Test
  public void unfinishedAndNextDoNotRequireTheSameProgress() {
    MediaEntry ongoing = item("1", "a", 0, NOW, 30, 100, false),
        next = item("2", "b", 1, NOW, 0, 0, false),
        finished = item("3", "c", 0, NOW, 98, 100, false);
    List<MediaEntry> result =
        MediaSelection.watchNext(
            Arrays.asList(ongoing, next, finished),
            Collections.emptySet(),
            Collections.emptySet(),
            NOW);
    assertEquals(2, result.size());
    assertTrue(result.contains(next));
    assertFalse(result.contains(finished));
  }

  @Test
  public void staleRowsExcludedButUndatedEpisodesSurvive() {
    MediaEntry stale = item("1", "a", 1, NOW - 91 * MediaSelection.DAY, 0, 0, false),
        undated = item("2", "b", 1, 0, 0, 0, false);
    assertEquals(
        Collections.singletonList(undated),
        MediaSelection.watchNext(
            Arrays.asList(stale, undated), Collections.emptySet(), Collections.emptySet(), NOW));
  }

  @Test
  public void duplicateLinksKeepMostRecentAndRespectSourceChoices() {
    MediaEntry old = item("1", "a", 0, NOW - 1, 10, 100, false),
        recent = item("2", "a", 0, NOW, 20, 100, false),
        other = item("3", "b", 1, NOW, 0, 0, false);
    List<MediaEntry> result =
        MediaSelection.watchNext(
            Arrays.asList(old, recent, other),
            Collections.singleton("b"),
            Collections.emptySet(),
            NOW);
    assertEquals(Collections.singletonList(recent), result);
    assertEquals(
        1,
        MediaSelection.watchNext(
                Arrays.asList(old, recent), Collections.emptySet(), Collections.singleton("2"), NOW)
            .size());
  }

  @Test
  public void missingAndInvalidRowsNeverPretendToBeContinueWatching() {
    MediaEntry unknown = item("1", "a", -1, NOW, 0, 0, false);
    MediaEntry missing = new MediaEntry("2", "b", "", "", "", "", "", 0, 0, 0, 0, 0, false);
    assertTrue(
        MediaSelection.watchNext(
                Arrays.asList(unknown, missing),
                Collections.emptySet(),
                Collections.emptySet(),
                NOW)
            .isEmpty());
  }

  @Test
  public void recommendationsHaveArtworkAndAreNotWatchNext() {
    MediaEntry watch = item("1", "a", 1, NOW, 0, 0, false),
        preview = item("2", "b", -1, 0, 0, 0, true);
    assertEquals(
        Collections.singletonList(preview),
        MediaSelection.recommendations(
            Arrays.asList(watch, preview), Collections.emptySet(), Collections.emptySet()));
  }

  @Test
  public void progressIsBoundedWithoutIntegerOverflow() {
    MediaEntry huge = item("1", "a", 0, NOW, Long.MAX_VALUE, Long.MAX_VALUE, false);
    assertEquals(100, huge.progress());
    assertEquals(0, item("2", "a", 1, NOW, 50, 0, false).progress());
  }

  @Test
  public void labelsCannotInjectBidiControlsOrNewlines() {
    assertEquals("Movie   title", MediaEntry.text("Movie\n\u202E title"));
  }

  @Test
  public void largeFeedsStayBoundedAndWatchNextKeepsTheNewestEntries() {
    List<MediaEntry> watch = new java.util.ArrayList<>();
    List<MediaEntry> previews = new java.util.ArrayList<>();
    for (int i = 0; i < 100; i++) {
      watch.add(item("w" + i, "app." + i, 1, NOW - i, 0, 0, false));
      previews.add(item("p" + i, "app." + i, -1, 0, 0, 0, true));
    }
    Collections.reverse(watch);
    List<MediaEntry> selected =
        MediaSelection.watchNext(watch, Collections.emptySet(), Collections.emptySet(), NOW);
    assertEquals(40, selected.size());
    assertEquals("w0", selected.get(0).key);
    assertEquals("w39", selected.get(39).key);
    assertEquals(
        30,
        MediaSelection.recommendations(previews, Collections.emptySet(), Collections.emptySet())
            .size());
  }

  @Test
  public void repeatedProviderRowsCannotFillTheFeaturedCarousel() {
    MediaEntry first = item("1", "app", -1, 0, 0, 0, true);
    MediaEntry duplicate = item("2", "app", -1, 0, 0, 0, true);
    assertEquals(
        Collections.singletonList(first),
        MediaSelection.recommendations(
            Arrays.asList(first, duplicate), Collections.emptySet(), Collections.emptySet()));
  }

  @Test
  public void clipsAndMissingArtworkDoNotBecomeFeaturedMovies() {
    MediaEntry clip =
        new MediaEntry(
            "clip",
            "app",
            "Clip",
            "",
            "https://poster",
            "https://wide",
            "app://clip",
            -1,
            4,
            0,
            0,
            0,
            true);
    MediaEntry noArt =
        new MediaEntry("noart", "app", "Movie", "", "", "", "app://movie", -1, 0, 0, 0, 0, true);
    assertTrue(
        MediaSelection.recommendations(
                Arrays.asList(clip, noArt), Collections.emptySet(), Collections.emptySet())
            .isEmpty());
  }

  @Test
  public void excludingAllSourcesLeavesNoFeaturedContent() {
    MediaEntry preview = item("1", "app", -1, 0, 0, 0, true);
    assertTrue(
        MediaSelection.recommendations(
                Collections.singletonList(preview),
                Collections.singleton("app"),
                Collections.emptySet())
            .isEmpty());
  }
}
