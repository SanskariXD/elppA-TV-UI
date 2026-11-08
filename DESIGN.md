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

# Cinematic Home — 0.2.0

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
| App dock | x24 y270, 912×94; 104×72 cards, 12-unit total horizontal margin |
| Media rail | x24 y366, 912×146; 128×104 cards plus reserved caption space |
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

Unfocused cards have no stroke. A focused card uses FocusGlow: a thin white/cyan/lilac/
warm edge and eight low-opacity geometric strokes for a soft falloff. The user's
explicit focus-light reference authorises this subtle illumination. No real-time blur,
software rendering layer, bitmap glow, infinite shimmer or animation loop is added.

Only the card surface lifts 2 logical units and scales to 1.055 over 180 ms with
DecelerateInterpolator. The caption remains stationary. App/system reduced-motion
settings make this instantaneous. Mouse hover enters the same focus state as D-pad.

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

## Artwork quality and performance

ArtworkSizing compares both app-provided image candidates using source dimensions,
with a preference for landscape composition and then usable detail. The loader reads
headers before deciding, decodes the chosen candidate and falls back if decoding fails.
Source dimensions are shown in Content diagnostics; no signed URLs are displayed.

Hero decoding targets at most 1920×1080 even if the window is 720p. Power-of-two sampling
now avoids falling below the target prematurely, followed by one filtered downscale.
It never upscales the stored bitmap or rewrites a provider URL. Small provider images
remain small sources; this cannot reproduce full-resolution studio backdrops or title
logos when the provider does not share them. A catalogue artwork source is future work.

Two workers, a bounded 24-request queue, 16 MiB decoded cache, 48 MiB disk cache and
8 MiB response limit remain. Header probes can fetch both distinct candidate files,
but reuse the disk cache. Decode allocation is bounded; paused generations cannot bind.
No new dependencies, permissions, background service or polling were added.

## Evidence and acceptance

The supplied first image is a physical-device photo of the earlier build, not a capture
of this revision. Read docs/elppa/QA.md for automated results. Fresh native screenshots
of Home, settings, focused movie captions and enlarged text are required on Mi Box.
No native visual pass or hardware performance improvement is claimed from source/build
checks. Mi Box acceptance cannot certify Sony inputs, mic or phone-remote behaviour.
