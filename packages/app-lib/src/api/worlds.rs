use crate::data::ModLoader;
use crate::launcher::get_loader_version_from_profile;
use crate::profile::get_full_path;
use crate::server_address::{parse_server_address, resolve_server_address};
use crate::state::attached_world_data::AttachedWorldData;
use crate::state::{
    Profile, ProfileInstallStage, attached_world_data, server_join_log,
};
use crate::util::protocol_version::OLD_PROTOCOL_VERSIONS;
pub use crate::util::protocol_version::ProtocolVersion;
pub use crate::util::server_ping::{
    ServerGameProfile, ServerPlayers, ServerStatus, ServerVersion,
};
use crate::util::{io, server_ping};
use crate::{ErrorKind, Result, State, launcher};
use async_walkdir::WalkDir;
use async_zip::{Compression, ZipEntryBuilder};
use chrono::{DateTime, Local, TimeDelta, TimeZone, Utc};
use either::Either;
use enumset::{EnumSet, EnumSetType};
use fs4::tokio::AsyncFileExt;
use futures::StreamExt;
use quartz_nbt::{NbtCompound, NbtTag};
use regex::{Regex, RegexBuilder};
use serde::{Deserialize, Serialize};
use sqlx::Row;
use std::cmp::Reverse;
use std::io::Cursor;
use std::path::{Component, Path, PathBuf};
use std::sync::LazyLock;
use tokio::io::AsyncWriteExt;
use tokio::task::JoinSet;
use tokio_util::compat::FuturesAsyncWriteCompatExt;
use url::Url;

const BACKUP_MANIFEST_PATH: &str = ".blockera-backup.json";
const BACKUP_FORMAT_VERSION: u32 = 1;
const MAX_RESTORE_ENTRIES: usize = 100_000;
const MAX_RESTORE_BYTES: u64 = 20 * 1024 * 1024 * 1024;

#[derive(Deserialize, Serialize, Debug, Clone, Copy, Eq, PartialEq)]
#[serde(rename_all = "snake_case")]
pub enum WorldBackupReason {
    Manual,
    Scheduled,
    PreUpdate,
    PreRepair,
    PreRestore,
}

#[derive(Deserialize, Serialize, Debug, Clone, Eq, PartialEq)]
#[serde(rename_all = "camelCase")]
pub struct WorldBackupSettings {
    pub enabled: bool,
    pub interval_minutes: u32,
    pub retention_per_world: u32,
}

impl Default for WorldBackupSettings {
    fn default() -> Self {
        Self {
            enabled: false,
            interval_minutes: 60,
            retention_per_world: 5,
        }
    }
}

#[derive(Deserialize, Serialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WorldBackup {
    pub id: String,
    pub profile: String,
    pub world: String,
    pub created_at: DateTime<Utc>,
    pub size: u64,
    pub reason: WorldBackupReason,
    pub path: PathBuf,
}

#[derive(Deserialize, Serialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
struct WorldBackupManifest {
    format_version: u32,
    profile: String,
    world: String,
    created_at: DateTime<Utc>,
    reason: WorldBackupReason,
}

#[derive(Deserialize, Serialize, Debug, Clone)]
#[serde(rename_all = "camelCase")]
pub struct WorldBackupFailure {
    pub world: String,
    pub error: String,
}

#[derive(Deserialize, Serialize, Debug, Clone, Default)]
#[serde(rename_all = "camelCase")]
pub struct BackupBatchResult {
    pub count: usize,
    pub total_bytes: u64,
    pub backups: Vec<WorldBackup>,
    pub failures: Vec<WorldBackupFailure>,
}

#[derive(Deserialize, Serialize, Debug, Clone)]
pub struct WorldWithProfile {
    pub profile: String,
    #[serde(flatten)]
    pub world: World,
}

#[derive(Deserialize, Serialize, Debug, Clone)]
pub struct World {
    pub name: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub last_played: Option<DateTime<Utc>>,
    #[serde(
        skip_serializing_if = "Option::is_none",
        with = "either::serde_untagged_optional"
    )]
    pub icon: Option<Either<PathBuf, Url>>,
    pub display_status: DisplayStatus,
    #[serde(flatten)]
    pub details: WorldDetails,
}

impl World {
    pub fn world_type(&self) -> WorldType {
        match self.details {
            WorldDetails::Singleplayer { .. } => WorldType::Singleplayer,
            WorldDetails::Server { .. } => WorldType::Server,
        }
    }

    pub fn world_id(&self) -> &str {
        match &self.details {
            WorldDetails::Singleplayer { path, .. } => path,
            WorldDetails::Server { address, .. } => address,
        }
    }
}

#[derive(
    Serialize, Deserialize, Debug, Copy, Clone, PartialEq, Eq, Hash, Default,
)]
#[serde(rename_all = "snake_case")]
pub enum WorldType {
    #[default]
    Singleplayer,
    Server,
}

impl WorldType {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Singleplayer => "singleplayer",
            Self::Server => "server",
        }
    }

    pub fn from_string(string: &str) -> Self {
        match string {
            "singleplayer" => Self::Singleplayer,
            "server" => Self::Server,
            _ => Self::Singleplayer,
        }
    }
}

#[derive(Deserialize, Serialize, EnumSetType, Debug, Default)]
#[serde(rename_all = "snake_case")]
#[enumset(serialize_repr = "list")]
pub enum DisplayStatus {
    #[default]
    Normal,
    Hidden,
    Favorite,
}

impl DisplayStatus {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Normal => "normal",
            Self::Hidden => "hidden",
            Self::Favorite => "favorite",
        }
    }

    pub fn from_string(string: &str) -> Self {
        match string {
            "normal" => Self::Normal,
            "hidden" => Self::Hidden,
            "favorite" => Self::Favorite,
            _ => Self::Normal,
        }
    }
}

#[derive(Deserialize, Serialize, Debug, Clone)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum WorldDetails {
    Singleplayer {
        path: String,
        game_mode: SingleplayerGameMode,
        hardcore: bool,
        locked: bool,
    },
    Server {
        index: usize,
        address: String,
        pack_status: ServerPackStatus,
    },
}

