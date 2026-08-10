CREATE TABLE world_backup_settings (
	id INTEGER PRIMARY KEY CHECK (id = 0),
	enabled INTEGER NOT NULL DEFAULT 0,
	interval_minutes INTEGER NOT NULL DEFAULT 60 CHECK (interval_minutes >= 5),
	retention_per_world INTEGER NOT NULL DEFAULT 5 CHECK (retention_per_world BETWEEN 1 AND 100)
);

INSERT INTO world_backup_settings (id, enabled, interval_minutes, retention_per_world)
VALUES (0, 0, 60, 5);
