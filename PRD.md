# PRD: NotifFilter Bug Fixes & UI Overlay Stabilization

## Problem & User Intent
The user reported that several bugs are affecting NotifFilter on real devices:
1. `FilterListenerService` fails to bind on Android due to `android:exported="false"` in `AndroidManifest.xml`.
2. Kept notifications (`Verdict.KEEP`) vanish immediately from the shade because `FilterListenerService.onNotificationRemoved` misinterprets the cancellation of the original notification as a dismissal and immediately cancels the newly reposted notification.
3. In `RuleEditorScreen`, changes to app filtering modes and toggles are lost if the user navigates back using the top-bar back arrow instead of scrolling to the bottom to click "Save".
4. In `FilterEngine`, unreviewed model proposals (`needsVerdict = true`) were treated as Tier 1 pinned verdicts, bypassing Whitelist and Blacklist rules. Furthermore, rule matches were being inserted as `source = USER` into `template_verdicts`, polluting the Bayesian classifier training set with synthetic data.
5. In `GlobalRulesScreen` and `HomeScreen`, the Floating Action Button (FAB) overlays and blocks the bottom cards in `LazyColumn`, preventing users from tapping delete or toggling the last rule. In `AddRuleDialog`, opening the soft keyboard covers dialog inputs and buttons because the content is not scrollable.
6. In `SettingsScreen`, the backup file picker is restricted to `"application/json"`, causing JSON files to be grayed out and unselectable on many OEM file pickers.

The intent is to autonomously fix all these bugs using the Ralph Loop with rigorous test backpressure.

---

## Mental Model & Core Abstractions

### 1. Notification Lifecycle Invariants
- **Service Binding**: `FilterListenerService` must be `android:exported="true"` (secured by `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`) so the Android OS Notification Manager can bind to it.
- **Repost Isolation**: When NotifFilter reposts a kept notification and cancels the source notification, that internal cancellation must NEVER trigger `cancelRepost()`. Reposts should only be cancelled when the source notification is removed by the source app itself or by an external system event that was not initiated by NotifFilter.

### 2. Decision Cascade & Training Purity
- **Tier 1 (Pinned User Answers)**: ONLY settled user verdicts (`source == VerdictSource.USER && !needsVerdict`) act as absolute authority over rules.
- **Tier 2 (Whitelist KEEP)**: Guaranteed safety for OTPs, deliveries, transactions.
- **Tier 3 (Blacklist HIDE)**: Deterministic marketing suppression.
- **Tier 4 (Bayesian Model)**: Operates only on unruled, unpinned templates. Rule matches must NEVER be stored as `source = USER` in `template_verdicts`, ensuring the model trains solely on actual human decisions.

### 3. UI Usability & Overlay Prevention
- **FAB Clearance**: All scrollable lists (`LazyColumn`) containing a FAB must have at least 88dp bottom content padding so the last item can be scrolled fully above the FAB.
- **Dialog Keyboard Resilience**: Dialogs containing text fields must wrap their content in a scrollable column (`Modifier.verticalScroll(rememberScrollState())`) so the soft keyboard does not obscure inputs or action buttons.
- **Auto-Save on Back**: App rule settings in `RuleEditorScreen` must be automatically saved when toggled or when navigating back.

---

## Verification & Backpressure
Command to run after every task:
- Test: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test`
- Build / Assembly: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug`

---

## Discrete Tasks

### Task 1: Fix Service Export & Re-post Lifecycle (Binding & Notification Vanishing)
- **Objective:** Fix `FilterListenerService` in `AndroidManifest.xml` (`android:exported="true"`). In `FilterListenerService.kt`, track internally cancelled keys so `onNotificationRemoved` does not cancel our active repost when NotifFilter cancels the source notification.
- **Acceptance Criteria:** `AndroidManifest.xml` has `android:exported="true"`. Internal cancellations do not dismiss reposted notifications. Tests pass.
- **Target Files:**
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/java/com/yk/notiffilter/service/FilterListenerService.kt`
  - `app/src/test/java/com/yk/notiffilter/FilterEngineTest.kt`

### Task 2: Fix Decision Cascade & Training Set Cleanliness in FilterEngine
- **Objective:** In `FilterEngine.kt`, restrict Tier 1 to settled user verdicts (`source == USER && !needsVerdict`). Ensure Tier 2 (Whitelist) and Tier 3 (Blacklist) rules take precedence over unreviewed proposals. Do not insert synthetic `source = USER` rows into `template_verdicts` when rules match.
- **Acceptance Criteria:** Unreviewed model proposals never bypass rules. Rule matches do not pollute `template_verdicts` training set. Tests pass.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/service/FilterEngine.kt`
  - `app/src/test/java/com/yk/notiffilter/FilterEngineTest.kt`

### Task 3: Fix RuleEditorScreen State Persistence & Auto-Save
- **Objective:** In `RuleEditorScreen.kt`, auto-save toggles (`enabled`, `repostKeepers`, `mode`) directly or ensure that exiting via the top bar back arrow (`onBack()`) preserves and saves the current configuration into the database.
- **Acceptance Criteria:** Changes to an app's rule settings are saved immediately upon toggling or navigating back.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/ui/screens/RuleEditorScreen.kt`

### Task 4: Fix UI Overlays (FAB Overlap, Dialog Soft-Keyboard, Navigation Insets)
- **Objective:** Add bottom content padding (`contentPadding = PaddingValues(bottom = 88.dp)`) to `LazyColumn` in `GlobalRulesScreen.kt` and `HomeScreen.kt` so the FAB never covers the bottom items. Wrap `AddRuleDialog` content in a vertical scroll container so the soft keyboard does not hide fields or buttons.
- **Acceptance Criteria:** All items in `GlobalRulesScreen` and `HomeScreen` can be scrolled fully above the FAB. `AddRuleDialog` is scrollable when keyboard is visible.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/ui/screens/GlobalRulesScreen.kt`
  - `app/src/main/java/com/yk/notiffilter/ui/screens/HomeScreen.kt`
  - `app/src/main/java/com/yk/notiffilter/ui/screens/RuleEditorScreen.kt`

### Task 5: Fix Backup Picker MIME Compatibility & Locale Formatting
- **Objective:** In `SettingsScreen.kt`, update `importLauncher.launch` to support `*/*` so file managers do not gray out JSON files. Fix locale formatting in `NaiveBayesClassifier.kt` and `LogScreen.kt`.
- **Acceptance Criteria:** File picker accepts all backup files without graying out. Default locale formatting warnings resolved. Tests pass.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/ui/screens/SettingsScreen.kt`
  - `app/src/main/java/com/yk/notiffilter/ui/screens/LogScreen.kt`
  - `app/src/main/java/com/yk/notiffilter/classify/NaiveBayesClassifier.kt`

### Task 6: Full Verification, Documentation & APK Assembly
- **Objective:** Run full test suite, build updated debug APK, update `README.md`, and complete `progress.txt` with `RALPH_COMPLETE`.
- **Acceptance Criteria:** All tests pass, APK compiled at `notiffilter-debug.apk`, git committed.
- **Target Files:**
  - `README.md`
  - `progress.txt`
  - `notiffilter-debug.apk`