#[derive(Deserialize, Serialize, Debug, Copy, Clone, Default)]
#[serde(rename_all = "snake_case")]
pub enum SingleplayerGameMode {
    #[default]
    Survival,
    Creative,
    Adventure,
    Spectator,
}

#[derive(Deserialize, Serialize, Debug, Copy, Clone, Default)]
#[serde(rename_all = "snake_case")]
pub enum ServerPackStatus {
    Enabled,
    Disabled,
    #[default]
    Prompt,
}

impl From<Option<bool>> for ServerPackStatus {
    fn from(value: Option<bool>) -> Self {
        match value {
            Some(true) => ServerPackStatus::Enabled,
            Some(false) => ServerPackStatus::Disabled,
            None => ServerPackStatus::Prompt,
        }
    }
}

impl From<ServerPackStatus> for Option<bool> {
    fn from(val: ServerPackStatus) -> Self {
        match val {
            ServerPackStatus::Enabled => Some(true),
            ServerPackStatus::Disabled => Some(false),
            ServerPackStatus::Prompt => None,
        }
    }
}

pub async fn get_recent_worlds(
    limit: usize,
    display_statuses: EnumSet<DisplayStatus>,
) -> Result<Vec<WorldWithProfile>> {
    let state = State::get().await?;
    let profiles_dir = state.directories.profiles_dir();

    let mut profiles = Profile::get_all(&state.pool).await?;
    profiles.sort_by_key(|x| Reverse(x.last_played));

    let mut result = Vec::with_capacity(limit);

    let mut least_recent_time = None;
    for profile in profiles {
        if result.len() >= limit && profile.last_played < least_recent_time {
            break;
        }
        let profile_path = &profile.path;
        let profile_dir = profiles_dir.join(profile_path);
        let profile_worlds =
            get_all_worlds_in_profile(profile_path, &profile_dir).await;
        if let Err(e) = profile_worlds {
            tracing::error!(
                "Failed to get worlds for profile {}: {}",
                profile_path,
                e
            );
            continue;
        }
        for world in profile_worlds? {
            let is_older = least_recent_time.is_none()
                || world.last_played < least_recent_time;
            if result.len() >= limit && is_older {
                continue;
            }
            if !display_statuses.contains(world.display_status) {
                continue;
            }
            if is_older {
                least_recent_time = world.last_played;
            }
            result.push(WorldWithProfile {
                profile: profile_path.clone(),
                world,
            });
        }
        if result.len() > limit {
            result.sort_by_key(|x| Reverse(x.world.last_played));
            result.truncate(limit);
        }
    }

    if result.len() <= limit {
        result.sort_by_key(|x| Reverse(x.world.last_played));
    }
    Ok(result)
}

pub async fn get_profile_worlds(profile_path: &str) -> Result<Vec<World>> {
    get_all_worlds_in_profile(profile_path, &get_full_path(profile_path).await?)
        .await
}

async fn get_all_worlds_in_profile(
    profile_path: &str,
    profile_dir: &Path,
) -> Result<Vec<World>> {
    let mut worlds = vec![];
    get_singleplayer_worlds_in_profile(profile_dir, &mut worlds).await?;
    get_server_worlds_in_profile(profile_path, profile_dir, &mut worlds)
        .await?;

    let state = State::get().await?;
    let attached_data =
        AttachedWorldData::get_all_for_instance(profile_path, &state.pool)
            .await?;
    if !attached_data.is_empty() {
        for world in &mut worlds {
            if let Some(data) = attached_data
                .get(&(world.world_type(), world.world_id().to_owned()))
            {
                attach_world_data_to_world(world, data);
            }
        }
    }

    Ok(worlds)
}

async fn get_singleplayer_worlds_in_profile(
    instance_dir: &Path,
    worlds: &mut Vec<World>,
) -> Result<()> {
    let saves_dir = instance_dir.join("saves");
    if !saves_dir.exists() {
        return Ok(());
    }
    let mut saves_dir = io::read_dir(saves_dir).await?;
    while let Some(world_dir) = saves_dir.next_entry().await? {
        let world_path = world_dir.path();
        let level_dat_path = world_path.join("level.dat");
        if !level_dat_path.exists() {
            continue;
        }
        if let Ok(world) = read_singleplayer_world(world_path).await {
            worlds.push(world);
        }
    }

    Ok(())
}

pub async fn get_singleplayer_world(
    instance: &str,
    world: &str,
) -> Result<World> {
    let state = State::get().await?;
    let profile_path = state.directories.profiles_dir().join(instance);
    let mut world =
        read_singleplayer_world(get_world_dir(&profile_path, world)).await?;

    if let Some(data) = AttachedWorldData::get_for_world(
        instance,
        world.world_type(),
        world.world_id(),
        &state.pool,
    )
    .await?
    {
        attach_world_data_to_world(&mut world, &data);
    }
    Ok(world)
}

async fn read_singleplayer_world(world_path: PathBuf) -> Result<World> {
    if let Some(_lock) = try_get_world_session_lock(&world_path).await? {
        read_singleplayer_world_maybe_locked(world_path, false).await
    } else {
        read_singleplayer_world_maybe_locked(world_path, true).await
    }
}

