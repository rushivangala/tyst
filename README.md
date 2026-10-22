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
| Copied text | ✅ The last thing you copied appears as a chip for 3 minutes. One tap pastes it. It steps aside while you type a word, and anything marked sensitive shows as dots. Held in memory only, and can be turned off in Passwords & codes. |
| What I learned and privacy proof | ✅ My style (habits you can toggle, phrases, pause, reset), Words (dictionary, never-correct, never-learn), Privacy & memory (search, delete with Undo, delete all, which destroys the key), and a live permission list read from Android. |
| On-device voice typing | ✅ Uses Android's *on-device* speech recogniser only (no network fallback), followed by a Swedish pass ("vasteras" → "Västerås"). |
| Material 3 Expressive theming | ✅ Light, dark or system. Wallpaper colour with a contrast guard, the six curated palettes (Hav, Fjäll, Mossa, Lingon, Bärnsten, Skymning) or time-of-day rotation. Key height S–XL, key borders, number row, one-handed mode (left/right), and a live preview. |

Slide along the space bar to move the cursor, and slide left from backspace to select whole words and delete them on release. The keyboard also has symbols (two pages), number and phone pads, offline emoji with your own most-used emoji first, and the keyboard picker on long-press of space. Long-press comma gives emoji, settings and one-handed mode. TalkBack can explore and type every key ("a. Hold for å, ä"). Changes from the design review are included: the outline token at ≥ 3:1, no backup of learned data, an incognito marker, truncated memory chips, a countdown text on codes, Translate and Reply removed from the keyboard, and long-press tips in onboarding.

### Different from the spec, on purpose

- **The writing tools use a built-in rules engine, not an LLM.** The spec plans a ~1 GB on-device model delivered by Play Asset Delivery. That model isn't part of this build. Rewrite and Proofread use deterministic phrase rules plus your learned habits. They work offline from the first minute and never invent facts. The engine sits behind `Rewriter` and `Proofreader` in `:core`, so a model can replace it later.
- **Not built yet:** swipe typing, a clipboard history panel (only the latest copy is offered), per-app default tones, encrypted export, a biometric lock for memory, Translate and Reply (shown as "Later" in Settings), and Direct Boot support. Before the first unlock after a reboot, Android uses another keyboard.

## Typing quality

`core/src/test/kotlin/app/tyst/core/bench/TypingBenchmark.kt` measures typing end to end through `TypingController`. A simulated thumb types held-out messages (`core/src/test/resources/bench/messages.txt`, never used for training) with Gaussian tap scatter on a phone-sized layout. The benchmark reports:

- **False corrections:** correctly typed words that autocorrect changed.
- **Typos fixed:** mistyped words that came out right.
- **Keystroke savings:** a typist who taps a suggestion as soon as the wanted word appears.
- **Next word in top 3** and **strip latency**.

Tune on the `TUNE` half and report the `TEST` half. `TypingBenchmarkTest` holds the regression floors.

| Held-out half | before | now |
|---|---|---|
| False corrections | 0.0% | 0.0% |
| Typos fixed | 64.8% | 86.4% |
| Keystroke savings | 40.3% | 41.0% |
| Next word in top 3 | 24.8% | 28.5% |
| Strip latency p95 (desktop JVM) | 6.7 ms | 2.7 ms |

How autocorrect decides:

- **Common words never change.** The 10,000 most common English words, curated Swedish names, places and words, and anything spelled with å ä ö are never changed.
- **Rare words.** A rarer word is replaced only when a far likelier word (about 60×) is one key-slip away. Entries that no spell-checker lists, such as "thr" and "pn", count as less likely (`en_unlisted.txt`).
- **Where the finger landed** on each letter makes the nearly-hit neighbour cheaper.
- **Swedish words.** Plain-ASCII Swedish words get a penalty inside English sentences on Medium and Strong. Mild keeps every Swedish word as typed.
- **Next words** blend the hand-written chat phrases with a general-English table (`en_next.txt`).

`tools/gen_bigrams.py` and `tools/gen_unlisted.py` rebuild the data files.

## Project layout

```
core/   Pure Kotlin. All keyboard intelligence, fully unit-tested on the JVM:
        lexicon, predictor, autocorrect, typing controller, memory, codes,
        rewrite/proofread, style learning, field classifier, layouts, theme tokens, icons.
app/    Android app (minSdk 31, target/compile 37):
        ime/   InputMethodService, custom-drawn keyboard, strip, panels, inline autofill, voice
        data/  AES-GCM storage with a hardware-backed Android Keystore key, settings, app graph
        ui/    Compose onboarding and settings
tools/  gen_lexicon.py (wordfreq), gen_bigrams.py and gen_unlisted.py (symspellpy) build the word data
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

Release APKs are signed with Tyst's permanent key, which lives in two repository secrets: `TYST_KEYSTORE_BASE64` and `TYST_KEYSTORE_PASSWORD`. Without them, branch builds fall back to a throwaway debug key, and a different one each run, so those can't update an installed copy.

### Releases

Put the version in `release.txt` (for example `v1.0.0`) and push to `main`. After the tests and emulator checks pass, CI builds the signed APK and checks its certificate (SHA-256 `E0:25:42:96:…:91:9A`). It then publishes a GitHub Release with `tyst-v1.0.0.apk` and its checksum, using `docs/releases/<tag>.md` as the notes. Each version is published once, and the release job refuses to publish without the permanent key. Pushing a `v*` tag works too.

### Install on a Pixel

1. Download the APK from the latest [release](https://github.com/rushivangala/tyst/releases/latest) on the phone and open it, or use `adb install tyst-v1.0.0.apk`.
2. Open Tyst. Onboarding walks you through enabling it and choosing it as your keyboard.

## Tests

- **`:core`: 101 JVM tests.**
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
