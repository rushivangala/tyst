# Tyst

**The keyboard that writes like you and never goes online.**

Tyst ("quiet" in Swedish) is an Android keyboard for Android 17 (API 37), tuned for the Pixel 10 Pro. English is the main language. Swedish words, names and places are recognised inside English text, and Tyst never "corrects" them. Everything runs on the phone, and the app has no internet permission.

The design work (spec, design system, screens, copy and the design review) is in [`design/`](design/). The icon exports are in [`brand/`](brand/).

## What's in the app

| Spec MVP feature | Status |
|---|---|
| Personal prediction and autocorrect | ✅ Three-slot strip with the most likely word in the centre in bold. Autocorrect fires on space or punctuation. Backspace or the underlined slot undoes it, and undoing the same word twice teaches it. Learns words, bigrams and trigrams on the phone. |
| Swedish-in-English layer | ✅ 40k Swedish words plus curated places and names. Never corrected. Restore å ä ö *suggests* Västerås for "Vasteras" and never applies it by itself. Long-press a/o for å ä ö, or turn on the optional å ä ö row. |
| Rewrite in my style | ✅ My style, Warmer, Formal, Shorter, Longer. Changed words are underlined. You can Replace (with 5 s Undo) or Copy. Names and Swedish words stay exactly as typed. |
| Proofread | ✅ Fix by fix: Accept, Skip or Accept all. Covers spelling (with the Swedish guard), i → I, capitals, repeated words, a/an, spacing and common confusions. |
| Private memory | ✅ Opt-in. Saves addresses, booking references, phone numbers and emails, but only ones introduced as the user's own ("my address is…"). Offered as a truncated chip with a lock. Forgotten after 30 or 90 days unless pinned. Browsers are excluded, and card numbers and codes are never saved. |
| Password manager suggestions | ✅ Android inline autofill pills (NordPass or any autofill service), drawn by the system. Tyst can't read them. |
| Verification code chip | ✅ Shows a code from a fresh clipboard item with a "hides in N s" countdown. The code is held in memory only. The clipboard can optionally be cleared after filling. |
| What I learned and privacy proof | ✅ My style (habits you can toggle, phrases, pause, reset), Words (dictionary, never-correct, never-learn), Privacy & memory (search, delete with Undo, delete all, which destroys the key), and a live permission list read from Android. |
| On-device voice typing | ✅ Uses Android's *on-device* speech recogniser only (no network fallback), followed by a Swedish pass ("vasteras" → "Västerås"). |
| Material 3 Expressive theming | ✅ Light, dark or system. Wallpaper colour with a contrast guard, the six curated palettes (Hav, Fjäll, Mossa, Lingon, Bärnsten, Skymning) or time-of-day rotation. Key height S–XL, key borders, number row, one-handed mode (left/right), and a live preview. |

The keyboard also has symbols (two pages), number and phone pads, offline emoji with your own most-used emoji first, and the keyboard picker on long-press of space. Long-press comma gives emoji, settings and one-handed mode. TalkBack can explore and type every key ("a. Hold for å, ä"). Changes from the design review are included: the outline token at ≥ 3:1, no backup of learned data, an incognito marker, truncated memory chips, a countdown text on codes, Translate and Reply removed from the keyboard, and long-press tips in onboarding.

### Different from the spec, on purpose

- **The writing tools use a built-in rules engine, not an LLM.** The spec plans a ~1 GB on-device model delivered by Play Asset Delivery. That model isn't part of this build. Rewrite and Proofread use deterministic phrase rules plus your learned habits. They work offline from the first minute and never invent facts. The engine sits behind `Rewriter` and `Proofreader` in `:core`, so a model can replace it later.
- **Not built yet:** swipe typing, the clipboard history panel, per-app default tones, encrypted export, a biometric lock for memory, Translate and Reply (shown as "Later" in Settings), and Direct Boot support. Before the first unlock after a reboot, Android uses another keyboard.

## Project layout

