"""Thermal and power policy for DroidOS.

The bands are data (``platform/power/thermal.conf``), so a device can tune them without touching
code; this module is the logic that turns a temperature into a CPU cap and a governor, and it is
tested off-device by ``scripts/check-power.py``. ``platform/power/apply-thermal.sh`` performs the
actual sysfs writes on a phone.
"""

import dataclasses

VALID_GOVERNORS = ("schedutil", "conservative", "powersave")


@dataclasses.dataclass(frozen=True)
class Band:
    """One temperature band: up to ``up_to_c`` degrees, cap the CPU at ``max_percent``."""

    up_to_c: int
    max_percent: int
    governor: str


def validate(bands):
    """Raises ValueError when a band list could throttle in the wrong direction."""
    if not bands:
        raise ValueError("at least one band is required")

    previous = None
    for band in bands:
        if band.max_percent < 0 or band.max_percent > 100:
            raise ValueError("max_percent %d is outside 0..100" % band.max_percent)
        if band.governor not in VALID_GOVERNORS:
            raise ValueError("governor %r is not one of %s" % (band.governor, ", ".join(VALID_GOVERNORS)))
        if previous is not None:
            if band.up_to_c <= previous.up_to_c:
                raise ValueError("bands must be ordered by ascending up_to_c")
            if band.max_percent > previous.max_percent:
                raise ValueError("max_percent must never increase as the temperature rises")
        previous = band

    if bands[-1].up_to_c < 100:
        raise ValueError("the last band must cover at least 100 C")


def parse(text):
    """Parses the conf format: ``up_to_c max_percent governor`` per line, ``#`` comments."""
    bands = []
    for number, raw in enumerate(text.splitlines(), start=1):
        line = raw.split("#", 1)[0].strip()
        if not line:
            continue
        parts = line.split()
        if len(parts) != 3:
            raise ValueError("line %d: expected 'up_to_c max_percent governor'" % number)
        try:
            up_to = int(parts[0])
            percent = int(parts[1])
        except ValueError:
            raise ValueError("line %d: up_to_c and max_percent must be integers" % number)
        bands.append(Band(up_to, percent, parts[2]))

    validate(bands)
    return bands


def band_for(celsius, bands):
    """The band that applies at ``celsius``; the hottest band covers anything above the table."""
    if not bands:
        raise ValueError("no bands")
    for band in bands:
        if celsius <= band.up_to_c:
            return band
    return bands[-1]


def throttle_percent(celsius, bands):
    return band_for(celsius, bands).max_percent


def governor_for(celsius, bands):
    return band_for(celsius, bands).governor


def capped_mhz(celsius, bands, max_mhz, min_mhz):
    """Clock cap for a temperature, clamped to the hardware range so it can never be zero."""
    if max_mhz <= 0 or min_mhz <= 0:
        raise ValueError("clock limits must be positive")
    if min_mhz > max_mhz:
        raise ValueError("min_mhz must not exceed max_mhz")

    target = max_mhz * throttle_percent(celsius, bands) // 100
    return max(min_mhz, min(max_mhz, target))
