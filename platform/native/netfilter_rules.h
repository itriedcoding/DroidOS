#ifndef DROIDOS_NETFILTER_RULES_H
#define DROIDOS_NETFILTER_RULES_H

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Highest UID Android assigns to an application. UIDs above this are system or isolated
 * processes and must never be rendered into a blocking rule by mistake.
 */
#define DROID_UID_MAX 99999

/* Returns 1 when the UID is one the platform may block, 0 otherwise. */
int droid_uid_valid(int uid);

/*
 * Renders an nftables rule dropping every packet originating from one UID, for example
 *   meta skuid 10123 drop
 * Returns the number of characters written, or -1 when the UID is out of range or the buffer
 * cannot hold the complete rule (a truncated firewall rule is never emitted).
 */
int droid_nft_rule(int uid, char *out, size_t out_len);

/*
 * Renders the equivalent iptables owner match:
 *   -m owner --uid-owner 10123 -j DROP
 * Same return contract as droid_nft_rule.
 */
int droid_iptables_rule(int uid, char *out, size_t out_len);

#ifdef __cplusplus
}
#endif

#endif /* DROIDOS_NETFILTER_RULES_H */