```
core/   Pure Kotlin. All keyboard intelligence, fully unit-tested on the JVM:
        lexicon, predictor, autocorrect, typing controller, memory, codes,
        rewrite/proofread, style learning, field classifier, layouts, theme tokens, icons.
app/    Android app (minSdk 31, target/compile 37):
        ime/   InputMethodService, custom-drawn keyboard, strip, panels, inline autofill, voice
        data/  AES-GCM storage with a hardware-backed Android Keystore key, settings, app graph
        ui/    Compose onboarding and settings
tools/  gen_lexicon.py builds the word lists from wordfreq
design/ The Claude Design handoff (spec, design system, screens, copy, chats)
brand/  Launcher icon exports
```

The typing logic is in `core/.../typing/TypingController.kt` and talks to the editor through a small `TextTarget` interface. The app adapts Android's `InputConnection` to that interface, and the tests drive the same controller with fake editors and real `EditText`s.

## Build

Requirements: JDK 17+ and the Android SDK (platform 37). AGP 9.4.0, Gradle 9.6.1 (wrapper) and Kotlin 2.4.21.

```bash
./gradlew :core:test                 # engine tests (JVM, no SDK needed)
./gradlew :app:testDebugUnitTest     # Robolectric + Compose UI tests
./gradlew :app:assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:connectedDebugAndroidTest   # end-to-end on a device or emulator
```

Without an Android SDK, the build includes only `:core`.

Every `assemble` runs `verify<Variant>NoNetworkPermission`. It fails the build if the merged manifest requests INTERNET or network-state permissions, which is the CI release gate from the spec (§08).

### CI

`.github/workflows/android.yml` runs on every push:

- the core tests, the Robolectric tests and lint
- debug and release APK builds, uploaded as the `tyst-apks` artifact
- the emulator end-to-end tests on API 35

The release APK is signed with your key when these repository secrets exist: `TYST_KEYSTORE_BASE64`, `TYST_KEYSTORE_PASSWORD`, `TYST_KEY_ALIAS` and `TYST_KEY_PASSWORD`. Without them, it falls back to the debug key so it can still be installed.

### Install on a Pixel

1. Download `tyst-apks` from the workflow run and `adb install app-release.apk`, or open the APK on the phone.
2. Open Tyst. Onboarding walks you through enabling it and choosing it as your keyboard.

## Tests

- **`:core`: 93 JVM tests.**
  - All ten autocorrect cases from the UX copy, and the Swedish guard.
  - A corpus check of fewer than 0.5% Swedish words wrongly corrected (spec metric), and that none of the 10,000 most common English words is changed.
  - Prediction p95 latency under 30 ms (spec metric).
  - Typing flows: caps, undo, double-space period, smart punctuation, incognito, codes and memory.
  - Memory extraction and its safety rules, OTP detection, rewrite and proofread, style learning, and every palette passing WCAG AA in both modes.
  - Layout geometry against the design system's sizes, and the icon parser.
- **`:app`: Robolectric tests.**
  - The encrypted store (tamper, wrong key, delete-all destroys the key), settings persistence, and the graph's load/save/expiry cycle.
  - Typing against a real `EditText` InputConnection.
  - KeyboardView touch handling (taps, long-press å, backspace repeat, space → picker), and TalkBack descriptions.
  - Manifest privacy checks (no INTERNET, no backup, protected service).
  - The service start-up, and Compose UI flows: onboarding, settings, delete with Undo, delete all, palettes, privacy proof and words.
- **Emulator: end-to-end tests** that type "Hi teh " with the real keyboard and check the autocorrect and its undo, pick a next-word suggestion, and open the settings app.

## Privacy, and how it's enforced

- **No network permission.** The manifest strips INTERNET and ACCESS_NETWORK_STATE even if a library adds them. The build gate fails if either appears, and the Privacy proof screen reads the permission list live from Android.
- **Encrypted at rest.** Everything learned is encrypted with AES-256-GCM using a key in the hardware-backed Android Keystore. "Delete all" deletes the files and destroys the key.
- **No backup or transfer.** `allowBackup=false`, and the data extraction rules exclude everything from cloud backup and device transfer.
- **Incognito where it matters.** Nothing is learned in password fields, number and code fields, fields that set `IME_FLAG_NO_PERSONALIZED_LEARNING`, or while learning is paused. The strip shows "Not learning here".
- **Codes and passwords.** Codes come only from text you copy and stay in memory. Passwords come from your autofill service through Android, and Tyst can't read them.

See [LICENSES.md](LICENSES.md) for third-party data and code.
