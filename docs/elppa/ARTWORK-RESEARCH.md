# Artwork and focus research

This research supports the owner's clean movie backdrop, separate title-logo and glass-focus
request. It does not establish a live catalogue test: a valid TMDB credential is still required.

## What can be supplied

TMDB's movie and TV-series image endpoints return separate backdrop, poster and logo arrays.
A launcher can therefore draw a landscape backdrop and independently fit a title logo without
stretching the logo, placing a textual title underneath only when the logo is unavailable.
Select a language-neutral backdrop where possible; select a suitable language for title logos.

- [Movie images](https://developer.themoviedb.org/reference/movie-images)
- [TV series images](https://developer.themoviedb.org/reference/tv-series-images)
- [Image languages](https://developer.themoviedb.org/docs/image-languages)
- [Image URL construction and sizes](https://developer.themoviedb.org/docs/image-basics)

Image-language metadata is a useful selector, not proof that every returned image contains no
text, badge or branding. Catalogue coverage varies. Some titles lack logos, sufficiently large
backdrops, or an unambiguous match. Do not promise this appearance for every recommendation.
A plain title and neutral background are honest fallbacks when clean catalogue art is unavailable.

The Android TV provider exposes poster/thumbnail URIs and aspect-ratio metadata. Its documentation
explicitly permits a system-generated thumbnail derived from the poster. It does not promise a
full-resolution studio backdrop, an independent transparent title logo, or unbranded artwork.
A badge embedded into an image's pixels cannot be removed by choosing another decode size.

- [TvContract.PreviewPrograms](https://developer.android.com/reference/android/media/tv/TvContract.PreviewPrograms)
- [TvContract.WatchNextPrograms](https://developer.android.com/reference/android/media/tv/TvContract.WatchNextPrograms)

## Authentication, matching and playback

TMDB supports application authentication through an API Read Access Token in an Authorization
Bearer header. Image-file URLs are constructed from returned paths and supported sizes; a file
URL being publicly retrievable does not provide credential-free catalogue search.

- [Application authentication](https://developer.themoviedb.org/docs/authentication-application)

Match title, media type and an explicit release year when available. Do not assume the first or
most popular search result is correct: remakes and identically named series/movies are common.
Prefer a conservative empty result to displaying the wrong movie logo. Provider-specific IDs
are not automatically TMDB IDs. Episodic content can use matched series artwork when its provider
title names the series; playback must keep the original installed-app deep link and progress.
Artwork matching does not establish streaming entitlement or change the recommendation ranking.

No access token should appear in Git, diagnostic output or URLs. Apply request timeouts,
cancellation, bounded metadata/image caches and a retry cooldown. A missing credential,
authorization failure or outage must leave Home responsive.

## Attribution

TMDB requires its approved mark and the notice below in an application's About/Credits section:

> This product uses the TMDB API but is not endorsed or certified by TMDB.

The approved mark is bundled as `tmdb_logo.png`, with its source recorded in
[ASSETS.md](../../launcher/elppa/ASSETS.md). The documented developer API is available for
non-commercial use with attribution; commercial use requires contacting TMDB.

- [FAQ and attribution requirements](https://developer.themoviedb.org/docs/faq)
- [Official logos](https://www.themoviedb.org/about/logos-attribution)

## Applying the Apple TV design reference to Android

Apple describes tvOS focus using elevation, illumination and animation, with enough room for a
focused item to expand and assets sharp enough at that larger size. It also describes subtle
layered parallax; that is an optional treatment for media images, not a reason to invent missing
foreground/background layers from a flat provider image.

- [Focus and selection](https://developer.apple.com/design/human-interface-guidelines/focus-and-selection/)
- [Images](https://developer.apple.com/design/human-interface-guidelines/images)
- [Layout](https://developer.apple.com/design/human-interface-guidelines/layout)

Apple's Liquid Glass guidance has device-specific support even within Apple TV hardware. On this
Android launcher, a bounded static frosting treatment and brief focus animation are appropriate
translations of the reference; they are not native Apple's Liquid Glass APIs. Keep motion
cancellable, respect animation settings, and avoid a perpetual shimmer or full-screen blur pass.

- [Materials](https://developer.apple.com/design/human-interface-guidelines/materials)
- [Adopting Liquid Glass](https://developer.apple.com/documentation/TechnologyOverviews/adopting-liquid-glass)

For this product, **16:9 movie cards** is an implementation choice consistent with landscape
streaming artwork. Larger app cards should fit full provider banners/logos without cropping.
Exact card size, glow intensity and legibility still require captures on the Mi Box, including
first/last cards, settings, enlarged text and rapid D-pad movement. Source review and successful
builds cannot establish the final visual appearance on the television.
