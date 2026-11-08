# Phase 1 verification — 8 November 2026

This is a build/source verification report. It is not native visual or Sony device acceptance.

## Results

| Check | Result | Scope |
|---|---|---|
| Both modules: release unit tests, lint and assembly, forced rerun | Passed | Source on main after PR #1 merge |
| Extra content-selection regression tests | Passed; 11 elppA tests, 315 reference tests, no failures/errors | Feed caps, newest Watch Next order, duplicate links, clips, missing artwork, excluded sources |
| Full Python regression suite | Passed on merged implementation commit `6f3e332a`; 3,214 tests, 74 skips | This ran before the owner's later README-only cleanup (`fab41167`). Legacy README assertions are not certified against the shortened README. The 5 elppA setup tests were rerun after synchronising that cleanup and passed. |
| Packaged manifest inspection | Passed | Correct elppA package, API 26 minimum / 34 target, exported Leanback/Home activity, existing market-launch handler |
| Settings/input/Assistant routing | Source checked | Standard platform intents, vendor settings helper and unavailable-action handling exist; real handler behaviour is unverified |
| Physical mic, Google Home phone remote, Sony picture/sound/input/standby/reboot | Not tested | Requires the actual Sony X75 |
| D-pad interactions, rendered layout, long titles and font scale 1.3 | Not accepted | No valid native capture; software TV emulators did not become usable, including an API 28 attempt with guest process failures |
| Global top-rated / personalised featured feed | Missing requirement | No catalogue ratings, vote counts or taste model exist in this version |

## What currently selects the cinematic background

1. Query accessible Android TV preview-program rows published by enabled, launchable installed apps.
2. Keep usable movie/series/season/episode entries (or unknown-type fallback entries) with artwork and a source-app link. Respect source exclusions and local dismissals; deduplicate by source/link, cap at 30.
3. Preserve the provider's query order. **There is no ratings or personal-relevance sort.**
4. If no preview entries survive, use filtered Watch Next entries instead. These are sorted by recent engagement, capped at 40; completed and stale entries are removed.
5. Rotate every 15 seconds only while Home is active, has window focus and the app dock holds focus. Next pick changes it manually; focusing a Watch Next card also displays that title.
6. Prefer the provider's landscape thumbnail/backdrop, falling back to poster artwork. The image is a still, centre-cropped behind scrims. Replacement images crossfade for 250 ms when animations are enabled.

There is no fixed real-movie list. Which titles appear depends on rows exposed by installed apps on the TV. Preview mode is opt-in and contains explicitly labelled fictional titles with NASA photos; it is not a movie catalogue or actual viewing history.

## Required featured-feed correction

The owner requires highly rated movies and relevant recommendations in the hero. This is a Phase 1 acceptance gap, not a completed feature.

- Add an authenticated movie catalogue such as TMDB; its discover API supports rating, vote-count, language, region and streaming-provider filters. Read authentication, image configuration and attribution requirements before implementation.
- Use a minimum vote threshold with ratings to avoid treating a title with only a few votes as a top-rated film. Mix established highly rated titles with recent well-rated/trending titles to avoid a permanently static all-time list.
- Provide an explicit preferences-based recommendation mode (languages/genres), and accurately label its basis. Truly personalised recommendations require chosen seeds or explicit local feedback; app previews alone do not establish a preference model.
- Filter regional availability separately from quality. Installing an OTT app does not prove a subscription or entitlement, and catalogue availability does not guarantee a valid native playback link.
- Use catalogue results for featured artwork; retain app-published Watch Next as the source of Continue Watching. Link to a title only when a usable source-app link is known; otherwise offer an honest find/details action.
- Cache the last successful catalogue response, handle offline/auth failures, bound requests and avoid refreshes while Home is hidden. Never embed a shared private API token in source.

TMDB requires an API key/read-access token. No credential is configured, and no authenticated live catalogue request or ranking is claimed here.

