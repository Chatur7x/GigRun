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

## Feature 27 — Instrumented tests have never been executed

**Added:** 2026-10-08
**Severity:** Medium (risk, not user-facing)
**Impact:** `Migration4To5Test`, `PenaltyDaoTest`, `PenaltyTrackerScreenTest` and
`PlatformComparisonScreenTest` compile but have never run — no emulator or device
was available in the development environment. `Migration4To5Test` is the only
check that `MIGRATION_4_5` produces a schema matching what Room expects; a
mismatch means crash-on-upgrade for existing installs.
**Fix:** Run `.\gradlew :app:connectedDebugAndroidTest` against a physical device.
Must be green before any further migration work (Feature 24 is v5 → v6).