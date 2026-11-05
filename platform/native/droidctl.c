/*
 * droidctl - DroidOS platform diagnostics helper.
 *
 * Reads kernel interfaces directly (/proc, /sys/fs/selinux) and reports them as text or JSON.
 * Every value is sampled at runtime. When the kernel does not expose an interface - for example
 * when the binary is compiled for a non-Linux host - the field is reported as unavailable with a
 * reason instead of a made-up value.
 *
 * Build (Linux or MinGW host):
 *   gcc -std=c11 -Wall -Wextra -Werror -O2 -o droidctl droidctl.c
 * Run:
 *   droidctl all            human readable
 *   droidctl all --json     machine readable
 *   droidctl --selftest     parses fixtures, exits non-zero on failure
 */

#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define DROIDCTL_VERSION "0.2.0"

#if defined(__linux__)
#include <sys/utsname.h>
#define DROIDCTL_HAS_UTSNAME 1
#else
#define DROIDCTL_HAS_UTSNAME 0
#endif

#define UNAVAILABLE_REASON "requires a Linux /proc filesystem"

typedef struct {
    int available;
    double seconds;
    const char *reason;
} uptime_t;

typedef struct {
    int available;
    unsigned long total_kb;
    unsigned long free_kb;
    unsigned long available_kb;
    const char *reason;
} memory_t;

/* enforcement: 1 = enforcing, 0 = permissive, -1 = unknown */
typedef struct {
    int available;
    int enforcement;
    const char *reason;
} selinux_t;

typedef struct {
    int available;
    char sysname[65];
    char release[65];
    char machine[65];
    const char *reason;
} kernel_t;

/* ------------------------------------------------------------------ helpers */

static int read_first_line(const char *path, char *buf, size_t len)
{
    FILE *file;
    size_t i;

    if (buf == NULL || len == 0) {
        return -EINVAL;
    }
    file = fopen(path, "r");
    if (file == NULL) {
        return -errno;
    }
    if (fgets(buf, (int)len, file) == NULL) {
        fclose(file);
        return -EIO;
    }
    fclose(file);

    /* Trim the line ending so callers can compare exact strings. */
    for (i = 0; buf[i] != '\0'; i++) {
        if (buf[i] == '\n' || buf[i] == '\r') {
            buf[i] = '\0';
            break;
        }
    }
    return 0;
}

/* Maps the raw contents of /sys/fs/selinux/enforce to a tri-state result. */
static int parse_enforcement(const char *raw)
{
    if (raw == NULL) {
        return -1;
    }
    while (*raw == ' ' || *raw == '\t') {
        raw++;
    }
    if (raw[0] == '1' && raw[1] == '\0') {
        return 1;
    }
    if (raw[0] == '0' && raw[1] == '\0') {
        return 0;
    }
    return -1;
}

static int parse_uptime(const char *raw, double *seconds)
{
    double value;
    char trailing;

    if (raw == NULL || seconds == NULL) {
        return -1;
    }
    if (sscanf(raw, "%lf%c", &value, &trailing) < 1) {
        return -1;
    }
    if (value < 0.0) {
        return -1;
    }
    *seconds = value;
    return 0;
}

/* ------------------------------------------------------------------ probes */

static void probe_uptime(uptime_t *out)
{
    char raw[128];

    out->available = 0;
    out->seconds = 0.0;
    out->reason = UNAVAILABLE_REASON;

    if (read_first_line("/proc/uptime", raw, sizeof(raw)) == 0 &&
        parse_uptime(raw, &out->seconds) == 0) {
        out->available = 1;
        out->reason = NULL;
    }
}

