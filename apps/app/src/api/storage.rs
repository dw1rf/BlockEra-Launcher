use crate::api::Result;
use serde::Serialize;
use std::collections::{HashMap, HashSet};
use std::fs::{self, Metadata};
use std::io;
use std::path::{Component, Path, PathBuf};
use std::sync::LazyLock;
use std::time::{Duration, Instant, SystemTime};
use tokio::sync::Mutex;
use uuid::Uuid;

const PREVIEW_TTL: Duration = Duration::from_secs(30 * 60);
const CACHE_MIN_AGE: Duration = Duration::from_secs(24 * 60 * 60);
const MAX_CANDIDATES: usize = 5000;
static PREVIEWS: LazyLock<Mutex<HashMap<String, Preview>>> =
    LazyLock::new(Mutex::default);
static CLEANUP: Mutex<()> = Mutex::const_new(());

pub fn init<R: tauri::Runtime>() -> tauri::plugin::TauriPlugin<R> {
    tauri::plugin::Builder::new("storage")
        .invoke_handler(tauri::generate_handler![storage_scan, storage_cleanup])
        .build()
}

#[derive(Serialize)]
pub struct ProfileUsage {
    path: String,
    name: String,
    bytes: u64,
    backup_bytes: u64,
}

#[derive(Clone, Serialize)]
pub struct CleanupItem {
    id: String,
    kind: &'static str,
    name: String,
    profile_name: Option<String>,
    relative_path: String,
    bytes: u64,
}

#[derive(Serialize)]
pub struct StorageUsage {
    preview_id: String,
    profiles: Vec<ProfileUsage>,
    cache_bytes: u64,
    candidates: Vec<CleanupItem>,
    warnings: Vec<String>,
}

#[derive(Clone)]
struct Candidate {
    item: CleanupItem,
    relative: PathBuf,
    modified: SystemTime,
}

struct Preview {
    created: Instant,
    root: PathBuf,
    candidates: Vec<Candidate>,
}

#[derive(Serialize)]
pub struct CleanupResult {
    removed_bytes: u64,
    removed_count: usize,
    failures: Vec<String>,
}

fn invalid(message: impl Into<String>) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, message.into())
}

fn is_link(metadata: &Metadata) -> bool {
    #[cfg(windows)]
    {
        use std::os::windows::fs::MetadataExt;
        metadata.file_type().is_symlink()
            || metadata.file_attributes() & 0x400 != 0
    }
    #[cfg(not(windows))]
    {
        metadata.file_type().is_symlink()
    }
}

// Every component is checked with symlink_metadata, including Windows junctions.
fn checked_path(root: &Path, relative: &Path) -> io::Result<PathBuf> {
    if relative.as_os_str().is_empty() {
        return Err(invalid("Пустой путь файла."));
    }
    let mut path = root.to_owned();
    for component in relative.components() {
        let Component::Normal(name) = component else {
            return Err(invalid("Недопустимый путь файла."));
        };
        path.push(name);
        if is_link(&fs::symlink_metadata(&path)?) {
            return Err(invalid("Ссылки и точки подключения пропущены."));
        }
    }
    if !fs::canonicalize(&path)?.starts_with(root) {
        return Err(invalid("Файл находится за пределами папки лаунчера."));
    }
    Ok(path)
}

