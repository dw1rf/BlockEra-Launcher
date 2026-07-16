use serde::{Deserialize, Serialize};
use tauri::Runtime;
use tauri_plugin_opener::OpenerExt;
use theseus::{
    handler,
    prelude::{CommandPayload, DirectoryInfo},
};

use crate::api::{Result, TheseusSerializableError};
use dashmap::DashMap;
use std::collections::hash_map::DefaultHasher;
use std::hash::{Hash, Hasher};
use std::path::{Path, PathBuf};
use theseus::prelude::{State, canonicalize};
use theseus::util::utils;
use url::Url;

pub fn init<R: Runtime>() -> tauri::plugin::TauriPlugin<R> {
    tauri::plugin::Builder::new("utils")
        .invoke_handler(tauri::generate_handler![
            apply_migration_fix,
            init_update_launcher,
            get_os,
            is_network_metered,
            should_disable_mouseover,
            highlight_in_folder,
            open_path,
            open_profile_folder,
            show_launcher_logs_folder,
            progress_bars_list,
            get_opening_command,
            create_desktop_shortcut,
            save_custom_background
        ])
        .build()
}

#[tauri::command]
pub async fn save_custom_background(
    source_path: PathBuf,
    scope: &str,
) -> Result<PathBuf> {
    let extension = source_path
        .extension()
        .and_then(|value| value.to_str())
        .map(str::to_ascii_lowercase)
        .filter(|value| {
            matches!(value.as_str(), "png" | "jpg" | "jpeg" | "webp")
        })
        .ok_or_else(|| {
            std::io::Error::new(
                std::io::ErrorKind::InvalidInput,
                "Background must be a PNG, JPG, JPEG, or WebP image",
            )
        })?;

    let state = State::get().await?;
    let backgrounds_dir = state.directories.caches_dir().join("backgrounds");
    tokio::fs::create_dir_all(&backgrounds_dir).await?;

    let source = tokio::fs::read(source_path).await?;

    let mut scope_hasher = DefaultHasher::new();
    scope.hash(&mut scope_hasher);
    let scope_hash = format!("{:016x}", scope_hasher.finish());

    let mut content_hasher = DefaultHasher::new();
    source.hash(&mut content_hasher);
    let destination = backgrounds_dir.join(format!(
        "{scope_hash}-{:016x}.{extension}",
        content_hasher.finish()
    ));
    tokio::fs::write(&destination, source).await?;

    let mut entries = tokio::fs::read_dir(&backgrounds_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        let path = entry.path();
        if path == destination {
            continue;
        }

        let Some(file_name) = path.file_name().and_then(|value| value.to_str())
        else {
            continue;
        };
        if file_name.starts_with(&format!("{scope_hash}-"))
            || file_name.starts_with(&format!("{scope_hash}."))
        {
            if let Err(error) = tokio::fs::remove_file(&path).await {
                tracing::warn!(
                    "Failed to remove stale custom background {}: {}",
                    path.display(),
                    error
                );
            }
        }
    }

    Ok(destination)
}

#[tauri::command]
pub async fn create_desktop_shortcut(
    profile_path: &str,
    profile_name: &str,
) -> Result<PathBuf> {
    let desktop = dirs::desktop_dir().ok_or_else(|| {
        std::io::Error::new(
            std::io::ErrorKind::NotFound,
            "Desktop directory is not available",
        )
    })?;
    let safe_name: String = profile_name
        .chars()
        .map(|character| match character {
            '<' | '>' | ':' | '"' | '/' | '\\' | '|' | '?' | '*' => '_',
            _ => character,
        })
        .collect();
    let shortcut_path = desktop.join(format!("{}.url", safe_name.trim()));
    let executable = std::env::current_exe()?;
    let contents = format!(
        "[InternetShortcut]\r\nURL=modrinth://profile/{}\r\nIconFile={}\r\nIconIndex=0\r\n",
        urlencoding::encode(profile_path),
        executable.display()
    );

    tokio::fs::write(&shortcut_path, contents).await?;
    Ok(shortcut_path)
}

#[derive(Debug, Clone, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ProfileFolder {
    Root,
    Mods,
    ResourcePacks,
    ShaderPacks,
    Saves,
    Backups,
}

// This code is modified by AstralRinth
#[tauri::command]
pub async fn apply_migration_fix(eol: &str) -> Result<bool> {
    let result = utils::apply_migration_fix(eol).await?;
    Ok(result)
}

