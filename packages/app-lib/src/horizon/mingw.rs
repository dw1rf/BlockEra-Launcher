//! Work around the bundled GCC 16.2 ICE with one complete, verified MSVCRT
//! toolchain. The same immutable pin is consumed by the Windows CI smoke test.
#![cfg(all(target_os = "windows", target_arch = "x86_64"))]

use super::{error, extract};
use crate::State;
use crate::util::{fetch, io::IOError};
use serde::Deserialize;
use sha2::{Digest, Sha256};
use std::fs;
use std::path::{Component, Path, PathBuf};

const MANIFEST: &str = include_str!("mingw.json");
const MARKER: &str = ".blockera-mingw.json";
static INSTALL: tokio::sync::Mutex<()> = tokio::sync::Mutex::const_new(());

#[derive(Deserialize)]
struct Pin {
    version: String,
    url: String,
    sha256: String,
    rust_version: String,
    toolchain_directory: String,
}

fn manifest_pin() -> crate::Result<Pin> {
    let pin: Pin = serde_json::from_str(MANIFEST)?;
    if pin.toolchain_directory
        != format!("{}-x86_64-pc-windows-gnu", pin.rust_version)
        || pin.sha256.len() != 64
        || !pin.sha256.bytes().all(|byte| byte.is_ascii_hexdigit())
    {
        return Err(error("Неверная конфигурация компилятора Horizon."));
    }
    Ok(pin)
}

fn reject_link(path: &Path) -> crate::Result<()> {
    use std::os::windows::fs::MetadataExt;
    let metadata = fs::symlink_metadata(path).map_err(IOError::from)?;
    if metadata.file_type().is_symlink()
        || metadata.file_attributes() & 0x400 != 0
    {
        return Err(error(format!(
            "Папка компилятора содержит ссылку: {}",
            path.display()
        )));
    }
    Ok(())
}

// Validate every existing ancestor, including junctions. A missing final path
// is allowed for rename destinations, but may never escape the canonical root.
fn checked(root: &Path, relative: &Path) -> crate::Result<PathBuf> {
    let mut path = root.to_owned();
    for component in relative.components() {
        let Component::Normal(name) = component else {
            return Err(error("Недопустимый путь компилятора Horizon."));
        };
        path.push(name);
        match fs::symlink_metadata(&path) {
            Ok(_) => reject_link(&path)?,
            Err(err) if err.kind() == std::io::ErrorKind::NotFound => {}
            Err(err) => return Err(IOError::from(err).into()),
        }
    }
    Ok(path)
}

struct Layout {
    root: PathBuf,
    target: PathBuf,
    previous: PathBuf,
}

fn tool_safe(path: &Path) -> bool {
    path.to_str()
        .is_some_and(|path| path.is_ascii() && !path.contains(' '))
}

// Mirrors horizon-launcher 0.2.58 backend::ascii_build_dir exactly.
fn ascii_build_dir(manager: &Path, program_data: Option<PathBuf>) -> PathBuf {
    let public = program_data
        .filter(|path| path.is_absolute() && tool_safe(path))
        .unwrap_or_else(|| PathBuf::from(r"C:\ProgramData"));
    let digest = Sha256::digest(manager.as_os_str().as_encoded_bytes());
    let key: String = digest[..4]
        .iter()
        .map(|byte| format!("{byte:02x}"))
        .collect();
    public.join(format!("horizon-launcher-{key}"))
}

fn validate_ancestors(path: &Path) -> crate::Result<()> {
    for ancestor in path.ancestors() {
        match fs::symlink_metadata(ancestor) {
            Ok(_) => reject_link(ancestor)?,
            Err(err) if err.kind() == std::io::ErrorKind::NotFound => {}
            Err(err) => return Err(IOError::from(err).into()),
        }
    }
    Ok(())
}

fn short_path(path: &Path) -> Option<PathBuf> {
    use std::os::windows::ffi::{OsStrExt, OsStringExt};
    #[link(name = "kernel32")]
    unsafe extern "system" {
        fn GetShortPathNameW(
            long: *const u16,
            short: *mut u16,
            len: u32,
        ) -> u32;
    }
    let wide: Vec<u16> = path.as_os_str().encode_wide().chain([0]).collect();
    // SAFETY: input is NUL terminated; a null output requests its required size.
    let need =
        unsafe { GetShortPathNameW(wide.as_ptr(), std::ptr::null_mut(), 0) };
    if need == 0 {
        return None;
    }
    let mut buffer = vec![0u16; need as usize];
    // SAFETY: buffer has exactly the capacity supplied to the Windows API.
    let got =
        unsafe { GetShortPathNameW(wide.as_ptr(), buffer.as_mut_ptr(), need) };
    (got > 0 && got < need).then(|| {
        PathBuf::from(std::ffi::OsString::from_wide(&buffer[..got as usize]))
    })
}