Primary references: [Discover movies](https://developer.themoviedb.org/reference/discover-movie), [Movie recommendations](https://developer.themoviedb.org/reference/movie-recommendations), [Authentication](https://developer.themoviedb.org/docs/authentication-application), [Image configuration](https://developer.themoviedb.org/docs/image-basics).

## Next native acceptance session

Install as an ordinary app using SETUP.md first. Capture the complete Home at 1080p and 720p, empty/permission/error states, long titles and font scale 1.3. Test every D-pad edge, held Select, dialogs, app hiding/reordering, source exclusion, card dismissal, title launch fallback and returning Home. Then check the real mic, phone remote, Sony settings/input, volume, standby and reboot before accepting a Home replacement.


## 0.1.1 — Mi Box preparation and emulator feedback

The user supplied a native emulator screenshot of 0.1.0 showing a large gap between
single-line title and subtitle, padded app artwork and a duplicate Settings dock tile.
0.1.1 bottom-aligns the hero title, groups metadata/actions, widens the main action,
enlarges app cards, fills banner artwork, strengthens caption scrims and omits stock
Settings from the dock unless pinned. Settings remains reachable; no packages are disabled.
Preview remains explicit sample content. The catalogue-ranked hero feed is still unimplemented.

Validation: both modules' release unit-test tasks, lint and APK builds passed;
315 upstream + 11 elppA tests; 5 setup tests passed. Lint: zero errors,
115 elppA warnings and 96 upstream warnings. Upstream APK remains 587,321 bytes,
below its existing 589,824-byte gate; elppA APK is 964,541 bytes.
The full inherited Python suite was not rerun for this UI/docs pass.

No device is connected in this workspace. A fresh 0.1.1 native capture, enlarged-text
review and physical remote/phone/standby checks remain pending. Xiaomi Mi Box is now
the first hardware test target; exact model/API/firmware must be probed, not assumed.
Mi Box testing does not certify Sony settings/inputs or remote compatibility.
Use [MIBOX-TEST.md](MIBOX-TEST.md) and [CODEX-CLI.md](CODEX-CLI.md).


## 0.2.0 — reference UI and artwork quality

Implemented from the owner's current Mi Box photo and second cinematic reference:
Wi-Fi/Settings only on Home; single accessible play icon; no Next pick/text action;
no app labels; movie labels below the focused card; quieter rounded cards with a
thin tinted edge, static soft light falloff and 180 ms focus lift. The shelf heading
is removed. Settings and launcher-owned menus use a grouped, scrollable side panel.
Inputs is still accessible in Settings. Source selection retains Save/Cancel.

Artwork now compares both supplied candidates by landscape suitability/detail;
source and decoded dimensions appear in Content diagnostics. Decode sampling avoids
premature undersizing, then filters down to at most 1920×1080. No provider URL guessing,
upscaling or new catalogue credentials are used. Low-resolution provider art remains
a source limitation. The top-rated/personal recommendation feed is still outstanding.

Automated checks: 315 upstream + 16 elppA release tests passed (5 new artwork policy
regressions); both release builds and lint passed with zero errors. Lint warnings:
116 elppA and 96 upstream. No setup script changed; full Python suite not rerun.

Native acceptance is pending: the workspace has no connected Mi Box, and its earlier
software emulator attempts could not produce a usable session. The owner's supplied
photo confirms the preceding app ran and displayed provider rows; it does not verify
0.2.0. No visual/performance or physical-integration pass is claimed here.

Next device checks: capture Home with app focus, movie focus (caption below), settings,
source switches and large text; inspect top and bottom safe areas, rapid D-pad movement,
Back, physical voice and phone remote. Check diagnostics on a blurry title to distinguish
small supplied artwork from a decode problem. Keep captures and identifiers out of Git.


## 0.3.0 — catalogue logos, clean backdrops and glass focus

User requested parallel research/implementation/review. Three agents covered catalogue
matching, card/material rendering and independent research/integration review. Root
integrated optional encrypted credentials, title logos, connection UI and cached frost.

- App cards 152×92 use FIT_CENTER without cropping complete logos. Movie cards 176×99
  are exact16:9 with focused captions below. No crisp white focus perimeter remains;
  diffuse light, static gloss and a 200 ms focus lift replace it.
- The play control uses a real64×32 blurred backdrop sample generated on an image
  worker, not full-screen/per-frame blur. Reduced motion and transparency are supported.
- Optional TMDB enrichment uses exact conservative title/type/year matching, neutral
  language high-resolution backdrop selection and original PNG title logos. Baked-in
  provider Top10 badges are bypassed by choosing another source; pixels are not erased.
- Original playback links/provider history remain authoritative. Missing/ambiguous
  matching or assets default to neutral hero/title after connection; provider fallback
  is optional. Without a token, app artwork continues to appear.
- The owner enters their Read Access Token privately in Settings → Movie artwork.
  It is verified online before encrypted Android Keystore storage. No token is bundled,
  committed, logged or available in this test workspace. Credits include the approved mark.
- Review fixed current-title artwork cache refresh and cancelled connection-dialog cleanup.

Release verification: 315 upstream  + 26 elppA unit tests passed (10 new conservative
matching cases), builds and lint passed with zero errors. Lint warnings: 116 elppA,
96 upstream. No setup scripts changed; full inherited Python suite not rerun.

Limits: authenticated live TMDB responses, Android Keystore on this Mi Box, keyboard
entry and native screenshots remain device acceptance work. No successful live feed,
logo coverage for every title, measured FPS, or final visual match is claimed. Prior
software emulators were unusable and no TV is connected to this workspace. Test on
Mi Box before considering Sony compatible. See CATALOGUE-ARTWORK.md and
ARTWORK-RESEARCH.md for primary sources and setup.

## 0.4.0 — device-photo frame correction and provider replacement

The owner's close-up exposed a thick coloured frame around app banners. Root and card
review identified the six-unit inset, mismatched card aspect and sampled-colour backplate.
These are removed: app cards are 160×90 (16:9), with no banner inset, cropped logo or
synthetic brand-colour frame. Nonstandard assets are neutrally fitted; square icons
retain padding. FocusGlow is deleted. Native dark elevation shadows, lift and scale
replace perimeter glow; restrained corner reflections appear on resting app cards.
Wi-Fi/Settings focus also loses its white stroke.

TMDB is replaced with OMDb movie metadata and TVmaze show artwork. The new credential
store cannot send legacy TMDB tokens to either service. OMDb cannot supply cinematic
movie backgrounds or title logos. TVmaze can supply some show backgrounds, but actual
logo transparency is checked and missing logos use text. App-background fallback is
on by default unless explicitly disabled, and may still include provider badges.

Independent review fixed preferred-image download fallback, content-type refresh,
same-title metadata freshness, sliding rate cooldowns, partial-metadata caching and
safe API error classification. Original OTT playback links/history are unchanged.

Verification: both modules' release unit tests, lint and APK assembly passed.
315 upstream + 39 elppA = 354 tests, zero failures/errors. The 13 new policy tests cover
Indian-script exact matching, remakes, media types, artwork dimensions, opaque logos,
URL validation and API errors. All 5 setup tests passed. Lint has zero errors,
116 elppA warnings and 96 upstream warnings. The full inherited Python suite was not
rerun; the device/setup behavior is unchanged.

Public TVmaze show/gallery responses were checked for six Indian shows across Hindi,
Tamil, Telugu, Kannada and Malayalam. Five exposed qualifying landscape dimensions;
Suzhal was below the 1280×720 threshold. Separate transparent logos were unavailable
in these samples. This is sample evidence, not complete Indian catalogue coverage.
OMDb authenticated responses remain untested because no owner API key is present.

No device is connected here. The photograph verifies the earlier rendering defect,
not the new appearance. Native shadow falloff, logo fit, rapid D-pad selection,
network failure behavior, API-key storage and physical remote/phone integration still
need a Mi Box acceptance pass. No measured hardware-performance or final visual-quality
claim is made. Mi Box testing does not certify the Sony X75.

## 0.5.0 — combined shelf, glass detail and horizontal focus

The movie row now combines filtered Watch Next first and filtered app previews second.
Duplicate provider/playback links retain the resume entry and its progress. Three new
regressions cover duplicate precedence, previews without watch history and the 70-item cap.
External catalogue, API-key setup, title-logo overlays and related classes/tests are removed
at the owner's request. App-published image loading, playback and frosted Play remain.

Resting app cards have stronger corner reflections and a fine directional edge. Only the
selected app gets a small feathered halo, aligned to its final scale/lift, plus the existing
dark shadow. Play now has a translucent warm-grey surface with no opaque normal undercoat.
Movie captions are left aligned at 75% warm-white opacity below the focused card.

Left/Right events are explicitly consumed by the row. Offscreen movement acquires focus
on the rail itself using temporary blocked descendants; pending focus transfers only when
the target attaches. Rapid keys use the latest target. Vertical departure cancels pending
movement; a no-op vertical edge restores card selection. Empty rows clear pending state.
Independent static review found and resolved the focus-anchor and no-op-vertical defects.

Both modules' release unit tests, lint and assembly passed: 315 upstream + 19 elppA = 334 tests,
zero failures/errors; all 5 setup tests passed. Lint has zero errors, 117 elppA warnings and
96 upstream warnings. The additional warning is the explicitly left-aligned caption's
Gravity.LEFT (RTL hardcoding). The full inherited Python suite was not rerun.

No TV is connected in the build workspace. Native glass appearance, opacity and real D-pad
behavior remain unverified. Required Mi Box pass: hold Right across offscreen cards, reverse
Left, immediately press Down while a target is attaching, refresh during navigation, test
both row ends and Up to utilities. Inspect the combined shelf with no watch history and
check preview links, caption placement, selected glow and reduced transparency/motion.
