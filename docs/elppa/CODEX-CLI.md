# Codex CLI handoff — elppA TV

Read this before changing code or connecting to a device. Work only in
`SanskariXD/elppA-TV-UI`, on `main`. Check `git status`, `git remote -v` and current
remote main before editing; preserve user changes. No upstream PRs or new branches.

## Product and current state

- Native Java launcher: `launcher/elppa`. Shared helpers/resources: `launcher/app`.
- Package `io.github.sanskarixd.elppa`; activity `com.gathin.tvhome.ElppaHomeActivity`.
- Current test device: Xiaomi Mi Box, model and Android version not yet verified.
- Final device: original Sony KD-50X75 (2021). Do not assume X75K/Google TV.
- Visual target: cinematic background, horizontal app dock, horizontal Watch Next shelf.
  Preserve Outfit and clear remote focus; no video, full-screen blur or background polling.
- 0.4.0 extends the owner's reference: Wi-Fi/Settings only, icon-only play,
  clean app tiles, movie captions below focus, shadow-led focus, grouped settings
  side panel, wide 16:9 media, larger uncropped app logos, dark drop shadows, corner reflections, cached
  frosted play, public TVmaze show artwork and optional OMDb movie metadata. See DESIGN.md.
- Preview mode is labelled sample content, not actual movies. Never fill the dock with
  fake installed OTT apps. Native capture of 0.4.0 is still required.
- **Open Phase 1 requirement:** top-rated + relevant recommended movie hero feed.
  Current hero uses app preview rows, then Watch Next. TVmaze can enrich show artwork;
  OMDb supplies movie metadata. Neither integration ranks or chooses recommendations.
  Implement catalogue ranking separately from app watch history; never commit tokens
  or invent entitlement.

## Windows build

Use JDK 17, Android SDK platform 34/build-tools 34.0.0, Python 3 and platform-tools
on PATH. Set ANDROID_HOME to the local SDK. From the repository root in PowerShell:

```powershell
cd launcher
.\gradlew.bat --no-daemon testReleaseUnitTest lintRelease assembleRelease
cd ..
python -m unittest discover -s tests -p test_elppa_setup.py -v
```

APK: `launcher/elppa/build/outputs/apk/release/elppa-release.apk`.
The existing release configuration uses the local Android debug signing key.
A new PC's key differs from the previously downloaded APK. If installation reports
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, stop and explain the key mismatch; do not
uninstall or erase app preferences automatically. For repeated local test builds,
keep the same local key. Do not commit keys, APKs or Android SDK files.

## Mi Box session

Follow [MIBOX-TEST.md](MIBOX-TEST.md). Require an explicitly selected authorised
ADB endpoint; never guess a device when an emulator and box are both connected.
Install and open as an app first. No Home switch until the user has checked the UI.
Use the existing backup/make-home/restore tool when ready; never disable stock Home.
Do not run cleanup scripts or alter Assistant, Remote Service, Cast or TV providers.

## Verification order

1. Read [QA.md](QA.md); rerun release tests, lint and builds after code changes.
2. Inspect native screenshots at the device's real resolution: default text, enlarged
   text, long title/provider, app banner/icon fallback, empty and permission-denied states.
3. Check D-pad from every edge, long press, All apps, refresh while focused, Back,
   opening titles, failed links, offline launch and returning from apps.
4. Ask the user to test physical mic/Home/volume, phone remote, standby and reboot.
   ADB-injected keys do not prove physical remote compatibility.
5. Record verified results and remaining failures; commit only source/docs/tests to main.
   Keep device identifiers, private logs, screenshots and recovery records out of Git.

Build success is not visual acceptance. Mi Box success is not Sony acceptance.
Do not report the catalogue feed, subscriptions or universal Continue Watching as working.


## Movie artwork connection (0.4.0)

TVmaze show artwork is enabled without an API key. It sends known TV-series titles to
TVmaze, uses exact matching and rejects undersized backgrounds/opaque title-logo art.
It can be disabled in Settings → Movie artwork → TV show artwork.

For movie details, open Settings → Movie artwork → Connect OMDb. Get the owner's key
at https://www.omdbapi.com/apikey.aspx and activate it through OMDb's email. Enter it
privately with the TV keyboard/phone remote; never ask for it in chat, tracked files,
command arguments or logs. The free tier advertises 1,000 requests/day; successful
results are cached. OMDb has no cinematic movie backdrop or separate-title-logo API.

Use app backgrounds is on by default and retains existing explicit preferences.
Turning it off gives a neutral hero when no suitable catalogue artwork exists.
Provider backgrounds can still contain Top10 badges. OMDb posters are not stretched
into the hero. Old TMDB credentials are isolated from the OMDb encrypted store.

Capture actual Home with app focus, movie focus and Settings. Check edge-to-edge
16:9 app banners, neutral padding for nonstandard assets, dark shadow falloff and corner
reflections on resting cards. Verify rapid selection, Back, offline and correct
metadata/artwork identity. No native screenshot of 0.4.0 or authenticated OMDb test
is available from the build workspace. Physical remote/phone tests remain required.
