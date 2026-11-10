//! Native Horizon installation. Java is used only by Horizon's resource preparer.
use crate::event::emit::{emit_loading, init_or_edit_loading};
use crate::event::{LoadingBarId, LoadingBarType};
use crate::state::{
    Credentials, ProcessMetadata, Profile, ProfileInstallStage,
};
use crate::util::{fetch, io::IOError, rpc::RpcServerBuilder};
use crate::{ErrorKind, State};
use reqwest::Method;
use serde::{Deserialize, Serialize};
use serde_json::Value;
use sha2::{Digest, Sha256};
use std::path::{Component, Path, PathBuf};
use tokio::process::Command;

const API: &str = "https://api.github.com/repos/horizon-rust/horizon/releases";

#[cfg(all(target_os = "windows", target_arch = "x86_64"))]
mod mingw;
pub mod mods;

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct HorizonVersion {
    pub id: String,
    pub name: String,
    pub game_version: String,
    pub asset_name: String,
    pub download_url: String,
    pub sha256: String,
}

fn error(message: impl Into<String>) -> crate::Error {
    ErrorKind::LauncherError(message.into()).into()
}

fn asset_name() -> crate::Result<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("windows", "x86_64") => Ok("horizon-windows-x64.zip"),
        ("windows", "aarch64") => Ok("horizon-windows-arm64.zip"),
        ("macos", "x86_64") => Ok("horizon-macos-x64.zip"),
        ("macos", "aarch64") => Ok("horizon-macos-arm64.zip"),
        ("linux", "x86_64") => Ok("horizon-linux-x86_64.tar.gz"),
        _ => Err(error("Для этой платформы нет готовой сборки Horizon.")),
    }
}

async fn json(url: &str) -> crate::Result<Value> {
    let state = State::get().await?;
    fetch::fetch_json(
        Method::GET,
        url,
        None,
        None,
        &state.api_semaphore,
        &state.pool,
    )
    .await
}

/// Offer the current official nightly; the selected artifact's checksum pins its content.
pub async fn versions() -> crate::Result<Vec<HorizonVersion>> {
    let local = installed_versions().await?;
    if !local.is_empty()
        && selected_player()
            .await?
            .is_some_and(|(_, kind)| offline_account(&kind))
    {
        return Ok(local);
    }
    let remote = tokio::time::timeout(
        std::time::Duration::from_secs(if local.is_empty() { 20 } else { 3 }),
        async {
            let release = json(&format!("{API}/tags/nightly")).await?;
            release_version(&release).await
        },
    )
    .await;
    match remote {
        Ok(Ok(version)) => Ok(vec![version]),
        _ if !local.is_empty() => Ok(local),
        Ok(Err(err)) => Err(err),
        Err(_) => Err(error(
            "Не удалось загрузить версии Horizon. Для первой установки нужен интернет.",
        )),
    }
}

fn offline_account(kind: &str) -> bool {
    !kind.eq_ignore_ascii_case("microsoft")
}

// Read the selected identity without Credentials::get_active, which refreshes tokens over the network.
async fn selected_player() -> crate::Result<Option<(String, String)>> {
    let state = State::get().await?;
    Ok(sqlx::query_as("SELECT username, account_type FROM minecraft_users WHERE active = TRUE")
        .fetch_optional(&state.pool).await?)
}

fn valid_player_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 16
        && name.bytes().all(|c| c.is_ascii_alphanumeric() || c == b'_')
}

fn configure_offline_player(
    command: &mut Command,
    name: Option<&str>,
) -> crate::Result<()> {
    if let Some(name) = name {
        if !valid_player_name(name) {
            return Err(error(
                "Имя офлайн-игрока должно содержать 1–16 латинских букв, цифр или символов _.",
            ));
        }
        command.arg("--name").arg(name);
    }
    Ok(())
}

async fn prepared_version(root: &Path) -> Option<HorizonVersion> {
    let bytes = tokio::fs::read(root.join("blockera-ready.json"))
        .await
        .ok()?;
    let version: HorizonVersion = serde_json::from_slice(&bytes).ok()?;
    if version.game_version.contains(['/', '\\'])
        || safe_relative(&version.game_version).is_err()
        || !binary(root, "horizon-client").is_file()
        || !root
            .join(".local/horizon-client")
            .join(&version.game_version)
            .is_dir()
    {
        return None;
    }
    Some(version)
}

