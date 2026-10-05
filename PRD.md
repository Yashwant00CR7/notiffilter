# PRD: NotifFilter Optimization & Hybrid Rule-Classifier Engine

## Problem & User Intent
In the current implementation of NotifFilter, marketing spam frequently slips through into the notification shade. Because marketing copy is constantly reworded, template fingerprints over-split into new unclassified templates. At the same time, the Naive Bayes classifier is held back by high autonomy barriers (`margin = 4.0`, `minExamples = 8`), leaving unreviewed promotional messages sitting untouched in the user's shade.

The user's vision is an **optimized hybrid system** that:
1. Keeps the notification filter strictly aligned with their interests by eliminating marketing spam.
2. Integrates deterministic **Rule Matching (Whitelist KEEP & Blacklist HIDE)** alongside the statistical Naive Bayes classifier.
3. Prioritizes safety so transactional alerts (OTPs, delivery tracking, banking alerts) can never be accidentally hidden.
4. Tunes classifier thresholds so the on-device AI learns rapidly after fewer user interactions.
5. Preserves existing session data and guarantees data portability via an in-app **Backup/Export & Import (JSON)** feature in Settings, with seamless Room migration (`MIGRATION_2_3`).

---

## Mental Model & Core Abstractions

### 1. Decision Hierarchy
Every incoming notification is evaluated through a strict 4-tier decision cascade:
1. **Tier 1: Pinned Template Overrides** (`TemplateVerdict` with `source = USER`): If the user has manually answered this exact template in the UI, that verdict is absolute authority.
2. **Tier 2: Whitelist KEEP Rules** (Global & Per-App): If the notification matches a Whitelist keyword/regex/channel pattern (e.g. OTP, order status, courier arrival, payment confirmation), it is guaranteed to be KEPT immediately (re-posted loudly and original cancelled).
3. **Tier 3: Blacklist HIDE Rules** (Global & Per-App): If the notification matches a Blacklist keyword/regex/channel pattern (e.g. `% off`, `cashback`, `flat ₹`, `deal of the day`, promotional channels), it is guaranteed to be HIDDEN immediately (cancelled silently).
4. **Tier 4: Tuned Bayesian Classifier**: For notifications that do not match explicit rules or pinned templates, the on-device classifier scores features. With responsive thresholds (`margin = 2.5`, `minExamples = 3`), confident decisions are acted on autonomously; uncertain ones are left untouched in the shade and queued for review.

### 2. Rule Scope & Entities
- **`NotificationRule`**: A rule can be **Global** (`packageName = null`) or **App-Specific** (`packageName = "com.x.y"`).
- **Match Targets**: `ANY` (title or text), `TITLE`, `TEXT`, or `CHANNEL`.
- **Match Types**: Substring/Keyword (case-insensitive) or Regular Expression (`isRegex = true`).

### 3. Fail-Safe Invariants
- **Optimistic Show**: If any error or uncertainty occurs, the notification is shown/kept intact.
- **Whitelist Dominance**: A Whitelist rule ALWAYS overrides a Blacklist rule and the AI classifier.
- **Zero Network**: 100% on-device operation. No network permissions in AndroidManifest.xml.
- **Synchronous DB Confirmation**: The live path reads from in-memory caches for fast evaluation, but persists the decision to the Room database before applying cancellation or loud reposting.

---

## Architectural Decisions & Constraints
- **Room Migration (`version = 3`)**: Add `notification_rules` table via `MIGRATION_2_3` without dropping existing tables or losing user verdicts.
- **In-Memory Cache**: Active rules and enabled apps are cached in-memory in the repository layer to eliminate cold disk queries on the live path.
- **JSON Backup & Restore**: A robust, zero-dependency serialization layer (`BackupManager`) that exports and imports `app_rules`, `template_verdicts`, and `notification_rules` to a standard JSON format.
- **Default Presets**: Pre-seed global and app-specific default rules for popular consumer/delivery apps (Swiggy, Zomato, Blinkit, Zepto, Uber, Ola) and transactional alerts.

---

