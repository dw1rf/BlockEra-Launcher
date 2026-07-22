use crate::api::pack::catalog::{CatalogModpack, get_catalog};
use crate::profile;
use crate::state::{ModLoader, Profile};
use futures::StreamExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use url::Url;

const PROFILE_MANIFEST_SCHEMA: u32 = 1;
const PROFILE_MANIFEST_DIR: &str = ".blockera";
const PROFILE_MANIFEST_FILE: &str = "runtime-manifest.json";
const MAX_CORE_BYTES: u64 = 64 * 1024 * 1024;
const BUNDLED_CORE_VERSION: &str = "0.4.0";
const BUNDLED_CORE_BYTES: &[u8] = include_bytes!(env!("BLOCKERA_CORE_JAR"));
pub(crate) const FABRIC_GAME_VERSION: &str = "1.21.11";
const FABRIC_CLIENT_VERSION: &str = "0.1.0-dev";
const FABRIC_CLIENT_FILE: &str = "blockera-client-fabric-0.1.0-dev.jar";
const FABRIC_CLIENT_PREFIX: &str = "blockera-client-fabric-";
const FABRIC_API_FILE: &str = "blockera-runtime-fabric-api-0.141.4+1.21.11.jar";
const BUNDLED_FABRIC_CLIENT_BYTES: &[u8] =
    include_bytes!(env!("BLOCKERA_FABRIC_CLIENT_JAR"));
const BUNDLED_FABRIC_API_BYTES: &[u8] =
    include_bytes!(env!("BLOCKERA_FABRIC_API_JAR"));

struct BundledFabricArtifact {
    id: &'static str,
    version: &'static str,
    file_name: &'static str,
    bytes: &'static [u8],
}

const FABRIC_RUNTIME_ARTIFACTS: [BundledFabricArtifact; 2] = [
    BundledFabricArtifact {
        id: "blockera-client",
        version: FABRIC_CLIENT_VERSION,
        file_name: FABRIC_CLIENT_FILE,
        bytes: BUNDLED_FABRIC_CLIENT_BYTES,
    },
    BundledFabricArtifact {
        id: "fabric-api",
        version: "0.141.4+1.21.11",
        file_name: FABRIC_API_FILE,
        bytes: BUNDLED_FABRIC_API_BYTES,
    },
];

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct BlockeraClientCompatibility {
    pub supported: bool,
    pub version: &'static str,
    pub minecraft: String,
    pub loader: String,
    pub required_minecraft: &'static str,
    pub required_loader: &'static str,
    pub reason: Option<String>,
}

pub fn client_compatibility(
    minecraft: &str,
    loader: ModLoader,
) -> BlockeraClientCompatibility {
    let supported =
        minecraft == FABRIC_GAME_VERSION && loader == ModLoader::Fabric;
    let reason = if minecraft != FABRIC_GAME_VERSION {
        Some(format!(
            "Blockera Client поддерживает Minecraft {FABRIC_GAME_VERSION}"
        ))
    } else if loader != ModLoader::Fabric {
        Some("Blockera Client требует Fabric".to_string())
    } else {
        None
    };
    BlockeraClientCompatibility {
        supported,
        version: FABRIC_CLIENT_VERSION,
        minecraft: minecraft.to_string(),
        loader: loader.as_str().to_string(),
        required_minecraft: FABRIC_GAME_VERSION,
        required_loader: "fabric",
        reason,
    }
}

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct BlockeraCoreArtifact {
    pub id: String,
    pub version: String,
    pub file_name: String,
    pub url: String,
    pub sha256: String,
    pub size: u64,
    pub required: bool,
    pub first_party: bool,
}

#[derive(Clone, Debug, Default, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct BlockeraIntegrationConfig {
    pub api_base_url: Option<String>,
    pub websocket_url: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
struct ProfileRuntimeManifest {
    schema_version: u32,
    catalog_id: String,
    pack_sha256: String,
    minecraft: String,
    loader: String,
    blockera_core: BlockeraCoreArtifact,
    integrations: BlockeraIntegrationConfig,
}

fn input_error(message: impl Into<String>) -> crate::Error {
    crate::ErrorKind::InputError(message.into()).into()
}

fn launcher_error(message: impl Into<String>) -> crate::Error {
    crate::ErrorKind::LauncherError(message.into()).into()
}

