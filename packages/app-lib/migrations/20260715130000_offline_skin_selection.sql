CREATE TABLE offline_minecraft_skin_selections (
    minecraft_user_uuid TEXT NOT NULL PRIMARY KEY,
    texture_key TEXT,
    variant TEXT CHECK (variant IS NULL OR variant IN ('CLASSIC', 'SLIM', 'UNKNOWN'))
);

CREATE TRIGGER offline_minecraft_skin_selection_user_uuid_update_cascade
    AFTER UPDATE OF uuid ON minecraft_users FOR EACH ROW
    BEGIN
        UPDATE offline_minecraft_skin_selections
        SET minecraft_user_uuid = NEW.uuid
        WHERE minecraft_user_uuid = OLD.uuid;
    END;