async fn read_singleplayer_world_maybe_locked(
    world_path: PathBuf,
    locked: bool,
) -> Result<World> {
    #[derive(Deserialize, Debug)]
    #[serde(rename_all = "PascalCase")]
    struct LevelDataRoot {
        data: LevelData,
    }

    #[derive(Deserialize, Debug)]
    #[serde(rename_all = "PascalCase")]
    struct LevelData {
        #[serde(default)]
        level_name: String,
        #[serde(default)]
        last_played: i64,
        #[serde(default)]
        game_type: i32,
        #[serde(default, rename = "hardcore")]
        hardcore: bool,
    }

    let level_data = io::read(world_path.join("level.dat")).await?;
    let level_data: LevelDataRoot = quartz_nbt::serde::deserialize(
        &level_data,
        quartz_nbt::io::Flavor::GzCompressed,
    )?
    .0;
    let level_data = level_data.data;

    let icon = Some(world_path.join("icon.png")).filter(|i| i.exists());

    let game_mode = match level_data.game_type {
        0 => SingleplayerGameMode::Survival,
        1 => SingleplayerGameMode::Creative,
        2 => SingleplayerGameMode::Adventure,
        3 => SingleplayerGameMode::Spectator,
        _ => SingleplayerGameMode::Survival,
    };

    Ok(World {
        name: level_data.level_name,
        last_played: Utc.timestamp_millis_opt(level_data.last_played).single(),
        icon: icon.map(Either::Left),
        display_status: DisplayStatus::Normal,
        details: WorldDetails::Singleplayer {
            path: world_path
                .file_name()
                .unwrap()
                .to_string_lossy()
                .to_string(),
            game_mode,
            hardcore: level_data.hardcore,
            locked,
        },
    })
}

async fn get_server_worlds_in_profile(
    profile_path: &str,
    instance_dir: &Path,
    worlds: &mut Vec<World>,
) -> Result<()> {
    let servers = servers_data::read(instance_dir).await?;
    if servers.is_empty() {
        return Ok(());
    }

    let state = State::get().await?;
    let join_log = server_join_log::get_joins(profile_path, &state.pool)
        .await
        .ok();

    let first_server_index = worlds.len();
    for (index, server) in servers.into_iter().enumerate() {
        if server.hidden {
            // TODO: Figure out whether we want to hide or show direct connect servers
            continue;
        }
        let world = World {
            name: server.name,
            last_played: join_log
                .as_ref()
                .and_then(|log| {
                    let (host, port) = parse_server_address(&server.ip).ok()?;
                    log.get(&(host.to_owned(), port))
                })
                .copied(),
            icon: server
                .icon
                .and_then(|icon| {
                    Url::parse(&format!("data:image/png;base64,{icon}")).ok()
                })
                .map(Either::Right),
            display_status: DisplayStatus::Normal,
            details: WorldDetails::Server {
                index,
                address: server.ip,
                pack_status: server.accept_textures.into(),
            },
        };
        worlds.push(world);
    }

    if let Some(join_log) = join_log {
        let mut futures = JoinSet::new();
        for (index, world) in worlds.iter().enumerate().skip(first_server_index)
        {
            // We can't check for the profile already having a last_played, in case the user joined
            // the target address directly more recently. This is often the case when using
            // quick-play before 1.20.
            if let WorldDetails::Server { address, .. } = &world.details
                && let Ok((host, port)) = parse_server_address(address)
            {
                let host = host.to_owned();
                futures.spawn(async move {
                    resolve_server_address(&host, port)
                        .await
                        .ok()
                        .map(|x| (index, x))
                });
            }
        }
        for (index, address) in futures.join_all().await.into_iter().flatten() {
            worlds[index].last_played = join_log.get(&address).copied();
        }
    }

    Ok(())
}

fn attach_world_data_to_world(world: &mut World, data: &AttachedWorldData) {
    world.display_status = data.display_status;
}

pub async fn set_world_display_status(
    instance: &str,
    world_type: WorldType,
    world_id: &str,
    display_status: DisplayStatus,
) -> Result<()> {
    let state = State::get().await?;
    attached_world_data::set_display_status(
        instance,
        world_type,
        world_id,
        display_status,
        &state.pool,
    )
    .await?;
    Ok(())
}

pub async fn rename_world(
    instance: &Path,
    world: &str,
    new_name: &str,
) -> Result<()> {
    let world = get_world_dir(instance, world);
    let level_dat_path = world.join("level.dat");
    if !level_dat_path.exists() {
        return Ok(());
    }
    let _lock = get_world_session_lock(&world).await?;

    let level_data = io::read(&level_dat_path).await?;
    let (mut root_data, _) = quartz_nbt::io::read_nbt(
        &mut Cursor::new(level_data),
        quartz_nbt::io::Flavor::GzCompressed,
    )?;
    let data = root_data.get_mut::<_, &mut NbtCompound>("Data")?;

    data.insert(
        "LevelName",
        NbtTag::String(new_name.trim_ascii().to_string()),
    );

    let mut level_data = vec![];
    quartz_nbt::io::write_nbt(
        &mut level_data,
        None,
        &root_data,
        quartz_nbt::io::Flavor::GzCompressed,
    )?;
    io::write(level_dat_path, level_data).await?;
    Ok(())
}

pub async fn reset_world_icon(instance: &Path, world: &str) -> Result<()> {
    let world = get_world_dir(instance, world);
    let icon = world.join("icon.png");
    if let Some(_lock) = try_get_world_session_lock(&world).await? {
        let _ = io::remove_file(icon).await;
    }
    Ok(())
}

fn validate_world_identifier(world: &str) -> Result<()> {
    let mut components = Path::new(world).components();
    if world.is_empty()
        || !matches!(components.next(), Some(Component::Normal(_)))
        || components.next().is_some()
    {
        return Err(ErrorKind::InputError(
            "World path must be a single directory name".to_string(),
        )
        .into());
    }
    Ok(())
}

pub async fn backup_world(instance: &Path, world: &str) -> Result<u64> {
    let profile = instance
        .file_name()
        .and_then(|name| name.to_str())
        .unwrap_or_default();
    let backup = backup_world_archive(
        instance,
        profile,
        world,
        WorldBackupReason::Manual,
    )
    .await?;
    let settings = get_world_backup_settings().await?;
    prune_world_backups(
        instance,
        profile,
        world,
        settings.retention_per_world as usize,
    )
    .await?;
    Ok(backup.size)
}

