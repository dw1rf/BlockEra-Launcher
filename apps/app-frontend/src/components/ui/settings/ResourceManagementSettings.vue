<script setup>
import { BoxIcon, FolderSearchIcon, TrashIcon } from '@modrinth/assets'
import { Button, injectNotificationManager, Slider } from '@modrinth/ui'
import { open } from '@tauri-apps/plugin-dialog'
import { ref, watch } from 'vue'

import ConfirmModalWrapper from '@/components/ui/modal/ConfirmModalWrapper.vue'
import { purge_cache_types } from '@/helpers/cache.js'
import { get, set } from '@/helpers/settings.ts'

const { handleError } = injectNotificationManager()
const settings = ref(await get())

watch(
	settings,
	async () => {
		const setSettings = JSON.parse(JSON.stringify(settings.value))

		if (!setSettings.custom_dir) {
			setSettings.custom_dir = null
		}

		await set(setSettings)
	},
	{ deep: true },
)

async function purgeCache() {
	await purge_cache_types([
		'project',
		'version',
		'user',
		'team',
		'organization',
		'loader_manifest',
		'minecraft_manifest',
		'categories',
		'report_types',
		'loaders',
		'game_versions',
		'donation_platforms',
		'file_update',
		'search_results',
	]).catch(handleError)
}

async function findLauncherDir() {
	const newDir = await open({
		multiple: false,
		directory: true,
		title: 'Select a new app directory',
	})

	if (newDir) {
		settings.value.custom_dir = newDir
	}
}
</script>

<template>
	<div class="launcher-settings-page">
	<header class="settings-page-header"><p>ХРАНИЛИЩЕ</p><h2>Файлы и загрузки</h2><span>Папка данных, кэш и нагрузка на интернет и диск.</span></header>
	<section class="settings-section settings-fields">
		<div class="settings-section-heading"><div><h3>Папка BlockEra Launcher</h3><p>Здесь хранятся сборки, библиотеки и служебные файлы. Изменение применится после перезапуска.</p></div></div>
	<div>
		<div class="iconified-input w-full">
			<BoxIcon />
			<input id="appDir" v-model="settings.custom_dir" type="text" class="input" />
			<Button class="r-btn" @click="findLauncherDir">
				<FolderSearchIcon />
			</Button>
		</div>
	</div>
	</section>

	<section class="settings-section settings-list">
		<ConfirmModalWrapper
			ref="purgeCacheConfirmModal"
			title="Are you sure you want to purge the cache?"
			description="If you proceed, your entire cache will be purged. This may slow down the app temporarily."
			:has-to-type="false"
			proceed-label="Purge cache"
			:show-ad-on-close="false"
			@proceed="purgeCache"
		/>

		<div class="settings-row"><div><h3>Кэш каталога</h3><p>Удаляет временные данные проектов и заставляет лаунчер получить их заново.</p></div><button id="purge-cache" class="btn min-w-max" @click="$refs.purgeCacheConfirmModal.show()"><TrashIcon /> Очистить</button></div>
	</section>

	<section class="settings-section">
	<div class="settings-section-heading"><div><h3>Параллельные загрузки</h3><p>Уменьшите значение при нестабильном интернете. Применится после перезапуска.</p></div><strong>{{ settings.max_concurrent_downloads }}</strong></div>
	<Slider
		id="max-downloads"
		v-model="settings.max_concurrent_downloads"
		:min="1"
		:max="10"
		:step="1"
	/>
	</section>
	<section class="settings-section">
	<div class="settings-section-heading"><div><h3>Параллельная запись на диск</h3><p>Снизьте значение при ошибках ввода-вывода или на медленном HDD.</p></div><strong>{{ settings.max_concurrent_writes }}</strong></div>
	<Slider id="max-writes" v-model="settings.max_concurrent_writes" :min="1" :max="50" :step="1" />
	</section>
	</div>
</template>
