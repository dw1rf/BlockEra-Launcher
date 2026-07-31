use std::io::ErrorKind;
use std::path::Path;

const RETIRED_FILES: [&str; 7] = [
    "mods/blockera-client-fabric-0.1.0-dev.jar",
    "mods/blockera-client-fabric-0.1.0-dev.jar.disabled",
    "mods/blockera-runtime-fabric-api-0.141.4+1.21.11.jar",
    "mods/blockera-runtime-fabric-api-0.141.4+1.21.11.jar.disabled",
    "mods/blockera-core-0.4.0.jar",
    "mods/blockera-core-0.4.0.jar.disabled",
    ".blockera/runtime-manifest.json",
];

async fn cleanup_profile_dir(profile_dir: &Path) -> crate::Result<usize> {
    let profile_dir = tokio::fs::canonicalize(profile_dir).await?;
    let mut removed = 0;

    for relative_path in RETIRED_FILES {
        let candidate = profile_dir.join(relative_path);
        let metadata = match tokio::fs::symlink_metadata(&candidate).await {
            Ok(metadata) => metadata,
            Err(error) if error.kind() == ErrorKind::NotFound => continue,
            Err(error) => return Err(error.into()),
        };

        if !metadata.is_file() || metadata.file_type().is_symlink() {
            continue;
        }

        let resolved = tokio::fs::canonicalize(&candidate).await?;
        if !resolved.starts_with(&profile_dir) {
            tracing::warn!(
                artifact = relative_path,
                "Skipped retired Blockera Client artifact outside the profile directory"
            );
            continue;
        }

        tokio::fs::remove_file(candidate).await?;
        removed += 1;
    }

    Ok(removed)
}

pub(super) async fn cleanup_all_profiles() -> crate::Result<usize> {
    let profiles = crate::profile::list().await?;
    let mut removed = 0;

    for profile in profiles {
        let profile_dir = match crate::profile::get_full_path(&profile.path)
            .await
        {
            Ok(profile_dir) => profile_dir,
            Err(error) => {
                tracing::warn!(
                    error = ?error,
                    "Could not resolve a profile while removing retired Blockera Client artifacts"
                );
                continue;
            }
        };

        match cleanup_profile_dir(&profile_dir).await {
            Ok(count) => removed += count,
            Err(error) => tracing::warn!(
                error = ?error,
                "Could not remove retired Blockera Client artifacts from a profile"
            ),
        }
    }

    if removed > 0 {
        tracing::info!(removed, "Removed retired Blockera Client artifacts");
    }

    Ok(removed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn removes_only_known_retired_files() {
        let directory = tempfile::tempdir().unwrap();
        let mods = directory.path().join("mods");
        let manifest_dir = directory.path().join(".blockera");
        tokio::fs::create_dir_all(&mods).await.unwrap();
        tokio::fs::create_dir_all(&manifest_dir).await.unwrap();

        for file in &RETIRED_FILES {
            let path = directory.path().join(file);
            if let Some(parent) = path.parent() {
                tokio::fs::create_dir_all(parent).await.unwrap();
            }
            tokio::fs::write(path, b"managed").await.unwrap();
        }
        let unrelated = mods.join("user-mod.jar");
        tokio::fs::write(&unrelated, b"keep").await.unwrap();

        assert_eq!(cleanup_profile_dir(directory.path()).await.unwrap(), 7);
        assert!(unrelated.is_file());
        for file in RETIRED_FILES {
            assert!(!directory.path().join(file).exists());
        }
    }
}
