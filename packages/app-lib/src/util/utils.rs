///
/// [AR] Feature
///
use crate::Result;
use crate::api::update;
use crate::state::db;
use serde::{Deserialize, Serialize};
use std::process;
use tokio::io;

const PACKAGE_JSON_CONTENT: &str =
    // include_str!("../../../../apps/app-frontend/package.json");
    include_str!("../../../../apps/app/tauri.conf.json");

#[derive(Serialize, Deserialize)]
pub struct Launcher {
    pub version: String,
}

pub fn read_package_json() -> io::Result<Launcher> {
    // Deserialize the content of package.json into a Launcher struct
    let launcher: Launcher = serde_json::from_str(PACKAGE_JSON_CONTENT)?;

    Ok(launcher)
}

pub async fn apply_migration_fix(eol: &str) -> Result<bool> {
    tracing::info!("[AR] • Attempting to apply migration fix");
    let patched = db::apply_migration_fix(eol).await?;
    if patched {
        tracing::info!("[AR] • Successfully applied migration fix");
    } else {
        tracing::error!("[AR] • Failed to apply migration fix");
    }
    Ok(patched)
}

pub async fn init_download(
    download_url: &str,
    local_filename: &str,
    os_type: &str,
    auto_update_supported: bool,
) -> Result<()> {
    println!("[AR] • Initialize downloading from • {:?}", download_url);
    println!("[AR] • Save local file name • {:?}", local_filename);
    if let Err(e) = update::get_resource(
        download_url,
        local_filename,
        os_type,
        auto_update_supported,
    )
    .await
    {
        eprintln!(
            "[AR] • An error occurred! Failed to download the file: {}",
            e
        );
    } else {
        println!("[AR] • Code finishes without errors.");
        process::exit(0)
    }
    Ok(())
}
