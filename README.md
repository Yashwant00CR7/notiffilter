# NotifFilter

An Android notification filter that uses an LLM to decide, per app, which
notifications are worth showing you — and which are marketing noise.

The motivating case: Swiggy sends a stream of coupons and restaurant ads with a
handful of things you actually need mixed in ("Order Delivered"). Keyword rules
can't separate those reliably, because marketing copy is rewritten constantly
while the messages you care about are few and stable.

## How it works

Every posted notification hits a `NotificationListenerService`:

1. **Not opted in → untouched.** Apps without a rule are never inspected, never
   classified, and never sent anywhere. OTPs, banking, and messages stay private
   by construction.
2. **Fingerprint.** `package + channelId + normalize(title) + normalize(text)`,
   with digits, URLs, punctuation and emoji stripped. Notification text is
   heavily templated, so a handful of fingerprints covers an app's whole output.
3. **Cache hit** (the overwhelming majority after warm-up) → verdict in
   milliseconds, no network.
4. **Cache miss** → the notification is **left on screen** while the model is
   consulted in the background.
5. `HIDE` → `cancelNotification()`. `KEEP` → re-posted on our own
   high-importance channel, carrying the original's `contentIntent`.

### Optimistic show

A notification stays visible until something *positively* decides to hide it.
Offline, no API key, rate-limited, service down, process killed mid-flight — all
degrade to "you see the notification", which is the behaviour without this app
installed. An ad you glimpse for a second is a far cheaper error than an "Order
Delivered" you never see.

### Why keepers get re-posted

Filtering happens *after* the phone has already buzzed, so hiding junk on its own
only tidies the shade — it does nothing about the interruptions. The fix is two
halves:

- You set the source app's channels to **Silent**, once, in Android Settings.
  There is no API for one app to change another's channel importance, so this
  step is necessarily manual.
- NotifFilter supplies the sound, for keepers only.

The re-post is posted *before* the original is cancelled. If it fails for any
reason, the silent original is still in the shade — a failure costs a buzz, never
a message.

### Corrections

Every notification from an opted-in app is logged with its verdict, the model's
reason, and its fingerprint. Tapping "wrong" in the log pins the opposite verdict
for that fingerprint permanently, with no API call.

This means the verdict cache is really a **user-editable rule table that the
model merely bootstraps**. A pinned row is never re-classified, so the same
mistake cannot recur — and it simultaneously becomes a few-shot example handed to
the model on future calls, which is what lets it generalise to templates it has
never seen.

## Design decisions worth knowing

**Conservative fingerprinting.** Over-splitting costs one extra free API call.
Over-merging silently applies the wrong verdict and is nearly impossible to
notice. Those errors are wildly asymmetric, so normalisation deliberately errs
towards treating things as distinct — proper nouns aren't stripped, for example.

**Groq over Gemini.** Gemini's free tier is explicitly licensed for training and
human review; Groq doesn't retain inference data by default. For a service that
reads notification text, that difference decides it.

**Model id lives in config, not code.** Groq's free model lineup churns, and a
retired id shouldn't require a new build.

**Key entered at runtime, not in `BuildConfig`.** The real argument isn't
cryptographic strength on an unrooted personal phone — it's that a key which was
never in the build can never be committed by accident. Stored encrypted under an
AndroidKeystore AES-GCM key, rolled by hand because `androidx.security-crypto` is
deprecated.

## Setup

1. Install, then **Settings → Groq API key** (free key from console.groq.com).
2. Grant **notification access** when prompted.
3. Exempt the app from **battery optimisation**, and enable **Auto-start** in
   realme/ColorOS settings — ColorOS kills bound services aggressively, and a
   dead listener means nothing gets filtered.
4. Add an app, write its instruction in plain English, and follow the in-app
   prompt to set that app's channels to Silent.

## Build

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew test                 # unit tests
```

Needs JDK 17 (`/opt/homebrew/opt/openjdk@17`) and Android SDK platform 35.

## Not built yet

**Periodic prose refinement** — a pass that reads the accumulated corrections and
proposes edits to an app's written instruction, which you approve or reject.
Deliberately deferred: it has to be designed against real correction data, and
that doesn't exist until the app has been in use for a few weeks.