impl Layout {
    fn new(home: &Path, pin: &Pin) -> crate::Result<Self> {
        validate_ancestors(home)?;
        let profile = dunce::canonicalize(home).map_err(IOError::from)?;
        let manager = checked(&profile, Path::new(".horizon-manager"))?;
        fs::create_dir_all(&manager).map_err(IOError::from)?;
        let manager = dunce::canonicalize(manager).map_err(IOError::from)?;
        let root = if tool_safe(&manager) {
            manager
        } else {
            let public = ascii_build_dir(
                &manager,
                std::env::var_os("ProgramData").map(PathBuf::from),
            );
            validate_ancestors(&public)?;
            if fs::create_dir_all(&public).is_ok() {
                public
            } else {
                short_path(&manager).filter(|path| tool_safe(path)).ok_or_else(||
                    error("Не удалось открыть папку инструментов Horizon в ProgramData."))?
            }
        };
        // Keep a short-name fallback as spelled: canonicalizing it again would
        // reintroduce spaces/non-ASCII names for MinGW.
        Self::at_root(root, pin)
    }

    fn at_root(root: PathBuf, pin: &Pin) -> crate::Result<Self> {
        validate_ancestors(&root)?;
        let target = checked(
            &root,
            &Path::new("rust")
                .join(&pin.toolchain_directory)
                .join("mingw64"),
        )?;
        // Keep rollback outside the helper's toolchain directory: its own
        // installer can replace that directory when an install is incomplete.
        let previous = checked(
            &root,
            &Path::new("rust").join(format!(
                ".blockera-mingw-previous-{}",
                pin.toolchain_directory
            )),
        )?;
        Ok(Self {
            root,
            target,
            previous,
        })
    }

    fn validate(&self, path: &Path) -> crate::Result<()> {
        checked(
            &self.root,
            path.strip_prefix(&self.root)
                .map_err(|_| error("Путь компилятора вне сборки."))?,
        )?;
        Ok(())
    }
}

fn ready(root: &Path, pin: &Pin) -> crate::Result<bool> {
    let marker = checked(root, Path::new(MARKER))?;
    match fs::read_to_string(marker) {
        Ok(contents) if contents == MANIFEST => {}
        Ok(_) => return Ok(false),
        Err(err) if err.kind() == std::io::ErrorKind::NotFound => {
            return Ok(false);
        }
        Err(err) => return Err(IOError::from(err).into()),
    }
    validate_payload(root, pin)?;
    Ok(true)
}

fn validate_payload(root: &Path, pin: &Pin) -> crate::Result<()> {
    for relative in [
        "bin/gcc.exe".to_owned(),
        "bin/g++.exe".to_owned(),
        "bin/ar.exe".to_owned(),
        "bin/ld.exe".to_owned(),
        format!("libexec/gcc/x86_64-w64-mingw32/{}/cc1.exe", pin.version),
        format!("libexec/gcc/x86_64-w64-mingw32/{}/cc1plus.exe", pin.version),
        format!(
            "lib/gcc/x86_64-w64-mingw32/{}/include/ia32intrin.h",
            pin.version
        ),
        "x86_64-w64-mingw32/include/windows.h".to_owned(),
    ] {
        let path = checked(root, Path::new(&relative))?;
        if !path.is_file() {
            return Err(error(format!(
                "В архиве компилятора отсутствует {relative}."
            )));
        }
    }
    Ok(())
}

// Only remove our own rollback tree, after checking all its children. Do not
// follow links left by another process while cleaning an interrupted install.
fn remove_previous(layout: &Layout) -> crate::Result<()> {
    layout.validate(&layout.previous)?;
    let mut pending = vec![layout.previous.clone()];
    while let Some(path) = pending.pop() {
        layout.validate(&path)?;
        if path.is_dir() {
            for entry in fs::read_dir(path).map_err(IOError::from)? {
                pending.push(entry.map_err(IOError::from)?.path());
            }
        }
    }
    fs::remove_dir_all(&layout.previous).map_err(IOError::from)?;
    Ok(())
}

fn recover_sync(home: &Path, pin: &Pin) -> crate::Result<()> {
    let layout = Layout::new(home, pin)?;
    recover_layout(&layout, pin)
}

fn recover_layout(layout: &Layout, pin: &Pin) -> crate::Result<()> {
    if !layout.previous.exists() {
        return Ok(());
    }
    if !layout.target.exists() {
        fs::rename(&layout.previous, &layout.target).map_err(IOError::from)?;
    } else if ready(&layout.target, pin)? {
        remove_previous(layout)?;
    } else {
        return Err(error(
            "Найдены две незавершённые установки компилятора Horizon. Восстановите сборку перед повторной сборкой клиента.",
        ));
    }
    Ok(())
}

