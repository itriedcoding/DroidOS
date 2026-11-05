#!/usr/bin/env python3
"""Measures the release APK so "lightweight" is a number rather than a claim.

    python scripts/check-footprint.py --budget-mb 6

Returns 0 when the APK is inside the budget, 1 when it is over. A missing APK is reported as
SKIPPED, not as a pass.
"""

import argparse
import os
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")


def main(argv):
    parser = argparse.ArgumentParser(description="Measure the release APK")
    parser.add_argument("--budget-mb", type=float, default=6.0, help="maximum size in MiB")
    args = parser.parse_args(argv)

    if not os.path.isfile(APK):
        print("FOOTPRINT: SKIPPED (no release APK; run scripts/build.sh release)")
        return 0

    size = os.path.getsize(APK)
    mib = size / (1024.0 * 1024.0)

    try:
        with zipfile.ZipFile(APK) as archive:
            entries = sorted(
                ((info.file_size, info.filename) for info in archive.infolist()),
                reverse=True,
            )
            dex_files = [name for _, name in entries if name.endswith(".dex")]
    except zipfile.BadZipFile:
        print("FOOTPRINT: FAILED (the APK is not a valid zip archive)")
        return 1

    print("FOOTPRINT: %.2f MiB compressed, %d dex file(s)" % (mib, len(dex_files)))
    print("  largest uncompressed entries:")
    for entry_size, name in entries[:5]:
        print("    %8.1f KiB  %s" % (entry_size / 1024.0, name))

    if mib > args.budget_mb:
        print("FOOTPRINT: FAILED, %.2f MiB is over the %.1f MiB budget" % (mib, args.budget_mb))
        return 1

    print("FOOTPRINT: within the %.1f MiB budget" % args.budget_mb)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