fn verify_backup_archive(path: &Path, world: &str) -> Result<()> {
    let file = std::fs::File::open(path)?;
    let mut archive = zip::ZipArchive::new(file).map_err(|error| {
        ErrorKind::InputError(format!("Backup archive is invalid: {error}"))
    })?;
    let manifest = {
        let mut entry =
            archive.by_name(BACKUP_MANIFEST_PATH).map_err(|_| {
                ErrorKind::InputError("Backup manifest is missing".to_string())
            })?;
        let mut value = String::new();
        std::io::Read::read_to_string(&mut entry, &mut value)?;
        serde_json::from_str::<WorldBackupManifest>(&value)?
    };
    if manifest.format_version != BACKUP_FORMAT_VERSION
        || manifest.world != world
    {
        return Err(ErrorKind::InputError(
            "Backup manifest does not match the world".to_string(),
        )
        .into());
    }

    let mut has_level_dat = false;
    for index in 0..archive.len() {
        let entry = archive.by_index(index).map_err(|error| {
            ErrorKind::InputError(format!(
                "Unable to verify backup entry: {error}"
            ))
        })?;
        if entry.name() == BACKUP_MANIFEST_PATH {
            continue;
        }
        let enclosed = entry.enclosed_name().ok_or_else(|| {
            ErrorKind::InputError("Backup contains an unsafe path".to_string())
        })?;
        let relative = enclosed.strip_prefix(world).map_err(|_| {
            ErrorKind::InputError(
                "Backup contains files for another world".to_string(),
            )
        })?;
        has_level_dat |= relative == Path::new("level.dat");
    }
    if !has_level_dat {
        return Err(ErrorKind::InputError(
            "Backup does not contain level.dat".to_string(),
        )
        .into());
    }
    Ok(())
}

pub async fn backup_world_archive(
    instance: &Path,
    profile: &str,
    world: &str,
    reason: WorldBackupReason,
) -> Result<WorldBackup> {
    validate_world_identifier(world)?;
    let world_dir = get_world_dir(instance, world);
    let _lock = get_world_session_lock(&world_dir).await?;
    let backups_dir = instance.join("backups");

    io::create_dir_all(&backups_dir).await?;

    let created_at = Utc::now();
    let name_base = {
        let formatted_time = Local::now().format("%Y-%m-%d_%H-%M-%S");
        format!("{formatted_time}_{world}")
    };
    let output_path =
        backups_dir.join(find_available_name(&backups_dir, &name_base, ".zip"));
    let part_path = backups_dir.join(format!(
        ".{}.{}.part",
        output_path
            .file_name()
            .and_then(|name| name.to_str())
            .unwrap_or("backup.zip"),
        uuid::Uuid::new_v4()
    ));
    let manifest = WorldBackupManifest {
        format_version: BACKUP_FORMAT_VERSION,
        profile: profile.to_string(),
        world: world.to_string(),
        created_at,
        reason,
    };

    let write_result = async {
        let writer = tokio::fs::File::create(&part_path).await?;
        let mut writer =
            async_zip::tokio::write::ZipFileWriter::with_tokio(writer);
        let manifest_data = serde_json::to_vec(&manifest)?;
        writer
            .write_entry_whole(
                ZipEntryBuilder::new(
                    BACKUP_MANIFEST_PATH.into(),
                    Compression::Deflate,
                ),
                &manifest_data,
            )
            .await?;

        let mut walker = WalkDir::new(&world_dir);
        while let Some(entry) = walker.next().await {
            let entry = entry.map_err(|e| io::IOError::IOPathError {
                path: e.path().unwrap().to_string_lossy().to_string(),
                source: e.into_io().unwrap(),
            })?;
            if !entry.file_type().await?.is_file()
                || entry.file_name() == "session.lock"
            {
                continue;
            }
            let zip_filename = format!(
                "{world}/{}",
                entry
                    .path()
                    .strip_prefix(&world_dir)?
                    .display()
                    .to_string()
                    .replace('\\', "/")
            );
            let mut stream = writer
                .write_entry_stream(
                    ZipEntryBuilder::new(
                        zip_filename.into(),
                        Compression::Deflate,
                    )
                    .build(),
                )
                .await?
                .compat_write();
            let mut source = tokio::fs::File::open(entry.path()).await?;
            tokio::io::copy(&mut source, &mut stream).await?;
            stream.into_inner().close().await?;
        }

        writer.close().await?;
        let verify_path = part_path.clone();
        let verify_world = world.to_string();
        tokio::task::spawn_blocking(move || {
            verify_backup_archive(&verify_path, &verify_world)
        })
        .await??;
        tokio::fs::rename(&part_path, &output_path).await?;
        Ok::<(), crate::Error>(())
    }
    .await;

    if let Err(error) = write_result {
        let _ = tokio::fs::remove_file(&part_path).await;
        return Err(error);
    }

    Ok(WorldBackup {
        id: output_path
            .file_name()
            .unwrap_or_default()
            .to_string_lossy()
            .into_owned(),
        profile: profile.to_string(),
        world: world.to_string(),
        created_at,
        size: io::metadata(&output_path).await?.len(),
        reason,
        path: output_path,
    })
}

pub async fn get_world_backup_settings() -> Result<WorldBackupSettings> {
    let state = State::get().await?;
    let row = sqlx::query(
        "SELECT enabled, interval_minutes, retention_per_world FROM world_backup_settings WHERE id = 0",
    )
    .fetch_optional(&state.pool)
    .await?;
    let Some(row) = row else {
        return Ok(WorldBackupSettings::default());
    };
    Ok(WorldBackupSettings {
        enabled: row.try_get::<i64, _>("enabled")? != 0,
        interval_minutes: row.try_get::<i64, _>("interval_minutes")? as u32,
        retention_per_world: row.try_get::<i64, _>("retention_per_world")?
            as u32,
    })
}

pub async fn set_world_backup_settings(
    settings: WorldBackupSettings,
) -> Result<()> {
    if !(5..=10_080).contains(&settings.interval_minutes)
        || !(1..=100).contains(&settings.retention_per_world)
    {
        return Err(ErrorKind::InputError(
            "Invalid world backup settings".to_string(),
        )
        .into());
    }
    let state = State::get().await?;
    sqlx::query(
        "UPDATE world_backup_settings SET enabled = ?, interval_minutes = ?, retention_per_world = ? WHERE id = 0",
    )
    .bind(settings.enabled)
    .bind(settings.interval_minutes as i64)
    .bind(settings.retention_per_world as i64)
    .execute(&state.pool)
    .await?;
    Ok(())
}