static void probe_memory(memory_t *out)
{
    FILE *file;
    char line[256];

    out->available = 0;
    out->total_kb = 0;
    out->free_kb = 0;
    out->available_kb = 0;
    out->reason = UNAVAILABLE_REASON;

    file = fopen("/proc/meminfo", "r");
    if (file == NULL) {
        return;
    }
    while (fgets(line, (int)sizeof(line), file) != NULL) {
        unsigned long value;
        if (sscanf(line, "MemTotal: %lu kB", &value) == 1) {
            out->total_kb = value;
        } else if (sscanf(line, "MemFree: %lu kB", &value) == 1) {
            out->free_kb = value;
        } else if (sscanf(line, "MemAvailable: %lu kB", &value) == 1) {
            out->available_kb = value;
        }
    }
    fclose(file);

    if (out->total_kb > 0) {
        out->available = 1;
        out->reason = NULL;
    }
}

static void probe_selinux(selinux_t *out)
{
    char raw[16];
    int enforcement;

    out->available = 0;
    out->enforcement = -1;
    out->reason = "SELinux is not mounted at /sys/fs/selinux";

    if (read_first_line("/sys/fs/selinux/enforce", raw, sizeof(raw)) != 0) {
        return;
    }
    enforcement = parse_enforcement(raw);
    if (enforcement < 0) {
        out->reason = "the enforcement flag was not a recognised value";
        return;
    }
    out->available = 1;
    out->enforcement = enforcement;
    out->reason = NULL;
}

static void probe_kernel(kernel_t *out)
{
    out->available = 0;
    out->sysname[0] = '\0';
    out->release[0] = '\0';
    out->machine[0] = '\0';
    out->reason = "uname is only available on POSIX hosts";

#if DROIDCTL_HAS_UTSNAME
    {
        struct utsname info;
        if (uname(&info) == 0) {
            snprintf(out->sysname, sizeof(out->sysname), "%s", info.sysname);
            snprintf(out->release, sizeof(out->release), "%s", info.release);
            snprintf(out->machine, sizeof(out->machine), "%s", info.machine);
            out->available = 1;
            out->reason = NULL;
        } else {
            out->reason = "uname failed";
        }
    }
#endif
}

/* ------------------------------------------------------------------- output */

static void print_text(const kernel_t *kernel, const uptime_t *uptime,
                       const memory_t *memory, const selinux_t *selinux)
{
    printf("droidctl %s\n", DROIDCTL_VERSION);

    if (kernel->available) {
        printf("kernel    %s %s (%s)\n", kernel->sysname, kernel->release, kernel->machine);
    } else {
        printf("kernel    unavailable: %s\n", kernel->reason);
    }

    if (uptime->available) {
        printf("uptime    %.2f s\n", uptime->seconds);
    } else {
        printf("uptime    unavailable: %s\n", uptime->reason);
    }

    if (memory->available) {
        printf("memory    total %lu kB, free %lu kB, available %lu kB\n",
               memory->total_kb, memory->free_kb, memory->available_kb);
    } else {
        printf("memory    unavailable: %s\n", memory->reason);
    }

    if (selinux->available) {
        printf("selinux   %s\n", selinux->enforcement == 1 ? "enforcing" : "permissive");
    } else {
        printf("selinux   unavailable: %s\n", selinux->reason);
    }
}

static void print_json(const kernel_t *kernel, const uptime_t *uptime,
                       const memory_t *memory, const selinux_t *selinux)
{
    printf("{\"version\":\"%s\",", DROIDCTL_VERSION);

    if (kernel->available) {
        printf("\"kernel\":{\"available\":true,\"sysname\":\"%s\",\"release\":\"%s\",\"machine\":\"%s\"},",
               kernel->sysname, kernel->release, kernel->machine);
    } else {
        printf("\"kernel\":{\"available\":false,\"reason\":\"%s\"},", kernel->reason);
    }

    if (uptime->available) {
        printf("\"uptime\":{\"available\":true,\"seconds\":%.2f},", uptime->seconds);
    } else {
        printf("\"uptime\":{\"available\":false,\"reason\":\"%s\"},", uptime->reason);
    }

    if (memory->available) {
        printf("\"memory\":{\"available\":true,\"total_kb\":%lu,\"free_kb\":%lu,\"available_kb\":%lu},",
               memory->total_kb, memory->free_kb, memory->available_kb);
    } else {
        printf("\"memory\":{\"available\":false,\"reason\":\"%s\"},", memory->reason);
    }

    if (selinux->available) {
        printf("\"selinux\":{\"available\":true,\"enforcing\":%s}}\n",
               selinux->enforcement == 1 ? "true" : "false");
    } else {
        printf("\"selinux\":{\"available\":false,\"reason\":\"%s\"}}\n", selinux->reason);
    }
}

