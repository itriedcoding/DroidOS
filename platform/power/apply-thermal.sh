#!/usr/bin/env bash
# Applies the DroidOS thermal policy to a running device.
#
# Reads thermal.conf, samples the SoC temperature and caps every CPU policy accordingly. Needs
# root, because the cpufreq nodes are root-owned.
#
#   adb push platform/power /data/local/tmp/droidos-power
#   adb shell su -c 'bash /data/local/tmp/droidos-power/apply-thermal.sh'
#
# The arithmetic in this script mirrors platform/power/thermal_policy.py, which is unit-tested by
# scripts/check-power.py. The sysfs writes have never run against real hardware here - there is no
# device in this repository's development environment.

set -euo pipefail

CONF=${1:-$(dirname "$0")/thermal.conf}
ZONE=${DROIDOS_THERMAL_ZONE:-/sys/class/thermal/thermal_zone0/temp}
CPUFREQ=${DROIDOS_CPUFREQ_DIR:-/sys/devices/system/cpu/cpufreq}

[ -r "$CONF" ] || { echo "thermal: cannot read $CONF" >&2; exit 1; }
[ -r "$ZONE" ] || { echo "thermal: no thermal zone at $ZONE" >&2; exit 1; }

raw=$(cat "$ZONE")
celsius=$(( raw / 1000 ))

percent=""
governor=""
while read -r up band_percent band_governor; do
    case "$up" in ''|'#'*) continue ;; esac
    if [ "$celsius" -le "$up" ]; then
        percent=$band_percent
        governor=$band_governor
        break
    fi
done < "$CONF"

[ -n "$percent" ] || { echo "thermal: no band matched ${celsius}C" >&2; exit 1; }

applied=0
for policy in "$CPUFREQ"/policy*; do
    [ -d "$policy" ] || continue
    hardware_max=$(cat "$policy/cpuinfo_max_freq")
    hardware_min=$(cat "$policy/cpuinfo_min_freq")
    target=$(( hardware_max * percent / 100 ))
    if [ "$target" -lt "$hardware_min" ]; then
        target=$hardware_min
    fi
    echo "$target" > "$policy/scaling_max_freq"
    echo "$governor" > "$policy/scaling_governor"
    applied=$(( applied + 1 ))
done

echo "thermal: ${celsius}C -> ${percent}% (${governor}) on ${applied} policy group(s)"
