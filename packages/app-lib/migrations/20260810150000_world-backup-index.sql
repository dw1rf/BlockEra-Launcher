CREATE TABLE world_backups (
    profile_path TEXT NOT NULL,
    id TEXT NOT NULL,
    world TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    size INTEGER NOT NULL,
    reason TEXT NOT NULL,
    format_version INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (profile_path, id)
);

CREATE INDEX world_backups_profile_world_created
    ON world_backups (profile_path, world, created_at DESC);
