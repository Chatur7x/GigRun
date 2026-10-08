"""
Offline stand-in for MigrationTestHelper.runMigrationsAndValidate(4 -> 5).

Builds a v4 database from app/schemas/.../4.json, applies every execSQL
statement extracted from Migrations.kt, then compares the resulting live schema
against app/schemas/.../5.json using the same comparison Room's TableInfo
performs: column affinity, notNull, primary-key position, default value, and
index name/uniqueness/column order.

Why this exists: Room exports the expected schema as JSON at build time, so the
migration can be validated on any machine with Python 3 -- no emulator, no
device, no connectedDebugAndroidTest round-trip.

Usage:  python tools/validate_migration.py <fromVersion> <toVersion>
Exit 0 = schema matches. Exit 1 = mismatch, problems printed.
"""
import json
import re
import sqlite3
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent / "app-root"
SCHEMAS = ROOT / "app" / "schemas" / "com.gigrun.data.database.AppDatabase"
MIGRATIONS = ROOT / "app" / "src" / "main" / "java" / "com" / "gigrun" / "data" / "database" / "Migrations.kt"


def load(version):
    with open(SCHEMAS / f"{version}.json", encoding="utf-8") as fh:
        return json.load(fh)["database"]


def statements_from_kotlin(path, src_v=None, dst_v=None):
    """Pull quoted SQL strings out of db.execSQL(...) calls.

    When (src_v, dst_v) is supplied, only statements inside the matching
    `object : Migration(src_v, dst_v) { ... }` block are returned, so a file
    holding several migrations validates just the one being tested.
    """
    src = path.read_text(encoding="utf-8")
    body = src
    if src_v is not None:
        marker = f"object : Migration({src_v}, {dst_v})"
        i = src.find(marker)
        if i < 0:
            raise SystemExit(f"No Migration({src_v}, {dst_v}) block found in {path}")
        # walk braces from the block's opening brace to find its extent
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
    for m in re.finditer(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)', body):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
        out.append("".join(parts).replace("\\`", "`"))
    return out


def affinity(decl_type):
    """Room's type-affinity rules (androidx.room.util.TableInfo)."""
    t = (decl_type or "").upper()
    if "INT" in t:
        return "INTEGER"
    if "CHAR" in t or "CLOB" in t or "TEXT" in t:
        return "TEXT"
    if "BLOB" in t:
        return "BLOB"
    if "REAL" in t or "FLOA" in t or "DOUB" in t:
        return "REAL"
    return "NUMERIC"