fn valid_hex(value: &str, len: usize) -> bool {
    value.len() == len && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

fn safe_version(value: &str) -> bool {
    !value.is_empty()
        && value.len() <= 64
        && value.bytes().all(|byte| {
            byte.is_ascii_alphanumeric() || matches!(byte, b'.' | b'-' | b'_')
        })
}

pub(crate) fn is_allowed_first_party_core_url(
    value: &str,
    version: &str,
    file_name: &str,
) -> bool {
    let Ok(url) = Url::parse(value) else {
        return false;
    };
    if url.scheme() != "https"
        || !url.username().is_empty()
        || url.password().is_some()
        || url.query().is_some()
        || url.fragment().is_some()
    {
        return false;
    }

    match url
        .host_str()
        .unwrap_or_default()
        .to_ascii_lowercase()
        .as_str()
    {
        "github.com" => {
            url.path()
                == format!(
                    "/dw1rf/BlockEra-Launcher/releases/download/blockera-core-v{version}/{file_name}"
                )
        }
        "blockera.space" => {
            url.path() == format!("/runtime/core/{version}/{file_name}")
        }
        _ => false,
    }
}

fn is_allowed_core_redirect_url(url: &Url) -> bool {
    url.scheme() == "https"
        && url.username().is_empty()
        && url.password().is_none()
        && matches!(
            url.host_str()
                .unwrap_or_default()
                .to_ascii_lowercase()
                .as_str(),
            "objects.githubusercontent.com"
                | "release-assets.githubusercontent.com"
        )
}

pub(crate) fn validate_core_artifact(
    artifact: &BlockeraCoreArtifact,
) -> crate::Result<()> {
    if artifact.id != "blockera-core"
        || !safe_version(&artifact.version)
        || artifact.file_name
            != format!("blockera-core-{}.jar", artifact.version)
        || !valid_hex(&artifact.sha256, 64)
        || artifact.size == 0
        || artifact.size > MAX_CORE_BYTES
        || !artifact.required
        || !artifact.first_party
        || !is_allowed_first_party_core_url(
            &artifact.url,
            &artifact.version,
            &artifact.file_name,
        )
    {
        return Err(input_error("Invalid first-party blockera-core metadata"));
    }
    Ok(())
}

pub(crate) fn validate_integration_config(
    config: &BlockeraIntegrationConfig,
) -> crate::Result<()> {
    for (value, expected_scheme) in [
        (config.api_base_url.as_deref(), "https"),
        (config.websocket_url.as_deref(), "wss"),
    ] {
        let Some(value) = value else {
            continue;
        };
        let url = Url::parse(value)?;
        if url.scheme() != expected_scheme
            || url.host_str() != Some("blockera.space")
            || !url.username().is_empty()
            || url.password().is_some()
            || url.fragment().is_some()
        {
            return Err(input_error("Invalid Blockera integration endpoint"));
        }
    }
    Ok(())
}

fn manifest_from_pack(pack: &CatalogModpack) -> ProfileRuntimeManifest {
    ProfileRuntimeManifest {
        schema_version: PROFILE_MANIFEST_SCHEMA,
        catalog_id: pack.id.clone(),
        pack_sha256: pack.download.sha256.clone(),
        minecraft: pack.minecraft.clone(),
        loader: "forge".to_string(),
        blockera_core: pack.blockera_core.clone(),
        integrations: pack.integrations.clone(),
    }
}

fn manifest_path(profile_dir: &Path) -> PathBuf {
    profile_dir
        .join(PROFILE_MANIFEST_DIR)
        .join(PROFILE_MANIFEST_FILE)
}

async fn replace_file(
    part_path: &Path,
    destination: &Path,
) -> crate::Result<()> {
    let backup = destination.with_extension("blockera-backup");
    if backup.exists() && !destination.exists() {
        tokio::fs::rename(&backup, destination).await?;
    }
    if backup.exists() {
        tokio::fs::remove_file(&backup).await?;
    }
    if destination.exists() {
        tokio::fs::rename(destination, &backup).await?;
    }
    match tokio::fs::rename(part_path, destination).await {
        Ok(()) => {
            if backup.exists() {
                tokio::fs::remove_file(backup).await?;
            }
            Ok(())
        }
        Err(error) => {
            if backup.exists() && !destination.exists() {
                let _ = tokio::fs::rename(&backup, destination).await;
            }
            Err(error.into())
        }
    }
}

async fn write_manifest(
    profile_dir: &Path,
    manifest: &ProfileRuntimeManifest,
) -> crate::Result<()> {
    let directory = profile_dir.join(PROFILE_MANIFEST_DIR);
    tokio::fs::create_dir_all(&directory).await?;
    let path = manifest_path(profile_dir);
    let part_path = directory.join("runtime-manifest.json.part");
    let bytes = serde_json::to_vec_pretty(manifest)?;
    tokio::fs::write(&part_path, bytes).await?;
    replace_file(&part_path, &path).await
}

pub(crate) async fn write_profile_manifest(
    profile_path: &str,
    pack: &CatalogModpack,
) -> crate::Result<()> {
    validate_core_artifact(&pack.blockera_core)?;
    validate_integration_config(&pack.integrations)?;
    let profile_dir = profile::get_full_path(profile_path).await?;
    write_manifest(&profile_dir, &manifest_from_pack(pack)).await
}

async fn read_manifest(
    profile_dir: &Path,
) -> crate::Result<Option<ProfileRuntimeManifest>> {
    let path = manifest_path(profile_dir);
    let bytes = match tokio::fs::read(&path).await {
        Ok(bytes) => bytes,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(None);
        }
        Err(error) => return Err(error.into()),
    };
    let manifest = serde_json::from_slice(&bytes)?;
    Ok(Some(manifest))
}

