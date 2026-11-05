# droidos_arm64 device tree

The device tree for a DroidOS image: product definition, board configuration, the prebuilt shell
APK import, the policy paths and the diagnostics init service.

| File | Purpose |
| --- | --- |
| `AndroidProducts.mk` | Registers `droidos_arm64-user` / `-userdebug` for `lunch` |
| `droidos_arm64.mk` | Inherits `aosp_arm64`, adds the DroidOS packages and product properties |
| `device.mk` | Package list, init service, Soong namespace, dexpreopt for the shell |
| `BoardConfig.mk` | ARM64 board config, kernel source and fragment, SELinux policy dirs |
| `Android.bp` | `android_app_import` for the staged shell APK |
| `init.droidos.rc` | On-demand `droidctl` service, disabled at boot |

## Using it

```bash
repo init -u https://android.googlesource.com/platform/manifest -b android-15.0.0_r1
repo sync -c -j8                                  # AOSP is ~200 GB, not vendored here
bash scripts/stage-prebuilt.sh                    # stage the shell APK into prebuilt/
ANDROID_BUILD_TOP=$PWD/aosp bash scripts/aosp-link.sh
cd aosp && . build/envsetup.sh && lunch droidos_arm64-userdebug && m
```

`BoardConfig.mk` sets `TARGET_CPU_VARIANT := generic` and `TARGET_KERNEL_SOURCE`. Building for a
real phone means inheriting that SoC's vendor product and setting the right CPU variant; the rest
of this tree stays unchanged.

## Honest status

None of these makefiles has been run through Soong: this machine has no AOSP tree, and Soong only
exists inside one. They are written against the standard Android build variables
(`PRODUCT_PACKAGES`, `PRODUCT_COPY_FILES`, `SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS`,
`TARGET_KERNEL_CONFIG`) and are intended to be validated with `m` on a Linux build host. Anything
that fails there is a real bug, not a missing feature.
