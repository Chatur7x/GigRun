# GigRun v14 Performance Report

**Date:** 2026-10-08  
**Device:** N/A (offline database benchmark only — no physical device available)  
**Build:** v14.0 (versionCode 14, Room v7)  
**Schema exports:** 3.json, 4.json, 5.json, 6.json, 7.json  

---

## Executive Summary

This report contains **real measured database-layer performance** from the actual Room v7 schema running on SQLite (Python stdlib). All numbers are from actual query execution against seeded datasets at three tiers (100/500/1000 shifts ≈ 100/500/1000 users' local data).

**Device-layer metrics (Macrobenchmark, Flashlight, Perfetto, 30-min foreground-service stress test) are NOT RUN** — no physical device or emulator was available in this environment. The harnesses for those are written in the codebase but require hardware.

---

## 1. Startup Performance (Macrobenchmark)

| Data tier | timeToInitialDisplayMs (P50) | timeToFullDisplayMs (P50) |
|-----------|------------------------------|----------------------------|
| 100 users | NOT RUN (device required)    | NOT RUN (device required)  |
| 500 users | NOT RUN (device required)    | NOT RUN (device required)  |
| 1000 users| NOT RUN (device required)    | NOT RUN (device required)  |

**Harness:** `app/src/androidTest/java/com/gigrun/benchmark/DashboardStartupBenchmark.kt` — written, ready to run with `./gradlew :app:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.gigrun.benchmark.DashboardStartupBenchmark`

---

## 2. Frame Timing (P50 / P90 / P99 in ms)

| Screen | 100 | 500 | 1000 |
|--------|-----|-----|------|
| Dashboard | NOT RUN | NOT RUN | NOT RUN |
| Comparison | NOT RUN | NOT RUN | NOT RUN |
| Penalties | NOT RUN | NOT RUN | NOT RUN |

**Harness:** `DashboardScrollBenchmark.kt`, `ComparisonScreenBenchmark.kt`, `PenaltyListScrollBenchmark.kt` — written, ready to run.

---

## 3. Database Query Performance (ms, P50 / P95 / P99)

*Measured on actual Room v7 schema (SQLite 3.50.4) with 20 iterations per query. All times in milliseconds.*

### getComparison (Platform Comparison screen query)

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.51 | 0.63 | 0.63 |
| 500 | 2.35 | 3.39 | 3.39 |
| 1000 | 5.38 | 7.84 | 7.84 |

### getMonthlyTotal_penalties

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.01 | 0.01 | 0.01 |
| 500 | 0.04 | 0.06 | 0.06 |
| 1000 | 0.10 | 0.16 | 0.16 |

### getTotalFuel

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.00 | 0.01 | 0.01 |
| 500 | 0.01 | 0.02 | 0.02 |
| 1000 | 0.01 | 0.02 | 0.02 |

### getTotalNonFuelExpenses

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.12 | 0.12 | 0.12 |
| 500 | 0.57 | 0.65 | 0.65 |
| 1000 | 1.21 | 1.48 | 1.48 |

### getActivityByPlatform

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.56 | 0.92 | 0.92 |
| 500 | 2.51 | 3.46 | 3.46 |
| 1000 | 8.19 | 11.89 | 11.89 |

### getAllPenalties

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.07 | 0.08 | 0.08 |
| 500 | 0.28 | 0.31 | 0.31 |
| 1000 | 0.80 | 1.25 | 1.25 |

### getMonthlyTotal_penalties_dao

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.01 | 0.01 | 0.01 |
| 500 | 0.04 | 0.04 | 0.04 |
| 1000 | 0.20 | 0.23 | 0.23 |

### getRevenueByPlatform

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.38 | 0.40 | 0.40 |
| 500 | 1.97 | 2.11 | 2.11 |
| 1000 | 5.24 | 10.56 | 10.56 |

### getActivityByPlatform_dao

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.50 | 0.84 | 0.84 |
| 500 | 3.13 | 3.78 | 3.78 |
| 1000 | 6.12 | 7.06 | 7.06 |

### getTotalFuel_dao

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.00 | 0.01 | 0.01 |
| 500 | 0.01 | 0.01 | 0.01 |
| 1000 | 0.01 | 0.02 | 0.02 |

### getTotalNonFuelExpenses_dao

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 0.07 | 0.08 | 0.08 |
| 500 | 0.68 | 0.75 | 0.75 |
| 1000 | 1.28 | 1.33 | 1.33 |

---

## 4. Migration Chain Performance (ms, P50 / P95 / P99)

*Full migration chain 4→5→6→7 on a pre-seeded v4 database. 5 iterations per tier.*

| Tier | P50 | P95 | P99 |
|------|-----|-----|-----|
| 100 | 2.77 | 2.82 | 2.82 |
| 500 | 2.12 | 2.41 | 2.41 |
| 1000 | 2.29 | 2.44 | 2.44 |

**Note:** Migration time is ~2-3ms regardless of dataset size because the migrations are schema-only (CREATE TABLE / ALTER TABLE / CREATE INDEX) and do not touch user data rows. All three migrations passed `tools/validate_migration.py` validation against the exported JSON schemas.

---

## 5. Flashlight Scores

| Screen | Score / 100 |
|--------|-------------|
| Dashboard | NOT RUN (device required) |
| Comparison | NOT RUN (device required) |
| Penalties | NOT RUN (device required) |
| Vehicle | NOT RUN (device required) |
| Repairs | NOT RUN (device required) |
| Tools | NOT RUN (device required) |

**Harness:** `npm install -g @perf/flashlight` then `flashlight test --bundleId com.gigrun`

---

## 6. Foreground Service (30-min stress test)

| Metric | Value |
|--------|-------|
| Peak memory | NOT RUN (device required) |
| Memory growth | NOT RUN (device required) |
| Battery drain | NOT RUN (device required) |
| Crashes | NOT RUN (device required) |
| ANRs | NOT RUN (device required) |

**Harness:** Debug-only "Stress Test" button in Settings — written, starts location foreground service, simulates 30 min GPS at 1 Hz, writes dummy earning every 30s, runs comparison query every 60s, logs memory/battery every 5 min.

---

## 7. Crash-Free Rate

| Metric | Value |
|--------|-------|
| Crash-free sessions (24h) | NOT RUN (Crashlytics not yet configured) |
| Crash-free sessions (7d) | NOT RUN |

**Status:** Firebase Crashlytics + Analytics added to `libs.versions.toml` and `build.gradle.kts`, `google-services.json` needed from user, initialization in `GigRunApplication`.

---

## 8. Efficiency Percentage

| Tier | Efficiency % |
|------|--------------|
| 100 users | NOT COMPUTED (device metrics missing) |
| 500 users | NOT COMPUTED (device metrics missing) |
| 1000 users | NOT COMPUTED (device metrics missing) |

**Formula (from spec):**
```
efficiency = (
    (startup_score / 500) +           // 500ms target
    (frame_score) +                    // % of frames < 16ms
    (db_score / 200) +                 // 200ms target
    (flashlight_avg / 100) +
    (service_stability_score)          // 100 if no crash/ANR
) / 5 * 100
```

**Database-layer `db_score` (P95 of getComparison):**
- 100 users: 0.63ms → 99.7%
- 500 users: 3.39ms → 98.3%
- 1000 users: 7.84ms → 96.1%

All well under the 200ms target.

---

## 9. Verdict

| Target | Status | Notes |
|--------|--------|-------|
| Startup < 500ms | NOT RUN | Device required |
| P90 frame < 16ms | NOT RUN | Device required |
| getComparison < 200ms | ✅ PASS | P99 = 7.84ms at 1000-user tier |
| Flashlight > 70 | NOT RUN | Device required |
| No crashes, no ANRs | NOT RUN | Device required |
| Migration chain < 3s | ✅ PASS | P99 = 2.82ms at 100 tier |

### Known Limitations

1. **Device metrics unavailable** — No physical device/emulator in this environment. All Compose UI, startup, frame-timing, battery, memory, and Crashlytics metrics are unmeasured.
2. **Compose animation flake** — `PlatformComparisonScreenTest.awaitTextIn` waits on `AnimatedRupees` counter; may timeout on slow hardware. Test uses 5s timeout.
3. **Dashboard reactivity** — `DashboardViewModel` reads comparison data one-shot in `loadTodayStatsInner()`. Mid-shift trip additions don't update "Best this week" until next load (same as Feature 27 penalty card).
4. **Screen duplication** — `Screen.Comparison` (new net ₹/hr screen) and `Screen.Platforms` (legacy gross-trips tab) both exist. Consolidation deferred pending on-device validation.

### Recommended Fixes

1. Run on device: connect phone via USB, enable USB debugging, run `./gradlew :app:connectedDebugAndroidTest`.
2. Run Macrobenchmarks: `./gradlew :app:connectedCheck -Pandroid.testInstrumentationRunnerArguments.class=com.gigrun.benchmark.DashboardStartupBenchmark` (and siblings).
3. Run Flashlight: `npm install -g @perf/flashlight && flashlight test --bundleId com.gigrun`.
4. Capture Perfetto trace: 60s simulated shift with foreground service + 1000-tier data.
5. Enable R8: `isMinifyEnabled = true`, add keep rules for Hilt/Room/Compose/GigRun entities.
6. Generate release keystore and `keystore.properties`, build signed bundle: `./gradlew clean bundleRelease`.
7. Verify with `jarsigner -verify`.
8. Add `google-services.json`, initialize Crashlytics, add custom logs at NotificationParser, LedgerManager, CrashDetectionService, LocationTrackingService.

---

## 10. Methodology & Reproducibility

- **Database benchmark:** `tools/db_perf_benchmark.py` — loads `schemas/.../7.json`, builds schema in SQLite 3.50.4, seeds 100/500/1000 shifts with realistic distributions (6-14 trips/shift, 3-7 expenses/shift, etc.), runs each DAO query 20×, reports P50/P95/P99.
- **Migration benchmark:** `tools/migration_benchmark.py` — builds v4 schema, seeds same tiers, applies `MIGRATION_4_5`, `MIGRATION_5_6`, `MIGRATION_6_7` sequentially, 5 iterations.
- **Schema validation:** `tools/validate_migration.py` — validates each migration against exported JSON schemas (4.json, 5.json, 6.json, 7.json) using Room's `TableInfo` comparison logic. All migrations pass.
- **No estimates** — Every number in this report comes from an actual run of the Python scripts above. No extrapolation, no modelling, no guesswork.

---

## Files Changed / Added for Performance Testing

| File | Purpose |
|------|---------|
| `tools/db_perf_benchmark.py` | Database query benchmark (real measured numbers) |
| `tools/migration_benchmark.py` | Migration chain benchmark (real measured numbers) |
| `tools/validate_migration.py` | Migration schema validation (offline, no device) |
| `tools/generate_test_data.kt` | NOT YET WRITTEN — would be debug-only data generator |
| `app/src/androidTest/java/com/gigrun/benchmark/DashboardStartupBenchmark.kt` | Macrobenchmark startup |
| `app/src/androidTest/java/com/gigrun/benchmark/DashboardScrollBenchmark.kt` | Macrobenchmark frame timing |
| `app/src/androidTest/java/com/gigrun/benchmark/ComparisonScreenBenchmark.kt` | Macrobenchmark frame timing |
| `app/src/androidTest/java/com/gigrun/benchmark/PenaltyListScrollBenchmark.kt` | Macrobenchmark frame timing |
| `app/src/androidTest/java/com/gigrun/benchmark/DatabaseQueryBenchmark.kt` | Android-side query timing (optional) |

---

*Report generated 2026-10-08 from real database-layer measurements. Device-layer sections pending hardware.*