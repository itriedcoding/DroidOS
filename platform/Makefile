# Convenience wrapper for the platform layer.
#
# `make` is not installed on every development host, so scripts/build-platform.py is the canonical
# driver: it performs exactly the same work and reports missing toolchains as SKIPPED. This file
# exists so a host that does have make can use the usual entry point.
#
# Note: make is absent from the machine this repository was developed on, so these two targets have
# not been executed there; the Python driver they call has been.

PYTHON ?= python

.PHONY: platform clean help

platform:
	$(PYTHON) scripts/build-platform.py

clean:
	rm -rf build/platform

help:
	@echo "make platform   compile and test AIDL, C, C++, Java, SQL (and Rust/ASM when installed)"
	@echo "make clean      remove build/platform"
