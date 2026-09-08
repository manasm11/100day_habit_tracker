# 100 Day Habit Tracker — Android Design Spec

**Date:** 2026-09-07
**Status:** Approved for planning
**Platform:** Native Android (Kotlin, Jetpack Compose, Room). Fully local, single user, no backend.

---

## 1. Product concept

A behaviour done daily for 100 days becomes an automatic habit. The app allows a
limited number of misses instead of resetting to zero on the first slip. The user
forms **one habit at a time**; finished habits move to a "mastered" shelf and switch
to light-touch monthly maintenance. A mastered habit that slips can run a shorter
30-day "tune-up" to lock itself back in.

This spec covers a v1 native Android reimplementation of the original product spec.
The **concept, core mechanics, screens, and design direction are unchanged** from the
original brief — only the implementation stack differs (was: Go + templ + HTMX +
Tailwind + SQLite web app).

---

## 2. Core mechanic — exact rules

These rules are the whole point of the product. There must be zero ambiguity.

### 2.1 Window and days

- **Window:** the *track length* in calendar days, counted from the attempt's start
  date. For a normal forming attempt the track length is **100**. For a tune-up it is
  **30**.
- **Day 1** is the start date. Day `N` is `startDate + (N-1) days`.
- Days advance at **local midnight in the habit's stored timezone** (IANA zone id,
  captured at habit creation). All day-boundary math uses that zone, never the
  device's current zone.
- **Current day number** = (number of whole days from `startDate` to "now" in the
  habit's zone) + 1. It can exceed the track length if the app has not been opened
  (see rollover).

### 2.2 Marking

- Each day the user marks the habit **done**. Marking is **one tap**.
- Only the **day in play** can be marked done — never an older day. Retroactive editing
  of history is **not allowed**; a finalized missed day stays missed permanently.
- **Morning grace window.** Between local midnight and **10:00** in the habit's zone,
  if the previous calendar day is still unmarked it remains the "day in play" (you can
  finish yesterday over morning coffee). At 10:00 it locks as a miss and the day in
  play becomes today. This is strictly forward-only: the day in play never goes back
  more than one calendar day, and only while that day is genuinely unfinished. Once the
  previous day is marked done, the day in play is today even before 10:00.
- **Same-day undo.** The user can un-mark the day in play (today, or the grace day)
  behind a confirmation dialog — it returns to pending and can be marked again before
  it locks. Undo never reaches a finalized past day.
- If the day in play is not marked done by the time it locks (local midnight, or 10:00
  for a grace day), it becomes a **miss** at rollover.

### 2.3 Miss budget

- Up to **10 total misses** are allowed across the track.
- The **11th miss fails** the attempt.

### 2.4 Never twice in a row (takes priority)

- **Two consecutive missed days fail the attempt immediately**, even if the miss
  budget is not used up.
- This rule is **stricter than the budget and is evaluated first**. When the engine
  walks the timeline day by day, a second consecutive miss fails the attempt at that
  day even if it is only, say, the 4th total miss.

### 2.5 Graduation

- Reaching the final day of the track (`currentDayNumber > trackLength`, or the final
  day itself marked done) having used **≤10 total misses** and **never missed two days
  in a row** → the attempt **graduates**.
- The final day may itself be a miss and the attempt still graduates, provided total
  misses ≤10 and it is not the second of two consecutive misses.
- Marking the final day done triggers graduation immediately.

### 2.6 Failure

- The attempt **fails the moment either** the 11th miss occurs **or** a second
  consecutive miss occurs — whichever comes first on the timeline.
- The engine reports the **failure reason** (`TWO_IN_A_ROW` | `BUDGET_EXCEEDED`) and
  the **day number** on which it failed.
- On failure the app offers two actions: **restart the same habit fresh** (new track,
  new window) or **abandon** it (habit stays `failed`, forming slot is freed).
  Abandoning moves the habit to status `abandoned` (excluded from all screens); the
  forming slot is already free.

### 2.7 Miss-warning (at-risk) state — the single most important nudge

- **Condition:** the attempt is still forming, and the effective status of *yesterday*
  (current day number − 1) is **missed**, and *today* (current day number) is **not
  yet marked done**.
- When true, the tracker shows a **prominent amber warning banner**: missing today
  would be two in a row and would fail the attempt.

### 2.8 One active slot

- Exactly **one** habit may be in state `forming` **or** `tuning_up` at any time
  ("the forming slot").
- New habits cannot be created, and tune-ups cannot be started, while the slot is
  occupied.
- Enforced in the repository layer (checked before any insert/transition). A DB-level
  guard (trigger or partial unique index via migration SQL) is added as a backstop.

### 2.9 Rollover (catch-up on app open / periodic)

On every app foreground (process lifecycle `ON_START`) and via a daily WorkManager
backstop, for the habit in the forming slot:

1. Compute the **day in play** in the habit's zone (§2.2 — the calendar day, or the
   grace day while its window is open).