async fn installed_versions() -> crate::Result<Vec<HorizonVersion>> {
    let state = State::get().await?;
    let mut versions = Vec::<HorizonVersion>::new();
    for profile in crate::profile::list()
        .await?
        .into_iter()
        .filter(|p| p.loader == crate::state::ModLoader::Horizon)
    {
        if let Some(version) = prepared_version(
            &state
                .directories
                .profiles_dir()
                .join(&profile.path)
                .join(".horizon"),
        )
        .await
            && !versions
                .iter()
                .any(|v| v.id == version.id && v.sha256 == version.sha256)
        {
            versions.push(version);
        }
    }
    Ok(versions)
}

async fn reuse_installed(profile: &Profile) -> crate::Result<bool> {
    let home = crate::profile::get_full_path(&profile.path).await?;
    let root = home.join(".horizon");
    let state = State::get().await?;
    for candidate in
        std::iter::once(profile.clone()).chain(crate::profile::list().await?)
    {
        if candidate.loader != crate::state::ModLoader::Horizon
            || candidate.game_version != profile.game_version
        {
            continue;
        }
        let from = state
            .directories
            .profiles_dir()
            .join(&candidate.path)
            .join(".horizon");
        let Some(version) = prepared_version(&from).await else {
            continue;
        };
        if version.game_version != profile.game_version
            || profile.loader_version.as_deref() != Some(&version.id)
        {
            continue;
        }
        if candidate.path != profile.path {
            let staging = tempfile::tempdir_in(&home).map_err(IOError::from)?;
            let to = staging.path().join("runtime");
            let cloned = to.clone();
            tokio::task::spawn_blocking(move || {
                mods::clone_runtime(&from, &cloned)
            })
            .await
            .map_err(|e| error(e.to_string()))??;
            if root.exists() {
                tokio::fs::remove_dir_all(&root)
                    .await
                    .map_err(IOError::from)?;
            }
            tokio::fs::rename(to, &root).await.map_err(IOError::from)?;
        }
        crate::profile::edit(&profile.path, |p| {
            p.install_stage = ProfileInstallStage::Installed;
            async { Ok(()) }
        })
        .await?;
        return Ok(true);
    }
    Ok(false)
}

async fn release_version(release: &Value) -> crate::Result<HorizonVersion> {
    let name = asset_name()?;
    let assets = release["assets"]
        .as_array()
        .ok_or_else(|| error("У Horizon нет файлов релиза."))?;
    let asset = assets.iter().find(|a| a["name"] == name).ok_or_else(|| {
        error("В релизе Horizon нет сборки для этой платформы.")
    })?;
    let url = asset["browser_download_url"]
        .as_str()
        .ok_or_else(|| error("Нет адреса сборки Horizon."))?;
    let state = State::get().await?;
    let sums_url = assets
        .iter()
        .find(|a| a["name"] == "SHA256SUMS")
        .and_then(|a| a["browser_download_url"].as_str())
        .ok_or_else(|| {
            error("В релизе Horizon отсутствуют контрольные суммы.")
        })?;
    let sums =
        fetch::fetch(sums_url, None, &state.api_semaphore, &state.pool).await?;
    let sha256 = checksum(&String::from_utf8_lossy(&sums), name)?;
    let tag = release["tag_name"]
        .as_str()
        .ok_or_else(|| error("Нет тега Horizon."))?;
    // This is descriptive only; the lock shipped in the checked archive is authoritative.
    let lock = json(&format!("https://raw.githubusercontent.com/horizon-rust/horizon/{tag}/research-lock.json")).await?;
    let game_version = lock["minecraft"]["version"]
        .as_str()
        .ok_or_else(|| error("Нет версии Minecraft в Horizon."))?;
    safe_relative(game_version)?;
    Ok(HorizonVersion {
        id: release["id"]
            .as_u64()
            .ok_or_else(|| error("Нет идентификатора релиза Horizon."))?
            .to_string(),
        name: release["name"].as_str().unwrap_or(tag).to_owned(),
        game_version: game_version.to_owned(),
        asset_name: name.to_owned(),
        download_url: url.to_owned(),
        sha256,
    })
}