fn folder_size(
    root: &Path,
    relative: &Path,
    warnings: &mut Vec<String>,
) -> u64 {
    let first = match checked_path(root, relative) {
        Ok(path) => path,
        Err(error) if error.kind() == io::ErrorKind::NotFound => return 0,
        Err(error) => {
            warnings.push(format!("{} — {error}", relative.display()));
            return 0;
        }
    };
    let mut total = 0_u64;
    let mut directories = vec![first];
    while let Some(directory) = directories.pop() {
        if let Err(error) = directory
            .strip_prefix(root)
            .map_err(|e| invalid(e.to_string()))
            .and_then(|relative| checked_path(root, relative))
        {
            if warnings.len() < 100 {
                warnings.push(error.to_string());
            }
            continue;
        }
        let entries = match fs::read_dir(&directory) {
            Ok(entries) => entries,
            Err(error) => {
                warnings.push(format!("{} — {error}", directory.display()));
                continue;
            }
        };
        for entry in entries {
            let result = entry.and_then(|entry| {
                let metadata = fs::symlink_metadata(entry.path())?;
                if is_link(&metadata) {
                    return Err(invalid(format!(
                        "Пропущена ссылка {}",
                        entry.path().display()
                    )));
                }
                if metadata.is_dir() {
                    directories.push(entry.path());
                } else if metadata.is_file() {
                    total = total.saturating_add(metadata.len());
                }
                Ok(())
            });
            if let Err(error) = result {
                if warnings.len() < 100 {
                    warnings.push(error.to_string());
                }
            }
        }
    }
    total
}

fn candidate(
    root: &Path,
    relative: PathBuf,
    kind: &'static str,
    profile: Option<&theseus::profile::Profile>,
) -> io::Result<Candidate> {
    let path = checked_path(root, &relative)?;
    let metadata = fs::symlink_metadata(&path)?;
    if !metadata.is_file() {
        return Err(invalid("Ожидался обычный файл."));
    }
    Ok(Candidate {
        item: CleanupItem {
            id: Uuid::new_v4().to_string(),
            kind,
            name: path
                .file_name()
                .unwrap_or_default()
                .to_string_lossy()
                .into_owned(),
            profile_name: profile.map(|p| p.name.clone()),
            relative_path: relative.to_string_lossy().into_owned(),
            bytes: metadata.len(),
        },
        relative,
        modified: metadata.modified()?,
    })
}

fn cache_archive_name(name: &str) -> bool {
    name.strip_suffix(".mrpack").is_some_and(|hash| {
        hash.len() == 64 && hash.bytes().all(|b| b.is_ascii_hexdigit())
    })
}