## Verification & Backpressure
Command to run after every task:
- Test: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test`
- Build / Assembly: `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug`

---

## Discrete Tasks

### Task 1: Database Schema Expansion & Migration 2->3
- **Objective:** Create `NotificationRule` entity and `NotificationRuleDao`, update `NotifFilterDatabase` to version 3, and implement `MIGRATION_2_3`.
- **Acceptance Criteria:** Room schema compiles, `MIGRATION_2_3` creates `notification_rules` table with indices, and `JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test` passes.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/data/Entities.kt`
  - `app/src/main/java/com/yk/notiffilter/data/Daos.kt`
  - `app/src/main/java/com/yk/notiffilter/data/NotifFilterDatabase.kt`

### Task 2: RuleMatcher Engine & Pre-seeded Default Rules
- **Objective:** Implement `RuleMatcher` (supporting Whitelist priority, Blacklist matching, regex and case-insensitive keyword evaluation) and `DefaultRules` containing standard consumer & delivery app presets.
- **Acceptance Criteria:** Unit tests in `RuleMatcherTest` verify Whitelist priority, Blacklist catches, case-insensitivity, channel matching, and regex support.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/rule/RuleMatcher.kt`
  - `app/src/main/java/com/yk/notiffilter/rule/DefaultRules.kt`
  - `app/src/test/java/com/yk/notiffilter/RuleMatcherTest.kt`

### Task 3: FilterEngine Hybrid Pipeline & In-Memory Caching
- **Objective:** Update `FilterRepository` to maintain an in-memory cache of active rules, integrate `RuleMatcher` into `FilterEngine.onPosted` (Tier 1 Pinned -> Tier 2 Whitelist -> Tier 3 Blacklist -> Tier 4 Bayesian), and tune default classifier thresholds to `margin = 2.5`, `minExamples = 3`.
- **Acceptance Criteria:** `FilterEngine` applies rule matches synchronously after DB persistence, logs rule reasons accurately, and passes unit tests.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/FilterRepository.kt`
  - `app/src/main/java/com/yk/notiffilter/service/FilterEngine.kt`
  - `app/src/main/java/com/yk/notiffilter/classify/NaiveBayesClassifier.kt`
  - `app/src/main/java/com/yk/notiffilter/settings/AppSettings.kt`
  - `app/src/test/java/com/yk/notiffilter/FilterEngineTest.kt`

### Task 4: In-App Backup & Restore (JSON Export & Import)
- **Objective:** Build `BackupManager` to export/import all `AppRule`, `TemplateVerdict`, and `NotificationRule` data as structured JSON, and wire it into `SettingsScreen` with Android file picker / share intents.
- **Acceptance Criteria:** Unit tests verify complete JSON serialization and deserialization with roundtrip integrity; settings UI triggers export and import.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/data/BackupManager.kt`
  - `app/src/test/java/com/yk/notiffilter/BackupManagerTest.kt`
  - `app/src/main/java/com/yk/notiffilter/ui/screens/SettingsScreen.kt`

### Task 5: Rule Management UI (App Rule Editor & Global Rules)
- **Objective:** Build `GlobalRulesScreen` to list and add/edit/delete global rules, and add a custom rules section inside `RuleEditorScreen` for per-app keyword/channel rules.
- **Acceptance Criteria:** Users can view, toggle, add, and remove both global and per-app rules directly in Compose UI; navigation links operate properly.
- **Target Files:**
  - `app/src/main/java/com/yk/notiffilter/ui/screens/GlobalRulesScreen.kt`
  - `app/src/main/java/com/yk/notiffilter/ui/screens/RuleEditorScreen.kt`
  - `app/src/main/java/com/yk/notiffilter/ui/MainActivity.kt`

### Task 6: Full Verification, Documentation & APK Assembly
- **Objective:** Run full test suite, assemble debug APK, and update README with documentation for the hybrid rule engine and backup/restore features.
- **Acceptance Criteria:** `./gradlew test` passes 100%, `./gradlew assembleDebug` builds `app-debug.apk`, and git commits follow local personal identity rules.
- **Target Files:**
  - `README.md`