fn swap(layout: &Layout, replacement: &Path) -> crate::Result<()> {
    layout.validate(&layout.target)?;
    layout.validate(&layout.previous)?;
    layout.validate(replacement)?;
    if layout.previous.exists() {
        return Err(error(
            "Предыдущая замена компилятора ещё не восстановлена.",
        ));
    }
    fs::rename(&layout.target, &layout.previous).map_err(IOError::from)?;
    if let Err(err) = fs::rename(replacement, &layout.target) {
        fs::rename(&layout.previous, &layout.target).map_err(IOError::from)?;
        return Err(IOError::from(err).into());
    }
    Ok(())
}

/// Explicit cc-rs tools prevent inherited user overrides and change cc-rs's
/// environment fingerprint once, rebuilding C dependencies after migration.
pub(super) fn configure(
    command: &mut tokio::process::Command,
    home: &Path,
) -> crate::Result<()> {
    let pin = manifest_pin()?;
    let layout = Layout::new(home, &pin)?;
    for (variable, executable) in [
        ("CC_x86_64_pc_windows_gnu", "gcc.exe"),
        ("CXX_x86_64_pc_windows_gnu", "g++.exe"),
        ("AR_x86_64_pc_windows_gnu", "ar.exe"),
    ] {
        let path = checked(&layout.target, &Path::new("bin").join(executable))?;
        command.env(variable, &path);
        command.env(
            variable.replace("x86_64_pc_windows_gnu", "x86_64-pc-windows-gnu"),
            path,
        );
    }
    Ok(())
}

/// Run before `horizon-launcher rust install`, so the helper never sees the
/// brief missing-directory state of an interrupted two-rename replacement.
pub(super) async fn recover(home: &Path) -> crate::Result<()> {
    let _guard = INSTALL.lock().await;
    let home = home.to_owned();
    let pin = manifest_pin()?;
    tokio::task::spawn_blocking(move || recover_sync(&home, &pin))
        .await
        .map_err(|err| error(err.to_string()))?
}

