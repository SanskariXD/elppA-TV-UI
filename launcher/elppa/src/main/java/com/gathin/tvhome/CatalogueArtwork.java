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
import org.json.JSONArray;
import org.json.JSONObject;

/** OMDb metadata and TVmaze show images. Call on a bounded worker, never the UI thread. */
final class CatalogueArtwork {
  static final String NOTICE =
      "Movie metadata: OMDb (CC BY-NC 4.0). Show data and artwork: TVmaze (CC BY-SA 4.0).";
  private static final String OMDB = "https://www.omdbapi.com/";
  private static final String TVMAZE = "https://api.tvmaze.com";
  private static final long DAY = 86_400_000L;
  private static final int JSON_LIMIT = 2 * 1024 * 1024, CACHE_LIMIT = 256, SEARCH_PAGES = 3;
  private final File directory;
  private final Object cacheLock = new Object();
  private volatile int epoch;
  private volatile long omdbRetryAfter, tvmazeRetryAfter;
  private volatile int omdbRetryCode;
  private volatile String omdbRetryStatus = "", tvmazeRetryStatus = "";

  static final class Result {
    final String backdrop, logo, attribution, status, detail, sourceUrl;
    final boolean matched;

    Result(String backdrop, String logo, boolean matched, String status) {
      this(backdrop, logo, matched, status, "", "", "");
    }

    Result(
        String backdrop,
        String logo,
        boolean matched,
        String status,
        String detail,
        String sourceUrl,
        String attribution) {
      this.backdrop = backdrop;
      this.logo = logo;
      this.matched = matched;
      this.status = status;
      this.detail = detail;
      this.sourceUrl = sourceUrl;
      this.attribution = attribution;
    }

    static Result empty(String status) {
      return new Result("", "", false, status);
    }

    Result status(String replacement) {
      return new Result(backdrop, logo, matched, replacement, detail, sourceUrl, attribution);
    }
  }

  CatalogueArtwork(Context context) {
    directory = new File(context.getCacheDir(), "catalogue_artwork_v2");
    directory.mkdirs();
  }

  Result resolve(MediaEntry entry, String apiKey, CancellationSignal signal) {
    return resolve(entry, apiKey, true, signal);
  }

