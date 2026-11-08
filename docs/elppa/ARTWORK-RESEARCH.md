> Archived research: external catalogue integration was removed in 0.5.0 at the owner’s request.

# Replacement catalogue research — 9 November 2026

The owner could not use TMDB's API setup page and requested OMDb or TVmaze instead.
Cloud access does not establish reachability through the owner's Indian ISP.

## Decision

OMDb is the appropriate catalogue type for Indian theatrical movies. Its documented
search accepts movie, series and episode types, title, year and IMDb ID. It documents
posters, not cinematic backdrops or transparent title logos. The dedicated high-resolution
Poster API (up to 2000×3000) is patrons-only. It cannot fulfill the earlier movie-art
requirement on its own. No authenticated OMDb title request was available in this workspace.

TVmaze explicitly excludes theatrical and direct-to-video releases. Its public API
supports TV-series backgrounds and typography images, making it useful as a separate
show-art source. It requires no API key. Typography does not guarantee transparency.

| Capability | OMDb | TVmaze |
| --- | --- | --- |
| Theatrical-movie metadata | Yes; completeness unverified | Excluded by inclusion policy |
| Show metadata | Yes | Yes |
| Cinematic backdrop endpoint | Not documented | Show gallery `background` type |
| Separate title artwork | Not documented | `typography`; alpha/quality must be checked |
| Credential | Personal API key | No key for public API |
| Public limit | Free key advertises 1,000 requests/day | At least 20 requests/10 seconds/IP; handle 429 |
| Data licence | CC BY-NC 4.0 | CC BY-SA 4.0 |

Sources: [OMDb](https://www.omdbapi.com/), [OMDb key registration](https://www.omdbapi.com/apikey.aspx),
[OMDb schema](https://www.omdbapi.com/swagger.json), [TVmaze API](https://www.tvmaze.com/api),
[TVmaze inclusion policy](https://www.tvmaze.com/faq/51/a-short-guide-for-new-users),
[image classifications](https://www.tvmaze.com/faq/14/show-extras).

## Live Indian-show samples

These are a small sample, not proof of complete regional coverage. API show metadata
and gallery responses were checked. The dimensions are source metadata, not evidence
of how the launcher renders on a TV.

| Show | Language | Gallery | Observed background | Separate typography |
| --- | --- | --- | --- | --- |
| Sacred Games | Hindi | [18197](https://api.tvmaze.com/shows/18197/images) |1920×1080 |None |
| Suzhal - The Vortex | Tamil | [62771](https://api.tvmaze.com/shows/62771/images) |1200×700; below chosen threshold |None |
| Dhootha | Telugu | [73104](https://api.tvmaze.com/shows/73104/images) |1920×1080 |None |
| Ekam | Kannada | [80979](https://api.tvmaze.com/shows/80979/images) |1920×1080 |Opaque JPEG; unsuitable overlay |
| Kerala Crime Files | Malayalam | [69583](https://api.tvmaze.com/shows/69583/images) |1920×1080 |None |
| Mirzapur | Hindi | [39868](https://api.tvmaze.com/shows/39868/images) |Includes 1280×720 |None |

A fuzzy TVmaze search for the movie Dangal returned unrelated shows. This supports
strict type and identity matching; a first search result is not evidence of a match.
OMDb without an API key returned an authentication error. No shared/example key was
used. No broad claim about its Indian movie completeness is made.

## UI correction grounded in the device photo

The visible thick cyan/pink frame came from the launcher's 6-unit banner inset plus
sampled corner-colour backplate, compounded by 152×92 card geometry around 16:9 banners.
Version 0.4.0 uses 160×90 app cards, no banner inset and no sampled-colour frame.
Full logos remain fitted; nonstandard banners may have neutral letterboxing.

The former multi-stroke glow is removed. Native dark shadows, a small lift and scale
communicate focus. Subtle radial corner reflections are present on resting app cards,
fade fully inside their bounds and do not form a perimeter. Actual falloff and focus
visibility still require a native Mi Box screenshot and D-pad check.
