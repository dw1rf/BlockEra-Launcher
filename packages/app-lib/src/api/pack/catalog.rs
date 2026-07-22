use crate::State;
use crate::blockera_runtime::{
    BlockeraCoreArtifact, BlockeraIntegrationConfig, validate_core_artifact,
    validate_integration_config,
};
use crate::util::fetch::REQWEST_CLIENT;
use serde::{Deserialize, Serialize};
use std::path::Path;
use url::Url;

const CATALOG_URL: &str = "https://raw.githubusercontent.com/dw1rf/BlockEra-Launcher/main/packages/app-lib/resources/modpacks.json";
const MAX_CATALOG_BYTES: usize = 1024 * 1024;
const BUILTIN_CATALOG: &str = include_str!("../../../resources/modpacks.json");

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct ModpackCatalog {
    pub schema_version: u32,
    pub updated_at: String,
    pub packs: Vec<CatalogModpack>,
}

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct CatalogModpack {
    pub id: String,
    pub source: CatalogPackSource,
    pub title: String,
    pub summary: String,
    pub description: Vec<String>,
    pub mods: Vec<String>,
    pub author: CatalogAuthor,
    pub official_url: String,
    pub original_author: String,
    pub unofficial_integration: bool,
    pub minecraft: String,
    pub forge: String,
    pub version: String,
    pub revision: String,
    pub size: u64,
    pub download: CatalogDownload,
    pub blockera_core: BlockeraCoreArtifact,
    #[serde(default)]
    pub integrations: BlockeraIntegrationConfig,
    pub cover_url: Option<String>,
    pub status: CatalogPackStatus,
    pub permission: String,
}

#[derive(Clone, Copy, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum CatalogPackSource {
    Mrpack,
}

#[derive(Clone, Copy, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum CatalogPackStatus {
    Ready,
}

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct CatalogAuthor {
    pub display_name: String,
    pub channel_name: String,
}

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct CatalogDownload {
    pub url: String,
    pub sha256: String,
}

#[derive(Clone, Copy, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum CatalogOrigin {
    Remote,
    LastKnownGood,
    BuiltIn,
}

#[derive(Clone, Debug, Deserialize, Serialize, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct CatalogResponse {
    pub catalog: ModpackCatalog,
    pub origin: CatalogOrigin,
}

pub fn is_allowed_download_url(value: &str) -> bool {
    let Ok(url) = Url::parse(value) else {
        return false;
    };
    if url.scheme() != "https"
        || !url.username().is_empty()
        || url.password().is_some()
    {
        return false;
    }

    matches!(
        url.host_str()
            .unwrap_or_default()
            .to_ascii_lowercase()
            .as_str(),
        "github.com"
            | "objects.githubusercontent.com"
            | "release-assets.githubusercontent.com"
            | "cdn.modrinth.com"
            | "edge.forgecdn.net"
            | "mediafilez.forgecdn.net"
    )
}

pub fn parse_catalog(value: &str) -> crate::Result<ModpackCatalog> {
    if value.len() > MAX_CATALOG_BYTES {
        return Err(crate::ErrorKind::InputError(
            "Modpack catalog is too large".to_string(),
        )
        .into());
    }

    let catalog: ModpackCatalog = serde_json::from_str(value)?;
    validate_catalog(&catalog)?;
    Ok(catalog)
}

pub fn validate_catalog(catalog: &ModpackCatalog) -> crate::Result<()> {
    if catalog.schema_version != 1 {
        return Err(crate::ErrorKind::InputError(
            "Unsupported modpack catalog schema".to_string(),
        )
        .into());
    }
    if catalog.packs.len() > 100 {
        return Err(crate::ErrorKind::InputError(
            "Modpack catalog contains too many entries".to_string(),
        )
        .into());
    }

    let mut ids = std::collections::HashSet::new();
    for pack in &catalog.packs {
        if pack.id.trim().is_empty() || !ids.insert(&pack.id) {
            return Err(crate::ErrorKind::InputError(
                "Modpack catalog contains an empty or duplicate ID".to_string(),
            )
            .into());
        }
        if pack.title.trim().is_empty()
            || pack.author.display_name.trim().is_empty()
            || pack.author.channel_name.trim().is_empty()
            || pack.official_url.trim().is_empty()
            || pack.permission.trim().is_empty()
        {
            return Err(crate::ErrorKind::InputError(format!(
                "Catalog entry {} is missing required authorship",
                pack.id
            ))
            .into());
        }
        if pack.mods.is_empty()
            || pack.mods.len() > 200
            || pack.mods.iter().any(|name| name.trim().is_empty())
        {
            return Err(crate::ErrorKind::InputError(format!(
                "Catalog entry {} has an invalid mod list",
                pack.id
            ))
            .into());
        }
        if pack.original_author.trim().is_empty()
            || pack.original_author != pack.author.display_name
            || !pack.unofficial_integration
        {
            return Err(crate::ErrorKind::InputError(format!(
                "Catalog entry {} has invalid attribution flags",
                pack.id
            ))
            .into());
        }
        if pack.size == 0
            || pack.download.sha256.len() != 64
            || !pack
                .download
                .sha256
                .bytes()
                .all(|byte| byte.is_ascii_hexdigit())
            || !is_allowed_download_url(&pack.download.url)
        {
            return Err(crate::ErrorKind::InputError(format!(
                "Catalog entry {} has invalid download metadata",
                pack.id
            ))
            .into());
        }
        validate_core_artifact(&pack.blockera_core).map_err(|_| {
            crate::ErrorKind::InputError(format!(
                "Catalog entry {} has invalid blockera-core metadata",
                pack.id
            ))
        })?;
        validate_integration_config(&pack.integrations).map_err(|_| {
            crate::ErrorKind::InputError(format!(
                "Catalog entry {} has invalid Blockera integration endpoints",
                pack.id
            ))
        })?;
        let official_url = Url::parse(&pack.official_url)?;
        if official_url.scheme() != "https" {
            return Err(crate::ErrorKind::InputError(format!(
                "Catalog entry {} has an insecure official URL",
                pack.id
            ))
            .into());
        }
    }

    Ok(())
}

