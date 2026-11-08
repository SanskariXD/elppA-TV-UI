package com.gathin.tvhome;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Source-independent identity and image rules, kept free of Android/network dependencies. */
final class CataloguePolicy {
  static final class Title {
    final String id, kind, name;
    final int year;

    Title(String id, String kind, String name, int year) {
      this.id = id;
      this.kind = kind;
      this.name = name;
      this.year = year;
    }
  }

  static final class Image {
    final String type, url;
    final int width, height;

    Image(String type, String url, int width, int height) {
      this.type = type;
      this.url = url;
      this.width = width;
      this.height = height;
    }
  }

  private CataloguePolicy() {}

  static boolean validKey(String key) {
    return key != null && key.matches("[A-Za-z0-9_-]{8,128}");
  }

  static boolean validImdbId(String id) {
    return id != null && id.matches("tt[0-9]{7,12}");
  }

  static int year(String value) {
    if (value == null || !value.matches("(?:18|19|20|21)[0-9]{2}(?:[-–].*)?")) return 0;
    return Integer.parseInt(value.substring(0, 4));
  }

  static boolean matches(CatalogueMatch.Query query, Title title) {
    return title != null
        && !CatalogueMatch.normalize(query.title).isEmpty()
        && CatalogueMatch.normalize(query.title).equals(CatalogueMatch.normalize(title.name))
        && (query.year == 0 || query.year == title.year);
  }

  static Title unique(CatalogueMatch.Query query, List<Title> candidates) {
    Map<String, Title> exact = new LinkedHashMap<>();
    for (Title title : candidates) {
      if (title.id == null || title.id.isEmpty()) continue;
      if (!"movie".equals(title.kind) && !"series".equals(title.kind)) continue;
      if (matches(query, title)) exact.put(title.kind + ":" + title.id, title);
    }
    return exact.size() == 1 ? exact.values().iterator().next() : null;
  }

  /** Original TVmaze uploads only. No alternate CDN, query parameters, or guessed derivative. */
  static boolean validImageUrl(String url) {
    return url != null
        && url.matches(
            "https://static\\.tvmaze\\.com/uploads/images/original_untouched/[0-9]+/[0-9]+\\.(?:jpg|jpeg|png|webp)");
  }

  static String chooseImage(List<Image> images, boolean logo) {
    String selected = "";
    double best = -1;
    for (Image image : images) {
      if (!validImageUrl(image.url)) continue;
      double score;
      if (logo) {
        // "Typography" alone says nothing about transparency: JPEG typography is not an overlay.
        // The bitmap consumer must also check alpha before replacing the readable title.
        if (!"typography".equals(image.type)
            || !image.url.endsWith(".png")
            || image.width < 200
            || image.height < 30
            || image.width > 4096
            || image.height > 2048
            || image.width < image.height * .3
            || image.width > image.height * 10) continue;
        score = Math.min(image.width, 1500);
      } else {
        if (!"background".equals(image.type)
            || image.width < 1280
            || image.height < 720
            || image.width > 4096
            || image.height > 2304
            || image.width < image.height * 1.5
            || image.width > image.height * 2.1) continue;
        score =
            Math.min(image.width, 1920)
                - Math.abs((double) image.width / image.height - 16d / 9) * 200;
      }
      if (score > best) {
        selected = image.url;
        best = score;
      }
    }
    return selected;
  }

  static String detail(String year, String languages, String imdbRating) {
    StringBuilder text = new StringBuilder();
    if (year != null && year.matches("(?:18|19|20|21)[0-9]{2}(?:[-–][0-9]{0,4})?"))
      text.append(year);
    if (languages != null && !"N/A".equals(languages) && languages.length() <= 250) {
      String[] all = languages.split(",\\s*");
      StringBuilder languageText = new StringBuilder();
      for (int i = 0; i < Math.min(all.length, 2); i++) {
        String part = all[i].trim();
        if (!part.matches("[\\p{L} .'-]{1,40}")) continue;
        if (languageText.length() > 0) languageText.append(", ");
        languageText.append(part);
      }
      if (languageText.length() > 0) {
        if (all.length > 2) languageText.append(" +").append(all.length - 2);
        append(text, languageText.toString());
      }
    }
    if (imdbRating != null && imdbRating.matches("(?:[0-9](?:\\.[0-9])?|10(?:\\.0)?)"))
      append(text, "IMDb " + imdbRating + "/10");
    return text.toString();
  }

  private static void append(StringBuilder text, String part) {
    if (text.length() > 0) text.append(" · ");
    text.append(part);
  }

  /** Service errors are classified, never echoed: OMDb authentication uses a URL query key. */
  static int omdbError(String error) {
    String text = error == null ? "" : error.toLowerCase(Locale.ROOT);
    if (text.contains("api key") || text.contains("apikey") || text.contains("not activated"))
      return 401;
    if (text.contains("limit reached") || text.contains("request limit")) return 429;
    if (text.contains("not found")) return 404;
    if (text.contains("too many results")) return 413;
    return 500;
  }
}
