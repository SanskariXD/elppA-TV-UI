---
name: elppA TV
description: Cinematic Android TV Home with illuminated focus and a native settings side panel.
colors:
  text: "#F4F2EF"
  muted: "#CDCBC8"
  background: "#090B10"
  surface: "#191D25"
  panel: "#171A20"
  panel-row: "#22262D"
typography:
  display:
    fontFamily: "Outfit"
    fontSize: "36px"
    fontWeight: 400
  clock:
    fontFamily: "Outfit"
    fontSize: "23px"
    fontWeight: 400
  caption:
    fontFamily: "Outfit"
    fontSize: "11px"
    fontWeight: 400
rounded:
  card: "12px"
  panel: "22px"
---

# Cinematic Home — 0.3.0

The owner's second reference image is the visual authority. Home is an artwork-led,
remote-operated surface. Native Java and RecyclerView keep rendering bounded on the
Mi Box and original Sony KD-50X75. The current device photo established the need to
remove utility clutter, app captions, overlaid media captions and thick focus borders.

## Home composition

Dimensions below use a centred 960×540 logical canvas, scaled uniformly to the viewport.
Backdrop and scrims fill the full window. Outfit is bundled through the shared UiFont.

| Element | Logical bounds / treatment |
| --- | --- |
| Clock | x36 y16; 23-unit text, system 12/24-hour choice |
| Wi-Fi / Settings | x848 / x900, y14, 38×38; no Inputs control on Home |
| Featured title | x48 y94, 450×78; bottom aligned; preferred 36-unit text, minimum 26 |
| Enlarged title | y76, height96; same baseline and auto-sizing |
| Provider metadata | x50 y182, 440×28; no decorative eyebrow |
| Play action | x50 y220, 88×36; vector triangle; accessible title/provider label |
| App dock | x24 y264, 912×112; 152×92 cards, 16-unit total horizontal margin |
| Media rail | x24 y378, 912×158; 176×99 (16:9) cards plus reserved caption space |
| Empty state | x36 y420, 888×70; access/diagnostics remain reachable |

Next pick and the textual Continue/Open buttons are removed. One icon-only play
control remains, as in the reference. It is hidden when no featured title exists;
D-pad Up then goes directly to Settings. Inputs is available within Settings.
The former shelf heading is removed to give the artwork more space.

App names never appear visually on focus; content descriptions remain for accessibility.
Media titles appear below their focused card, over transparent space, up to two lines.
They auto-fit within the caption area. The image fallback still displays a title if art
cannot be loaded. Progress remains sourced from app-published playback metadata.

## Focus and motion

Unfocused and focused cards have no crisp perimeter stroke. FocusGlow draws twelve
broad low-opacity tinted strokes, ending in a diffuse falloff rather than a final white
rim. CardReflection adds a fixed diagonal sheen and a soft radial light above artwork.
App banners use FIT_CENTER with 6-unit insets; square icons use 22. A 16×16 corner sample
extends the banner's surface colour without cropping the logo. Sampled colours are
cached per rail; no image sampling runs each frame.

The card lifts 3 logical units and scales to 1.045 over 200 ms with a decelerating curve.
D-pad and mouse hover share focus. Reduced motion snaps these transforms. Reduce
transparency removes gloss/tinted falloff and uses a solid brighter focus surface.
The single play button uses a 64×32 crop of the actual backdrop, blurred with two small
separable box passes on an artwork worker once per hero. A translucent tint and fixed
sheen finish the surface; no full-screen blur or per-frame sampling is used.

Hero images crossfade for 250 ms when motion is enabled. Auto-rotation remains every
15 seconds only while Home has window focus and the app dock is focused. Dialogs,
other rows and hidden Home pause rotation. Artwork is still, never background video.

## Settings and menus

HomePanel is a right-hand 390×500 logical panel with a 20-unit window inset, a solid
charcoal surface, 22-unit corners and a dimmed Home behind it. Native ScrollView and
LinearLayout provide remote navigation. Grouped Home / Device / Manage content options
have plain labels, supporting descriptions and native switches. Focused rows use a light
surface with dark text. Switches update in place; actions open the chosen destination.
Back or Done closes the panel. Permission prompts still use Android's own UI.

The same panel styles All apps, app actions, source selection, diagnostics and messages.
All apps retains long-press management. Source edits are saved explicitly; cancelling
leaves the prior source selection intact.

## Catalogue artwork, logos and performance

A separate transparent ImageView at x48 y84 (370×100 logical units) displays the real
movie/show PNG title logo with FIT_START and its original aspect ratio. Plain title text
remains until an image loads, and returns for missing/ambiguous logos. Logos are not
redrawn in a lookalike typeface. Watch history, source app and playback URI remain
from the installed app.

Settings → Movie artwork connects an owner-provided TMDB API Read Access Token.
The app validates it over HTTPS, stores it encrypted with Android Keystore, and includes
the approved TMDB mark and required notice in Artwork credits. Names of focused/featured
titles are sent to TMDB for artwork lookup. No shared credential ships in the app.

The resolver matches exact normalized titles/types, using an explicit title year when
provided; ambiguous remakes and unidentified episode/season rows are left unresolved.
It selects language-neutral landscape backdrops >=1280×720 and a suitable PNG logo,
using configuration-advertised CDN sizes. No-language metadata cannot prove image pixels
contain no text. Asset coverage is incomplete; no “all titles” guarantee is made.

Before connection, provider artwork remains available. Once connected, a missing clean
match defaults to a neutral hero with title text. Optional provider fallback is clearly
labelled as potentially containing badges/low-resolution art. A Top10 badge baked into
provider pixels is not erased; a different catalogue image replaces that image.

Requests wait 180 ms for focus to settle, run on one catalogue worker, cancel on hero
changes/hidden Home, and use bounded metadata cache/TTL, timeouts and HTTP cooldowns.
Image loading retains two workers, 24 queued requests, 16 MiB decoded cache, 48 MiB disk
cache and 8 MiB per response. Hero decode remains at most 1920×1080; frost is 2048 pixels.
No new dependencies, permissions, background service or continuous animation are added.

See docs/elppa/CATALOGUE-ARTWORK.md and ARTWORK-RESEARCH.md for sources and setup.

## Evidence and acceptance

The supplied first image is a physical-device photo of the earlier build, not a capture
of this revision. Read docs/elppa/QA.md for automated results. Fresh native screenshots
of Home, settings, focused movie captions and enlarged text are required on Mi Box.
No native visual pass or hardware performance improvement is claimed from source/build
checks. Mi Box acceptance cannot certify Sony inputs, mic or phone-remote behaviour.