2. For each `dayNumber` from `(highest logged day + 1)` up to
   `min(dayInPlay − 1, trackLength)` that has no `done` log, insert a **`missed`**
   `day_log` with the correct `logDate`, **in ascending order**. A day still inside
   its grace window is left pending, not materialised.
3. Re-run the rule engine over the resulting logs.
4. If the engine reports `FAILED`, set habit `status = failed` and persist the
   failure reason/day. If `GRADUATED`, set `status = mastered`, set `graduatedAt`,
   set `trophyAttempt = currentAttempt`; for a tune-up also clear the parent's
   `slipped` flag.
5. Rollover is **idempotent** — running it twice produces no additional changes.

Because WorkManager cannot guarantee exact-midnight execution under Doze, the
foreground recompute is the real guarantee and the worker is only a backstop.

### 2.10 Maintenance pulse (mastered shelf)

- Each mastered habit is **due for a check-in once per calendar month** (month
  boundary in the habit's zone). "Due" = no `maintenance_checkins` row for the
  current `YYYY-MM` period.
- A due habit shows an amber **"Check in"** badge; otherwise a green **"Going
  strong"** badge.
- Tapping a due habit asks "still doing this?": one tap **confirm** (writes a
  `strong` check-in, badge goes green) or **report a slip** (writes a `slipped`
  check-in).
- A **slip** (from a monthly check-in or self-reported at any time) sets the habit's
  `slipped` flag and offers a **30-day tune-up**: a new attempt with track length 30
  that takes the forming slot. Graduating the tune-up clears `slipped`.

---

## 3. Architecture

Single Gradle module `:app`. Manual dependency injection via an `AppContainer` held
by the `Application` subclass (no Hilt). Clean package boundaries:

```
com.manasm.habit100
├── domain/        Pure Kotlin. No Android imports. The rule engine + models.
├── data/          Room entities, DAOs, database, TypeConverters, HabitRepository.
├── rollover/      RolloverEngine (pure logic over a repo port) + RolloverWorker.
├── clock/         Clock interface, SystemClock, DevClock (+ DataStore offset).
├── ui/            Compose screens, ViewModels, theme, the HabitGrid composable.
│   ├── tracker/   ├── graduation/   ├── shelf/   ├── newhabit/   └── theme/
└── HabitApplication.kt, MainActivity.kt, AppContainer.kt
```

**Data flow:** Compose screen → ViewModel → `HabitRepository` (Room `Flow`s) →
ViewModel maps DB rows through `HabitRules.evaluate(input, clock.now())` into a UI
state → screen renders. Writes (mark done, check-in, slip, start habit) go
ViewModel → repository (suspend, transactional) → Room emits → UI recomposes.

**Navigation:** Compose Navigation. Start destination is resolved from app state:
- forming/tuning_up habit that is still `FORMING` per the engine → `tracker`
- forming habit that the engine reports `GRADUATED` (not yet acknowledged) → `graduation`
- no active habit → `tracker` shows an empty state with "Start a habit" → `newhabit`
- `shelf` is always reachable from a top-bar action.

Single `MainActivity`, Compose-only, edge-to-edge, Material 3.

---

## 4. Domain: the rule engine

Pure Kotlin, no Android dependencies, in `domain/`. This is the heart of the product
and is tested exhaustively.

```kotlin
enum class DayStatus { DONE, MISSED }
enum class HabitState { FORMING, GRADUATED, FAILED }
enum class FailureReason { TWO_IN_A_ROW, BUDGET_EXCEEDED }

data class DayLog(val dayNumber: Int, val status: DayStatus)

data class RuleInput(
    val startDate: LocalDate,      // day 1, in the habit's zone
    val zoneId: ZoneId,
    val trackLength: Int,          // 100 or 30
    val missBudget: Int = 10,      // total misses allowed
    val dayLogs: List<DayLog>,     // sparse; only days actually logged
)

data class RuleSnapshot(
    val currentDayNumber: Int,     // the DAY IN PLAY (§2.2): today, or the grace day. >= 1, may exceed trackLength
    val calendarDayNumber: Int,    // the true calendar day; == currentDayNumber except during an open grace window
    val effectiveDay: Int,         // min(currentDayNumber, trackLength)
    val doneCount: Int,
    val missCount: Int,            // includes implied misses for elapsed unmarked (finalized) days
    val missesLeft: Int,           // max(0, missBudget - missCount)
    val bestStreak: Int,           // longest run of effective DONE days
    val atRisk: Boolean,
    val state: HabitState,
    val failureReason: FailureReason?,   // non-null iff state == FAILED
    val failedOnDay: Int?,               // non-null iff state == FAILED
    val canMarkToday: Boolean,            // FORMING && day in play in track && not marked
    val todayMarkedDone: Boolean,         // the day in play is marked
    val canUndoMark: Boolean,             // undoDayNumber != null
    val undoDayNumber: Int?,              // day undo would clear: current day if marked, else grace day while open; never finalized
    val graceDeadline: Instant?,          // when the grace day locks as a miss; non-null only while a grace window is open
)

object HabitRules {
    fun evaluate(input: RuleInput, now: Instant): RuleSnapshot
}
```

### 4.1 Algorithm

1. `calendarDayNumber = daysBetween(startDate, now.atZone(zoneId).toLocalDate()) + 1`,
   floored at 1.
   `currentDayNumber` (the day in play) = `calendarDayNumber − 1` when `calendarDayNumber ≥ 2`,
   the local wall time is before **10:00**, and day `calendarDayNumber − 1` is not marked
   done; otherwise `calendarDayNumber`.
2. Build an **effective timeline** for days `1..min(currentDayNumber, trackLength)`:
   - `DONE` if a `done` log exists for that day.
   - For a **finalized past** day (`day < currentDayNumber`) with no done log → `MISSED`
     (implied; the persisted rollover will materialise these, but the engine does
     not depend on that having happened).
   - The **day in play** (`day == currentDayNumber`) with no done log → *pending*
     (not counted as a miss, not DONE). During an open grace window this is yesterday.
3. Walk the timeline in ascending day order, tracking `totalMisses` and
   `consecutiveMisses`:
   - On a `MISSED` day: `consecutiveMisses++`, `totalMisses++`.
     - If `consecutiveMisses == 2` → `FAILED(TWO_IN_A_ROW, day)`. **Stop.**
     - Else if `totalMisses > missBudget` → `FAILED(BUDGET_EXCEEDED, day)`. **Stop.**
   - On a `DONE` day: `consecutiveMisses = 0`.
4. If not failed and `currentDayNumber > trackLength` → `GRADUATED`.
   If not failed and the final day (`trackLength`) is marked done → `GRADUATED`.
   Otherwise `FORMING`.
5. `atRisk = state == FORMING && effectiveStatus(currentDayNumber - 1) == MISSED &&
   not todayMarkedDone` (and `currentDayNumber - 1 >= 1`). Measured against the day in
   play, so during grace it means "miss yesterday by 10:00 and it's two in a row".
6. `bestStreak` = longest run of `DONE` in the walked timeline (up to failure day if
   failed).
7. `undoDayNumber` = the later of {the grace day, if the window is open and it is
   marked; today, if it is marked}; `null` if neither. `canUndoMark = undoDayNumber != null`
   (and `state == FORMING`). `graceDeadline` = today's date at 10:00 local, non-null iff
   `currentDayNumber < calendarDayNumber`.

### 4.2 Edge cases the engine must handle (and test)

- Two consecutive implied misses from not opening the app → `FAILED(TWO_IN_A_ROW)` on
  the second missed day, **not** later.
- 11th miss with no two-in-a-row anywhere → `FAILED(BUDGET_EXCEEDED)` on day of 11th.
- Never-twice fires before budget: a 2nd consecutive miss that is only the 4th total
  miss still fails as `TWO_IN_A_ROW`.
- Day 100 marked done with exactly 10 misses, none consecutive → `GRADUATED`.
- Day 100 is an (implied) miss, 10 total, day 99 done → `GRADUATED` (window complete,
  not failed).
- Day 100 is an implied miss and day 99 was also a miss → `FAILED(TWO_IN_A_ROW, 100)`.
- `currentDayNumber` far past `trackLength` (app unopened for weeks) → evaluate as if
  each day rolled over in order; report the first failure or graduation.
- **Grace window (all in the habit's zone):** at 08:00 on calendar day N with day N−1
  unmarked → day in play is N−1, `graceDeadline` = day N at 10:00, N−1 is pending (not
  a miss). At 10:01 → day in play is N, N−1 has locked as a miss. Once N−1 is marked,
  day in play is N even before 10:00, and N−1 stays undoable (`undoDayNumber = N−1`)
  until 10:00. After 10:00 a marked N−1 is finalized and cannot be undone.
- **Grace at the track boundary:** day 100 (or day 30 for a tune-up) still markable at
  08:00 on the calendar day after it; missing it past 10:00 with day 99 also missed →
  `FAILED(TWO_IN_A_ROW, 100)`.
- Timezone: start date in `Pacific/Kiritimati` vs device in `Pacific/Honolulu` — day
  number computed from the habit's zone only.
- Tune-up: identical logic with `trackLength = 30`.

---

## 5. Data layer (Room)

### 5.1 Entities

**`habits`**

| column              | type            | notes |
|---------------------|-----------------|-------|
| id                  | Long PK autogen | |
| name                | String          | |
| timeZoneId          | String          | IANA zone id, set at creation |
| status              | String enum     | `forming` \| `mastered` \| `failed` \| `tuning_up` \| `abandoned` |
| currentAttempt      | Int             | starts at 1; bumped on restart / tune-up |
| attemptStartDate    | LocalDate       | day 1 of the current attempt, in `timeZoneId` |
| attemptTrackLength  | Int             | 100 for forming, 30 for tune-up |
| trophyAttempt       | Int?            | the attempt that earned mastery; null until mastered |
| slipped             | Boolean         | mastered habit currently flagged as slipped |
| createdAt           | Instant         | |
| graduatedAt         | Instant?        | set when first reaching `mastered` |
| failureReason       | String?         | last failure reason, for the restart/abandon screen |
| failedOnDay         | Int?            | |

**`day_logs`**

| column     | type            | notes |
|------------|-----------------|-------|
| id         | Long PK autogen | |
| habitId    | Long FK → habits| CASCADE delete |
| attempt    | Int             | which attempt this log belongs to |
| dayNumber  | Int             | 1..attemptTrackLength |
| logDate    | LocalDate       | date in the habit's zone |
| status     | String enum     | `done` \| `missed` |
| markedAt   | Instant         | when the row was written |

Unique index `(habitId, attempt, dayNumber)`. Index `(habitId, attempt)`.

**`maintenance_checkins`**

| column    | type            | notes |
|-----------|-----------------|-------|
| id        | Long PK autogen | |
| habitId   | Long FK → habits| CASCADE delete |
| period    | String          | `YYYY-MM` in the habit's zone |
| status    | String enum     | `strong` \| `slipped` |
| checkedAt | Instant         | |

Unique index `(habitId, period)`.

### 5.2 TypeConverters

`Instant` ↔ epoch millis (Long); `LocalDate` ↔ ISO string; enums ↔ lowercase string.
`ZoneId` is stored as a plain `String` column, not converted.

### 5.3 HabitRepository (the service seam)

Suspend functions, Room transactions where multiple writes must be atomic. Key
operations:

- `observeActiveHabit(): Flow<HabitWithLogs?>` — the forming/tuning_up habit + its
  current-attempt logs.
- `observeMasteredHabits(): Flow<List<MasteredHabitView>>` — includes trophy-attempt
  logs and current-period check-in status.
- `createHabit(name, zoneId, now)` — **fails if the forming slot is occupied**;
  inserts `habits` row `status=forming, currentAttempt=1, attemptTrackLength=100,
  attemptStartDate = today in zone`.
- `markTodayDone(habitId, now)` — validates via engine that today is markable
  (`canMarkToday`), inserts a `done` day_log for the current day, then runs the
  post-write transition (graduation check).
- `runRollover(now)` — section 2.9. Idempotent.
- `restartFailedHabit(habitId, now)` / `abandonHabit(habitId)`.
- `acknowledgeGraduation(habitId, keepGoing: Boolean)` — habit is already `mastered`
  after rollover/mark; this just controls navigation (start next vs. go to shelf).
- `checkIn(habitId, strong: Boolean, now)` — writes a `maintenance_checkins` row for
  the current period; on slip also sets `slipped = true`.
- `reportSlip(habitId, now)` — self-reported slip outside a check-in.
- `startTuneUp(habitId, now)` — **fails if the forming slot is occupied**; bumps
  `currentAttempt`, sets `status=tuning_up, attemptTrackLength=30,
  attemptStartDate=today`.

Single-active-slot invariant is asserted at the top of `createHabit` and
`startTuneUp`. Migration adds a SQLite trigger that raises on a second
`forming`/`tuning_up` row as a backstop.

---

## 6. Rollover component

`rollover/RolloverEngine` depends only on a narrow `RolloverPort` (read active habit
+ logs, insert missed day, transition status) so it is unit-testable with a fake.
Invoked from:

- `HabitApplication` registering a `DefaultLifecycleObserver` on
  `ProcessLifecycleOwner` → `onStart` launches `runRollover(clock.now())`.
- `RolloverWorker` (`CoroutineWorker`), scheduled as a ~daily `PeriodicWorkRequest`
  with a backoff, as a backstop for when the app is not opened across a midnight.

---

## 7. Clock and dev override

```kotlin
interface Clock { fun now(): Instant }
class SystemClock : Clock
class DevClock(private val store: DevClockStore) : Clock   // now() = real + offset
```

`DevClockStore` persists a `Duration` offset (and/or a pinned date) in DataStore.
`AppContainer` provides `DevClock` only when `BuildConfig.DEBUG`, else `SystemClock`.
In debug builds the tracker shows a small dev panel: **"Set date"** and **"+1 day"**,
which adjust the offset. Advancing the dev clock and re-foregrounding runs the same
rollover path, so every mechanic (rollover, at-risk, graduation, monthly pulse) is
exercisable immediately.

---

## 8. UI screens (Compose, Material 3)

Mobile-first, single column, flat: minimal elevation, **no gradients, no heavy
shadows**, generous whitespace. One tap wherever possible. Calm, not gamified.

### 8.1 Theme / colors

- done `#1D9E75` · missed `#E24B4A` · amber (at-risk / check-in due) `#F4A825`
- future/neutral cell: a dim grey derived from the surface
- Material 3 light + dark schemes; `today` cell = outlined (2dp stroke, no fill).

### 8.2 `HabitGrid` composable (the signature element)

A `Canvas`-drawn **10×10 grid** (100 rounded-rect cells, 10 per row, small gap).
Parameters: list of 100 cell states (`DONE` / `MISSED` / `TODAY` / `FUTURE`) and a
size mode (`HERO` on the tracker, `TROPHY` on graduation, `THUMBNAIL` on the shelf).
It is the calendar and the progress/percentage visual in one. No per-cell tap
handling.

### 8.3 Daily tracker (home when a habit is forming)

- Habit name.
- Headline **"Day N of 100"** (or "of 30" for a tune-up).
- Two stat cards: **Completed** (done count / trackLength) and **Misses left**
  (missesLeft / 10).
- `HabitGrid` in `HERO` mode.
- **Amber miss-warning banner** when `snapshot.atRisk` — prominent, above the grid.
- Large **"Mark today done"** button; disabled with an explanatory caption when
  `!canMarkToday` (already done today / not a markable day).
- Legend: done / missed / today.
- Empty state (no active habit): short copy + **"Start a habit"** → new-habit flow.
- Debug-only dev clock panel.

### 8.4 New-habit flow

Single screen: text field for the habit name, a note that the habit's timezone will
be the device's current zone (`ZoneId.systemDefault()`) captured now, and a
**"Start 100 days"** button. Blocked (with explanation) if the forming slot is
occupied. On success → tracker at Day 1.

### 8.5 Graduation screen (engine reports GRADUATED, not yet acknowledged)

- Success check mark + "100 days complete".
- Headline **"It's a habit now"** + habit name.
- `HabitGrid` in `TROPHY` mode — greens with the honest red misses preserved.
- Three stats: **Days done**, **Best streak**, **Misses used**.
- **"Start your next habit"** → new-habit flow (slot is now free).
- **"Keep this one going"** → mastered shelf.
- Subtle **share** affordance: renders the trophy grid + caption to a bitmap and
  fires a `Sharesheet` `ACTION_SEND` image intent.

### 8.6 Mastered shelf

- Header **"Mastered"** + count; subline "Tap a habit to check in this month".
- One row per mastered habit: `HabitGrid` `THUMBNAIL` of `trophyAttempt`, habit name,
  "graduated {date}" / current maintenance streak, and a status badge:
  - due this month → amber **"Check in"**
  - otherwise → green **"Going strong"**
  - `slipped` → a distinct **"Slipped"** treatment with a **"Start 30-day tune-up"**
    action (disabled if the forming slot is occupied).
- Tapping a due habit → inline "still doing this?" with **Confirm** / **Report a
  slip**.
- Any mastered habit row has an overflow **"I slipped"** self-report action.
- Separated **"Forming now"** footer: the single active habit — name, "Day N of
  100" (or 30), misses left, a thin horizontal progress bar (`effectiveDay /
  trackLength`). Shows "No habit forming — start one" when the slot is empty. This
  visually enforces the one-at-a-time rule.

---

## 9. Testing strategy

### 9.1 Pure JVM unit tests (`src/test/`, JUnit4) — run in this environment

- **`HabitRules`** — the bulk of the effort. A table-driven suite covering every
  edge case in §4.2 plus: at-risk true/false transitions, `canMarkToday` states,
  best-streak with and without failure, day-number math across DST and across the
  international date line, tune-up (trackLength 30), missBudget boundary exactly at
  10 vs 11.
- **`RolloverEngine`** — with a fake `RolloverPort`: fills N elapsed days in order;
  stops materialising at the failure day; idempotent on re-run; graduation
  transition; tune-up graduation clears parent `slipped`.
- **Period / month-boundary helper** for the maintenance pulse.

### 9.2 Robolectric tests (`src/test/`, run on JVM — no device needed)

- Room DAO round-trips + unique-index enforcement on `day_logs`.
- `HabitRepository` single-active-slot: `createHabit` / `startTuneUp` throw when the
  slot is occupied; DB trigger backstop rejects a direct second active insert.
- Key ViewModel → UI-state mappings: tracker state produces the amber banner when
  at-risk; graduation state routes to the graduation screen.

### 9.3 Not run in this environment

- Instrumented (`androidTest/`) tests and running the app on a device/emulator —
  none available here. A few `androidTest` Compose UI tests are written for the
  record (mark-done updates the grid; "Mark today done" disabled after marking) but
  are not executed as part of the delivered verification.

### 9.4 Delivered verification

- `./gradlew :app:testDebugUnitTest` green (JVM + Robolectric).
- `./gradlew :app:assembleDebug` produces `app/build/outputs/apk/debug/app-debug.apk`.
- `./gradlew :app:lintDebug` clean of errors.

---

## 10. Toolchain bootstrap (no root, into home dir)

Nothing is installed on the machine. A `scripts/bootstrap-toolchain.sh` performs:

1. Download Temurin **JDK 17** (tar.gz) → `~/.local/jdk17`.
2. Download Android **command-line tools** → `~/Android/Sdk/cmdline-tools/latest`;
   `sdkmanager --licenses` (auto-accept); install `platform-tools`,
   `platforms;android-34`, `build-tools;34.0.0`.
3. Download **Gradle 8.7** → `~/.local/gradle`; run `gradle wrapper
   --gradle-version 8.7` once in the project to generate `gradlew` + wrapper jar
   (committed thereafter).
4. Write `local.properties` with `sdk.dir=~/Android/Sdk` (gitignored) and document
   the required `JAVA_HOME` / `ANDROID_HOME` exports (also captured in the script and
   a `.envrc`-style note).

Versions: AGP 8.5.x, Kotlin 2.0.x, Compose BOM 2024.09.x, Room 2.6.x, KSP for Room,
WorkManager 2.9.x, DataStore 1.1.x, Robolectric 4.13, JUnit4 (`junit:junit:4.13.2`
with `androidx.test.ext:junit` for Robolectric). Exact versions are pinned in the
version catalog at scaffold time and recorded in the stage-1 checkpoint.

---

## 11. Build order (delivery, with checkpoints)

1. **Scaffold + data model.** Toolchain bootstrap; Gradle project; `AppContainer`;
   Room schema + migrations + TypeConverters; `HabitRepository` with the
   single-active-slot rule and its tests. `assembleDebug` + `testDebugUnitTest`
   green. → **checkpoint**
2. **Rule engine.** `domain/HabitRules` + the exhaustive test suite (§9.1). →
   **checkpoint**
3. **New-habit flow + daily tracker end to end.** Compose tracker screen, `HabitGrid`,
   mark-done, amber miss-warning, legend, empty state, dev clock panel. Installable
   APK that works day-to-day. → **checkpoint**
4. **Rollover.** Foreground observer + `RolloverWorker`; failure evaluation on
   catch-up.
5. **Graduation screen + transitions.** Start-next / keep-going; subtle share.
6. **Mastered shelf + maintenance pulse + slip → 30-day tune-up.**
7. **Polish.** Empty states, "Forming now" footer, dark theme pass, share polish,
   lint clean.

---

## 12. Non-goals for v1

- No weekly or custom-cadence habits — daily only. The data model leaves room for a
  future habit `type`/cadence but no non-daily logic is built.
- No multi-user accounts, auth, social features, or notifications infrastructure
  beyond the in-app maintenance pulse. (An auth seam is not needed for a local
  single-user app; the repository layer is the seam if it is ever added.)
- No cloud sync or backup. Data is on-device only.
- No more than one forming habit at a time.
- No widget, no Wear app, no tablet-optimised layout.
