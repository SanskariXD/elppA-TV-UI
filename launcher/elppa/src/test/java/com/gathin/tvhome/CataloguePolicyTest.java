package com.gathin.tvhome;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class CataloguePolicyTest {
  private static final String CDN =
      "https://static.tvmaze.com/uploads/images/original_untouched/544/";

  @Test
  public void remakeRequiresExplicitYearAndKeepsImdbLeadingZeros() {
    CataloguePolicy.Title old = new CataloguePolicy.Title("tt0087182", "movie", "Dune", 1984);
    CataloguePolicy.Title recent = new CataloguePolicy.Title("tt1160419", "movie", "Dune", 2021);
    assertNull(
        CataloguePolicy.unique(new CatalogueMatch.Query("Dune"), Arrays.asList(old, recent)));
    assertEquals(
        "tt0087182",
        CataloguePolicy.unique(new CatalogueMatch.Query("Dune (1984)"), Arrays.asList(old, recent))
            .id);
  }

  @Test
  public void unknownTypeCannotChooseMovieOverShow() {
    assertNull(
        CataloguePolicy.unique(
            new CatalogueMatch.Query("Fargo"),
            Arrays.asList(
                new CataloguePolicy.Title("tt0116282", "movie", "Fargo", 1996),
                new CataloguePolicy.Title("tt2802850", "series", "Fargo", 2014))));
  }

  @Test
  public void exactIndianTitlesAreSupportedWithoutFuzzyTranslation() {
    CataloguePolicy.Title title =
        new CataloguePolicy.Title("1", "series", "Suzhal - The Vortex", 2022);
    assertNotNull(
        CataloguePolicy.unique(
            new CatalogueMatch.Query("Suzhal: The Vortex"), Arrays.asList(title)));
    assertNull(
        CataloguePolicy.unique(new CatalogueMatch.Query("The Vortex"), Arrays.asList(title)));
    CataloguePolicy.Title kannada = new CataloguePolicy.Title("2", "series", "ಏಕಂ", 2024);
    assertNotNull(CataloguePolicy.unique(new CatalogueMatch.Query("ಏಕಂ"), Arrays.asList(kannada)));
    assertNull(CataloguePolicy.unique(new CatalogueMatch.Query("Ekam"), Arrays.asList(kannada)));
  }

  @Test
  public void episodeIsNeverTreatedAsMovieOrSeriesCandidate() {
    assertNull(
        CataloguePolicy.unique(
            new CatalogueMatch.Query("Pilot"),
            Arrays.asList(new CataloguePolicy.Title("tt1234567", "episode", "Pilot", 2011))));
  }

  @Test
  public void parsesSeriesStartYearAndTvmazePremiereButNotMissingYear() {
    assertEquals(2011, CataloguePolicy.year("2011–2019"));
    assertEquals(2011, CataloguePolicy.year("2011–"));
    assertEquals(2022, CataloguePolicy.year("2022-06-17"));
    assertEquals(0, CataloguePolicy.year("N/A"));
    assertEquals(0, CataloguePolicy.year("S1 E2022"));
  }

  @Test
  public void showBackgroundPrefersFullHdLandscapeOverPortraitAndLowResolution() {
    assertEquals(
        CDN + "3.jpg",
        CataloguePolicy.chooseImage(
            Arrays.asList(
                new CataloguePolicy.Image("poster", CDN + "1.jpg", 2000, 3000),
                new CataloguePolicy.Image("background", CDN + "2.jpg", 1200, 700),
                new CataloguePolicy.Image("background", CDN + "3.jpg", 1920, 1080),
                new CataloguePolicy.Image("background", CDN + "4.jpg", 1280, 720)),
            false));
  }

  @Test
  public void rejectsMirzapurFourByThreeBackdropEvenIfLarger() {
    assertEquals(
        CDN + "2.jpg",
        CataloguePolicy.chooseImage(
            Arrays.asList(
                new CataloguePolicy.Image("background", CDN + "1.jpg", 2560, 1920),
                new CataloguePolicy.Image("background", CDN + "2.jpg", 1280, 720)),
            false));
  }

  @Test
  public void neitherPosterNorOversizeImageIsHeroFallback() {
    assertEquals(
        "",
        CataloguePolicy.chooseImage(
            Arrays.asList(
                new CataloguePolicy.Image("poster", CDN + "1.jpg", 1920, 1080),
                new CataloguePolicy.Image("background", CDN + "2.jpg", 8000, 4500),
                new CataloguePolicy.Image("background", CDN + "3.jpg", 1200, 700)),
            false));
  }

  @Test
  public void actualEkamJpegTypographyCannotReplaceTitle() {
    assertEquals(
        "",
        CataloguePolicy.chooseImage(
            Arrays.asList(new CataloguePolicy.Image("typography", CDN + "1361880.jpg", 2043, 748)),
            true));
    assertEquals(
        CDN + "2.png",
        CataloguePolicy.chooseImage(
            Arrays.asList(
                new CataloguePolicy.Image("typography", CDN + "1.jpg", 2043, 748),
                new CataloguePolicy.Image("typography", CDN + "2.png", 1000, 300)),
            true));
  }

  @Test
  public void rejectsImageUrlRewritesCredentialLeaksAndOffHostRequests() {
    assertTrue(CataloguePolicy.validImageUrl(CDN + "1361880.jpg"));
    assertFalse(CataloguePolicy.validImageUrl(CDN + "1361880.jpg?apikey=private"));
    assertFalse(CataloguePolicy.validImageUrl(CDN.replace("https", "http") + "1361880.jpg"));
    assertFalse(
        CataloguePolicy.validImageUrl(
            "https://static.tvmaze.com.evil.test/uploads/images/original_untouched/1/2.png"));
    assertFalse(
        CataloguePolicy.validImageUrl(
            "https://static.tvmaze.com/uploads/images/medium_portrait/1/2.png"));
    assertFalse(CataloguePolicy.validImageUrl(CDN + "../1.png"));
  }

  @Test
  public void imdbIdsAndKeysHaveNoUrlOrHeaderSyntax() {
    assertTrue(CataloguePolicy.validImdbId("tt0944947"));
    assertFalse(CataloguePolicy.validImdbId("tt0944947&apikey=x"));
    assertFalse(CataloguePolicy.validImdbId("1234567"));
    assertTrue(CataloguePolicy.validKey("example1"));
    assertFalse(CataloguePolicy.validKey("example1&other=value"));
    assertFalse(CataloguePolicy.validKey("Bearer example1"));
  }

  @Test
  public void metadataUsesVerifiedYearLanguagesAndImdbRatingOnly() {
    assertEquals(
        "2024 · Hindi, Tamil +1 · IMDb 8.2/10",
        CataloguePolicy.detail("2024", "Hindi, Tamil, Telugu", "8.2"));
    assertEquals(
        "2011–2019 · English · IMDb 9.2/10", CataloguePolicy.detail("2011–2019", "English", "9.2"));
    assertEquals("", CataloguePolicy.detail("N/A", "N/A", "N/A"));
    assertEquals("2024", CataloguePolicy.detail("2024", "<script>bad</script>", "99.0"));
  }

  @Test
  public void omdbErrorsMapToFixedStatusesWithoutEchoingPrivateValues() {
    assertEquals(401, CataloguePolicy.omdbError("Invalid API key!"));
    assertEquals(401, CataloguePolicy.omdbError("No API key provided."));
    assertEquals(429, CataloguePolicy.omdbError("Request limit reached!"));
    assertEquals(404, CataloguePolicy.omdbError("Movie not found!"));
    assertEquals(413, CataloguePolicy.omdbError("Too many results."));
    assertEquals(500, CataloguePolicy.omdbError("Unexpected error containing private value"));
  }
}
