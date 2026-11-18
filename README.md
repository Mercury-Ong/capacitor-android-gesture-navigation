# capacitor-android-gesture-navigation

Keeps Android's status bar and navigation buttons readable in edge-to-edge Capacitor apps, and tells your app whether the user navigates with gestures or buttons.

## The problem

Apps that target Android 15 or later draw behind transparent system bars. The bar icons are either light or dark, and the app has to pick one. A single choice fails as soon as screens differ: white icons on a blue header turn invisible on a white page, and dark icons disappear on a dark one. Content scrolling under the bar changes the colour again.

With 3-button navigation it is worse: the back, home and recents buttons float over whatever is behind them, with no backing.

Declaring a colour per screen does not hold up either: it goes stale with every scroll, bottom sheet, dialog or photo, and every new screen needs one.

## What it does

- **Navigation mode:** reports `gesture` or `buttons` (2- or 3-button navigation), and fires `modeChange` when the user switches in system settings and comes back.
- **Auto style:** about four times a second, it copies a small thumbnail (48 × 208 px) of your app's own window, finds the most common colour under the status bar and in an 8dp strip just above the navigation bar, and sets light or dark icons for each bar. A little hysteresis keeps scrolling content from making the icons flicker.
- **Backing colour:** it reports that bottom colour in `toneChange`, so your web layer can draw a solid strip behind 3-button navigation that matches the screen.

It only reads your app's window, never the system UI or other apps, and needs no permissions. Sampling pauses while the app is in the background.

## Install

```bash
npm install github:Mercury-Ong/capacitor-android-gesture-navigation
npx cap sync android
```

Requires Capacitor 8 and an edge-to-edge activity (for example `EdgeToEdge.enable(this)` in `MainActivity`).

## Usage

```ts
import { GestureNavigation } from "capacitor-android-gesture-navigation";

const { mode } = await GestureNavigation.getMode();

// Keep a solid backing behind 3-button navigation in the screen's bottom colour.
await GestureNavigation.addListener("toneChange", ({ bottomColor }) => {
  document.documentElement.style.setProperty("--nav-backing", bottomColor);
});

await GestureNavigation.startAutoStyle();
```

```css
/* Shown only when mode is "buttons". */
.nav-backing {
  position: fixed;
  inset: auto 0 0 0;
  height: env(safe-area-inset-bottom);
  background: var(--nav-backing);
  pointer-events: none;
}
```

If another plugin also sets the bar style (for example `@capacitor/status-bar` or the safe-area plugin's `navigationBarStyle`), remove that setting: auto style re-applies its choice on every pass.

## API

| Method | Description |
|---|---|
| `getMode()` | `{ mode: "gesture" \| "buttons" }`. Web and iOS resolve `gesture`. |
| `setStyle({ buttons })` | Navigation button colour by hand: `"dark"` over light backgrounds, `"light"` over dark ones. |
| `startAutoStyle({ interval? })` | Samples the screen edges and styles both bars. `interval` in ms, default 250, minimum 100. |
| `stopAutoStyle()` | Stops sampling; the bars keep their last style. |
| `addListener("modeChange", …)` | `{ mode }` after the user switches navigation mode. |
| `addListener("toneChange", …)` | `{ top, bottom, bottomColor }` whenever a bar's tone or the bottom colour changes. `top` and `bottom` are `"light"` or `"dark"`; `bottomColor` is `#RRGGBB`. |

## Tested devices

| Phone | Android | Navigation | Capacitor | Result | Date |
|---|---|---|---|---|---|
| Samsung Galaxy A36 5G (SM-A366B) | 16 | 3-button | 8.4 | ✅ Works | 2026-11-18 |

Measured on that phone: white buttons on a blue screen 10.0:1, grey buttons on a white screen 4.2:1 (Android draws "dark" navigation buttons as grey), status bar icons 9.5–11.6:1.

## Notes

- Android only for now. On iOS and the web every call is a no-op, so you can call it unconditionally. iOS status bar support is planned.
- Android draws the button colours; the plugin only chooses between its light and dark variants.

## License

MIT
