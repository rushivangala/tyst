# Keyboard app wireframe flows

_Started 2026-10-20 12:31 UTC_

---

## User

Using the attached spec, create low-fidelity wireframes (greyscale,
no final styling) on a Pixel 10 Pro frame.


Wireframe these flows, one row of screens per flow, with arrows:
1. First-time setup: welcome → enable keyboard → select keyboard →
   privacy explainer → pick theme → done (max 5 screens).
2. Typing: keyboard in a chat app with the suggestion strip, an
   English prediction, and a Swedish place name recognized
   without being corrected.
3. AI panel: select text → open panel → rewrite in my style /
   proofread / translate / reply → preview → insert.
4. Private memory: typing "my address is" → memory chip offers
   the saved address → tap to insert.
5. Settings: home, typing & dictionaries (English + Swedish words),
   AI features, my style, privacy & memory, appearance, about.
6. Login: password field → NordPass suggestion chips in the strip
   → tap to fill. Then code field → number pad + code chip from the
   clipboard → tap to fill.
7. Learned data manager: words, phrases and memory items, with
   search, filters and delete.

Give me 3 layout variants for the keyboard + suggestion strip only,
labelled A/B/C, with one line on the trade-off of each.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: dc_write]_

_[tool: ready_for_verification]_

I built the seven flows and the A/B/C keyboard variants in `Tyst Wireframes.dc.html`. It's greyscale, drawn on Pixel 10 Pro frames, with one row of screens per flow and arrows between them. I haven't looked at it yet, so layout and any console errors are unchecked; a background check is running and will report only if it finds problems.

- **Setup:** it has 5 screens. I merged "enable keyboard" and "select keyboard" into one two-step screen to stay inside your maximum of five.
- **Translate and Reply:** these are in the AI panel flow, but the spec lists them as later features. They're marked "later" and sit in a second row of alternate previews under the main rewrite → preview → insert row. Proofread is in that row too.
- **Settings and learned data:** their screens aren't linked by arrows, since they're list screens rather than a sequence.
- **Learned data manager:** it has 5 screens: words, phrases, memory, delete one item, and delete everything.
- **Variants A/B/C:** each has a full keyboard in a chat, then the strip states: predictions, memory chip, autofill pill, code chip and incognito. A and C also show the AI toolbar open. The trade-off for each is one line under its heading.
  - A is a single strip.
  - B is a strip with a tool row above it.
  - C is a strip with a context lane that adds a row when a chip appears.

The strip text and tool labels are plain text boxes, not final icons.

## User

Found issues — fixing…

## Assistant

_[tool: run_script]_

_[tool: ready_for_verification]_

I fixed the issues the check found, and the file now opens with no console errors. I haven't re-checked the screens visually.

- **Clipped labels:** the tool buttons now wrap onto two lines. "Translate" and "Reply" read "(later)" instead of "· later". The step 3.1 strip uses shorter labels: "Rewrite", "Check" and "More".
- **Variant headings:** the doubled letter is gone, so they read "A  Single strip" and so on.
- **Console warning:** every block now has a default empty `s`, which clears it.

