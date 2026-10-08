#!/usr/bin/env python3
"""
GigRun v14 Migration Chain Benchmark (offline, no device)

Measures the full migration chain 4->5->6->7 on a pre-seeded v4 database.
"""
import json
import random
import sqlite3
import statistics
import time
from pathlib import Path

ROOT = Path(r"D:\Projects\GigRun\app-root")
SCHEMAS = ROOT / "app" / "schemas" / "com.gigrun.data.database.AppDatabase"
MIGRATIONS = ROOT / "app" / "src" / "main" / "java" / "com" / "gigrun" / "data" / "database" / "Migrations.kt"

def load_schema(version):
    with open(SCHEMAS / f"{version}.json", encoding="utf-8") as f:
        return json.load(f)["database"]

def build_schema(db_json, con):
    for e in db_json["entities"]:
        con.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for idx in e.get("indices", []):
            con.execute(idx["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    con.commit()

def statements_from_kotlin(path, src_v=None, dst_v=None):
    src = path.read_text(encoding="utf-8")
    body = src
    if src_v is not None:
        marker = f"object : Migration({src_v}, {dst_v})"
        i = src.find(marker)
        if i < 0:
            raise SystemExit(f"No Migration({src_v}, {dst_v}) block found in {path}")
        start = src.find("{", i)
        depth = 0
        j = start
        while j < len(src):
            if src[j] == "{":
                depth += 1
            elif src[j] == "}":
                depth -= 1
                if depth == 0:
                    break
            j += 1
        body = src[start:j]
    out = []
    for m in __import__("re").finditer(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)', body):
        parts = __import__("re").findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
        out.append("".join(parts).replace("\\`", "`"))
    return out

def seed_v4(con, num_shifts):
    """Seed a v4 database (no penalties, insurance, documents, shift_logs tables)."""
    base_ts = 1_700_000_000_000
    day_ms = 86_400_000
    span_ms = 90 * day_ms

    shifts = []
    for i in range(num_shifts):
        start = base_ts + random.randint(0, span_ms)
        dur = random.randint(1_800_000, 14_400_000)
        end = start + dur
        shifts.append((start, end))

    shift_ids = []
    for start, end in shifts:
        cur = con.execute("INSERT INTO shifts (startTime, endTime) VALUES (?, ?)", (start, end))
        shift_ids.append(cur.lastrowid)

    trips = []
    for sid, (s_start, s_end) in zip(shift_ids, shifts):
        n_trips = random.randint(6, 14)
        for _ in range(n_trips):
            t_start = s_start + random.randint(0, max(1, s_end - s_start - 60_000))
            t_end = t_start + random.randint(60_000, 1_800_000)
            platform = random.choice(["Blinkit", "Zepto", "Rapido", "Uber", "Other"])
            dist = round(random.uniform(0.5, 15.0), 1)
            start_lat = round(random.uniform(12.9, 13.1), 6)
            start_lon = round(random.uniform(77.5, 77.7), 6)
            wait_sec = random.randint(0, 600)
            is_surge = random.randint(0, 1)
            trips.append((sid, platform, t_start, t_end, start_lat, start_lon, dist, wait_sec, is_surge))
    con.executemany(
        "INSERT INTO trips (shiftId, platform, startTime, endTime, startLat, startLon, distanceKm, waitTimeSec, isSurgeTrip) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
        trips
    )

    earnings = []
    cur = con.execute("SELECT id, platform, startTime, endTime FROM trips")
    for tid, platform, t_start, t_end in cur.fetchall():
        amt = round(random.uniform(20.0, 250.0), 2)
        earnings.append((tid, amt, "notification", t_start, platform))
    con.executemany(
        "INSERT INTO earnings (tripId, amountInr, source, timestamp, platform) VALUES (?, ?, ?, ?, ?)",
        earnings
    )

    fuel_logs = []
    for i in range(max(1, num_shifts // 10)):
        ts = 1_700_000_000_000 + random.randint(0, 90 * 86_400_000)
        fuel_logs.append((1, round(random.uniform(200.0, 600.0), 2), round(random.uniform(1.0, 8.0), 2), 1000.0 + i * 50, ts, 1))
    con.executemany(
        "INSERT INTO fuel_logs (vehicleId, amountInr, liters, odometer, timestamp, isClosed) VALUES (?, ?, ?, ?, ?, ?)",
        fuel_logs
    )

    expenses = []
    for s_start, s_end in zip([s for s,_ in shifts], [e for _,e in shifts]):
        n_exp = random.randint(3, 7)
        for _ in range(n_exp):
            cat = random.choice(["FUEL", "PARKING", "REPAIRS", "PHONE", "FOOD", "TOLL", "INSURANCE", "OTHER"])
            amt = round(random.uniform(10.0, 300.0), 2)
            expenses.append((None, cat, amt, None, None, s_start + random.randint(0, max(1, s_end - s_start)), 1))
    con.executemany(
        "INSERT INTO expenses (shiftId, category, amount, note, receiptUri, timestamp, isDeductible) VALUES (?, ?, ?, ?, ?, ?, ?)",
        expenses
    )

    con.commit()

def time_migration_chain(con, tiers, iterations=5):
    """Time the full migration chain 4->5->6->7."""
    results = {}
    for tier in tiers:
        print(f"\n=== Migration chain benchmark: {tier} shifts ===")
        chain_times = []
        for _ in range(iterations):
            con = sqlite3.connect(":memory:")
            schema_v4 = load_schema(4)
            build_schema(schema_v4, con)
            seed_v4(con, tier)
            con.commit()

            t0 = time.perf_counter_ns()

            # Apply migrations in order
            for (src_v, dst_v) in [(4, 5), (5, 6), (6, 7)]:
                stmts = statements_from_kotlin(MIGRATIONS, src_v, dst_v)
                for s in stmts:
                    con.execute(s)
            con.commit()

            chain_times.append((time.perf_counter_ns() - t0) / 1_000_000)
            con.close()

        results[tier] = {
            "p50": round(statistics.median(chain_times), 2),
            "p95": round(sorted(chain_times)[int(0.95 * len(chain_times)) - 1], 2),
            "p99": round(sorted(chain_times)[int(0.99 * len(chain_times)) - 1], 2),
        }
        print(f"  Tier {tier}: p50={results[tier]['p50']:.2f}ms, p95={results[tier]['p95']:.2f}ms, p99={results[tier]['p99']:.2f}ms")
    return results

if __name__ == "__main__":
    tiers = [100, 500, 1000]
    results = time_migration_chain(tiers, tiers)

    print("\n# Migration Chain 4->5->6->7 Performance (ms, P50 / P95 / P99)")
    print()
    print("| Tier | P50 | P95 | P99 |")
    print("|------|-----|-----|-----|")
    for t in tiers:
        r = results[t]
        print(f"| {t} | {r['p50']:.2f} | {r['p95']:.2f} | {r['p99']:.2f} |")