// This code is modified by AstralRinth
#[tauri::command]
pub async fn init_update_launcher(
    download_url: &str,
    filename: &str,
    os_type: &str,
    auto_update_supported: bool,
) -> Result<()> {
    let _ = utils::init_update_launcher(
        download_url,
        filename,
        os_type,
        auto_update_supported,
    )
    .await;
    Ok(())
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[allow(clippy::enum_variant_names)]
pub enum OS {
    Windows,
    Linux,
    MacOS,
}

/// Gets OS
#[tauri::command]
pub fn get_os() -> OS {
    #[cfg(target_os = "windows")]
    let os = OS::Windows;
    #[cfg(target_os = "linux")]
    let os = OS::Linux;
    #[cfg(target_os = "macos")]
    let os = OS::MacOS;
    os
}

#[tauri::command]
pub async fn is_network_metered() -> Result<bool> {
    Ok(theseus::prelude::is_network_metered().await?)
}

// Lists active progress bars
// Create a new HashMap with the same keys
// Values provided should not be used directly, as they are not guaranteed to be up-to-date
#[tauri::command]
pub async fn progress_bars_list()
-> Result<DashMap<uuid::Uuid, theseus::LoadingBar>> {
    let res = theseus::EventState::list_progress_bars().await?;
    Ok(res)
}

// disables mouseover and fixes a random crash error only fixed by recent versions of macos
#[tauri::command]
pub async fn should_disable_mouseover() -> bool {
    if cfg!(target_os = "macos") {
        // We try to match version to 12.2 or higher. If unrecognizable to pattern or lower, we default to the css with disabled mouseover for safety
        if let tauri_plugin_os::Version::Semantic(major, minor, _) =
            tauri_plugin_os::version()
            && major >= 12
            && minor >= 3
        {
            // Mac os version is 12.3 or higher, we allow mouseover
            return false;
        }
        true
    } else {
        // Not macos, we allow mouseover
        false
    }
}

#[tauri::command]
pub fn highlight_in_folder<R: Runtime>(
    app: tauri::AppHandle<R>,
    path: PathBuf,
) {
    if let Err(e) = app.opener().reveal_item_in_dir(path) {
        tracing::error!("Failed to highlight file in folder: {}", e);
    }
}

#[tauri::command]
pub async fn open_path<R: Runtime>(app: tauri::AppHandle<R>, path: PathBuf) {
    tauri::async_runtime::spawn_blocking(move || {
        if let Err(e) =
            app.opener().open_path(path.to_string_lossy(), None::<&str>)
        {
            tracing::error!("Failed to open path: {}", e);
        }
    })
    .await
    .ok();
}

#[tauri::command]
pub async fn open_profile_folder<R: Runtime>(
    app: tauri::AppHandle<R>,
    profile_path: &str,
    folder: ProfileFolder,
) -> Result<()> {
    let mut path = theseus::profile::get_full_path(profile_path).await?;
    match folder {
        ProfileFolder::Root => {}
        ProfileFolder::Mods => path.push("mods"),
        ProfileFolder::ResourcePacks => path.push("resourcepacks"),
        ProfileFolder::ShaderPacks => path.push("shaderpacks"),
        ProfileFolder::Saves => path.push("saves"),
        ProfileFolder::Backups => path.push("backups"),
    }

    tokio::fs::create_dir_all(&path).await?;
    open_path(app, path).await;
    Ok(())
}

#[tauri::command]
pub async fn show_launcher_logs_folder<R: Runtime>(app: tauri::AppHandle<R>) {
    let path = DirectoryInfo::launcher_logs_dir().unwrap_or_default();
    // failure to get folder just opens filesystem
    // (ie: if in debug mode only and launcher_logs never created)
    open_path(app, path).await;
}

// Get opening command
// For example, if a user clicks on an .mrpack to open the app.
// This should be called once and only when the app is done booting up and ready to receive a command
// Returns a Command struct- see events.js
#[tauri::command]
#[cfg(target_os = "macos")]
pub async fn get_opening_command(
    state: tauri::State<'_, crate::macos::deep_link::InitialPayload>,
) -> Result<Option<CommandPayload>> {
    let payload = state.payload.lock().await;

    return if let Some(payload) = payload.as_ref() {
        tracing::info!("opening command {payload}");

        Ok(Some(handler::parse_command(payload).await?))
    } else {
        Ok(None)
    };
}

#[tauri::command]
#[cfg(not(target_os = "macos"))]
pub async fn get_opening_command() -> Result<Option<CommandPayload>> {
    // Tauri is not CLI, we use arguments as path to file to call
    let cmd_arg = std::env::args_os().nth(1);

    tracing::info!("opening command {cmd_arg:?}");

    let cmd_arg = cmd_arg.map(|path| path.to_string_lossy().to_string());
    if let Some(cmd) = cmd_arg {
        tracing::debug!("Opening command: {:?}", cmd);
        return Ok(Some(handler::parse_command(&cmd).await?));
    }
    Ok(None)
}

// helper function called when redirected by a weblink (ie: modrith://do-something) or when redirected by a .mrpack file (in which case its a filepath)
// We hijack the deep link library (which also contains functionality for instance-checking)
pub async fn handle_command(command: String) -> Result<()> {
    tracing::info!("handle command: {command}");
    Ok(theseus::handler::parse_and_emit_command(&command).await?)
}

// Remove when (and if) https://github.com/tauri-apps/tauri/issues/12022 is implemented
pub(crate) fn tauri_convert_file_src(path: &Path) -> Result<Url> {
    #[cfg(any(windows, target_os = "android"))]
    const BASE: &str = "http://asset.localhost/";
    #[cfg(not(any(windows, target_os = "android")))]
    const BASE: &str = "asset://localhost/";

    macro_rules! theseus_try {
        ($test:expr) => {
            match $test {
                Ok(val) => val,
                Err(e) => {
                    return Err(TheseusSerializableError::Theseus(e.into()))
                }
            }
        };
    }

    let path = theseus_try!(canonicalize(path));
    let path = path.to_string_lossy();
    let encoded = urlencoding::encode(&path);

    Ok(theseus_try!(Url::parse(&format!("{BASE}{encoded}"))))
}
