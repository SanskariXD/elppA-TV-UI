package com.gathin.tvhome;

import android.content.Context;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional TMDB enrichment of app titles. Call on a bounded worker, never the UI thread. */
final class CatalogueArtwork {
  static final String NOTICE =
      "This product uses the TMDB API but is not endorsed or certified by TMDB.";
  private static final long DAY = 86_400_000L;
  private static final int JSON_LIMIT = 2 * 1024 * 1024, CACHE_LIMIT = 256, SEARCH_PAGES = 3;
  private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9._-]{20,4096}");
  private static final Pattern IMAGE_PATH =
      Pattern.compile("/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)");
  private final File directory;
  private final Object cacheLock = new Object();
  private volatile int epoch;
  private JSONObject config;
  private long configTime;
  private volatile long retryAfter;
  private volatile String retryStatus = "";

  static final class Result {
    final String backdrop, logo, attribution, status;
    final boolean matched;

    Result(String backdrop, String logo, boolean matched, String status) {
      this.backdrop = backdrop;
      this.logo = logo;
      this.matched = matched;
      this.status = status;
      attribution = backdrop.isEmpty() && logo.isEmpty() ? "" : "TMDB";
    }

    static Result empty(String status) {
      return new Result("", "", false, status);
    }
  }

  CatalogueArtwork(Context context) {
    directory = new File(context.getCacheDir(), "catalogue_artwork_v1");
    directory.mkdirs();
  }

  Result resolve(MediaEntry entry, String readToken, CancellationSignal signal) {
    String token = readToken == null ? "" : readToken.trim();
    if (token.startsWith("Bearer ")) token = token.substring(7).trim();
    if (token.isEmpty()) return Result.empty("Add your TMDB read token to enable clean artwork");
    if (!TOKEN.matcher(token).matches()) return Result.empty("Check the TMDB read token format");
    check(signal);
    if (entry == null || "demo".equals(entry.source)) return Result.empty("Sample artwork");
    // Episode/season title alone cannot identify the owning series safely.
    if (entry.contentType == 2 || entry.contentType == 3 || entry.contentType > 3)
      return Result.empty("Series identity unavailable for this episode or season");
    CatalogueMatch.Query query = new CatalogueMatch.Query(entry.title);
    if (query.title.isEmpty() || query.title.length() > 250)
      return Result.empty("Title is unavailable for matching");
    Locale locale = Locale.getDefault();
    String language = locale.getLanguage();
    if (!language.matches("[a-z]{2,3}")) language = "en";
    String languageTag = locale.toLanguageTag();
    if (!languageTag.matches("[A-Za-z-]{2,20}")) languageTag = "en-US";
    int ticket = epoch;
    String key = digest("v1|" + entry.contentType + "|" + entry.title + "|" + languageTag);
    Result cached = cached(key);
    if (cached != null) return cached;
    if (System.currentTimeMillis() < retryAfter) return Result.empty(retryStatus);
    try {
      List<CatalogueMatch.Candidate> candidates = new ArrayList<>();
      if (entry.contentType == 0 || entry.contentType < 0)
        search("movie", query, languageTag, token, signal, candidates);
      if (entry.contentType == 1 || entry.contentType < 0)
        search("tv", query, languageTag, token, signal, candidates);
      CatalogueMatch.Candidate match = CatalogueMatch.unique(query, candidates);
      if (match == null) {
        Result absent = Result.empty("No unambiguous catalogue match");
        save(key, absent, DAY, ticket);
        return absent;
      }
      JSONObject images =
          get(
              "/"
                  + match.kind
                  + "/"
                  + match.id
                  + "/images?include_image_language="
                  + encode(language + ",en,null"),
              token,
              signal);
      JSONObject configuration = configuration(token, signal).optJSONObject("images");
      if (configuration == null) throw new IOException("Image configuration unavailable");
      String base = configuration.optString("secure_base_url", "");
      // Do not pass credentials or trust off-domain CDN URLs from a response/cache.
      if (!"https://image.tmdb.org/t/p/".equals(base))
        throw new IOException("Unsupported image host");
      String backdropSize = size(configuration.optJSONArray("backdrop_sizes"), 1280, 1920);
      String logoSize = size(configuration.optJSONArray("logo_sizes"), 500, 1000);
      ImageChoice backdrop = image(images.optJSONArray("backdrops"), false, language);
      ImageChoice logo = image(images.optJSONArray("logos"), true, language);
      if (backdrop.width >= 1920
          && backdrop.width <= 3840
          && backdrop.height <= 2160
          && includes(configuration.optJSONArray("backdrop_sizes"), "original"))
        backdropSize = "original";
      Result result =
          new Result(
              backdrop.path.isEmpty() || backdropSize.isEmpty()
                  ? ""
                  : base + backdropSize + backdrop.path,
              logo.path.isEmpty() || logoSize.isEmpty() ? "" : base + logoSize + logo.path,
              true,
              backdrop.path.isEmpty()
                  ? "Matched title; clean high-resolution backdrop unavailable"
                  : logo.path.isEmpty()
                      ? "Clean backdrop available; title logo unavailable"
                      : "Clean backdrop and title logo from TMDB");
      check(signal);
      save(key, result, 7 * DAY, ticket);
      return result;
    } catch (OperationCanceledException canceled) {
      throw canceled;
    } catch (MatchLimit ambiguous) {
      Result absent = Result.empty("Too many possible catalogue titles");
      save(key, absent, DAY, ticket);
      return absent;
    } catch (ApiFailure failure) {
      if (failure.status == 401 || failure.status == 403) {
        retryStatus = "TMDB rejected the read token; check artwork settings";
        retryAfter = System.currentTimeMillis() + 60_000L;
      } else if (failure.status == 429) {
        retryStatus = "TMDB request limit reached";
        retryAfter = System.currentTimeMillis() + 60_000L;
      }
      return Result.empty(
          failure.status == 401 || failure.status == 403 || failure.status == 429
              ? retryStatus
              : "Catalogue temporarily unavailable");
    } catch (Exception failure) {
      check(signal);
      return Result.empty("Catalogue unavailable or ambiguous");
    }
  }

  private void search(
      String kind,
      CatalogueMatch.Query query,
      String language,
      String token,
      CancellationSignal signal,
      List<CatalogueMatch.Candidate> candidates)
      throws Exception {
    int pages = 1;
    for (int page = 1; page <= pages; page++) {
      JSONObject response =
          get(
              "/search/"
                  + kind
                  + "?query="
                  + encode(query.title)
                  + "&language="
                  + encode(language)
                  + "&include_adult=false&page="
                  + page,
              token,
              signal);
      pages = response.optInt("total_pages", 0);
      // A bounded search must never mistake an unseen remake for an unambiguous result.
      if (pages > SEARCH_PAGES) throw new MatchLimit();
      JSONArray results = response.optJSONArray("results");
      if (results == null) continue;
      if (results.length() > 100) throw new IOException("Unexpected search size");
      for (int i = 0; i < results.length(); i++) {
        JSONObject item = results.optJSONObject(i);
        if (item == null || item.optBoolean("adult", false)) continue;
        String date = item.optString("movie".equals(kind) ? "release_date" : "first_air_date", "");
        int year =
            date.matches("\\d{4}-\\d{2}-\\d{2}") ? Integer.parseInt(date.substring(0, 4)) : 0;
        candidates.add(
            new CatalogueMatch.Candidate(
                item.optLong("id", 0),
                kind,
                item.optString("movie".equals(kind) ? "title" : "name", ""),
                item.optString("movie".equals(kind) ? "original_title" : "original_name", ""),
                year));
      }
    }
  }

  private JSONObject configuration(String token, CancellationSignal signal) throws Exception {
    synchronized (this) {
      if (config != null && System.currentTimeMillis() - configTime < DAY) return config;
    }
    JSONObject response = get("/configuration", token, signal);
    synchronized (this) {
      config = response;
      configTime = System.currentTimeMillis();
    }
    return response;
  }

  /**
   * Empty string is success. Always validates this token online; never uses cached configuration.
   */
  String validateToken(String readToken, CancellationSignal signal) {
    String token = readToken == null ? "" : readToken.trim();
    if (token.startsWith("Bearer ")) token = token.substring(7).trim();
    if (!TOKEN.matcher(token).matches())
      return "Enter the API Read Access Token from your TMDB API settings.";
    try {
      JSONObject response = get("/configuration", token, signal);
      if (response.optJSONObject("images") == null)
        return "TMDB returned an incomplete configuration. Try again later.";
      synchronized (this) {
        config = response;
        configTime = System.currentTimeMillis();
      }
      retryAfter = 0;
      retryStatus = "";
      return "";
    } catch (OperationCanceledException canceled) {
      throw canceled;
    } catch (ApiFailure failure) {
      return failure.status == 401 || failure.status == 403
          ? "TMDB rejected this token. Use your API Read Access Token."
          : "TMDB is unavailable or limiting requests. Try again later.";
    } catch (Exception failure) {
      check(signal);
      return "Could not contact TMDB. Check the connection and try again.";
    }
  }

  private static final class ImageChoice {
    final String path;
    final int width, height;

    ImageChoice(String path, int width, int height) {
      this.path = path;
      this.width = width;
      this.height = height;
    }
  }

  private static boolean includes(JSONArray values, String wanted) {
    if (values == null) return false;
    for (int i = 0; i < values.length(); i++) if (wanted.equals(values.optString(i))) return true;
    return false;
  }

  /**
   * Null-language landscapes avoid known translated title treatments; image content is not OCR'd.
   */
  private static ImageChoice image(JSONArray images, boolean logo, String language) {
    ImageChoice chosen = new ImageChoice("", 0, 0);
    if (images == null) return chosen;
    double best = -1;
    for (int i = 0; i < Math.min(images.length(), 2000); i++) {
      JSONObject image = images.optJSONObject(i);
      if (image == null) continue;
      String path = image.optString("file_path", "");
      if (!IMAGE_PATH.matcher(path).matches()) continue;
      int width = image.optInt("width", 0), height = image.optInt("height", 0);
      String lang = image.isNull("iso_639_1") ? "" : image.optString("iso_639_1", "");
      double score;
      if (logo) {
        // PNG title logos preserve transparency; do not invent SVG->PNG derivatives.
        if (!path.endsWith(".png")
            || width < 200
            || height < 30
            || width < height * .3
            || width > height * 10) continue;
        int priority = lang.equals(language) ? 3 : lang.equals("en") ? 2 : lang.isEmpty() ? 1 : 0;
        if (priority == 0) continue;
        score =
            priority * 1_000_000d
                + Math.min(width, 1000) * 100d
                + image.optDouble("vote_average", 0);
      } else {
        if (!lang.isEmpty()
            || width < 1280
            || height < 720
            || width < height * 1.5
            || width > height * 2.5) continue;
        score =
            (width >= 1920 && width <= 3840 && height <= 2160 ? 1920 : 1280) * 100d
                + image.optDouble("vote_average", 0) * 10
                + Math.min(100, image.optInt("vote_count", 0));
      }
      if (score > best) {
        best = score;
        chosen = new ImageChoice(path, width, height);
      }
    }
    return chosen;
  }

  /**
   * Uses only widths advertised by /configuration; originals are selected separately by source
   * dimensions.
   */
  private static String size(JSONArray sizes, int preferred, int maximum) {
    if (sizes == null) return "";
    String chosen = "";
    int best = 0;
    for (int i = 0; i < sizes.length(); i++) {
      String size = sizes.optString(i, "");
      if (!size.matches("w[0-9]{2,4}")) continue;
      int width = Integer.parseInt(size.substring(1));
      if (width <= maximum && width > best) {
        best = width;
        chosen = size;
      }
    }
    return best >= preferred ? chosen : "";
  }

  private JSONObject get(String path, String token, CancellationSignal signal) throws Exception {
    check(signal);
    HttpURLConnection connection =
        (HttpURLConnection) new URL("https://api.themoviedb.org/3" + path).openConnection();
    connection.setConnectTimeout(4000);
    connection.setReadTimeout(5000);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Accept", "application/json");
    connection.setRequestProperty("Authorization", "Bearer " + token);
    if (signal != null) signal.setOnCancelListener(connection::disconnect);
    try {
      int status = connection.getResponseCode();
      if (status != 200) throw new ApiFailure(status);
      if (connection.getContentLengthLong() > JSON_LIMIT)
        throw new IOException("Response too large");
      try (InputStream input = connection.getInputStream()) {
        return new JSONObject(read(input, JSON_LIMIT, signal));
      }
    } finally {
      if (signal != null) signal.setOnCancelListener(null);
      connection.disconnect();
    }
  }

  private Result cached(String key) {
    synchronized (cacheLock) {
      File file = new File(directory, key + ".json");
      if (!file.isFile() || file.length() > 8192) return null;
      try (InputStream input = new FileInputStream(file)) {
        JSONObject value = new JSONObject(read(input, 8192, null));
        if (value.optLong("expires", 0) < System.currentTimeMillis()) {
          file.delete();
          return null;
        }
        String backdrop = value.optString("backdrop", ""), logo = value.optString("logo", "");
        if (!validCachedUrl(backdrop) || !validCachedUrl(logo)) {
          file.delete();
          return null;
        }
        return new Result(
            backdrop,
            logo,
            value.optBoolean("matched", false),
            value.optString("status", "Cached artwork"));
      } catch (Exception ignored) {
        file.delete();
        return null;
      }
    }
  }

  private void save(String key, Result result, long ttl, int ticket) {
    synchronized (cacheLock) {
      if (ticket != epoch) return;
      File temporary = null;
      try {
        directory.mkdirs();
        JSONObject value =
            new JSONObject()
                .put("expires", System.currentTimeMillis() + ttl)
                .put("backdrop", result.backdrop)
                .put("logo", result.logo)
                .put("matched", result.matched)
                .put("status", result.status);
        temporary = File.createTempFile("catalogue-", ".tmp", directory);
        try (FileOutputStream output = new FileOutputStream(temporary)) {
          output.write(value.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!temporary.renameTo(new File(directory, key + ".json"))) return;
        File[] files = directory.listFiles();
        if (files == null || files.length <= CACHE_LIMIT) return;
        Arrays.sort(files, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < files.length - CACHE_LIMIT; i++) files[i].delete();
      } catch (Exception ignored) {
        // Metadata caching is optional; never fail a playable app title because the cache is full.
      } finally {
        if (temporary != null) temporary.delete();
      }
    }
  }

  void clear() {
    synchronized (cacheLock) {
      epoch++;
      File[] files = directory.listFiles();
      if (files != null) for (File file : files) file.delete();
    }
    synchronized (this) {
      config = null;
      configTime = 0;
    }
    retryAfter = 0;
    retryStatus = "";
  }

  private static boolean validCachedUrl(String value) {
    return value.isEmpty()
        || value.matches(
            "https://image\\.tmdb\\.org/t/p/(?:w[0-9]{2,4}|original)/[A-Za-z0-9_-]+\\.(?:jpg|jpeg|png|webp)");
  }

  private static String encode(String value) throws Exception {
    return URLEncoder.encode(value, "UTF-8");
  }

  private static void check(CancellationSignal signal) {
    if (signal != null) signal.throwIfCanceled();
    if (Thread.currentThread().isInterrupted()) throw new OperationCanceledException();
  }

  private static String read(InputStream input, int limit, CancellationSignal signal)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int count;
    while ((count = input.read(buffer)) != -1) {
      check(signal);
      if (bytes.size() + count > limit) throw new IOException("Response too large");
      bytes.write(buffer, 0, count);
    }
    return bytes.toString(StandardCharsets.UTF_8.name());
  }

  private static String digest(String key) {
    try {
      StringBuilder result = new StringBuilder();
      for (byte b :
          MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8)))
        result.append(String.format(Locale.ROOT, "%02x", b & 255));
      return result.toString();
    } catch (Exception impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static final class MatchLimit extends IOException {}

  private static final class ApiFailure extends IOException {
    final int status;

    ApiFailure(int status) {
      super("Catalogue request failed");
      this.status = status;
    }
  }
}