fn checksum(sums: &str, name: &str) -> crate::Result<String> {
    sums.lines()
        .find_map(|line| {
            let mut words = line.split_whitespace();
            let hash = words.next()?;
            let file = words.next()?.trim_start_matches('*');
            (file == name
                && hash.len() == 64
                && hash.bytes().all(|c| c.is_ascii_hexdigit()))
            .then(|| hash.to_ascii_lowercase())
        })
        .ok_or_else(|| error("Не найдена контрольная сумма сборки Horizon."))
}

fn safe_relative(name: &str) -> crate::Result<PathBuf> {
    // Reject Windows paths even when the archive is being read on Unix.
    if name.contains(['\\', ':']) || name.starts_with('/') {
        return Err(error("Недопустимый путь в архиве Horizon."));
    }
    if name
        .split('/')
        .any(|part| part != "." && (part.ends_with(' ') || part.ends_with('.')))
    {
        return Err(error("Недопустимый путь в архиве Horizon."));
    }
    let path = PathBuf::from(name);
    if path.as_os_str().is_empty()
        || path
            .components()
            .any(|c| !matches!(c, Component::Normal(_) | Component::CurDir))
    {
        return Err(error("Недопустимый путь в архиве Horizon."));
    }
    Ok(path)
}

fn binary(root: &Path, name: &str) -> PathBuf {
    root.join(format!("{name}{}", std::env::consts::EXE_SUFFIX))
}

/// Revalidate ownership with Mojang; alternate/offline accounts cannot prepare or start Horizon.
fn validate_account_type(account_type: &str) -> crate::Result<()> {
    if !account_type.eq_ignore_ascii_case("microsoft") {
        return Err(error(
            "Horizon требует Microsoft-аккаунт с Minecraft: Java Edition.",
        ));
    }
    Ok(())
}

async fn owner() -> crate::Result<Credentials> {
    let state = State::get().await?;
    let credentials = Credentials::get_default_credential(&state.pool).await?
        .ok_or_else(|| error("Войдите в Microsoft-аккаунт с Minecraft: Java Edition для Horizon."))?;
    validate_account_type(&credentials.account_type)?;
    let response = reqwest::Client::new()
        .get("https://api.minecraftservices.com/minecraft/profile")
        .timeout(std::time::Duration::from_secs(30))
        .bearer_auth(&credentials.access_token)
        .send()
        .await?;
    if !response.status().is_success() {
        return Err(error(
            "Не удалось подтвердить владение Minecraft: Java Edition. Войдите в Microsoft-аккаунт повторно.",
        ));
    }
    let profile: Value = response.json().await?;
    if profile["id"].as_str()
        != Some(&credentials.offline_profile.id.simple().to_string())
    {
        return Err(error(
            "Профиль Microsoft не совпадает с выбранным аккаунтом.",
        ));
    }
    Ok(credentials)
}