  Result resolve(
      MediaEntry entry, String apiKey, boolean allowShowArtwork, CancellationSignal signal) {
    check(signal);
    if (entry == null || "demo".equals(entry.source)) return Result.empty("Sample artwork");
    // The provider has supplied an episode/season title, not an owning series identity.
    if (entry.contentType == 2 || entry.contentType == 3 || entry.contentType > 3)
      return Result.empty("Series identity unavailable for this episode or season");
    if (entry.contentType == 1 && !allowShowArtwork)
      return Result.empty("TVmaze show artwork is turned off");
    String key = apiKey == null ? "" : apiKey.trim();
    boolean needsOmdb = entry.contentType != 1;
    if (needsOmdb && key.isEmpty())
      return Result.empty("Add your OMDb API key for movie metadata and unknown title matching");
    if (needsOmdb && !CataloguePolicy.validKey(key))
      return Result.empty("Check the OMDb API key in artwork settings");
    CatalogueMatch.Query query = new CatalogueMatch.Query(entry.title);
    if (query.title.isEmpty() || query.title.length() > 250)
      return Result.empty("Title is unavailable for matching");
    int ticket = epoch;
    String cacheKey =
        digest("v2|" + entry.contentType + "|" + entry.title + "|" + allowShowArtwork);
    Result cached = cached(cacheKey);
    if (cached != null) return cached;
    Result metadata = null;
    try {
      Result result;
      if (entry.contentType == 1) {
        JSONObject show = findShow(query, signal);
        result =
            show == null
                ? Result.empty("No unambiguous TVmaze show match")
                : showArtwork(show, "", signal);
      } else {
        CataloguePolicy.Title title = findOmdbTitle(query, entry.contentType, key, signal);
        if (title == null) {
          result = Result.empty("No unambiguous OMDb title match");
        } else {
          JSONObject details = omdb("i=" + title.id + "&plot=short", key, signal);
          CataloguePolicy.Title confirmed = omdbTitle(details);
          // Search and details must agree. A provider title is never replaced by a fuzzy hit.
          if (confirmed == null
              || !title.id.equals(confirmed.id)
              || !title.kind.equals(confirmed.kind)
              || !CataloguePolicy.matches(query, confirmed)) {
            result = Result.empty("Catalogue identity could not be confirmed");
          } else {
            String detail =
                CataloguePolicy.detail(
                    details.optString("Year", ""),
                    details.optString("Language", ""),
                    details.optString("imdbRating", ""));
            metadata =
                new Result(
                    "",
                    "",
                    true,
                    "OMDb metadata available; OMDb does not provide backdrops or title logos",
                    detail,
                    OMDB,
                    "OMDb");
            if ("series".equals(title.kind) && allowShowArtwork) {
              JSONObject show = lookupShow(title.id, signal);
              if (show != null) {
                JSONObject external = show.optJSONObject("externals");
                if (external == null || !title.id.equals(external.optString("imdb", "")))
                  throw new IOException("Show identity mismatch");
                result = showArtwork(show, detail, signal);
              } else {
                result =
                    metadata.status(
                        "OMDb metadata available; no TVmaze show with this IMDb identity");
              }
            } else {
              result = metadata;
            }
          }
        }
      }
      check(signal);
      save(cacheKey, result, result.matched ? 7 * DAY : DAY, ticket);
      return result;
    } catch (OperationCanceledException canceled) {
      throw canceled;
    } catch (MatchLimit ambiguous) {
      Result absent =
          Result.empty("Too many possible catalogue titles; add an explicit title year");
      save(cacheKey, absent, DAY, ticket);
      return absent;
    } catch (ApiFailure failure) {
      check(signal);
      String status = failureMessage(failure);
      if (metadata == null) return Result.empty(status);
      Result partial = metadata.status(status);
      // Preserve already verified OMDb data briefly when TVmaze is down, without spending the
      // owner's daily OMDb quota again on each automatic hero rotation.
      save(cacheKey, partial, 5 * 60_000L, ticket);
      return partial;
    } catch (Exception failure) {
      check(signal);
      // Do not echo exceptions/URLs: OMDb puts the owner's private key in the query string.
      String status = "Catalogue temporarily unavailable";
      if (metadata == null) return Result.empty(status);
      Result partial = metadata.status(status);
      save(cacheKey, partial, 5 * 60_000L, ticket);
      return partial;
    }
  }

  private CataloguePolicy.Title findOmdbTitle(
      CatalogueMatch.Query query, int contentType, String key, CancellationSignal signal)
      throws Exception {
    List<CataloguePolicy.Title> titles = new ArrayList<>();
    searchOmdb(query, "movie", key, signal, titles);
    if (contentType < 0) searchOmdb(query, "series", key, signal, titles);
    return CataloguePolicy.unique(query, titles);
  }

  private void searchOmdb(
      CatalogueMatch.Query query,
      String kind,
      String key,
      CancellationSignal signal,
      List<CataloguePolicy.Title> titles)
      throws Exception {
    int total = -1;
    int pages = 1;
    for (int page = 1; page <= pages; page++) {
      JSONObject response =
          omdb(
              "s="
                  + encode(query.title)
                  + "&type="
                  + kind
                  + (query.year == 0 ? "" : "&y=" + query.year)
                  + "&page="
                  + page,
              key,
              signal);
      if (!response.optBoolean("Response", false)) {
        if (page == 1) return;
        throw new IOException("Incomplete search");
      }
      String count = response.optString("totalResults", "");
      if (!count.matches("[0-9]{1,7}")) throw new IOException("Incomplete search count");
      int currentTotal = Integer.parseInt(count);
      if (total != -1 && currentTotal != total)
        throw new IOException("Search changed during paging");
      total = currentTotal;
      pages = (total + 9) / 10;
      // If any unexamined page might contain a remake, do not choose a popular first-page hit.
      if (pages > SEARCH_PAGES) throw new MatchLimit();
      JSONArray found = response.optJSONArray("Search");
      if (found == null || found.length() != Math.min(10, total - (page - 1) * 10))
        throw new IOException("Incomplete search page");
      for (int i = 0; i < found.length(); i++) {
        CataloguePolicy.Title candidate = omdbTitle(found.optJSONObject(i));
        if (candidate != null && kind.equals(candidate.kind)) titles.add(candidate);
      }
    }
  }

