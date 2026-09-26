# Device compatibility

What the app needs from a panel, and how it behaves when a panel falls short. Installers can
check a specific board with **Settings → Device diagnostics**.

## Minimum requirements

| | Minimum | Why |
|---|---|---|
| Android | **9 (API 28)** | `minSdk = 28`. Android 7/8 panels cannot install the app. Lowering it would touch storage/export code. |
| RAM | 2 GB | Tested on a 2 GB emulator: about 130 MB PSS on a board with ink (`largeHeap` is on). |
| Screen | Landscape, any size | Layout scales with `Dimens`; locked to landscape. |
| Touch | Single touch works | Pan and pinch-zoom need 2 touch points. |
| Network | Optional | Needed only for AI features and the one-off Text pen model download. |

## Tested on 2026-09-26

Emulator **Android 9 (API 28), `default` image with no Google Play Services, 2 GB RAM, 2560×1800**.

| Scenario | Result |
|---|---|
| Install and first launch, no Play Services | ✅ Works |
| Draw ink, and it is saved and reloaded after a reinstall | ✅ Works |
| **Text pen** (ML Kit Digital Ink) without Play Services | ✅ Model downloads (about 20 s on emulator Wi-Fi) and recognition works |
| **No WebView installed** (`pm disable com.android.webview`) | ❌→✅ Opening **Web** **crashed the app**. Fixed: Web is hidden, and the pane falls back to a message |
| No app to receive an image share (no Lens/Photos) | ✅ "Search with Lens" is hidden |
| No browser | ✅ "Search the web" is hidden |
| No pressure data (emulator and IR touch report 0 or a constant) | ✅ Ink never drops below 40% of base width (`InkSmoothing.widthForPressure`), so it stays visible. Turn off **Settings → Pressure sensitivity** for even lines |

## Not yet tested (needs real hardware)

- A real IR-touch panel: touch-point count, palm rejection with large contacts, latency.
- A low-end chipset (for example Allwinner or Rockchip at 2 GB): PDF import of a large book, split view with 6 panes.
- **ML Kit model download behind a school firewall.** The model comes from Google servers. If a school blocks them, the Text pen shows "Could not download the handwriting model". Pre-downloading at install time on an open network avoids this.
- Android 10–14 panels without Play Services (only 9 was tested; newer versions should be less restrictive).

## How features degrade

| Missing | Effect |
|---|---|
| Play Services | Nothing: the app has no Play Services dependency. ML Kit is the standalone artifact. |
| WebView | Web search pane hidden. 3D Maths and Notes are native and unaffected. |
| Network | Whiteboard, shapes, 3D Maths, the Text pen (after its first download) and saved lessons all work. AI Look up and Snapshot notes fail with a message, and the snapshot is kept for Retry. |
| Pressure | Constant-width ink. |
| Stylus | Fingers write. Stylus-only mode must stay off. |
