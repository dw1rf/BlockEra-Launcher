use crate::state::DirectoryInfo;
use sqlx::sqlite::{
    SqliteConnectOptions, SqliteJournalMode, SqlitePoolOptions,
};
use sqlx::{Pool, Sqlite};
use tokio::time::Instant;
use std::str::FromStr;
use std::time::Duration;

pub(crate) async fn connect() -> crate::Result<Pool<Sqlite>> {
    let settings_dir = DirectoryInfo::get_initial_settings_dir().ok_or(
        crate::ErrorKind::FSError(
            "Could not find valid config dir".to_string(),
        ),
    )?;

    if !settings_dir.exists() {
        crate::util::io::create_dir_all(&settings_dir).await?;
    }

    let db_path = settings_dir.join("app.db");
    let db_exists = db_path.exists();

    let uri = format!("sqlite:{}", db_path.display());
    let conn_options = SqliteConnectOptions::from_str(&uri)?
        .busy_timeout(Duration::from_secs(30))
        .journal_mode(SqliteJournalMode::Wal)
        .optimize_on_close(true, None)
        .create_if_missing(true);

    let pool = SqlitePoolOptions::new()
        .max_connections(100)
        .connect_with(conn_options)
        .await?;

    if db_exists {
        fix_modrinth_issued_migrations(&pool).await?;
    }

    sqlx::migrate!().run(&pool).await?;

    if !db_exists {
        fix_modrinth_issued_migrations(&pool).await?;
    }

    if let Err(err) = stale_data_cleanup(&pool).await {
        tracing::warn!(
            "Failed to clean up stale data from state database: {err}"
        );
    }

    Ok(pool)
}

/// Cleans up data from the database that is no longer referenced, but must be
/// kept around for a little while to allow users to recover from accidental
/// deletions.
async fn stale_data_cleanup(pool: &Pool<Sqlite>) -> crate::Result<()> {
    let mut tx = pool.begin().await?;

    sqlx::query!(
        "DELETE FROM default_minecraft_capes WHERE minecraft_user_uuid NOT IN (SELECT uuid FROM minecraft_users)"
    )
    .execute(&mut *tx)
    .await?;
    sqlx::query!(
        "DELETE FROM custom_minecraft_skins WHERE minecraft_user_uuid NOT IN (SELECT uuid FROM minecraft_users)"
    )
    .execute(&mut *tx)
    .await?;

    tx.commit().await?;

    Ok(())
}
/*
// Patch by AstralRinth - 08.07.2025
Problem files:
/packages/app-lib/migrations/20240711194701_init.sql !eol
/packages/app-lib/migrations/20240813205023_drop-active-unique.sql !eol
/packages/app-lib/migrations/20240930001852_disable-personalized-ads.sql !eol
/packages/app-lib/migrations/20241222013857_feature-flags.sql !eol
*/
async fn fix_modrinth_issued_migrations(
    pool: &Pool<Sqlite>,
) -> crate::Result<()> {
    let started = Instant::now();
    tracing::info!("Fixing modrinth issued migrations");
    sqlx::query(
            r#"
            UPDATE "_sqlx_migrations"
            SET checksum = X'e973512979feac07e415405291eefafc1ef0bd89454958ad66f5452c381db8679c20ffadab55194ecf6ba8ec4ca2db21'
            WHERE version = '20240711194701';
            "#,
        )
        .execute(pool)
        .await?;
    tracing::info!("⚙️ Fixed first migration");
    sqlx::query(
            r#"
            UPDATE "_sqlx_migrations"
            SET checksum = X'5b53534a7ffd74eebede234222be47e1d37bd0cc5fee4475212491b0c0379c16e3079e08eee0af959b1fa20835eeb206'
            WHERE version = '20240813205023';
            "#,
        )
        .execute(pool)
        .await?;
    tracing::info!("⚙️ Fixed second migration");
    sqlx::query(
            r#"
            UPDATE "_sqlx_migrations"
            SET checksum = X'c0de804f171b5530010edae087a6e75645c0e90177e28365f935c9fdd9a5c68e24850b8c1498e386a379d525d520bc57'
            WHERE version = '20240930001852';
            "#,
        )
        .execute(pool)
        .await?;
    tracing::info!("⚙️ Fixed third migration");
    sqlx::query(
            r#"
            UPDATE "_sqlx_migrations"
            SET checksum = X'c17542cb989a0466153e695bfa4717f8970feee185ca186a2caa1f2f6c5d4adb990ab97c26cacfbbe09c39ac81551704'
            WHERE version = '20241222013857';
            "#,
        )
        .execute(pool)
        .await?;
    tracing::info!("⚙️ Fixed fourth migration");
    let elapsed = started.elapsed();
    tracing::info!(
        "✅ Fixed all known modrinth-issued migrations in {:.2?}",
        elapsed
    );
    Ok(())
}
