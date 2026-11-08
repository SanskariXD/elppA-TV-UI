# Artwork sources — 0.4.0

The owner requested replacing TMDB with OMDb/TVmaze. This version removes TMDB calls
and setup. It keeps app-published recommendations, Continue Watching and playback links.
It does not add a new recommendation ranking engine or infer subscriptions.

## What each service does

| Source | Used for | Limits |
| --- | --- | --- |
| Installed streaming app | Recommendation titles, history, playback URI and fallback image | Images can be small or contain baked-in branding/Top10 badges |
| OMDb | Exact movie identity and optional year, language and IMDb score | Personal API key; no cinematic backdrop or separate title-logo endpoint |
| TVmaze | Known TV-series identity, original background images and suitable typography | Public API; excludes theatrical movies; artwork/transparent logos are incomplete |

OMDb is the suitable choice of the two for Indian theatrical-movie metadata. Neither
service guarantees every Indian film/show or title alias. Hindi, Tamil, Telugu,
Kannada and Malayalam show samples were found in TVmaze; this is not a complete
coverage study. See [ARTWORK-RESEARCH.md](ARTWORK-RESEARCH.md).

## Setup

- TVmaze show artwork is on by default and needs no key. Disable **TV show artwork**
  under Settings → Movie artwork to stop show lookups.
- For movie metadata, get a personal key at https://www.omdbapi.com/apikey.aspx.
  Activate it through OMDb's email, then enter it privately under **Connect OMDb**.
  The free key advertises 1,000 requests/day; artwork selection does not bypass that.
- API keys are validated online, encrypted using a non-exportable Android Keystore
  key, and never logged. A separate `omdb_credentials` store and `elppa_omdb` alias
  prevent an old TMDB token from being sent to a replacement service.
- **Use app backgrounds** defaults to on and honours a previously explicit choice.
  Turning it off produces a neutral background if no suitable catalogue image exists.
  Leaving it on can retain Netflix/provider badges. OMDb cannot remove those badges
  or replace movie backgrounds with equivalent artwork.

## Identity and rendering

Exact normalized title/type matching uses an explicit year only when the provider
includes it. Ambiguous remakes fail closed. Unknown types require OMDb identity before
TVmaze show lookup; episodes and seasons are skipped without an owning-series identity.
No fuzzy first-result selection, poster stretching, invented title logo or guessed
high-resolution CDN URL is used.

TVmaze `background` images must be at least 1280×720, landscape, and inside bounded
source dimensions. `typography` candidates must be PNGs; decoded pixels must contain
both real transparency and visible content before replacing the plain title. The
artwork can be copyrighted or contain words even when classified as a background.
A missing or rejected logo keeps readable title text.

Catalogue image download/decode failure falls back to app images only when allowed.
A larger app thumbnail never takes priority over a successfully loaded catalogue
background. Credits include source links and licences. The matched TVmaze show page
can be opened from Artwork and data credits.

## Work bounds and failure behavior

- 180 ms focus debounce; one catalogue worker; cancellation on selection change/hidden Home.
- HTTPS only, fixed API hosts, bounded redirects for TVmaze identity lookup; no OMDb key
  in image URLs, disk cache files, logs or user-facing errors.
- 2 MiB JSON response bound, 4-second connection and 5-second read timeouts.
- Bounded search pagination; incomplete/ambiguous results do not become matches.
- 256 metadata entries; successful metadata/artwork cached 7 days, absent matches 1 day.
- Rate/auth errors produce safe messages and source-specific cooldowns.
- Image pipeline remains bounded: two workers, 24 queued requests, 16 MiB decoded and 48 MiB
  disk cache, at most 1920×1080 hero decode. No new app dependency or background service.

The workspace has no OMDb key or connected Mi Box. Public TVmaze responses were
checked; Android runtime/API-key persistence, final native appearance and physical
remote behavior remain device acceptance checks. Source tests do not establish those.

## Attribution

OMDb: https://www.omdbapi.com/ — CC BY-NC 4.0.
TVmaze: https://www.tvmaze.com/api — CC BY-SA 4.0.
Artwork retains its owners' rights; images are fitted for display and not bundled.