fn manifest_matches_profile(
    manifest: &ProfileRuntimeManifest,
    profile: &Profile,
) -> bool {
    let Some(metadata) = profile.external_pack.as_ref() else {
        return false;
    };
    manifest.schema_version == PROFILE_MANIFEST_SCHEMA
        && manifest.catalog_id == metadata.catalog_id
        && manifest.pack_sha256 == metadata.sha256
        && manifest.minecraft == profile.game_version
        && manifest.loader == profile.loader.as_str()
        && validate_core_artifact(&manifest.blockera_core).is_ok()
        && validate_integration_config(&manifest.integrations).is_ok()
}

async fn resolve_manifest(
    profile: &Profile,
    profile_dir: &Path,
) -> crate::Result<ProfileRuntimeManifest> {
    let local = read_manifest(profile_dir)
        .await?
        .filter(|manifest| manifest_matches_profile(manifest, profile));
    let metadata = profile.external_pack.as_ref().ok_or_else(|| {
        launcher_error("Blockera runtime requested for a non-catalog profile")
    })?;

    let refreshed = get_catalog().await.ok().and_then(|response| {
        response.catalog.packs.into_iter().find(|pack| {
            pack.id == metadata.catalog_id
                && pack.download.sha256 == metadata.sha256
                && pack.minecraft == profile.game_version
                && profile.loader == ModLoader::Forge
                && profile.loader_version.as_deref() == Some(&pack.forge)
        })
    });

    if let Some(pack) = refreshed {
        let manifest = manifest_from_pack(&pack);
        if local.as_ref() != Some(&manifest) {
            write_manifest(profile_dir, &manifest).await?;
        }
        return Ok(manifest);
    }

    local.ok_or_else(|| {
        launcher_error(
            "Required Blockera runtime manifest is missing or incompatible",
        )
    })
}

