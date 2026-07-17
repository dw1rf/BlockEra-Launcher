use crate::api::Result;

use theseus::{
    pack::{
        install_from::{CreatePackLocation, CreatePackProfile},
        install_mrpack::install_zipped_mrpack,
    },
    prelude::*,
};

pub fn init<R: tauri::Runtime>() -> tauri::plugin::TauriPlugin<R> {
    tauri::plugin::Builder::new("pack")
        .invoke_handler(tauri::generate_handler![
            pack_install,
            pack_get_profile_from_pack,
            pack_get_catalog,
            pack_install_catalog,
            pack_cancel_catalog_install,
            pack_catalog_install_status,
            pack_verify_catalog_install,
        ])
        .build()
}

#[tauri::command]
pub async fn pack_get_catalog() -> Result<pack::catalog::CatalogResponse> {
    Ok(pack::catalog::get_catalog().await?)
}

#[tauri::command]
pub async fn pack_install_catalog(pack_id: &str) -> Result<String> {
    Ok(pack::catalog_install::start_catalog_install(pack_id).await?)
}

#[tauri::command]
pub fn pack_cancel_catalog_install(job_id: &str) -> Result<()> {
    Ok(pack::catalog_install::cancel_catalog_install(job_id)?)
}

#[tauri::command]
pub fn pack_catalog_install_status(
    job_id: &str,
) -> Result<pack::catalog_install::CatalogInstallStatus> {
    Ok(pack::catalog_install::catalog_install_status(job_id)?)
}

#[tauri::command]
pub async fn pack_verify_catalog_install(profile_path: &str) -> Result<bool> {
    Ok(
        pack::catalog_install::verify_installed_catalog_pack(profile_path)
            .await?,
    )
}

#[tauri::command]
pub async fn pack_install(
    location: CreatePackLocation,
    profile: String,
) -> Result<String> {
    Ok(install_zipped_mrpack(location, profile).await?)
}

#[tauri::command]
pub fn pack_get_profile_from_pack(
    location: CreatePackLocation,
) -> Result<CreatePackProfile> {
    Ok(pack::install_from::get_profile_from_pack(location))
}
