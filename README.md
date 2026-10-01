# NotifFilter

An Android notification filter that learns, from your own decisions, which
notifications are worth showing you and which are marketing noise.

Nothing leaves your phone. There is no API key, no account, no server — and no
`INTERNET` permission in the manifest, so the operating system will not let this app
open a network connection even if a future version tried to.

The motivating case: Swiggy sends a stream of coupons and restaurant ads with a
handful of things you actually need mixed in ("Order Delivered"). Hand-written keyword
rules can't separate those reliably, because marketing copy is rewritten constantly
while the messages you care about are few and stable.

## How it works

Every posted notification hits a `NotificationListenerService`:

1. **Not opted in → untouched.** Apps without a rule are never inspected. OTPs,
   banking, and messages stay private by construction — and by construction twice
   over, since nothing from any app can be transmitted anywhere.
2. **Fingerprint.** `package + channelId + normalize(title) + normalize(text)`, with
   digits, URLs, punctuation and emoji stripped. Notification text is heavily
   templated, so a handful of fingerprints covers an app's whole output.
3. **Known template → your verdict, instantly.**
4. **Unseen template → classified on-device**, in under a millisecond, by a naive
   Bayes model trained on the verdicts you have already given.
5. Confident → act. **Not confident → ask you**, and show the notification meanwhile.

### The user is the authority

The model may act alone only when the evidence clears a margin — in practice, when a
template is a trivial variant of one you already ruled on ("Arriving from Empire"
after you labelled "Arriving from Meghana Foods"). Everything else is queued for you.

Queued templates are announced by a single **silent, low-priority** notification —
"4 templates need your verdict" — that leads to a bulk-label screen. An app whose
purpose is fewer interruptions does not get to interrupt you with questions.

Each answer covers every future notification with that wording *and* becomes evidence
that steers the templates the model has not seen, which is why a queue that looks long
on day three is a trickle by the end of the week.

### Observe first, filter second

A newly added app starts in **observe mode**: logged, templates collected, nothing
hidden, nothing re-posted, app left entirely loud. After a few days you clear the
queue in one sitting and switch it to active. Only then does the app tell you to set
that app's channels to Silent — silencing it while still observing would mute real
messages for no benefit.

A classifier that has been taught nothing has no business hiding anything. Observing
costs nothing because it cannot change what you see.

### Optimistic show

A notification stays visible until something *positively* decides to hide it. Dead
listener, untrained model, process killed mid-flight — all degrade to "you see the
notification", which is the behaviour without this app installed.

The decision table is deliberately short:

| Verdict | What happens |
|---|---|
| KEEP (certain, or only leaning) | Re-posted on a channel that makes a sound |
| HIDE (certain) | Cancelled |
| HIDE (only leaning) | **Left exactly as the source app posted it** — silent, intact, queued |

An unsure lean towards keep still buzzes, because a maybe-important message has to
reach a user who has set the source app to Silent. An unsure lean towards hide is
never destroyed on a guess — and it is left untouched rather than re-posted, because
our copy is lossy (see below).

### Why keepers get re-posted

Filtering happens *after* the phone has already buzzed, so hiding junk on its own only
tidies the shade. The fix is two halves: you set the source app's channels to Silent
once, in Android Settings (there is no API for one app to change another's channel
importance), and NotifFilter supplies the sound for keepers only.

The re-post is posted *before* the original is cancelled, so a failure costs a buzz and
never a message. But the copy is lossy — it carries title, body, big-text and the
tap-through intent, and drops images, inbox style, progress and inline reply. That
cost is worth paying for a decided keeper and not for a template you haven't ruled on
yet, which is why unsure-lean-hide items are left alone.

## Design decisions worth knowing

**Counting tokens, not matching keywords.** A keyword rule is authored once, static,
and decided by a single word. This weighs every feature by evidence and retrains on
every correction, so it tracks how one specific app actually writes. Marketing copy
varies its words far more than its register — imperatives, prices, urgency, second
person — which is why Bayesian spam filters outlived blocklists.

**The classifier does not reuse the fingerprinter's normalisation.** `Fingerprinter`
strips digits, punctuation and emoji, which is right for template identity and
catastrophic for classification: `%`, `₹`, `🎉`, `!` and SHOUTING are the strongest
marketing signals in the string, and they survive rewording when the words don't.
`Tokenizer` keeps them, plus `channelId` — many apps already file promos separately,
which on some apps does most of the work by itself.

**Hierarchical counts.** Token counts pooled across every opted-in app form a
background prior; an app's own labels sit on top with triple weight. A new app behaves
like the pooled model instead of a coin flip; a well-labelled app is governed by its
own vocabulary. No cold-start cliff between the two.

**A minimum before any autonomy.** Below a handful of labels the log-odds are
arithmetically large and epistemically worthless — two examples can look confident.
The model stays quiet until you have actually taught it something.

**The model is rebuilt on every classification, never stored.** Recomputing counts
from a few dozen rows takes microseconds and guarantees the model can never drift out
of step with the labels you can see and edit in the UI.

**Only your verdicts train it.** Rows the model wrote itself are cache entries, not
ground truth. Training on them would bake unreviewed guesses into every future
decision with no way to tell which ones were wrong.

**Conservative fingerprinting.** Over-splitting costs one cheap local classification.
Over-merging silently applies the wrong verdict and is nearly impossible to notice.
Normalisation errs towards treating things as distinct — proper nouns aren't stripped.
The classifier is what absorbs the resulting tail.

**Reasons name their evidence.** The log shows the raw log-odds and the features that
moved the verdict ("Hidden (6.2): off, pct, now"). A reason you can check beats a
fluent sentence you can't.

## Setup

1. Install, then grant **notification access** when prompted.
2. Exempt the app from **battery optimisation**, and enable **Auto-start** in
   realme/ColorOS settings — ColorOS kills bound services aggressively, and a dead
   listener means nothing gets filtered.
3. Add an app. It starts **observing**: nothing changes yet.
4. After a few days, clear the label queue, then **Start filtering** and follow the
   in-app prompt to set that app's channels to Silent.

## Build

```bash
./gradlew assembleDebug        # APK at app/build/outputs/apk/debug/
./gradlew test                 # unit tests: tokenizer + classifier
./gradlew connectedAndroidTest # migration test; needs a device or emulator
```

Needs JDK 17 (`/opt/homebrew/opt/openjdk@17`) and Android SDK platform 35.

## History

This app previously classified notifications by sending their text to Groq's API. That
was removed in favour of the on-device model: a service that reads your notifications
should not need you to trust anybody's retention policy, including one that happened
to be favourable. The v1 → v2 migration keeps every verdict you pinned yourself — they
are training data now — while flagging the cloud model's own guesses for your
confirmation, since nobody reviewed them.

What was lost with it: the plain-English per-app instruction, which only ever meant
something to a language model. Writing a sentence about an app was replaced by tapping
keep/hide on its actual notifications, which is both more precise and the only thing a
counting model can learn from.

## Not built yet

**Deciding from the notification shade.** Answering a queued template still means
opening the app. Action buttons on the silent nudge could resolve the most recent one
in place; the trade-off is that a button can only ever address one template, and the
queue is usually several.
