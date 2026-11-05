// AArch64 memory barrier helpers.
//
// The platform's key-handling paths publish data to the kernel and to other cores, which needs
// explicit ordering on ARM64; these wrappers keep the barrier kinds in one place so no caller
// guesses between dmb, dsb and isb.
//
// Assembled as part of the device image, for example:
//   aarch64-linux-gnu-gcc -c -o barriers_arm64.o native/barriers_arm64.S
//   (inside AOSP: Soong assembles the same file for arm64)
//
// This host has no AArch64 assembler (no aarch64-linux-gnu-gcc, no clang, no NDK), so
// scripts/build-platform.py reports ASM as SKIPPED here rather than pretending it was checked.

    .text
    .arch armv8-a

// void droid_full_barrier(void)
// Full system barrier: use after writing data that the kernel or a device must observe.
    .global droid_full_barrier
    .type droid_full_barrier, %function
    .balign 4
droid_full_barrier:
    dsb sy
    ret
    .size droid_full_barrier, .-droid_full_barrier

// void droid_shared_barrier(void)
// Inner-shareable barrier: orders memory shared with other CPUs of the same cluster.
    .global droid_shared_barrier
    .type droid_shared_barrier, %function
    .balign 4
droid_shared_barrier:
    dmb ish
    ret
    .size droid_shared_barrier, .-droid_shared_barrier

// void droid_instruction_barrier(void)
// Instruction synchronisation: use after writing executable code before branching to it.
    .global droid_instruction_barrier
    .type droid_instruction_barrier, %function
    .balign 4
droid_instruction_barrier:
    isb
    ret
    .size droid_instruction_barrier, .-droid_instruction_barrier