#[tauri::command]
pub async fn storage_scan() -> Result<StorageUsage> {
    let state = theseus::State::get().await?;
    let root = fs::canonicalize(&state.directories.config_dir)?;
    let profiles = theseus::profile::list().await?;
    let mut backup_files = HashMap::new();
    let mut warnings = Vec::new();
    for profile in &profiles {
        let relative = PathBuf::from("profiles").join(&profile.path);
        match checked_path(&root, &relative.join("backups")) {
            Ok(_) => {
                match theseus::worlds::list_world_backups(
                    &root.join(&relative),
                    &profile.path,
                    None,
                )
                .await
                {
                    Ok(backups) => {
                        backup_files.insert(profile.path.clone(), backups);
                    }
                    Err(error) => {
                        warnings.push(format!("{} — {error}", profile.name))
                    }
                }
            }
            Err(error) if error.kind() == io::ErrorKind::NotFound => {}
            Err(error) => warnings.push(format!("{} — {error}", profile.name)),
        }
    }
    let preview_id = Uuid::new_v4().to_string();
    let id = preview_id.clone();
    let (usage, preview) = tokio::task::spawn_blocking(move || {
        let mut rows = Vec::new();
        let mut candidates = Vec::new();
        for profile in profiles {
            let relative = PathBuf::from("profiles").join(&profile.path);
            let total = folder_size(&root, &relative, &mut warnings);
            let backup_bytes =
                folder_size(&root, &relative.join("backups"), &mut warnings);
            for backup in backup_files.remove(&profile.path).unwrap_or_default()
            {
                if candidates.len() >= MAX_CANDIDATES {
                    break;
                }
                if let Ok(file) = candidate(
                    &root,
                    relative.join("backups").join(backup.id),
                    "backup",
                    Some(&profile),
                ) {
                    candidates.push(file);
                }
            }
            rows.push(ProfileUsage {
                path: profile.path,
                name: profile.name,
                bytes: total.saturating_sub(backup_bytes),
                backup_bytes,
            });
        }
        let cache_bytes =
            folder_size(&root, Path::new("caches"), &mut warnings);
        let cache_relative = Path::new("caches/external-packs");
        if let Ok(cache_dir) = checked_path(&root, cache_relative) {
            if let Ok(entries) = fs::read_dir(cache_dir) {
                for entry in entries.flatten() {
                    if candidates.len() >= MAX_CANDIDATES {
                        break;
                    }
                    if !cache_archive_name(&entry.file_name().to_string_lossy())
                    {
                        continue;
                    }
                    if let Ok(file) = candidate(
                        &root,
                        cache_relative.join(entry.file_name()),
                        "cache",
                        None,
                    ) {
                        if file
                            .modified
                            .elapsed()
                            .is_ok_and(|age| age >= CACHE_MIN_AGE)
                        {
                            candidates.push(file);
                        }
                    }
                }
            }
        }
        if candidates.len() == MAX_CANDIDATES {
            warnings.push(
                "Показаны первые 5000 файлов. После очистки обновите список."
                    .into(),
            );
        }
        rows.sort_by(|a, b| {
            (b.bytes + b.backup_bytes).cmp(&(a.bytes + a.backup_bytes))
        });
        candidates.sort_by(|a, b| b.item.bytes.cmp(&a.item.bytes));
        let usage = StorageUsage {
            preview_id: id,
            profiles: rows,
            cache_bytes,
            candidates: candidates.iter().map(|c| c.item.clone()).collect(),
            warnings,
        };
        (
            usage,
            Preview {
                created: Instant::now(),
                root,
                candidates,
            },
        )
    })
    .await
    .map_err(|error| io::Error::other(error.to_string()))?;
    let mut previews = PREVIEWS.lock().await;
    previews.retain(|_, p| p.created.elapsed() < PREVIEW_TTL);
    if previews.len() >= 8 {
        if let Some(oldest) = previews
            .iter()
            .min_by_key(|(_, p)| p.created)
            .map(|(id, _)| id.clone())
        {
            previews.remove(&oldest);
        }
    }
    previews.insert(preview_id, preview);
    Ok(usage)
}

async fn require_idle() -> Result<()> {
    if !theseus::process::get_all().await?.is_empty()
        || !theseus::EventState::list_progress_bars().await?.is_empty()
        || theseus::pack::catalog_install::has_active_catalog_installs()
    {
        return Err(invalid(
            "Остановите игры и дождитесь окончания загрузок перед очисткой.",
        )
        .into());
    }
    Ok(())
}

fn revalidate(root: &Path, file: &Candidate) -> io::Result<PathBuf> {
    let path = checked_path(root, &file.relative)?;
    let metadata = fs::symlink_metadata(&path)?;
    if !metadata.is_file()
        || metadata.len() != file.item.bytes
        || metadata.modified()? != file.modified
    {
        return Err(invalid("Файл изменился после проверки. Обновите список."));
    }
    Ok(path)
}

