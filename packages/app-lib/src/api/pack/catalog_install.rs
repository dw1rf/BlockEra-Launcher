use super::catalog::{CatalogModpack, builtin_catalog, get_catalog};
use super::install_from::{
    CreatePackLocation, PackDependency, PackFileHash, PackFormat,
};
use super::install_mrpack::install_zipped_mrpack;
use crate::state::ExternalPackMetadata;
use crate::{State, profile};
use dashmap::DashMap;
use futures::StreamExt;
use parking_lot::RwLock;
use path_util::SafeRelativeUtf8UnixPathBuf;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::collections::HashSet;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, LazyLock};
use std::time::Duration;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use uuid::Uuid;

const MAX_PACK_BYTES: u64 = 512 * 1024 * 1024;
const MAX_ZIP_ENTRIES: usize = 20_000;
const MAX_UNPACKED_BYTES: u64 = 3 * 1024 * 1024 * 1024;
const MAX_MANIFEST_BYTES: u64 = 5 * 1024 * 1024;
const MAX_MANIFEST_FILES: usize = 5_000;

#[derive(Clone, Copy, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum CatalogInstallState {
    Downloading,
    Installing,
    Installed,
    Cancelled,
    Error,
}

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct CatalogInstallStatus {
    pub job_id: String,
    pub pack_id: String,
    pub state: CatalogInstallState,
    pub downloaded: u64,
    pub total: u64,
    pub profile_path: Option<String>,
    pub error: Option<String>,
}

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct MrpackValidation {
    pub manifest_files: usize,
    pub archive_entries: usize,
    pub unpacked_size: u64,
}

#[derive(Clone)]
struct JobControl {
    cancelled: Arc<AtomicBool>,
    status: Arc<RwLock<CatalogInstallStatus>>,
}

struct VerificationPlan {
    manifest_files: Vec<(String, String)>,
    override_files: Vec<(String, String)>,
}

static INSTALL_JOBS: LazyLock<DashMap<String, JobControl>> =
    LazyLock::new(DashMap::new);

fn input_error(message: impl Into<String>) -> crate::Error {
    crate::ErrorKind::InputError(message.into()).into()
}

fn find_pack(catalog: &[CatalogModpack], id: &str) -> Option<CatalogModpack> {
    catalog.iter().find(|pack| pack.id == id).cloned()
}

async fn catalog_pack(id: &str) -> crate::Result<CatalogModpack> {
    let response = get_catalog().await?;
    find_pack(&response.catalog.packs, id).ok_or_else(|| {
        input_error(format!("Catalog modpack {id} was not found"))
    })
}

fn update_status(
    control: &JobControl,
    update: impl FnOnce(&mut CatalogInstallStatus),
) {
    update(&mut control.status.write());
}

fn download_client() -> crate::Result<reqwest::Client> {
    let policy = reqwest::redirect::Policy::custom(|attempt| {
        if attempt.previous().len() >= 5 {
            return attempt.error("too many redirects");
        }
        if super::catalog::is_allowed_download_url(attempt.url().as_str()) {
            attempt.follow()
        } else {
            attempt.error("redirect target is not allowlisted")
        }
    });
    Ok(reqwest::Client::builder()
        .user_agent(crate::launcher_user_agent())
        .connect_timeout(Duration::from_secs(15))
        .read_timeout(Duration::from_secs(30))
        .timeout(Duration::from_secs(30 * 60))
        .redirect(policy)
        .build()?)
}

async fn remove_download_dir(path: &Path) {
    if let Err(error) = tokio::fs::remove_dir_all(path).await
        && error.kind() != std::io::ErrorKind::NotFound
    {
        tracing::warn!(%error, path = %path.display(), "Unable to clean download directory");
    }
}

fn validate_download_response(
    status: reqwest::StatusCode,
    final_url: &str,
    content_length: Option<u64>,
    expected_size: u64,
) -> crate::Result<u64> {
    if !status.is_success() {
        return Err(input_error(format!("Modpack server returned {status}")));
    }
    if !super::catalog::is_allowed_download_url(final_url) {
        return Err(input_error("Final download URL is not allowlisted"));
    }
    let content_length = content_length
        .ok_or_else(|| input_error("Modpack response has no Content-Length"))?;
    if content_length != expected_size || content_length > MAX_PACK_BYTES {
        return Err(input_error(format!(
            "Unexpected modpack size: expected {expected_size}, received {content_length}"
        )));
    }
    Ok(content_length)
}