/// Called after the helper has installed Rust; does not run any compiler.
pub(super) async fn ensure(home: &Path) -> crate::Result<()> {
    let _guard = INSTALL.lock().await;
    let pin = manifest_pin()?;
    let home = home.to_owned();
    let check_home = home.clone();
    let installed = tokio::task::spawn_blocking(move || {
        let pin = manifest_pin()?;
        recover_sync(&check_home, &pin)?;
        let layout = Layout::new(&check_home, &pin)?;
        if !layout.target.is_dir() {
            return Err(error(
                "Сначала установите инструменты Rust для Horizon.",
            ));
        }
        ready(&layout.target, &pin)
    })
    .await
    .map_err(|err| error(err.to_string()))??;
    if installed {
        return Ok(());
    }

    let state = State::get().await?;
    let bytes =
        fetch::fetch(&pin.url, None, &state.api_semaphore, &state.pool).await?;
    let expected = pin.sha256.clone();
    let bytes = tokio::task::spawn_blocking(move || {
        if format!("{:x}", Sha256::digest(&bytes)) != expected {
            return Err(error(
                "Контрольная сумма компилятора Horizon не совпадает.",
            ));
        }
        Ok(bytes)
    })
    .await
    .map_err(|err| error(err.to_string()))??;
    let layout = Layout::new(&home, &pin)?;
    let staging_parent = checked(&layout.root, Path::new("rust"))?;
    let staging = tempfile::Builder::new()
        .prefix(".blockera-mingw-staging-")
        .tempdir_in(staging_parent)
        .map_err(IOError::from)?;
    let archive = staging.path().join("toolchain.zip");
    tokio::fs::write(&archive, bytes)
        .await
        .map_err(IOError::from)?;
    let unpacked = staging.path().join("unpacked");
    tokio::fs::create_dir(&unpacked)
        .await
        .map_err(IOError::from)?;
    extract(&archive, &unpacked, true).await?;
    tokio::task::spawn_blocking(move || {
        let replacement = unpacked.join("mingw64");
        layout.validate(&replacement)?;
        validate_payload(&replacement, &pin)?;
        fs::write(replacement.join(MARKER), MANIFEST).map_err(IOError::from)?;
        fs::OpenOptions::new()
            .write(true)
            .open(replacement.join(MARKER))
            .map_err(IOError::from)?
            .sync_all()
            .map_err(IOError::from)?;
        swap(&layout, &replacement)?;
        // A cleanup failure is recoverable on the next call; the new compiler
        // is already complete. Keep the original tree until it can be removed.
        if let Err(err) = remove_previous(&layout) {
            tracing::warn!("Could not remove previous Horizon compiler: {err}");
        }
        drop(staging);
        Ok(())
    })
    .await
    .map_err(|err| error(err.to_string()))?
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fixture() -> (tempfile::TempDir, Pin, Layout) {
        let home = tempfile::tempdir().expect("temporary profile");
        let pin = manifest_pin().unwrap();
        let layout =
            Layout::at_root(dunce::canonicalize(home.path()).unwrap(), &pin)
                .unwrap();
        fs::create_dir_all(&layout.target).unwrap();
        fs::write(layout.target.join("old-compiler"), b"original").unwrap();
        (home, pin, layout)
    }

    fn replacement(layout: &Layout, pin: &Pin) -> PathBuf {
        let root = layout.root.join("replacement");
        for path in [
            "bin/gcc.exe".to_owned(),
            "bin/g++.exe".to_owned(),
            "bin/ar.exe".to_owned(),
            "bin/ld.exe".to_owned(),
            format!("libexec/gcc/x86_64-w64-mingw32/{}/cc1.exe", pin.version),
            format!(
                "libexec/gcc/x86_64-w64-mingw32/{}/cc1plus.exe",
                pin.version
            ),
            format!(
                "lib/gcc/x86_64-w64-mingw32/{}/include/ia32intrin.h",
                pin.version
            ),
            "x86_64-w64-mingw32/include/windows.h".to_owned(),
        ] {
            let path = root.join(path);
            fs::create_dir_all(path.parent().unwrap()).unwrap();
            fs::write(path, b"fixture").unwrap();
        }
        fs::write(root.join(MARKER), MANIFEST).unwrap();
        root
    }

    #[test]
    fn complete_replacement_keeps_pin_and_recovers_cleanup() {
        let (_home, pin, layout) = fixture();
        let replacement = replacement(&layout, &pin);
        validate_payload(&replacement, &pin).unwrap();
        swap(&layout, &replacement).unwrap();
        assert!(ready(&layout.target, &pin).unwrap());
        assert!(layout.previous.join("old-compiler").is_file());
        recover_layout(&layout, &pin).unwrap();
        assert!(!layout.previous.exists());
        assert!(ready(&layout.target, &pin).unwrap());
    }

    #[test]
    fn interrupted_swap_restores_previous_compiler_before_helper_install() {
        let (_home, pin, layout) = fixture();
        fs::rename(&layout.target, &layout.previous).unwrap();
        recover_layout(&layout, &pin).unwrap();
        assert_eq!(
            fs::read(layout.target.join("old-compiler")).unwrap(),
            b"original"
        );
        assert!(!layout.previous.exists());
    }

    #[test]
    fn failed_replacement_rolls_back_the_original_directory() {
        let (_home, _pin, layout) = fixture();
        let missing = layout.root.join("missing-replacement");
        assert!(swap(&layout, &missing).is_err());
        assert_eq!(
            fs::read(layout.target.join("old-compiler")).unwrap(),
            b"original"
        );
        assert!(!layout.previous.exists());
    }

    #[test]
    fn paths_outside_profile_and_incomplete_payload_are_rejected() {
        let (_home, pin, layout) = fixture();
        assert!(checked(&layout.root, Path::new("../outside")).is_err());
        assert!(checked(&layout.root, &layout.target).is_err());
        assert!(validate_payload(&layout.target, &pin).is_err());
        assert!(!ready(&layout.target, &pin).unwrap());
    }

    #[test]
    fn unicode_build_roots_are_ascii_distinct_and_ignore_unsafe_programdata() {
        let manager = Path::new(r"C:\Users\Игрок\Сборка\.horizon-manager");
        let normal = ascii_build_dir(manager, None);
        assert!(tool_safe(&normal));
        assert!(normal.starts_with(r"C:\ProgramData"));
        assert_ne!(
            normal,
            ascii_build_dir(
                Path::new(r"C:\Users\Другой\Сборка\.horizon-manager"),
                None
            )
        );
        assert_eq!(
            normal,
            ascii_build_dir(manager, Some(PathBuf::from(r"C:\Program Data")))
        );
        assert_eq!(
            normal,
            ascii_build_dir(manager, Some(PathBuf::from("relative")))
        );
        assert!(
            ascii_build_dir(manager, Some(PathBuf::from(r"D:\Shared")))
                .starts_with(r"D:\Shared")
        );
        assert!(!tool_safe(manager));
        assert!(!tool_safe(Path::new(r"C:\My Profile")));
    }
}