async fn sha256_file(path: &Path) -> crate::Result<String> {
    let mut file = tokio::fs::File::open(path).await?;
    let mut hasher = Sha256::new();
    let mut buffer = vec![0_u8; 1024 * 1024];
    loop {
        let read = file.read(&mut buffer).await?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(format!("{:x}", hasher.finalize()))
}

fn sha256_bytes(bytes: &[u8]) -> String {
    format!("{:x}", Sha256::digest(bytes))
}

fn bundled_matches(artifact: &BlockeraCoreArtifact) -> bool {
    artifact.version == BUNDLED_CORE_VERSION
        && artifact.size == BUNDLED_CORE_BYTES.len() as u64
        && artifact
            .sha256
            .eq_ignore_ascii_case(&sha256_bytes(BUNDLED_CORE_BYTES))
}

async fn install_bundled(
    artifact: &BlockeraCoreArtifact,
    part_path: &Path,
    destination: &Path,
) -> crate::Result<()> {
    if !bundled_matches(artifact) {
        return Err(launcher_error(
            "Bundled blockera-core does not match the required manifest",
        ));
    }
    tokio::fs::write(part_path, BUNDLED_CORE_BYTES).await?;
    replace_file(part_path, destination).await
}

fn core_download_client() -> crate::Result<reqwest::Client> {
    let policy = reqwest::redirect::Policy::custom(|attempt| {
        if attempt.previous().len() >= 5 {
            attempt.error("too many blockera-core redirects")
        } else if is_allowed_core_redirect_url(attempt.url()) {
            attempt.follow()
        } else {
            attempt.error("blockera-core redirect target is not allowlisted")
        }
    });
    Ok(reqwest::Client::builder()
        .user_agent(crate::launcher_user_agent())
        .connect_timeout(Duration::from_secs(15))
        .read_timeout(Duration::from_secs(30))
        .timeout(Duration::from_secs(10 * 60))
        .redirect(policy)
        .build()?)
}

async fn download_artifact(
    artifact: &BlockeraCoreArtifact,
    part_path: &Path,
    destination: &Path,
) -> crate::Result<()> {
    let result = async {
        let response =
            core_download_client()?.get(&artifact.url).send().await?;
        if !response.status().is_success() {
            return Err(launcher_error(format!(
                "blockera-core server returned {}",
                response.status()
            )));
        }
        if response.content_length() != Some(artifact.size) {
            return Err(launcher_error(
                "Unexpected blockera-core content length",
            ));
        }

        let mut file = tokio::fs::File::create(part_path).await?;
        let mut stream = response.bytes_stream();
        let mut hasher = Sha256::new();
        let mut downloaded = 0_u64;
        while let Some(chunk) = stream.next().await {
            let chunk = chunk?;
            downloaded = downloaded.saturating_add(chunk.len() as u64);
            if downloaded > artifact.size || downloaded > MAX_CORE_BYTES {
                return Err(launcher_error(
                    "blockera-core download exceeded its declared size",
                ));
            }
            hasher.update(&chunk);
            file.write_all(&chunk).await?;
        }
        file.flush().await?;
        drop(file);

        let actual = format!("{:x}", hasher.finalize());
        if downloaded != artifact.size
            || !actual.eq_ignore_ascii_case(&artifact.sha256)
        {
            return Err(launcher_error(format!(
                "blockera-core checksum mismatch: expected {}, got {actual}",
                artifact.sha256
            )));
        }
        replace_file(part_path, destination).await
    }
    .await;

    if result.is_err() {
        let _ = tokio::fs::remove_file(part_path).await;
    }
    result
}

async fn remove_previous_core_jars(
    mods_dir: &Path,
    keep_file_name: &str,
) -> crate::Result<()> {
    let mut entries = tokio::fs::read_dir(mods_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if name != keep_file_name
            && name.starts_with("blockera-core-")
            && name.ends_with(".jar")
            && entry.file_type().await?.is_file()
        {
            tokio::fs::remove_file(entry.path()).await?;
        }
    }
    Ok(())
}

async fn ensure_bundled_runtime_file(
    mods_dir: &Path,
    file_name: &str,
    bytes: &[u8],
) -> crate::Result<()> {
    let destination = mods_dir.join(file_name);
    let expected_sha256 = sha256_bytes(bytes);
    if destination.is_file()
        && tokio::fs::metadata(&destination).await?.len() == bytes.len() as u64
        && sha256_file(&destination)
            .await?
            .eq_ignore_ascii_case(&expected_sha256)
    {
        return Ok(());
    }

    let part_path = mods_dir.join(format!(".{file_name}.part"));
    let result = async {
        tokio::fs::write(&part_path, bytes).await?;
        if tokio::fs::metadata(&part_path).await?.len() != bytes.len() as u64
            || !sha256_file(&part_path)
                .await?
                .eq_ignore_ascii_case(&expected_sha256)
        {
            return Err(launcher_error(format!(
                "{file_name} failed its pre-install checksum"
            )));
        }
        replace_file(&part_path, &destination).await?;
        if tokio::fs::metadata(&destination).await?.len() != bytes.len() as u64
            || !sha256_file(&destination)
                .await?
                .eq_ignore_ascii_case(&expected_sha256)
        {
            return Err(launcher_error(format!(
                "{file_name} failed its post-install checksum"
            )));
        }
        Ok(())
    }
    .await;
    if result.is_err() {
        let _ = tokio::fs::remove_file(&part_path).await;
    }
    result
}

fn read_fabric_api_version(path: &Path) -> Option<String> {
    let file = std::fs::File::open(path).ok()?;
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let mut metadata = archive.by_name("fabric.mod.json").ok()?;
    let mut json = String::new();
    metadata.read_to_string(&mut json).ok()?;
    let value: serde_json::Value = serde_json::from_str(&json).ok()?;
    (value.get("id")?.as_str()? == "fabric-api")
        .then(|| value.get("version")?.as_str().map(str::to_owned))?
}

async fn ensure_fabric_api(mods_dir: &Path) -> crate::Result<()> {
    let mut external_versions = Vec::new();
    let mut entries = tokio::fs::read_dir(mods_dir).await?;
    while let Some(entry) = entries.next_entry().await? {
        if !entry.file_type().await?.is_file()
            || entry.file_name().to_string_lossy() == FABRIC_API_FILE
            || !entry
                .file_name()
                .to_string_lossy()
                .to_ascii_lowercase()
                .ends_with(".jar")
        {
            continue;
        }
        let path = entry.path();
        let version =
            tokio::task::spawn_blocking(move || read_fabric_api_version(&path))
                .await
                .map_err(|error| {
                    launcher_error(format!(
                        "Unable to inspect Fabric API: {error}"
                    ))
                })?;
        if let Some(version) = version {
            external_versions.push(version);
        }
    }

    if external_versions.len() > 1 {
        return Err(launcher_error(
            "Multiple external Fabric API JARs are installed",
        ));
    }
    if let Some(version) = external_versions.first() {
        if !version.ends_with(&format!("+{FABRIC_GAME_VERSION}")) {
            return Err(launcher_error(format!(
                "Installed Fabric API {version} is not compatible with Minecraft {FABRIC_GAME_VERSION}"
            )));
        }
        let managed = mods_dir.join(FABRIC_API_FILE);
        if managed.is_file() {
            tokio::fs::remove_file(managed).await?;
        }
        return Ok(());
    }

    ensure_bundled_runtime_file(
        mods_dir,
        FABRIC_API_FILE,
        BUNDLED_FABRIC_API_BYTES,
    )
    .await
}

async fn remove_previous_fabric_client_jars(
    mods_dir: &Path,
    keep_file_name: &str,
) -> crate::Result<()> {
    let mut entries = match tokio::fs::read_dir(mods_dir).await {
        Ok(entries) => entries,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(());
        }
        Err(error) => return Err(error.into()),
    };
    while let Some(entry) = entries.next_entry().await? {
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if name != keep_file_name
            && name.starts_with(FABRIC_CLIENT_PREFIX)
            && name.ends_with(".jar")
            && entry.file_type().await?.is_file()
        {
            tokio::fs::remove_file(entry.path()).await?;
        }
    }
    Ok(())
}