async fn download_pack(
    pack: &CatalogModpack,
    control: &JobControl,
    job_dir: &Path,
) -> crate::Result<PathBuf> {
    if pack.size > MAX_PACK_BYTES {
        return Err(input_error("Catalog modpack exceeds the size limit"));
    }
    tokio::fs::create_dir_all(job_dir).await?;
    let part_path = job_dir.join("pack.mrpack.part");
    let final_path = job_dir.join("pack.mrpack");

    let response = download_client()?.get(&pack.download.url).send().await?;
    let content_length = validate_download_response(
        response.status(),
        response.url().as_str(),
        response.content_length(),
        pack.size,
    )?;

    let mut file = tokio::fs::File::create(&part_path).await?;
    let mut stream = response.bytes_stream();
    let mut hasher = Sha256::new();
    let mut downloaded = 0_u64;
    while let Some(chunk) = stream.next().await {
        if control.cancelled.load(Ordering::Relaxed) {
            drop(file);
            let _ = tokio::fs::remove_file(&part_path).await;
            return Err(input_error("Modpack download was cancelled"));
        }
        let chunk = chunk?;
        downloaded = downloaded.saturating_add(chunk.len() as u64);
        if downloaded > content_length || downloaded > MAX_PACK_BYTES {
            return Err(input_error(
                "Modpack download exceeded its size limit",
            ));
        }
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
        update_status(control, |status| status.downloaded = downloaded);
    }
    file.flush().await?;
    drop(file);

    if downloaded != content_length {
        return Err(input_error(format!(
            "Incomplete modpack download: {downloaded} of {content_length} bytes"
        )));
    }
    let actual_hash = format!("{:x}", hasher.finalize());
    if !actual_hash.eq_ignore_ascii_case(&pack.download.sha256) {
        return Err(input_error(format!(
            "Incorrect modpack SHA-256: {actual_hash}"
        )));
    }

    tokio::fs::rename(&part_path, &final_path).await?;
    Ok(final_path)
}

fn is_binary_path(path: &str) -> bool {
    let lower = path.to_ascii_lowercase();
    lower.ends_with(".exe") || lower.ends_with(".dll")
}

fn valid_hex(value: &str, len: usize) -> bool {
    value.len() == len && value.bytes().all(|byte| byte.is_ascii_hexdigit())
}

