use std::{
    sync::{
        Arc,
        atomic::{AtomicBool, Ordering},
    },
    time::{SystemTime, UNIX_EPOCH},
};

use discord_rich_presence::{
    DiscordIpc, DiscordIpcClient,
    activity::{Activity, Assets, Timestamps},
};
use rand::seq::SliceRandom;
use tokio::sync::RwLock;

use crate::{State, util::utils};

const DISCORD_APPLICATION_ID: &str = "1527438567263965434";
/// Upload the BlockEra logo in Discord Developer Portal → Rich Presence → Art Assets
/// with this key. Discord normalizes art asset keys to lowercase.
const DISCORD_LOGO_ASSET_KEY: &str = "blockera_logo";

pub struct DiscordGuard {
    client: Arc<RwLock<DiscordIpcClient>>,
    connected: Arc<AtomicBool>,
}

pub(crate) const ACTIVE_STATE: [&str; 6] = [
    "Играет в",
    "Исследует",
    "Запускает",
    "Путешествует в",
    "Проверяет сборку",
    "Проводит время в",
];

const INACTIVE_STATE: [&str; 3] = [
    "Готов к запуску Minecraft",
    "Настраивает сборки",
    "Выбирает, во что поиграть",
];

impl DiscordGuard {
    /// Initializes the Discord IPC client. The connection is established lazily so
    /// starting BlockEra does not require the Discord desktop app to be open.
    pub fn init() -> crate::Result<Self> {
        Ok(Self {
            client: Arc::new(RwLock::new(DiscordIpcClient::new(
                DISCORD_APPLICATION_ID,
            ))),
            connected: Arc::new(AtomicBool::new(false)),
        })
    }

    async fn retry_if_not_ready(&self) -> bool {
        if self.connected.load(Ordering::Relaxed) {
            return true;
        }

        let mut client = self.client.write().await;
        if client.connect().is_ok() {
            self.connected.store(true, Ordering::Relaxed);
            true
        } else {
            false
        }
    }

    /// Publishes an activity only when the user enabled Discord status in settings.
    pub async fn set_activity(
        &self,
        message: &str,
        reconnect_if_fail: bool,
    ) -> crate::Result<()> {
        let state = State::get().await?;
        let settings = crate::state::Settings::get(&state.pool).await?;

        if settings.discord_rpc {
            self.force_set_activity(message, reconnect_if_fail).await
        } else {
            self.clear_activity(reconnect_if_fail).await
        }
    }

    /// Publishes Rich Presence without checking the user setting.
    pub async fn force_set_activity(
        &self,
        message: &str,
        reconnect_if_fail: bool,
    ) -> crate::Result<()> {
        if !self.retry_if_not_ready().await {
            return Ok(());
        }

        let version = utils::read_package_json()
            .map(|launcher| launcher.version)
            .unwrap_or_else(|_| env!("CARGO_PKG_VERSION").to_owned());
        let started_at = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs() as i64;
        let activity = Activity::new()
            .details("BlockEra Launcher")
            .state(message)
            .assets(
                Assets::new()
                    .large_image(DISCORD_LOGO_ASSET_KEY)
                    .large_text(format!("BlockEra Launcher v{version}"))
                    .small_image(DISCORD_LOGO_ASSET_KEY)
                    .small_text("BlockEra Launcher"),
            )
            .timestamps(Timestamps::new().start(started_at));

        let mut client = self.client.write().await;
        if let Err(error) = client.set_activity(activity.clone()) {
            self.connected.store(false, Ordering::Relaxed);
            if !reconnect_if_fail {
                return Err(error.into());
            }

            client.reconnect()?;
            self.connected.store(true, Ordering::Relaxed);
            client.set_activity(activity)?;
        }

        Ok(())
    }

    pub async fn clear_activity(
        &self,
        reconnect_if_fail: bool,
    ) -> crate::Result<()> {
        if !self.retry_if_not_ready().await {
            return Ok(());
        }

        let mut client = self.client.write().await;
        if let Err(error) = client.clear_activity() {
            self.connected.store(false, Ordering::Relaxed);
            if !reconnect_if_fail {
                return Err(error.into());
            }

            client.reconnect()?;
            self.connected.store(true, Ordering::Relaxed);
            client.clear_activity()?;
        }

        Ok(())
    }

    /// Shows a useful launcher status when Minecraft is not running.
    pub async fn clear_to_default(
        &self,
        reconnect_if_fail: bool,
    ) -> crate::Result<()> {
        let message = INACTIVE_STATE
            .choose(&mut rand::thread_rng())
            .expect("inactive status list is non-empty");
        self.set_activity(message, reconnect_if_fail).await
    }
}
