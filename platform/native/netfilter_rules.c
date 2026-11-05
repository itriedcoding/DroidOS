#include "netfilter_rules.h"

#include <stdio.h>
#include <string.h>

int droid_uid_valid(int uid)
{
    return uid >= 0 && uid <= DROID_UID_MAX;
}

/* Shared rendering path: snprintf reports the length it wanted, so a truncated rule is
 * detected and reported as a failure instead of being returned half-formed. */
static int render(char *out, size_t out_len, const char *format, int uid)
{
    int written;

    if (out == NULL || out_len == 0) {
        return -1;
    }
    out[0] = '\0';
    written = snprintf(out, out_len, format, uid);
    if (written < 0) {
        out[0] = '\0';
        return -1;
    }
    if ((size_t)written >= out_len) {
        out[0] = '\0';
        return -1;
    }
    return written;
}

int droid_nft_rule(int uid, char *out, size_t out_len)
{
    if (!droid_uid_valid(uid)) {
        if (out != NULL && out_len > 0) {
            out[0] = '\0';
        }
        return -1;
    }
    return render(out, out_len, "meta skuid %d drop", uid);
}

int droid_iptables_rule(int uid, char *out, size_t out_len)
{
    if (!droid_uid_valid(uid)) {
        if (out != NULL && out_len > 0) {
            out[0] = '\0';
        }
        return -1;
    }
    return render(out, out_len, "-m owner --uid-owner %d -j DROP", uid);
}