fn validate_pack_sync(
    path: &Path,
    expected: &CatalogModpack,
) -> crate::Result<MrpackValidation> {
    let file = std::fs::File::open(path)?;
    let mut archive = zip::ZipArchive::new(file).map_err(|_| {
        input_error("Downloaded file is not a readable MRPACK archive")
    })?;
    if archive.is_empty() || archive.len() > MAX_ZIP_ENTRIES {
        return Err(input_error("MRPACK archive has an invalid entry count"));
    }

    let mut names = HashSet::new();
    let mut unpacked_size = 0_u64;
    for index in 0..archive.len() {
        let entry = archive.by_index(index).map_err(|error| {
            input_error(format!("Unable to read MRPACK entry: {error}"))
        })?;
        let name = entry.name().to_string();
        if name.contains('\\')
            || entry.enclosed_name().is_none()
            || SafeRelativeUtf8UnixPathBuf::try_from(name.clone()).is_err()
            || !names.insert(name.clone())
        {
            return Err(input_error(format!(
                "MRPACK contains an unsafe or duplicate path: {name}"
            )));
        }
        unpacked_size = unpacked_size
            .checked_add(entry.size())
            .ok_or_else(|| input_error("MRPACK unpacked size overflow"))?;
        if unpacked_size > MAX_UNPACKED_BYTES {
            return Err(input_error("MRPACK unpacked size exceeds the limit"));
        }
        if is_binary_path(&name)
            && (name.starts_with("overrides/")
                || name.starts_with("client-overrides/"))
        {
            return Err(input_error(format!(
                "MRPACK contains an undeclared executable: {name}"
            )));
        }
    }

    let mut manifest_entry =
        archive.by_name("modrinth.index.json").map_err(|_| {
            input_error("MRPACK does not contain modrinth.index.json")
        })?;
    if manifest_entry.size() > MAX_MANIFEST_BYTES {
        return Err(input_error("MRPACK manifest is too large"));
    }
    let mut manifest = String::new();
    manifest_entry.read_to_string(&mut manifest)?;
    drop(manifest_entry);
    let manifest: PackFormat = serde_json::from_str(&manifest)?;

    if manifest.game != "minecraft" || manifest.format_version != 1 {
        return Err(input_error("MRPACK has an unsupported format or game"));
    }
    if manifest.name != expected.title
        || manifest.version_id
            != format!("{}+{}", expected.version, expected.revision)
        || manifest.dependencies.get(&PackDependency::Minecraft)
            != Some(&expected.minecraft)
        || manifest.dependencies.get(&PackDependency::Forge)
            != Some(&expected.forge)
    {
        return Err(input_error(
            "MRPACK identity, Minecraft, or Forge version does not match the catalog",
        ));
    }
    if manifest.files.len() > MAX_MANIFEST_FILES {
        return Err(input_error("MRPACK manifest contains too many files"));
    }

    let mut paths = HashSet::new();
    for file in &manifest.files {
        if !paths.insert(file.path.as_str())
            || file.downloads.is_empty()
            || file.downloads.len() > 5
            || file.file_size == 0
        {
            return Err(input_error(format!(
                "MRPACK contains invalid metadata for {}",
                file.path
            )));
        }
        if file
            .downloads
            .iter()
            .any(|url| !super::catalog::is_allowed_download_url(url))
        {
            return Err(input_error(format!(
                "MRPACK contains a forbidden download URL for {}",
                file.path
            )));
        }
        let sha1 = file.hashes.get(&PackFileHash::Sha1);
        let sha512 = file.hashes.get(&PackFileHash::Sha512);
        if sha1.is_none_or(|hash| !valid_hex(hash, 40))
            || sha512.is_some_and(|hash| !valid_hex(hash, 128))
        {
            return Err(input_error(format!(
                "MRPACK contains invalid hashes for {}",
                file.path
            )));
        }
    }

    Ok(MrpackValidation {
        manifest_files: manifest.files.len(),
        archive_entries: archive.len(),
        unpacked_size,
    })
}

pub async fn validate_catalog_mrpack(
    path: PathBuf,
    expected: CatalogModpack,
) -> crate::Result<MrpackValidation> {
    tokio::task::spawn_blocking(move || validate_pack_sync(&path, &expected))
        .await?
}

async fn store_verified_pack(
    source: &Path,
    sha256: &str,
) -> crate::Result<PathBuf> {
    let state = State::get().await?;
    let directory = state.directories.caches_dir().join("external-packs");
    tokio::fs::create_dir_all(&directory).await?;
    let destination = directory.join(format!("{sha256}.mrpack"));
    if destination.exists() {
        if sha256_file(&destination)
            .await?
            .eq_ignore_ascii_case(sha256)
        {
            tokio::fs::remove_file(source).await?;
            return Ok(destination);
        }
        tokio::fs::remove_file(&destination).await?;
    }
    if let Err(error) = tokio::fs::rename(source, &destination).await {
        if destination.exists()
            && sha256_file(&destination)
                .await?
                .eq_ignore_ascii_case(sha256)
        {
            tokio::fs::remove_file(source).await?;
            return Ok(destination);
        }
        return Err(error.into());
    }
    Ok(destination)
}

