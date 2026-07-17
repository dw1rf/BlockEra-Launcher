import { check } from '@tauri-apps/plugin-updater'
import { ref, shallowRef } from 'vue'

import { areUpdatesEnabled, restartApp } from '@/helpers/utils.js'

export const allowState = ref(false)
export const installState = ref(false)
export const updateState = ref(false)
export const checkingState = ref(false)
export const updateError = ref('')
/** @type {import('vue').ShallowRef<import('@tauri-apps/plugin-updater').Update | null>} */
export const availableUpdate = shallowRef(null)

let activeCheck = null

export function formatUpdaterError(error) {
	if (error instanceof Error) return error.message
	if (typeof error === 'string') return error
	if (error && typeof error === 'object') {
		for (const key of ['message', 'error', 'reason']) {
			if (typeof error[key] === 'string') return error[key]
		}
		try {
			return JSON.stringify(error)
		} catch {
			return String(error)
		}
	}
	return String(error ?? 'Неизвестная ошибка')
}

export async function checkLauncherUpdate() {
	if (activeCheck) return await activeCheck

	activeCheck = (async () => {
		checkingState.value = true
		updateError.value = ''
		try {
			if (!(await areUpdatesEnabled())) {
				availableUpdate.value = null
				updateState.value = false
				allowState.value = false
				return null
			}

			const update = await check()
			availableUpdate.value = update
			updateState.value = Boolean(update)
			allowState.value = Boolean(update)
			return update
		} catch (error) {
			availableUpdate.value = null
			updateState.value = false
			allowState.value = false
			updateError.value = formatUpdaterError(error)
			console.error('Не удалось проверить обновления BlockEra:', error)
			return null
		} finally {
			checkingState.value = false
			activeCheck = null
		}
	})()

	return await activeCheck
}

export async function installLauncherUpdate() {
	const update = availableUpdate.value ?? (await checkLauncherUpdate())
	if (!update) {
		throw new Error(updateError.value || 'Новая версия BlockEra Launcher не найдена')
	}

	installState.value = true
	updateError.value = ''
	try {
		// Install while the updater resource is still alive. Deferring installation until the
		// app's Exit event races Tauri's restart path on Windows and can reopen the old binary.
		await update.downloadAndInstall()
		availableUpdate.value = null
		updateState.value = false
		allowState.value = false

		// The Windows updater exits the process while installing. Platforms where installation
		// returns still need an explicit restart to load the new binary.
		await restartApp()
	} catch (error) {
		updateError.value = formatUpdaterError(error)
		throw error
	} finally {
		installState.value = false
	}
}

export async function getRemote(isDownloadState) {
	const update = await checkLauncherUpdate()
	if (isDownloadState && update) await installLauncherUpdate()
	return Boolean(update)
}
