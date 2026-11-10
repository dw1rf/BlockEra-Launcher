//! Horizon's official CLI verifies registry files and builds native mods; BlockEra owns the UI
//! and supplies its existing Microsoft session when starting the resulting game.
use super::{API, binary, error, extract, json};
use crate::state::{ModLoader, Profile};
use crate::util::{fetch, io::IOError};
use crate::{
    State,
    event::{
        LoadingBarType,
        emit::{emit_loading, init_loading},
    },
};
use serde_json::{Value, json as value};
use sha2::{Digest, Sha256};
use std::io::Read;
use std::path::{Path, PathBuf};
use tokio::process::Command;

const INDEX: &str = "https://horizon-rust.github.io/horizon-mods/index.json";
const ICON_ROOT: &str = "https://horizon-rust.github.io/horizon-mods/icons/";
const HELPER_VERSION: &str = "0.2.58";
const MANAGER: &str = ".horizon-manager";
static MUTATION: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());
static BUSY: std::sync::LazyLock<dashmap::DashSet<PathBuf>> =
    std::sync::LazyLock::new(dashmap::DashSet::new);

pub(super) fn is_busy(home: &Path) -> bool {
    BUSY.contains(home)
}

async fn profile(path: &str) -> crate::Result<Profile> {
    let profile = crate::profile::get(path)
        .await?
        .ok_or_else(|| error("Сборка не найдена."))?;
    if profile.loader != ModLoader::Horizon {
        return Err(error(
            "Каталог Horizon доступен только для сборок Horizon.",
        ));
    }
    Ok(profile)
}

fn valid_id(id: &str) -> bool {
    !id.is_empty()
        && id.len() <= 64
        && id
            .bytes()
            .all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == b'_')
}

fn valid_version(version: &str) -> bool {
    !version.is_empty()
        && version != "."
        && version != ".."
        && version.len() <= 100
        && version
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || c == b'-' || c == b'.')
}

async fn read_json(path: &Path) -> crate::Result<Value> {
    Ok(serde_json::from_slice(
        &tokio::fs::read(path).await.map_err(IOError::from)?,
    )?)
}

async fn write_json(path: &Path, data: &Value) -> crate::Result<()> {
    let pending = path.with_extension("json.pending");
    tokio::fs::write(&pending, serde_json::to_vec_pretty(data)?)
        .await
        .map_err(IOError::from)?;
    tokio::fs::rename(pending, path)
        .await
        .map_err(IOError::from)?;
    Ok(())
}

pub async fn catalog(path: &str) -> crate::Result<Value> {
    let profile = profile(path).await?;
    let index = json(INDEX).await?;
    if index["schema"] != 1 {
        return Err(error(
            "Обновите BlockEra: формат каталога Horizon изменился.",
        ));
    }
    let projects: Vec<Value> = index["projects"].as_array().ok_or_else(|| error("Неверный каталог Horizon."))?
        .iter().filter(|p| matches!(p["kind"].as_str(), Some("mod" | "native-mod")))
        .filter_map(|project| {
            let version = project["versions"].as_array()?.iter().find(|v| compatible(project, v, &profile.game_version))?;
            let revoked = index["revoked"].as_array().is_some_and(|entries| entries.iter().any(|r|
                r["id"] == project["id"] && (r["sha256"].is_null() || r["sha256"] == version["file"]["sha256"])));
            if revoked { return None; }
            Some(value!({ "id": project["id"], "name": project["name"], "summary": project["summary"],
                "authors": project["authors"], "source": project["source"], "kind": project["kind"],
                "icon": registry_icon(project),
                "description": project["description"],
                "version": version["version"], "size": version["file"]["size"], "signed": project.get("key").is_some(),
                "license": project["license"], "categories": project["categories"] }))
        }).collect();
    Ok(value!({ "projects": projects, "generated": index["generated"] }))
}

fn registry_icon(project: &Value) -> Option<&str> {
    let id = project["id"].as_str().filter(|id| valid_id(id))?;
    let icon = project["icon"].as_str()?;
    (icon.strip_prefix(ICON_ROOT)?.strip_suffix(".png")? == id).then_some(icon)
}