async fn install_verified_pack(
    pack: &CatalogModpack,
    path: PathBuf,
    control: &JobControl,
) -> crate::Result<String> {
    if control.cancelled.load(Ordering::Relaxed) {
        return Err(input_error("Modpack installation was cancelled"));
    }
    update_status(control, |status| {
        status.state = CatalogInstallState::Installing
    });

    let profile_path = crate::api::profile::create::profile_create(
        pack.title.clone(),
        pack.minecraft.clone(),
        crate::data::ModLoader::Forge,
        Some(pack.forge.clone()),
        None,
        None,
        Some(true),
        Some(false),
    )
    .await?;

    let result = async {
        crate::api::profile::edit(&profile_path, |profile| {
            profile.external_pack = Some(ExternalPackMetadata {
                catalog_id: pack.id.clone(),
                version: pack.version.clone(),
                sha256: pack.download.sha256.clone(),
                official_url: pack.official_url.clone(),
                author_display_name: pack.author.display_name.clone(),
                original_author: pack.original_author.clone(),
                unofficial_integration: pack.unofficial_integration,
            });
            async { Ok(()) }
        })
        .await?;
        crate::blockera_runtime::write_profile_manifest(&profile_path, pack)
            .await?;

        let installed_profile = install_zipped_mrpack(
            CreatePackLocation::FromFile { path },
            profile_path.clone(),
        )
        .await?;
        Ok(installed_profile)
    }
    .await;

    if result.is_err() {
        let _ = crate::api::profile::remove(&profile_path).await;
    }
    result
}

async fn run_install(
    pack: CatalogModpack,
    control: JobControl,
    job_dir: PathBuf,
) {
    let result = async {
        let downloaded_path = download_pack(&pack, &control, &job_dir).await?;
        validate_catalog_mrpack(downloaded_path.clone(), pack.clone()).await?;
        let cached_path =
            store_verified_pack(&downloaded_path, &pack.download.sha256)
                .await?;
        install_verified_pack(&pack, cached_path, &control).await
    }
    .await;

    match result {
        Ok(profile_path) => update_status(&control, |status| {
            status.state = CatalogInstallState::Installed;
            status.profile_path = Some(profile_path);
            status.error = None;
        }),
        Err(error) => {
            let cancelled = control.cancelled.load(Ordering::Relaxed);
            update_status(&control, |status| {
                status.state = if cancelled {
                    CatalogInstallState::Cancelled
                } else {
                    CatalogInstallState::Error
                };
                status.error = (!cancelled).then(|| error.to_string());
            });
        }
    }
    remove_download_dir(&job_dir).await;
}

pub async fn start_catalog_install(pack_id: &str) -> crate::Result<String> {
    let pack = catalog_pack(pack_id).await?;
    let job_id = Uuid::new_v4().to_string();
    let state = State::get().await?;
    let job_dir = state.directories.config_dir.join("downloads").join(&job_id);
    let status = CatalogInstallStatus {
        job_id: job_id.clone(),
        pack_id: pack.id.clone(),
        state: CatalogInstallState::Downloading,
        downloaded: 0,
        total: pack.size,
        profile_path: None,
        error: None,
    };
    let control = JobControl {
        cancelled: Arc::new(AtomicBool::new(false)),
        status: Arc::new(RwLock::new(status)),
    };
    INSTALL_JOBS.insert(job_id.clone(), control.clone());
    tokio::spawn(run_install(pack, control, job_dir));
    Ok(job_id)
}

pub fn cancel_catalog_install(job_id: &str) -> crate::Result<()> {
    let control = INSTALL_JOBS
        .get(job_id)
        .ok_or_else(|| input_error("Unknown catalog install job"))?;
    if control.status.read().state != CatalogInstallState::Downloading {
        return Err(input_error(
            "Only an active catalog download can be cancelled",
        ));
    }
    control.cancelled.store(true, Ordering::Relaxed);
    Ok(())
}

