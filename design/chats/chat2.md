# Android 17 Keyboard Design System

_Started 2026-10-20 12:21 UTC_

---

## User

Create a design system for an Android 17 keyboard app on the
Pixel 10 Pro.

Style: modern Material 3 Expressive. Rounded keys, soft depth,
calm, premium and minimal. The AI should feel quietly smart, not
flashy. Not playful or cartoonish.

Include:
1. Color tokens for light and dark mode: surface, keyboard
   background, key, key-pressed, special keys (shift, delete,
   enter, space), suggestion strip, text primary/secondary,
   divider, error, success.
2. Accent system: one accent role mapped to active keys, enter key,
   cursor, AI highlights and toggles. Provide:
   - Dynamic color (wallpaper-based) behaviour
   - 6 curated accent palettes with evocative names, each with
     light and dark values that pass WCAG AA on keys and text
   - Rules for auto-rotating accents by time of day
3. Typography: key labels, suggestion strip, settings screens.
   Diacritics (å/ä/ö) must render clearly in suggestions.
4. Shape and size: key radius, key gap, key height range, min 48dp
   touch targets, one-handed mode sizing.
5. Components: key (all states incl. long-press popup), suggestion
   chip, AI action chip, private-memory suggestion chip, "on-device"
   badge, "in my style" indicator, password/autofill chip,
   verification-code chip with expiry, toggle, list row, accent swatch.
6. Motion: key press, suggestion appear, AI panel expand, theme
   and accent transitions. Durations and easing.
7. A specimen page showing everything in light and dark side by side.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: run_script]_

_[tool: dc_write]_

_[tool: ready_for_verification]_

I've built the design system as one specimen page, `Tyst Keyboard Design System.dc.html`, with light and dark side by side throughout. The design system attached to this project was empty, so I worked from your brief and the Tyst product spec. It's with a background reviewer now.

- **Colour tokens:** 15 per mode, each with its contrast ratio.
- **Accents:** the six palettes are Hav, Fjäll, Mossa, Lingon, Bärnsten and Skymning. All of them reach at least 7:1 on keys and text in both modes, and the page calculates the ratios live. Clicking any swatch re-themes the whole page.
- **Wallpaper colour:** wallpaper colour is muted so the keyboard stays calm. If a wallpaper colour fails the contrast check, the accent falls back to Hav.
- **Time of day:** six fixed time windows, with a marker showing the current time. Rotation is opt-in, uses the clock only and never changes the accent while you're typing.
- **Interactive parts:** the key press, suggestion, AI panel, theme and accent animations can be played on the page. The verification-code countdown and the "Learn my style" toggle also work.

Things to check:
- **Touch targets:** letter keys are 41dp wide on a 412dp screen. That's below the 48dp minimum, which is normal for keyboards; the spec says so and covers it with the touch model. Every other control is at least 48dp.
- **Font:** the page asks for Google Sans Flex and falls back to Hanken Grotesk if it doesn't load.
- **Screen width:** I assumed 412dp for the Pixel 10 Pro at default display size. Please confirm it against the real device.
- **Tone strips:** the wallpaper-colour strips illustrate the mapping and aren't exact Android values.

