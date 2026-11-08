package com.gathin.tvhome;

import static org.junit.Assert.*;

import java.util.Arrays;
import org.junit.Test;

public class CatalogueMatchTest {
  private CatalogueMatch.Candidate movie(long id, String title, int year) {
    return new CatalogueMatch.Candidate(id, "movie", title, title, year);
  }

  @Test
  public void refusesRemakeWithoutExplicitYear() {
    assertNull(
        CatalogueMatch.unique(
            new CatalogueMatch.Query("Dune"),
            Arrays.asList(movie(1, "Dune", 1984), movie(2, "Dune", 2021))));
  }

  @Test
  public void explicitTrailingYearDisambiguates() {
    CatalogueMatch.Candidate found =
        CatalogueMatch.unique(
            new CatalogueMatch.Query("Dune (2021)"),
            Arrays.asList(movie(1, "Dune", 1984), movie(2, "Dune", 2021)));
    assertNotNull(found);
    assertEquals(2, found.id);
  }

  @Test
  public void numericMovieTitleIsNotTreatedAsReleaseYear() {
    CatalogueMatch.Query query = new CatalogueMatch.Query("1917");
    assertEquals(0, query.year);
    assertEquals("1917", query.title);
  }

  @Test
  public void differentMediaTypesAreAmbiguous() {
    assertNull(
        CatalogueMatch.unique(
            new CatalogueMatch.Query("Fargo"),
            Arrays.asList(
                movie(1, "Fargo", 1996),
                new CatalogueMatch.Candidate(1, "tv", "Fargo", "Fargo", 2014))));
  }

  @Test
  public void rejectsPopularPartialMatch() {
    assertNull(
        CatalogueMatch.unique(
            new CatalogueMatch.Query("Dune Part Three"),
            Arrays.asList(movie(2, "Dune: Part Two", 2024))));
  }

  @Test
  public void comparesOriginalTitleWithoutTransliterating() {
    CatalogueMatch.Candidate film =
        new CatalogueMatch.Candidate(1, "movie", "Translated Title", "ಕಾಂತಾರ", 2022);
    assertNotNull(CatalogueMatch.unique(new CatalogueMatch.Query("ಕಾಂತಾರ"), Arrays.asList(film)));
    assertNull(CatalogueMatch.unique(new CatalogueMatch.Query("Kantara"), Arrays.asList(film)));
  }

  @Test
  public void duplicateSameIdAcrossPagesIsOneTitle() {
    assertNotNull(
        CatalogueMatch.unique(
            new CatalogueMatch.Query("The Batman"),
            Arrays.asList(movie(1, "The Batman", 2022), movie(1, "The Batman", 2022))));
  }

  @Test
  public void normalizesPunctuationButPreservesNumbers() {
    assertNotNull(
        CatalogueMatch.unique(
            new CatalogueMatch.Query("Dune: Part Two"),
            Arrays.asList(movie(1, "Dune - Part Two", 2024))));
    assertNull(
        CatalogueMatch.unique(
            new CatalogueMatch.Query("Dune 2"), Arrays.asList(movie(1, "Dune", 2021))));
  }

  @Test
  public void wrongExplicitYearCannotMatch() {
    assertNull(
        CatalogueMatch.unique(
            new CatalogueMatch.Query("Dune (2000)"), Arrays.asList(movie(2, "Dune", 2021))));
  }

  @Test
  public void emptyAndInvalidCandidatesCannotMatch() {
    assertNull(
        CatalogueMatch.unique(new CatalogueMatch.Query(""), Arrays.asList(movie(1, "", 2022))));
    assertNull(
        CatalogueMatch.unique(
            new CatalogueMatch.Query("Example"), Arrays.asList(movie(0, "Example", 2022))));
  }
}
