# Products defined by this device tree.
#
# Consumed by AOSP's build system: breakfast/lunch read COMMON_LUNCH_CHOICES from here.

PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/droidos_arm64.mk

COMMON_LUNCH_CHOICES := \
    droidos_arm64-user \
    droidos_arm64-userdebug