fn compatible(project: &Value, version: &Value, game: &str) -> bool {
    version["game"] == game
        && (project["kind"] != "mod"
            || version["capabilities"]
                .as_array()
                .is_some_and(|capabilities| {
                    capabilities
                        .iter()
                        .all(|capability| capability == "registries")
                }))
}

fn enabled(settings: &str, id: &str) -> bool {
    settings
        .lines()
        .rev()
        .find_map(|line| {
            line.trim()
                .split_once(':')
                .filter(|(key, _)| *key == format!("mod.{id}"))
                .map(|(_, value)| value.trim() == "true")
        })
        .unwrap_or(false)
}

async fn installed(home: &Path) -> crate::Result<Vec<Value>> {
    let manifest = home.join("mod-browser.json");
    let entries = if manifest.is_file() {
        read_json(&manifest).await?
    } else {
        value!({"entries": []})
    };
    let settings = tokio::fs::read_to_string(home.join("horizon-mods.txt"))
        .await
        .unwrap_or_default();
    Ok(entries["entries"].as_array().ok_or_else(|| error("Не удалось прочитать установленные моды Horizon."))?
        .iter().filter(|e| e["source"] == "horizon")
        .filter_map(|e| {
            let id = e["slug"].as_str()?;
            if !valid_id(id) { return None; }
            let native = e["file"].as_str()?.starts_with("horizon-native-mods/");
            Some(value!({ "id": id, "name": e["title"], "version": e["number"], "file": e["file"],
                "kind": if native {"native-mod"} else {"mod"}, "enabled": enabled(&settings, id),
                "checksum": e["checksum"], "source": e["page"] }))
        }).collect())
}

async fn keep_native_versions(
    home: &Path,
    update_id: Option<&str>,
) -> crate::Result<()> {
    let path = home.join("mod-browser/chosen-versions.json");
    let mut versions: std::collections::BTreeMap<String, String> =
        match tokio::fs::read(&path).await {
            Ok(bytes) => serde_json::from_slice(&bytes)?,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                Default::default()
            }
            Err(e) => return Err(IOError::from(e).into()),
        };
    let previous = versions.clone();
    if let Some(id) = update_id {
        versions.remove(id);
    } else {
        // The player approved the installed versions. Prevent the CLI from
        // substituting other packages while it tries to fit mods to the game.
        for entry in installed(home).await? {
            if entry["kind"] == "native-mod"
                && entry["enabled"] == true
                && let (Some(id), Some(version)) =
                    (entry["id"].as_str(), entry["version"].as_str())
            {
                versions.insert(id.to_owned(), version.to_owned());
            }
        }
    }
    if versions != previous {
        tokio::fs::create_dir_all(home.join("mod-browser"))
            .await
            .map_err(IOError::from)?;
        write_json(&path, &value!(versions)).await?;
    }
    Ok(())
}

fn native_mod_revision(
    home: &Path,
    entry: &Value,
    game: &str,
) -> Option<String> {
    let id = entry["id"].as_str()?;
    let path = home.join(format!("horizon-native-mods/{id}.hnmod"));
    let mut bundle =
        zip::ZipArchive::new(std::fs::File::open(path).ok()?).ok()?;
    let manifest = bundle.by_name("horizon-native.json").ok()?;
    let mut bytes = Vec::new();
    manifest.take(65_537).read_to_end(&mut bytes).ok()?;
    if bytes.len() > 65_536 {
        return None;
    }
    let manifest: Value = serde_json::from_slice(&bytes).ok()?;
    let revision = manifest["horizon"].as_str()?;
    (manifest["id"] == id
        && manifest["game"] == game
        && revision.len() == 40
        && revision.bytes().all(|b| b.is_ascii_hexdigit()))
    .then(|| revision.to_ascii_lowercase())
}

