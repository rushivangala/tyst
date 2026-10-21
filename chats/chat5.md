# Keyboard app high-fidelity screens

_Started 2026-10-20 20:49 UTC_

---

## User

Using my design system and the approved keyboard design, create
high-fidelity versions of every remaining screen from the wireframes.
Match the keyboard's style exactly. Light and dark for each.

1. Onboarding (max 5 screens), including a plain-language privacy
   screen: "Everything you type stays on this phone."
2. Settings home + sub-screens: Typing & dictionaries (English main,
   Swedish words on/off), AI features, My style, Privacy & memory,
   Appearance, About.
3. My style: what the keyboard has learned about how the user
   writes (e.g. "short sentences", "casual with friends", common
   phrases), with the option to edit, pause or reset.
4. Privacy & memory: list of remembered items, search, swipe to
   delete, pause memory, "Delete all learned data" with confirmation.
5. Appearance / theme editor: mode selector (light/dark/system),
   dynamic color toggle, curated accent swatches, auto-rotate
   accents option, and a live keyboard preview that updates.
6. Passwords & codes: autofill chips on/off, a link to the system
   autofill setting to choose NordPass, clipboard code chips on/off,
   code expiry time, and a plain note that the keyboard never reads
   messages or stores passwords.
7. AI features screen: a toggle per feature with a one-line
   explanation, and model storage used.
8. Empty, loading and error states for the AI panel (model still
   downloading, low battery mode, feature not available).

Use placeholder copy for now; final copy comes later.
Flag any place where the design system was missing something.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: update_todos]_

_[tool: dc_write]_

_[tool: show_html]_

_[tool: ready_for_verification]_

I've built all the remaining screens in **Tyst Screens Hi-fi.dc.html**, each shown in light and dark. The background check on layout and errors is still running.

- **Onboarding (1.1–1.5):** Welcome, Turn on Tyst, Privacy (your line, word for word, as the headline), Pick a look, and Ready. Ready has a practice field, and a ring around the AI key shows the model download.
- **Settings (2.1–2.8):** Home, Typing & dictionaries, AI features, My style, a My style edit sheet, Privacy & memory, the Delete all confirmation, Appearance, Passwords & codes and About.
- **AI panel states (3.1–3.5):** Nothing selected, Writing, Model still downloading, Battery Saver on, and Translate not available yet.

Most controls work in the preview:
- **Switches and selectors:** the toggles, the light/dark/system selector and the accent swatches.
- **Live preview:** the keyboard preview on Appearance (2.6) and Pick a look (1.4) updates as you change settings, and the accent you pick there recolours every screen.
- **Memory list:** the search filters as you type. Tapping a row stands in for swipe-to-delete, and deleting gives you Undo.
- **Delete all:** the button opens the confirmation, and confirming clears the list.

The design system project you attached is empty, so I worked from the Tyst Keyboard Design System file in this project. The places where it was missing something are listed at the top of the page, with links to the screens affected. The main ones:
- buttons
- a top app bar
- segmented buttons
- search
- swipe rows and an "on error" colour
- dialog and sheet surfaces with a dim overlay
- snackbar colours
- progress bars
- empty states
- a warning colour
- page dots
- an app icon
- a wallpaper sample for dynamic colour

I filled each gap with the closest existing tokens; the list notes what I used.

There's a setting to show light only, dark only or both, and another to hide the notes under each screen.

## User

Found issues — fixing…

## Assistant

_[tool: dc_html_str_replace]_

_[tool: dc_html_str_replace]_

_[tool: ready_for_verification]_

I fixed a layout bug in the AI panel screens (3.1–3.5). The four tool chips were wrapping onto two lines, which pushed the buttons out of the panel and over the bottom navigation handle. The chips now sit on a single row that scrolls sideways and fades at the edge, so the buttons fit back inside. A second check is running now.