async fn ensure_fabric_client(profile: &Profile) -> crate::Result<()> {
    let compatibility =
        client_compatibility(&profile.game_version, profile.loader);
    if !compatibility.supported {
        return Err(input_error(compatibility.reason.unwrap_or_else(|| {
            "Blockera Client is not compatible with this profile".to_string()
        })));
    }
    let profile_dir = profile::get_full_path(&profile.path).await?;
    let mods_dir = profile_dir.join("mods");
    tokio::fs::create_dir_all(&mods_dir).await?;
    for artifact in FABRIC_RUNTIME_ARTIFACTS
        .iter()
        .filter(|artifact| artifact.id == "blockera-client")
    {
        tracing::debug!(
            artifact_id = artifact.id,
            artifact_version = artifact.version,
            "Verifying bundled Fabric runtime artifact"
        );
        ensure_bundled_runtime_file(
            &mods_dir,
            artifact.file_name,
            artifact.bytes,
        )
        .await?;
    }
    ensure_fabric_api(&mods_dir).await?;
    remove_previous_fabric_client_jars(&mods_dir, FABRIC_CLIENT_FILE).await
}

pub async fn set_client_enabled(
    profile_path: &str,
    enabled: bool,
) -> crate::Result<()> {
    let mut current = profile::get(profile_path).await?.ok_or_else(|| {
        crate::ErrorKind::UnmanagedProfileError(profile_path.to_string())
    })?;
    if enabled {
        current.blockera_client_enabled = true;
        ensure_fabric_client(&current).await?;
    } else {
        let profile_dir = profile::get_full_path(profile_path).await?;
        let mods_dir = profile_dir.join("mods");
        remove_previous_fabric_client_jars(&mods_dir, "").await?;
    }
    profile::edit(profile_path, |profile| {
        profile.blockera_client_enabled = enabled;
        profile.modified = chrono::Utc::now();
        async { Ok(()) }
    })
    .await
}