async fn shared_native_revision(
    home: &Path,
    game: &str,
) -> crate::Result<Option<String>> {
    let entries = installed(home).await?;
    let home = home.to_owned();
    let game = game.to_owned();
    tokio::task::spawn_blocking(move || {
        let mut revisions = entries
            .iter()
            .filter(|e| e["kind"] == "native-mod" && e["enabled"] == true)
            .map(|e| native_mod_revision(&home, e, &game));
        let revision = revisions.next()??;
        revisions
            .all(|other| other.as_ref() == Some(&revision))
            .then_some(revision)
    })
    .await
    .map_err(|e| error(e.to_string()))
}

async fn selected(home: &Path) -> crate::Result<Option<String>> {
    let file = home.join(MANAGER).join("instances.json");
    if !file.is_file() {
        return Ok(None);
    }
    let data = read_json(&file).await?;
    let version = data["instances"]["horizon"]["version"]
        .as_str()
        .map(str::to_owned);
    if version.as_deref().is_some_and(|v| !valid_version(v)) {
        return Err(error("Некорректная версия нативной сборки Horizon."));
    }
    Ok(version)
}

pub async fn status(path: &str) -> crate::Result<Value> {
    profile(path).await?;
    let home = crate::profile::get_full_path(path).await?;
    let entries = installed(&home).await?;
    let native_enabled = entries
        .iter()
        .filter(|e| e["kind"] == "native-mod" && e["enabled"] == true)
        .count();
    let selected = selected(&home).await?;
    let built = selected.as_deref().is_some_and(|v| {
        v.starts_with("native-")
            && binary(
                &home.join(MANAGER).join("versions").join(v),
                "horizon-client",
            )
            .is_file()
    });
    Ok(
        value!({ "installed": entries, "native_enabled": native_enabled,
        "needs_build": native_enabled > 0 && (!built || home.join(MANAGER).join("native-dirty").exists()),
        "built_version": selected, "busy": BUSY.contains(&home) }),
    )
}

pub(super) async fn launch_runtime(home: &Path) -> crate::Result<PathBuf> {
    if BUSY.contains(home) {
        return Err(error("Дождитесь завершения операции с модами Horizon."));
    }
    let native = installed(home)
        .await?
        .iter()
        .any(|e| e["kind"] == "native-mod" && e["enabled"] == true);
    if native {
        if home.join(MANAGER).join("native-dirty").exists() {
            return Err(error(
                "Моды Horizon изменились. Откройте «Контент» и соберите клиент с модами.",
            ));
        }
        if let Some(version) =
            selected(home).await?.filter(|v| v.starts_with("native-"))
        {
            let root = home.join(MANAGER).join("versions").join(version);
            if binary(&root, "horizon-client").is_file() {
                return Ok(root);
            }
        }
        return Err(error(
            "Сначала соберите клиент с нативными модами в разделе «Контент».",
        ));
    }
    Ok(home.join(".horizon"))
}

