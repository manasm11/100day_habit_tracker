# 100 Day Habit Tracker (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android app where the user forms one habit at a time over a 100-day window with a 10-miss budget and a never-twice-in-a-row failure rule, graduates habits to a mastered shelf with monthly maintenance, and can run a 30-day tune-up when a mastered habit slips.

**Architecture:** Single Gradle module (`:app`), Kotlin + Jetpack Compose (Material 3), Room over SQLite, WorkManager for a rollover backstop, manual DI via an `AppContainer` on the `Application`. A pure-Kotlin rule engine in `domain/` is the single source of truth for all derived state; the data and UI layers never re-implement rules. All day-boundary math uses the habit's stored IANA timezone.

**Tech Stack:** Kotlin 2.0.21, AGP 8.7.2, Gradle 8.9, JDK 17, Compose BOM 2024.10.01, Room 2.6.1 (KSP), WorkManager 2.10.0, DataStore Preferences 1.1.1, Navigation-Compose 2.8.4, Lifecycle 2.8.7. Tests: JUnit4 for pure JVM, Robolectric 4.14.1 for Room/ViewModel. minSdk 26, compile/targetSdk 35.

**Spec:** `docs/superpowers/specs/2026-09-07-100day-habit-tracker-android-design.md`

## Global Constraints

- **Package / applicationId:** `com.manasm.habit100`. App label: `100 Day Habit Tracker`.
- **minSdk 26, compileSdk 35, targetSdk 35.** No core-library desugaring (java.time is native at API 26).
- **No Hilt.** Manual DI only, via `AppContainer` held by `HabitApplication`.
- **The rule engine (`com.manasm.habit100.domain`) has zero Android imports.** No `android.*`, no `androidx.*`. Pure Kotlin + `java.time`.
- **All derived state** (day number, done/miss counts, misses-left, best streak, at-risk, graduated/failed) comes from `HabitRules.evaluate(...)`. Never store it denormalized; never recompute it ad hoc in data/UI code.
- **Colors:** done `#1D9E75`, missed `#E24B4A`, amber (at-risk / check-in due) `#F4A825`, future/neutral cell derived from surface. `today` cell = 2dp outline, no fill.
- **Miss budget = 10** (11th miss fails). **Track length = 100** for forming, **30** for tune-up.
- **Never-twice-in-a-row is evaluated before the budget** and reports `TWO_IN_A_ROW`.
- **Only the current day** can be marked done. No retroactive marking.
- **One active slot:** at most one habit with status `forming` or `tuning_up`. Enforced in the repository AND by a SQLite trigger backstop.
- **Design:** flat Material 3, no gradients, no heavy shadows (elevation ≤ 1dp), generous whitespace, single column, one tap where possible.
- Commit after every green step. Conventional commit messages.

---

## File Structure

```
settings.gradle.kts                         Gradle settings, module include, repos
build.gradle.kts                            Root: plugin versions via `apply false`
gradle/libs.versions.toml                   Version catalog (single source of dep versions)
gradle.properties                           JVM args, AndroidX flags
scripts/bootstrap-toolchain.sh              Downloads JDK 17 + Android SDK + Gradle (no root)
.gitignore                                  Replace the Android-template one
app/build.gradle.kts                        App module config, deps, KSP, test setup
app/proguard-rules.pro                      (empty for v1; minify off)
app/src/main/AndroidManifest.xml            Application + single Activity
app/src/main/java/com/manasm/habit100/
  HabitApplication.kt                       Creates AppContainer, registers rollover lifecycle observer, schedules worker
  MainActivity.kt                           setContent { HabitTheme { AppNavHost(...) } }
  AppContainer.kt                           Builds DB, DAOs, repository, clock; ViewModelFactory
  domain/
    Models.kt                               Enums + DayLog, RuleInput, RuleSnapshot
    DateMath.kt                             currentDayNumber, dateForDay, periodOf
    HabitRules.kt                           evaluate(input, now): RuleSnapshot
  data/
    Entities.kt                             HabitEntity, DayLogEntity, MaintenanceCheckinEntity
    Converters.kt                           Instant/LocalDate TypeConverters
    Daos.kt                                 HabitDao, DayLogDao, CheckinDao
    HabitDatabase.kt                        RoomDatabase + single-slot triggers in onCreate callback
    Mappers.kt                              Entity <-> domain (toDayLog, toRuleInput)
    HabitRepository.kt                      All writes + observeActive/observeMastered + rollover delegation
    RepoModels.kt                           ActiveHabit, MasteredHabitRow (repo-level view types)
  rollover/
    RolloverPort.kt                         Narrow interface the engine depends on
    RolloverEngine.kt                       Pure catch-up logic (fill misses, re-evaluate, transition)
    RolloverWorker.kt                       CoroutineWorker backstop
  clock/
    Clock.kt                                Clock interface + SystemClock
    DevClock.kt                             DevClock + DevClockStore (DataStore offset)
  ui/
    theme/Color.kt, Theme.kt, Type.kt       Material 3 theme, semantic colors
    HabitGrid.kt                            Canvas 10x10 grid, 3 size modes
    AppNavHost.kt                           NavHost + start-destination resolution
    CellState.kt                            enum + fun gridCells(snapshot, logs): List<CellState>
    tracker/TrackerScreen.kt, TrackerViewModel.kt, TrackerUiState.kt
    newhabit/NewHabitScreen.kt, NewHabitViewModel.kt
    graduation/GraduationScreen.kt, GraduationViewModel.kt
    shelf/ShelfScreen.kt, ShelfViewModel.kt, ShelfUiState.kt
    share/GridShare.kt                      Render trophy grid to bitmap + ACTION_SEND
app/src/test/java/com/manasm/habit100/
  domain/DateMathTest.kt
  domain/HabitRulesTest.kt
  rollover/RolloverEngineTest.kt
  data/ConvertersTest.kt
  data/HabitDaoTest.kt                      (Robolectric)
  data/HabitRepositoryTest.kt               (Robolectric)
  ui/TrackerViewModelTest.kt                (Robolectric)
  ui/ShelfViewModelTest.kt                  (Robolectric)
  support/FakeClock.kt                      MutableClock test helper
app/src/androidTest/java/com/manasm/habit100/
  TrackerScreenTest.kt                      (written; not run in this environment)
```

---

## Task 1: Toolchain bootstrap script

**Files:**
- Create: `scripts/bootstrap-toolchain.sh`
- Create: `.tool-versions.md` (human notes on required env vars)
- Modify: `.gitignore` (replace Android-Studio template with a Gradle/Android one)

**Interfaces:**
- Produces: a working `JAVA_HOME` (`~/.local/jdk17`), `ANDROID_HOME` (`~/.local/android-sdk`), and `gradle` (`~/.local/gradle/bin/gradle`) usable by later tasks. Every later task assumes `scripts/env.sh` has been sourced.

- [ ] **Step 1: Write `scripts/bootstrap-toolchain.sh`**

```bash
#!/usr/bin/env bash
set -euo pipefail

PREFIX="${HOME}/.local"
JDK_DIR="${PREFIX}/jdk17"
SDK_DIR="${PREFIX}/android-sdk"
GRADLE_DIR="${PREFIX}/gradle"
GRADLE_VERSION="8.9"
CMDLINE_TOOLS_ZIP="commandlinetools-linux-11076708_latest.zip"

mkdir -p "${PREFIX}" "${SDK_DIR}"

if [ ! -x "${JDK_DIR}/bin/javac" ]; then
  echo ">> Downloading Temurin JDK 17"
  curl -fsSL -o /tmp/jdk17.tar.gz \
    "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
  rm -rf "${JDK_DIR}" && mkdir -p "${JDK_DIR}"
  tar -xzf /tmp/jdk17.tar.gz -C "${JDK_DIR}" --strip-components=1
fi

if [ ! -x "${GRADLE_DIR}/bin/gradle" ]; then
  echo ">> Downloading Gradle ${GRADLE_VERSION}"
  curl -fsSL -o /tmp/gradle.zip \
    "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  rm -rf "${GRADLE_DIR}" && mkdir -p /tmp/gradle-x
  unzip -q /tmp/gradle.zip -d /tmp/gradle-x
  mv "/tmp/gradle-x/gradle-${GRADLE_VERSION}" "${GRADLE_DIR}"
fi

export JAVA_HOME="${JDK_DIR}"
export PATH="${JAVA_HOME}/bin:${PATH}"

if [ ! -d "${SDK_DIR}/cmdline-tools/latest" ]; then
  echo ">> Downloading Android command-line tools"
  curl -fsSL -o /tmp/cmdline-tools.zip \
    "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}"
  rm -rf /tmp/cmdline-tools-x && mkdir -p /tmp/cmdline-tools-x
  unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-x
  mkdir -p "${SDK_DIR}/cmdline-tools"
  mv /tmp/cmdline-tools-x/cmdline-tools "${SDK_DIR}/cmdline-tools/latest"
fi

export ANDROID_HOME="${SDK_DIR}"
SDKMANAGER="${SDK_DIR}/cmdline-tools/latest/bin/sdkmanager"

echo ">> Accepting licenses"
yes | "${SDKMANAGER}" --sdk_root="${SDK_DIR}" --licenses >/dev/null || true

echo ">> Installing platform-tools, platforms;android-35, build-tools;35.0.0"
"${SDKMANAGER}" --sdk_root="${SDK_DIR}" \
  "platform-tools" "platforms;android-35" "build-tools;35.0.0"

cat > "${PWD}/scripts/env.sh" <<EOF
export JAVA_HOME="${JDK_DIR}"
export ANDROID_HOME="${SDK_DIR}"
export PATH="\${JAVA_HOME}/bin:${GRADLE_DIR}/bin:\${PATH}"
EOF

echo ">> Done. Run: source scripts/env.sh"
```

- [ ] **Step 2: Make it executable and run it**

Run: `chmod +x scripts/bootstrap-toolchain.sh && ./scripts/bootstrap-toolchain.sh`
Expected: finishes with `>> Done.`; `~/.local/jdk17/bin/javac -version` prints `javac 17.x`; `~/.local/android-sdk/platforms/android-35/` exists.

- [ ] **Step 3: Source env and verify**

Run: `source scripts/env.sh && java -version && gradle --version`
Expected: `openjdk version "17`, `Gradle 8.9`.

- [ ] **Step 4: Replace `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# Android
local.properties
*.apk
*.aab
*.keystore
*.jks

# IDE
.idea/
*.iml
.kotlin/

# OS / logs
*.log
.DS_Store

# Toolchain (installed by scripts/bootstrap-toolchain.sh)
scripts/env.sh
```

- [ ] **Step 5: Write `.tool-versions.md`**

```markdown
# Toolchain

Run `./scripts/bootstrap-toolchain.sh` once, then `source scripts/env.sh` in every shell.

- JDK 17  -> ~/.local/jdk17   (JAVA_HOME)
- Android SDK -> ~/.local/android-sdk (ANDROID_HOME): platform-tools, platforms;android-35, build-tools;35.0.0
- Gradle 8.9 -> ~/.local/gradle (used once to generate ./gradlew)

CI / fresh shell: `source scripts/env.sh && ./gradlew :app:testDebugUnitTest`
```

- [ ] **Step 6: Commit**

```bash
git add scripts/bootstrap-toolchain.sh .tool-versions.md .gitignore
git commit -m "build: toolchain bootstrap script (no-root JDK 17 + Android SDK + Gradle)"
```

---

## Task 2: Gradle scaffold — empty app that builds

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/manasm/habit100/HabitApplication.kt`, `MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Create: `app/src/test/java/com/manasm/habit100/SmokeTest.kt`
- Create: `local.properties` (gitignored)

**Interfaces:**
- Produces: `HabitApplication` (android:name), `MainActivity` (launcher), a buildable `:app` module. `./gradlew :app:assembleDebug` and `:app:testDebugUnitTest` both succeed.

- [ ] **Step 1: `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
coreKtx = "1.13.1"
lifecycle = "2.8.7"
activityCompose = "1.9.3"
composeBom = "2024.10.01"
navigationCompose = "2.8.4"
room = "2.6.1"
work = "2.10.0"
datastore = "1.1.1"
junit = "4.13.2"
robolectric = "4.14.1"
androidxTestExt = "1.2.1"
androidxTestCore = "1.6.1"
coroutinesTest = "1.9.0"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-lifecycle-runtime-ktx = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-process = { module = "androidx.lifecycle:lifecycle-process", version.ref = "lifecycle" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { module = "androidx.compose.ui:ui" }
androidx-compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
androidx-compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
androidx-compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
androidx-compose-material3 = { module = "androidx.compose.material3:material3" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
junit = { module = "junit:junit", version.ref = "junit" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutinesTest" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "100DayHabitTracker"
include(":app")
```

- [ ] **Step 3: root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

- [ ] **Step 4: `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
org.gradle.caching=true
```

- [ ] **Step 5: `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.manasm.habit100"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.manasm.habit100"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }

    ksp { arg("room.schemaLocation", "$projectDir/schemas") }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.datastore.preferences)
    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
}
```

- [ ] **Step 6: `app/proguard-rules.pro`** — create empty file with a header comment `# v1: minify disabled`.

- [ ] **Step 7: `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:name=".HabitApplication"
        android:allowBackup="true"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.Habit">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.Habit">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 8: resources**

`app/src/main/res/values/strings.xml`:
```xml
<resources><string name="app_name">100 Day Habit Tracker</string></resources>
```
`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.Habit" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 9: `HabitApplication.kt` and `MainActivity.kt` (minimal)**

```kotlin
// HabitApplication.kt
package com.manasm.habit100

import android.app.Application

class HabitApplication : Application()
```

```kotlin
// MainActivity.kt
package com.manasm.habit100

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Text("100 Day Habit Tracker") }
    }
}
```

- [ ] **Step 10: `local.properties`**

```properties
sdk.dir=/home/manas/.local/android-sdk
```

- [ ] **Step 11: Generate the Gradle wrapper**

Run: `source scripts/env.sh && gradle wrapper --gradle-version 8.9 --distribution-type bin`
Expected: creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 12: Write `SmokeTest.kt`**

```kotlin
package com.manasm.habit100

import org.junit.Assert.assertEquals
import org.junit.Test

class SmokeTest {
    @Test fun arithmetic_sanity() = assertEquals(4, 2 + 2)
}
```

- [ ] **Step 13: Build and test**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`; `SmokeTest` passes.

- [ ] **Step 14: Commit**

```bash
git add -A
git commit -m "build: Gradle scaffold — Compose app module builds and tests green"
```

---

## Task 3: Domain models + date math

**Files:**
- Create: `app/src/main/java/com/manasm/habit100/domain/Models.kt`
- Create: `app/src/main/java/com/manasm/habit100/domain/DateMath.kt`
- Test: `app/src/test/java/com/manasm/habit100/domain/DateMathTest.kt`

**Interfaces:**
- Produces:
  - `enum DayStatus { DONE, MISSED }`, `enum HabitState { FORMING, GRADUATED, FAILED }`, `enum FailureReason { TWO_IN_A_ROW, BUDGET_EXCEEDED }`
  - `data class DayLog(val dayNumber: Int, val status: DayStatus)`
  - `data class RuleInput(val startDate: LocalDate, val zoneId: ZoneId, val trackLength: Int, val dayLogs: List<DayLog>, val missBudget: Int = 10)`
  - `data class RuleSnapshot(currentDayNumber, effectiveDay, doneCount, missCount, missesLeft, bestStreak, atRisk, state, failureReason, failedOnDay, canMarkToday, todayMarkedDone)` — all `Int`/`Boolean` except `state: HabitState`, `failureReason: FailureReason?`, `failedOnDay: Int?`
  - `fun currentDayNumber(startDate: LocalDate, zoneId: ZoneId, now: Instant): Int`
  - `fun dateForDay(startDate: LocalDate, dayNumber: Int): LocalDate`
  - `fun periodOf(zoneId: ZoneId, now: Instant): String` — `"YYYY-MM"`

- [ ] **Step 1: Write `Models.kt`**

```kotlin
package com.manasm.habit100.domain

