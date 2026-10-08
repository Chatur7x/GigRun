#!/usr/bin/env python3
"""
GigRun v14 Database Query Performance Benchmark (offline, no device)

Creates the exact v7 schema from 7.json, seeds 100/500/1000 user tiers,
and times the actual DAO aggregate queries against real rows.

Output: markdown table for docs/perf/v14-performance-report.md
"""
import json
import random
import sqlite3
import statistics
import sys
import time
from pathlib import Path

ROOT = Path(r"D:\Projects\GigRun\app-root")
SCHEMAS = ROOT / "app" / "schemas" / "com.gigrun.data.database.AppDatabase"
SCHEMA_FILE = SCHEMAS / "7.json"  # v7 = current (Phase C done)

PLATFORMS = ["Blinkit", "Zepto", "Rapido", "Uber", "Other"]
EXPENSE_CATEGORIES = ["FUEL", "PARKING", "REPAIRS", "PHONE", "FOOD", "TOLL", "INSURANCE", "OTHER"]

def load_schema():
    with open(SCHEMA_FILE, encoding="utf-8") as f:
        return json.load(f)["database"]

def build_schema(db_json, con):
    for e in db_json["entities"]:
        con.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for idx in e.get("indices", []):
            con.execute(idx["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    con.commit()

def seed_tier(con, num_shifts):
    """Seed one user's worth of data scaled to num_shifts.
    ~10 trips/shift, ~1 earning/trip, ~0.1 fuel logs/shift, ~5 expenses/shift, ~0.5 penalties/shift
    """
    base_ts = 1_700_000_000_000  # ~Nov 2023
    day_ms = 86_400_000
    span_ms = 90 * day_ms

    # shifts - get the generated IDs
    shifts = []
    for i in range(num_shifts):
        start = base_ts + random.randint(0, span_ms)
        dur = random.randint(1_800_000, 14_400_000)  # 0.5h-4h
        end = start + dur
        shifts.append((start, end))

    shift_ids = []
    for start, end in shifts:
        cur = con.execute("INSERT INTO shifts (startTime, endTime) VALUES (?, ?)", (start, end))
        shift_ids.append(cur.lastrowid)

    # trips
    trips = []
    for sid, (s_start, s_end) in zip(shift_ids, shifts):
        n_trips = random.randint(6, 14)
        for _ in range(n_trips):
            t_start = s_start + random.randint(0, max(1, s_end - s_start - 60_000))
            t_end = t_start + random.randint(60_000, 1_800_000)
            platform = random.choice(PLATFORMS)
            dist = round(random.uniform(0.5, 15.0), 1)
            start_lat = round(random.uniform(12.9, 13.1), 6)  # Bangalore-ish
            start_lon = round(random.uniform(77.5, 77.7), 6)
            wait_sec = random.randint(0, 600)
            is_surge = random.randint(0, 1)
            trips.append((sid, platform, t_start, t_end, start_lat, start_lon, dist, wait_sec, is_surge))
    con.executemany(
        "INSERT INTO trips (shiftId, platform, startTime, endTime, startLat, startLon, distanceKm, waitTimeSec, isSurgeTrip) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        trips
    )

    # earnings (one per trip, roughly)
    earnings = []
    cur = con.execute("SELECT id, platform, startTime, endTime FROM trips")
    for tid, platform, t_start, t_end in cur.fetchall():
        amt = round(random.uniform(20.0, 250.0), 2)
        earnings.append((tid, amt, "notification", t_start, platform))
    con.executemany(
        "INSERT INTO earnings (tripId, amountInr, source, timestamp, platform) VALUES (?, ?, ?, ?, ?)",
        earnings
    )

    # fuel logs (every ~10 shifts)
    fuel_logs = []
    for i in range(max(1, num_shifts // 10)):
        ts = base_ts + random.randint(0, span_ms)
        fuel_logs.append((1, round(random.uniform(200.0, 600.0), 2), round(random.uniform(1.0, 8.0), 2), 1000.0 + i * 50, ts, 1))
    con.executemany(
        "INSERT INTO fuel_logs (vehicleId, amountInr, liters, odometer, timestamp, isClosed) VALUES (?, ?, ?, ?, ?, ?)",
        fuel_logs
    )

    # expenses
    expenses = []
    for s_start, s_end in shifts:
        n_exp = random.randint(3, 7)
        for _ in range(n_exp):
            cat = random.choice(EXPENSE_CATEGORIES)
            amt = round(random.uniform(10.0, 300.0), 2)
            expenses.append((None, cat, amt, None, None, s_start + random.randint(0, max(1, s_end - s_start)), 1))
    con.executemany(
        "INSERT INTO expenses (shiftId, category, amount, note, receiptUri, timestamp, isDeductible) VALUES (?, ?, ?, ?, ?, ?, ?)",
        expenses
    )

    # penalties (sparse)
    penalties = []
    for _ in range(max(1, num_shifts // 2)):
        p = random.choice(PLATFORMS)
        amt = round(random.uniform(50.0, 500.0), 2)
        reason = random.choice(["Late delivery", "Customer complaint", "Policy violation", "Traffic rule"])
        ts = base_ts + random.randint(0, span_ms)
        penalties.append((p, amt, reason, ts, 0))
    con.executemany(
        "INSERT INTO penalties (platform, amountInr, reason, timestamp, isDisputed) VALUES (?, ?, ?, ?, ?)",
        penalties
    )

    # insurance_policies
    policies = []
    for _ in range(random.randint(1, 3)):
        start = base_ts + random.randint(0, span_ms)
        policies.append(("ProviderX", f"POL{random.randint(1000,9999)}", "comprehensive", random.uniform(2000,8000), start, start + 365*day_ms, 0, 30))
    con.executemany(
        "INSERT INTO insurance_policies (provider, policyNumber, type, premiumInr, startDate, endDate, isPlatformProvided, claimDeadlineDays) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        policies
    )

    # documents
    docs = []
    for t in ["rc", "insurance", "license", "aadhaar"]:
        docs.append((t, f"/encrypted/{t}.bin", base_ts + 730*day_ms, None))
    con.executemany(
        "INSERT INTO documents (type, filePath, expiryDate, notes) VALUES (?, ?, ?, ?)",
        docs
    )

    # shift_logs (derived from shifts)
    shift_logs = []
    for s_start, s_end in shifts:
        shift_logs.append((s_start, s_end, random.randint(0, 3), random.randint(0, 30), random.randint(60, 95)))
    con.executemany(
        "INSERT INTO shift_logs (startTime, endTime, breakCount, totalBreakMinutes, fatigueScore) VALUES (?, ?, ?, ?, ?)",
        shift_logs
    )

    con.commit()

def time_query(con, sql, params=(), iterations=20):
    """Run query 'iterations' times, return list of ms."""
    times = []
    for _ in range(iterations):
        t0 = time.perf_counter_ns()
        con.execute(sql, params).fetchall()
        times.append((time.perf_counter_ns() - t0) / 1_000_000)  # ms
    return times

def run_benchmarks(tiers):
    schema = load_schema()
    results = {}

    for tier in tiers:
        print(f"\n=== TIER: {tier} shifts ===")
        con = sqlite3.connect(":memory:")
        build_schema(schema, con)
        seed_tier(con, tier)

        # Real DAO queries (copied/adapted from the actual DAO files)
        queries = {
            "getComparison": (
                """SELECT platform,
                         SUM(amountInr) AS total
                  FROM earnings
                  WHERE timestamp >= ? AND timestamp < ?
                  GROUP BY platform""",
                (0, 2**63 - 1)
            ),
            "getMonthlyTotal_penalties": (
                """SELECT COALESCE(SUM(amountInr), 0) FROM penalties WHERE timestamp >= ? AND timestamp < ?""",
                (0, 2**63 - 1)
            ),
            "getTotalFuel": (
                """SELECT COALESCE(SUM(amountInr), 0) FROM fuel_logs WHERE timestamp >= ? AND timestamp < ?""",
                (0, 2**63 - 1)
            ),
            "getTotalNonFuelExpenses": (
                """SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE timestamp >= ? AND timestamp < ? AND category != 'FUEL' COLLATE NOCASE""",
                (0, 2**63 - 1)
            ),
            "getActivityByPlatform": (
                """SELECT platform,
                         SUM((endTime - startTime) / 3600000.0) AS hours,
                         SUM(distanceKm) AS km,
                         COUNT(*) AS tripCount
                  FROM trips
                  WHERE startTime >= ? AND startTime < ? AND endTime IS NOT NULL
                  GROUP BY platform""",
                (0, 2**63 - 1)
            ),
            "getAllPenalties": (
                "SELECT * FROM penalties ORDER BY timestamp DESC",
                ()
            ),
            "getMonthlyTotal_penalties_dao": (
                "SELECT COALESCE(SUM(amountInr), 0) FROM penalties WHERE timestamp >= ? AND timestamp < ?",
                (0, 2**63 - 1)
            ),
            "getRevenueByPlatform": (
                """SELECT platform, SUM(amountInr) AS total FROM earnings WHERE timestamp >= ? AND timestamp < ? GROUP BY platform""",
                (0, 2**63 - 1)
            ),
            "getActivityByPlatform_dao": (
                """SELECT platform, SUM((endTime - startTime) / 3600000.0) AS hours, SUM(distanceKm) AS km, COUNT(*) AS tripCount FROM trips WHERE startTime >= ? AND startTime < ? AND endTime IS NOT NULL GROUP BY platform""",
                (0, 2**63 - 1)
            ),
            "getTotalFuel_dao": (
                "SELECT COALESCE(SUM(amountInr), 0) FROM fuel_logs WHERE timestamp >= ? AND timestamp < ?",
                (0, 2**63 - 1)
            ),
            "getTotalNonFuelExpenses_dao": (
                "SELECT COALESCE(SUM(amount), 0) FROM expenses WHERE timestamp >= ? AND timestamp < ? AND category != 'FUEL' COLLATE NOCASE",
                (0, 2**63 - 1)
            ),
        }

        tier_results = {}
        for name, (sql, params) in queries.items():
            times = time_query(con, sql, params)
            tier_results[name] = {
                "p50": round(statistics.median(times), 2),
                "p95": round(sorted(times)[int(0.95 * len(times)) - 1], 2),
                "p99": round(sorted(times)[int(0.99 * len(times)) - 1], 2),
            }
            print(f"  {name}: p50={tier_results[name]['p50']:.2f}ms, p95={tier_results[name]['p95']:.2f}ms, p99={tier_results[name]['p99']:.2f}ms")

        results[tier] = tier_results
        con.close()

    return results

def print_markdown(results, tiers):
    print("\n# Database Query Performance (ms, P50 / P95 / P99)")
    print()
    query_names = list(results[tiers[0]].keys())
    for q in query_names:
        print(f"## {q}")
        print()
        print("| Tier | P50 | P95 | P99 |")
        print("|------|-----|-----|-----|")
        for t in tiers:
            r = results[t][q]
            print(f"| {t} | {r['p50']:.2f} | {r['p95']:.2f} | {r['p99']:.2f} |")
        print()

if __name__ == "__main__":
    tiers = [100, 500, 1000]
    results = run_benchmarks(tiers)
    print_markdown(results, tiers)