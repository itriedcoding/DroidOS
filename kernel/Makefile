# Builds the DroidOS kernel with the power/thermal fragment merged in.
#
#   make -f kernel/Makefile KDIR=/srv/aosp/kernel/droidos/linux image
#
# None of these targets has been executed on this machine: it has no make, no AArch64 cross
# compiler and no kernel source. The fragment they merge is plain data, and scripts/check-power.py
# covers the policy arithmetic that the fragment enables at runtime.

KDIR ?= kernel/droidos/linux
ARCH ?= arm64
CROSS_COMPILE ?= aarch64-linux-gnu-
FRAGMENT := $(CURDIR)/droidos_power.config
JOBS ?= 8

.PHONY: all defconfig fragment image clean help

all: image

help:
	@echo "make -f kernel/Makefile KDIR=<path> image   configured kernel with the power fragment"
	@echo "make -f kernel/Makefile KDIR=<path> clean   mrproper"

defconfig:
	$(MAKE) -C $(KDIR) ARCH=$(ARCH) CROSS_COMPILE=$(CROSS_COMPILE) droidos_defconfig

fragment: defconfig
	$(KDIR)/scripts/kconfig/merge_config.sh -m $(KDIR)/.config $(FRAGMENT)
	$(MAKE) -C $(KDIR) ARCH=$(ARCH) CROSS_COMPILE=$(CROSS_COMPILE) olddefconfig

image: fragment
	$(MAKE) -C $(KDIR) ARCH=$(ARCH) CROSS_COMPILE=$(CROSS_COMPILE) -j$(JOBS) Image.gz

clean:
	$(MAKE) -C $(KDIR) ARCH=$(ARCH) CROSS_COMPILE=$(CROSS_COMPILE) mrproper
