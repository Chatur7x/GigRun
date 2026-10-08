# Known Issues

Deferred, accepted trade-offs. Each entry records *why* the behaviour is the way
it is, so a future change doesn't "fix" it by accident or re-introduce it.

Closed issues stay in this file with a **Resolved:** line and the commit that
closed them — the reasoning is worth keeping even after the fix.

---

## Feature 28 — Dashboard comparison card is not reactive

**Added:** 2026-10-08
**Severity:** Low
**Impact:** "Best this week" card on the dashboard only refreshes when the
dashboard reloads. A trip added mid-shift won't update it until next load.
**Where:** `presentation/dashboard/DashboardViewModel.kt` →
`loadTodayStatsInner()`
**Same as:** Feature 27 penalty card (`penaltiesThisMonth` / `disputedThisMonth`
read the same way, same reload boundary).
**Fix:** Would require restructuring `DashboardViewModel` to be reactive
(`combine()` over DAO `Flow`s instead of one-shot `suspend` reads). Deferred.

**Note:** this is a deliberate choice, not an oversight. `DashboardViewModel`
uses a `MutableStateFlow` + one-shot `loadTodayStats()` pattern, not `combine()`.
Making only the comparison card reactive would introduce a second, competing
state mechanism into one ViewModel. Fix both cards together, or neither.

---

## Feature 28 — `Screen.Comparison` and `Screen.Platforms` both exist

**Added:** 2026-10-08
**Severity:** Low
**Impact:** New comparison screen (`Screen.Comparison` → `PlatformComparisonScreen`,
route `comparison`) is reachable only from the dashboard "Best this week" card.
The bottom-nav "Compare" tab (`Screen.Platforms` → legacy `PlatformCompareScreen`)
is unchanged and still shows gross per-platform trip/earnings counts.

The two overlap in purpose but not in content: the legacy tab shows gross trip
counts and earnings; the new screen shows net ₹/hour after allocated fuel and
expenses, with a time-range selector.

**Where:** `MainActivity.kt` → `sealed class Screen`
**Fix:** Consolidate once the new screen is validated on a real device. Likely
delete `PlatformCompareScreen` and re-point the bottom-nav tab at
`PlatformComparisonScreen`. Deferred pending on-device validation.

---

## Feature 27/28 — Instrumented tests have never been executed

**Added:** 2026-10-08
**Severity:** Medium (risk, not user-facing)
**Impact:** `PenaltyDaoTest`, `PenaltyTrackerScreenTest` and
`PlatformComparisonScreenTest` compile but have never run — no emulator or device
is available in the development environment.
**Fix:** Run `.\gradlew :app:connectedDebugAndroidTest` against a physical device.

### Schema validation is covered offline (this part is NOT open)

`MIGRATION_4_5` was validated without a device using `tools/validate_migration.py`,
which rebuilds the v4 schema from `schemas/…/4.json`, applies the `execSQL`
statements extracted from `Migrations.kt`, and diffs the live result against
`schemas/…/5.json` using Room's own `TableInfo` comparison rules (affinity,
`notNull`, default value, PK position, index name/uniqueness/column order).

Result: **PASSED** — all 11 tables match, `penalties` introduced correctly, all 3
expected indexes present. The `DEFAULT 0` regression class was specifically
checked and is clean (0 fields declare a `defaultValue`).

Run `python tools/validate_migration.py 4 5` after any migration edit. Prefer it
over a device round-trip for schema questions — it is instant and deterministic.

**Remaining device-only risk:** the Compose UI tests and `PenaltyDaoTest` still
need real execution. In particular `platformRowRendersCorrectNetPerHour` and
`best_platform_rate` wait on the `AnimatedRupees` counter and are the expected
flake candidates.

---

## Feature 27 — `Migration4To5Test` seed insert violated a foreign key

**Added:** 2026-10-08
**Resolved:** 2026-10-08 — commit `49e200f`
**Severity:** Low (test-only, would have looked like a migration bug)
**Impact:** The test seeded `expenses` with `shiftId = 1` and no matching `shifts`
row. `MigrationTestHelper` enables `PRAGMA foreign_keys = ON`, so that insert
throws `FOREIGN KEY constraint failed` during setup — producing a red test that
reads as a migration failure when `MIGRATION_4_5` was in fact correct.
**Fix:** Seed with `shiftId = NULL`, matching the entity (`shiftId: Long? = null`).
The migration and the entity were both left untouched.
**Why it matters:** a migration test that fails in its own setup trains you to
suspect the migration. That instinct is wrong often enough to be dangerous.