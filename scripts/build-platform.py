#!/usr/bin/env python3
"""Builds and tests the DroidOS platform layer.

Every step runs the real compiler for that language and propagates its exit status:

  AIDL   compiled with the Android SDK aidl tool (found via PATH or ANDROID_HOME)
  C      compiled with gcc/clang, strict warnings promoted to errors, then self-tested
  Rust   compiled and tested with cargo when it is installed

A missing toolchain is reported as SKIPPED, never as a pass. Pass --strict to turn a skipped
step into a failure (useful on a release machine that is expected to have everything).
"""

import glob
import json
import os
import shutil
import sqlite3
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLATFORM = os.path.join(ROOT, "platform")
NATIVE = os.path.join(PLATFORM, "native")
OUT = os.path.join(ROOT, "build", "platform")
EXE = ".exe" if os.name == "nt" else ""

CFLAGS = ["-std=c11", "-Wall", "-Wextra", "-Werror", "-O2"]

skipped = []


def which(names):
    for name in names:
        found = shutil.which(name)
        if found:
            return found
    return None


def run(cmd, cwd=None):
    print("+ " + " ".join(cmd))
    sys.stdout.flush()
    return subprocess.call(cmd, cwd=cwd)


def note_skip(step, detail):
    skipped.append(step)
    print("%s: SKIPPED (%s)" % (step, detail))


def find_aidl():
    override = os.environ.get("DROIDOS_AIDL")
    if override and os.path.isfile(override):
        return override
    found = which(["aidl", "aidl.exe"])
    if found:
        return found
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    candidates = []
    if android_home:
        for pattern in ("build-tools/*/aidl.exe", "build-tools/*/aidl"):
            candidates.extend(sorted(glob.glob(os.path.join(android_home, pattern))))
    return candidates[-1] if candidates else None


def build_aidl():
    sources = sorted(glob.glob(os.path.join(PLATFORM, "aidl", "**", "*.aidl"), recursive=True))
    if not sources:
        print("AIDL: FAILED (no interfaces found)")
        return 1

    aidl = find_aidl()
    if not aidl:
        note_skip("AIDL", "aidl not found; set ANDROID_HOME or DROIDOS_AIDL")
        return 0

    out_dir = os.path.join(OUT, "aidl")
    os.makedirs(out_dir, exist_ok=True)
    include = os.path.join(PLATFORM, "aidl")

    for source in sources:
        code = run([aidl, "-I" + include, "-o" + out_dir, source])
        if code != 0:
            print("AIDL: FAILED while compiling %s" % os.path.relpath(source, ROOT))
            return code

    generated = glob.glob(os.path.join(out_dir, "**", "*.java"), recursive=True)
    if len(generated) < len(sources):
        print(
            "AIDL: FAILED (compiled %d sources but only %d Java files were produced)"
            % (len(sources), len(generated))
        )
        return 1
    print("AIDL: %d interface(s) compiled to %d Java file(s)" % (len(sources), len(generated)))
    return 0


def build_c():
    compiler = which(["cc", "gcc", "clang"])
    if not compiler:
        note_skip("C", "no C compiler on PATH")
        return 0

    os.makedirs(OUT, exist_ok=True)
    droidctl = os.path.join(OUT, "droidctl" + EXE)

    code = run([compiler] + CFLAGS + ["-I", NATIVE, "-o", droidctl,
                                      os.path.join(NATIVE, "droidctl.c")])
    if code != 0:
        print("C: FAILED to compile droidctl")
        return code

    code = run([droidctl, "--selftest"])
    if code != 0:
        print("C: FAILED droidctl selftest")
        return code

    try:
        payload = json.loads(subprocess.check_output([droidctl, "all", "--json"]).decode("utf-8"))
    except (ValueError, OSError) as error:
        print("C: FAILED droidctl did not emit valid JSON: %s" % error)
        return 1
    for key in ("version", "kernel", "uptime", "memory", "selinux"):
        if key not in payload:
            print("C: FAILED droidctl JSON is missing '%s'" % key)
            return 1
    print("C: droidctl JSON schema valid")

    tests = os.path.join(OUT, "netfilter_test" + EXE)
    code = run([compiler] + CFLAGS + ["-I", NATIVE, "-o", tests,
                                      os.path.join(NATIVE, "netfilter_rules.c"),
                                      os.path.join(NATIVE, "netfilter_rules_test.c")])
    if code != 0:
        print("C: FAILED to compile the netfilter tests")
        return code
    return run([tests])


