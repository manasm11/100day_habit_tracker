# 100 Day Habit Tracker

Native Android (Kotlin + Jetpack Compose + Room). One habit at a time, 100 days.

## The mechanic

- **One active habit.** You form a single habit at a time — no second slot until the
  current one graduates, fails, or is abandoned.
- **100 days.** Mark each day done on the day itself.
- **10-miss budget, never two in a row.** You can miss up to 10 days across the attempt,
  but two consecutive misses end the attempt. The "two in a row" rule is checked first.
- **Graduation.** Day 100 done → the habit is "mastered" and moves to the shelf.
- **Mastered shelf + monthly check-in.** Each mastered habit gets a once-a-month
  maintenance pulse: confirm you're still doing it, or report a slip.
- **Slip → 30-day tune-up.** A slip offers a shorter 30-day re-forming attempt (which
  also occupies the single active slot).

Screens: daily tracker, graduation, mastered shelf, new-habit.

## Build

1. `./scripts/bootstrap-toolchain.sh` — one-time; installs JDK 17 + Android SDK + Gradle,
   no root required.
2. `source scripts/env.sh` — sets `JAVA_HOME` / `ANDROID_HOME` / `PATH` for the shell.
   Run this before any Gradle command.
3. `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
4. `./gradlew :app:testDebugUnitTest` — runs the rule-engine (JVM) and Robolectric suites.
5. `./gradlew :app:lintDebug` — static analysis.

## Continuous integration

`.github/workflows/android.yml` runs on every push and pull request: unit tests, lint,
and `assembleDebug`. Each run publishes the built **`app-debug.apk`** as a downloadable
workflow artifact (Actions → the run → *Artifacts*), plus the test and lint reports.
No signing config is needed — it builds the debug variant. To also produce a signed
release APK, add a `release` signing config and a keystore secret, then a
`:app:assembleRelease` step.

## Dev clock

Debug builds show a **"dev: +1 day"** and **"dev: set date"** control on the tracker. It advances a persisted
offset on top of the system clock so you can walk a habit through rollover, at-risk,
failure, graduation, the monthly check-in, a slip, and the tune-up without waiting real
days. Release builds use the plain system clock and never show the control.

## Architecture

- `domain/` — pure rule engine (`HabitRules`, `Maintenance`, `DateMath`), no Android deps.
- `data/` — Room entities, DAOs, `HabitRepository`, SQL trigger guards for the one-slot rule.
- `rollover/` — `RolloverEngine` + `RolloverWorker` daily backstop that fills elapsed
  misses and persists terminal transitions.
- `clock/` — `Clock` abstraction with `SystemClock` / `DevClock`.
- `ui/` — Compose screens and ViewModels, `HabitGrid` (three sizes), theme.
- App wiring is manual DI via `AppContainer` (created by `HabitApplication`).
