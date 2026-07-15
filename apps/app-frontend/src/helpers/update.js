import { invoke } from '@tauri-apps/api/core'
import { ref } from 'vue'

import { areUpdatesEnabled, enqueueUpdateForInstallation, restartApp } from '@/helpers/utils.js'

export const allowState = ref(false)
export const installState = ref(false)
export const updateState = ref(false)

let availableUpdate = null

export async function getRemote(isDownloadState) {
	if (!(await areUpdatesEnabled())) {
		updateState.value = false
		allowState.value = false
		installState.value = false
		return false
	}

	try {
		availableUpdate = await invoke('plugin:updater|check')
		const hasUpdate = Boolean(availableUpdate)
		updateState.value = hasUpdate
		allowState.value = hasUpdate

		const releaseTag = document.getElementById('releaseTag')
		const releaseTitle = document.getElementById('releaseTitle')
		if (releaseTag) releaseTag.textContent = availableUpdate?.version ?? 'Актуальная версия'
		if (releaseTitle) releaseTitle.textContent = availableUpdate?.body ?? 'Обновлений нет'

		if (isDownloadState && availableUpdate) {
			installState.value = true
			await enqueueUpdateForInstallation(availableUpdate.rid)
			await restartApp()
		}

		return hasUpdate
	} catch (error) {
		console.error('Не удалось проверить обновления BlockEra:', error)
		updateState.value = false
		allowState.value = false
		installState.value = false
		if (isDownloadState) throw error
		return false
	}
}
