# DroidOS platform layer

The shell in `app/` is the user-facing half of DroidOS. This directory holds the system-side code:
the pieces that would ship in a device image rather than in an APK. It is plain source, built by
`scripts/build-platform.py` on a developer machine and by Soong (`Android.bp`) inside an AOSP tree.

## Layout

| Path | Language | Purpose |
| --- | --- | --- |
| `native/droidctl.c` | C11 | Kernel diagnostics helper (`/proc/uptime`, `/proc/meminfo`, `/sys/fs/selinux/enforce`) with a self-test |
| `native/netfilter_rules.c` | C11 | Renders nftables/iptables rules that drop egress for one application UID |
| `native/netfilter_rules_test.c` | C11 | Test binary for the rule compiler |
| `aidl/dev/droidos/platform/*.aidl` | AIDL | `IDroidPlatformService` and its privacy callback |
| `native/blocklist_store.{hpp,cpp}` | C++20 | Sorted, duplicate-free UID blocklist sharing the UID range rule |
| `native/blocklist_store_test.cpp` | C++20 | Test binary for the store |
| `java/dev/droidos/platform/PlatformService.java` | Java | `IDroidPlatformService.Stub` implementation: blocklist state, privacy fan-out |
| `native/barriers_arm64.S` | ARM64 assembly | `dsb`/`dmb`/`isb` helpers for the key-handling paths |
| `rust/droidpolicy` | Rust 2021 | Dependency-free capability risk scoring with unit tests |
| `selinux/droidos.te`, `file_contexts` | SELinux policy | Unprivileged domain for `droidctl` |
| `sql/schema.sql` | SQL | Blocklist, privacy-event and update-history tables with CHECK constraints |
| `Android.bp` | Blueprint | Soong modules: `droidctl`, `libdroidnetfilter`, `droidnetfilter_test`, `libdroidpolicy`, `droidos.platform` |
| `Makefile` | Make | Wrapper around the Python driver for hosts that have make |

## Building

```bash
python scripts/build-platform.py          # compile and test everything this host supports
python scripts/build-platform.py --strict # fail if a toolchain is missing
```

The script prints every command it runs, uses `-Wall -Wextra -Werror`, and returns the compiler's
exit status. A missing toolchain is reported as `SKIPPED`, never as a pass.

## Verification status

Compiled and executed on the development host (Windows, gcc/g++ 16.2, JDK 17, Android
build-tools 35):

- `droidctl` — compiles clean under `-Werror`, `--selftest` passes, JSON output parses and carries
  the documented keys.
- `netfilter_rules` — compiles clean, 16/16 checks pass (invalid UIDs rejected, small buffers
  refused instead of truncated, no stale output left behind).
- `blocklist_store` — compiles clean as C++20 under `-Werror`, 25/25 checks pass (ordering,
  duplicate rejection, UID range, rejections leaving the store unchanged).
- AIDL — both interfaces compile with the SDK `aidl` tool into Java stubs.
- Java — `PlatformService` compiles against the generated stubs and `android.jar` with
  `-Xlint:all -Werror`.
- SQL — the schema applies twice, and every CHECK constraint, both indexes and the idempotency
  are asserted against a real SQLite engine.

Not compiled here, because the toolchain is absent from this machine:

- **AArch64 assembly** — no `aarch64-linux-gnu-gcc`, clang or NDK on this host, so
  `barriers_arm64.S` has been reviewed but never assembled.
- **Make** — `make` is not installed here, so `platform/Makefile` has not been executed; it calls
  the Python driver that has.

- **Rust** — no `cargo`/`rustc` installed. The crate is dependency-free and has five unit tests;
  `build-platform.py` runs `cargo test` for it as soon as cargo is available. Until then it is
  reported as skipped, and Soong compiles it inside AOSP.
- **SELinux** — requires `checkpolicy`/`secilc` or an AOSP tree (`m selinux_policy`). The policy
  names only the accesses the helper actually performs, but it has not been through a policy
  compiler.
- **Soong** — `Android.bp` is only meaningful inside AOSP; nothing here has run through `soong_ui`.
- **AIDL service registration** — the interfaces compile, but no system server registers them.
  On a plain Android device the shell falls back to its own binder-free implementation.
