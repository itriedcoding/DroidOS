-- DroidOS platform database schema.
--
-- Used by the platform service in a device image and, unchanged, by the shell's own database so
-- the two never drift. Applied with:
--   sqlite3 droidos.db < platform/sql/schema.sql
-- scripts/build-platform.py applies it to an in-memory database and asserts the constraints,
-- indexes and idempotency on every build.

CREATE TABLE IF NOT EXISTS schema_version (
    version    INTEGER NOT NULL PRIMARY KEY,
    applied_at INTEGER NOT NULL
);

-- Applications whose egress the kernel filter drops. The CHECK mirrors DROID_UID_MAX in
-- platform/native/netfilter_rules.h: a UID outside the application range is never a valid target.
CREATE TABLE IF NOT EXISTS uid_blocklist (
    uid          INTEGER NOT NULL PRIMARY KEY
                 CHECK (uid >= 0 AND uid <= 99999),
    package_name TEXT    NOT NULL CHECK (length(package_name) > 0),
    reason       TEXT,
    created_at   INTEGER NOT NULL DEFAULT (strftime('%s', 'now'))
);

-- Camera, microphone and location activation history shown in the Privacy panel.
CREATE TABLE IF NOT EXISTS privacy_events (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    sensor       TEXT    NOT NULL CHECK (sensor IN ('camera', 'microphone', 'location')),
    package_name TEXT    NOT NULL CHECK (length(package_name) > 0),
    started_at   INTEGER NOT NULL,
    duration_ms  INTEGER NOT NULL DEFAULT 0 CHECK (duration_ms >= 0)
);

CREATE INDEX IF NOT EXISTS privacy_events_by_sensor_time
    ON privacy_events (sensor, started_at DESC);
CREATE INDEX IF NOT EXISTS privacy_events_by_package
    ON privacy_events (package_name, started_at DESC);

-- Applied updates, keyed by version code so a downgrade cannot be recorded twice.
CREATE TABLE IF NOT EXISTS update_history (
    version_code INTEGER NOT NULL PRIMARY KEY CHECK (version_code > 0),
    version_name TEXT    NOT NULL,
    sha256       TEXT    NOT NULL CHECK (length(sha256) = 64),
    installed_at INTEGER NOT NULL
);

INSERT OR IGNORE INTO schema_version (version, applied_at)
    VALUES (1, strftime('%s', 'now'));