  private static CataloguePolicy.Title omdbTitle(JSONObject value) {
    if (value == null) return null;
    String id = value.optString("imdbID", "");
    String kind = value.optString("Type", "");
    if (!CataloguePolicy.validImdbId(id) || (!"movie".equals(kind) && !"series".equals(kind)))
      return null;
    return new CataloguePolicy.Title(
        id, kind, value.optString("Title", ""), CataloguePolicy.year(value.optString("Year", "")));
  }

  private JSONObject findShow(CatalogueMatch.Query query, CancellationSignal signal)
      throws Exception {
    JSONArray found =
        new JSONArray(tvmaze("/search/shows?q=" + encode(query.title), false, signal));
    // Public show search is relevance ordered and has no paging contract. At its usual result cap,
    // an unseen exact-name remake is possible, so the caller must supply a less ambiguous title.
    if (found.length() >= 10) throw new MatchLimit();
    List<CataloguePolicy.Title> titles = new ArrayList<>();
    List<JSONObject> shows = new ArrayList<>();
    for (int i = 0; i < found.length(); i++) {
      JSONObject hit = found.optJSONObject(i);
      JSONObject show = hit == null ? null : hit.optJSONObject("show");
      if (show == null || show.optLong("id", 0) <= 0) continue;
      titles.add(
          new CataloguePolicy.Title(
              Long.toString(show.optLong("id")),
              "series",
              show.optString("name", ""),
              CataloguePolicy.year(show.optString("premiered", ""))));
      shows.add(show);
    }
    CataloguePolicy.Title match = CataloguePolicy.unique(query, titles);
    if (match == null) return null;
    for (JSONObject show : shows)
      if (match.id.equals(Long.toString(show.optLong("id")))) return show;
    return null;
  }

  private JSONObject lookupShow(String imdbId, CancellationSignal signal) throws Exception {
    try {
      return new JSONObject(tvmaze("/lookup/shows?imdb=" + imdbId, true, signal));
    } catch (ApiFailure failure) {
      if (failure.code == 404) return null;
      throw failure;
    }
  }

  private Result showArtwork(JSONObject show, String detail, CancellationSignal signal)
      throws Exception {
    long id = show.optLong("id", 0);
    if (id <= 0) throw new IOException("Missing show identity");
    JSONArray images = new JSONArray(tvmaze("/shows/" + id + "/images", false, signal));
    if (images.length() > 2000) throw new IOException("Image gallery too large");
    List<CataloguePolicy.Image> choices = new ArrayList<>();
    for (int i = 0; i < images.length(); i++) {
      JSONObject image = images.optJSONObject(i);
      JSONObject resolutions = image == null ? null : image.optJSONObject("resolutions");
      JSONObject original = resolutions == null ? null : resolutions.optJSONObject("original");
      if (original == null) continue;
      choices.add(
          new CataloguePolicy.Image(
              image.optString("type", ""),
              original.optString("url", ""),
              original.optInt("width", 0),
              original.optInt("height", 0)));
    }
    String backdrop = CataloguePolicy.chooseImage(choices, false);
    String logo = CataloguePolicy.chooseImage(choices, true);
    String status =
        backdrop.isEmpty()
            ? "Matched TVmaze show; suitable wide backdrop unavailable"
            : logo.isEmpty()
                ? "TVmaze backdrop available; title logo unavailable"
                : "TVmaze backdrop and PNG title-logo candidate available";
    return new Result(
        backdrop,
        logo,
        true,
        status,
        detail,
        "https://www.tvmaze.com/shows/" + id,
        detail.isEmpty() ? "TVmaze" : "OMDb + TVmaze");
  }

