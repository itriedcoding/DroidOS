// Tests for the blocklist store. Failures are counted and reported rather than asserted, so the
// checks survive a -DNDEBUG build and the binary exits non-zero when anything is wrong.

#include "blocklist_store.hpp"

#include <cstdio>
#include <string>
#include <vector>

namespace {

int failures = 0;
int checks = 0;

void check(bool condition, const char *expression, int line)
{
    ++checks;
    if (!condition) {
        ++failures;
        std::fprintf(stderr, "FAIL %s:%d  %s\n", __FILE__, line, expression);
    }
}

#define CHECK(expression) check((expression), #expression, __LINE__)

}  // namespace

int main()
{
    using droidos::BlockStatus;
    using droidos::BlocklistStore;

    BlocklistStore store;

    CHECK(store.size() == 0);
    CHECK(BlocklistStore::is_valid_uid(0));
    CHECK(BlocklistStore::is_valid_uid(droidos::kMaxAppUid));
    CHECK(!BlocklistStore::is_valid_uid(-1));
    CHECK(!BlocklistStore::is_valid_uid(droidos::kMaxAppUid + 1));

    // Rejections must not mutate the store.
    CHECK(store.add(-1, "dev.example.app") == BlockStatus::InvalidUid);
    CHECK(store.add(droidos::kMaxAppUid + 1, "dev.example.app") == BlockStatus::InvalidUid);
    CHECK(store.add(10123, "") == BlockStatus::EmptyPackage);
    CHECK(store.size() == 0);

    // Entries are kept in ascending UID order regardless of insertion order.
    CHECK(store.add(10200, "dev.example.third") == BlockStatus::Added);
    CHECK(store.add(10100, "dev.example.first") == BlockStatus::Added);
    CHECK(store.add(10150, "dev.example.second", "user blocked") == BlockStatus::Added);
    CHECK(store.size() == 3);
    CHECK((store.uids() == std::vector<std::int32_t>{10100, 10150, 10200}));

    // A duplicate UID is refused and the original entry keeps its data.
    CHECK(store.add(10100, "dev.example.impostor") == BlockStatus::DuplicateUid);
    CHECK(store.size() == 3);
    CHECK(store.find(10100).has_value());
    CHECK(store.find(10100)->package_name == "dev.example.first");

    CHECK(store.find(10150).has_value());
    CHECK(store.find(10150)->reason == "user blocked");
    CHECK(!store.find(99999).has_value());

    CHECK(store.remove(10150));
    CHECK(!store.remove(10150));
    CHECK(store.size() == 2);
    CHECK((store.uids() == std::vector<std::int32_t>{10100, 10200}));

    std::printf("blocklist_store: %d checks, %d failure(s)\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
