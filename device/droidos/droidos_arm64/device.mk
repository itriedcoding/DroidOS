# Device packages, services and permissions for DroidOS.

PRODUCT_SOONG_NAMESPACES += droidos/platform

# droidctl ships as a host-side diagnostic on the image; the shell can run it under su, and init
# can start it on demand for a support bundle.
PRODUCT_PACKAGES += \
    droidctl \
    droidos.platform

PRODUCT_COPY_FILES += \
    $(LOCAL_PATH)/init.droidos.rc:$(TARGET_COPY_OUT_VENDOR)/etc/init/init.droidos.rc

# Runtime permissions the shell asks for are declared in its own manifest; nothing is granted
# automatically, because a launcher that silently grants itself access is the thing we avoid.
PRODUCT_PACKAGES += DroidOSShell

# Keep RAM and disk use low: the shell is dex-optimised for speed but the platform helpers are
# small native binaries, and no optional AOSP test packages are pulled in.
PRODUCT_DEXPREOPT_SPEED_APPS += DroidOSShell