  /**
   * Empty string is success. Always validates this key online, without a metadata-cache shortcut.
   */
  String validateToken(String apiKey, CancellationSignal signal) {
    String key = apiKey == null ? "" : apiKey.trim();
    if (!CataloguePolicy.validKey(key)) return "Enter your activated OMDb API key.";
    try {
      // Stable known ID is only an authentication probe, never a fallback recommendation.
      JSONObject response = omdbRequest("i=tt0111161&plot=short", key, signal);
      if (!"tt0111161".equals(response.optString("imdbID", ""))
          || !"movie".equals(response.optString("Type", ""))
          || response.optString("Title", "").isEmpty())
        return "OMDb returned an incomplete response. Try again later.";
      omdbRetryAfter = 0;
      omdbRetryStatus = "";
      return "";
    } catch (OperationCanceledException canceled) {
      throw canceled;
    } catch (ApiFailure failure) {
      check(signal);
      if (failure.code == 401 || failure.code == 403)
        return "OMDb rejected this key. Check it and complete the activation email.";
      if (failure.code == 429) {
        failureMessage(failure);
        return "OMDb request limit reached. Try again after your allowance resets.";
      }
      return "OMDb is unavailable. Try again later.";
    } catch (Exception failure) {
      check(signal);
      return "Could not contact OMDb. Check the connection and try again.";
    }
  }

  private JSONObject omdb(String query, String key, CancellationSignal signal) throws Exception {
    if (System.currentTimeMillis() < omdbRetryAfter)
      throw new ApiFailure("OMDb", omdbRetryCode, true);
    return omdbRequest(query, key, signal);
  }

  private JSONObject omdbRequest(String query, String key, CancellationSignal signal)
      throws Exception {
    JSONObject response =
        new JSONObject(
            get(OMDB + "?" + query + "&r=json&apikey=" + encode(key), "OMDb", false, signal));
    if (!response.optBoolean("Response", false)) {
      int code = CataloguePolicy.omdbError(response.optString("Error", ""));
      if (code == 413) throw new MatchLimit();
      if (code != 404) throw new ApiFailure("OMDb", code);
    }
    return response;
  }

  private String tvmaze(String path, boolean lookupRedirect, CancellationSignal signal)
      throws Exception {
    if (System.currentTimeMillis() < tvmazeRetryAfter) throw new ApiFailure("TVmaze", 429, true);
    return get(TVMAZE + path, "TVmaze", lookupRedirect, signal);
  }

  private String failureMessage(ApiFailure failure) {
    boolean omdb = "OMDb".equals(failure.service);
    // A local suppression must not extend the original deadline on every hero rotation.
    if (failure.suppressed) return omdb ? omdbRetryStatus : tvmazeRetryStatus;
    if (omdb && (failure.code == 401 || failure.code == 403)) {
      String status = "OMDb rejected the API key; check artwork settings";
      omdbRetryCode = failure.code;
      omdbRetryStatus = status;
      omdbRetryAfter = System.currentTimeMillis() + 60_000;
      return status;
    }
    if (failure.code == 429) {
      String status = failure.service + " request limit reached; try again later";
      if (omdb) {
        omdbRetryCode = failure.code;
        omdbRetryStatus = status;
        omdbRetryAfter = System.currentTimeMillis() + 15 * 60_000L;
      } else {
        tvmazeRetryStatus = status;
        tvmazeRetryAfter = System.currentTimeMillis() + 60_000;
      }
      return status;
    }
    return failure.service + " temporarily unavailable";
  }