def build_rust():
    cargo = which(["cargo"])
    if not cargo:
        note_skip("RUST", "cargo not installed")
        return 0
    manifest_dir = os.path.join(PLATFORM, "rust", "droidpolicy")
    return run([cargo, "test", "--quiet"], cwd=manifest_dir)


def build_cxx():
    compiler = which(["g++", "clang++"])
    if not compiler:
        note_skip("C++", "no C++ compiler on PATH")
        return 0

    os.makedirs(OUT, exist_ok=True)
    tests = os.path.join(OUT, "blocklist_test" + EXE)
    code = run([
        compiler, "-std=c++20", "-Wall", "-Wextra", "-Werror", "-O2", "-I", NATIVE, "-o", tests,
        os.path.join(NATIVE, "blocklist_store.cpp"),
        os.path.join(NATIVE, "blocklist_store_test.cpp"),
    ])
    if code != 0:
        print("C++: FAILED to compile the blocklist tests")
        return code
    return run([tests])


def find_android_jar():
    android_home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    if not android_home:
        return None
    jars = sorted(glob.glob(os.path.join(android_home, "platforms", "*", "android.jar")))
    return jars[-1] if jars else None


def build_java():
    javac = which(["javac"])
    if not javac:
        java_home = os.environ.get("JAVA_HOME") or ""
        candidate = os.path.join(java_home, "bin", "javac" + EXE)
        javac = candidate if os.path.isfile(candidate) else None
    if not javac:
        note_skip("JAVA", "javac not found; set JAVA_HOME")
        return 0

    android_jar = find_android_jar()
    if not android_jar:
        note_skip("JAVA", "android.jar not found; set ANDROID_HOME")
        return 0

    generated = sorted(glob.glob(os.path.join(OUT, "aidl", "**", "*.java"), recursive=True))
    if not generated:
        note_skip("JAVA", "the AIDL stubs are unavailable")
        return 0

    sources = generated + sorted(
        glob.glob(os.path.join(PLATFORM, "java", "**", "*.java"), recursive=True)
    )
    classes = os.path.join(OUT, "java")
    os.makedirs(classes, exist_ok=True)

    code = run([javac, "-Xlint:all", "-Werror", "-cp", android_jar, "-d", classes] + sources)
    if code != 0:
        print("JAVA: FAILED to compile the platform service against android.jar")
        return code
    print("JAVA: %d source file(s) compiled against %s" % (len(sources), android_jar))
    return 0


def assemble_asm():
    sources = sorted(glob.glob(os.path.join(NATIVE, "*.S")))
    if not sources:
        return 0

    assembler = which(["aarch64-linux-gnu-gcc", "aarch64-linux-android-clang", "clang"])
    if not assembler:
        note_skip("ASM", "no AArch64 assembler on PATH")
        return 0

    os.makedirs(OUT, exist_ok=True)
    for source in sources:
        code = run([
            assembler, "-c", "-o", os.path.join(OUT, os.path.basename(source) + ".o"), source
        ])
        if code != 0:
            print("ASM: FAILED to assemble %s" % os.path.basename(source))
            return code
    print("ASM: %d AArch64 source file(s) assembled" % len(sources))
    return 0