#[tauri::command]
pub async fn storage_cleanup(
    preview_id: String,
    selected_ids: Vec<String>,
) -> Result<CleanupResult> {
    let _guard = CLEANUP.lock().await;
    require_idle().await?;
    let _archive_guard =
        theseus::pack::catalog_install::try_lock_cache_cleanup().ok_or_else(
            || invalid("Дождитесь окончания проверки или установки сборок."),
        )?;
    let preview =
        PREVIEWS.lock().await.remove(&preview_id).ok_or_else(|| {
            invalid("Проверка устарела. Обновите список файлов.")
        })?;
    let current_root =
        fs::canonicalize(&theseus::State::get().await?.directories.config_dir)?;
    if preview.created.elapsed() >= PREVIEW_TTL || preview.root != current_root
    {
        return Err(invalid(
            "Папка данных или проверка изменились. Обновите список.",
        )
        .into());
    }
    let selected: HashSet<_> = selected_ids.into_iter().collect();
    if selected.is_empty()
        || selected
            .iter()
            .any(|id| !preview.candidates.iter().any(|c| &c.item.id == id))
    {
        return Err(invalid("Выберите файлы из текущего списка.").into());
    }
    let mut result = CleanupResult {
        removed_bytes: 0,
        removed_count: 0,
        failures: Vec::new(),
    };
    for file in preview
        .candidates
        .into_iter()
        .filter(|c| selected.contains(&c.item.id))
    {
        let remove: Result<()> = async {
            require_idle().await?;
            let root = current_root.clone();
            let target = file.clone();
            tokio::task::spawn_blocking(move || {
                // Delete the exact validated path, with no async gap or profile
                // re-resolution. Backup indexes reconcile on the next listing.
                let path = revalidate(&root, &target)?;
                fs::remove_file(path)
            })
            .await
            .map_err(|error| io::Error::other(error.to_string()))??;
            Ok(())
        }
        .await;
        match remove {
            Ok(()) => {
                result.removed_bytes += file.item.bytes;
                result.removed_count += 1;
            }
            Err(error) => result
                .failures
                .push(format!("{} — {error}", file.item.relative_path)),
        }
    }
    Ok(result)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn only_downloaded_pack_archives_are_cleanable_cache() {
        assert!(cache_archive_name(&format!("{}.mrpack", "a".repeat(64))));
        for name in [
            "background.png",
            "horizon-launcher.exe",
            "pack.mrpack",
            "../pack.mrpack",
        ] {
            assert!(!cache_archive_name(name));
        }
    }

    #[test]
    fn changed_file_and_parent_traversal_are_rejected() {
        let directory = std::env::temp_dir()
            .join(format!("blockera-storage-test-{}", Uuid::new_v4()));
        fs::create_dir(&directory).unwrap();
        let root = fs::canonicalize(&directory).unwrap();
        let path = root.join("fixture.zip");
        fs::write(&path, b"before").unwrap();
        let file =
            candidate(&root, PathBuf::from("fixture.zip"), "backup", None)
                .unwrap();
        assert!(revalidate(&root, &file).is_ok());
        assert!(checked_path(&root, Path::new("../fixture.zip")).is_err());
        assert!(checked_path(&root, &path).is_err());
        fs::write(&path, b"changed contents").unwrap();
        assert!(revalidate(&root, &file).is_err());
        fs::remove_file(path).unwrap();
        fs::remove_dir(directory).unwrap();
    }

    #[test]
    fn linked_files_are_not_cleanup_candidates() {
        let directory = std::env::temp_dir()
            .join(format!("blockera-storage-link-test-{}", Uuid::new_v4()));
        fs::create_dir(&directory).unwrap();
        let root = fs::canonicalize(&directory).unwrap();
        let target = root.join("target.zip");
        let link = root.join("link.zip");
        fs::write(&target, b"keep").unwrap();
        #[cfg(windows)]
        let linked = std::os::windows::fs::symlink_file(&target, &link);
        #[cfg(not(windows))]
        let linked = std::os::unix::fs::symlink(&target, &link);
        match linked {
            Ok(()) => {
                assert!(checked_path(&root, Path::new("link.zip")).is_err());
                assert!(
                    candidate(&root, PathBuf::from("link.zip"), "backup", None)
                        .is_err()
                );
                fs::remove_file(link).unwrap();
            }
            Err(error)
                if cfg!(windows) && error.raw_os_error() == Some(1314) =>
            {
                eprintln!(
                    "Symlink fixture unavailable without Windows developer mode: {error}"
                );
            }
            Err(error) => panic!("Cannot create link fixture: {error}"),
        }
        assert_eq!(fs::read(&target).unwrap(), b"keep");
        fs::remove_file(target).unwrap();
        fs::remove_dir(directory).unwrap();
    }
}
