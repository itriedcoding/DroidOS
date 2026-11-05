# Kernel

`droidos_power.config` is a **fragment**, merged on top of the device defconfig. A full defconfig
would duplicate thousands of lines the SoC vendor owns (clocks, regulators, storage) and would rot
silently; a fragment states only what DroidOS adds.

## What it turns on

- `schedutil` as the default governor, plus `conservative` and `powersave` for the thermal bands
- the thermal framework with CPU cooling and `step_wise` as the default policy
- `SCHED_MC` so the governor sees the whole cluster

## What it turns off

KASAN, UBSAN, ftrace, `DEBUG_KERNEL` and `DEBUG_PAGEALLOC`. They are valuable while developing a
kernel and expensive in a shipped image: RAM, flash, and in the case of ftrace, real CPU time.

## Build

```bash
make -f kernel/Makefile KDIR=/srv/aosp/kernel/droidos/linux image
```

which runs `droidos_defconfig`, merges the fragment, `olddefconfig`, then `Image.gz`.

## Honest status

This host has no `make`, no `aarch64-linux-gnu-gcc` and no kernel source, so **none of this has
been built here**. The fragment uses standard upstream symbols; a vendor defconfig may not have
every dependency enabled, so check the merge output for "not in final .config" lines before
trusting it. The runtime side of the policy - which band applies at which temperature - is the part
that *is* tested, in `platform/power/thermal_policy.py` via `scripts/check-power.py`.

## Source

The kernel tree itself is not vendored: an arm64 tree is several gigabytes and only builds on
Linux. `scripts/aosp-link.sh` links this directory into the checkout you synced with `repo`.