import java.time.LocalDate
import java.time.ZoneId

enum class DayStatus { DONE, MISSED }
enum class HabitState { FORMING, GRADUATED, FAILED }
enum class FailureReason { TWO_IN_A_ROW, BUDGET_EXCEEDED }

data class DayLog(val dayNumber: Int, val status: DayStatus)

data class RuleInput(
    val startDate: LocalDate,
    val zoneId: ZoneId,
    val trackLength: Int,
    val dayLogs: List<DayLog>,
    val missBudget: Int = 10,
)

data class RuleSnapshot(
    val currentDayNumber: Int,
    val effectiveDay: Int,
    val doneCount: Int,
    val missCount: Int,
    val missesLeft: Int,
    val bestStreak: Int,
    val atRisk: Boolean,
    val state: HabitState,
    val failureReason: FailureReason?,
    val failedOnDay: Int?,
    val canMarkToday: Boolean,
    val todayMarkedDone: Boolean,
)
```

- [ ] **Step 2: Write the failing `DateMathTest.kt`**

```kotlin
package com.manasm.habit100.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DateMathTest {
    private val ny = ZoneId.of("America/New_York")

    @Test fun day1_is_start_date() {
        val start = LocalDate.of(2026, 1, 10)
        val now = start.atStartOfDay(ny).toInstant().plusSeconds(3600)
        assertEquals(1, currentDayNumber(start, ny, now))
    }

    @Test fun day_advances_at_local_midnight() {
        val start = LocalDate.of(2026, 1, 10)
        val justBeforeMidnight = LocalDate.of(2026, 1, 10).atTime(23, 59).atZone(ny).toInstant()
        val justAfterMidnight = LocalDate.of(2026, 1, 11).atTime(0, 1).atZone(ny).toInstant()
        assertEquals(1, currentDayNumber(start, ny, justBeforeMidnight))
        assertEquals(2, currentDayNumber(start, ny, justAfterMidnight))
    }

    @Test fun uses_habit_zone_not_utc() {
        val start = LocalDate.of(2026, 1, 10)
        // 03:00 UTC on Jan 11 is still Jan 10 in New York -> day 1
        val instant = LocalDate.of(2026, 1, 11).atTime(3, 0).atZone(ZoneId.of("UTC")).toInstant()
        assertEquals(1, currentDayNumber(start, ny, instant))
    }

    @Test fun before_start_floors_to_day_1() {
        val start = LocalDate.of(2026, 1, 10)
        val now = LocalDate.of(2026, 1, 1).atStartOfDay(ny).toInstant()
        assertEquals(1, currentDayNumber(start, ny, now))
    }

    @Test fun far_future_day_number() {
        val start = LocalDate.of(2026, 1, 1)
        val now = LocalDate.of(2026, 4, 11).atStartOfDay(ny).toInstant() // 100 days later
        assertEquals(101, currentDayNumber(start, ny, now))
    }

    @Test fun date_for_day() {
        assertEquals(LocalDate.of(2026, 1, 1), dateForDay(LocalDate.of(2026, 1, 1), 1))
        assertEquals(LocalDate.of(2026, 4, 10), dateForDay(LocalDate.of(2026, 1, 1), 100))
    }

    @Test fun period_of_uses_zone() {
        val instant = LocalDate.of(2026, 10, 1).atTime(2, 0).atZone(ZoneId.of("UTC")).toInstant()
        // still Sept 30 in New York
        assertEquals("2026-09", periodOf(ny, instant))
    }
}
```

- [ ] **Step 3: Run it, expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*DateMathTest"`
Expected: FAIL — `currentDayNumber` unresolved.

- [ ] **Step 4: Write `DateMath.kt`**

```kotlin
package com.manasm.habit100.domain

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** 1-based day number for [now] given [startDate], measured in [zoneId]. Day 1 == startDate. Floored at 1. */
fun currentDayNumber(startDate: LocalDate, zoneId: ZoneId, now: Instant): Int {
    val today = now.atZone(zoneId).toLocalDate()
    val elapsed = ChronoUnit.DAYS.between(startDate, today)
    return (elapsed + 1L).coerceAtLeast(1L).toInt()
}

/** Calendar date of [dayNumber] (1-based) for a track starting on [startDate]. */
fun dateForDay(startDate: LocalDate, dayNumber: Int): LocalDate =
    startDate.plusDays((dayNumber - 1).toLong())

/** "YYYY-MM" period string for [now] in [zoneId]. */
fun periodOf(zoneId: ZoneId, now: Instant): String =
    YearMonth.from(now.atZone(zoneId)).toString()
```

- [ ] **Step 5: Run tests, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*DateMathTest"`
Expected: PASS (7 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/manasm/habit100/domain app/src/test/java/com/manasm/habit100/domain/DateMathTest.kt
git commit -m "feat(domain): rule-engine models + timezone-aware date math"
```

---

## Task 4: Rule engine — counts and failure rules

**Files:**
- Create: `app/src/main/java/com/manasm/habit100/domain/HabitRules.kt`
- Test: `app/src/test/java/com/manasm/habit100/domain/HabitRulesTest.kt`

**Interfaces:**
- Consumes: everything from Task 3.
- Produces: `object HabitRules { fun evaluate(input: RuleInput, now: Instant): RuleSnapshot }`

- [ ] **Step 1: Write the failing tests (counts + failure)**

```kotlin
package com.manasm.habit100.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class HabitRulesTest {
    private val z = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 1, 1)

    /** now = the moment described by [dayNumber] at 12:00 local. */
    private fun nowForDay(dayNumber: Int): Instant =
        dateForDay(start, dayNumber).atTime(12, 0).atZone(z).toInstant()

    private fun input(trackLength: Int = 100, vararg done: Int) = RuleInput(
        startDate = start, zoneId = z, trackLength = trackLength,
        dayLogs = done.map { DayLog(it, DayStatus.DONE) },
    )

    @Test fun fresh_habit_day1() {
        val s = HabitRules.evaluate(input(), nowForDay(1))
        assertEquals(1, s.currentDayNumber)
        assertEquals(0, s.doneCount)
        assertEquals(0, s.missCount)
        assertEquals(10, s.missesLeft)
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(true, s.canMarkToday)
        assertEquals(false, s.todayMarkedDone)
        assertNull(s.failureReason)
    }

    @Test fun marked_today_blocks_second_mark() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1)), nowForDay(1))
        assertEquals(1, s.doneCount)
        assertEquals(true, s.todayMarkedDone)
        assertEquals(false, s.canMarkToday)
    }

    @Test fun elapsed_unmarked_past_days_count_as_misses() {
        // day 5 today; days 1 and 3 done; days 2 and 4 are elapsed unmarked -> misses
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 3)), nowForDay(5))
        assertEquals(2, s.doneCount)
        assertEquals(2, s.missCount)
        assertEquals(8, s.missesLeft)
        assertEquals(HabitState.FORMING, s.state)
    }

    @Test fun current_day_not_yet_a_miss() {
        // day 3 today, only day 1 done. day 2 = miss, day 3 = pending (not miss)
        val s = HabitRules.evaluate(input(done = intArrayOf(1)), nowForDay(3))
        assertEquals(1, s.missCount)
    }

    @Test fun two_consecutive_misses_fail_immediately_even_under_budget() {
        // days 1 done, 2 & 3 missed (elapsed), today = day 4
        val s = HabitRules.evaluate(input(done = intArrayOf(1)), nowForDay(4))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(3, s.failedOnDay)
        assertEquals(2, s.missCount) // stops counting at failure day
    }

    @Test fun never_twice_beats_budget_priority() {
        // misses on days 2,4,6,8 (non-consecutive, done between), then 9 & 10 consecutive.
        // total would be 6 but two-in-a-row fires first at day 10.
        val done = intArrayOf(1, 3, 5, 7)
        val s = HabitRules.evaluate(input(done = done), nowForDay(11))
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(10, s.failedOnDay)
    }

    @Test fun eleventh_miss_fails_on_budget() {
        // done on every odd day 1..21 -> misses on 2,4,...,22 = 11 misses, none consecutive
        val done = (1..21 step 2).toList().toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(23))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.BUDGET_EXCEEDED, s.failureReason)
        assertEquals(22, s.failedOnDay)
    }

    @Test fun exactly_ten_misses_is_not_failure() {
        val done = (1..19 step 2).toList().toIntArray() // misses on 2..20 = 10
        val s = HabitRules.evaluate(input(done = done), nowForDay(21))
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(10, s.missCount)
        assertEquals(0, s.missesLeft)
    }
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*HabitRulesTest"`
Expected: FAIL — `HabitRules` unresolved.

- [ ] **Step 3: Write `HabitRules.kt`**

```kotlin
package com.manasm.habit100.domain

import java.time.Instant

object HabitRules {

    fun evaluate(input: RuleInput, now: Instant): RuleSnapshot {
        val currentDay = currentDayNumber(input.startDate, input.zoneId, now)
        val doneDays = input.dayLogs
            .filter { it.status == DayStatus.DONE }
            .map { it.dayNumber }
            .toHashSet()

        val lastDay = minOf(currentDay, input.trackLength)
        val todayMarkedDone = currentDay <= input.trackLength && currentDay in doneDays

        var done = 0
        var misses = 0
        var consecutive = 0
        var streak = 0
        var bestStreak = 0
        var failureReason: FailureReason? = null
        var failedOnDay: Int? = null

        var day = 1
        while (day <= lastDay) {
            val isDone = day in doneDays
            val isPast = day < currentDay
            if (isDone) {
                done++
                consecutive = 0
                streak++
                if (streak > bestStreak) bestStreak = streak
            } else if (isPast) {
                misses++
                consecutive++
                streak = 0
                if (consecutive >= 2) {
                    failureReason = FailureReason.TWO_IN_A_ROW
                    failedOnDay = day
                } else if (misses > input.missBudget) {
                    failureReason = FailureReason.BUDGET_EXCEEDED
                    failedOnDay = day
                }
            }
            // else: current day, unmarked -> pending, not counted
            if (failureReason != null) break
            day++
        }

        val state = when {
            failureReason != null -> HabitState.FAILED
            currentDay > input.trackLength -> HabitState.GRADUATED
            todayMarkedDone && currentDay == input.trackLength -> HabitState.GRADUATED
            else -> HabitState.FORMING
        }

        val yesterday = currentDay - 1
        val yesterdayMissed = yesterday in 1..input.trackLength &&
            yesterday < currentDay &&
            yesterday !in doneDays
        val atRisk = state == HabitState.FORMING && yesterdayMissed && !todayMarkedDone

        val canMarkToday = state == HabitState.FORMING &&
            currentDay in 1..input.trackLength &&
            !todayMarkedDone

        return RuleSnapshot(
            currentDayNumber = currentDay,
            effectiveDay = lastDay,
            doneCount = done,
            missCount = misses,
            missesLeft = (input.missBudget - misses).coerceAtLeast(0),
            bestStreak = bestStreak,
            atRisk = atRisk,
            state = state,
            failureReason = failureReason,
            failedOnDay = failedOnDay,
            canMarkToday = canMarkToday,
            todayMarkedDone = todayMarkedDone,
        )
    }
}
```

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*HabitRulesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/manasm/habit100/domain/HabitRules.kt app/src/test/java/com/manasm/habit100/domain/HabitRulesTest.kt
git commit -m "feat(domain): rule engine — miss counting, never-twice + budget failure"
```

---

## Task 5: Rule engine — graduation, at-risk, best streak

**Files:**
- Modify: `app/src/test/java/com/manasm/habit100/domain/HabitRulesTest.kt` (append cases)

**Interfaces:**
- Consumes: `HabitRules.evaluate` (already complete — this task adds coverage and fixes any gaps found).

- [ ] **Step 1: Append the failing tests**

```kotlin
    // --- graduation ---

    @Test fun graduates_when_day_100_marked_done_with_clean_record() {
        val done = (1..100).toList().toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(100))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(100, s.doneCount)
        assertEquals(100, s.bestStreak)
        assertEquals(0, s.missCount)
    }

    @Test fun graduates_past_window_with_misses_within_budget() {
        // done every day except days 10,20 (non-consecutive) ; today day 101
        val done = (1..100).filter { it != 10 && it != 20 }.toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(101))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(2, s.missCount)
    }

    @Test fun day_100_implied_miss_still_graduates_if_not_two_in_row() {
        // done days 1..99, today day 101 -> day 100 implied miss (1 total), day 99 done
        val done = (1..99).toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(101))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(1, s.missCount)
    }

    @Test fun day_99_and_100_both_missed_fails_two_in_row_on_100() {
        val done = (1..98).toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(101))
        assertEquals(HabitState.FAILED, s.state)
        assertEquals(FailureReason.TWO_IN_A_ROW, s.failureReason)
        assertEquals(100, s.failedOnDay)
    }

    @Test fun day_100_today_unmarked_is_still_forming() {
        val done = (1..99).toIntArray()
        val s = HabitRules.evaluate(input(done = done), nowForDay(100))
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(true, s.canMarkToday)
    }

    @Test fun tuneup_track_length_30_graduates() {
        val done = (1..30).toIntArray()
        val s = HabitRules.evaluate(input(trackLength = 30, done = done), nowForDay(30))
        assertEquals(HabitState.GRADUATED, s.state)
        assertEquals(30, s.effectiveDay)
    }

    // --- at-risk ---

    @Test fun at_risk_when_yesterday_missed_and_today_unmarked() {
        // today day 4; days 1..2 done, day 3 (yesterday) missed
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2)), nowForDay(4))
        assertEquals(HabitState.FORMING, s.state)
        assertEquals(true, s.atRisk)
    }

    @Test fun not_at_risk_once_today_marked() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 4)), nowForDay(4))
        assertEquals(false, s.atRisk)
    }

    @Test fun not_at_risk_when_yesterday_was_done() {
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 3)), nowForDay(4))
        assertEquals(false, s.atRisk)
    }

    @Test fun not_at_risk_on_day_1() {
        val s = HabitRules.evaluate(input(), nowForDay(1))
        assertEquals(false, s.atRisk)
    }

    // --- best streak ---

    @Test fun best_streak_is_longest_run_of_done() {
        // done 1,2,3 (streak 3) miss 4, done 5,6 (streak 2), today 7
        val s = HabitRules.evaluate(input(done = intArrayOf(1, 2, 3, 5, 6)), nowForDay(7))
        assertEquals(3, s.bestStreak)
    }
```

- [ ] **Step 2: Run, expect PASS (engine already handles these)**

Run: `./gradlew :app:testDebugUnitTest --tests "*HabitRulesTest"`
Expected: PASS. If any fail, fix `HabitRules.kt` minimally and re-run — the tests are the contract.

- [ ] **Step 3: Add DST + date-line cases**

```kotlin
    @Test fun day_number_survives_spring_dst_gap() {
        val z = ZoneId.of("America/New_York")
        val start = LocalDate.of(2026, 3, 7) // DST begins Mar 8, 2026
        val now = LocalDate.of(2026, 3, 10).atTime(12, 0).atZone(z).toInstant()
        assertEquals(4, currentDayNumber(start, z, now))
    }

    @Test fun day_number_uses_far_east_zone() {
        val z = ZoneId.of("Pacific/Kiritimati") // UTC+14
        val start = LocalDate.of(2026, 1, 1)
        // 23:00 UTC Jan 1 == 13:00 Jan 2 in Kiritimati -> day 2
        val now = LocalDate.of(2026, 1, 1).atTime(23, 0).atZone(ZoneId.of("UTC")).toInstant()
        assertEquals(2, currentDayNumber(start, z, now))
    }
