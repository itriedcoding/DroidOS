// Sorted, duplicate-free blocklist store for the DroidOS egress filter.
//
// Mirrors the constraints in platform/sql/schema.sql and DROID_UID_MAX in netfilter_rules.h, so a
// UID that the database or the rule renderer would refuse is refused here first.

#ifndef DROIDOS_BLOCKLIST_STORE_HPP
#define DROIDOS_BLOCKLIST_STORE_HPP

#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <vector>

namespace droidos {

constexpr std::int32_t kMaxAppUid = 99999;

struct BlockEntry {
    std::int32_t uid{0};
    std::string package_name;
    std::string reason;
};

enum class BlockStatus {
    Added,
    DuplicateUid,
    InvalidUid,
    EmptyPackage,
};

class BlocklistStore {
  public:
    // Adds an entry. Rejects duplicates, UIDs outside the application range and empty package
    // names; the store is never left half-updated on rejection.
    BlockStatus add(std::int32_t uid, const std::string &package_name, const std::string &reason = {});

    // Returns true when the UID was present and has been removed.
    bool remove(std::int32_t uid);

    std::optional<BlockEntry> find(std::int32_t uid) const;

    std::size_t size() const noexcept { return entries_.size(); }

    // UIDs in ascending order, which is the order the kernel rules should be installed in.
    std::vector<std::int32_t> uids() const;

    static bool is_valid_uid(std::int32_t uid) noexcept;

  private:
    std::vector<BlockEntry> entries_;
};

}  // namespace droidos

#endif  // DROIDOS_BLOCKLIST_STORE_HPP
