#include "blocklist_store.hpp"

#include <algorithm>
#include <utility>

namespace droidos {
namespace {

// The store keeps entries sorted by UID, so every lookup is a binary search and the kernel rules
// are installed in a deterministic order.
std::vector<BlockEntry>::iterator lower_bound_for(std::vector<BlockEntry> &entries, std::int32_t uid)
{
    return std::lower_bound(entries.begin(), entries.end(), uid,
                            [](const BlockEntry &entry, std::int32_t value) {
                                return entry.uid < value;
                            });
}

std::vector<BlockEntry>::const_iterator lower_bound_for(const std::vector<BlockEntry> &entries,
                                                        std::int32_t uid)
{
    return std::lower_bound(entries.begin(), entries.end(), uid,
                            [](const BlockEntry &entry, std::int32_t value) {
                                return entry.uid < value;
                            });
}

}  // namespace

bool BlocklistStore::is_valid_uid(std::int32_t uid) noexcept
{
    return uid >= 0 && uid <= kMaxAppUid;
}

BlockStatus BlocklistStore::add(std::int32_t uid, const std::string &package_name,
                                const std::string &reason)
{
    if (!is_valid_uid(uid)) {
        return BlockStatus::InvalidUid;
    }
    if (package_name.empty()) {
        return BlockStatus::EmptyPackage;
    }

    auto position = lower_bound_for(entries_, uid);
    if (position != entries_.end() && position->uid == uid) {
        return BlockStatus::DuplicateUid;
    }

    entries_.insert(position, BlockEntry{uid, package_name, reason});
    return BlockStatus::Added;
}

bool BlocklistStore::remove(std::int32_t uid)
{
    auto position = lower_bound_for(entries_, uid);
    if (position == entries_.end() || position->uid != uid) {
        return false;
    }
    entries_.erase(position);
    return true;
}

std::optional<BlockEntry> BlocklistStore::find(std::int32_t uid) const
{
    auto position = lower_bound_for(entries_, uid);
    if (position == entries_.end() || position->uid != uid) {
        return std::nullopt;
    }
    return *position;
}

std::vector<std::int32_t> BlocklistStore::uids() const
{
    std::vector<std::int32_t> result;
    result.reserve(entries_.size());
    for (const auto &entry : entries_) {
        result.push_back(entry.uid);
    }
    return result;
}

}  // namespace droidos