async fn read_cached_catalog(path: &Path) -> Option<ModpackCatalog> {
    let value = tokio::fs::read_to_string(path).await.ok()?;
    parse_catalog(&value).ok()
}

async fn store_cached_catalog(path: &Path, value: &[u8]) -> crate::Result<()> {
    if let Some(parent) = path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let part_path = path.with_extension("json.part");
    tokio::fs::write(&part_path, value).await?;
    if path.exists() {
        tokio::fs::remove_file(path).await?;
    }
    tokio::fs::rename(part_path, path).await?;
    Ok(())
}

fn fallback_catalog(
    cached: Option<ModpackCatalog>,
) -> crate::Result<CatalogResponse> {
    if let Some(catalog) = cached {
        Ok(CatalogResponse {
            catalog,
            origin: CatalogOrigin::LastKnownGood,
        })
    } else {
        Ok(CatalogResponse {
            catalog: parse_catalog(BUILTIN_CATALOG)?,
            origin: CatalogOrigin::BuiltIn,
        })
    }
}

pub async fn get_catalog() -> crate::Result<CatalogResponse> {
    let state = State::get().await?;
    let cache_path = state
        .directories
        .caches_dir()
        .join("catalog")
        .join("modpacks.last-known-good.json");

    let remote = async {
        let response = REQWEST_CLIENT.get(CATALOG_URL).send().await?;
        if !response.status().is_success() {
            return Err(crate::ErrorKind::InputError(format!(
                "Catalog server returned {}",
                response.status()
            ))
            .into());
        }
        if response
            .content_length()
            .is_some_and(|length| length > MAX_CATALOG_BYTES as u64)
        {
            return Err(crate::ErrorKind::InputError(
                "Remote modpack catalog is too large".to_string(),
            )
            .into());
        }
        let bytes = response.bytes().await?;
        if bytes.len() > MAX_CATALOG_BYTES {
            return Err(crate::ErrorKind::InputError(
                "Remote modpack catalog is too large".to_string(),
            )
            .into());
        }
        let text = std::str::from_utf8(&bytes).map_err(|_| {
            crate::ErrorKind::InputError(
                "Remote modpack catalog is not UTF-8".to_string(),
            )
        })?;
        let catalog = parse_catalog(text)?;
        store_cached_catalog(&cache_path, &bytes).await?;
        Ok::<_, crate::Error>(catalog)
    }
    .await;

    match remote {
        Ok(catalog) => Ok(CatalogResponse {
            catalog,
            origin: CatalogOrigin::Remote,
        }),
        Err(error) => {
            tracing::warn!(%error, "Unable to refresh modpack catalog");
            fallback_catalog(read_cached_catalog(&cache_path).await)
        }
    }
}

pub fn builtin_catalog() -> crate::Result<ModpackCatalog> {
    parse_catalog(BUILTIN_CATALOG)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn catalog_json(url: &str, hash: &str) -> String {
        BUILTIN_CATALOG
            .replace(
                "https://github.com/dw1rf/BlockEra-Launcher/releases/download/modpack-nafi-industrial-era-2.0-blockera.1/nafi-aquian-industrial-era-2.0-blockera.1.mrpack",
                url,
            )
            .replace(
                "146fa14f449cc69ab1089576cc1094a008ee3770d39b61e3ef3cdf1d578cc448",
                hash,
            )
    }

    #[test]
    fn builtin_catalog_has_required_attribution() {
        let catalog = builtin_catalog().unwrap();
        let pack = &catalog.packs[0];
        assert_eq!(pack.author.display_name, "Nafi Aquian");
        assert_eq!(pack.original_author, "Nafi Aquian");
        assert!(pack.unofficial_integration);
        assert!(pack.mods.iter().any(|name| name == "Create"));
    }

    #[test]
    fn catalog_rejects_forbidden_domain() {
        let value =
            catalog_json("https://example.com/pack.mrpack", &"a".repeat(64));
        assert!(parse_catalog(&value).is_err());
    }

    #[test]
    fn catalog_rejects_invalid_sha256() {
        let value = catalog_json("https://github.com/a/b", "invalid");
        assert!(parse_catalog(&value).is_err());
    }

    #[test]
    fn official_download_hosts_are_allowed() {
        assert!(is_allowed_download_url(
            "https://cdn.modrinth.com/data/project/file.jar"
        ));
        assert!(is_allowed_download_url(
            "https://edge.forgecdn.net/files/1/2/file.jar"
        ));
        assert!(!is_allowed_download_url("http://github.com/a/b"));
    }

    #[test]
    fn fallback_prefers_last_known_good_over_builtin() {
        let cached = builtin_catalog().unwrap();
        let response = fallback_catalog(Some(cached.clone())).unwrap();
        assert_eq!(response.origin, CatalogOrigin::LastKnownGood);
        assert_eq!(response.catalog, cached);
    }

    #[test]
    fn fallback_uses_builtin_without_cache() {
        let response = fallback_catalog(None).unwrap();
        assert_eq!(response.origin, CatalogOrigin::BuiltIn);
        assert!(!response.catalog.packs.is_empty());
    }
}
