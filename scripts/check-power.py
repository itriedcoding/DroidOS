#!/usr/bin/env python3
"""Tests the thermal policy and prints the band table it would apply.

Run by scripts/build-platform.py, and useful on its own after editing thermal.conf:

    python scripts/check-power.py
"""

import importlib.util
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POLICY = os.path.join(ROOT, "platform", "power", "thermal_policy.py")
CONF = os.path.join(ROOT, "platform", "power", "thermal.conf")

checks = 0
failures = 0


def check(condition, description):
    global checks, failures
    checks += 1
    if not condition:
        failures += 1
        print("FAIL %s" % description)


def load_module(path):
    spec = importlib.util.spec_from_file_location("thermal_policy", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def expect_rejected(action, description):
    try:
        action()
    except ValueError:
        check(True, description)
    else:
        check(False, description)


def main():
    for path in (POLICY, CONF):
        if not os.path.isfile(path):
            print("POWER: FAILED (missing %s)" % os.path.relpath(path, ROOT))
            return 1

    policy = load_module(POLICY)
    with open(CONF, "r", encoding="utf-8") as handle:
        bands = policy.parse(handle.read())

    # Cool bands must never throttle; hot bands must always throttle.
    check(policy.throttle_percent(30, bands) == 100, "30C is throttled")
    check(policy.governor_for(30, bands) == "schedutil", "30C does not use schedutil")
    check(policy.throttle_percent(300, bands) == bands[-1].max_percent, "extreme heat is not capped")

    # The cap may never increase as the temperature rises.
    previous = 101
    for celsius in range(0, 121):
        percent = policy.throttle_percent(celsius, bands)
        if percent > previous:
            check(False, "cap rises from %d%% to %d%% at %dC" % (previous, percent, celsius))
            break
        previous = percent
    else:
        check(True, "cap is monotonic from 0C to 120C")

    # Clock caps stay inside the hardware range, including at 0% throttle.
    max_mhz, min_mhz = 2400000, 300000
    check(policy.capped_mhz(30, bands, max_mhz, min_mhz) == max_mhz, "cool band does not use the full clock")
    check(policy.capped_mhz(300, bands, max_mhz, min_mhz) >= min_mhz, "clock cap falls below the hardware minimum")
    check(0 <= policy.capped_mhz(70, bands, max_mhz, min_mhz) <= max_mhz, "clock cap outside the hardware range")

    # Bad tables must be rejected instead of silently mis-throttling.
    Band = policy.Band
    expect_rejected(lambda: policy.validate([]), "empty band list accepted")
    expect_rejected(
        lambda: policy.validate([Band(45, 100, "schedutil"), Band(40, 50, "powersave")]),
        "out-of-order bands accepted",
    )
    expect_rejected(
        lambda: policy.validate([Band(45, 50, "schedutil"), Band(75, 90, "powersave")]),
        "a cap that rises with temperature accepted",
    )
    expect_rejected(
        lambda: policy.validate([Band(120, 100, "performance")]),
        "an unknown governor accepted",
    )
    expect_rejected(
        lambda: policy.validate([Band(120, 140, "schedutil")]),
        "an out-of-range percentage accepted",
    )
    expect_rejected(lambda: policy.capped_mhz(30, bands, 300000, 2400000), "inverted clock limits accepted")

    print("POWER: band table")
    print("  %-8s %-10s %s" % ("temp", "cpu cap", "governor"))
    for celsius in (30, 50, 60, 70, 90, 120):
        print(
            "  %-8s %-10s %s"
            % (
                "%dC" % celsius,
                "%d%%" % policy.throttle_percent(celsius, bands),
                policy.governor_for(celsius, bands),
            )
        )

    print("POWER: %d checks, %d failure(s)" % (checks, failures))
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