fn read_backup_metadata(
    path: PathBuf,
    fallback_profile: String,
) -> Option<WorldBackup> {
    let file = std::fs::File::open(&path).ok()?;
    let size = file.metadata().ok()?.len();
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let manifest =
        archive
            .by_name(BACKUP_MANIFEST_PATH)
            .ok()
            .and_then(|mut entry| {
                let mut value = String::new();
                std::io::Read::read_to_string(&mut entry, &mut value).ok()?;
                serde_json::from_str::<WorldBackupManifest>(&value).ok()
            });
    let id = path.file_name()?.to_string_lossy().into_owned();
    if let Some(manifest) = manifest {
        if manifest.format_version != BACKUP_FORMAT_VERSION {
            return None;
        }
        return Some(WorldBackup {
            id,
            profile: manifest.profile,
            world: manifest.world,
            created_at: manifest.created_at,
            size,
            reason: manifest.reason,
            path,
        });
    }

    let legacy = Regex::new(
        r"^\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}_(?<world>.+?)(?: \(\d+\))?\.zip$",
    )
    .ok()?;
    let world = legacy.captures(&id)?.name("world")?.as_str().to_string();
    let modified = std::fs::metadata(&path).ok()?.modified().ok()?;
    Some(WorldBackup {
        id,
        profile: fallback_profile,
        world,
        created_at: DateTime::<Utc>::from(modified),
        size,
        reason: WorldBackupReason::Manual,
        path,
    })
}

pub async fn list_world_backups(
    instance: &Path,
    profile: &str,
    world: Option<&str>,
) -> Result<Vec<WorldBackup>> {
    if let Some(world) = world {
        validate_world_identifier(world)?;
    }
    let backups_dir = instance.join("backups");
    let profile = profile.to_string();
    let mut backups = tokio::task::spawn_blocking(move || {
        let Ok(entries) = std::fs::read_dir(backups_dir) else {
            return Vec::new();
        };
        entries
            .filter_map(|entry| entry.ok())
            .map(|entry| entry.path())
            .filter(|path| {
                path.extension().is_some_and(|extension| {
                    extension.eq_ignore_ascii_case("zip")
                })
            })
            .filter_map(|path| read_backup_metadata(path, profile.clone()))
            .collect::<Vec<_>>()
    })
    .await?;
    if let Some(world) = world {
        backups.retain(|backup| backup.world == world);
    }
    backups.sort_by_key(|backup| Reverse(backup.created_at));
    Ok(backups)
}

async fn prune_world_backups(
    instance: &Path,
    profile: &str,
    world: &str,
    retention: usize,
) -> Result<()> {
    let backups = list_world_backups(instance, profile, Some(world)).await?;
    for backup in backups.into_iter().skip(retention) {
        io::remove_file(backup.path).await?;
    }
    Ok(())
}

pub async fn backup_profile_worlds(
    profile: &str,
    reason: WorldBackupReason,
) -> Result<BackupBatchResult> {
    let instance = get_full_path(profile).await?;
    let settings = get_world_backup_settings().await?;
    let worlds = get_profile_worlds(profile).await?;
    let mut result = BackupBatchResult::default();

    for world in worlds {
        let WorldDetails::Singleplayer { path, .. } = world.details else {
            continue;
        };
        match backup_world_archive(&instance, profile, &path, reason).await {
            Ok(backup) => {
                result.count += 1;
                result.total_bytes =
                    result.total_bytes.saturating_add(backup.size);
                result.backups.push(backup);
                if let Err(error) = prune_world_backups(
                    &instance,
                    profile,
                    &path,
                    settings.retention_per_world as usize,
                )
                .await
                {
                    result.failures.push(WorldBackupFailure {
                        world: world.name.clone(),
                        error: format!(
                            "Backup created, but cleanup failed: {error}"
                        ),
                    });
                }
            }
            Err(error) => result.failures.push(WorldBackupFailure {
                world: world.name,
                error: error.to_string(),
            }),
        }
    }
    Ok(result)
}

pub async fn delete_world_backup(profile: &str, backup_id: &str) -> Result<()> {
    validate_world_identifier(backup_id)?;
    let instance = get_full_path(profile).await?;
    let backup = list_world_backups(&instance, profile, None)
        .await?
        .into_iter()
        .find(|backup| backup.id == backup_id)
        .ok_or_else(|| {
            ErrorKind::InputError("Backup was not found".to_string())
        })?;
    io::remove_file(backup.path).await?;
    Ok(())
}

