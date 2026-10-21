# Pixel 10 Pro Keyboard Design

_Started 2026-10-20 12:40 UTC_

---

## User

Using my saved design system, design the keyboard in high fidelity,
based on wireframe variant [A/B/C].

Show it inside a realistic messaging app on a Pixel 10 Pro frame.

Layout: English QWERTY. å, ä, ö available by long-press on a and o,
plus an optional setting that adds them as a compact extra row.
No language-switch key.

Suggestion strip: 3 suggestions plus an AI button that opens the
AI panel. Show this real scenario: the user has typed
"Can we meet at Järntorget around" and the strip suggests
"6", "lunch", "noon" in the user's usual style.

Deliver these states as separate frames:
1. Light mode, default accent
2. Dark mode, default accent
3. Dark mode with 2 other curated accents
4. Long-press popup on "a" showing å and ä
5. Autocorrect fixing an English typo ("teh" → "the") in the same
   sentence while leaving "Järntorget" untouched
6. Private memory chip: after "my address is", a chip offers the
   saved address with a small lock icon
7. Login screen: NordPass username and password chips in the strip
8. Verification code field: number pad and a "Code 482 913" chip
   with a fading expiry indicator
9. One-handed mode (right)
10. AI panel expanded: Rewrite in my style / Proofread / Translate /
   Reply chips, the "on-device" badge, and a preview of a rewrite

Prioritise legibility, calm, and generous key spacing. Explain your
key design decisions in 5 bullets.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: dc_write]_

_[tool: ready_for_verification]_

_[tool: update_todos]_

## Assistant

The board is in **Tyst Keyboard Hi-fi.dc.html**: 12 Pixel 10 Pro frames covering states 1–10. Two extras: 1b shows the optional å ä ö row, and frame 3 is split into 3a (Fjäll) and 3b (Lingon). It's still out for review, so I haven't seen a final render yet.

The five key design decisions:

- **Built on wireframe variant A, the single strip.** Your message left the variant as "[A/B/C]", so I chose A because it's the lowest keyboard. The AI button sits on one fixed key at the left of the strip. Rich chips (memory, NordPass, code) take the place of the suggestions only when they're relevant.
- **Legibility.** Letters are 22sp on 52dp keys, with 6dp gaps across and 10dp between rows. No secondary hints are printed on keys. The top suggestion ("lunch") is marked with bold text, not colour, so the strip stays calm.
- **One accent, used sparingly.** The accent colour appears only on Enter, the cursor, the selected long-press letter, the memory chip and the selected AI action. Switching between Hav, Fjäll and Lingon changes just those few elements.
- **Swedish letters without a language key.** Long-pressing "a" shows å and ä, with ä pre-selected after "J" because "Järntorget" is a known place name. The compact å ä ö row is off by default and centred, so the QWERTY keys don't move. Autocorrect fixes "teh" and keeps "teh" in the first suggestion slot to undo, but never touches Swedish place names.
- **Visible trust.**
  - The private memory chip is the only chip in the accent colour, and it has a lock icon.
  - The NordPass and code chips stay neutral.
  - The code's expiry ring drains and fades, and turns red under 30 seconds.
  - The AI panel shows an "On device" badge and underlines the words the rewrite changed.
  - The AI button is hidden on the sign-in screen.

You can switch key height (46, 52 or 58dp) and hide the notes under each frame from the Tweaks panel.