async fn ensure_artifact(
    profile_dir: &Path,
    artifact: &BlockeraCoreArtifact,
) -> crate::Result<()> {
    validate_core_artifact(artifact)?;
    let mods_dir = profile_dir.join("mods");
    tokio::fs::create_dir_all(&mods_dir).await?;
    let destination = mods_dir.join(&artifact.file_name);
    if destination.is_file()
        && sha256_file(&destination)
            .await?
            .eq_ignore_ascii_case(&artifact.sha256)
    {
        remove_previous_core_jars(&mods_dir, &artifact.file_name).await?;
        return Ok(());
    }

    let part_path = mods_dir.join(format!(".{}.part", artifact.file_name));
    if bundled_matches(artifact) {
        install_bundled(artifact, &part_path, &destination).await?;
    } else {
        download_artifact(artifact, &part_path, &destination).await?;
    }

    if !sha256_file(&destination)
        .await?
        .eq_ignore_ascii_case(&artifact.sha256)
    {
        return Err(launcher_error(
            "Required blockera-core failed its post-install checksum",
        ));
    }
    remove_previous_core_jars(&mods_dir, &artifact.file_name).await?;
    Ok(())
}

pub(crate) async fn ensure_profile(profile: &Profile) -> crate::Result<()> {
    if profile.blockera_client_enabled {
        ensure_fabric_client(profile).await?;
    }
    if profile.external_pack.is_none() {
        return Ok(());
    }
    if profile.loader == ModLoader::Vanilla {
        return Err(launcher_error(
            "Blockera catalog profiles require a supported mod loader",
        ));
    }

    let profile_dir = profile::get_full_path(&profile.path).await?;
    let manifest = resolve_manifest(profile, &profile_dir).await?;
    ensure_artifact(&profile_dir, &manifest.blockera_core).await
}