def build(db_json, con):
    for e in db_json["entities"]:
        con.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for idx in e.get("indices", []):
            con.execute(idx["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    con.commit()


def read_live(con):
    """Read the actual schema back out of SQLite, the way Room does at open."""
    tables = {}
    q = ("SELECT name FROM sqlite_master WHERE type='table' "
         "AND name NOT LIKE 'sqlite_%' ORDER BY name")
    for (t,) in con.execute(q):
        cols, pk_pos = {}, 0
        for cid, name, decl, notnull, dflt, pk in con.execute(f'PRAGMA table_info("{t}")'):
            if pk:
                pk_pos = pk
            cols[name] = {
                "affinity": affinity(decl),
                "notNull": bool(notnull),
                "defaultValue": None if dflt is None else str(dflt),
                "pkPosition": pk,
            }
        indices = {}
        for row in con.execute(f'PRAGMA index_list("{t}")'):
            iname, iunique = row[1], bool(row[2])
            icols = [c[2] for c in con.execute(f'PRAGMA index_info("{iname}")')]
            indices[iname] = {"unique": iunique, "columns": icols}
        fks = [{"table": r[2], "from": r[3], "to": r[4],
                "onDelete": r[6], "onUpdate": r[5]}
               for r in con.execute(f'PRAGMA foreign_key_list("{t}")')]
        tables[t] = {"columns": cols, "indices": indices, "foreignKeys": fks, "pkOrder": pk_pos}
    return tables


def read_expected(db_json):
    tables = {}
    for e in db_json["entities"]:
        cols = {}
        # Room records primary keys at the ENTITY level (primaryKey.columnNames),
        # not as a per-field attribute. Positions are 1-based in column order.
        pk_columns = e.get("primaryKey", {}).get("columnNames", [])
        pk_pos = {name: i + 1 for i, name in enumerate(pk_columns)}
        for f in e["fields"]:
            cols[f["columnName"]] = {
                "affinity": f["affinity"],
                "notNull": bool(f.get("notNull", False)),
                "defaultValue": f.get("defaultValue"),
                "pkPosition": pk_pos.get(f["columnName"], 0),
            }
        indices = {i["name"]: {"unique": i["unique"], "columns": i["columnNames"]}
                   for i in e.get("indices", [])}
        fks = [{"table": f["table"], "from": c, "to": f["referencedColumns"][n],
                "onDelete": f["onDelete"], "onUpdate": f["onUpdate"]}
               for f in e.get("foreignKeys", [])
               for n, c in enumerate(f["columns"])]
        tables[e["tableName"]] = {"columns": cols, "indices": indices,
                                  "foreignKeys": fks, "pkOrder": pk_pos}
    return tables


def diff(expected, live):
    problems = []
    for t in sorted(expected):
        if t not in live:
            problems.append(f"  MISSING TABLE: {t}")
            continue
        e, l = expected[t], live[t]
        if set(e["columns"]) != set(l["columns"]):
            problems.append(f"  {t}: column set differs")
            problems.append(f"      expected: {sorted(e['columns'])}")
            problems.append(f"      found   : {sorted(l['columns'])}")
            continue
        for cn, ce in e["columns"].items():
            cl = l["columns"][cn]
            if ce["affinity"] != cl["affinity"]:
                problems.append(f"  {t}.{cn}: affinity expected {ce['affinity']} got {cl['affinity']}")
            if ce["notNull"] != cl["notNull"]:
                problems.append(f"  {t}.{cn}: notNull expected {ce['notNull']} got {cl['notNull']}")
            if (ce["defaultValue"] or None) != (cl["defaultValue"] or None):
                problems.append(f"  {t}.{cn}: defaultValue expected {ce['defaultValue']!r} got {cl['defaultValue']!r}")
            if ce["pkPosition"] != cl["pkPosition"]:
                problems.append(f"  {t}.{cn}: pk position expected {ce['pkPosition']} got {cl['pkPosition']}")
        ei, li = dict(e["indices"]), {k: v for k, v in l["indices"].items()
                                     if not k.startswith("sqlite_autoindex")}
        if set(ei) != set(li):
            problems.append(f"  {t}: index names differ")
            problems.append(f"      expected: {sorted(ei)}")
            problems.append(f"      found   : {sorted(li)}")
        for n in sorted(set(ei) & set(li)):
            if ei[n]["columns"] != li[n]["columns"]:
                problems.append(f"  {t}: index {n} columns expected {ei[n]['columns']} got {li[n]['columns']}")
            if ei[n]["unique"] != li[n]["unique"]:
                problems.append(f"  {t}: index {n} unique expected {ei[n]['unique']} got {li[n]['unique']}")
    return problems


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    src_v, dst_v = int(sys.argv[1]), int(sys.argv[2])

    v_src, v_dst = load(src_v), load(dst_v)
    print("=" * 74)
    print(f"MIGRATION {src_v} -> {dst_v} VALIDATION (offline, no device)")
    print("=" * 74)
    print(f"v{src_v} version={v_src['version']} entities={len(v_src['entities'])}")
    print(f"v{dst_v} version={v_dst['version']} entities={len(v_dst['entities'])}")

    stmts = statements_from_kotlin(MIGRATIONS, src_v, dst_v)
    print(f"\nStatements extracted from Migrations.kt: {len(stmts)}")
    for s in stmts:
        print("   " + (s if len(s) <= 96 else s[:93] + "..."))

    con = sqlite3.connect(":memory:")
    build(v_src, con)
    print(f"\nCreated v{src_v} schema ({len(v_src['entities'])} tables).")
    for s in stmts:
        con.execute(s)
    con.commit()
    n = len(con.execute("SELECT name FROM sqlite_master WHERE type='table' "
                        "AND name NOT LIKE 'sqlite_%'").fetchall())
    print(f"Applied migration. Tables now: {n}")

    expected, live = read_expected(v_dst), read_live(con)
    problems = diff(expected, live)

    print("\n" + "=" * 74)
    if problems:
        print(f"RESULT: FAILED — {len(problems)} schema problem(s)\n")
        for p in problems:
            print(p)
        return 1
    print(f"RESULT: PASSED — migrated schema matches {dst_v}.json exactly")
    print("=" * 74)

    added = sorted(set(live) - set(read_expected(v_src)))
    print(f"\nTables introduced by this migration: {added}")
    for t in added:
        cols = live[t]["columns"]
        print(f"\n  {t}:")
        for cn, c in cols.items():
            print(f"    {cn:12} affinity={c['affinity']:8} notNull={str(c['notNull']):5} "
                  f"pk={c['pkPosition']} default={c['defaultValue']}")
        for nme, i in sorted(live[t]["indices"].items()):
            print(f"    index {nme:38} cols={i['columns']}")
        expected_idx = sorted(read_expected(v_dst)[t]["indices"])
        print(f"    index count expected={len(expected_idx)} found={len(live[t]['indices'])} "
              f"-> {'PASS' if len(live[t]['indices']) == len(expected_idx) else 'FAIL'}")

    # A migration test seeds rows into pre-existing tables to prove data survives.
    # If those inserts violate a foreign key, the test fails in setup — which looks
    # like a migration failure but is not. Probe every FK to say so up front.
    print("\n--- pre-migration seed-insert viability (FK enforcement ON vs OFF) ---")
    fks = [(t, f) for t, live_t in sorted(read_live(sqlite3.connect(":memory:")).items())
           for f in live_t["foreignKeys"]]
    for fk_mode in ("ON", "OFF"):
        probe = sqlite3.connect(":memory:")
        probe.execute(f"PRAGMA foreign_keys={fk_mode}")
        build(v_src, probe)
        try:
            probe.execute(
                "INSERT INTO expenses (shiftId, category, amount, note, receiptUri, "
                "timestamp, isDeductible) VALUES (1, 'FUEL', 500.0, 'seed', NULL, 1700000000000, 1)")
            probe.commit()
            print(f"  foreign_keys={fk_mode:3} -> orphan expenses row INSERTED (FK not enforced)")
        except sqlite3.IntegrityError as exc:
            print(f"  foreign_keys={fk_mode:3} -> FAILED: {exc}")
        probe.close()
    if fks:
        print(f"  tables with foreign keys: {sorted({t for t, _ in fks})}")
        print("  -> If MigrationTestHelper enables FK enforcement, seed inserts must")
        print("     insert the parent row first, or use NULL for the FK column.")
    return 0


if __name__ == "__main__":
    sys.exit(main())