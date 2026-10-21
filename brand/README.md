# Tyst launcher icon (option 1a)

A keycap whose spacebar is a flat, silent line. Drawn on the 108dp adaptive-icon grid; the glyph sits well inside the 66dp safe zone.

| File | Use |
|---|---|
| `tyst-icon-full.svg` | Full-bleed square, `#0D5C58` background (source for store and marketing) |
| `tyst-icon-circle.svg` | Circle-masked preview |
| `tyst-icon-foreground.svg` | White glyph on transparent (adaptive foreground) |
| `tyst-icon-monochrome.svg` | Black glyph on transparent (themed icon; the system tints it) |
| `tyst-icon-playstore-512.png` | Google Play listing icon (512×512, Play applies the mask) |

Android resources live in `app/src/main/res/`: `mipmap-anydpi-v26/ic_launcher(_round).xml` combine the background colour, foreground and monochrome layers. Point the manifest at them with `android:icon="@mipmap/ic_launcher"`, `android:roundIcon="@mipmap/ic_launcher_round"` and `android:label="@string/app_name"`.

The spacebar is a cut-out, not a painted shape, so the background (or the system's themed colour) shows through it in every variant.
