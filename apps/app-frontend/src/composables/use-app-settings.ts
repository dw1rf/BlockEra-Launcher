import { computed, type Ref,ref } from 'vue'

import { type AppSettings,get, set } from '@/helpers/settings'

export type SettingsSaveState = 'idle' | 'pending' | 'success' | 'error'

const settings = ref<AppSettings | null>(null)
const saveState = ref<SettingsSaveState>('idle')
const saveError = ref('')
let loadPromise: Promise<AppSettings> | null = null
let writeQueue: Promise<void> = Promise.resolve()
let writeGeneration = 0
type PendingDebounce = {
	timer: ReturnType<typeof setTimeout>
	resolve: Array<() => void>
	reject: Array<(error: unknown) => void>
}
const debounceTimers = new Map<keyof AppSettings, PendingDebounce>()

async function loadSettings() {
	if (settings.value) return settings.value
	loadPromise ??= get().then((value) => {
		settings.value = value
		return value
	})
	return await loadPromise
}

function cloneSettings(value: AppSettings): AppSettings {
	return JSON.parse(JSON.stringify(value)) as AppSettings
}

function enqueueWrite() {
	if (!settings.value) return writeQueue

	const snapshot = cloneSettings(settings.value)
	const generation = ++writeGeneration
	saveState.value = 'pending'
	saveError.value = ''
	const operation = writeQueue
		.catch(() => undefined)
		.then(async () => {
			await set(snapshot)
		})

	writeQueue = operation.catch(() => undefined)

	return operation.then(
		() => {
			if (generation === writeGeneration) saveState.value = 'success'
		},
		(error: unknown) => {
			if (generation === writeGeneration) {
				saveState.value = 'error'
				saveError.value = error instanceof Error ? error.message : String(error)
			}
			throw error
		},
	)
}

function saveKey<K extends keyof AppSettings>(key: K, value: AppSettings[K], debounceMs = 0) {
	if (!settings.value) return Promise.reject(new Error('Settings are not loaded'))
	settings.value[key] = value

	if (debounceMs <= 0) return enqueueWrite()

	return new Promise<void>((resolve, reject) => {
		const previous = debounceTimers.get(key)
		if (previous) clearTimeout(previous.timer)
		saveState.value = 'pending'
		saveError.value = ''
		const resolves = [...(previous?.resolve ?? []), resolve]
		const rejects = [...(previous?.reject ?? []), reject]
		const timer = setTimeout(() => {
			debounceTimers.delete(key)
			enqueueWrite()
				.then(() => resolves.forEach((done) => done()))
				.catch((error) => rejects.forEach((fail) => fail(error)))
		}, debounceMs)
		debounceTimers.set(key, { timer, resolve: resolves, reject: rejects })
	})
}

export async function useAppSettings(): Promise<{
	settings: Ref<AppSettings>
	saveState: Ref<SettingsSaveState>
	saveError: Ref<string>
	saveLabel: Readonly<Ref<string>>
	saveKey: typeof saveKey
	retrySave: () => Promise<void>
}> {
	await loadSettings()
	return {
		settings: settings as Ref<AppSettings>,
		saveState,
		saveError,
		saveLabel: computed(() => {
			switch (saveState.value) {
				case 'pending':
					return 'Сохранение…'
				case 'success':
					return 'Сохранено'
				case 'error':
					return 'Ошибка сохранения'
				default:
					return ''
			}
		}),
		saveKey,
		retrySave: enqueueWrite,
	}
}