async fn extract(archive: &Path, root: &Path, zip: bool) -> crate::Result<()> {
    if zip {
        let reader =
            async_zip::tokio::read::fs::ZipFileReader::new(archive).await?;
        for (i, entry) in reader.file().entries().iter().enumerate() {
            let name = entry.filename().as_str()?;
            let target = root.join(safe_relative(name)?);
            if name.ends_with('/') {
                tokio::fs::create_dir_all(&target)
                    .await
                    .map_err(IOError::from)?;
                continue;
            }
            if entry
                .unix_permissions()
                .is_some_and(|m| m & 0o170000 == 0o120000)
            {
                return Err(error(
                    "Символьные ссылки в архиве Horizon не поддерживаются.",
                ));
            }
            let mut bytes = Vec::new();
            reader
                .reader_with_entry(i)
                .await?
                .read_to_end_checked(&mut bytes)
                .await?;
            if let Some(parent) = target.parent() {
                tokio::fs::create_dir_all(parent)
                    .await
                    .map_err(IOError::from)?;
            }
            tokio::fs::write(&target, bytes)
                .await
                .map_err(IOError::from)?;
            #[cfg(unix)]
            if let Some(mode) = entry.unix_permissions() {
                use std::os::unix::fs::PermissionsExt;
                tokio::fs::set_permissions(
                    &target,
                    std::fs::Permissions::from_mode(mode & 0o777),
                )
                .await
                .map_err(IOError::from)?;
            }
        }
    } else {
        let archive = archive.to_owned();
        let root = root.to_owned();
        tokio::task::spawn_blocking(move || -> crate::Result<()> {
            let gzip = flate2::read::GzDecoder::new(
                std::fs::File::open(archive).map_err(IOError::from)?,
            );
            for entry in
                tar::Archive::new(gzip).entries().map_err(IOError::from)?
            {
                let mut entry = entry.map_err(IOError::from)?;
                let path = entry.path().map_err(IOError::from)?.into_owned();
                safe_relative(&path.to_string_lossy())?;
                if !(entry.header().entry_type().is_file()
                    || entry.header().entry_type().is_dir())
                {
                    return Err(error(
                        "Недопустимый тип файла в архиве Horizon.",
                    ));
                }
                if !entry.unpack_in(&root).map_err(IOError::from)? {
                    return Err(error("Недопустимый путь в архиве Horizon."));
                }
            }
            Ok(())
        })
        .await
        .map_err(|e| error(e.to_string()))??;
    }
    Ok(())
}