def check_sql():
    schema = os.path.join(PLATFORM, "sql", "schema.sql")
    if not os.path.isfile(schema):
        print("SQL: FAILED (platform/sql/schema.sql is missing)")
        return 1

    with open(schema, "r", encoding="utf-8") as handle:
        script = handle.read()

    connection = sqlite3.connect(":memory:")
    problems = []

    def expect_rejected(statement, params, problem):
        try:
            connection.execute(statement, params)
        except sqlite3.IntegrityError:
            return
        problems.append(problem)

    try:
        # Applied twice: the schema must be idempotent, as it is on every boot.
        connection.executescript(script)
        connection.executescript(script)

        if connection.execute("SELECT version FROM schema_version").fetchall() != [(1,)]:
            problems.append("schema_version should hold exactly one row, version 1")

        connection.execute(
            "INSERT INTO uid_blocklist (uid, package_name) VALUES (?, ?)",
            (10123, "dev.example.app"),
        )
        expect_rejected(
            "INSERT INTO uid_blocklist (uid, package_name) VALUES (?, ?)",
            (10123, "dev.example.other"),
            "a duplicate uid was accepted",
        )
        expect_rejected(
            "INSERT INTO uid_blocklist (uid, package_name) VALUES (?, ?)",
            (100000, "dev.example.range"),
            "an out-of-range uid was accepted",
        )

        connection.execute(
            "INSERT INTO privacy_events (sensor, package_name, started_at, duration_ms)"
            " VALUES (?, ?, ?, ?)",
            ("camera", "dev.example.app", 1700000000, 4200),
        )
        expect_rejected(
            "INSERT INTO privacy_events (sensor, package_name, started_at) VALUES (?, ?, ?)",
            ("not-a-sensor", "dev.example.app", 1700000001),
            "an unknown sensor was accepted",
        )

        connection.execute(
            "INSERT INTO update_history (version_code, version_name, sha256, installed_at)"
            " VALUES (?, ?, ?, ?)",
            (4, "1.3.0", "a" * 64, 1700000000),
        )
        expect_rejected(
            "INSERT INTO update_history (version_code, version_name, sha256, installed_at)"
            " VALUES (?, ?, ?, ?)",
            (4, "1.2.1", "too-short", 1700000001),
            "a malformed checksum was accepted",
        )

        indexes = set(
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type = 'index'"
            ).fetchall()
        )
        for index in ("privacy_events_by_sensor_time", "privacy_events_by_package"):
            if index not in indexes:
                problems.append("index %s was not created" % index)

        rows = connection.execute(
            "SELECT started_at FROM privacy_events WHERE sensor = 'camera'"
            " ORDER BY started_at DESC"
        ).fetchall()
        if rows != [(1700000000,)]:
            problems.append("the privacy_events query did not return the inserted row")
    finally:
        connection.close()

    if problems:
        for problem in problems:
            print("SQL: FAILED %s" % problem)
        return 1

    print("SQL: schema, constraints, indexes and idempotency verified")
    return 0


def check_power():
    script = os.path.join(ROOT, "scripts", "check-power.py")
    if not os.path.isfile(script):
        print("POWER: FAILED (scripts/check-power.py is missing)")
        return 1
    return run([sys.executable, script])


def check_footprint():
    script = os.path.join(ROOT, "scripts", "check-footprint.py")
    if not os.path.isfile(script):
        print("FOOTPRINT: FAILED (scripts/check-footprint.py is missing)")
        return 1
    return run([sys.executable, script, "--budget-mb", "6"])


def main(argv):
    strict = "--strict" in argv
    print("DroidOS platform build")
    print("sources: %s" % os.path.relpath(PLATFORM, ROOT))
    print("output:  %s" % os.path.relpath(OUT, ROOT))

    for step in (
        build_aidl,
        build_c,
        build_cxx,
        build_java,
        check_sql,
        check_power,
        check_footprint,
        assemble_asm,
        build_rust,
    ):
        code = step()
        if code != 0:
            print("platform layer: FAILED (%s)" % step.__name__)
            return code

    if skipped and strict:
        print("platform layer: FAILED, required toolchains are missing: %s" % ", ".join(skipped))
        return 1

    if skipped:
        print("platform layer: executed steps passed; skipped: %s" % ", ".join(skipped))
    else:
        print("platform layer: all steps passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
