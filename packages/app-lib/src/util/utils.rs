use crate::api::update;
use crate::state::db;
///
/// [AR] Feature Utils
///
use crate::{Result, State};
use serde::{Deserialize, Serialize};
use std::process;
use tokio::{fs, io};

const PACKAGE_JSON_CONTENT: &str =
    // include_str!("../../../../apps/app-frontend/package.json");
    include_str!("../../../../apps/app/tauri.conf.json");

#[derive(Serialize, Deserialize)]
pub struct Launcher {
    pub version: String,
}

#[derive(Debug, Deserialize)]
struct Artifact {
    path: Option<String>,
    sha1: Option<String>,
    url: Option<String>,
}

#[derive(Debug, Deserialize)]
struct Downloads {
    artifact: Option<Artifact>,
}

#[derive(Debug, Deserialize)]
struct Library {
    name: String,
    downloads: Option<Downloads>,
}

#[derive(Debug, Deserialize)]
struct VersionJson {
    libraries: Vec<Library>,
}

/// Deserialize the content of package.json into a Launcher struct
pub fn read_package_json() -> io::Result<Launcher> {
    let launcher: Launcher = serde_json::from_str(PACKAGE_JSON_CONTENT)?;
    Ok(launcher)
}

/// ### AR • Universal Write (IO) Function
/// Saves the downloaded bytes to the `libraries` directory using the given relative path.
async fn write_file_to_libraries(
    relative_path: &str,
    bytes: &bytes::Bytes,
) -> Result<()> {
    let state = State::get().await?;
    let output_path = state.directories.libraries_dir().join(relative_path);

    fs::write(&output_path, bytes).await.map_err(|e| {
        tracing::error!("[AR] • Failed to save file: {:?}", e);
        crate::ErrorKind::IOErrorOccurred {
            error: format!("Failed to save file: {e}"),
        }
        .as_error()
    })
}

/// ### AR • AuthLib (Ely By)
/// Initializes the AuthLib patching process.
///
/// Returns `true` if the authlib patched successfully.
pub async fn init_authlib_patching(
    minecraft_version: &str,
    is_mojang: bool,
) -> Result<bool> {
    let minecraft_library_metadata = get_minecraft_library_metadata(minecraft_version).await?;
    // Parses the AuthLib version from string
    // Example output: "com.mojang:authlib:6.0.58" -> "6.0.58"
    let authlib_version = minecraft_library_metadata.name.split(':').nth(2).unwrap_or("unknown");
    
    tracing::info!(
        "[AR] • Attempting to download AuthLib {}.",
        authlib_version
    );

    download_authlib(
        &minecraft_library_metadata,
        authlib_version,
        minecraft_version,
        is_mojang,
    )
    .await
}

/// ### AR • AuthLib (Ely By)
/// Downloads the AuthLib file from Mojang libraries or Git Astralium services.
async fn download_authlib(
    minecraft_library_metadata: &Library,
    authlib_version: &str,
    minecraft_version: &str,
    is_mojang: bool,
) -> Result<bool> {
    let state = State::get().await?;
    let (url, path) = extract_download_info(minecraft_library_metadata, minecraft_version)?;
    let mut download_url = url.to_string();
    let full_path = state.directories.libraries_dir().join(path);

    if !is_mojang {
        tracing::info!(
            "[AR] • Attempting to download AuthLib from Git Astralium"
        );
        download_url = extract_ely_authlib_url(authlib_version).await?;
    }
    tracing::info!("[AR] • Downloading AuthLib from URL: {}", download_url);
    let bytes = fetch_bytes_from_url(&download_url).await?;
    tracing::info!("[AR] • Will save to path: {}", full_path.to_str().unwrap());
    write_file_to_libraries(full_path.to_str().unwrap(), &bytes).await?;
    tracing::info!("[AR] • Successfully saved AuthLib to {:?}", full_path);
    Ok(true)
}

/// ### AR • AuthLib (Ely By)
/// Parses the ElyIntegration release JSON and returns the download URL for the given AuthLib version.
async fn extract_ely_authlib_url(authlib_version: &str) -> Result<String> {
    let url = "https://git.astralium.su/api/v1/repos/didirus/ElyIntegration/releases/latest";

    let response = reqwest::get(url).await.map_err(|e| {
        tracing::error!(
            "[AR] • Failed to fetch ElyIntegration release JSON: {:?}",
            e
        );
        crate::ErrorKind::NetworkErrorOccurred {
            error: format!("Failed to fetch ElyIntegration release JSON: {}", e),
        }
        .as_error()
    })?;

    let json: serde_json::Value = response.json().await.map_err(|e| {
        tracing::error!("[AR] • Failed to parse ElyIntegration JSON: {:?}", e);
        crate::ErrorKind::ParseError {
            reason: format!("Failed to parse ElyIntegration JSON: {}", e),
        }
        .as_error()
    })?;

    let assets =
        json.get("assets")
            .and_then(|v| v.as_array())
            .ok_or_else(|| {
                crate::ErrorKind::ParseError {
                    reason: "Missing 'assets' array".into(),
                }
                .as_error()
            })?;

    let asset = assets
        .iter()
        .find(|a| {
            a.get("name")
                .and_then(|n| n.as_str())
                .map(|n| n.contains(authlib_version))
                .unwrap_or(false)
        })
        .ok_or_else(|| {
            crate::ErrorKind::ParseError {
                reason: format!(
                    "No matching asset for authlib-{}.jar",
                    authlib_version
                ),
            }
            .as_error()
        })?;

    let download_url = asset
        .get("browser_download_url")
        .and_then(|u| u.as_str())
        .ok_or_else(|| {
            crate::ErrorKind::ParseError {
                reason: "Missing 'browser_download_url'".into(),
            }
            .as_error()
        })?;

    Ok(download_url.to_string())
}