/* ---------------------------------------------------------------- selftest */

static int selftest(void)
{
    int failures = 0;
    char raw[32];

    if (parse_enforcement("1") != 1) {
        fprintf(stderr, "selftest: '1' should mean enforcing\n");
        failures++;
    }
    if (parse_enforcement("0") != 0) {
        fprintf(stderr, "selftest: '0' should mean permissive\n");
        failures++;
    }
    if (parse_enforcement("2") != -1 || parse_enforcement("") != -1 ||
        parse_enforcement(NULL) != -1) {
        fprintf(stderr, "selftest: unexpected enforcement values must map to unknown\n");
        failures++;
    }

    {
        double seconds = -1.0;
        if (parse_uptime("1234.56 987.65\n", &seconds) != 0 || seconds < 1234.55 ||
            seconds > 1234.57) {
            fprintf(stderr, "selftest: uptime parse failed\n");
            failures++;
        }
        if (parse_uptime("not-a-number", &seconds) != -1) {
            fprintf(stderr, "selftest: garbage uptime should fail\n");
            failures++;
        }
    }

    if (read_first_line("/nonexistent/droidos-selftest", raw, sizeof(raw)) >= 0) {
        fprintf(stderr, "selftest: reading a missing path should fail\n");
        failures++;
    }
    if (read_first_line("/proc/self/cmdline", raw, sizeof(raw)) >= 0) {
        /* On Linux this must succeed; on other hosts the probe is expected to fail. */
#if defined(__linux__)
        if (raw[0] == '\0') {
            fprintf(stderr, "selftest: cmdline came back empty\n");
            failures++;
        }
#endif
    }

    if (failures == 0) {
        printf("droidctl: selftest passed\n");
        return 0;
    }
    fprintf(stderr, "droidctl: %d selftest failure(s)\n", failures);
    return 1;
}

/* ------------------------------------------------------------------- entry */

static void usage(FILE *stream)
{
    fprintf(stream,
            "droidctl %s - DroidOS platform diagnostics\n"
            "\n"
            "usage:\n"
            "  droidctl all [--json]   print every probe\n"
            "  droidctl --selftest     validate the parsers, exit 1 on failure\n"
            "  droidctl --version      print the tool version\n",
            DROIDCTL_VERSION);
}

int main(int argc, char **argv)
{
    int json = 0;
    int i;
    const char *command = "all";
    kernel_t kernel;
    uptime_t uptime;
    memory_t memory;
    selinux_t selinux;

    for (i = 1; i < argc; i++) {
        if (strcmp(argv[i], "--json") == 0) {
            json = 1;
        } else if (strcmp(argv[i], "--selftest") == 0) {
            return selftest();
        } else if (strcmp(argv[i], "--version") == 0) {
            printf("%s\n", DROIDCTL_VERSION);
            return 0;
        } else if (strcmp(argv[i], "--help") == 0 || strcmp(argv[i], "-h") == 0) {
            usage(stdout);
            return 0;
        } else if (argv[i][0] == '-') {
            fprintf(stderr, "droidctl: unknown option %s\n", argv[i]);
            usage(stderr);
            return 2;
        } else {
            command = argv[i];
        }
    }

    if (strcmp(command, "all") != 0) {
        fprintf(stderr, "droidctl: unknown command %s\n", command);
        usage(stderr);
        return 2;
    }

    probe_kernel(&kernel);
    probe_uptime(&uptime);
    probe_memory(&memory);
    probe_selinux(&selinux);

    if (json) {
        print_json(&kernel, &uptime, &memory, &selinux);
    } else {
        print_text(&kernel, &uptime, &memory, &selinux);
    }
    return 0;
}
