---
name: elppA TV
description: Cinematic Android TV Home with shadow-led focus and a native settings side panel.
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

# Cinematic Home — 0.5.0

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
| App dock | x24 y264, 912×112; 160×90 (16:9) cards, 16-unit total horizontal margin |
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

App cards use their native landscape banner edge to edge in a 160×90 (16:9) tile.
FIT_CENTER preserves complete logos; banners get zero synthetic inset. Square icons
retain 22-unit padding. Non-16:9 artwork may have neutral letterboxing instead of
cropping. There is no sampled-colour backplate or perimeter stroke. The user's device
photo exposed how the old 6-unit inset plus sampled colour made an ugly thick frame.

The entire card has a rounded outline and native black elevation shadow. Focus lifts
3 units and scales to 1.045 over 200 ms. Resting app cards have visible corner sheen
and a fine directional reflective edge, fading out rather than forming a uniform white
border. Only the selected app gets a small soft glow. Reduced transparency disables
these glass details; focus scale/lift/shadow remain. Reduced motion snaps transforms.
The play pill uses a cached 64×32 blurred crop of the actual backdrop, a translucent
warm-grey tint and restrained highlights. Its normal surface has no opaque grey base.

Movie captions are left aligned at 75% warm-white opacity, below the focused image.
The row combines filtered Watch Next first, then app preview recommendations, with
source/playback-link duplicates removed and a 70-item cap. Resume progress wins when
both tables refer to the same target. Preview entries do not invent watch progress.
Left/Right is explicitly consumed by the row; an offscreen target holds focus inside
the rail until attached. Up/Down cancels pending horizontal movement before leaving.

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

## Provider artwork and performance

External catalogue/title-artwork integration is removed for now at the owner's request.
No TMDB, OMDb or TVmaze lookup or API-key setup runs in 0.5.0. Featured titles use text;
images come from installed apps' accessible TV-provider rows. Android's thumbnail URI
is not guaranteed to be a cinematic backdrop; source badges and low resolution can
remain. Original provider playback links handle opening titles.

Artwork loading retains two workers, 24 queued requests, 16 MiB decoded cache, 48 MiB disk
cache and 8 MiB per response. Hero decode is at most 1920×1080; frost is 2048 pixels and
prepared off the UI thread. No new dependencies, permissions or background services.

## Evidence and acceptance

The supplied first image is a physical-device photo of the earlier build, not a capture
of this revision. Read docs/elppa/QA.md for automated results. Fresh native screenshots
of Home, settings, focused movie captions and enlarged text are required on Mi Box.
No native visual pass or hardware performance improvement is claimed from source/build
checks. Mi Box acceptance cannot certify Sony inputs, mic or phone-remote behaviour.