/// ### AR • AuthLib (Ely By)
/// Extracts the artifact URL and Path from the library structure.
///
/// Returns a tuple of references to the URL and path strings,
/// or an error if the required metadata is missing.
fn extract_download_info<'a>(
    minecraft_library_metadata: &'a Library,
    minecraft_version: &str,
) -> Result<(&'a str, &'a str)> {
    let artifact = minecraft_library_metadata
        .downloads
        .as_ref()
        .and_then(|d| d.artifact.as_ref())
        .ok_or_else(|| {
            crate::ErrorKind::MinecraftMetadataNotFound {
                minecraft_version: minecraft_version.to_string(),
            }
            .as_error()
        })?;

    let url = artifact.url.as_deref().ok_or_else(|| {
        crate::ErrorKind::MinecraftMetadataNotFound {
            minecraft_version: minecraft_version.to_string(),
        }
        .as_error()
    })?;

    let path = artifact.path.as_deref().ok_or_else(|| {
        crate::ErrorKind::MinecraftMetadataNotFound {
            minecraft_version: minecraft_version.to_string(),
        }
        .as_error()
    })?;

    Ok((url, path))
}

/// ### AR • AuthLib (Ely By)
/// Downloads bytes from the provided URL with a 15 second timeout.
async fn fetch_bytes_from_url(url: &str) -> Result<bytes::Bytes> {
    // Create client instance with request timeout.
    let client = reqwest::Client::new();
    const TIMEOUT_SECONDS: u64 = 15;

    let response = tokio::time::timeout(
        std::time::Duration::from_secs(TIMEOUT_SECONDS),
        client.get(url).send(),
    )
    .await
    .map_err(|_| {
        tracing::error!("[AR] • Download timed out after {} seconds", TIMEOUT_SECONDS);
        crate::ErrorKind::NetworkErrorOccurred {
            error: format!("Download timed out after {TIMEOUT_SECONDS} seconds").to_string(),
        }
        .as_error()
    })?
    .map_err(|e| {
        tracing::error!("[AR] • Request error: {:?}", e);
        crate::ErrorKind::NetworkErrorOccurred {
            error: format!("Request error: {e}"),
        }
        .as_error()
    })?;

    if !response.status().is_success() {
        let status = response.status().to_string();
        tracing::error!("[AR] • Failed to download authlib: HTTP {}", status);
        return Err(crate::ErrorKind::NetworkErrorOccurred {
            error: format!("Failed to download authlib: HTTP {status}"),
        }
        .as_error());
    }

    response.bytes().await.map_err(|e| {
        tracing::error!("[AR] • Failed to read response bytes: {:?}", e);
        crate::ErrorKind::NetworkErrorOccurred {
            error: format!("Failed to read response bytes: {e}"),
        }
        .as_error()
    })
}

/// ### AR • AuthLib (Ely By)
/// Gets the Minecraft library metadata from the local libraries directory.
async fn get_minecraft_library_metadata(minecraft_version: &str) -> Result<Library> {
    let state = State::get().await?;

    let path = state
        .directories
        .version_dir(minecraft_version)
        .join(format!("{}.json", minecraft_version));
    if !path.exists() {
        tracing::error!("[AR] • File not found: {:#?}", path);
        return Err(crate::ErrorKind::InvalidMinecraftVersion {
            minecraft_version: minecraft_version.to_string(),
        }
        .as_error());
    }

    let content = fs::read_to_string(&path).await?;
    let version_data: VersionJson = serde_json::from_str(&content)?;

    for lib in version_data.libraries {
        if lib.name.contains("com.mojang:authlib") {
            if let Some(downloads) = &lib.downloads {
                if let Some(artifact) = &downloads.artifact {
                    if artifact.path.is_some()
                        && artifact.url.is_some()
                        && artifact.sha1.is_some()
                    {
                        tracing::info!("[AR] • Found AuthLib: {}", lib.name);
                        tracing::info!(
                            "[AR] • Path: {}",
                            artifact.path.as_ref().unwrap()
                        );
                        tracing::info!(
                            "[AR] • URL: {}",
                            artifact.url.as_ref().unwrap()
                        );
                        tracing::info!(
                            "[AR] • SHA1: {}",
                            artifact.sha1.as_ref().unwrap()
                        );

                        return Ok(lib);
                    }
                }
            }
        }
    }

    Err(crate::ErrorKind::MinecraftMetadataNotFound {
        minecraft_version: minecraft_version.to_string(),
    }
    .as_error())
}

/// ### AR • Migration
/// Applying migration fix for SQLite database.
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

/// ### AR • Updater
/// Initialize the update launcher.
pub async fn init_update_launcher(
    download_url: &str,
    local_filename: &str,
    os_type: &str,
    auto_update_supported: bool,
) -> Result<()> {
    tracing::info!("[AR] • Initialize downloading from • {:?}", download_url);
    tracing::info!("[AR] • Save local file name • {:?}", local_filename);
    tracing::info!("[AR] • OS type • {}", os_type);
    tracing::info!("[AR] • Auto update supported • {}", auto_update_supported);

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
