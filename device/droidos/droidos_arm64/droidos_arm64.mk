# DroidOS product definition.
#
# Inherits the AOSP arm64 emulator/reference product and adds the DroidOS packages. A real phone
# build replaces the inherited product with that SoC's vendor product; everything below stays.

$(call inherit-product, $(SRC_TARGET_DIR)/product/core_64_bit.mk)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_arm64.mk)
$(call inherit-product, device/droidos/droidos_arm64/device.mk)

PRODUCT_NAME := droidos_arm64
PRODUCT_DEVICE := droidos_arm64
PRODUCT_BRAND := DroidOS
PRODUCT_MODEL := DroidOS
PRODUCT_MANUFACTURER := DroidOS

# Keep the image small: no debug ramdisk extras, no eng-only tools.
PRODUCT_PACKAGES += \
    droidctl \
    libdroidnetfilter \
    libdroidpolicy \
    DroidOSShell

# Product identity reported to the shell and to apps.
PRODUCT_PROPERTY_OVERRIDES += \
    ro.droidos.version=1.7.0 \
    ro.droidos.build=platform

# The update channel the shell reads; it still verifies the checksum and the signing certificate.
PRODUCT_PROPERTY_OVERRIDES += \
    ro.droidos.update.channel=https://raw.githubusercontent.com/itriedcoding/DroidOS/main/updates/channel.json