  private String get(
      String address, String service, boolean lookupRedirect, CancellationSignal signal)
      throws Exception {
    check(signal);
    URL url = new URL(address);
    String host = "OMDb".equals(service) ? "www.omdbapi.com" : "api.tvmaze.com";
    if (!"https".equals(url.getProtocol())
        || !host.equals(url.getHost())
        || url.getPort() != -1
        || url.getUserInfo() != null) throw new IOException("Unsupported catalogue endpoint");
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setConnectTimeout(4000);
    connection.setReadTimeout(5000);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestProperty("Accept", "application/json");
    if (signal != null) signal.setOnCancelListener(connection::disconnect);
    String redirect = null;
    try {
      check(signal);
      int status = connection.getResponseCode();
      // This endpoint officially returns 301. Permit exactly one same-host numeric show URL.
      if (status == 301 && lookupRedirect && "TVmaze".equals(service)) {
        URL next = new URL(url, connection.getHeaderField("Location"));
        if (!next.toString().matches("https://api\\.tvmaze\\.com/shows/[0-9]+"))
          throw new IOException("Unsupported catalogue redirect");
        redirect = next.toString();
      } else {
        if (status != 200) {
          int code = status;
          // OMDb also emits JSON errors with non-200 statuses. A quota error is not an invalid key.
          if ("OMDb".equals(service) && connection.getContentLengthLong() <= JSON_LIMIT) {
            try (InputStream error = connection.getErrorStream()) {
              if (error != null) {
                JSONObject response = new JSONObject(read(error, JSON_LIMIT, signal));
                int classified = CataloguePolicy.omdbError(response.optString("Error", ""));
                if (classified == 401 || classified == 429) code = classified;
              }
            } catch (OperationCanceledException canceled) {
              throw canceled;
            } catch (Exception ignored) {
              check(signal);
            }
          }
          throw new ApiFailure(service, code);
        }
        if (connection.getContentLengthLong() > JSON_LIMIT)
          throw new IOException("Response too large");
        try (InputStream input = connection.getInputStream()) {
          return read(input, JSON_LIMIT, signal);
        }
      }
    } finally {
      if (signal != null) signal.setOnCancelListener(null);
      connection.disconnect();
    }
    return get(redirect, service, false, signal);
  }

  private Result cached(String key) {
    synchronized (cacheLock) {
      File file = new File(directory, key + ".json");
      if (!file.isFile() || file.length() > 8192) return null;
      try (InputStream input = new FileInputStream(file)) {
        JSONObject value = new JSONObject(read(input, 8192, null));
        if (value.optInt("version", 0) != 2
            || value.optLong("expires", 0) < System.currentTimeMillis()) {
          file.delete();
          return null;
        }
        String backdrop = value.optString("backdrop", ""), logo = value.optString("logo", "");
        if ((!backdrop.isEmpty() && !CataloguePolicy.validImageUrl(backdrop))
            || (!logo.isEmpty()
                && (!CataloguePolicy.validImageUrl(logo) || !logo.endsWith(".png")))) {
          file.delete();
          return null;
        }
        String source = value.optString("sourceUrl", "");
        if (!source.isEmpty()
            && !OMDB.equals(source)
            && !source.matches("https://www\\.tvmaze\\.com/shows/[0-9]+")) return null;
        return new Result(
            backdrop,
            logo,
            value.optBoolean("matched", false),
            value.optString("status", "Cached catalogue result"),
            value.optString("detail", ""),
            source,
            value.optString("attribution", ""));
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
                .put("version", 2)
                .put("expires", System.currentTimeMillis() + ttl)
                .put("backdrop", result.backdrop)
                .put("logo", result.logo)
                .put("matched", result.matched)
                .put("status", result.status)
                .put("detail", result.detail)
                .put("sourceUrl", result.sourceUrl)
                .put("attribution", result.attribution);
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
        // Cache failure must not affect the installed app's original playback link.
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
    omdbRetryAfter = 0;
    tvmazeRetryAfter = 0;
    omdbRetryStatus = "";
    tvmazeRetryStatus = "";
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
    final String service;
    final int code;
    final boolean suppressed;

    ApiFailure(String service, int code) {
      this(service, code, false);
    }

    ApiFailure(String service, int code, boolean suppressed) {
      super("Catalogue request failed");
      this.service = service;
      this.code = code;
      this.suppressed = suppressed;
    }
  }
}
