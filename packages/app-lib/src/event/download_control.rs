//! Per-operation pause/resume/cancel. Dropping the operation closes its network streams.
use dashmap::DashMap;
use serde::{Deserialize, Serialize};
use std::{future::Future, sync::LazyLock};
use tokio::sync::watch;
use uuid::Uuid;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum DownloadState {
    Running,
    Paused,
    Cancelled,
}

tokio::task_local! {
    static ACTIVE: Vec<watch::Receiver<DownloadState>>;
}

pub(crate) fn active_controls() -> Vec<watch::Receiver<DownloadState>> {
    ACTIVE.try_with(Clone::clone).unwrap_or_default()
}

static CONTROLS: LazyLock<DashMap<Uuid, watch::Sender<DownloadState>>> =
    LazyLock::new(DashMap::new);

pub fn states() -> std::collections::HashMap<Uuid, DownloadState> {
    CONTROLS
        .iter()
        .map(|entry| (*entry.key(), *entry.value().borrow()))
        .collect()
}

pub fn set(id: Uuid, state: DownloadState) -> crate::Result<()> {
    let control = CONTROLS.get(&id).ok_or_else(|| {
        crate::ErrorKind::InputError(
            "Загрузка уже завершена или не поддерживает управление.".into(),
        )
    })?;
    control.send_if_modified(|current| {
        if *current == DownloadState::Cancelled {
            return false;
        }
        *current = state;
        true
    });
    Ok(())
}

struct Registration {
    id: Uuid,
    owner: bool,
}
impl Drop for Registration {
    fn drop(&mut self) {
        if self.owner {
            CONTROLS.remove(&self.id);
        }
    }
}

pub async fn run<T>(
    id: Uuid,
    operation: impl Future<Output = crate::Result<T>>,
) -> crate::Result<T> {
    let (mut receiver, owner) = match CONTROLS.entry(id) {
        dashmap::mapref::entry::Entry::Occupied(entry) => {
            (entry.get().subscribe(), false)
        }
        dashmap::mapref::entry::Entry::Vacant(entry) => {
            let (sender, receiver) = watch::channel(DownloadState::Running);
            entry.insert(sender);
            (receiver, true)
        }
    };
    let _registration = Registration { id, owner };
    let mut parents = active_controls();
    parents.push(receiver.clone());
    let operation = ACTIVE.scope(parents, operation);
    tokio::pin!(operation);
    loop {
        let state = *receiver.borrow_and_update();
        match state {
            DownloadState::Cancelled => return Err(crate::ErrorKind::InputError("Загрузка отменена. Уже установленные файлы сохранены; сборку можно восстановить.".into()).into()),
            DownloadState::Paused => { let _ = receiver.changed().await; }
            DownloadState::Running => tokio::select! {
                biased;
                _ = receiver.changed() => {},
                result = &mut operation => return result,
            },
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{
        Arc,
        atomic::{AtomicUsize, Ordering},
    };
    #[tokio::test]
    async fn pause_resume_cancel_and_independent_operations() {
        let id = Uuid::new_v4();
        let ticks = Arc::new(AtomicUsize::new(0));
        let counter = ticks.clone();
        let task = tokio::spawn(run(id, async move {
            loop {
                tokio::time::sleep(std::time::Duration::from_millis(5)).await;
                counter.fetch_add(1, Ordering::SeqCst);
            }
            #[allow(unreachable_code)]
            Ok(())
        }));
        while !states().contains_key(&id) {
            tokio::task::yield_now().await;
        }
        set(id, DownloadState::Paused).unwrap();
        tokio::time::sleep(std::time::Duration::from_millis(20)).await;
        let paused = ticks.load(Ordering::SeqCst);
        tokio::time::sleep(std::time::Duration::from_millis(20)).await;
        assert_eq!(ticks.load(Ordering::SeqCst), paused);
        assert_eq!(run(Uuid::new_v4(), async { Ok(42) }).await.unwrap(), 42);
        set(id, DownloadState::Running).unwrap();
        tokio::time::timeout(std::time::Duration::from_secs(2), async {
            while ticks.load(Ordering::SeqCst) == paused {
                tokio::task::yield_now().await;
            }
        })
        .await
        .unwrap();
        set(id, DownloadState::Paused).unwrap();
        set(id, DownloadState::Cancelled).unwrap();
        assert!(task.await.unwrap().is_err());
        assert!(!states().contains_key(&id));
    }
    #[tokio::test]
    async fn failed_and_completed_operations_release_controls() {
        let id = Uuid::new_v4();
        assert!(
            run::<()>(id, async {
                Err(crate::ErrorKind::InputError("test".into()).into())
            })
            .await
            .is_err()
        );
        assert!(!states().contains_key(&id));
        assert!(set(id, DownloadState::Running).is_err());
    }
}