async fn helper() -> crate::Result<PathBuf> {
    let state = State::get().await?;
    let root = state
        .directories
        .caches_dir()
        .join("horizon-mod-tools")
        .join(HELPER_VERSION);
    let marker = root.join("blockera-helper.json");
    if marker.is_file() {
        let stored = read_json(&marker).await?;
        if let Some(path) = stored["binary"].as_str() {
            let path = root.join(super::safe_relative(path)?);
            if path.is_file() {
                return Ok(path);
            }
        }
    }
    let name =
        super::asset_name()?.replacen("horizon-", "horizon-launcher-", 1);
    let release =
        json(&format!("{API}/tags/launcher%40{HELPER_VERSION}")).await?;
    let asset = release["assets"]
        .as_array()
        .and_then(|a| a.iter().find(|a| a["name"] == name))
        .ok_or_else(|| error("Нет инструментов Horizon для этой платформы."))?;
    let digest = asset["digest"]
        .as_str()
        .and_then(|d| d.strip_prefix("sha256:"))
        .filter(|d| d.len() == 64 && d.bytes().all(|b| b.is_ascii_hexdigit()))
        .ok_or_else(|| error("Нет контрольной суммы инструментов Horizon."))?;
    let url = asset["browser_download_url"]
        .as_str()
        .ok_or_else(|| error("Нет адреса инструментов Horizon."))?;
    let bytes =
        fetch::fetch(url, None, &state.api_semaphore, &state.pool).await?;
    if format!("{:x}", Sha256::digest(&bytes)) != digest {
        return Err(error(
            "Контрольная сумма инструментов Horizon не совпадает.",
        ));
    }
    tokio::fs::create_dir_all(&root)
        .await
        .map_err(IOError::from)?;
    let temp = tempfile::tempdir_in(&root).map_err(IOError::from)?;
    let archive = temp.path().join("download");
    let out = root.join("bin");
    tokio::fs::write(&archive, bytes)
        .await
        .map_err(IOError::from)?;
    tokio::fs::create_dir_all(&out)
        .await
        .map_err(IOError::from)?;
    extract(&archive, &out, name.ends_with(".zip")).await?;
    let program = tokio::task::spawn_blocking(move || find_binary(&out))
        .await
        .map_err(|e| error(e.to_string()))??;
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        tokio::fs::set_permissions(
            &program,
            std::fs::Permissions::from_mode(0o755),
        )
        .await
        .map_err(IOError::from)?;
    }
    let relative = program
        .strip_prefix(&root)
        .map_err(|_| error("Неверный путь инструментов Horizon."))?
        .to_string_lossy()
        .replace('\\', "/");
    write_json(&marker, &value!({"binary": relative})).await?;
    Ok(program)
}

fn find_binary(root: &Path) -> crate::Result<PathBuf> {
    let mut dirs = vec![root.to_owned()];
    while let Some(dir) = dirs.pop() {
        for entry in std::fs::read_dir(dir).map_err(IOError::from)? {
            let entry = entry.map_err(IOError::from)?;
            let kind = entry.file_type().map_err(IOError::from)?;
            if kind.is_dir() {
                dirs.push(entry.path());
            }
            if kind.is_file()
                && entry.file_name()
                    == format!(
                        "horizon-launcher{}",
                        std::env::consts::EXE_SUFFIX
                    )
                    .as_str()
            {
                return Ok(entry.path());
            }
        }
    }
    Err(error(
        "Архив инструментов Horizon не содержит программу управления модами.",
    ))
}

pub(super) fn clone_runtime(
    source: &Path,
    destination: &Path,
) -> crate::Result<()> {
    let mut dirs = vec![(source.to_owned(), destination.to_owned())];
    while let Some((source, destination)) = dirs.pop() {
        std::fs::create_dir_all(&destination).map_err(IOError::from)?;
        for entry in std::fs::read_dir(source).map_err(IOError::from)? {
            let entry = entry.map_err(IOError::from)?;
            let target = destination.join(entry.file_name());
            let kind = entry.file_type().map_err(IOError::from)?;
            if kind.is_dir() {
                dirs.push((entry.path(), target));
            } else if kind.is_file() {
                if std::fs::hard_link(entry.path(), &target).is_err() {
                    std::fs::copy(entry.path(), target)
                        .map_err(IOError::from)?;
                }
            } else {
                return Err(error(
                    "Ссылки в установленном клиенте Horizon не поддерживаются.",
                ));
            }
        }
    }
    Ok(())
}

fn base_version(name: &str) -> crate::Result<String> {
    let parts: Vec<_> = name.split_whitespace().collect();
    if parts.len() == 4 && parts[0] == "Nightly" && parts[2] == "·" {
        let version = format!("nightly-{}-{}", parts[1], parts[3]);
        if valid_version(&version) {
            return Ok(version);
        }
    }
    Err(error(
        "Не удалось определить версию Horizon для модов. Восстановите сборку.",
    ))
}