pub async fn install(
    profile: &Profile,
    bar: Option<LoadingBarId>,
    repairing: bool,
) -> crate::Result<()> {
    if !crate::process::get_by_profile_path(&profile.path)
        .await?
        .is_empty()
    {
        return Err(error(
            "Остановите Horizon перед восстановлением установки.",
        ));
    }
    let state = State::get().await?;
    let home = crate::profile::get_full_path(&profile.path).await?;
    if mods::is_busy(&home) {
        return Err(error("Дождитесь завершения операции с модами Horizon."));
    }
    if !repairing && reuse_installed(profile).await? {
        return Ok(());
    }
    if selected_player()
        .await?
        .is_none_or(|(_, kind)| offline_account(&kind))
    {
        return Err(error(
            "Эта версия Horizon ещё не установлена. Для первой загрузки войдите в Microsoft-аккаунт; затем её можно запускать офлайн.",
        ));
    }
    owner().await?;
    let root = home.join(".horizon");
    let marker = root.join("blockera-ready.json");
    let bar = init_or_edit_loading(
        bar,
        LoadingBarType::MinecraftDownload {
            profile_name: profile.name.clone(),
            profile_path: profile.path.clone(),
        },
        // Minecraft's downloader contributes about 100 units; preparation finishes afterward.
        200.0,
        "Установка Horizon",
    )
    .await?;
    crate::profile::edit(&profile.path, |p| {
        p.install_stage = ProfileInstallStage::MinecraftInstalling;
        async { Ok(()) }
    })
    .await?;
    let result = crate::event::download_control::run(bar.uuid(), async {
        if marker.is_file() && binary(&root, "horizon-client").is_file()
            && root.join(".local/horizon-client").join(&profile.game_version).is_dir() && !repairing {
            return Ok(());
        }
        let release_id = profile.loader_version.as_deref().ok_or_else(|| error("Выберите сборку Horizon."))?;
        if !release_id.bytes().all(|c| c.is_ascii_digit()) || release_id.is_empty() {
            return Err(error("Некорректный идентификатор сборки Horizon."));
        }
        let release = json(&format!("{API}/{release_id}")).await?;
        let version = release_version(&release).await?;
        if version.game_version != profile.game_version {
            return Err(error("Версия Minecraft в релизе Horizon изменилась. Создайте сборку заново."));
        }
        emit_loading(&bar, 0.0, Some("Загрузка нативного клиента Horizon"))?;
        let bytes = fetch::fetch(&version.download_url, None, &state.api_semaphore, &state.pool).await?;
        if format!("{:x}", Sha256::digest(&bytes)) != version.sha256 {
            return Err(error("Контрольная сумма Horizon не совпадает. Установка остановлена."));
        }
        // A fresh staging folder avoids following files/links left by an earlier install.
        let staging = tempfile::tempdir_in(&home).map_err(IOError::from)?;
        let archive = staging.path().join("download");
        tokio::fs::write(&archive, bytes).await.map_err(IOError::from)?;
        let unpacked = staging.path().join("runtime");
        tokio::fs::create_dir_all(&unpacked).await.map_err(IOError::from)?;
        extract(&archive, &unpacked, version.asset_name.ends_with(".zip")).await?;
        let mut entries = tokio::fs::read_dir(&unpacked).await.map_err(IOError::from)?;
        let first = entries.next_entry().await.map_err(IOError::from)?;
        let second = entries.next_entry().await.map_err(IOError::from)?;
        let top = match (first, second) {
            (Some(entry), None) if entry.path().is_dir() => entry.path(),
            _ => unpacked,
        };
        let lock: Value = serde_json::from_slice(&tokio::fs::read(top.join("research-lock.json")).await.map_err(IOError::from)?)?;
        if lock["minecraft"]["version"] != profile.game_version {
            return Err(error("Архив Horizon предназначен для другой версии Minecraft."));
        }
        if !binary(&top, "horizon-client").is_file() || !binary(&top, "horizon-prepare").is_file() {
            return Err(error("Архив Horizon не содержит клиент или утилиту подготовки ресурсов."));
        }
        if root.exists() {
            // Only this profile's dedicated runtime is replaced; worlds stay in the profile home.
            tokio::fs::remove_dir_all(&root).await.map_err(IOError::from)?;
        }
        tokio::fs::rename(&top, &root).await.map_err(IOError::from)?;
        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            for program in ["horizon-client", "horizon-prepare"] {
                tokio::fs::set_permissions(binary(&root, program), std::fs::Permissions::from_mode(0o755)).await.map_err(IOError::from)?;
            }
        }
        emit_loading(&bar, 25.0, Some("Подготовка файлов Minecraft для Horizon"))?;
        // The Java launcher's serialized metadata is not byte-for-byte Mojang's JSON.
        // Horizon checks the original bytes against its lock, so keep a separate pinned copy.
        let manifest_sha1 = lock["minecraft"]["manifest_sha1"].as_str()
            .filter(|s| s.len() == 40 && s.bytes().all(|c| c.is_ascii_hexdigit()))
            .ok_or_else(|| error("Некорректный хеш метаданных Minecraft в Horizon."))?;
        let manifest_url = format!("https://piston-meta.mojang.com/v1/packages/{manifest_sha1}/{}.json", profile.game_version);
        let original = fetch::fetch(&manifest_url, Some(manifest_sha1), &state.api_semaphore, &state.pool).await?;
        let original_info: daedalus::minecraft::VersionInfo = serde_json::from_slice(&original)?;
        let major = lock["minecraft"]["java_major"].as_u64().unwrap_or(25) as u32;
        let installed = crate::jre::get_java_versions().await?;
        let java = if let Some(java) = installed.get(&major) {
            PathBuf::from(&java.path)
        } else {
            crate::jre::auto_install_java(major).await?
        };
        let java_version = crate::jre::check_jre(java.clone()).await?;
        if java_version.parsed_version != major {
            return Err(error("Версия Java не подходит для подготовки Horizon."));
        }
        java_version.upsert(&state.pool).await?;
        crate::launcher::download::download_minecraft(&state, &original_info, &bar,
            &java_version.architecture, repairing, true).await?;
        let original_path = root.join("mojang-version.json");
        tokio::fs::write(&original_path, original).await.map_err(IOError::from)?;
        let asset_index = &original_info.asset_index;
        safe_relative(&asset_index.id)?;
        let index_bytes = fetch::fetch(&asset_index.url, Some(&asset_index.sha1), &state.api_semaphore, &state.pool).await?;
        tokio::fs::write(state.directories.assets_index_dir().join(format!("{}.json", asset_index.id)), index_bytes).await.map_err(IOError::from)?;
        let server = original_info.downloads.get(&daedalus::minecraft::DownloadType::Server)
            .ok_or_else(|| error("Отсутствует серверный jar для подготовки Horizon."))?;
        let server_bytes = fetch::fetch(&server.url, Some(&server.sha1), &state.api_semaphore, &state.pool).await?;
        let server_path = root.join("server.jar");
        tokio::fs::write(&server_path, server_bytes).await.map_err(IOError::from)?;
        let java_home = java.parent().and_then(Path::parent).ok_or_else(|| error("Не найдена папка Java для Horizon."))?;
        emit_loading(&bar, 35.0, Some("Подготовка ресурсов Horizon — первый запуск может занять несколько минут"))?;
        let mut prepare = Command::new(binary(&root, "horizon-prepare"));
        prepare.current_dir(&root).env("JAVA_HOME", java_home)
            .arg("--root").arg(&root).arg("--prepare")
            .arg("--server-jar").arg(&server_path)
            .arg("--version-json").arg(&original_path)
            .arg("--minecraft-dir").arg(state.directories.metadata_dir());
        run_preparer(prepare, &home).await?;
        for kind in ["sounds", "packs", "panorama", "fonts", "languages"] {
            let mut prepare = Command::new(binary(&root, "horizon-prepare"));
            prepare.current_dir(&root).env("JAVA_HOME", java_home).arg(kind)
                .arg("--version-json").arg(&original_path)
                .arg("--root").arg(&root).arg("--minecraft-dir").arg(state.directories.metadata_dir());
            run_preparer(prepare, &home).await?;
        }
        let mut check = Command::new(binary(&root, "horizon-prepare"));
        check.current_dir(&root).arg("--check").arg("--root").arg(&root);
        run_preparer(check, &home).await?;
        tokio::fs::write(&marker, serde_json::to_vec(&version)?).await.map_err(IOError::from)?;
        if home.join(".horizon-manager").is_dir() {
            tokio::fs::write(home.join(".horizon-manager/native-dirty"), "").await.map_err(IOError::from)?;
        }
        Ok(())
    }).await;
    let success = result.is_ok();
    crate::profile::edit(&profile.path, |p| {
        p.install_stage = if success {
            ProfileInstallStage::Installed
        } else {
            ProfileInstallStage::NotInstalled
        };
        async { Ok(()) }
    })
    .await?;
    result?;
    emit_loading(&bar, 100.0, Some("Horizon установлен"))?;
    Ok(())
}

