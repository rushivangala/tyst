# Design review framework

_Started 2026-10-20 21:24 UTC_

---

## User

You are a panel of three demanding reviewers:
- a senior Android/Material Design lead
- an accessibility specialist (WCAG 2.2, TalkBack)
- a UX writer and privacy reviewer

[ATTACH: screenshots or link to all screens and the prototype,
plus the design system and Stage 1 spec]

Review the design harshly but fairly. Check:
1. Is the USP ("writes like you and never goes online") visible and
   believable on every key screen? Where is it weak?
2. Do the AI features feel helpful and calm, or intrusive?
3. Contrast for every accent in light and dark mode (keys, labels,
   suggestion text, chips). List any failing combos with ratios.
4. Touch targets, one-handed reach, key spacing, long-press
   discoverability for å/ä/ö.
5. Consistency with Material 3 and Android 17 conventions.
6. Privacy and memory: is it clear what is stored, where, and how
   to delete it? Any overclaiming?
7. Passwords and codes: is it clear the keyboard never sees or
   stores them? Are chips safe when others can see the screen?
8. Anything a user would find confusing in the first 5 minutes.

Output a table: issue | screen | severity (blocker/major/minor) |
concrete fix. Then the top 5 changes that would most improve it.

## Assistant

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: grep]_

_[tool: grep]_

_[tool: grep]_

_[tool: read_file]_

_[tool: run_script]_

**Scope:** I reviewed the source of Keyboard Hi-fi (frames 1–10), Screens Hi-fi (1.1–3.5) and the Prototype. I calculated contrast for all 7 accents (6 palettes plus the wallpaper stand-in) in both modes.

