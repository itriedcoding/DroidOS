# DroidOS

A custom Android shell: home screen, lock screen, notification shade, quick settings, recents,
settings hub and a suite of first-party apps, built on the real Android platform.

Everything in this project is functional. There are no mock screens, no placeholder buttons and
no simulated data: every control reads and writes real system state through public Android APIs,
and every failure path reports what actually happened instead of pretending to succeed.

---

## What DroidOS is (and is not)

**It is** a complete launcher-replacement shell plus first-party applications:
a real `HOME` app that replaces your launcher, notification shade, lock screen, quick settings,
recents view, file manager, notes app, encrypted vault, security scanner, firewall, calculator,
terminal, sensor reader, converter, digest tools, text tools, compass, netcheck, ledger,
cleaner, torch, level, time lab, cipher, speed test and generator.

**It is not** a fork of the Android kernel or framework. Verified boot, full-disk encryption
and the app sandbox come from the Android platform and the device firmware. A normal application
cannot reimplement them, and this project does not claim to. What DroidOS adds on top of the
platform is described in [Security model](#security-model).

This distinction matters: claiming a chat-built app "implements secure boot" would be exactly
the kind of fake feature this project avoids.

---

## Features

### Shell
| Feature | Implementation |
| --- | --- |
| Home screen | Custom Compose surface with clock, live system/security/privacy widgets, favourites, dock |
| App drawer | Real `PackageManager` enumeration, search, cached icons, adaptive grid |
| Lock screen | `BiometricPrompt` unlock plus a PBKDF2-hashed PIN with exponential lockout |
| Notification shade | `NotificationListenerService` reading and dismissing live notifications |
| Quick settings | Wi-Fi, Bluetooth, torch, DND, rotation lock, dark theme, brightness slider |
| Recents | `UsageStatsManager` history with foreground time |
| Theming | Seven accent palettes (hot pink by default), true-black gradients, white text, five wallpapers |
| App lock | Chosen apps require biometrics before the launcher will open them |

### Built-in apps
* **Files** — Storage Access Framework browser with create folder, rename, delete, open, revoke
* **Notes** — SQLite-backed CRUD with search, pinning and inline editing
* **Vault** — AES-256-GCM encrypted notes and files, key held by the Android Keystore
* **Security scan** — heuristic risk review of every installed package, with cited findings
* **Firewall** — per-app network blocking through a local, non-forwarding `VpnService`
* **Privacy** — live camera/microphone indicators, system-wide mic mute, permission holders
* **Calculator** — recursive-descent expression evaluator with live preview
* **Terminal** — real `sh` commands through `ProcessBuilder("/system/bin/sh", "-c", …)`, with
  stdout and stderr kept apart, the process's own exit code, a cancel action and a timeout.
  The shell runs as the app's UID inside the app sandbox, so system-only commands fail with the
  kernel's permission error instead of printing something invented. There is no fake root
* **Sensors** — every sensor the device's `SensorManager` actually reports, with live values in
  their real units plus vendor, range, resolution and power. Sensors the hardware lacks are not
  listed, and a refused listener is reported as such
* **Convert** — length, mass, temperature, data, speed and time through one base unit per
  category, so there is no pairwise factor matrix to get wrong. Amounts parse with grouping and
  the result is formatted to useful digits
* **Digest** — SHA-256, SHA-1 and MD5 through the platform's `MessageDigest`, Base64 encode/decode
  with an explicit error for invalid input, and UUID v4 values
* **Text tools** — characters, words, lines and sentences, case transforms, slug and line
  sorting, and regex replace that reports an invalid pattern instead of returning nothing
* **Compass** — azimuth from `SensorManager`'s rotation vector and `getOrientation`, smoothed along
  the shortest arc so the needle does not spin at north, with the sensor's own accuracy value
* **Netcheck** — a real DNS lookup, a real TCP connect and a real HTTPS request against a host you
  type, each timed, with the platform's error text when one fails
* **Ledger** — income and spending in integer cents in a local SQLite database, with totals that
  cannot accumulate rounding error
* **Cleaner** — capacity from `StorageStatsManager`, a real walk of every directory the app can
  read, a cache purge whose freed bytes are measured before and after, and the media store's own
  totals. Media deletion goes through `MediaStore.createDeleteRequest`, so the system confirms it
* **Torch** — the camera flash through `CameraManager.setTorchMode`, following state changes made
  elsewhere, and saying plainly when the device has no flash
* **Level** — pitch and roll from the gravity vector (accelerometer fallback) with a low-pass
  filter and a bubble that clamps at the rim
* **Time lab** — live clocks in any zone, epoch and ISO parsing, offset labels that respect DST,
  and whole-day date differences
* **Cipher** — AES-256-GCM with PBKDF2-HMAC-SHA256 at 120,000 rounds, a fresh salt and IV per
  message, and a GCM tag that rejects tampering
* **Speed test** — a real HTTPS download streamed and counted byte by byte with a 25 MB cap,
  reporting the measured rate rather than a guess
* **Generator** — `SecureRandom` passwords with per-set guarantees and honest entropy maths, plus
  dice and coin flips
* **Clock** — alarms through `AlarmManager.setAlarmClock`, a timer that finishes in the
  background, and a stopwatch with laps. Exact alarms need the user-granted
  `SCHEDULE_EXACT_ALARM`, and Android cancels pending alarms if the app is force-stopped

### Quick settings and notifications
* Volume mixer for the media, ring and alarm streams, driven by `AudioManager`, with per-stream mute
* Brightness, rotation lock, screen timeout, torch, Wi-Fi, Bluetooth, Do Not Disturb and theme tiles
* Live notification list with each app's own action buttons, run through its `PendingIntent`

### App drawer
* Search across built-in apps and installed packages
* Long-press any app for its info page, uninstall, or to place it behind the lock

### Search
* One query across built-in tools, installed packages, the notes database and the folder granted to Files
* Sources that are unavailable are named in the result list, never filled with invented entries

### Settings
Appearance, Display, Sound & vibration, Location, Date & time, Wi-Fi & Bluetooth, Battery,
Storage, Accessibility, Installed apps, Permission audit, Security centre, System update,
Diagnostics, About.

### Security & privacy
* AES-256-GCM chunked container, key generated inside the Android Keystore
* PBKDF2-HMAC-SHA256 (120k iterations, 128-bit salt) PIN storage with constant-time compare
* Signing-identity pinning (trust-on-first-use) to detect re-packaged builds
* Real per-app firewall with no external server
* Live privacy indicators from `CameraManager` and `AudioManager` callbacks
* Permission audit sourced from `PackageManager.checkPermission`
* Heuristic malware engine: install source, permissions, control surfaces, debuggable flag
* Signed update channel: HTTPS manifest, SHA-256 verification, same-signer check
* Optional daily automatic update check that raises a notification, never a silent install
* Structured logging with rotation, crash reports, and a diagnostics viewer

---

## Requirements

* Windows, macOS or Linux
* About 4 GB free disk space for the toolchain and Gradle caches
* No administrator rights required — everything installs inside `.tools/`

The bootstrap script installs a self-contained JDK 17, Gradle 8.9 and the Android SDK
(platform 35, build-tools 35.0.0, platform-tools) into `.tools/`, which is git-ignored.

```bash
./scripts/bootstrap-toolchain.sh
```

Android Studio is optional. If you have it, open the project root and let it use its own SDK;
`local.properties` is generated per machine and never committed.

---

## Build

```bash
./scripts/build.sh            # debug APK + unit tests
./scripts/build.sh release    # minified release APK + unit tests
./scripts/build.sh test       # unit tests only
./scripts/build.sh lint       # Android lint
```

Outputs:

```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

Both variants are signed with the development key in `.tools/keystore/droidos.jks`
(password `droidos-dev`), which makes the APK installable as-is. For a real release, create your
own keystore and pass it through the environment variables read by `app/build.gradle.kts`:

```
DROIDOS_KEYSTORE, DROIDOS_KEYSTORE_PASSWORD, DROIDOS_KEY_ALIAS, DROIDOS_KEY_PASSWORD
```

Never commit a production keystore.

---

## Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then set DroidOS as the home app:

**Settings → Apps → Default apps → Home app → DroidOS**

Supported: Android 11 (API 30) and newer, `minSdk 30`, `targetSdk 35`, any screen size.

### Optional grants that unlock extra features

Each is requested through the official system UI at the moment it is needed, never silently.

| Grant | Enables | How |
| --- | --- | --- |
| Notification listener | Shade notifications and dismissal | Shade → "Open access settings" |
| Usage access | Recents list | Recents → "Grant access" |
| VPN consent | Firewall | Firewall toggle (one system dialog) |
| Modify system settings | Rotation lock and brightness | Tile → prompt |
| Biometric enrolment | Lock screen / app lock / vault | System settings |

---

## Architecture

```
app/src/main/java/dev/droidos/shell/
├── core/        Outcome, AppLog, CrashGuard, BuildInfo, ServiceLocator, app entry
├── data/        DataStore-backed settings repository and preference model
├── security/    CryptoManager, VaultFormat, PinManager, IntegrityVerifier,
│                PermissionAudit, PrivacyMonitor, ScanEngine
├── firewall/    VpnService, controller, pure rule maths, boot receiver
├── system/      Battery, memory, storage and network telemetry
├── updates/     Manifest parsing, version comparison, verified download
├── shell/       Shell root, navigator, lock screen, home, drawer, shade, recents, controls
├── apps/        Files, Notes, Vault, Calculator, Scanner, Privacy
├── settings/    Settings hub and every sub-screen
└── ui/          Theme engine, shared components, biometric gate
```

Design notes:

* **One activity, one composition.** Navigation, the shade and the lock screen are Compose state,
  which keeps them consistent and avoids activity-restart races.
* **Manual dependency container.** `ServiceLocator` builds repositories against the application
  context, so the VPN service and notification listener share instances with the UI.
* **Typed results, no swallowed errors.** Repository methods return `Outcome.Ok`/`Outcome.Err`;
  every failure reaches either the screen that triggered it or the diagnostics log.
* **Pure logic is separated.** `VaultFormat`, `PinManager`, `ScanEngine`, `FirewallRules`,
  `CalculatorEngine`, `Fmt` and `Versions` have no Android dependencies and are unit-tested.
* **Memory discipline.** App icons are cached in a bounded `LruCache`, lists are lazy, the
  privacy monitors only run while their screen is visible, and no background polling services
  exist.

---

## Platform layer

`app/` is the shell. `platform/` holds the system-side source that would ship in a device image
rather than in an APK, written in the languages a platform actually uses:

| Stack | Where | What it does |
| --- | --- | --- |
| C11 | `platform/native/droidctl.c` | Kernel diagnostics helper reading `/proc/uptime`, `/proc/meminfo` and `/sys/fs/selinux/enforce`; a value the kernel does not expose is reported as unavailable, never invented |
| C11 | `platform/native/netfilter_rules.c` | Renders the nftables/iptables rule that drops egress for one application UID |
| AIDL | `platform/aidl/dev/droidos/platform/` | `IDroidPlatformService`: API version, build fingerprint, firewall state, UID blocklist, privacy callbacks |
| C++20 | `platform/native/blocklist_store.cpp` | Sorted, duplicate-free UID blocklist that applies the same UID range rule as the database and the rule renderer |
| Java | `platform/java/dev/droidos/platform/PlatformService.java` | `IDroidPlatformService.Stub` implementation: blocklist state, `RemoteCallbackList` privacy fan-out, compiled against `android.jar` |
| ARM64 assembly | `platform/native/barriers_arm64.S` | `dsb`/`dmb`/`isb` helpers for the key-handling and cache-maintenance paths |
| Rust | `platform/rust/droidpolicy` | Dependency-free capability risk scoring with unit tests |
| SELinux | `platform/selinux/droidos.te` | Unprivileged domain for the helper: explicit allows, `neverallow` guards, no permissive mode |
| Blueprint | `platform/Android.bp` | Soong modules for every module above |
| SQL | `platform/sql/schema.sql` | Blocklist, privacy-event and update-history tables with CHECK constraints and indexes |
| Python, Bash, Make | `scripts/build-platform.py`, `scripts/build.sh`, `platform/Makefile` | Build drivers that run the real compilers and return their exit status |

```bash
python scripts/build-platform.py          # compile and test everything this host supports
python scripts/build-platform.py --strict # fail if a toolchain is missing
```

The driver compiles the AIDL interfaces with the SDK `aidl` tool, builds the C and C++ with
`-Wall -Wextra -Werror`, runs `droidctl --selftest`, the 16 netfilter rule checks and the 25
blocklist checks, compiles the Java service against `android.jar` with `-Xlint:all -Werror`,
applies the SQL schema twice to an in-memory database and asserts every constraint, index and the
idempotency, assembles the ARM64 sources when an AArch64 assembler exists, and runs `cargo test`
when Rust is present.

### How the shell uses it

The shell compiles the same AIDL (`app/build.gradle.kts` points at `platform/aidl`; there is no
second copy) and `PlatformBridge` binds to `dev.droidos.platform.PlatformServiceHost` with an
explicit component name. Three outcomes, all truthful:

* **Bound** — the bridge checks `getApiVersion()` against the surface it understands and refuses a
  newer one. Firewall rules are installed through `applyUidBlocklist` (package names resolved to
  real UIDs by the package manager) and privacy transitions arrive with platform attribution.
* **No such app** — a retail phone returns `false` from `bindService`. Every call then reports
  unavailable, the in-process VPN tunnel stays the only blocker, and the Privacy screen shows no
  attribution line rather than an invented one.
* **Disconnected or wrong version** — treated exactly like the second case, with the reason logged.

What this machine could not compile is reported honestly rather than claimed: the AArch64
assembly (no ARM64 assembler), the Rust crate (no cargo), the SELinux policy (needs
`checkpolicy`/`secilc`), `Android.bp` (needs Soong) and the Makefile (no make) are reported as
`SKIPPED` or noted as unrun. There is no kernel or bootable image in this repository, and AOSP is not
vendored here; [platform/README.md](platform/README.md) has the full verification matrix.

---

## Vendor tree, kernel and power

`device/` and `kernel/` are the Android-side integration: what a device image needs beyond the APK.

| Path | What it is |
| --- | --- |
| `device/droidos/droidos_arm64/{AndroidProducts,device,BoardConfig}.mk` | Product and board definition inheriting `aosp_arm64`: ARM64, kernel source and fragment paths, SELinux policy directory |
| `device/droidos/droidos_arm64/Android.bp` | `android_app_import` that places the shell APK on the image |
| `device/droidos/droidos_arm64/init.droidos.rc` | On-demand `droidctl` service, disabled at boot, labelled `u:r:droidos:s0` |
| `kernel/droidos_power.config` | Kernel fragment: schedutil, CPU thermal, step-wise governor; KASAN, ftrace and debug page alloc off |
| `kernel/Makefile` | `make image` wrapper that runs defconfig, merges the fragment, then builds `Image.gz` |
| `platform/power/thermal.conf`, `thermal_policy.py` | Thermal bands plus the tested logic that turns a temperature into a clock cap and governor |
| `scripts/{check-power,check-footprint,stage-prebuilt,aosp-link}` | Policy tests, APK size budget, APK staging, AOSP linking |

**AOSP and the kernel are not vendored, deliberately.** Together they are hundreds of gigabytes,
they build only on Linux, and a stub copy would be the fake this project avoids. `scripts/aosp-link.sh`
links these directories into a tree you sync yourself, which is how device trees are distributed:

```bash
repo init -u https://android.googlesource.com/platform/manifest -b android-15.0.0_r1
repo sync -c -j8                                 # ~200 GB, Linux host
bash scripts/stage-prebuilt.sh                   # shell APK into the device tree
ANDROID_BUILD_TOP=$PWD/aosp bash scripts/aosp-link.sh
cd aosp && . build/envsetup.sh && lunch droidos_arm64-userdebug && m
```

Nothing in `device/` or `kernel/` has been built: no Linux host, no `make`, no AArch64 cross
compiler and no AOSP tree on this machine. It is source for a real build, not a claim of one.

### Staying light and cool

The image is ARM64-only with no optional AOSP test packages, the shell APK is 3.6 MiB, and the
kernel fragment enables thermal management while dropping KASAN, UBSAN, ftrace and debug page
allocation. The thermal table stays fully open below 45 C and steps the CPU cap down from there, so
sustained load throttles instead of shutting the phone down; its logic is unit-tested and
`scripts/check-footprint.py` fails a build that grows past 6 MiB. The sysfs writes in
`apply-thermal.sh` have not run on hardware here.

---

## Security model

| Concern | What DroidOS actually does |
| --- | --- |
| Boot integrity | Not applicable to an app. Kernel verified boot is firmware. DroidOS pins its own signing certificate on first launch and flags any later build signed by a different identity. |
| Data encryption | Real: AES-256-GCM, chunked container, per-chunk nonces, key inside the Android Keystore and never exported. |
| Sandboxing | Provided by Android. DroidOS adds an app-lock layer enforced at the single launch path of the shell. |
| Authentication | `BiometricPrompt` for device biometrics; PIN fallback hashed with PBKDF2-HMAC-SHA256 and constant-time verification, with lockout backoff. |
| Firewall | Real local VPN; blocked apps are captured by a tunnel that is never read, so the kernel drops their packets. |
| Privacy | Live camera/mic callbacks, mute control, and a permission audit over the package database. |
| Malware protection | Heuristic, not signature-based. Every finding states the fact it was derived from; nothing is guessed. |
| Updates | HTTPS-only manifest, SHA-256 of the APK, and an APK-signature equality check against the installed app before the installer is invoked. |

Threat model: DroidOS defends against casual access to the shell and vault, against silent
network exfiltration by installed apps, and against tampered update packages. It cannot defend
against a rooted device, a malicious OS build, or an attacker with the device unlock credential.

---

## Testing

```bash
./scripts/build.sh test
```

Unit tests cover the pure logic: expression parsing and precedence, vault container round-trip
and tamper rejection, PIN hashing and lockout policy, risk-rule behaviour and thresholds,
firewall allow-list maths, volume step mapping, stopwatch accumulation, alarm next-occurrence
scheduling, version comparison and formatting, terminal command parsing (quoting, `cd` path
resolution, builtin detection, exit labels), sensor label/unit formatting, unit conversion factors,
SHA/Base64 known-answer vectors, text statistics and regex replacement, compass cardinal names and
circular smoothing, money parsing with ledger totals, storage formatting, level trigonometry, time parsing and zone
offsets, AES-GCM round trips and tamper rejection, password entropy and throughput maths.

Instrumented tests (`app/src/androidTest`) require a device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

---

## Release artifacts

`./scripts/build.sh debug` and `./scripts/build.sh release` produce two APKs. Both are signed by
the development keystore, so the update channel's signer check accepts either of them:

| Build | Path | Size | Notes |
| --- | --- | --- | --- |
| debug | `app/build/outputs/apk/debug/app-debug.apk` | ~62 MB | Debuggable, keeps the full icon set |
| release | `app/build/outputs/apk/release/app-release.apk` | ~3.5 MB | R8-minified, unused resources removed |

Both carry the certificate `CN=DroidOS, OU=Shell, O=DroidOS`
(SHA-256 `a99eb98a508143479132f116ac13c097fb118a0061e8e65d9ce610bf8c0a9d04`). Confirm it with:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

The `sha256` committed in `updates/channel.json` is the checksum of the release build above - the
same file attached to release `v1.7.0`. Recompute it (`sha256sum`, or `shasum -a 256`) if you
upload a different artifact, otherwise installed clients will reject the update.

---

## Update channel

`updates/channel.json` is the manifest served to installed clients. It is fetched over HTTPS and
must contain a valid `sha256` of the published APK. Replace the values and upload the matching APK
to the URL in `apkUrl` when you publish a release; clients reject anything whose checksum or
signing certificate does not match.

Release `v1.7.0` carries `droidos-1.7.0-release.apk`, so an update check runs end to end:
manifest fetched, checksum compared, signing certificate matched against the installed app, and
the system installer invoked. Download it from
https://github.com/itriedcoding/DroidOS/releases/download/v1.7.0/droidos-1.7.0-release.apk

---

## License

MIT, see [LICENSE.md](LICENSE.md). DroidOS is a third-party launcher that runs on Android; this
repository does not include or redistribute the Android platform or Google's applications.

---

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| "Only system apps switch Wi-Fi directly" | Android 10+ restriction. DroidOS opens the system Wi-Fi panel so the change still completes. |
| Shade shows no notifications | Notification listener access is not granted. Shade → access settings. |
| Recents is empty | Usage access is not granted. Recents → grant access. |
| Firewall will not start | Another VPN is active, or consent was declined. Disconnect the other VPN and retry. |
| Update check fails | No network, or the channel URL is unreachable. The error text includes the HTTP status. |

Logs and crash reports are visible in **Settings → System → Diagnostics**.

