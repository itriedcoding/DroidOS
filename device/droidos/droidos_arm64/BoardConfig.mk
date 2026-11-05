# Board configuration for DroidOS.
#
# ARM64 only: armv8-a baseline plus the kernel and policy paths this project owns. The vendor
# SoC product (when building for a real phone) sets TARGET_CPU_VARIANT and the graphics stack.

TARGET_ARCH := arm64
TARGET_ARCH_VARIANT := armv8-a
TARGET_CPU_ABI := arm64-v8a
TARGET_CPU_ABI2 :=
TARGET_CPU_VARIANT := generic
TARGET_CPU_VARIANT_RUNTIME := cortex-a55
TARGET_USES_64_BIT_BINDER := true

TARGET_BOARD_PLATFORM := droidos
TARGET_BOOTLOADER_BOARD_NAME := droidos_arm64

# Kernel: source and config fragment live in this repository under kernel/.
TARGET_KERNEL_SOURCE := kernel/droidos/linux
TARGET_KERNEL_CONFIG := droidos_defconfig
TARGET_KERNEL_ADDITIONAL_CONFIG := droidos_power.config

# SELinux policy from platform/selinux, linked into the tree by scripts/aosp-link.sh.
SYSTEM_EXT_PRIVATE_SEPOLICY_DIRS += droidos/platform/selinux

# Keep the system image lean: no debug ramdisk, no A/B overhead for a first image.
BOARD_USES_METADATA_PARTITION := false
BOARD_BUILD_SYSTEM_ROOT_IMAGE := false