pub fn catalog_install_status(
    job_id: &str,
) -> crate::Result<CatalogInstallStatus> {
    let control = INSTALL_JOBS
        .get(job_id)
        .ok_or_else(|| input_error("Unknown catalog install job"))?;
    let status = control.status.read().clone();
    Ok(status)
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

async fn sha1_file(path: &Path) -> crate::Result<String> {
    let mut file = tokio::fs::File::open(path).await?;
    let mut hasher = sha1_smol::Sha1::new();
    let mut buffer = vec![0_u8; 1024 * 1024];
    loop {
        let read = file.read(&mut buffer).await?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Ok(hasher.digest().to_string())
}

fn verification_plan_sync(path: &Path) -> crate::Result<VerificationPlan> {
    let file = std::fs::File::open(path)?;
    let mut archive = zip::ZipArchive::new(file).map_err(|error| {
        input_error(format!("Unable to read MRPACK: {error}"))
    })?;
    let mut manifest_entry =
        archive.by_name("modrinth.index.json").map_err(|_| {
            input_error("MRPACK does not contain modrinth.index.json")
        })?;
    let mut manifest = String::new();
    manifest_entry.read_to_string(&mut manifest)?;
    drop(manifest_entry);
    let manifest: PackFormat = serde_json::from_str(&manifest)?;
    let manifest_files = manifest
        .files
        .into_iter()
        .map(|file| {
            let hash = file
                .hashes
                .get(&PackFileHash::Sha1)
                .cloned()
                .ok_or_else(|| input_error("MRPACK file has no SHA-1"))?;
            Ok((file.path.as_str().to_string(), hash))
        })
        .collect::<crate::Result<Vec<_>>>()?;

    let mut override_files = Vec::new();
    for index in 0..archive.len() {
        let mut entry = archive.by_index(index).map_err(|error| {
            input_error(format!("Unable to read MRPACK entry: {error}"))
        })?;
        let name = entry.name().to_string();
        let relative = name
            .strip_prefix("overrides/")
            .or_else(|| name.strip_prefix("client-overrides/"));
        let Some(relative) = relative else {
            continue;
        };
        let relative = relative.to_string();
        if relative.is_empty() || entry.is_dir() {
            continue;
        }
        let mut hasher = Sha256::new();
        let mut buffer = vec![0_u8; 1024 * 1024];
        loop {
            let read = entry.read(&mut buffer)?;
            if read == 0 {
                break;
            }
            hasher.update(&buffer[..read]);
        }
        override_files.push((relative, format!("{:x}", hasher.finalize())));
    }

    Ok(VerificationPlan {
        manifest_files,
        override_files,
    })
}

pub async fn verify_installed_catalog_pack(
    profile_path: &str,
) -> crate::Result<bool> {
    let profile = profile::get(profile_path)
        .await?
        .ok_or_else(|| input_error("Profile was not found"))?;
    let metadata = profile.external_pack.ok_or_else(|| {
        input_error("Profile is not linked to a catalog pack")
    })?;
    let pack = find_pack(&builtin_catalog()?.packs, &metadata.catalog_id)
        .ok_or_else(|| input_error("Catalog pack metadata was not found"))?;
    if metadata.sha256 != pack.download.sha256
        || profile.game_version != pack.minecraft
        || profile.loader != crate::data::ModLoader::Forge
        || profile.loader_version.as_deref() != Some(&pack.forge)
    {
        return Ok(false);
    }

    let state = State::get().await?;
    let archive = state
        .directories
        .caches_dir()
        .join("external-packs")
        .join(format!("{}.mrpack", metadata.sha256));
    if !archive.exists() {
        return Ok(false);
    }
    if !sha256_file(&archive)
        .await?
        .eq_ignore_ascii_case(&metadata.sha256)
        || validate_catalog_mrpack(archive.clone(), pack)
            .await
            .is_err()
    {
        return Ok(false);
    }

    let plan =
        tokio::task::spawn_blocking(move || verification_plan_sync(&archive))
            .await??;
    let profile_dir = profile::get_full_path(profile_path).await?;
    for (relative, expected) in plan.manifest_files {
        let path = profile_dir.join(relative);
        if !path.is_file()
            || !sha1_file(&path).await?.eq_ignore_ascii_case(&expected)
        {
            return Ok(false);
        }
    }
    for (relative, expected) in plan.override_files {
        let path = profile_dir.join(relative);
        if !path.is_file()
            || !sha256_file(&path).await?.eq_ignore_ascii_case(&expected)
        {
            return Ok(false);
        }
    }
    Ok(true)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn manifest(download: &str, forge: &str) -> String {
        format!(
            r#"{{
                "game":"minecraft",
                "formatVersion":1,
                "versionId":"2.0+blockera.1",
                "name":"Индустриальная эра 2.0",
                "summary":"test",
                "files":[{{
                    "path":"mods/example.jar",
                    "hashes":{{"sha1":"{}","sha512":"{}"}},
                    "downloads":["{download}"],
                    "fileSize":1
                }}],
                "dependencies":{{"minecraft":"1.19.2","forge":"{forge}"}}
            }}"#,
            "a".repeat(40),
            "b".repeat(128),
        )
    }

    fn test_archive(
        manifest: &str,
        extras: &[(&str, &[u8])],
    ) -> (tempfile::TempDir, PathBuf) {
        let directory = tempfile::tempdir().unwrap();
        let path = directory.path().join("test.mrpack");
        let file = std::fs::File::create(&path).unwrap();
        let mut writer = zip::ZipWriter::new(file);
        let options = zip::write::SimpleFileOptions::default();
        writer.start_file("modrinth.index.json", options).unwrap();
        writer.write_all(manifest.as_bytes()).unwrap();
        for (name, bytes) in extras {
            writer.start_file(*name, options).unwrap();
            writer.write_all(bytes).unwrap();
        }
        writer.finish().unwrap();
        (directory, path)
    }

    fn expected_pack() -> CatalogModpack {
        let mut catalog = builtin_catalog().unwrap();
        catalog.packs.remove(0)
    }

    #[test]
    fn hash_validation_accepts_only_expected_lengths() {
        assert!(valid_hex(&"a".repeat(40), 40));
        assert!(valid_hex(&"F".repeat(128), 128));
        assert!(!valid_hex("not-a-hash", 40));
    }

    #[test]
    fn binaries_are_detected_case_insensitively() {
        assert!(is_binary_path("overrides/native/HELPER.EXE"));
        assert!(is_binary_path("overrides/native/libcef.dll"));
        assert!(!is_binary_path("mods/example.jar"));
    }

    #[test]
    fn valid_mrpack_passes_preflight() {
        let (_directory, path) = test_archive(
            &manifest("https://cdn.modrinth.com/data/a/example.jar", "43.5.2"),
            &[("overrides/config/example.toml", b"enabled=true")],
        );
        let result = validate_pack_sync(&path, &expected_pack()).unwrap();
        assert_eq!(result.manifest_files, 1);
        assert_eq!(result.archive_entries, 2);
    }

    #[test]
    fn mrpack_rejects_corrupt_manifest() {
        let (_directory, path) = test_archive("not-json", &[]);
        assert!(validate_pack_sync(&path, &expected_pack()).is_err());
    }

    #[test]
    fn mrpack_rejects_unsafe_path() {
        let (_directory, path) = test_archive(
            &manifest("https://cdn.modrinth.com/data/a/example.jar", "43.5.2"),
            &[("overrides/../escape.txt", b"unsafe")],
        );
        assert!(validate_pack_sync(&path, &expected_pack()).is_err());
    }

    #[test]
    fn mrpack_rejects_unknown_download_host() {
        let (_directory, path) = test_archive(
            &manifest("https://example.com/example.jar", "43.5.2"),
            &[],
        );
        assert!(validate_pack_sync(&path, &expected_pack()).is_err());
    }

    #[test]
    fn mrpack_rejects_undeclared_binary() {
        let (_directory, path) = test_archive(
            &manifest("https://cdn.modrinth.com/data/a/example.jar", "43.5.2"),
            &[("overrides/native/helper.exe", b"binary")],
        );
        assert!(validate_pack_sync(&path, &expected_pack()).is_err());
    }

    #[test]
    fn mrpack_rejects_wrong_forge_version() {
        let (_directory, path) = test_archive(
            &manifest("https://cdn.modrinth.com/data/a/example.jar", "43.5.1"),
            &[],
        );
        assert!(validate_pack_sync(&path, &expected_pack()).is_err());
    }

    #[test]
    fn http_errors_and_forbidden_redirects_are_rejected() {
        for status in [403, 404, 429, 500] {
            assert!(
                validate_download_response(
                    reqwest::StatusCode::from_u16(status).unwrap(),
                    "https://github.com/a/b",
                    Some(1),
                    1,
                )
                .is_err()
            );
        }
        assert!(
            validate_download_response(
                reqwest::StatusCode::OK,
                "https://example.com/redirected.mrpack",
                Some(1),
                1,
            )
            .is_err()
        );
    }

    #[test]
    fn http_response_requires_exact_content_length() {
        assert!(
            validate_download_response(
                reqwest::StatusCode::OK,
                "https://github.com/a/b",
                None,
                1,
            )
            .is_err()
        );
        assert!(
            validate_download_response(
                reqwest::StatusCode::OK,
                "https://github.com/a/b",
                Some(2),
                1,
            )
            .is_err()
        );
    }
}
