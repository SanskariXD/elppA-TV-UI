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
