# Clean catalogue artwork and title logos

The launcher can enrich an installed app's existing movie/series recommendations with
TMDB artwork. App-published records still determine the titles and playback links.
This artwork layer is not Google's recommendation engine, a streaming entitlement
lookup or a source of watch history.

## Why a separate source is necessary

A Netflix Top 10 badge can be part of the bitmap supplied through Android TV listings.
Scaling or blurring that bitmap cannot recover the clean original. The implementation
matches a title against TMDB and requests a separate backdrop plus a PNG movie/series
logo. The catalogue must actually contain them; some titles have no suitable asset.
A no-language backdrop is a useful metadata filter, not a guarantee that every image
is free of text, watermarks or badges. The implementation does not erase image content.

## Official sources and implementation

- [Movie images](https://developer.themoviedb.org/reference/movie-images) and
  [TV series images](https://developer.themoviedb.org/reference/tv-series-images)
  return poster, backdrop and logo records with language, dimensions, rating and path.
- [Image languages](https://developer.themoviedb.org/docs/image-languages) documents
  combining a requested language with English and language-neutral images.
- [Image basics](https://developer.themoviedb.org/docs/image-basics) and
  [configuration](https://developer.themoviedb.org/reference/configuration-details)
  supply the image host and available sizes. The resolver uses only advertised sizes.
  Hero assets normally use the widest bounded width (currently w1280); a 1920–3840 px
  source no taller than 2160 px may use the advertised original size so the loader can
  retain 1080p detail. The existing loader still caps response size and decoded pixels.
  PNG logos use an advertised bounded width; unsupported vector logo paths are skipped.
- [Application authentication](https://developer.themoviedb.org/docs/authentication-application)
  requires the user's API Read Access Token in the Authorization header. An empty token
  makes no network requests. The setup path validates through a fresh configuration
  request; tokens are not embedded in source, metadata caches, URLs or diagnostics.
- [Movie search](https://developer.themoviedb.org/reference/search-movie) and
  [TV search](https://developer.themoviedb.org/reference/search-tv) supply candidates.
  The resolver compares exact normalised returned/original titles and accepts only one
  identity. A trailing bracketed year can disambiguate. It never picks the most popular
  ambiguous result or transliterates a title to guess a match. Unknown provider types
  require a unique result across both movie and TV searches. Seasons/episodes fall back
  because the current MediaEntry does not contain a verified owning-series ID.

The resolver reads at most three pages per applicable search namespace. If the result
set is larger, it declines enrichment rather than claiming uniqueness from a partial
search. Search/configuration/image JSON responses are capped at 2 MiB; requests use
4 s connect and 5 s read timeouts, HTTPS and no redirects. Cancellation disconnects the
active request. The caller owns a bounded worker and must cancel on replacement/pause.

Resolved metadata is cached for seven days; missing/ambiguous results for one day.
The cache has at most 256 small records, each read capped at 8 KiB. HTTP authentication
and rate-limit failures use a brief cooldown and are never saved as permanent misses.
Clear removes metadata and configuration, and prevents an older in-flight result from
writing back after the clear. Cache files never contain the read token or search title.

## Attribution and coverage

[TMDB's FAQ](https://developer.themoviedb.org/docs/faq) requires an approved TMDB logo
and the following notice in an About/Credits section:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

Use the [approved logos](https://www.themoviedb.org/about/logos-attribution) without
redrawing them. The developer API permits noncommercial use with attribution; commercial
use has separate terms. The UI must explain that enabling catalogue artwork sends the
selected movie/show title to TMDB.

A successful match can provide a backdrop with no logo, or a logo with no acceptable
backdrop. Keep each layer's fallback independent. Without credentials, catalogue coverage,
an unambiguous title identity or a suitable asset, do not claim clean HD artwork or logos
for every recommendation. The current API has not been live-tested with a real read token
in this workspace. Unit tests cover the conservative identity matching policy; authenticated
movie/TV/image responses and device rendering remain integration checks.