async fn run_preparer(mut command: Command, home: &Path) -> crate::Result<()> {
    let logs = home.join("logs");
    tokio::fs::create_dir_all(&logs)
        .await
        .map_err(IOError::from)?;
    let log = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(logs.join("horizon-prepare.log"))
        .map_err(IOError::from)?;
    command
        .stdout(log.try_clone().map_err(IOError::from)?)
        .stderr(log);
    #[cfg(windows)]
    command.creation_flags(0x08000000);
    let status = command.status().await.map_err(IOError::from)?;
    if !status.success() {
        return Err(error(
            "Не удалось подготовить ресурсы Horizon. Подробности: logs/horizon-prepare.log в папке сборки.",
        ));
    }
    Ok(())
}

pub async fn launch(
    profile: &Profile,
    quick_play: crate::profile::QuickPlayType,
) -> crate::Result<ProcessMetadata> {
    launch_mode(profile, quick_play, false).await
}

pub async fn launch_offline(path: &str) -> crate::Result<ProcessMetadata> {
    let profile = crate::profile::get(path)
        .await?
        .ok_or_else(|| error("Сборка не найдена."))?;
    if profile.loader != crate::state::ModLoader::Horizon {
        return Err(error("Офлайн-запуск доступен здесь только для Horizon."));
    }
    launch_mode(&profile, crate::profile::QuickPlayType::None, true).await
}