```

- [ ] **Step 4: Run full domain suite**

Run: `./gradlew :app:testDebugUnitTest --tests "*domain*"`
Expected: PASS (all DateMath + HabitRules).

- [ ] **Step 5: Commit**

```bash
git add app/src/test/java/com/manasm/habit100/domain/HabitRulesTest.kt app/src/main/java/com/manasm/habit100/domain/HabitRules.kt
git commit -m "test(domain): graduation, at-risk, best-streak, DST + date-line coverage"
```

**CHECKPOINT 1** — scaffold builds; rule engine fully specced and tested. Stop for review.

---

## Task 6: Room entities, converters, DAOs, database

**Files:**
- Create: `data/Entities.kt`, `data/Converters.kt`, `data/Daos.kt`, `data/HabitDatabase.kt`
- Test: `data/ConvertersTest.kt`, `data/HabitDaoTest.kt` (Robolectric)
- Create: `app/src/test/java/com/manasm/habit100/support/FakeClock.kt`

**Interfaces:**
- Produces:
  - `HabitEntity(id: Long = 0, name: String, timeZoneId: String, status: String, currentAttempt: Int, attemptStartDate: LocalDate, attemptTrackLength: Int, trophyAttempt: Int?, slipped: Boolean, createdAt: Instant, graduatedAt: Instant?, failureReason: String?, failedOnDay: Int?)`
  - `DayLogEntity(id: Long = 0, habitId: Long, attempt: Int, dayNumber: Int, logDate: LocalDate, status: String, markedAt: Instant)`
  - `MaintenanceCheckinEntity(id: Long = 0, habitId: Long, period: String, status: String, checkedAt: Instant)`
  - `HabitDao`, `DayLogDao`, `CheckinDao` (signatures below)
  - `HabitDatabase.build(context): HabitDatabase` with `.habitDao() / .dayLogDao() / .checkinDao()`
  - `class FakeClock(var instant: Instant) : Clock` — but `Clock` is defined in Task 8; for now define `FakeClock` against a local `fun now(): Instant` and re-home it in Task 8. To avoid churn, **do Task 8 (Clock) before this task's Robolectric tests need it** — see note. Simplest: implement `clock/Clock.kt` (just the interface + SystemClock, ~6 lines) as Step 0 here.

- [ ] **Step 0: Create `clock/Clock.kt` (needed by tests and repo)**

```kotlin
package com.manasm.habit100.clock

import java.time.Instant

interface Clock { fun now(): Instant }

class SystemClock : Clock {
    override fun now(): Instant = Instant.now()
}
```

- [ ] **Step 1: `support/FakeClock.kt`**

```kotlin
package com.manasm.habit100.support

import com.manasm.habit100.clock.Clock
import java.time.Duration
import java.time.Instant

class FakeClock(var instant: Instant) : Clock {
    override fun now(): Instant = instant
    fun advance(d: Duration) { instant = instant.plus(d) }
    fun advanceDays(n: Long) { instant = instant.plus(Duration.ofDays(n)) }
}
```

- [ ] **Step 2: `data/Entities.kt`**

```kotlin
package com.manasm.habit100.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val timeZoneId: String,
    val status: String,              // forming | mastered | failed | tuning_up
    val currentAttempt: Int,
    val attemptStartDate: LocalDate,
    val attemptTrackLength: Int,
    val trophyAttempt: Int?,
    val slipped: Boolean,
    val createdAt: Instant,
    val graduatedAt: Instant?,
    val failureReason: String?,
    val failedOnDay: Int?,
)

@Entity(
    tableName = "day_logs",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index(value = ["habitId", "attempt", "dayNumber"], unique = true),
        Index(value = ["habitId", "attempt"]),
    ],
)
data class DayLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val attempt: Int,
    val dayNumber: Int,
    val logDate: LocalDate,
    val status: String,              // done | missed
    val markedAt: Instant,
)