fn restore_backup_sync(
    backup_path: &Path,
    saves_dir: &Path,
    world: &str,
) -> Result<()> {
    let file = std::fs::File::open(backup_path)?;
    let mut archive = zip::ZipArchive::new(file).map_err(|error| {
        ErrorKind::InputError(format!("Backup archive is invalid: {error}"))
    })?;
    if archive.is_empty() || archive.len() > MAX_RESTORE_ENTRIES {
        return Err(ErrorKind::InputError(
            "Backup archive has an invalid entry count".to_string(),
        )
        .into());
    }

    let restore_root =
        saves_dir.join(format!(".blockera-restore-{}", uuid::Uuid::new_v4()));
    let restore_world = restore_root.join(world);
    std::fs::create_dir_all(&restore_world)?;
    let mut unpacked = 0_u64;

    let extraction = (|| -> Result<()> {
        for index in 0..archive.len() {
            let mut entry = archive.by_index(index).map_err(|error| {
                ErrorKind::InputError(format!(
                    "Unable to read backup entry: {error}"
                ))
            })?;
            if entry.name() == BACKUP_MANIFEST_PATH {
                continue;
            }
            unpacked = unpacked.checked_add(entry.size()).ok_or_else(|| {
                ErrorKind::InputError("Backup size overflow".to_string())
            })?;
            if unpacked > MAX_RESTORE_BYTES {
                return Err(ErrorKind::InputError(
                    "Backup unpacked size exceeds the limit".to_string(),
                )
                .into());
            }
            let enclosed = entry.enclosed_name().ok_or_else(|| {
                ErrorKind::InputError(
                    "Backup contains an unsafe path".to_string(),
                )
            })?;
            let relative = enclosed.strip_prefix(world).map_err(|_| {
                ErrorKind::InputError(
                    "Backup contains files for another world".to_string(),
                )
            })?;
            if relative.as_os_str().is_empty() {
                continue;
            }
            let destination = restore_world.join(relative);
            if entry.is_dir() {
                std::fs::create_dir_all(&destination)?;
            } else {
                if let Some(parent) = destination.parent() {
                    std::fs::create_dir_all(parent)?;
                }
                let mut output = std::fs::File::create(&destination)?;
                std::io::copy(&mut entry, &mut output)?;
            }
        }
        if !restore_world.join("level.dat").is_file() {
            return Err(ErrorKind::InputError(
                "Backup does not contain level.dat".to_string(),
            )
            .into());
        }
        Ok(())
    })();
    if let Err(error) = extraction {
        let _ = std::fs::remove_dir_all(&restore_root);
        return Err(error);
    }

    let current_world = saves_dir.join(world);
    let previous_world =
        saves_dir.join(format!(".blockera-previous-{}", uuid::Uuid::new_v4()));
    std::fs::rename(&current_world, &previous_world)?;
    if let Err(error) = std::fs::rename(&restore_world, &current_world) {
        let _ = std::fs::rename(&previous_world, &current_world);
        let _ = std::fs::remove_dir_all(&restore_root);
        return Err(error.into());
    }
    let _ = std::fs::remove_dir_all(&previous_world);
    let _ = std::fs::remove_dir_all(&restore_root);
    Ok(())
}

pub async fn restore_world_backup(
    profile: &str,
    backup_id: &str,
) -> Result<()> {
    validate_world_identifier(backup_id)?;
    if !crate::process::get_by_profile_path(profile)
        .await?
        .is_empty()
    {
        return Err(ErrorKind::InputError(
            "Stop Minecraft before restoring a world".to_string(),
        )
        .into());
    }
    let instance = get_full_path(profile).await?;
    let backup = list_world_backups(&instance, profile, None)
        .await?
        .into_iter()
        .find(|backup| backup.id == backup_id)
        .ok_or_else(|| {
            ErrorKind::InputError("Backup was not found".to_string())
        })?;
    let safety = backup_world_archive(
        &instance,
        profile,
        &backup.world,
        WorldBackupReason::PreRestore,
    )
    .await?;
    let saves_dir = instance.join("saves");
    let world = backup.world.clone();
    let backup_path = backup.path.clone();
    let result = tokio::task::spawn_blocking(move || {
        restore_backup_sync(&backup_path, &saves_dir, &world)
    })
    .await?;
    if result.is_err() {
        tracing::warn!(
            safety_backup = %safety.path.display(),
            "World restore failed; safety backup was preserved"
        );
    } else {
        let settings = get_world_backup_settings().await?;
        prune_world_backups(
            &instance,
            profile,
            &backup.world,
            settings.retention_per_world as usize,
        )
        .await?;
    }
    result
}

pub async fn run_due_world_backups() -> Result<Vec<BackupBatchResult>> {
    let settings = get_world_backup_settings().await?;
    if !settings.enabled {
        return Ok(Vec::new());
    }
    let cutoff =
        Utc::now() - TimeDelta::minutes(settings.interval_minutes as i64);
    let mut batches = Vec::new();
    for profile in crate::profile::list().await? {
        if !crate::process::get_by_profile_path(&profile.path)
            .await?
            .is_empty()
        {
            continue;
        }
        let instance = get_full_path(&profile.path).await?;
        let existing =
            list_world_backups(&instance, &profile.path, None).await?;
        let worlds = get_profile_worlds(&profile.path).await?;
        let mut batch = BackupBatchResult::default();
        for world in worlds {
            let WorldDetails::Singleplayer { path, .. } = world.details else {
                continue;
            };
            let last = existing
                .iter()
                .filter(|backup| backup.world == path)
                .map(|backup| backup.created_at)
                .max();
            if last.is_some_and(|created_at| created_at > cutoff) {
                continue;
            }
            match backup_world_archive(
                &instance,
                &profile.path,
                &path,
                WorldBackupReason::Scheduled,
            )
            .await
            {
                Ok(backup) => {
                    batch.count += 1;
                    batch.total_bytes =
                        batch.total_bytes.saturating_add(backup.size);
                    batch.backups.push(backup);
                    if let Err(error) = prune_world_backups(
                        &instance,
                        &profile.path,
                        &path,
                        settings.retention_per_world as usize,
                    )
                    .await
                    {
                        batch.failures.push(WorldBackupFailure {
                            world: world.name.clone(),
                            error: format!(
                                "Backup created, but cleanup failed: {error}"
                            ),
                        });
                    }
                }
                Err(error) => batch.failures.push(WorldBackupFailure {
                    world: world.name,
                    error: error.to_string(),
                }),
            }
        }
        if batch.count > 0 || !batch.failures.is_empty() {
            batches.push(batch);
        }
    }
    Ok(batches)
}