async fn launch_mode(
    profile: &Profile,
    quick_play: crate::profile::QuickPlayType,
    force_offline: bool,
) -> crate::Result<ProcessMetadata> {
    if profile.install_stage == ProfileInstallStage::MinecraftInstalling {
        return Err(error("Дождитесь завершения установки Horizon."));
    }
    let state = State::get().await?;
    if !crate::process::get_by_profile_path(&profile.path)
        .await?
        .is_empty()
    {
        return Err(error("Эта сборка Horizon уже запущена."));
    }
    if profile.install_stage != ProfileInstallStage::Installed {
        if force_offline {
            if !reuse_installed(profile).await? {
                return Err(error(
                    "Для офлайн-запуска сначала установите эту версию Horizon с интернетом.",
                ));
            }
        } else {
            install(profile, None, false).await?;
        }
    }
    let selected = selected_player().await?;
    let offline = force_offline
        || selected
            .as_ref()
            .is_none_or(|(_, kind)| offline_account(kind));
    let credentials = if offline { None } else { Some(owner().await?) };
    let home = crate::profile::get_full_path(&profile.path).await?;
    let root = mods::launch_runtime(&home).await?;
    let session_dir = tempfile::tempdir().map_err(IOError::from)?;
    let session = session_dir.path().join("session.json");
    // The game reads and deletes this short-lived file; its path is the only command argument.
    let mut command = Command::new(binary(&root, "horizon-client"));
    command
        .current_dir(&home)
        .env("HORIZON_HOME", &home)
        .env_remove("HORIZON_ACCOUNT_SESSIONS")
        .env_remove("HORIZON_LAUNCHER_EXE")
        .env_remove("HORIZON_PLAYER_NAME")
        .arg("--resources")
        .arg(
            root.join(".local/horizon-client")
                .join(&profile.game_version),
        );
    if let Some(credentials) = credentials {
        let mut file = std::fs::OpenOptions::new();
        file.write(true).create_new(true);
        #[cfg(unix)]
        {
            use std::os::unix::fs::OpenOptionsExt;
            file.mode(0o600);
        }
        let mut file = file.open(&session).map_err(IOError::from)?;
        use std::io::Write;
        file.write_all(
            serde_json::to_string(&serde_json::json!({
                "name": credentials.offline_profile.name,
                "uuid": credentials.offline_profile.id.simple().to_string(),
                "access_token": credentials.access_token,
                "user_type": "msa", "xuid": "",
            }))?
            .as_bytes(),
        )
        .map_err(IOError::from)?;
        drop(file);
        command.arg("--session-file").arg(&session);
    } else if let Some((name, _)) = selected {
        configure_offline_player(&mut command, Some(&name))?;
    }
    match quick_play {
        crate::profile::QuickPlayType::None => {}
        crate::profile::QuickPlayType::Singleplayer(world) => {
            command.arg("--world").arg(world);
        }
        crate::profile::QuickPlayType::Server(server) => {
            command.arg("--server").arg(server.to_string());
        }
    }
    #[cfg(windows)]
    command.creation_flags(0x08000000);
    crate::profile::edit(&profile.path, |p| {
        p.last_played = Some(chrono::Utc::now());
        async { Ok(()) }
    })
    .await?;
    let metadata = state
        .process_manager
        .insert_new_process(
            &profile.path,
            command,
            None,
            state.directories.profile_logs_dir(&profile.path),
            false,
            session_dir,
            None,
            RpcServerBuilder::new().launch().await?,
            async |_, _| Ok(()),
        )
        .await?;
    tokio::spawn(async move {
        tokio::time::sleep(std::time::Duration::from_secs(120)).await;
        let _ = tokio::fs::remove_file(session).await;
    });
    Ok(metadata)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn local_accounts_use_offline_mode_and_only_microsoft_uses_online_auth() {
        assert!(offline_account(
            &crate::state::AccountType::Pirate.as_lowercase_str()
        ));
        assert!(offline_account(
            &crate::state::AccountType::ElyBy.as_lowercase_str()
        ));
        assert!(!offline_account("microsoft"));
        assert!(!offline_account("Microsoft"));
    }

    #[test]
    fn offline_command_passes_player_name_without_a_session_or_token() {
        let mut command = Command::new("horizon-client");
        configure_offline_player(&mut command, Some("alexdefult")).unwrap();
        let args: Vec<_> = command.as_std().get_args().collect();
        assert_eq!(args, ["--name", "alexdefult"]);
        assert!(
            configure_offline_player(&mut command, Some("bad\nname")).is_err()
        );
        assert!(
            configure_offline_player(&mut command, Some("NameTooLongForGame"))
                .is_err()
        );
    }

    #[tokio::test]
    async fn cached_runtime_requires_completed_resources_and_reads_local_metadata()
     {
        let temp = tempfile::tempdir().unwrap();
        let root = temp.path();
        let descriptor = serde_json::json!({"id":"123", "name":"Nightly 2026-11-05 · 66f3981", "game_version":"26.3", "asset_name":"fixture.zip", "download_url":"https://example.invalid", "sha256":"fixture"});
        tokio::fs::write(
            root.join("blockera-ready.json"),
            serde_json::to_vec(&descriptor).unwrap(),
        )
        .await
        .unwrap();
        assert!(prepared_version(root).await.is_none());
        tokio::fs::write(binary(root, "horizon-client"), "fixture")
            .await
            .unwrap();
        tokio::fs::create_dir_all(root.join(".local/horizon-client/26.3"))
            .await
            .unwrap();
        let cached = prepared_version(root).await.unwrap();
        assert_eq!(cached.id, "123");
        assert_eq!(cached.game_version, "26.3");
    }

    #[test]
    fn accepts_microsoft_login_storage_format_and_legacy_casing() {
        let stored_type =
            crate::state::AccountType::Microsoft.as_lowercase_str();
        assert_eq!(stored_type, "microsoft");
        assert!(validate_account_type(&stored_type).is_ok());
        assert!(validate_account_type("Microsoft").is_ok());
        for account_type in [
            crate::state::AccountType::Pirate.as_lowercase_str(),
            crate::state::AccountType::ElyBy.as_lowercase_str(),
            crate::state::AccountType::Unknown.as_lowercase_str(),
            String::new(),
        ] {
            assert!(validate_account_type(&account_type).is_err());
        }
    }

    #[test]
    fn rejects_archive_escape_paths_on_all_platforms() {
        for path in [
            "../outside",
            "/outside",
            "C:/outside",
            "dir/../../outside",
            "dir\\..\\outside",
            "dir/.. /outside",
            "dir/.../outside",
            "",
        ] {
            assert!(safe_relative(path).is_err(), "{path}");
        }
        assert!(safe_relative("./crates/horizon-prepare/src/lib.rs").is_ok());
    }

    #[test]
    fn checksum_matches_exact_artifact_and_requires_sha256() {
        let hash = "a".repeat(64);
        let sums = format!(
            "{hash}  horizon-windows-x64.zip\n{hash} *horizon-macos-x64.zip\n"
        );
        assert_eq!(checksum(&sums, "horizon-windows-x64.zip").unwrap(), hash);
        assert!(checksum(&sums, "horizon-windows-arm64.zip").is_err());
        assert!(
            checksum("abc  horizon-windows-x64.zip", "horizon-windows-x64.zip")
                .is_err()
        );
    }

    #[tokio::test]
    async fn extracts_files_and_rejects_zip_traversal() {
        for name in ["horizon/test.txt", "../escaped.txt"] {
            let temp = tempfile::tempdir().unwrap();
            let archive = temp.path().join("test.zip");
            let mut file = tokio::fs::File::create(&archive).await.unwrap();
            let mut writer =
                async_zip::tokio::write::ZipFileWriter::with_tokio(&mut file);
            writer
                .write_entry_whole(
                    async_zip::ZipEntryBuilder::new(
                        name.into(),
                        async_zip::Compression::Stored,
                    ),
                    b"fixture",
                )
                .await
                .unwrap();
            writer.close().await.unwrap();
            drop(file);
            let target = temp.path().join("out");
            tokio::fs::create_dir_all(&target).await.unwrap();
            let result = extract(&archive, &target, true).await;
            if name.starts_with("../") {
                assert!(result.is_err());
                assert!(!temp.path().join("escaped.txt").exists());
            } else {
                result.unwrap();
                assert_eq!(
                    tokio::fs::read(target.join(name)).await.unwrap(),
                    b"fixture"
                );
            }
        }
    }
}
