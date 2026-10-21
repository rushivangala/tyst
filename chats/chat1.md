# # Pixel Keyboard Product Spec

## 1. Positioning & USP
**Positioning:** The only Android keyboard that learns your voice locally

_Started 2026-10-20 12:16 UTC_

---

## User

You are a senior product strategist for mobile keyboards and
on-device AI.

PRODUCT BRIEF
An intelligent keyboard for Android 17, optimized for the Pixel 10 Pro.
English is the primary language. Swedish words are supported too:
the keyboard recognizes Swedish words, names and places (å/ä/ö
included) inside English typing, without switching languages, and
never "corrects" them into English. All intelligence runs on-device;
no typed text leaves the phone.

USP: "The keyboard that writes like you and never goes online."
- Learns the user's personal style locally (vocabulary, tone per app
  or contact, emoji habits), so suggestions and rewrites sound like
  the user, not like generic AI
- Private memory: recalls things the user typed before (addresses,
  booking numbers, names) when relevant, encrypted on-device
- Verifiable privacy: the keyboard has no internet permission

Core AI features: smart prediction and autocorrect, rewrite in my
style (tone/length), proofread, smart replies, on-device voice
typing, inline translate, summarize pasted text.
Passwords and codes: one-tap NordPass (or any password manager)
suggestions via Android's inline autofill, and verification codes
from the clipboard or autofill shown as a chip. The keyboard never
reads SMS, email or notifications and never stores secrets.
Design: modern Material 3 Expressive, light/dark/system mode,
dynamic and curated accent colors with optional auto-rotation.

Turn this brief into a product spec I can hand to a designer:
1. Positioning and USP, one sentence each.
2. Three personas with their top 3 typing pains (include one who
   often types Swedish names, places and words in English text).
3. Gap analysis vs. Gboard, SwiftKey, Samsung Keyboard and the
   Grammarly keyboard: why would someone switch?
4. MVP features (max 10) vs. later, each tied to a persona pain.
5. Every screen the app needs, with its purpose in one line.
6. Core flows as numbered steps: first-time setup, typing with
   predictions, rewriting "in my style", using private memory, logging in with a
   NordPass suggestion, entering a verification code,
   changing theme and accent, reviewing what the keyboard learned.
7. Platform constraints (what an Android IME can see, on-device
   model limits, autofill and OTP restrictions, Gemini Nano access for third-party apps) and how
   the design handles them.
8. Success metrics with targets.

State assumptions explicitly. Be concise and concrete; no filler.

## Assistant

_[tool: list_files]_

_[tool: web_search]_

_[tool: read_skill_prompt]_

_[tool: web_search]_

_[tool: copy_starter_component]_

_[tool: dc_write]_

_[tool: ready_for_verification]_

I've written the spec as a printable document, **Keyboard Product Spec**. It covers all eight sections, with the assumptions listed at the top. It's being checked in the background now.

The finding that changes the architecture most: Gemini Nano probably won't work for a keyboard. Google's documentation says GenAI API inference is permitted only when the app is the top foreground application. Using the API when the app is not in the foreground, including using a foreground service, will result in an ErrorCode.BACKGROUND_USE_BLOCKED response. A keyboard always runs on top of another app, so it is never the foreground app. The spec therefore plans to ship the app's own small writing model, downloaded through Play so the app still needs no internet permission. Nano is only an optional extra inside the app's own Settings screens.

Other things to know:
- **Smart replies and per-contact tone are in "later".** A keyboard can't see incoming messages or who the user is writing to, so the MVP sets tone per app instead.
- **No usage analytics.** With no internet permission, the metrics come from Play Console, local counters that users can choose to share as a file, and user studies.
- **Competitor notes need checking.** I wrote them from what I know of each product, not from fresh research. Verify them before using them outside the team.
- **"Tyst" is a placeholder name** for the app.
- **Styling is my own.** The attached design system project is empty, so I picked a simple look for the document. Tell me if you want a different one.

