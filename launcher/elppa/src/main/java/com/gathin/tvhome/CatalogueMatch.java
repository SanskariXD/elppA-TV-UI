package com.gathin.tvhome;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Conservative title matching. A search result rank is never proof of identity. */
final class CatalogueMatch {
  private static final Pattern YEAR =
      Pattern.compile("\\s*[\\[(]((?:18|19|20|21)\\d{2})[\\])]\\s*$");

  static final class Query {
    final String title;
    final int year;

    Query(String value) {
      String clean = value == null ? "" : value.trim();
      Matcher match = YEAR.matcher(clean);
      year = match.find() ? Integer.parseInt(match.group(1)) : 0;
      title = year == 0 ? clean : clean.substring(0, match.start()).trim();
    }
  }

  static final class Candidate {
    final long id;
    final String kind, title, originalTitle;
    final int year;

    Candidate(long id, String kind, String title, String originalTitle, int year) {
      this.id = id;
      this.kind = kind;
      this.title = title;
      this.originalTitle = originalTitle;
      this.year = year;
    }
  }

  private CatalogueMatch() {}

  static String normalize(String value) {
    return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .trim();
  }

  /** Null means absent or ambiguous. Dates only disambiguate if explicitly present in the title. */
  static Candidate unique(Query query, List<Candidate> results) {
    String wanted = normalize(query.title);
    if (wanted.isEmpty()) return null;
    Map<String, Candidate> exact = new LinkedHashMap<>();
    for (Candidate candidate : results) {
      if (candidate.id <= 0 || (!"movie".equals(candidate.kind) && !"tv".equals(candidate.kind)))
        continue;
      if (query.year != 0 && candidate.year != query.year) continue;
      if (wanted.equals(normalize(candidate.title))
          || wanted.equals(normalize(candidate.originalTitle)))
        exact.put(candidate.kind + ":" + candidate.id, candidate);
    }
    return exact.size() == 1 ? exact.values().iterator().next() : null;
  }
}