async fn bridge(home: &Path, revision: Option<&str>) -> crate::Result<String> {
    let original = home.join(".horizon");
    let version = read_json(&original.join("blockera-ready.json")).await?;
    let base = match revision {
        Some(revision) => revision.to_owned(),
        None => base_version(version["name"].as_str().unwrap_or_default())?,
    };
    let manager = home.join(MANAGER);
    let root = manager.join("versions").join(&base);
    let fingerprint = root.join("blockera-bridge.json");
    let ready = if fingerprint.is_file() {
        read_json(&fingerprint).await?["sha256"] == version["sha256"]
    } else {
        false
    };
    if !ready {
        if root.exists() {
            tokio::fs::remove_dir_all(&root)
                .await
                .map_err(IOError::from)?;
        }
        let from = original.clone();
        let to = root.clone();
        tokio::task::spawn_blocking(move || clone_runtime(&from, &to))
            .await
            .map_err(|e| error(e.to_string()))??;
        tokio::fs::write(root.join(".installed"), "")
            .await
            .map_err(IOError::from)?;
        write_json(&fingerprint, &value!({"sha256": version["sha256"]}))
            .await?;
    }
    let settings = manager.join("settings.json");
    if revision.is_none() && !settings.is_file() {
        write_json(&settings, &value!({"selected": base, "auto_install": false, "auto_prune": false})).await?;
    }
    Ok(base)
}

fn command(program: &Path, home: &Path) -> Command {
    let mut cmd = Command::new(program);
    cmd.current_dir(home)
        .kill_on_drop(true)
        .env("HORIZON_HOME", home)
        .env("HORIZON_LAUNCHER_HOME", home.join(MANAGER))
        .env("HORIZON_LAUNCHER_NO_SELF_UPDATE", "1");
    #[cfg(windows)]
    cmd.creation_flags(0x08000000);
    cmd
}

async fn logged(mut cmd: Command, log: &Path) -> crate::Result<()> {
    let file = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(log)
        .map_err(IOError::from)?;
    cmd.stdout(file.try_clone().map_err(IOError::from)?)
        .stderr(file);
    let status = cmd.status().await.map_err(IOError::from)?;
    if !status.success() {
        let tail = log_tail(log).await?;
        return Err(error(format!(
            "Операция Horizon завершилась с ошибкой.\n{tail}"
        )));
    }
    Ok(())
}

async fn build_native(
    program: &Path,
    home: &Path,
    base: &str,
    game: &str,
    allow_version_mismatch: bool,
    log: &Path,
) -> crate::Result<()> {
    #[cfg(all(target_os = "windows", target_arch = "x86_64"))]
    {
        // Install Rust without compiling, then migrate the complete C toolchain.
        // Horizon prepends this managed MinGW directory to every build's PATH.
        super::mingw::recover(home).await?;
        let mut install = command(program, home);
        install.args(["rust", "install"]);
        logged(install, log).await?;
        super::mingw::ensure(home).await?;
    }
    let build = |version: &str| {
        let mut cmd = command(program, home);
        #[cfg(all(target_os = "windows", target_arch = "x86_64"))]
        let configured = super::mingw::configure(&mut cmd, home);
        #[cfg(not(all(target_os = "windows", target_arch = "x86_64")))]
        let configured: crate::Result<()> = Ok(());
        cmd.arg("native-build")
            .arg("--instance")
            .arg("horizon")
            .arg("--version")
            .arg(version)
            .arg("--trust-native-code");
        if allow_version_mismatch {
            cmd.arg("--allow-version-mismatch");
        }
        async move {
            configured?;
            logged(cmd, log).await
        }
    };
    let first = build(base).await;
    if !log_tail(log).await?.contains("E_TARGET_NOT_FOUND") {
        return first;
    }
    let Some(revision) = shared_native_revision(home, game).await? else {
        return first;
    };
    if base
        .rsplit('-')
        .next()
        .is_some_and(|r| revision.starts_with(r))
    {
        return first;
    }
    // Some revisions advertise the same hook API but have different raw
    // mixin targets. Reuse the prepared resources for this Minecraft version.
    let current_lock =
        read_json(&home.join(".horizon/research-lock.json")).await?;
    let target_lock = json(&format!(
        "https://raw.githubusercontent.com/horizon-rust/horizon/{revision}/research-lock.json"
    ))
    .await?;
    if target_lock["minecraft"]["version"] != game
        || ["manifest_sha1", "client_sha1", "server_sha1"]
            .iter()
            .any(|key| {
                let checksum = &current_lock["minecraft"][*key];
                !checksum.is_string()
                    || checksum != &target_lock["minecraft"][*key]
            })
    {
        return first;
    }
    let fallback = bridge(home, Some(&revision)).await?;
    use tokio::io::AsyncWriteExt;
    let mut file = tokio::fs::OpenOptions::new()
        .append(true)
        .open(log)
        .await
        .map_err(IOError::from)?;
    file.write_all(
        format!(
            "\nПовтор сборки на ревизии модов Horizon {}.\n",
            &revision[..7]
        )
        .as_bytes(),
    )
    .await
    .map_err(IOError::from)?;
    drop(file);
    build(&fallback).await
}