**Contrast summary.** All text passes AA in both modes with every accent. The lowest is secondary text on special keys in dark mode, at 4.68:1. The failures are in non-text contrast and in signals that rely on colour alone:
- **Key vs keyboard background:** 1.19 light, 1.36 dark.
- **Divider outlines** (outlined buttons, chips, segmented control, badges): 1.30 on the keyboard background and 1.54 on keys in light. In dark, 1.43 on the keyboard background and **1.06 on keys**.
- **Accent vs error:** 1.00–1.19 in every palette. This means the code ring turning red under 30s is invisible without colour vision. Lingon light (#A02541) is also close in hue to the error red (#B3261E).
- **Dimmed AI chips at 38% opacity:** about 2.4:1 light and 2.9:1 dark. The screen says "visible but dimmed", so people are expected to read them.

| Issue | Screen | Sev | Fix |
|---|---|---|---|
| A ~1 GB model "downloading" with "Internet: Not requested" breaks the main claim on first use | 1.5, 3.3, AI key ring | **Blocker** | Say where it comes from: "Downloaded by Google Play. Tyst itself has no internet access." Show this on 1.1 and 1.5 too |
| "Everything stays on this phone" ignores Android backup and device-to-device transfer | 1.3, 2.5 | **Blocker** | Turn off backup for learned data and say so ("Not included in Google backup"), or add a toggle |
| "Tyst never reads your messages" contradicts "From 1,240 messages you sent" and codes "copied from Messages" | 2.7, 2.4 | **Blocker** | "Tyst learns only from what you type with it. It can't open your messages." Rename the counter to "1,240 things you typed" |
| Implies the keyboard never sees passwords. A keyboard receives every key you tap, so it does see a typed password | 2.7, f7 | Major | "Passwords you type aren't learned or saved. Saved passwords come from NordPass and Tyst never sees them." |
| No incognito state. Doesn't respect apps that ask keyboards not to learn (e.g. private browser tabs) | Keyboard | Major | Show a small "Not learning here" marker in the suggestion strip, and add a row in 2.5 explaining it |
| Memory stores other people's data and data from Chrome (a colleague's email, a member number), with no rules or retention | 2.5 | Major | List what's never saved: passwords, card numbers, codes, private fields. Add "Forget after 30/90 days / Never". Exclude browsers by default |
| Memory chip auto-shows the full address in the suggestion strip (privacy when others can see the screen) | f6 | Major | Show it truncated ("Linnégatan 14…"), hide it while screen sharing or recording, and use a "Paste address" label for TalkBack |
| Code chip shows the full code; red under 30s is the only expiry signal | f8 | Major | Hide while screen sharing. Add "Expires in 0:24" text with a live announcement for TalkBack. Clear it from the clipboard after use |
| Booking ref "ABX42K" saved in memory reads like a one-time code | 2.5 | Minor | Make sure codes never enter memory, and give it a clearer type label ("Booking reference") |
| Translate and Reply chips in the keyboard panel lead to "not available" | 3.1, 3.5 | Major | Remove features that haven't shipped from the keyboard. Keep the "Later" rows in Settings only |
| Long-press for å/ä/ö can't be discovered (no hints on keys) | f4 | Major | Teach it in the 1.5 practice field ("Hold a for å ä"), and show a one-time tip the first time a Swedish word is typed |
| The pre-selected ä means releasing a long-press inserts a predicted letter people didn't choose | f4 | Minor | Pre-select the first option in order. Put the prediction in the suggestion strip instead |
| Long-press accents with TalkBack | f4 | Major | Announce "Hold for å, ä" on the key, and make sure "Restore å ä ö" covers TalkBack users |
| Phrase chip close icon is 16dp | 2.4 | Major | 48dp hit area, or replace with an edit sheet |
| Delete only appears on tap (stand-in for swipe); no menu or TalkBack action | 2.5 | Major | Real swipe plus a per-row overflow menu with Delete, and a "Delete" accessibility action |
| Key edges at 1.19/1.36 contrast | Keyboard | Major | Raise the key fill difference or add a 1dp edge (≥3:1) as an option in a high-contrast setting |
| Divider outlines at 1.06–1.54 are the only edge of outlined buttons and chips | 2.4, 2.5, 2.7, 3.x | Major | Add a separate outline token at ≥3:1 for interactive edges; keep the divider for separators |
| Accent and error can't be told apart by brightness | f8, 2.5b, Lingon | Major | Pair red with an icon or text change; consider making Lingon more pink |
| Dimmed AI chips at about 2.4:1 | 3.1, 3.3, 3.4 | Minor | Keep chip text full contrast and dim only the icon and fill, or hide the chips |
| One-handed keys are about 34dp wide | f9 | Minor | Offer 75/82/90% width, and make sure the side rail doesn't steal edge touches |
| Three names for one thing: "AI features", "Writing tools", ✦ key | 2.3, 3.x | Minor | Pick one ("Writing tools") everywhere |
| Three overlapping stores (My style, Private memory, Personal dictionary) with no overview | 2.1, 2.4, 2.5 | Major | Add a "What Tyst keeps" summary in 2.5 with all three, sizes, and delete links |
| "Delete all" doesn't say if it covers the dictionary and clipboard | 2.5b | Minor | List all of it, including "142 dictionary words" |
| Snackbar repeats the deleted address on screen | 2.5 | Minor | "Address deleted" |
| AI key progress ring has no label | 1.5, keyboard | Minor | First run: "Writing model 62%" tooltip, plus an accessibility label |
| Segmented control is a pill inside a pill; snackbar has 12dp corners | 1.4, 2.6, 2.7 | Minor | Use the standard M3 segmented and connected button groups and snackbar shape |
| Predictive back not specified for the AI panel or sheets | 3.x, 2.4b | Minor | Back gesture closes the panel and returns to the keys; show the preview animation |
| "There is no way for it to send…" is too absolute | 1.3 | Minor | "It can't connect to the internet, so it can't send what you write anywhere." |
| Fixed text sizes with single-line truncation in memory rows | 2.5 | Minor | Wrap to two lines at large font scale; test at 200% |
| "Writes like you" is weak on the keyboard; only Rewrite shows it | f10 | Minor | Show a short reason under results ("Shorter, digits for times: your Messages style") |
| Learning is on by default during onboarding | 1.3 | Minor | Fine, since it's on-device, but add "You can see everything it learns in My style" |

**Top 5 changes**
1. **Explain the model download** (Play delivers it, Tyst has no network) on 1.1, 1.5 and 3.3. Without this, the main claim falls apart in the first minute.
2. **Fix three claims:** backup, "never reads messages", "never sees passwords". Then add an incognito / "not learning here" state.
3. **Add a "What Tyst keeps" overview** with clear rules for what memory never saves, retention, and per-store delete.
4. **Make chips safe on a shared screen:** hide them during screen sharing or recording, truncate the address, show code expiry as text, no colour-only red.
5. **Accessibility pass:** add a ≥3:1 outline token, 48dp targets on chip close icons, TalkBack actions for delete and accents, and teach long-press for å/ä/ö in onboarding.