pub(crate) async fn integration_jvm_args(
    profile: &Profile,
) -> crate::Result<Vec<String>> {
    if profile.external_pack.is_none() {
        return Ok(Vec::new());
    }
    let profile_dir = profile::get_full_path(&profile.path).await?;
    let manifest = read_manifest(&profile_dir)
        .await?
        .filter(|manifest| manifest_matches_profile(manifest, profile))
        .ok_or_else(|| {
            launcher_error("Required Blockera runtime manifest is missing")
        })?;
    validate_integration_config(&manifest.integrations)?;
    let mut args = Vec::with_capacity(2);
    if let Some(url) = manifest.integrations.api_base_url {
        args.push(format!("-Dblockera.apiBaseUrl={url}"));
    }
    if let Some(url) = manifest.integrations.websocket_url {
        args.push(format!("-Dblockera.websocketUrl={url}"));
    }
    Ok(args)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn artifact(url: &str) -> BlockeraCoreArtifact {
        BlockeraCoreArtifact {
            id: "blockera-core".to_string(),
            version: BUNDLED_CORE_VERSION.to_string(),
            file_name: format!("blockera-core-{BUNDLED_CORE_VERSION}.jar"),
            url: url.to_string(),
            sha256: sha256_bytes(BUNDLED_CORE_BYTES),
            size: BUNDLED_CORE_BYTES.len() as u64,
            required: true,
            first_party: true,
        }
    }

    #[test]
    fn bundled_core_is_a_valid_first_party_artifact() {
        let artifact = artifact(
            "https://github.com/dw1rf/BlockEra-Launcher/releases/download/blockera-core-v0.4.0/blockera-core-0.4.0.jar",
        );
        validate_core_artifact(&artifact).unwrap();
        assert!(bundled_matches(&artifact));
    }

    #[test]
    fn fabric_client_compatibility_is_explicit() {
        assert!(
            client_compatibility(FABRIC_GAME_VERSION, ModLoader::Fabric)
                .supported
        );
        assert!(
            !client_compatibility(FABRIC_GAME_VERSION, ModLoader::Forge)
                .supported
        );
        assert!(!client_compatibility("1.19.2", ModLoader::Fabric).supported);
        assert_eq!(FABRIC_CLIENT_VERSION, "0.1.0-dev");
    }

    #[tokio::test]
    async fn fabric_runtime_is_repaired_without_touching_user_mods() {
        let directory = tempfile::tempdir().unwrap();
        let mods = directory.path().join("mods");
        tokio::fs::create_dir_all(&mods).await.unwrap();
        tokio::fs::write(mods.join(FABRIC_CLIENT_FILE), b"broken")
            .await
            .unwrap();
        tokio::fs::write(mods.join("user-mod.jar"), b"user")
            .await
            .unwrap();

        ensure_bundled_runtime_file(
            &mods,
            FABRIC_CLIENT_FILE,
            BUNDLED_FABRIC_CLIENT_BYTES,
        )
        .await
        .unwrap();

        assert_eq!(
            tokio::fs::metadata(mods.join(FABRIC_CLIENT_FILE))
                .await
                .unwrap()
                .len(),
            BUNDLED_FABRIC_CLIENT_BYTES.len() as u64
        );
        assert!(mods.join("user-mod.jar").is_file());
    }

    #[tokio::test]
    async fn compatible_external_fabric_api_prevents_managed_duplicate() {
        let directory = tempfile::tempdir().unwrap();
        let mods = directory.path().join("mods");
        tokio::fs::create_dir_all(&mods).await.unwrap();
        let external = mods.join("fabric-api-user-managed.jar");
        tokio::fs::write(&external, BUNDLED_FABRIC_API_BYTES)
            .await
            .unwrap();
        tokio::fs::write(mods.join(FABRIC_API_FILE), b"old managed copy")
            .await
            .unwrap();

        ensure_fabric_api(&mods).await.unwrap();

        assert!(external.is_file());
        assert!(!mods.join(FABRIC_API_FILE).exists());
    }

    #[test]
    fn builtin_catalog_references_the_exact_bundled_core() {
        let catalog = crate::api::pack::catalog::builtin_catalog().unwrap();
        assert!(bundled_matches(&catalog.packs[0].blockera_core));
    }

    #[test]
    fn third_party_and_insecure_core_urls_are_rejected() {
        for url in [
            "https://example.com/blockera-core-0.4.0.jar",
            "http://github.com/dw1rf/BlockEra-Launcher/releases/download/blockera-core-v0.4.0/blockera-core-0.4.0.jar",
            "https://github.com/other/repo/releases/download/blockera-core-v0.4.0/blockera-core-0.4.0.jar",
        ] {
            assert!(validate_core_artifact(&artifact(url)).is_err());
        }
    }

    #[test]
    fn integration_endpoints_require_blockera_https_and_wss() {
        let valid = BlockeraIntegrationConfig {
            api_base_url: Some("https://blockera.space/api/v1/".to_string()),
            websocket_url: Some(
                "wss://blockera.space/api/v1/client/events".to_string(),
            ),
        };
        assert!(validate_integration_config(&valid).is_ok());

        for invalid in [
            BlockeraIntegrationConfig {
                api_base_url: Some("http://blockera.space/api/v1/".to_string()),
                websocket_url: None,
            },
            BlockeraIntegrationConfig {
                api_base_url: Some("https://example.com/api/v1/".to_string()),
                websocket_url: None,
            },
            BlockeraIntegrationConfig {
                api_base_url: None,
                websocket_url: Some("ws://blockera.space/events".to_string()),
            },
        ] {
            assert!(validate_integration_config(&invalid).is_err());
        }
    }

    #[tokio::test]
    async fn bundled_core_is_repaired_and_old_first_party_version_removed() {
        let directory = tempfile::tempdir().unwrap();
        let mods = directory.path().join("mods");
        tokio::fs::create_dir_all(&mods).await.unwrap();
        tokio::fs::write(mods.join("blockera-core-0.0.1.jar"), b"old")
            .await
            .unwrap();
        tokio::fs::write(mods.join("unrelated.jar"), b"user mod")
            .await
            .unwrap();
        let artifact = artifact(
            "https://github.com/dw1rf/BlockEra-Launcher/releases/download/blockera-core-v0.4.0/blockera-core-0.4.0.jar",
        );

        ensure_artifact(directory.path(), &artifact).await.unwrap();

        assert!(mods.join(&artifact.file_name).is_file());
        assert!(!mods.join("blockera-core-0.0.1.jar").exists());
        assert!(mods.join("unrelated.jar").is_file());
    }
}