fn find_available_name(dir: &Path, file_name: &str, extension: &str) -> String {
    static RESERVED_WINDOWS_FILENAMES: LazyLock<Regex> = LazyLock::new(|| {
        RegexBuilder::new(r#"^.*\.|(?:COM|CLOCK\$|CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\..*)?$"#)
            .case_insensitive(true)
            .build()
            .unwrap()
    });
    static COPY_COUNTER_PATTERN: LazyLock<Regex> = LazyLock::new(|| {
        RegexBuilder::new(r#"^(?<name>.*) \((?<count>\d*)\)$"#)
            .case_insensitive(true)
            .unicode(true)
            .build()
            .unwrap()
    });

    let mut file_name = file_name.replace(
        [
            '/', '\n', '\r', '\t', '\0', '\x0c', '`', '?', '*', '\\', '<', '>',
            '|', '"', ':', '.', '/', '"',
        ],
        "_",
    );
    if RESERVED_WINDOWS_FILENAMES.is_match(&file_name) {
        file_name.insert(0, '_');
        file_name.push('_');
    }

    let mut count = 0;
    if let Some(find) = COPY_COUNTER_PATTERN.captures(&file_name) {
        count = find
            .name("count")
            .unwrap()
            .as_str()
            .parse::<i32>()
            .unwrap_or(0);
        let end = find.name("name").unwrap().end();
        drop(find);
        file_name.truncate(end);
    }

    if file_name.len() > 255 - extension.len() {
        file_name.truncate(255 - extension.len());
    }

    let mut current_attempt = file_name.clone();
    loop {
        if count != 0 {
            let with_count = format!(" ({count})");
            if file_name.len() > 255 - with_count.len() {
                current_attempt.truncate(255 - with_count.len());
            }
            current_attempt.push_str(&with_count);
        }

        current_attempt.push_str(extension);

        let result = dir.join(&current_attempt);
        if !result.exists() {
            return current_attempt;
        }

        count += 1;
        current_attempt.replace_range(..current_attempt.len(), &file_name);
    }
}

pub async fn delete_world(instance: &Path, world: &str) -> Result<()> {
    let world = get_world_dir(instance, world);
    let lock = get_world_session_lock(&world).await?;
    let lock_path = world.join("session.lock");

    let mut dir = io::read_dir(&world).await?;
    while let Some(entry) = dir.next_entry().await? {
        let path = entry.path();
        if entry.file_type().await?.is_dir() {
            io::remove_dir_all(path).await?;
            continue;
        }
        if path != lock_path {
            io::remove_file(path).await?;
        }
    }

    drop(lock);
    io::remove_file(lock_path).await?;
    io::remove_dir(world).await?;

    Ok(())
}

fn get_world_dir(instance: &Path, world: &str) -> PathBuf {
    instance.join("saves").join(world)
}

async fn get_world_session_lock(world: &Path) -> Result<tokio::fs::File> {
    let lock_path = world.join("session.lock");
    let mut file = tokio::fs::File::options()
        .create(true)
        .write(true)
        .truncate(false)
        .open(&lock_path)
        .await?;
    file.write_all("☃".as_bytes()).await?;
    file.sync_all().await?;
    let locked = file.try_lock_exclusive()?;
    locked.then_some(file).ok_or_else(|| {
        io::IOError::IOPathError {
            source: std::io::Error::new(
                std::io::ErrorKind::ResourceBusy,
                "already locked by Minecraft",
            ),
            path: lock_path.to_string_lossy().into_owned(),
        }
        .into()
    })
}

async fn try_get_world_session_lock(
    world: &Path,
) -> Result<Option<tokio::fs::File>> {
    let file = tokio::fs::File::options()
        .create(true)
        .write(true)
        .truncate(false)
        .open(world.join("session.lock"))
        .await?;
    file.sync_all().await?;
    let locked = file.try_lock_exclusive()?;
    Ok(locked.then_some(file))
}

pub async fn add_server_to_profile(
    profile_path: &Path,
    name: String,
    address: String,
    pack_status: ServerPackStatus,
) -> Result<usize> {
    let mut servers = servers_data::read(profile_path).await?;
    let insert_index = servers
        .iter()
        .position(|x| x.hidden)
        .unwrap_or(servers.len());
    servers.insert(
        insert_index,
        servers_data::ServerData {
            name,
            ip: address,
            accept_textures: pack_status.into(),
            hidden: false,
            icon: None,
        },
    );
    servers_data::write(profile_path, &servers).await?;
    Ok(insert_index)
}

pub async fn edit_server_in_profile(
    profile_path: &Path,
    index: usize,
    name: String,
    address: String,
    pack_status: ServerPackStatus,
) -> Result<()> {
    let mut servers = servers_data::read(profile_path).await?;
    let server =
        servers
            .get_mut(index)
            .filter(|x| !x.hidden)
            .ok_or_else(|| {
                ErrorKind::InputError(format!(
                    "No editable server at index {index}"
                ))
                .as_error()
            })?;
    server.name = name;
    server.ip = address;
    server.accept_textures = pack_status.into();
    servers_data::write(profile_path, &servers).await?;
    Ok(())
}

pub async fn remove_server_from_profile(
    profile_path: &Path,
    index: usize,
) -> Result<()> {
    let mut servers = servers_data::read(profile_path).await?;
    if servers.get(index).filter(|x| !x.hidden).is_none() {
        return Err(ErrorKind::InputError(format!(
            "No removable server at index {index}"
        ))
        .into());
    }
    servers.remove(index);
    servers_data::write(profile_path, &servers).await?;
    Ok(())
}

mod servers_data {
    use crate::Result;
    use crate::util::io;
    use serde::{Deserialize, Serialize};
    use std::path::Path;

    #[derive(Serialize, Deserialize, Debug, Clone)]
    #[serde(rename_all = "camelCase")]
    pub struct ServerData {
        #[serde(default)]
        pub hidden: bool,
        #[serde(skip_serializing_if = "Option::is_none")]
        pub icon: Option<String>,
        #[serde(default)]
        pub ip: String,
        #[serde(default)]
        pub name: String,
        #[serde(skip_serializing_if = "Option::is_none")]
        pub accept_textures: Option<bool>,
    }

    pub async fn read(instance_dir: &Path) -> Result<Vec<ServerData>> {
        #[derive(Deserialize, Debug)]
        struct ServersData {
            #[serde(default)]
            servers: Vec<ServerData>,
        }

        let servers_dat_path = instance_dir.join("servers.dat");
        if !servers_dat_path.exists() {
            return Ok(vec![]);
        }
        let servers_data = io::read(servers_dat_path).await?;
        let servers_data: ServersData = quartz_nbt::serde::deserialize(
            &servers_data,
            quartz_nbt::io::Flavor::Uncompressed,
        )?
        .0;
        Ok(servers_data.servers)
    }

    pub async fn write(
        instance_dir: &Path,
        servers: &[ServerData],
    ) -> Result<()> {
        #[derive(Serialize, Debug)]
        struct ServersData<'a> {
            servers: &'a [ServerData],
        }

        let servers_dat_path = instance_dir.join("servers.dat");
        let data = quartz_nbt::serde::serialize(
            &ServersData { servers },
            None,
            quartz_nbt::io::Flavor::Uncompressed,
        )?;
        io::write(servers_dat_path, data).await?;
        Ok(())
    }
}

pub async fn get_profile_protocol_version(
    profile: &str,
) -> Result<Option<ProtocolVersion>> {
    let mut profile = super::profile::get(profile).await?.ok_or_else(|| {
        ErrorKind::UnmanagedProfileError(format!(
            "Could not find profile {profile}"
        ))
    })?;
    if profile.install_stage != ProfileInstallStage::Installed {
        return Ok(None);
    }

    if let Some(protocol_version) = profile.protocol_version {
        return Ok(Some(ProtocolVersion::modern(protocol_version)));
    }
    if let Some(protocol_version) =
        OLD_PROTOCOL_VERSIONS.get(&profile.game_version)
    {
        return Ok(Some(*protocol_version));
    }

    let minecraft = crate::api::metadata::get_minecraft_versions().await?;
    let version_index = minecraft
        .versions
        .iter()
        .position(|it| it.id == profile.game_version)
        .ok_or(ErrorKind::LauncherError(format!(
            "Invalid game version: {}",
            profile.game_version
        )))?;
    let version = &minecraft.versions[version_index];

    let loader_version = get_loader_version_from_profile(
        &profile.game_version,
        profile.loader,
        profile.loader_version.as_deref(),
    )
    .await?;
    if profile.loader != ModLoader::Vanilla && loader_version.is_none() {
        return Ok(None);
    }

    let version_jar =
        loader_version.as_ref().map_or(version.id.clone(), |it| {
            format!("{}-{}", version.id.clone(), it.id.clone())
        });

    let state = State::get().await?;
    let client_path = state
        .directories
        .version_dir(&version_jar)
        .join(format!("{version_jar}.jar"));

    if !client_path.exists() {
        return Ok(None);
    }

    let version = launcher::read_protocol_version_from_jar(client_path).await?;
    if version.is_some() {
        profile.protocol_version = version;
        profile.upsert(&state.pool).await?;
    }
    Ok(version.map(ProtocolVersion::modern))
}

pub async fn get_server_status(
    address: &str,
    protocol_version: Option<ProtocolVersion>,
) -> Result<ServerStatus> {
    let (original_host, original_port) = parse_server_address(address)?;
    let (host, port) =
        resolve_server_address(original_host, original_port).await?;
    tracing::debug!(
        "Pinging {address} with protocol version {protocol_version:?}"
    );
    server_ping::get_server_status(
        &(&host as &str, port),
        (original_host, original_port),
        protocol_version,
    )
    .await
}

#[cfg(test)]
mod backup_tests {
    use super::{restore_backup_sync, validate_world_identifier};
    use std::io::{Read, Write};

    #[test]
    fn world_and_backup_identifiers_cannot_escape_the_profile() {
        assert!(validate_world_identifier("My World").is_ok());
        assert!(validate_world_identifier("backup.zip").is_ok());
        assert!(validate_world_identifier("../world").is_err());
        assert!(validate_world_identifier("nested/world").is_err());
        assert!(validate_world_identifier("C:\\world").is_err());
    }

    #[test]
    fn restore_rejects_zip_path_traversal_without_touching_the_world() {
        let temp = tempfile::tempdir().expect("temp dir");
        let saves = temp.path().join("saves");
        let world = saves.join("World");
        std::fs::create_dir_all(&world).expect("world dir");
        std::fs::write(world.join("level.dat"), b"current").expect("level.dat");

        let backup_path = temp.path().join("unsafe.zip");
        let file = std::fs::File::create(&backup_path).expect("backup file");
        let mut writer = zip::ZipWriter::new(file);
        writer
            .start_file(
                "World/../../escaped.txt",
                zip::write::SimpleFileOptions::default(),
            )
            .expect("zip entry");
        writer.write_all(b"unsafe").expect("zip content");
        writer.finish().expect("finish zip");

        assert!(restore_backup_sync(&backup_path, &saves, "World").is_err());
        assert!(!temp.path().join("escaped.txt").exists());
        let mut current = String::new();
        std::fs::File::open(world.join("level.dat"))
            .expect("current world remains")
            .read_to_string(&mut current)
            .expect("read current world");
        assert_eq!(current, "current");
    }

    #[test]
    fn restore_atomically_replaces_a_valid_world() {
        let temp = tempfile::tempdir().expect("temp dir");
        let saves = temp.path().join("saves");
        let world = saves.join("World");
        std::fs::create_dir_all(&world).expect("world dir");
        std::fs::write(world.join("level.dat"), b"current").expect("level.dat");

        let backup_path = temp.path().join("valid.zip");
        let file = std::fs::File::create(&backup_path).expect("backup file");
        let mut writer = zip::ZipWriter::new(file);
        writer
            .start_file(
                "World/level.dat",
                zip::write::SimpleFileOptions::default(),
            )
            .expect("level entry");
        writer.write_all(b"restored").expect("level content");
        writer
            .start_file(
                "World/region/r.0.0.mca",
                zip::write::SimpleFileOptions::default(),
            )
            .expect("region entry");
        writer.write_all(b"region").expect("region content");
        writer.finish().expect("finish zip");

        restore_backup_sync(&backup_path, &saves, "World")
            .expect("restore succeeds");
        assert_eq!(
            std::fs::read(world.join("level.dat")).expect("restored level"),
            b"restored"
        );
        assert_eq!(
            std::fs::read(world.join("region/r.0.0.mca"))
                .expect("restored region"),
            b"region"
        );
    }
}
