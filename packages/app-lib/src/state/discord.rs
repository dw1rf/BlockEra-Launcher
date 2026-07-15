pub struct DiscordGuard;

pub(crate) const ACTIVE_STATE: [&str; 6] = [
    "Играет в",
    "Исследует",
    "Запускает",
    "Путешествует в",
    "Проверяет сборку",
    "Проводит время в",
];
impl DiscordGuard {
    /// Initialize discord IPC client, and attempt to connect to it
    /// If it fails, it will still return a DiscordGuard, but the client will be unconnected
    pub fn init() -> crate::Result<DiscordGuard> {
        Ok(DiscordGuard)
    }

    /// If the client failed connecting during init(), this will check for connection and attempt to reconnect
    /// This MUST be called first in any client method that requires a connection, because those can PANIC if the client is not connected
    /// (No connection is different than a failed connection, the latter will not panic and can be retried)
    pub async fn retry_if_not_ready(&self) -> bool {
        false
    }

    /// Set the activity to the given message
    /// First checks if discord is disabled, and if so, clear the activity instead
    pub async fn set_activity(
        &self,
        _msg: &str,
        reconnect_if_fail: bool,
    ) -> crate::Result<()> {
        self.clear_activity(reconnect_if_fail).await
    }

    /// Sets the activity to the given message, regardless of if discord is disabled or offline
    /// Should not be used except for in the above method, or if it is already known that discord is enabled (specifically for state initialization) and we are connected to the internet
    pub async fn force_set_activity(
        &self,
        _msg: &str,
        reconnect_if_fail: bool,
    ) -> crate::Result<()> {
        self.clear_activity(reconnect_if_fail).await
    }

    /// Clear the activity entirely ('disabling' the RPC until the next set_activity)
    pub async fn clear_activity(
        &self,
        _reconnect_if_fail: bool,
    ) -> crate::Result<()> {
        Ok(())
    }

    /// Clear the activity, but if there is a running profile, set the activity to that instead
    pub async fn clear_to_default(
        &self,
        reconnect_if_fail: bool,
    ) -> crate::Result<()> {
        self.clear_activity(reconnect_if_fail).await
    }
}