async fn log_tail(log: &Path) -> crate::Result<String> {
    use tokio::io::{AsyncReadExt, AsyncSeekExt};
    let mut file = tokio::fs::File::open(log).await.map_err(IOError::from)?;
    let len = file.metadata().await.map_err(IOError::from)?.len();
    file.seek(std::io::SeekFrom::Start(len.saturating_sub(16_000)))
        .await
        .map_err(IOError::from)?;
    let mut bytes = Vec::new();
    file.read_to_end(&mut bytes).await.map_err(IOError::from)?;
    Ok(String::from_utf8_lossy(&bytes)
        .lines()
        .rev()
        .take(45)
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .collect::<Vec<_>>()
        .join("\n"))
}

pub async fn logs(path: &str) -> crate::Result<String> {
    profile(path).await?;
    let log = crate::profile::get_full_path(path)
        .await?
        .join("logs/horizon-mods.log");
    if !log.is_file() {
        return Ok("Операции с модами ещё не выполнялись.".into());
    }
    log_tail(&log).await
}

pub async fn action(
    path: &str,
    action: &str,
    id: Option<&str>,
    trust_native_code: bool,
    allow_version_mismatch: bool,
) -> crate::Result<Value> {
    let _guard = MUTATION.lock().await;
    let profile = profile(path).await?;
    if !crate::process::get_by_profile_path(path).await?.is_empty() {
        return Err(error("Остановите игру перед изменением модов Horizon."));
    }
    if !matches!(
        action,
        "install" | "update" | "remove" | "enable" | "disable" | "build"
    ) {
        return Err(error("Неизвестная операция с модами Horizon."));
    }
    let id = id.unwrap_or_default();
    if action != "build" && !valid_id(id) {
        return Err(error("Некорректный идентификатор мода."));
    }
    if action == "build" && !trust_native_code {
        return Err(error(
            "Подтвердите доверие к коду нативных модов перед сборкой.",
        ));
    }
    let home = crate::profile::get_full_path(path).await?;
    let manager = home.join(MANAGER);
    tokio::fs::create_dir_all(&manager)
        .await
        .map_err(IOError::from)?;
    let bar = init_loading(
        LoadingBarType::MinecraftDownload {
            profile_name: profile.name,
            profile_path: path.to_owned(),
        },
        100.0,
        if action == "build" {
            "Сборка Horizon с нативными модами"
        } else {
            "Моды Horizon"
        },
    )
    .await?;
    BUSY.insert(home.clone());
    let result: crate::Result<()> = async {
        let program = helper().await?;
        emit_loading(&bar, 10.0, Some("Подготовка инструментов Horizon"))?;
        let base = bridge(&home, None).await?;
        let log_dir = home.join("logs");
        tokio::fs::create_dir_all(&log_dir).await.map_err(IOError::from)?;
        let log = log_dir.join("horizon-mods.log");
        tokio::fs::write(&log, format!("Horizon {action} {id}\n")).await.map_err(IOError::from)?;
        if action == "build" {
            keep_native_versions(&home, None).await?;
            tokio::fs::write(manager.join("native-dirty"), "").await.map_err(IOError::from)?;
            emit_loading(&bar, 10.0, Some("Компиляция нативных модов. Прогресс доступен в журнале Horizon"))?;
            build_native(&program, &home, &base, &profile.game_version, allow_version_mismatch, &log).await?;
            tokio::fs::remove_file(manager.join("native-dirty")).await.map_err(IOError::from)?;
        } else {
            // Mark before an operation: a failed update must not silently run a stale native build.
            let native = installed(&home).await?.iter().any(|e| e["id"] == id && e["kind"] == "native-mod")
                || (action == "install" && catalog(path).await?["projects"].as_array().is_some_and(|p| p.iter().any(|p| p["id"] == id && p["kind"] == "native-mod")));
            if native { tokio::fs::write(manager.join("native-dirty"), "").await.map_err(IOError::from)?; }
            if action == "update" && native { keep_native_versions(&home, Some(id)).await?; }
            let mut cmd = command(&program, &home);
            cmd.arg("mods").arg(action).arg(id).arg("--instance").arg("horizon");
            if action == "install" { cmd.arg("--source").arg("horizon"); }
            logged(cmd, &log).await?;
        }
        emit_loading(&bar, 90.0, Some("Готово"))?;
        Ok(())
    }.await;
    BUSY.remove(&home);
    result?;
    status(path).await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_ids_and_pinned_release_names() {
        assert!(valid_id("fps_counter"));
        for id in ["../other", "--help", "Zoom", "", "a/b"] {
            assert!(!valid_id(id));
        }
        assert_eq!(
            base_version("Nightly 2026-11-05 · 66f3981").unwrap(),
            "nightly-2026-11-05-66f3981"
        );
        assert!(base_version("Nightly ../outside · commit").is_err());
        assert!(!valid_version(".."));
        assert!(!valid_version("."));
    }

    #[test]
    fn hides_legacy_webassembly_mods_and_other_game_versions() {
        let content = value!({"kind": "mod"});
        assert!(compatible(
            &content,
            &value!({"game": "26.3", "capabilities": ["registries"]}),
            "26.3"
        ));
        assert!(!compatible(
            &content,
            &value!({"game": "26.3", "capabilities": ["render", "input"]}),
            "26.3"
        ));
        assert!(!compatible(
            &content,
            &value!({"game": "1.21", "capabilities": ["registries"]}),
            "26.3"
        ));
        assert!(compatible(
            &value!({"kind": "native-mod"}),
            &value!({"game": "26.3"}),
            "26.3"
        ));
    }

    #[tokio::test]
    async fn build_keeps_enabled_native_versions_until_an_explicit_update() {
        let temp = tempfile::tempdir().unwrap();
        let home = temp.path();
        let pins = home.join("mod-browser/chosen-versions.json");
        tokio::fs::create_dir_all(pins.parent().unwrap())
            .await
            .unwrap();
        write_json(&pins, &value!({"other": "2.0"})).await.unwrap();
        write_json(
            &home.join("mod-browser.json"),
            &value!({"entries": [
                {"source": "horizon", "slug": "zoom", "number": "1.0", "file": "horizon-native-mods/zoom.hnmod"},
                {"source": "horizon", "slug": "hud", "number": "3.0", "file": "horizon-native-mods/hud.hnmod"}
            ]}),
        )
        .await
        .unwrap();
        tokio::fs::write(
            home.join("horizon-mods.txt"),
            "mod.zoom:true\nmod.hud:false",
        )
        .await
        .unwrap();

        keep_native_versions(home, None).await.unwrap();
        assert_eq!(
            read_json(&pins).await.unwrap(),
            value!({"other": "2.0", "zoom": "1.0"})
        );
        keep_native_versions(home, Some("zoom")).await.unwrap();
        assert_eq!(read_json(&pins).await.unwrap(), value!({"other": "2.0"}));
    }

    #[tokio::test]
    async fn fallback_requires_one_revision_shared_by_enabled_mods() {
        use std::io::Write;

        let temp = tempfile::tempdir().unwrap();
        let home = temp.path();
        let packages = home.join("horizon-native-mods");
        std::fs::create_dir(&packages).unwrap();
        let first = "1".repeat(40);
        let second = "2".repeat(40);
        for (id, revision) in [("zoom", &first), ("hud", &second)] {
            let mut bundle = zip::ZipWriter::new(
                std::fs::File::create(packages.join(format!("{id}.hnmod")))
                    .unwrap(),
            );
            bundle
                .start_file(
                    "horizon-native.json",
                    zip::write::SimpleFileOptions::default(),
                )
                .unwrap();
            write!(
                bundle,
                "{}",
                value!({"id": id, "horizon": revision, "game": "26.3"})
            )
            .unwrap();
            bundle.finish().unwrap();
        }
        write_json(
            &home.join("mod-browser.json"),
            &value!({"entries": [
                {"source": "horizon", "slug": "zoom", "file": "horizon-native-mods/zoom.hnmod"},
                {"source": "horizon", "slug": "hud", "file": "horizon-native-mods/hud.hnmod"}
            ]}),
        )
        .await
        .unwrap();
        let settings = home.join("horizon-mods.txt");
        tokio::fs::write(&settings, "mod.zoom:true\nmod.hud:false")
            .await
            .unwrap();
        assert_eq!(
            shared_native_revision(home, "26.3").await.unwrap(),
            Some(first)
        );
        assert_eq!(shared_native_revision(home, "26.4").await.unwrap(), None);

        tokio::fs::write(&settings, "mod.zoom:true\nmod.hud:true")
            .await
            .unwrap();
        assert_eq!(shared_native_revision(home, "26.3").await.unwrap(), None);
    }

    #[tokio::test]
    async fn native_mod_changes_block_stale_builds_and_disabled_mods_use_base_client()
     {
        let temp = tempfile::tempdir().unwrap();
        let home = temp.path();
        write_json(&home.join("mod-browser.json"), &value!({"entries": [{"source": "horizon", "slug": "zoom", "title": "Zoom", "number": "1.0", "file": "horizon-native-mods/zoom.hnmod"}]})).await.unwrap();
        tokio::fs::write(home.join("horizon-mods.txt"), "mod.zoom:true")
            .await
            .unwrap();
        assert!(launch_runtime(home).await.is_err());
        let native = home.join(MANAGER).join("versions/native-test");
        tokio::fs::create_dir_all(&native).await.unwrap();
        tokio::fs::write(binary(&native, "horizon-client"), "fixture")
            .await
            .unwrap();
        write_json(
            &home.join(MANAGER).join("instances.json"),
            &value!({"instances": {"horizon": {"version": "native-test"}}}),
        )
        .await
        .unwrap();
        assert_eq!(launch_runtime(home).await.unwrap(), native);
        tokio::fs::write(home.join(MANAGER).join("native-dirty"), "")
            .await
            .unwrap();
        assert!(launch_runtime(home).await.is_err());
        tokio::fs::write(home.join("horizon-mods.txt"), "mod.zoom:false")
            .await
            .unwrap();
        assert_eq!(launch_runtime(home).await.unwrap(), home.join(".horizon"));
    }

    #[test]
    fn reads_last_per_mod_choice_without_matching_other_keys() {
        assert!(enabled("mod.zoom:false\nmod.zoom:true\n", "zoom"));
        assert!(!enabled("mod.zoom:true\nmod.zoom:false\n", "zoom"));
        assert!(!enabled("mod.zoom_extra:true", "zoom"));
    }

    #[tokio::test]
    async fn vanilla_runtime_is_used_without_native_mods() {
        let home = tempfile::tempdir().unwrap();
        assert_eq!(
            launch_runtime(home.path()).await.unwrap(),
            home.path().join(".horizon")
        );
        tokio::fs::create_dir_all(home.path().join(MANAGER))
            .await
            .unwrap();
        BUSY.insert(home.path().to_owned());
        assert!(launch_runtime(home.path()).await.is_err());
        BUSY.remove(home.path());
    }
}
