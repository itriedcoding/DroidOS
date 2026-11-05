/*
 * Tests for the netfilter rule compiler. Deliberately assertion-free so the checks survive
 * -DNDEBUG builds: every failure is counted and reported, and the binary exits non-zero.
 */

#include "netfilter_rules.h"

#include <stdio.h>
#include <string.h>

static int failures;
static int checks;

static void check(int condition, const char *expression, int line)
{
    checks++;
    if (!condition) {
        failures++;
        fprintf(stderr, "FAIL %s:%d  %s\n", __FILE__, line, expression);
    }
}

#define CHECK(expression) check((expression) ? 1 : 0, #expression, __LINE__)

int main(void)
{
    char buffer[64];

    /* Happy path: the rule must match the documented nftables syntax exactly. */
    CHECK(droid_nft_rule(10123, buffer, sizeof(buffer)) == (int)strlen("meta skuid 10123 drop"));
    CHECK(strcmp(buffer, "meta skuid 10123 drop") == 0);

    CHECK(droid_iptables_rule(10042, buffer, sizeof(buffer)) > 0);
    CHECK(strcmp(buffer, "-m owner --uid-owner 10042 -j DROP") == 0);

    /* Boundary UIDs are blockable; anything outside the application range is not. */
    CHECK(droid_uid_valid(0) == 1);
    CHECK(droid_uid_valid(DROID_UID_MAX) == 1);
    CHECK(droid_uid_valid(-1) == 0);
    CHECK(droid_uid_valid(DROID_UID_MAX + 1) == 0);

    /* Invalid UIDs must be refused and must not leave a partial rule behind. */
    strcpy(buffer, "stale");
    CHECK(droid_nft_rule(-1, buffer, sizeof(buffer)) == -1);
    CHECK(buffer[0] == '\0');
    strcpy(buffer, "stale");
    CHECK(droid_iptables_rule(DROID_UID_MAX + 1, buffer, sizeof(buffer)) == -1);
    CHECK(buffer[0] == '\0');

    /* A buffer that cannot hold the whole rule must fail rather than truncate it. */
    strcpy(buffer, "stale");
    CHECK(droid_nft_rule(1000, buffer, 8) == -1);
    CHECK(buffer[0] == '\0');

    /* Defensive cases: null and zero-length buffers are rejected without crashing. */
    CHECK(droid_nft_rule(1000, NULL, 0) == -1);
    CHECK(droid_nft_rule(1000, buffer, 0) == -1);

    printf("netfilter_rules: %d checks, %d failure(s)\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