@Entity(
    tableName = "maintenance_checkins",
    foreignKeys = [ForeignKey(
        entity = HabitEntity::class, parentColumns = ["id"], childColumns = ["habitId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["habitId", "period"], unique = true)],
)
data class MaintenanceCheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val habitId: Long,
    val period: String,              // YYYY-MM
    val status: String,              // strong | slipped
    val checkedAt: Instant,
)
```

- [ ] **Step 3: `data/Converters.kt` + failing `ConvertersTest.kt`**

```kotlin
package com.manasm.habit100.data

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

class Converters {
    @TypeConverter fun instantToLong(v: Instant?): Long? = v?.toEpochMilli()
    @TypeConverter fun longToInstant(v: Long?): Instant? = v?.let(Instant::ofEpochMilli)
    @TypeConverter fun dateToString(v: LocalDate?): String? = v?.toString()
    @TypeConverter fun stringToDate(v: String?): LocalDate? = v?.let(LocalDate::parse)
}
```

```kotlin
package com.manasm.habit100.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ConvertersTest {
    private val c = Converters()
    @Test fun instant_round_trip() {
        val i = Instant.ofEpochMilli(1_725_000_000_000)
        assertEquals(i, c.longToInstant(c.instantToLong(i)))
    }
    @Test fun date_round_trip() {
        val d = LocalDate.of(2026, 9, 7)
        assertEquals(d, c.stringToDate(c.dateToString(d)))
    }
    @Test fun nulls() {
        assertEquals(null, c.instantToLong(null))
        assertEquals(null, c.stringToDate(null))
    }
}
```

- [ ] **Step 4: `data/Daos.kt`**

```kotlin
package com.manasm.habit100.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Insert suspend fun insert(h: HabitEntity): Long
    @Update suspend fun update(h: HabitEntity)
    @Query("SELECT * FROM habits WHERE id = :id") suspend fun byId(id: Long): HabitEntity?

    @Query("SELECT * FROM habits WHERE status IN ('forming','tuning_up') LIMIT 1")
    fun observeActive(): Flow<HabitEntity?>

    @Query("SELECT * FROM habits WHERE status IN ('forming','tuning_up') LIMIT 1")
    suspend fun activeOnce(): HabitEntity?

    @Query("SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up')")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM habits WHERE trophyAttempt IS NOT NULL ORDER BY graduatedAt DESC")
    fun observeMastered(): Flow<List<HabitEntity>>
}

@Dao
interface DayLogDao {
    @Insert suspend fun insert(log: DayLogEntity): Long
    @Insert suspend fun insertAll(logs: List<DayLogEntity>)

    @Query("SELECT * FROM day_logs WHERE habitId = :habitId AND attempt = :attempt ORDER BY dayNumber")
    suspend fun forAttempt(habitId: Long, attempt: Int): List<DayLogEntity>

    @Query("SELECT * FROM day_logs WHERE habitId = :habitId AND attempt = :attempt ORDER BY dayNumber")
    fun observeForAttempt(habitId: Long, attempt: Int): Flow<List<DayLogEntity>>
}

@Dao
interface CheckinDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(c: MaintenanceCheckinEntity): Long

    @Query("SELECT * FROM maintenance_checkins WHERE habitId = :habitId AND period = :period LIMIT 1")
    suspend fun forPeriod(habitId: Long, period: String): MaintenanceCheckinEntity?

    @Query("SELECT * FROM maintenance_checkins WHERE habitId = :habitId ORDER BY checkedAt DESC")
    fun observeForHabit(habitId: Long): Flow<List<MaintenanceCheckinEntity>>
}
```

- [ ] **Step 5: `data/HabitDatabase.kt` with the single-slot triggers**

```kotlin
package com.manasm.habit100.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [HabitEntity::class, DayLogEntity::class, MaintenanceCheckinEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class HabitDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun dayLogDao(): DayLogDao
    abstract fun checkinDao(): CheckinDao

    companion object {
        private val CALLBACK = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TRIGGER trg_single_slot_insert
                    BEFORE INSERT ON habits
                    WHEN NEW.status IN ('forming','tuning_up')
                      AND (SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up')) > 0
                    BEGIN
                      SELECT RAISE(ABORT, 'single active slot violated');
                    END;
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TRIGGER trg_single_slot_update
                    BEFORE UPDATE OF status ON habits
                    WHEN NEW.status IN ('forming','tuning_up')
                      AND (SELECT COUNT(*) FROM habits
                           WHERE status IN ('forming','tuning_up') AND id <> NEW.id) > 0
                    BEGIN
                      SELECT RAISE(ABORT, 'single active slot violated');
                    END;
                    """.trimIndent()
                )
            }
        }

        fun build(context: Context): HabitDatabase =
            Room.databaseBuilder(context, HabitDatabase::class.java, "habit.db")
                .addCallback(CALLBACK)
                .build()
    }
}
```

- [ ] **Step 6: Write failing `HabitDaoTest.kt` (Robolectric)**

```kotlin
package com.manasm.habit100.data

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitDaoTest {
    private lateinit var db: HabitDatabase

    private fun habit(status: String = "forming", attempt: Int = 1) = HabitEntity(
        name = "Read", timeZoneId = "America/New_York", status = status,
        currentAttempt = attempt, attemptStartDate = LocalDate.of(2026, 1, 1),
        attemptTrackLength = 100, trophyAttempt = null, slipped = false,
        createdAt = Instant.EPOCH, graduatedAt = null, failureReason = null, failedOnDay = null,
    )

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(), HabitDatabase::class.java,
        ).allowMainThreadQueries()
            .addCallback(HabitDatabaseTestHooks.callback())
            .build()
    }

    @After fun tearDown() = db.close()

    @Test fun insert_and_read_active() = runTest {
        db.habitDao().insert(habit())
        assertEquals("Read", db.habitDao().activeOnce()?.name)
        assertEquals(1, db.habitDao().activeCount())
    }

    @Test fun day_log_unique_index_rejects_duplicate_day() = runTest {
        val id = db.habitDao().insert(habit())
        db.dayLogDao().insert(DayLogEntity(habitId = id, attempt = 1, dayNumber = 3, logDate = LocalDate.of(2026,1,3), status = "done", markedAt = Instant.EPOCH))
        var threw = false
        try {
            db.dayLogDao().insert(DayLogEntity(habitId = id, attempt = 1, dayNumber = 3, logDate = LocalDate.of(2026,1,3), status = "missed", markedAt = Instant.EPOCH))
        } catch (e: android.database.sqlite.SQLiteConstraintException) { threw = true }
        assertTrue(threw)
    }

    @Test fun trigger_blocks_second_active_habit() = runTest {
        db.habitDao().insert(habit())
        var threw = false
        try { db.habitDao().insert(habit(attempt = 2)) }
        catch (e: android.database.sqlite.SQLiteException) { threw = true }
        assertTrue(threw)
    }
}
```

Add a tiny test hook object so the in-memory DB gets the triggers:

```kotlin
// data/HabitDatabaseTestHooks.kt  (src/test)
package com.manasm.habit100.data

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

object HabitDatabaseTestHooks {
    fun callback(): RoomDatabase.Callback = object : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TRIGGER trg_single_slot_insert BEFORE INSERT ON habits WHEN NEW.status IN ('forming','tuning_up') AND (SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up')) > 0 BEGIN SELECT RAISE(ABORT, 'single active slot violated'); END;")
            db.execSQL("CREATE TRIGGER trg_single_slot_update BEFORE UPDATE OF status ON habits WHEN NEW.status IN ('forming','tuning_up') AND (SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up') AND id <> NEW.id) > 0 BEGIN SELECT RAISE(ABORT, 'single active slot violated'); END;")
        }
    }
}
```

(Note: to keep the trigger SQL in one place, later refactor both call sites to reference a `TriggerSql` constant in `data/`. Do that refactor as the final step of this task.)

- [ ] **Step 7: Run, expect FAIL then implement then PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*data*"`
Expected: first run FAIL (compile), after Steps 2–6 PASS. Robolectric downloads an Android-all jar on first run — allow network.

- [ ] **Step 8: Refactor trigger SQL to a shared constant**

```kotlin
// data/TriggerSql.kt
package com.manasm.habit100.data

object TriggerSql {
    val INSERT_GUARD = "CREATE TRIGGER trg_single_slot_insert BEFORE INSERT ON habits WHEN NEW.status IN ('forming','tuning_up') AND (SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up')) > 0 BEGIN SELECT RAISE(ABORT, 'single active slot violated'); END;"
    val UPDATE_GUARD = "CREATE TRIGGER trg_single_slot_update BEFORE UPDATE OF status ON habits WHEN NEW.status IN ('forming','tuning_up') AND (SELECT COUNT(*) FROM habits WHERE status IN ('forming','tuning_up') AND id <> NEW.id) > 0 BEGIN SELECT RAISE(ABORT, 'single active slot violated'); END;"
}
```
Point `HabitDatabase.CALLBACK` and `HabitDatabaseTestHooks` at `TriggerSql`. Re-run `*data*` tests → PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/manasm/habit100/data app/src/main/java/com/manasm/habit100/clock/Clock.kt app/src/test/java/com/manasm/habit100/data app/src/test/java/com/manasm/habit100/support app/schemas
git commit -m "feat(data): Room schema, DAOs, single-active-slot triggers + Robolectric tests"
```

---

## Task 7: Mappers + HabitRepository (create, mark done, single-slot)

**Files:**
- Create: `data/Mappers.kt`, `data/RepoModels.kt`, `data/HabitRepository.kt`
- Test: `data/HabitRepositoryTest.kt` (Robolectric)

**Interfaces:**
- Consumes: DAOs (Task 6), `HabitRules` (Task 4), `Clock` (Task 6 Step 0), `dateForDay`/`currentDayNumber` (Task 3).
- Produces:
  - `fun DayLogEntity.toDayLog(): DayLog`
  - `fun HabitEntity.toRuleInput(logs: List<DayLog>): RuleInput`
  - `data class ActiveHabit(val habit: HabitEntity, val logs: List<DayLog>, val snapshot: RuleSnapshot)`
  - `class HabitRepository(db, habitDao, dayLogDao, checkinDao, clock)` with:
    - `fun observeActive(): Flow<ActiveHabit?>`
    - `suspend fun createHabit(name: String, zoneId: ZoneId)`
    - `suspend fun markTodayDone(habitId: Long)`
    - `suspend fun restartFailedHabit(habitId: Long)`
    - `suspend fun abandonHabit(habitId: Long)`
    - `internal suspend fun applyTransition(habitId: Long)` — recompute + promote to mastered/failed
    - `fun snapshotOf(habit: HabitEntity, logs: List<DayLog>): RuleSnapshot` (wraps `HabitRules.evaluate` with `clock.now()`)

- [ ] **Step 1: `data/Mappers.kt` + `data/RepoModels.kt`**

```kotlin
// Mappers.kt
package com.manasm.habit100.data

import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.RuleInput
import java.time.ZoneId

fun DayLogEntity.toDayLog(): DayLog =
    DayLog(dayNumber, if (status == "done") DayStatus.DONE else DayStatus.MISSED)

fun HabitEntity.toRuleInput(logs: List<DayLog>): RuleInput = RuleInput(
    startDate = attemptStartDate,
    zoneId = ZoneId.of(timeZoneId),
    trackLength = attemptTrackLength,
    dayLogs = logs,
)
```

```kotlin
// RepoModels.kt
package com.manasm.habit100.data

import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.RuleSnapshot

data class ActiveHabit(
    val habit: HabitEntity,
    val logs: List<DayLog>,
    val snapshot: RuleSnapshot,
)
```

- [ ] **Step 2: Write failing `HabitRepositoryTest.kt`**

```kotlin
package com.manasm.habit100.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.support.FakeClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HabitRepositoryTest {
    private lateinit var db: HabitDatabase
    private lateinit var repo: HabitRepository
    private val zone = ZoneId.of("America/New_York")
    private val clock = FakeClock(LocalDate.of(2026, 1, 1).atTime(9, 0).atZone(ZoneId.of("America/New_York")).toInstant())

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HabitDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(HabitDatabaseTestHooks.callback())
            .build()
        repo = HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), clock)
    }
    @After fun tearDown() = db.close()

    @Test fun create_then_mark_today() = runTest {
        repo.createHabit("Read", zone)
        val a1 = repo.observeActive().first()!!
        assertEquals(1, a1.snapshot.currentDayNumber)
        repo.markTodayDone(a1.habit.id)
        val a2 = repo.observeActive().first()!!
        assertEquals(1, a2.snapshot.doneCount)
        assertTrue(a2.snapshot.todayMarkedDone)
    }

    @Test fun second_create_is_rejected() = runTest {
        repo.createHabit("Read", zone)
        assertThrows(IllegalStateException::class.java) {
            runTest { repo.createHabit("Run", zone) }
        }
    }

    @Test fun cannot_mark_twice_same_day() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)
        assertThrows(IllegalStateException::class.java) {
            runTest { repo.markTodayDone(id) }
        }
    }

    @Test fun marking_day_100_graduates() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        // fast-path: mark each day advancing the clock
        repeat(100) {
            repo.markTodayDone(id)
            clock.advanceDays(1)
        }
        assertNull(repo.observeActive().first()) // no longer forming
        val mastered = db.habitDao().byId(id)!!
        assertEquals("mastered", mastered.status)
        assertEquals(1, mastered.trophyAttempt)
    }

    @Test fun two_missed_days_fail_on_next_open() = runTest {
        repo.createHabit("Read", zone)
        val id = repo.observeActive().first()!!.habit.id
        repo.markTodayDone(id)            // day 1 done
        clock.advanceDays(3)             // days 2,3 unmarked; now day 4
        repo.applyTransition(id)          // rollover is Task 13; here transition alone won't fail
        // observeActive still recomputes snapshot from clock:
        val a = repo.observeActive().first()!!
        assertEquals(HabitState.FAILED, a.snapshot.state)
    }
}
```

- [ ] **Step 3: Run, expect FAIL**

Run: `./gradlew :app:testDebugUnitTest --tests "*HabitRepositoryTest"`
Expected: FAIL — `HabitRepository` unresolved.

- [ ] **Step 4: Write `HabitRepository.kt`**

```kotlin
package com.manasm.habit100.data

import androidx.room.withTransaction
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.FailureReason
import com.manasm.habit100.domain.HabitRules
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.RuleSnapshot
import com.manasm.habit100.domain.dateForDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HabitRepository(
    private val db: HabitDatabase,
    private val habitDao: HabitDao,
    private val dayLogDao: DayLogDao,
    private val checkinDao: CheckinDao,
    private val clock: Clock,
) {
    fun snapshotOf(habit: HabitEntity, logs: List<DayLog>): RuleSnapshot =
        HabitRules.evaluate(habit.toRuleInput(logs), clock.now())

    fun observeActive(): Flow<ActiveHabit?> =
        habitDao.observeActive().flatMapLatest { habit ->
            if (habit == null) {
                flowOf(null)
            } else {
                dayLogDao.observeForAttempt(habit.id, habit.currentAttempt).map { rows ->
                    val logs = rows.map { it.toDayLog() }
                    ActiveHabit(habit, logs, snapshotOf(habit, logs))
                }
            }
        }

    suspend fun createHabit(name: String, zoneId: ZoneId) {
        check(habitDao.activeCount() == 0) { "A habit is already forming" }
        val now = clock.now()
        val today = now.atZone(zoneId).toLocalDate()
        habitDao.insert(
            HabitEntity(
                name = name.trim(),
                timeZoneId = zoneId.id,
                status = "forming",
                currentAttempt = 1,
                attemptStartDate = today,
                attemptTrackLength = 100,
                trophyAttempt = null,
                slipped = false,
                createdAt = now,
                graduatedAt = null,
                failureReason = null,
                failedOnDay = null,
            )
        )
    }

    suspend fun markTodayDone(habitId: Long) {
        db.withTransaction {
            val habit = habitDao.byId(habitId) ?: return@withTransaction
            val logs = dayLogDao.forAttempt(habit.id, habit.currentAttempt).map { it.toDayLog() }
            val snap = snapshotOf(habit, logs)
            check(snap.canMarkToday) { "Today is not markable" }
            dayLogDao.insert(
                DayLogEntity(
                    habitId = habit.id,
                    attempt = habit.currentAttempt,
                    dayNumber = snap.currentDayNumber,
                    logDate = dateForDay(habit.attemptStartDate, snap.currentDayNumber),
                    status = "done",
                    markedAt = clock.now(),
                )
            )
            applyTransitionLocked(habit.id)
        }
    }

    suspend fun applyTransition(habitId: Long) {
        db.withTransaction { applyTransitionLocked(habitId) }
    }

    private suspend fun applyTransitionLocked(habitId: Long) {
        val habit = habitDao.byId(habitId) ?: return
        if (habit.status != "forming" && habit.status != "tuning_up") return
        val logs = dayLogDao.forAttempt(habit.id, habit.currentAttempt).map { it.toDayLog() }
        val snap = snapshotOf(habit, logs)
        when (snap.state) {
            HabitState.GRADUATED -> {
                val wasTuneUp = habit.status == "tuning_up"
                habitDao.update(
                    habit.copy(
                        status = "mastered",
                        trophyAttempt = if (wasTuneUp) habit.trophyAttempt else habit.currentAttempt,
                        graduatedAt = habit.graduatedAt ?: clock.now(),
                        slipped = false,
                        failureReason = null,
                        failedOnDay = null,
                    )
                )
            }
            HabitState.FAILED -> {
                val wasTuneUp = habit.status == "tuning_up"
                habitDao.update(
                    habit.copy(
                        status = if (wasTuneUp) "mastered" else "failed",
                        slipped = wasTuneUp, // failed tune-up: habit stays mastered, still slipped
                        failureReason = snap.failureReason?.name,
                        failedOnDay = snap.failedOnDay,
                    )
                )
            }
            HabitState.FORMING -> Unit
        }
    }

    suspend fun restartFailedHabit(habitId: Long) {
        db.withTransaction {
            val habit = habitDao.byId(habitId) ?: return@withTransaction
            check(habit.status == "failed") { "Habit is not failed" }
            check(habitDao.activeCount() == 0) { "A habit is already forming" }
            val zone = ZoneId.of(habit.timeZoneId)
            val today = clock.now().atZone(zone).toLocalDate()
            habitDao.update(
                habit.copy(
                    status = "forming",
                    currentAttempt = habit.currentAttempt + 1,
                    attemptStartDate = today,
                    attemptTrackLength = 100,
                    failureReason = null,
                    failedOnDay = null,
                )
            )
        }
    }

    suspend fun abandonHabit(habitId: Long) {
        // Failed habits are already out of the slot; this is a no-op marker for v1
        // kept so the UI has a symmetric action. Leaves status = 'failed'.
    }
}
```

- [ ] **Step 5: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*HabitRepositoryTest"`
Expected: PASS. (The `two_missed_days_fail_on_next_open` test passes because `observeActive` recomputes the snapshot from the live clock even before rollover materializes the misses.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/manasm/habit100/data app/src/test/java/com/manasm/habit100/data/HabitRepositoryTest.kt
git commit -m "feat(data): HabitRepository — create, mark-done, graduation/failure transitions"
```

---

## Task 8: DevClock + DevClockStore

**Files:**
- Create: `clock/DevClock.kt`
- Test: `app/src/test/java/com/manasm/habit100/clock/DevClockTest.kt` (Robolectric — DataStore needs a Context)

**Interfaces:**
- Consumes: `Clock` (Task 6 Step 0).
- Produces:
  - `class DevClockStore(context: Context)` with `val offsetSeconds: Flow<Long>`, `suspend fun setOffsetSeconds(v: Long)`, `suspend fun addDays(n: Long)`, `suspend fun reset()`
  - `class DevClock(private val base: Clock, private val store: DevClockStore) : Clock` — `now() = base.now() + offset` (offset read via `runBlocking` on a cached `StateFlow`, or kept in memory after first collect; see impl)

- [ ] **Step 1: Write `clock/DevClock.kt`**

```kotlin
package com.manasm.habit100.clock

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant

private val Context.devClockDataStore: DataStore<Preferences> by preferencesDataStore("dev_clock")
private val OFFSET = longPreferencesKey("offset_seconds")

class DevClockStore(private val context: Context) {
    val offsetSeconds: Flow<Long> = context.devClockDataStore.data.map { it[OFFSET] ?: 0L }
    suspend fun setOffsetSeconds(v: Long) { context.devClockDataStore.edit { it[OFFSET] = v } }
    suspend fun addDays(n: Long) {
        context.devClockDataStore.edit { it[OFFSET] = (it[OFFSET] ?: 0L) + Duration.ofDays(n).seconds }
    }
    suspend fun reset() { context.devClockDataStore.edit { it[OFFSET] = 0L } }
}

/**
 * Wraps a base clock and adds a persisted offset. The offset is cached in memory and
 * refreshed whenever [refresh] is called (the Application observes the store and calls it).
 */
class DevClock(
    private val base: Clock,
    @Volatile private var offsetSeconds: Long = 0L,
) : Clock {
    override fun now(): Instant = base.now().plusSeconds(offsetSeconds)
    fun update(offsetSeconds: Long) { this.offsetSeconds = offsetSeconds }
}
```

- [ ] **Step 2: Write failing `DevClockTest.kt`**

```kotlin
package com.manasm.habit100.clock

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DevClockTest {
    @Test fun offset_shifts_now() {
        val fixed = object : Clock { override fun now() = Instant.ofEpochSecond(1_000) }
        val dev = DevClock(fixed, offsetSeconds = 0)
        assertEquals(Instant.ofEpochSecond(1_000), dev.now())
        dev.update(86_400)
        assertEquals(Instant.ofEpochSecond(87_400), dev.now())
    }

    @Test fun store_add_days_accumulates() = runTest {
        val store = DevClockStore(org.robolectric.RuntimeEnvironment.getApplication())
        store.reset()
        store.addDays(2)
        store.addDays(1)
        val v = kotlinx.coroutines.flow.first(store.offsetSeconds)
        assertEquals(3L * 86_400, v)
    }
}
```

- [ ] **Step 3: Run FAIL → already-correct impl → PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*DevClockTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/manasm/habit100/clock/DevClock.kt app/src/test/java/com/manasm/habit100/clock/DevClockTest.kt
git commit -m "feat(clock): dev-only persisted clock offset for exercising rollover/graduation"
```

---

## Task 9: AppContainer + Application wiring + theme + nav skeleton

**Files:**
- Create: `AppContainer.kt`; Modify: `HabitApplication.kt`, `MainActivity.kt`
- Create: `ui/theme/Color.kt`, `ui/theme/Theme.kt`, `ui/theme/Type.kt`
- Create: `ui/AppNavHost.kt`
- Create: `ui/HabitViewModelFactory.kt`

**Interfaces:**
- Produces:
  - `class AppContainer(app: Application)` exposing `val repository: HabitRepository`, `val clock: Clock`, `val devClock: DevClock?` (non-null only in debug), `val devClockStore: DevClockStore?`, `val rolloverEngine: RolloverEngine` (added in Task 13 — leave a `lateinit`/lazy seam), `val isDebug: Boolean`
  - `HabitApplication.container: AppContainer`
  - `@Composable fun HabitTheme(content: @Composable () -> Unit)`
  - semantic colors: `object HabitColors { val done = Color(0xFF1D9E75); val missed = Color(0xFFE24B4A); val amber = Color(0xFFF4A825) }`
  - `@Composable fun AppNavHost(container: AppContainer)` with routes `"tracker"`, `"newHabit"`, `"graduation"`, `"shelf"`
  - `class HabitViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory`

- [ ] **Step 1: `ui/theme/Color.kt` + `Theme.kt` + `Type.kt`**

```kotlin
// Color.kt
package com.manasm.habit100.ui.theme
import androidx.compose.ui.graphics.Color

object HabitColors {
    val done = Color(0xFF1D9E75)
    val missed = Color(0xFFE24B4A)
    val amber = Color(0xFFF4A825)
}
```

```kotlin
// Theme.kt
package com.manasm.habit100.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = HabitColors.done,
    surface = Color(0xFFFBFBF9),
    background = Color(0xFFFBFBF9),
    error = HabitColors.missed,
)
private val Dark = darkColorScheme(
    primary = HabitColors.done,
    surface = Color(0xFF16181A),
    background = Color(0xFF101214),
    error = HabitColors.missed,
)

@Composable
fun HabitTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) Dark else Light,
        typography = HabitTypography,
        content = content,
    )
}
```

```kotlin
// Type.kt
package com.manasm.habit100.ui.theme
import androidx.compose.material3.Typography
val HabitTypography = Typography()
```

- [ ] **Step 2: `AppContainer.kt`**

```kotlin
package com.manasm.habit100

import android.app.Application
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.clock.DevClock
import com.manasm.habit100.clock.DevClockStore
import com.manasm.habit100.clock.SystemClock
import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitRepository

class AppContainer(app: Application) {
    val isDebug: Boolean = BuildConfig.DEBUG

    private val db = HabitDatabase.build(app)
    val devClockStore: DevClockStore? = if (isDebug) DevClockStore(app) else null
    private val devClockImpl: DevClock? = if (isDebug) DevClock(SystemClock()) else null
    val devClock: DevClock? get() = devClockImpl
    val clock: Clock = devClockImpl ?: SystemClock()

    val repository = HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), clock)

    // Task 13 fills this in:
    // val rolloverEngine by lazy { RolloverEngine(RepoRolloverPort(repository, db), clock) }
}
```

- [ ] **Step 3: `HabitApplication.kt` + `HabitViewModelFactory.kt`**

```kotlin
package com.manasm.habit100

import android.app.Application

class HabitApplication : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
```

```kotlin
package com.manasm.habit100.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.manasm.habit100.AppContainer
import com.manasm.habit100.ui.newhabit.NewHabitViewModel
import com.manasm.habit100.ui.shelf.ShelfViewModel
import com.manasm.habit100.ui.tracker.TrackerViewModel

class HabitViewModelFactory(private val c: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
        when (modelClass) {
            TrackerViewModel::class.java -> TrackerViewModel(c.repository, c.clock, c.devClockStore, c.devClock)
            NewHabitViewModel::class.java -> NewHabitViewModel(c.repository)
            ShelfViewModel::class.java -> ShelfViewModel(c.repository)
            else -> error("Unknown VM $modelClass")
        } as T
}
```

(If a VM referenced here isn't created yet, stub the class as `class X(...) : ViewModel()` — later tasks flesh them out. Keep constructor params stable.)

- [ ] **Step 4: `ui/AppNavHost.kt` (skeleton with placeholders)**

```kotlin
package com.manasm.habit100.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.manasm.habit100.AppContainer

object Routes {
    const val TRACKER = "tracker"
    const val NEW_HABIT = "newHabit"
    const val GRADUATION = "graduation"
    const val SHELF = "shelf"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.TRACKER) {
        composable(Routes.TRACKER) { Text("tracker") }
        composable(Routes.NEW_HABIT) { Text("new habit") }
        composable(Routes.GRADUATION) { Text("graduation") }
        composable(Routes.SHELF) { Text("shelf") }
    }
}
```

- [ ] **Step 5: `MainActivity.kt`**

```kotlin
package com.manasm.habit100

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.manasm.habit100.ui.AppNavHost
import com.manasm.habit100.ui.theme.HabitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as HabitApplication).container
        setContent { HabitTheme { AppNavHost(container) } }
    }
}
```

- [ ] **Step 6: Build**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; all existing tests still green.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat(app): AppContainer DI, Material 3 theme, nav skeleton"
```

---

## Task 10: HabitGrid composable + cell-state mapping

**Files:**
- Create: `ui/CellState.kt`, `ui/HabitGrid.kt`
- Test: `app/src/test/java/com/manasm/habit100/ui/CellStateTest.kt` (pure JVM)

**Interfaces:**
- Consumes: `DayLog`, `RuleSnapshot`.
- Produces:
  - `enum class CellState { DONE, MISSED, TODAY, FUTURE }`
  - `fun gridCells(trackLength: Int, currentDay: Int, doneDays: Set<Int>, missedDays: Set<Int>): List<CellState>` — length 100 always (tune-up: first 30 meaningful, rest FUTURE); index i → day i+1
  - `enum class GridSize { HERO, TROPHY, THUMBNAIL }`
  - `@Composable fun HabitGrid(cells: List<CellState>, size: GridSize, modifier: Modifier = Modifier)`

- [ ] **Step 1: Write failing `CellStateTest.kt`**

```kotlin
package com.manasm.habit100.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class CellStateTest {
    @Test fun maps_done_missed_today_future() {
        val cells = gridCells(
            trackLength = 100, currentDay = 4,
            doneDays = setOf(1, 3), missedDays = setOf(2),
        )
        assertEquals(100, cells.size)
        assertEquals(CellState.DONE, cells[0])
        assertEquals(CellState.MISSED, cells[1])
        assertEquals(CellState.DONE, cells[2])
        assertEquals(CellState.TODAY, cells[3])
        assertEquals(CellState.FUTURE, cells[4])
    }

    @Test fun past_current_day_has_no_today_cell() {
        val cells = gridCells(100, currentDay = 105, doneDays = (1..100).toSet(), missedDays = emptySet())
        assertEquals(CellState.DONE, cells[99])
        assertEquals(0, cells.count { it == CellState.TODAY })
    }

    @Test fun tuneup_cells_beyond_30_are_future() {
        val cells = gridCells(30, currentDay = 5, doneDays = setOf(1,2,3,4), missedDays = emptySet())
        assertEquals(CellState.TODAY, cells[4])
        assertEquals(CellState.FUTURE, cells[29])
        assertEquals(CellState.FUTURE, cells[99])
    }
}
```

- [ ] **Step 2: Run FAIL, then write `ui/CellState.kt`**

```kotlin
package com.manasm.habit100.ui

enum class CellState { DONE, MISSED, TODAY, FUTURE }

fun gridCells(
    trackLength: Int,
    currentDay: Int,
    doneDays: Set<Int>,
    missedDays: Set<Int>,
): List<CellState> = (1..100).map { day ->
    when {
        day in doneDays -> CellState.DONE
        day in missedDays -> CellState.MISSED
        day == currentDay && day <= trackLength -> CellState.TODAY
        else -> CellState.FUTURE
    }
}
```

- [ ] **Step 3: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*CellStateTest"`
Expected: PASS.

- [ ] **Step 4: Write `ui/HabitGrid.kt`**

```kotlin
package com.manasm.habit100.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.manasm.habit100.ui.theme.HabitColors

enum class GridSize(val gapFraction: Float, val cornerFraction: Float, val strokeDp: Float) {
    HERO(0.14f, 0.22f, 2f),
    TROPHY(0.12f, 0.22f, 2f),
    THUMBNAIL(0.10f, 0.18f, 1f),
}

@Composable
fun HabitGrid(cells: List<CellState>, size: GridSize, modifier: Modifier = Modifier) {
    val future = Color(0x1F000000)
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cols = 10
        val cell = this.size.width / (cols + (cols - 1) * size.gapFraction)
        val gap = cell * size.gapFraction
        val radius = CornerRadius(cell * size.cornerFraction)
        cells.forEachIndexed { i, state ->
            val cx = (i % cols) * (cell + gap)
            val cy = (i / cols) * (cell + gap)
            val topLeft = Offset(cx, cy)
            val cs = Size(cell, cell)
            when (state) {
                CellState.DONE -> drawRoundRect(HabitColors.done, topLeft, cs, radius)
                CellState.MISSED -> drawRoundRect(HabitColors.missed, topLeft, cs, radius)
                CellState.FUTURE -> drawRoundRect(future, topLeft, cs, radius)
                CellState.TODAY -> drawRoundRect(
                    color = HabitColors.done.copy(alpha = 0.9f),
                    topLeft = topLeft, size = cs, cornerRadius = radius,
                    style = Stroke(width = size.strokeDp.dp.toPx()),
                )
            }
        }
    }
}
```

- [ ] **Step 5: Add a `@Preview`** (debug source) rendering HERO/THUMBNAIL with a sample list. Build.

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/manasm/habit100/ui/CellState.kt app/src/main/java/com/manasm/habit100/ui/HabitGrid.kt app/src/test/java/com/manasm/habit100/ui/CellStateTest.kt
git commit -m "feat(ui): Canvas 10x10 HabitGrid + cell-state mapping"
```

---

## Task 11: New-habit flow

**Files:**
- Create: `ui/newhabit/NewHabitViewModel.kt`, `ui/newhabit/NewHabitScreen.kt`
- Test: `app/src/test/java/com/manasm/habit100/ui/NewHabitViewModelTest.kt` (Robolectric)
- Modify: `ui/AppNavHost.kt`

**Interfaces:**
- Consumes: `HabitRepository.createHabit`, `HabitRepository.observeActive`.
- Produces:
  - `class NewHabitViewModel(private val repo: HabitRepository) : ViewModel()` with `val name: StateFlow<String>`, `fun onNameChange(s: String)`, `val slotBlocked: StateFlow<Boolean>`, `val canСreateEnabled: StateFlow<Boolean>`, `suspend fun create(zoneId: ZoneId): Boolean` (returns success)
  - `@Composable fun NewHabitScreen(vm: NewHabitViewModel, onCreated: () -> Unit, onBack: () -> Unit)`

- [ ] **Step 1: Write failing `NewHabitViewModelTest.kt`**

```kotlin
package com.manasm.habit100.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.ui.newhabit.NewHabitViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewHabitViewModelTest {
    private fun repo(): HabitRepository {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HabitDatabase::class.java)
            .allowMainThreadQueries().addCallback(HabitDatabaseTestHooks.callback()).build()
        return HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), FakeClock(Instant.parse("2026-01-01T09:00:00Z")))
    }

    @Test fun blank_name_cannot_create() {
        val vm = NewHabitViewModel(repo())
        vm.onNameChange("   ")
        assertFalse(vm.canCreateEnabled.value)
        vm.onNameChange("Read")
        assertTrue(vm.canCreateEnabled.value)
    }

    @Test fun create_succeeds_and_blocks_second() = runTest {
        val r = repo()
        val vm = NewHabitViewModel(r)
        vm.onNameChange("Read")
        assertTrue(vm.create(ZoneId.of("America/New_York")))
        val vm2 = NewHabitViewModel(r)
        assertFalse(vm2.create(ZoneId.of("America/New_York")))
    }
}
```

- [ ] **Step 2: Run FAIL → write `NewHabitViewModel.kt`**

```kotlin
package com.manasm.habit100.ui.newhabit

import androidx.lifecycle.ViewModel
import com.manasm.habit100.data.HabitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId

class NewHabitViewModel(private val repo: HabitRepository) : ViewModel() {
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    val canCreateEnabled: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        // recompute on change
    }.asStateFlow()

    fun onNameChange(s: String) {
        _name.value = s
        (canCreateEnabled as MutableStateFlow).value = s.isNotBlank()
    }

    suspend fun create(zoneId: ZoneId): Boolean = try {
        repo.createHabit(_name.value, zoneId)
        true
    } catch (e: IllegalStateException) {
        false
    }
}
```

(If the `as MutableStateFlow` cast reads poorly to the reviewer, use a plain `MutableStateFlow` backing field `_canCreate` and expose `.asStateFlow()`. Do that — it's cleaner. Repeat the pattern in later VMs.)

Cleaner version to actually write:

```kotlin
class NewHabitViewModel(private val repo: HabitRepository) : ViewModel() {
    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()
    private val _canCreate = MutableStateFlow(false)
    val canCreateEnabled: StateFlow<Boolean> = _canCreate.asStateFlow()

    fun onNameChange(s: String) { _name.value = s; _canCreate.value = s.isNotBlank() }

    suspend fun create(zoneId: ZoneId): Boolean = try {
        repo.createHabit(_name.value, zoneId); true
    } catch (e: IllegalStateException) { false }
}
```

- [ ] **Step 3: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*NewHabitViewModelTest"`

- [ ] **Step 4: Write `NewHabitScreen.kt`**

```kotlin
package com.manasm.habit100.ui.newhabit

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewHabitScreen(vm: NewHabitViewModel, onCreated: () -> Unit, onBack: () -> Unit) {
    val name by vm.name.collectAsStateWithLifecycle()
    val canCreate by vm.canCreateEnabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var blocked by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("New habit") }, navigationIcon = {
        TextButton(onClick = onBack) { Text("Cancel") }
    }) }) { pad ->
        Column(Modifier.padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("One habit at a time. 100 days. Up to 10 misses — but never two days in a row.",
                style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = name, onValueChange = vm::onNameChange,
                label = { Text("What will you do daily?") }, singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Timezone: ${ZoneId.systemDefault().id} (locked to this habit)",
                style = MaterialTheme.typography.labelMedium)
            if (blocked) Text("You already have a habit forming. Finish it first.",
                color = MaterialTheme.colorScheme.error)
            Button(
                onClick = {
                    scope.launch {
                        if (vm.create(ZoneId.systemDefault())) onCreated() else blocked = true
                    }
                },
                enabled = canCreate, modifier = Modifier.fillMaxWidth(),
            ) { Text("Start 100 days") }
        }
    }
}
```

- [ ] **Step 5: Wire route in `AppNavHost.kt`** — replace the `Text("new habit")` placeholder:

```kotlin
composable(Routes.NEW_HABIT) {
    val vm: NewHabitViewModel = viewModel(factory = HabitViewModelFactory(container))
    NewHabitScreen(
        vm = vm,
        onCreated = { nav.navigate(Routes.TRACKER) { popUpTo(Routes.TRACKER) { inclusive = true } } },
        onBack = { nav.popBackStack() },
    )
}
```

- [ ] **Step 6: Build + test + commit**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
```bash
git add -A && git commit -m "feat(ui): new-habit flow with single-slot guard"
```

---

## Task 12: Daily tracker screen — end to end

**Files:**
- Create: `ui/tracker/TrackerUiState.kt`, `ui/tracker/TrackerViewModel.kt`, `ui/tracker/TrackerScreen.kt`
- Test: `app/src/test/java/com/manasm/habit100/ui/TrackerViewModelTest.kt` (Robolectric)
- Modify: `ui/AppNavHost.kt` (real tracker + start-destination resolution)

**Interfaces:**
- Consumes: `HabitRepository.observeActive`, `markTodayDone`; `DevClockStore`, `DevClock`; `gridCells`; `HabitGrid`.
- Produces:
  - `sealed interface TrackerUiState { data object Loading; data object Empty; data class Forming(name, dayNumber, trackLength, doneCount, missesLeft, atRisk, canMarkToday, cells: List<CellState>, isTuneUp: Boolean); data class Graduated(habitId: Long) }`
  - `class TrackerViewModel(repo, clock, devClockStore?, devClock?) : ViewModel()` with `val state: StateFlow<TrackerUiState>`, `fun markDone()`, `fun devAdvanceDay()`, `fun devSetToday(date: LocalDate)` (debug only; no-op if store null)
  - `@Composable fun TrackerScreen(vm, onStartHabit: () -> Unit, onGraduated: (Long) -> Unit, onOpenShelf: () -> Unit)`

- [ ] **Step 1: Write failing `TrackerViewModelTest.kt`**

```kotlin
package com.manasm.habit100.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.manasm.habit100.data.HabitDatabase
import com.manasm.habit100.data.HabitDatabaseTestHooks
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.support.FakeClock
import com.manasm.habit100.ui.tracker.TrackerUiState
import com.manasm.habit100.ui.tracker.TrackerViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackerViewModelTest {
    private val zone = ZoneId.of("America/New_York")
    private fun clockAt(d: LocalDate) = FakeClock(d.atTime(9, 0).atZone(zone).toInstant())

    private fun setup(clock: FakeClock): Pair<HabitRepository, TrackerViewModel> {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HabitDatabase::class.java)
            .allowMainThreadQueries().addCallback(HabitDatabaseTestHooks.callback()).build()
        val repo = HabitRepository(db, db.habitDao(), db.dayLogDao(), db.checkinDao(), clock)
        return repo to TrackerViewModel(repo, clock, null, null)
    }

    private suspend fun TrackerViewModel.settled(): TrackerUiState =
        state.filterNot { it is TrackerUiState.Loading }.first()

    @Test fun empty_when_no_habit() = runTest {
        val (_, vm) = setup(clockAt(LocalDate.of(2026, 1, 1)))
        assertTrue(vm.settled() is TrackerUiState.Empty)
    }

    @Test fun forming_after_create_then_mark() = runTest {
        val (repo, vm) = setup(clockAt(LocalDate.of(2026, 1, 1)))
        repo.createHabit("Read", zone)
        val s1 = vm.settled() as TrackerUiState.Forming
        assertEquals(1, s1.dayNumber)
        assertTrue(s1.canMarkToday)
        vm.markDone()
        val s2 = vm.state.first { it is TrackerUiState.Forming && !(it as TrackerUiState.Forming).canMarkToday } as TrackerUiState.Forming
        assertEquals(1, s2.doneCount)
    }

    @Test fun amber_at_risk_after_missed_yesterday() = runTest {
        val clock = clockAt(LocalDate.of(2026, 1, 1))
        val (repo, vm) = setup(clock)
        repo.createHabit("Read", zone)
        vm.markDone()                       // day 1 done
        clock.advanceDays(2)                // day 2 missed, now day 3
        val s = vm.state.first { it is TrackerUiState.Forming && (it as TrackerUiState.Forming).atRisk } as TrackerUiState.Forming
        assertTrue(s.atRisk)
        assertEquals(3, s.dayNumber)
    }

    @Test fun graduated_state_when_engine_says_so() = runTest {
        val clock = clockAt(LocalDate.of(2026, 1, 1))
        val (repo, vm) = setup(clock)
        repo.createHabit("Read", zone)
        repeat(99) { vm.markDone(); clock.advanceDays(1) }
        // day 100, not yet marked -> still forming
        assertTrue(vm.settled() is TrackerUiState.Forming)
        vm.markDone()                       // day 100 done -> graduate
        assertTrue(vm.state.first { it is TrackerUiState.Graduated } is TrackerUiState.Graduated)
    }
}
```

- [ ] **Step 2: Run FAIL → write `TrackerUiState.kt`**

```kotlin
package com.manasm.habit100.ui.tracker

import com.manasm.habit100.ui.CellState

sealed interface TrackerUiState {
    data object Loading : TrackerUiState
    data object Empty : TrackerUiState
    data class Forming(
        val habitId: Long,
        val name: String,
        val dayNumber: Int,
        val trackLength: Int,
        val doneCount: Int,
        val missesLeft: Int,
        val atRisk: Boolean,
        val canMarkToday: Boolean,
        val alreadyDoneToday: Boolean,
        val cells: List<CellState>,
        val isTuneUp: Boolean,
    ) : TrackerUiState
    data class Graduated(val habitId: Long) : TrackerUiState
}
```

- [ ] **Step 3: Write `TrackerViewModel.kt`**

```kotlin
package com.manasm.habit100.ui.tracker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manasm.habit100.clock.Clock
import com.manasm.habit100.clock.DevClock
import com.manasm.habit100.clock.DevClockStore
import com.manasm.habit100.data.ActiveHabit
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.ui.gridCells
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class TrackerViewModel(
    private val repo: HabitRepository,
    private val clock: Clock,
    private val devClockStore: DevClockStore?,
    private val devClock: DevClock?,
) : ViewModel() {

    // A ticker so the snapshot re-derives when the dev clock changes.
    private val refresh = MutableStateFlow(0)

    val state: StateFlow<TrackerUiState> =
        combine(repo.observeActive(), refresh) { active, _ -> active }
            .map { active -> toUiState(active) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackerUiState.Loading)

    private fun toUiState(a: ActiveHabit?): TrackerUiState {
        if (a == null) return TrackerUiState.Empty
        val snap = repo.snapshotOf(a.habit, a.logs)   // fresh clock read
        if (snap.state == HabitState.GRADUATED) return TrackerUiState.Graduated(a.habit.id)
        val done = a.logs.filter { it.status == DayStatus.DONE }.map { it.dayNumber }.toSet()
        val missed = a.logs.filter { it.status == DayStatus.MISSED }.map { it.dayNumber }.toSet()
        // include implied misses for elapsed unmarked past days so the grid is honest pre-rollover
        val impliedMissed = (1 until snap.currentDayNumber)
            .filter { it <= a.habit.attemptTrackLength && it !in done && it !in missed }
            .toSet()
        return TrackerUiState.Forming(
            habitId = a.habit.id,
            name = a.habit.name,
            dayNumber = snap.currentDayNumber.coerceAtMost(a.habit.attemptTrackLength),
            trackLength = a.habit.attemptTrackLength,
            doneCount = snap.doneCount,
            missesLeft = snap.missesLeft,
            atRisk = snap.atRisk,
            canMarkToday = snap.canMarkToday,
            alreadyDoneToday = snap.todayMarkedDone,
            cells = gridCells(a.habit.attemptTrackLength, snap.currentDayNumber, done, missed + impliedMissed),
            isTuneUp = a.habit.status == "tuning_up",
        )
    }

    fun markDone() {
        viewModelScope.launch {
            val a = repo.observeActive().first() ?: return@launch
            runCatching { repo.markTodayDone(a.habit.id) }
            refresh.value++
        }
    }

    fun devAdvanceDay() {
        val store = devClockStore ?: return
        viewModelScope.launch {
            store.addDays(1)
            devClock?.update(store.offsetSeconds.first())
            repo.observeActive().first()?.let { repo.applyTransition(it.habit.id) }
            refresh.value++
        }
    }

    fun devSetToday(date: LocalDate) {
        val store = devClockStore ?: return
        viewModelScope.launch {
            val active = repo.observeActive().first()
            val zone = active?.let { ZoneId.of(it.habit.timeZoneId) } ?: ZoneId.systemDefault()
            val targetInstant = date.atTime(9, 0).atZone(zone).toInstant()
            val delta = targetInstant.epochSecond - clock.now().epochSecond + (store.offsetSeconds.first())
            store.setOffsetSeconds(delta)
            devClock?.update(store.offsetSeconds.first())
            active?.let { repo.applyTransition(it.habit.id) }
            refresh.value++
        }
    }
}
```

(Note: `devSetToday`'s delta math — simpler alternative: `store.setOffsetSeconds(targetInstant.epochSecond - SystemClock().now().epochSecond)`. Use whichever the implementer finds clearer; both land the dev clock on `date` at 09:00. Prefer the `SystemClock()` version — it's unambiguous.)

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*TrackerViewModelTest"`
Expected: PASS.

- [ ] **Step 5: Write `TrackerScreen.kt`**

```kotlin
package com.manasm.habit100.ui.tracker

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.manasm.habit100.BuildConfig
import com.manasm.habit100.ui.CellState
import com.manasm.habit100.ui.GridSize
import com.manasm.habit100.ui.HabitGrid
import com.manasm.habit100.ui.theme.HabitColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    vm: TrackerViewModel,
    onStartHabit: () -> Unit,
    onGraduated: (Long) -> Unit,
    onOpenShelf: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        (state as? TrackerUiState.Graduated)?.let { onGraduated(it.habitId) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("100 Day Habit Tracker") }, actions = {
            TextButton(onClick = onOpenShelf) { Text("Mastered") }
        })
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().padding(20.dp)) {
            when (val s = state) {
                TrackerUiState.Loading -> {}
                TrackerUiState.Empty -> EmptyState(onStartHabit)
                is TrackerUiState.Graduated -> {}
                is TrackerUiState.Forming -> FormingContent(s, vm)
            }
        }
    }
}

@Composable
private fun EmptyState(onStartHabit: () -> Unit) {
    Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
        Text("No habit forming yet.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("Pick one thing. Do it for 100 days.", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onStartHabit) { Text("Start a habit") }
    }
}

@Composable
private fun FormingContent(s: TrackerUiState.Forming, vm: TrackerViewModel) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text("Day ${s.dayNumber} of ${s.trackLength}", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Completed", "${s.doneCount} / ${s.trackLength}", Modifier.weight(1f))
            StatCard("Misses left", "${s.missesLeft} / 10", Modifier.weight(1f))
        }

        if (s.atRisk) {
            Surface(color = HabitColors.amber.copy(alpha = 0.18f), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Don't miss today", fontWeight = FontWeight.Bold, color = HabitColors.amber)
                    Text("You missed yesterday. Miss today and the attempt fails — two in a row.")
                }
            }
        }

        HabitGrid(cells = s.cells, size = GridSize.HERO, modifier = Modifier.fillMaxWidth())

        Legend()

        Button(
            onClick = vm::markDone,
            enabled = s.canMarkToday,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) { Text(if (s.alreadyDoneToday) "Done for today ✓" else "Mark today done") }

        if (BuildConfig.DEBUG) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::devAdvanceDay) { Text("dev: +1 day") }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, tonalElevation = 1.dp) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        LegendDot(HabitColors.done, "done")
        LegendDot(HabitColors.missed, "missed")
        LegendDot(Color(0x33000000), "today")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(color = color, shape = MaterialTheme.shapes.small, modifier = Modifier.size(12.dp)) {}
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
```

- [ ] **Step 6: Replace tracker route + add start-destination resolver in `AppNavHost.kt`**

```kotlin
composable(Routes.TRACKER) {
    val vm: TrackerViewModel = viewModel(factory = HabitViewModelFactory(container))
    TrackerScreen(
        vm = vm,
        onStartHabit = { nav.navigate(Routes.NEW_HABIT) },
        onGraduated = { id -> nav.navigate("${Routes.GRADUATION}/$id") },
        onOpenShelf = { nav.navigate(Routes.SHELF) },
    )
}
```

(Graduation route becomes `"graduation/{habitId}"` in Task 15; for now point it at a placeholder that reads the arg.)

- [ ] **Step 7: Build the APK and sanity-check tests**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`; APK at `app/build/outputs/apk/debug/app-debug.apk`. All unit + Robolectric tests green.

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(ui): daily tracker end-to-end — grid, stats, amber warning, mark-done, dev clock"
```

**CHECKPOINT 2** — installable APK: create a habit, mark days, see the grid fill, trigger the amber warning via `dev: +1 day`. Stop for review.

---

## Task 13: Rollover engine

**Files:**
- Create: `rollover/RolloverPort.kt`, `rollover/RolloverEngine.kt`, `data/RepoRolloverPort.kt`
- Test: `app/src/test/java/com/manasm/habit100/rollover/RolloverEngineTest.kt` (pure JVM, fake port)

**Interfaces:**
- Consumes: `HabitEntity`, `DayLog`, `HabitRules`, `Clock`, `currentDayNumber`, `dateForDay`.
- Produces:
  - ```kotlin
    interface RolloverPort {
        suspend fun activeHabit(): HabitEntity?
        suspend fun loggedDays(habitId: Long, attempt: Int): List<DayLog>
        suspend fun insertMissedDays(habitId: Long, attempt: Int, startDate: LocalDate, days: List<Int>, markedAt: Instant)
        suspend fun onFailed(habit: HabitEntity, reason: FailureReason, onDay: Int)
        suspend fun onGraduated(habit: HabitEntity)
    }
    ```
  - `class RolloverEngine(private val port: RolloverPort, private val clock: Clock) { suspend fun run() }`
  - `class RepoRolloverPort(private val repo: HabitRepository, private val db: HabitDatabase) : RolloverPort`

- [ ] **Step 1: Write `RolloverPort.kt`** (exact interface above).

- [ ] **Step 2: Write failing `RolloverEngineTest.kt`**

```kotlin
package com.manasm.habit100.rollover

import com.manasm.habit100.data.HabitEntity
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.FailureReason
import com.manasm.habit100.support.FakeClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class RolloverEngineTest {
    private val zone = ZoneId.of("America/New_York")
    private val start = LocalDate.of(2026, 1, 1)

    private fun habit(status: String = "forming", trackLength: Int = 100, attempt: Int = 1) =
        HabitEntity(
            id = 7, name = "Read", timeZoneId = zone.id, status = status,
            currentAttempt = attempt, attemptStartDate = start, attemptTrackLength = trackLength,
            trophyAttempt = if (status == "tuning_up") 1 else null, slipped = status == "tuning_up",
            createdAt = Instant.EPOCH, graduatedAt = null, failureReason = null, failedOnDay = null,
        )

    private class FakePort(
        var habit: HabitEntity?,
        val logs: MutableList<DayLog>,
    ) : RolloverPort {
        val inserted = mutableListOf<Int>()
        var failed: Pair<FailureReason, Int>? = null
        var graduated = false
        override suspend fun activeHabit() = habit
        override suspend fun loggedDays(habitId: Long, attempt: Int) = logs.toList()
        override suspend fun insertMissedDays(habitId: Long, attempt: Int, startDate: LocalDate, days: List<Int>, markedAt: Instant) {
            inserted += days; days.forEach { logs += DayLog(it, DayStatus.MISSED) }
        }
        override suspend fun onFailed(habit: HabitEntity, reason: FailureReason, onDay: Int) { failed = reason to onDay; this.habit = null }
        override suspend fun onGraduated(habit: HabitEntity) { graduated = true; this.habit = null }
    }

    private fun clockOnDay(n: Int) =
        FakeClock(start.plusDays((n - 1).toLong()).atTime(12, 0).atZone(zone).toInstant())

    @Test fun fills_elapsed_unmarked_days_in_order() = runTest {
        val port = FakePort(habit(), mutableListOf(DayLog(1, DayStatus.DONE)))
        RolloverEngine(port, clockOnDay(5)).run()   // days 2,3,4 elapsed unmarked
        // day 2 & 3 consecutive -> fails at day 3, only inserts up to failure? -> spec: insert then evaluate
        assertEquals(listOf(2, 3, 4), port.inserted)
        assertEquals(FailureReason.TWO_IN_A_ROW to 3, port.failed)
    }

    @Test fun no_op_when_nothing_elapsed() = runTest {
        val port = FakePort(habit(), mutableListOf(DayLog(1, DayStatus.DONE)))
        RolloverEngine(port, clockOnDay(1)).run()
        assertTrue(port.inserted.isEmpty())
        assertNull(port.failed)
        assertFalse(port.graduated)
    }

    @Test fun idempotent_second_run_does_nothing() = runTest {
        val port = FakePort(habit(), mutableListOf(DayLog(1, DayStatus.DONE)))
        val eng = RolloverEngine(port, clockOnDay(3))
        eng.run()
        val insertedAfterFirst = port.inserted.toList()
        eng.run()
        assertEquals(insertedAfterFirst, port.inserted)
    }

    @Test fun graduates_when_window_complete_clean() = runTest {
        val logs = (1..100).map { DayLog(it, DayStatus.DONE) }.toMutableList()
        val port = FakePort(habit(), logs)
        RolloverEngine(port, clockOnDay(101)).run()
        assertTrue(port.graduated)
    }
}
```

- [ ] **Step 3: Run FAIL → write `RolloverEngine.kt`**

```kotlin
package com.manasm.habit100.rollover

import com.manasm.habit100.clock.Clock
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.HabitРules
import com.manasm.habit100.domain.HabitState
import com.manasm.habit100.domain.RuleInput
import com.manasm.habit100.domain.currentDayNumber
import java.time.ZoneId

class RolloverEngine(private val port: RolloverPort, private val clock: Clock) {

    suspend fun run() {
        val habit = port.activeHabit() ?: return
        val zone = ZoneId.of(habit.timeZoneId)
        val now = clock.now()
        val current = currentDayNumber(habit.attemptStartDate, zone, now)

        val logs = port.loggedDays(habit.id, habit.currentAttempt)
        val loggedNums = logs.map { it.dayNumber }.toHashSet()

        val lastPastDay = minOf(current - 1, habit.attemptTrackLength)
        val missing = (1..lastPastDay).filter { it !in loggedNums }
        if (missing.isNotEmpty()) {
            port.insertMissedDays(habit.id, habit.currentAttempt, habit.attemptStartDate, missing, now)
        }

        val allLogs = logs + missing.map { DayLog(it, DayStatus.MISSED) }
        val snap = HabitRules.evaluate(
            RuleInput(habit.attemptStartDate, zone, habit.attemptTrackLength, allLogs), now,
        )
        when (snap.state) {
            HabitState.FAILED -> port.onFailed(habit, snap.failureReason!!, snap.failedOnDay!!)
            HabitState.GRADUATED -> port.onGraduated(habit)
            HabitState.FORMING -> Unit
        }
    }
}
```

(Fix the deliberate typo `HabitРules`/`HabitRules` when transcribing — it must be `com.manasm.habit100.domain.HabitRules`.)

- [ ] **Step 4: Run, expect PASS**

Run: `./gradlew :app:testDebugUnitTest --tests "*RolloverEngineTest"`

- [ ] **Step 5: Write `data/RepoRolloverPort.kt`**

```kotlin
package com.manasm.habit100.data

import androidx.room.withTransaction
import com.manasm.habit100.domain.DayLog
import com.manasm.habit100.domain.FailureReason
import com.manasm.habit100.domain.dateForDay
import com.manasm.habit100.rollover.RolloverPort
import java.time.Instant
import java.time.LocalDate

class RepoRolloverPort(
    private val repo: HabitRepository,
    private val db: HabitDatabase,
    private val habitDao: HabitDao,
    private val dayLogDao: DayLogDao,
) : RolloverPort {

    override suspend fun activeHabit(): HabitEntity? = habitDao.activeOnce()

    override suspend fun loggedDays(habitId: Long, attempt: Int): List<DayLog> =
        dayLogDao.forAttempt(habitId, attempt).map { it.toDayLog() }

    override suspend fun insertMissedDays(
        habitId: Long, attempt: Int, startDate: LocalDate, days: List<Int>, markedAt: Instant,
    ) {
        db.withTransaction {
            dayLogDao.insertAll(
                days.sorted().map {
                    DayLogEntity(
                        habitId = habitId, attempt = attempt, dayNumber = it,
                        logDate = dateForDay(startDate, it), status = "missed", markedAt = markedAt,
                    )
                }
            )
        }
    }

    override suspend fun onFailed(habit: HabitEntity, reason: FailureReason, onDay: Int) {
        repo.applyTransition(habit.id)
    }

    override suspend fun onGraduated(habit: HabitEntity) {
        repo.applyTransition(habit.id)
    }
}
```

(Both `onFailed`/`onGraduated` just delegate to `repo.applyTransition`, which re-evaluates from the now-materialized logs and writes the correct terminal status — single source of truth. Wire `RolloverEngine` into `AppContainer`: `val rolloverEngine by lazy { RolloverEngine(RepoRolloverPort(repository, db, db.habitDao(), db.dayLogDao()), clock) }` — expose `db` or pass daos.)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/manasm/habit100/rollover app/src/main/java/com/manasm/habit100/data/RepoRolloverPort.kt app/src/test/java/com/manasm/habit100/rollover
git commit -m "feat(rollover): catch-up engine — fill misses in order, evaluate failure/graduation"
```

---

## Task 14: Rollover wiring — foreground observer + WorkManager backstop

**Files:**
- Create: `rollover/RolloverWorker.kt`
- Modify: `AppContainer.kt` (expose `rolloverEngine`, `runRolloverNow()`), `HabitApplication.kt` (ProcessLifecycle observer + schedule worker)
- Test: `app/src/test/java/com/manasm/habit100/rollover/RolloverWorkerTest.kt` (Robolectric, `TestListenableWorkerBuilder`)

**Interfaces:**
- Produces:
  - `AppContainer.rolloverEngine: RolloverEngine`; `suspend fun AppContainer.runRolloverNow()`
  - `class RolloverWorker(ctx, params) : CoroutineWorker` — pulls `container.rolloverEngine` from `applicationContext as HabitApplication`
  - `HabitApplication` registers `ProcessLifecycleOwner.get().lifecycle.addObserver { onStart -> scope.launch { container.runRolloverNow() } }` and enqueues a `PeriodicWorkRequestBuilder<RolloverWorker>(1, TimeUnit.DAYS)` as `KEEP`.

- [ ] **Step 1: Write `RolloverWorker.kt`**

```kotlin
package com.manasm.habit100.rollover

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.manasm.habit100.HabitApplication

class RolloverWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as HabitApplication).container
        return try {
            container.rolloverEngine.run()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
```

- [ ] **Step 2: Wire `HabitApplication`**

```kotlin
package com.manasm.habit100

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.manasm.habit100.rollover.RolloverWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class HabitApplication : Application() {
    lateinit var container: AppContainer
        private set
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                appScope.launch { runCatching { container.rolloverEngine.run() } }
            }
        })

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "rollover",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RolloverWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
```

- [ ] **Step 3: Add `rolloverEngine` to `AppContainer`**

```kotlin
    val rolloverEngine: com.manasm.habit100.rollover.RolloverEngine by lazy {
        com.manasm.habit100.rollover.RolloverEngine(
            com.manasm.habit100.data.RepoRolloverPort(repository, db, db.habitDao(), db.dayLogDao()),
            clock,
        )
    }
```

(Change `private val db` to `internal val db` or keep `db` accessible within the file — `AppContainer` and the lazy are in the same file, so `private` is fine.)

- [ ] **Step 4: Write failing `RolloverWorkerTest.kt`**

```kotlin
package com.manasm.habit100.rollover

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.manasm.habit100.HabitApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = HabitApplication::class)
class RolloverWorkerTest {
    @Test fun worker_runs_and_succeeds_with_no_active_habit() {
        val ctx = ApplicationProvider.getApplicationContext<HabitApplication>()
        val worker = TestListenableWorkerBuilder<RolloverWorker>(ctx).build()
        val result = runBlocking { worker.doWork() }
        assertEquals(ListenableWorker.Result.success(), result)
    }
}
```

Add `testImplementation("androidx.work:work-testing:2.10.0")` to the version catalog + `app/build.gradle.kts`.

- [ ] **Step 5: Run + build APK + commit**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
```bash
git add -A && git commit -m "feat(rollover): foreground catch-up on app start + daily WorkManager backstop"
```

---

## Task 15: Graduation screen + transitions + share

**Files:**
- Create: `ui/graduation/GraduationViewModel.kt`, `ui/graduation/GraduationScreen.kt`, `ui/share/GridShare.kt`
- Test: `app/src/test/java/com/manasm/habit100/ui/GraduationViewModelTest.kt` (Robolectric)
- Modify: `ui/AppNavHost.kt`, `ui/HabitViewModelFactory.kt`

**Interfaces:**
- Consumes: `HabitRepository` (needs new `suspend fun habitWithTrophyLogs(habitId): Pair<HabitEntity, List<DayLog>>` and `suspend fun startNextHabitAllowed(): Boolean`).
- Produces:
  - `data class GraduationUi(val name: String, val daysDone: Int, val bestStreak: Int, val missesUsed: Int, val cells: List<CellState>)`
  - `class GraduationViewModel(repo, habitId: Long) : ViewModel()` — `val ui: StateFlow<GraduationUi?>`
  - `@Composable fun GraduationScreen(vm, onStartNext: () -> Unit, onKeepGoing: () -> Unit)`
  - `fun shareGrid(context: Context, cells: List<CellState>, caption: String)` — renders an offscreen bitmap of `HabitGrid` and fires `ACTION_SEND` (image/png via FileProvider)

- [ ] **Step 1: Add repo helpers + tests**

```kotlin
// in HabitRepository
suspend fun trophyView(habitId: Long): Pair<HabitEntity, List<DayLog>>? {
    val habit = habitDao.byId(habitId) ?: return null
    val attempt = habit.trophyAttempt ?: habit.currentAttempt
    val logs = dayLogDao.forAttempt(habit.id, attempt).map { it.toDayLog() }
    return habit to logs
}
suspend fun canStartNew(): Boolean = habitDao.activeCount() == 0
```

Test in `HabitRepositoryTest.kt`:
```kotlin
@Test fun trophy_view_after_graduation() = runTest {
    repo.createHabit("Read", zone)
    val id = repo.observeActive().first()!!.habit.id
    repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
    val (h, logs) = repo.trophyView(id)!!
    assertEquals("mastered", h.status)
    assertEquals(100, logs.count { it.status == com.manasm.habit100.domain.DayStatus.DONE })
    assertTrue(repo.canStartNew())
}
```

- [ ] **Step 2: Write failing `GraduationViewModelTest.kt`**

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GraduationViewModelTest {
    // build repo like other tests; graduate a habit with 2 non-consecutive misses,
    // assert ui.daysDone == 98, ui.missesUsed == 2, ui.bestStreak computed, cells size 100.
}
```

(Write the full body mirroring `HabitRepositoryTest` setup: create habit, mark days 1..100 skipping day 10 and day 40, advancing the clock each day; then `GraduationViewModel(repo, id).ui.filterNotNull().first()` and assert.)

- [ ] **Step 3: Write `GraduationViewModel.kt`**

```kotlin
package com.manasm.habit100.ui.graduation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.manasm.habit100.data.HabitRepository
import com.manasm.habit100.domain.DayStatus
import com.manasm.habit100.domain.RuleInput
import com.manasm.habit100.domain.HabitRules
import com.manasm.habit100.ui.CellState
import com.manasm.habit100.ui.gridCells
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class GraduationUi(
    val name: String, val daysDone: Int, val bestStreak: Int,
    val missesUsed: Int, val cells: List<CellState>,
)

class GraduationViewModel(
    private val repo: HabitRepository,
    private val habitId: Long,
) : ViewModel() {
    private val _ui = MutableStateFlow<GraduationUi?>(null)
    val ui: StateFlow<GraduationUi?> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val (habit, logs) = repo.trophyView(habitId) ?: return@launch
            // Evaluate at a time past the window so the snapshot is the final one.
            val snap = HabitRules.evaluate(
                RuleInput(habit.attemptStartDate, ZoneId.of(habit.timeZoneId), habit.attemptTrackLength, logs),
                habit.attemptStartDate.plusDays(habit.attemptTrackLength.toLong())
                    .atStartOfDay(ZoneId.of(habit.timeZoneId)).toInstant(),
            )
            val done = logs.filter { it.status == DayStatus.DONE }.map { it.dayNumber }.toSet()
            val missed = logs.filter { it.status == DayStatus.MISSED }.map { it.dayNumber }.toSet()
            _ui.value = GraduationUi(
                name = habit.name,
                daysDone = snap.doneCount,
                bestStreak = snap.bestStreak,
                missesUsed = snap.missCount,
                cells = gridCells(habit.attemptTrackLength, currentDay = habit.attemptTrackLength + 1, done, missed),
            )
        }
    }
}
```

- [ ] **Step 4: Write `GraduationScreen.kt`** — success check, "It's a habit now", habit name, `HabitGrid(TROPHY)`, three `StatCard`s (Days done / Best streak / Misses used), two buttons, and a subtle `TextButton("Share")` calling `shareGrid`.

```kotlin
@Composable
fun GraduationScreen(vm: GraduationViewModel, onStartNext: () -> Unit, onKeepGoing: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val u = ui ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓", style = MaterialTheme.typography.displayMedium, color = HabitColors.done)
        Text("100 days complete", style = MaterialTheme.typography.titleMedium)
        Text("It's a habit now", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(u.name, style = MaterialTheme.typography.titleLarge)
        HabitGrid(u.cells, GridSize.TROPHY, Modifier.fillMaxWidth(0.9f))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Days done", "${u.daysDone}", Modifier.weight(1f))
            StatCard("Best streak", "${u.bestStreak}", Modifier.weight(1f))
            StatCard("Misses used", "${u.missesUsed}", Modifier.weight(1f))
        }
        Button(onClick = onStartNext, Modifier.fillMaxWidth()) { Text("Start your next habit") }
        OutlinedButton(onClick = onKeepGoing, Modifier.fillMaxWidth()) { Text("Keep this one going") }
        TextButton(onClick = { shareGrid(ctx, u.cells, "${u.name}: 100 days. It's a habit now.") }) {
            Text("Share", style = MaterialTheme.typography.labelMedium)
        }
    }
}
```

(`StatCard` is currently `private` in `tracker` — extract it to `ui/components/StatCard.kt` as a public composable and update both call sites. Do that extraction as part of this step.)

- [ ] **Step 5: Write `ui/share/GridShare.kt`**

```kotlin
package com.manasm.habit100.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import androidx.core.content.FileProvider
import com.manasm.habit100.ui.CellState
import com.manasm.habit100.ui.theme.HabitColors
import java.io.File

fun shareGrid(context: Context, cells: List<CellState>, caption: String) {
    val px = 1080
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    c.drawColor(android.graphics.Color.WHITE)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val gap = px * 0.012f
    val cell = (px - gap * 9) / 10f
    val future = android.graphics.Color.argb(31, 0, 0, 0)
    cells.forEachIndexed { i, s ->
        val x = (i % 10) * (cell + gap); val y = (i / 10) * (cell + gap)
        paint.color = when (s) {
            CellState.DONE -> HabitColors.done.value.toInt() or 0
            CellState.MISSED -> HabitColors.missed.value.toInt() or 0
            else -> future
        }
        // Use plain ARGB ints to avoid Compose Color -> Android color pitfalls:
        paint.color = when (s) {
            CellState.DONE -> android.graphics.Color.rgb(0x1D, 0x9E, 0x75)
            CellState.MISSED -> android.graphics.Color.rgb(0xE2, 0x4B, 0x4A)
            else -> future
        }
        c.drawRoundRect(x, y, x + cell, y + cell, cell * 0.2f, cell * 0.2f, paint)
    }
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "grid.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, caption)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your grid"))
}
```

Add a `FileProvider` to `AndroidManifest.xml` + `res/xml/file_paths.xml` (`<cache-path name="shared" path="shared/" />`).

- [ ] **Step 6: Nav wiring**

`AppNavHost.kt`: route `"graduation/{habitId}"` with a `navArgument`, `GraduationViewModel` built via a factory that takes the id (use `viewModel(factory = ...)` with a small inline factory or `CreationExtras`). `onStartNext` → `newHabit` (pop graduation); `onKeepGoing` → `shelf`. Also update `HabitViewModelFactory` if you route graduation VM through it (needs the id — simplest is a dedicated inline factory in the composable).

- [ ] **Step 7: Run + build + commit**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
```bash
git add -A && git commit -m "feat(ui): graduation screen — trophy grid, final stats, start-next/keep-going, share"
```

---

## Task 16: Maintenance pulse — data + due logic

**Files:**
- Modify: `data/HabitRepository.kt` (checkin methods + mastered view), create `data/RepoModels.kt` addition `MasteredHabitRow`
- Create: `domain/Maintenance.kt` (`fun isCheckInDue(period: String, checkins: List<String>): Boolean` — trivial but tested)
- Test: `app/src/test/java/com/manasm/habit100/data/MaintenanceTest.kt` (Robolectric)

**Interfaces:**
- Produces:
  - `data class MasteredHabitRow(val habit: HabitEntity, val trophyCells: List<CellState>, val checkInDue: Boolean, val maintenanceStreakMonths: Int, val slipped: Boolean, val slotFree: Boolean)`
  - `fun HabitRepository.observeMastered(): Flow<List<MasteredHabitRow>>`
  - `suspend fun HabitRepository.checkIn(habitId: Long, strong: Boolean)`
  - `suspend fun HabitRepository.reportSlip(habitId: Long)`
  - `fun isCheckInDue(currentPeriod: String, existingPeriods: Set<String>): Boolean = currentPeriod !in existingPeriods`

- [ ] **Step 1: `domain/Maintenance.kt` + test**

```kotlin
package com.manasm.habit100.domain

fun isCheckInDue(currentPeriod: String, existingPeriods: Set<String>): Boolean =
    currentPeriod !in existingPeriods

fun maintenanceStreakMonths(currentPeriod: String, strongPeriods: Set<String>): Int {
    // count back consecutive months (YYYY-MM) present in strongPeriods, ending at the
    // month before currentPeriod (or currentPeriod itself if present)
    var p = java.time.YearMonth.parse(currentPeriod)
    var count = 0
    while (strongPeriods.contains(p.toString())) { count++; p = p.minusMonths(1) }
    return count
}
```

Test both: `isCheckInDue` true/false; `maintenanceStreakMonths` with `{2026-09, 2026-08, 2026-06}` and current `2026-09` → 2.

- [ ] **Step 2: Repo checkin methods**

```kotlin
suspend fun checkIn(habitId: Long, strong: Boolean) {
    val habit = habitDao.byId(habitId) ?: return
    val period = periodOf(ZoneId.of(habit.timeZoneId), clock.now())
    checkinDao.insert(MaintenanceCheckinEntity(
        habitId = habitId, period = period,
        status = if (strong) "strong" else "slipped", checkedAt = clock.now(),
    ))
    if (!strong) habitDao.update(habit.copy(slipped = true))
}

suspend fun reportSlip(habitId: Long) = checkIn(habitId, strong = false)
```

- [ ] **Step 3: `observeMastered()` combining habit list + per-habit checkins + trophy logs**

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
fun observeMastered(): Flow<List<MasteredHabitRow>> =
    combine(habitDao.observeMastered(), habitDao.observeActive()) { list, active ->
        list to (active == null)
    }.flatMapLatest { (list, slotFree) ->
        if (list.isEmpty()) flowOf(emptyList())
        else combine(list.map { h -> rowFlow(h, slotFree) }) { it.toList() }
    }

private fun rowFlow(h: HabitEntity, slotFree: Boolean): Flow<MasteredHabitRow> =
    combine(
        checkinDao.observeForHabit(h.id),
        dayLogDao.observeForAttempt(h.id, h.trophyAttempt ?: h.currentAttempt),
    ) { checkins, logs ->
        val now = clock.now(); val zone = ZoneId.of(h.timeZoneId)
        val period = periodOf(zone, now)
        val periods = checkins.map { it.period }.toSet()
        val strong = checkins.filter { it.status == "strong" }.map { it.period }.toSet()
        val done = logs.filter { it.status == "done".let { _ -> it.status == "done" } }.map { it.dayNumber }.toSet()
        // simpler:
        val doneDays = logs.filter { it.status == "done" }.map { it.dayNumber }.toSet()
        val missedDays = logs.filter { it.status == "missed" }.map { it.dayNumber }.toSet()
        MasteredHabitRow(
            habit = h,
            trophyCells = gridCells(h.attemptTrackLength.coerceAtLeast(100).let { 100 },
                currentDay = 101, doneDays, missedDays),
            checkInDue = isCheckInDue(period, periods) && h.status == "mastered",
            maintenanceStreakMonths = maintenanceStreakMonths(period, strong),
            slipped = h.slipped,
            slotFree = slotFree,
        )
    }
```

(Clean this up while implementing — the point is: trophy grid always 100 cells from the trophy attempt's logs, `checkInDue` from period, streak from strong check-ins. `gridCells` first arg should be `100` for a mastered habit's trophy.)

- [ ] **Step 4: `MaintenanceTest.kt` (Robolectric)** — create + graduate a habit, assert `observeMastered().first()` has one row with `checkInDue == true`; call `checkIn(id, strong = true)`; assert `checkInDue == false` and `maintenanceStreakMonths == 1`; call `reportSlip`; assert `slipped == true`.

- [ ] **Step 5: Run + commit**

```bash
git add -A && git commit -m "feat(data): monthly maintenance pulse — due logic, check-ins, slip flag"
```

---

## Task 17: Mastered shelf screen + Forming-now footer

**Files:**
- Create: `ui/shelf/ShelfUiState.kt`, `ui/shelf/ShelfViewModel.kt`, `ui/shelf/ShelfScreen.kt`
- Test: `app/src/test/java/com/manasm/habit100/ui/ShelfViewModelTest.kt` (Robolectric)
- Modify: `ui/AppNavHost.kt`

**Interfaces:**
- Consumes: `HabitRepository.observeMastered()`, `observeActive()`, `checkIn`, `reportSlip`, `startTuneUp` (Task 18 — leave the button calling a `vm.startTuneUp(id)` that Task 18 implements; for now stub it to call `repo` method that will exist).
- Produces:
  - `data class ShelfRow(id, name, cells: List<CellState>, badge: Badge, subtitle: String, slipped: Boolean, canTuneUp: Boolean)` ; `enum Badge { CHECK_IN, GOING_STRONG, SLIPPED }`
  - `data class FormingNow(name: String, dayNumber: Int, trackLength: Int, missesLeft: Int, progress: Float)` (nullable)
  - `class ShelfViewModel(repo) : ViewModel()` — `val rows: StateFlow<List<ShelfRow>>`, `val formingNow: StateFlow<FormingNow?>`, `val masteredCount: StateFlow<Int>`, `fun confirm(id)`, `fun slip(id)`, `fun startTuneUp(id)`
  - `@Composable fun ShelfScreen(vm, onBack: () -> Unit)`

- [ ] **Step 1: Write failing `ShelfViewModelTest.kt`** — graduate one habit; assert `rows` size 1, `badge == CHECK_IN`, `masteredCount == 1`; create a second forming habit; assert `formingNow != null` with `dayNumber == 1`; assert the mastered row's `canTuneUp == false` (slot occupied).

- [ ] **Step 2: `ShelfUiState.kt` + `ShelfViewModel.kt`**

```kotlin
class ShelfViewModel(private val repo: HabitRepository) : ViewModel() {
    val rows: StateFlow<List<ShelfRow>> = repo.observeMastered()
        .map { list -> list.map { it.toRow() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val masteredCount: StateFlow<Int> = rows.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val formingNow: StateFlow<FormingNow?> = repo.observeActive()
        .map { a -> a?.let {
            FormingNow(
                name = it.habit.name,
                dayNumber = it.snapshot.currentDayNumber.coerceAtMost(it.habit.attemptTrackLength),
                trackLength = it.habit.attemptTrackLength,
                missesLeft = it.snapshot.missesLeft,
                progress = it.snapshot.effectiveDay.toFloat() / it.habit.attemptTrackLength,
            )
        } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun confirm(id: Long) = viewModelScope.launch { repo.checkIn(id, strong = true) }
    fun slip(id: Long) = viewModelScope.launch { repo.reportSlip(id) }
    fun startTuneUp(id: Long) = viewModelScope.launch { runCatching { repo.startTuneUp(id) } }
}

private fun MasteredHabitRow.toRow(): ShelfRow {
    val badge = when {
        slipped -> Badge.SLIPPED
        checkInDue -> Badge.CHECK_IN
        else -> Badge.GOING_STRONG
    }
    val subtitle = when {
        slipped -> "Slipped — lock it back in"
        maintenanceStreakMonths > 0 -> "$maintenanceStreakMonths month streak"
        habit.graduatedAt != null -> "Graduated"
        else -> ""
    }
    return ShelfRow(habit.id, habit.name, trophyCells, badge, subtitle, slipped,
        canTuneUp = slipped && slotFree && habit.status == "mastered")
}
```

- [ ] **Step 3: `ShelfScreen.kt`** — `LazyColumn`:
  - header `"Mastered"` + count + `"Tap a habit to check in this month"`
  - one `ShelfRowItem` per row: `HabitGrid(THUMBNAIL, size 64.dp)`, name, subtitle, badge chip (amber `CHECK_IN`/`SLIPPED`, green `GOING_STRONG`). Tapping a `CHECK_IN` row expands inline `Confirm` / `Report a slip`. A `SLIPPED` row shows `Start 30-day tune-up` (enabled = `canTuneUp`). Overflow `I slipped` on any row.
  - a `Divider` then **"Forming now"** section: if `formingNow != null` → name, "Day N of M", "misses left K", thin `LinearProgressIndicator(progress)`. Else → "No habit forming — start one".

- [ ] **Step 4: Nav** — `AppNavHost` route `Routes.SHELF` → `ShelfScreen(viewModel(factory=...), onBack = { nav.popBackStack() })`.

- [ ] **Step 5: Run + build + commit**

```bash
git add -A && git commit -m "feat(ui): mastered shelf — rows, monthly badges, check-in, forming-now footer"
```

---

## Task 18: 30-day tune-up

**Files:**
- Modify: `data/HabitRepository.kt` (`startTuneUp`), `ui/shelf/ShelfScreen.kt` (confirm dialog already calls it)
- Test: add cases to `HabitRepositoryTest.kt`

**Interfaces:**
- Produces: `suspend fun HabitRepository.startTuneUp(habitId: Long)` — requires `habit.status == "mastered"`, `habit.slipped`, and `activeCount() == 0`. Bumps `currentAttempt`, sets `status = "tuning_up"`, `attemptStartDate = today`, `attemptTrackLength = 30`. Keeps `trophyAttempt` pointing at the original 100-day board.

- [ ] **Step 1: Write failing tests in `HabitRepositoryTest.kt`**

```kotlin
@Test fun tune_up_takes_the_slot_and_is_30_days() = runTest {
    repo.createHabit("Read", zone)
    val id = repo.observeActive().first()!!.habit.id
    repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
    repo.reportSlip(id)
    repo.startTuneUp(id)
    val a = repo.observeActive().first()!!
    assertEquals("tuning_up", a.habit.status)
    assertEquals(30, a.habit.attemptTrackLength)
    assertEquals(2, a.habit.currentAttempt)
    assertEquals(1, a.habit.trophyAttempt)
    assertFalse(repo.canStartNew())
}

@Test fun tune_up_blocked_when_slot_busy() = runTest {
    // graduate habit A, slip it; create habit B (forming); startTuneUp(A) must throw
    repo.createHabit("Read", zone)
    val a = repo.observeActive().first()!!.habit.id
    repeat(100) { repo.markTodayDone(a); clock.advanceDays(1) }
    repo.reportSlip(a)
    repo.createHabit("Run", zone)
    assertThrows(IllegalStateException::class.java) { runTest { repo.startTuneUp(a) } }
}

@Test fun graduating_tune_up_clears_slip_and_keeps_trophy() = runTest {
    repo.createHabit("Read", zone)
    val id = repo.observeActive().first()!!.habit.id
    repeat(100) { repo.markTodayDone(id); clock.advanceDays(1) }
    repo.reportSlip(id)
    repo.startTuneUp(id)
    repeat(30) { repo.markTodayDone(id); clock.advanceDays(1) }
    val h = db.habitDao().byId(id)!!
    assertEquals("mastered", h.status)
    assertFalse(h.slipped)
    assertEquals(1, h.trophyAttempt)
}
```

- [ ] **Step 2: Implement `startTuneUp`**

```kotlin
suspend fun startTuneUp(habitId: Long) {
    db.withTransaction {
        val habit = habitDao.byId(habitId) ?: return@withTransaction
        check(habit.status == "mastered") { "Only a mastered habit can tune up" }
        check(habit.slipped) { "Habit is not slipped" }
        check(habitDao.activeCount() == 0) { "A habit is already forming" }
        val zone = ZoneId.of(habit.timeZoneId)
        habitDao.update(
            habit.copy(
                status = "tuning_up",
                currentAttempt = habit.currentAttempt + 1,
                attemptStartDate = clock.now().atZone(zone).toLocalDate(),
                attemptTrackLength = 30,
            )
        )
    }
}
```

- [ ] **Step 3: Run + commit**

Run: `./gradlew :app:testDebugUnitTest --tests "*HabitRepositoryTest"`
```bash
git add -A && git commit -m "feat: 30-day tune-up for slipped mastered habits (takes the forming slot)"
```

---

## Task 19: Polish — empty states, dark theme, lint, androidTest stubs

**Files:**
- Modify: theme, screens; Create: `app/src/androidTest/java/com/manasm/habit100/TrackerScreenTest.kt`
- Create: `app/src/main/res/values/strings.xml` entries; app icon (use `@mipmap/ic_launcher` default or a simple adaptive icon)

- [ ] **Step 1: Adaptive launcher icon** — add `res/mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` with a flat green foreground (a 10×10 dot motif) on a neutral background; reference in manifest `android:icon` / `android:roundIcon`.

- [ ] **Step 2: Empty / edge states pass** — tracker Empty state (done); shelf empty ("Nothing mastered yet — your first 100 days are how it starts"); new-habit blocked banner; graduation share failure (wrap `startActivity` in `runCatching`). Verify all four render without a habit / with only a forming habit.

- [ ] **Step 3: Dark theme pass** — run each screen under `HabitTheme(darkTheme = true)` via `@Preview(uiMode = UI_MODE_NIGHT_YES)`. Fix any hardcoded colors (grid `future` cell: use `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)` instead of a fixed `0x1F000000`).

- [ ] **Step 4: androidTest stub (written, not run)**

```kotlin
package com.manasm.habit100

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test

class TrackerScreenTest {
    @get:Rule val rule = createComposeRule()

    @Test fun mark_today_button_disables_after_marking() {
        // set content to TrackerScreen with a fake VM in Forming(canMarkToday=true);
        // click "Mark today done"; assert node with text "Done for today ✓" exists.
    }
}
```

Fill in with a hand-rolled fake `TrackerViewModel` subclass or an interface seam. This file is documentation of intended coverage; it is not part of `testDebugUnitTest`.

- [ ] **Step 5: Lint**

Run: `./gradlew :app:lintDebug`
Expected: no errors. Fix or baseline warnings deliberately (`lint { baseline = file("lint-baseline.xml") }` only if needed, with a note).

- [ ] **Step 6: Full verification**

Run: `source scripts/env.sh && ./gradlew clean :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`
Expected: `BUILD SUCCESSFUL`; APK present; all tests green.

- [ ] **Step 7: Update `README.md`**

```markdown
# 100 Day Habit Tracker

Native Android (Kotlin + Compose + Room). One habit at a time, 100 days, 10-miss budget,
never two misses in a row. Mastered habits move to a shelf with a monthly check-in;
a slip offers a 30-day tune-up.

## Build
1. `./scripts/bootstrap-toolchain.sh` (one-time; installs JDK 17 + Android SDK + Gradle, no root)
2. `source scripts/env.sh`
3. `./gradlew :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
4. `./gradlew :app:testDebugUnitTest` runs the rule-engine + Robolectric suites

Debug builds show a "dev: +1 day" control on the tracker for exercising rollover/graduation.
```

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "polish: dark theme, empty states, launcher icon, lint clean, README"
```

**CHECKPOINT 3** — full app: form → graduate → shelf → monthly check-in → slip → tune-up, all exercisable via the dev clock. Final review.

---

## Self-Review

**Spec coverage check (spec §-by-§):**

- §2.1 window/days → Task 3 (`currentDayNumber`, `dateForDay`), Task 4/5 tests. ✓
- §2.2 marking, current-day-only → Task 4 (`canMarkToday`), Task 7 (`markTodayDone` checks `canMarkToday`). ✓
- §2.3 miss budget / 11th fails → Task 4. ✓
- §2.4 never-twice, evaluated first → Task 4 (`consecutive >= 2` checked before budget). ✓
- §2.5 graduation → Task 4/5, Task 7 transition, Task 15 screen. ✓
- §2.6 failure + restart/abandon → Task 4, Task 7 (`restartFailedHabit`, `abandonHabit`). ✓
- §2.7 at-risk amber → Task 4 (`atRisk`), Task 12 (banner). ✓
- §2.8 one active slot → Task 6 (triggers), Task 7 (`check(activeCount()==0)`). ✓
- §2.9 rollover → Task 13 (engine), Task 14 (wiring). ✓
- §2.10 maintenance pulse + slip + tune-up → Task 16, 17, 18. ✓
- §3 architecture / packages → Tasks 6–17 follow the package layout. ✓
- §4 rule engine API → Task 3/4; `RuleSnapshot` fields match across all consumers. ✓
- §5 Room schema → Task 6 matches the spec's column list (habits/day_logs/maintenance_checkins). ✓
- §6 rollover component (port + engine + worker) → Task 13/14. ✓
- §7 clock/dev override → Task 8, Task 12 (`devAdvanceDay`). ✓
- §8 screens + `HabitGrid` 3 sizes + colors → Task 10, 11, 12, 15, 17. ✓
- §9 testing (JVM + Robolectric; androidTest written not run) → every task has tests; Task 19 Step 4. ✓
- §10 toolchain bootstrap → Task 1. ✓
- §11 build order + checkpoints → Tasks map 1:1 to spec build order; checkpoints after Task 5, 12, 19. ✓
- §12 non-goals → no weekly cadence, no auth, no sync anywhere in the plan. ✓

**Type consistency:** `RuleSnapshot`, `RuleInput`, `DayLog`, `HabitEntity`, `ActiveHabit`, `CellState`, `gridCells(trackLength, currentDay, doneDays, missedDays)` used identically in Tasks 4, 7, 10, 12, 13, 15, 16, 17. `HabitRepository` method names (`createHabit`, `markTodayDone`, `applyTransition`, `snapshotOf`, `observeActive`, `trophyView`, `canStartNew`, `checkIn`, `reportSlip`, `startTuneUp`, `restartFailedHabit`) are consistent between definition and call sites.

**Known deliberate seams (not placeholders):**
- Task 6 creates `clock/Clock.kt` early (Step 0) because tests and repo need it before Task 8.
- Task 9's `HabitViewModelFactory` references VMs created in later tasks — stub-then-flesh, constructor params fixed up front.
- Task 15 extracts `StatCard` from `tracker` to `ui/components/` (mid-plan refactor, called out in-step).
- Task 17's tune-up button calls `repo.startTuneUp` which lands in Task 18; Task 17 tests don't exercise the happy path of tune-up (Task 18 does).

**Fixes applied inline during review:** `NewHabitViewModel` rewritten to plain backing `MutableStateFlow` (removed the `as MutableStateFlow` cast); `GridShare` color computation simplified to explicit `Color.rgb(...)`; grid `future` color moved to a theme token in Task 19 Step 3.


