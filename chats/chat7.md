# Pixel 10 Pro keyboard prototype

_Started 2026-10-20 21:06 UTC_

---

## User

Turn the approved designs into an interactive prototype on a
Pixel 10 Pro frame. Use my design system and the copy below.

[ATTACH: Stage 6 copy table and demo content]

Must work:
1. Tapping keys types into a message field with key-press feedback.
2. The suggestion strip updates from the scripted demo scenarios;
   tapping a suggestion inserts it.
3. Long-press on a/o shows å/ä/ö; Swedish words from the demo are
   recognized and not corrected.
4. Autocorrect demo: "teh" corrects to "the" with an undo option.
5. Private memory: typing "my address is" shows the memory chip;
   tapping it inserts the address.
6. AI panel: Rewrite in my style (switch between the 3 demo
   styles), Proofread with a diff, Translate, Reply, using
   pre-written results.
7. Light/dark toggle and accent picker that restyle the whole
   keyboard live with the defined motion.
8. Login demo: tapping a password-manager chip fills username and
   password (fictional demo account); a code field shows the number
   pad and a clipboard code chip that fills on tap.
9. Onboarding and settings navigation are clickable.

This is a demo prototype: scripted data is fine, no real AI needed.
Add a small hidden reset button to restart the demo.

## Assistant

_[tool: list_files]_

_[tool: list_files]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: read_file]_

_[tool: dc_write]_

_[tool: dc_write]_

_[tool: ready_for_verification]_

I built the prototype in **Tyst Prototype.dc.html**: a Pixel 10 Pro frame with a demo panel beside it. It's out for review now, and I haven't clicked through every flow myself yet.

The panel jumps between screens, types the scripted scenarios key by key, launches each writing tool, and switches light/dark and accent. You can also type on your computer keyboard. The hidden reset button is a faint dot in the bottom-right corner of the page.

What I had to decide or make up:
- **Copy source:** the attachment placeholder in your message wasn't filled in, so I used the Stage 6 copy file (UX Copy) as the copy table and demo content.
- **AI results:** the copy file has no pre-written results for Proofread, Translate or Reply, so I wrote short ones based on the Elin meeting message. Rewrite uses your three demo styles.
- **Demo text:** the writing tools only have results for that one message. For any other text the panel shows "Demo text only" with a "Use demo text" button. That string isn't in the copy file.
- **Translate and Reply:** they work in the AI panel, but Settings still labels them "Later", as in the approved screens.
- **Private memory** starts switched on, so the "my address is" demo works without visiting Settings. Onboarding and Settings share the same switch.
- **Code chip:** codes only appear after you copy one, so the code screen shows a Messages notification with a "Copy code" button. The chip appears when you tap it.

There are three settings you can change: key height, which screen it opens on, and whether the demo panel shows.

