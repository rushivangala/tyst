# # UX Writing Brief Stage 5

_Started 2026-10-20 21:02 UTC_

---

## User

You are a UX writer for a modern, privacy-first tech product.

[ATTACH: screen list or screenshots from Stage 5]

Write all UI copy for the app in English. Tone: warm, short,
direct, no jargon, never overclaiming about AI or privacy.

Deliver a table: string key | screen | text | max length note.

Also write demo content for the prototype:
1. 15 realistic typing scenarios (work chat, family chat, email,
   notes), each with the next-word suggestions the keyboard should
   show. Include 4 where Swedish names, places or words appear in
   English text and are recognized, not corrected.
2. 10 autocorrect cases: common English typos, plus cases where a
   Swedish word must be left alone (e.g. "fika", "Liseberg").
3. "Rewrite in my style": one neutral message rewritten for 3
   different personal styles (brief and direct, warm and chatty,
   formal), to show the feature in the prototype.
4. 5 private-memory examples (trigger phrase → suggested item).
5. Copy for the code chip, password chips and the Passwords &
   codes settings screen.
6. App store short description and 3 taglines.

## Assistant

_[tool: list_files]_

_[tool: grep]_

_[tool: grep]_

_[tool: grep]_

_[tool: read_file]_

_[tool: dc_write]_

_[tool: ready_for_verification]_

I've written all the copy and demo content in one document, `Tyst UX Copy.dc.html`. I haven't checked how it renders yet. You'll need to confirm the Swedish words and the few details below.

It has eight sections:
1. UI strings for every screen (1.1–3.5), as a table with string key, screen, text and max length. Strings already in your screens are kept verbatim. The ones the screens still lack (dialogs, errors, empty states, voice permission) are tagged NEW.
2. 15 typing scenarios, with next-word suggestions. Scenarios 12–15 have Swedish names, places or words inside English text, and are recognized rather than corrected.
3. 10 autocorrect cases. "fika" and "Liseberg" are left alone. "Vasteras" is only suggested as "Västerås".
4. "Rewrite in my style": one neutral message in three styles, each tied to a habit already on the My style screen.
5. 5 private-memory examples, each a trigger phrase and the item it suggests.
6. Copy for the code chip, the password chips and the Passwords & codes screen.
7. App store short description (68 of 80 characters) and 3 taglines.
8. A short list of wording to check, including one fix to the screen 2.7 note.

- **Screen 2.7 note:** I changed it. "Tyst never reads your messages" claims more than a keyboard can promise. It now says: "Tyst doesn't save passwords. They come from {manager} through Android. A code only appears after you copy it."
- **Max lengths:** These are my layout estimates, not measured against your screens.
- **Swedish examples:** Please confirm the names and places I used (Åsa, Liseberg, Järntorget, Västerås).
- **Memory examples:** The address, phone number and email are made-up placeholders.

