<script setup>
import { BoxIcon, FolderSearchIcon, TrashIcon } from '@modrinth/assets'
import { Button, injectNotificationManager, Slider } from '@modrinth/ui'
import { open } from '@tauri-apps/plugin-dialog'
import { ref, watch } from 'vue'

import ConfirmModalWrapper from '@/components/ui/modal/ConfirmModalWrapper.vue'
import { useAppSettings } from '@/composables/use-app-settings'
import { purge_cache_types } from '@/helpers/cache.js'

const { handleError } = injectNotificationManager()
const { settings, saveKey } = await useAppSettings()
const pendingDirectory = ref(settings.value.custom_dir ?? '')
const directoryError = ref('')

watch(
	() => settings.value.max_concurrent_downloads,
	(value) => void saveKey('max_concurrent_downloads', value, 250),
)
watch(
	() => settings.value.max_concurrent_writes,
	(value) => void saveKey('max_concurrent_writes', value, 250),
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
		title: 'Выберите новую папку BlockEra Launcher',
	})

	if (newDir) {
		pendingDirectory.value = newDir.path ?? newDir
		directoryError.value = ''
	}
}

async function applyLauncherDir() {
	const directory = pendingDirectory.value.trim()
	if (!directory) {
		directoryError.value = 'Выберите существующую папку через системный диалог.'
		return
	}
	directoryError.value = ''
	try {
		await saveKey('custom_dir', directory)
	} catch (error) {
		directoryError.value = error instanceof Error ? error.message : String(error)
	}
}
</script>

<template>
	<div class="launcher-settings-page">
		<header class="settings-page-header">
			<p>ХРАНИЛИЩЕ</p>
			<h2>Файлы и загрузки</h2>
			<span>Папка данных, кэш и нагрузка на интернет и диск.</span>
		</header>
		<section class="settings-section settings-fields">
			<div class="settings-section-heading">
				<div>
					<h3>Папка BlockEra Launcher</h3>
					<p>
						Здесь хранятся сборки, библиотеки и служебные файлы. Изменение применится после
						перезапуска.
					</p>
				</div>
			</div>
			<div>
				<div class="iconified-input w-full">
					<BoxIcon />
					<input id="appDir" v-model="pendingDirectory" type="text" class="input" readonly />
					<Button
						v-tooltip="'Выбрать папку'"
						class="r-btn"
						aria-label="Выбрать папку"
						@click="findLauncherDir"
					>
						<FolderSearchIcon />
					</Button>
				</div>
				<p v-if="directoryError" class="text-red" role="alert">{{ directoryError }}</p>
				<Button
					:disabled="pendingDirectory === (settings.custom_dir ?? '')"
					@click="applyLauncherDir"
					>Применить папку</Button
				>
			</div>
		</section>

		<section class="settings-section settings-list">
			<ConfirmModalWrapper
				ref="purgeCacheConfirmModal"
				title="Очистить кэш?"
				description="Временные данные проектов будут удалены. Следующая загрузка каталога может занять больше времени."
				:has-to-type="false"
				proceed-label="Очистить кэш"
				:show-ad-on-close="false"
				@proceed="purgeCache"
			/>

			<div class="settings-row">
				<div>
					<h3>Кэш каталога</h3>
					<p>Удаляет временные данные проектов и заставляет лаунчер получить их заново.</p>
				</div>
				<button id="purge-cache" class="btn min-w-max" @click="$refs.purgeCacheConfirmModal.show()">
					<TrashIcon /> Очистить
				</button>
			</div>
		</section>

		<section class="settings-section">
			<div class="settings-section-heading">
				<div>
					<h3>Параллельные загрузки</h3>
					<p>Уменьшите значение при нестабильном интернете. Применится после перезапуска.</p>
				</div>
				<strong>{{ settings.max_concurrent_downloads }}</strong>
			</div>
			<Slider
				id="max-downloads"
				v-model="settings.max_concurrent_downloads"
				:min="1"
				:max="10"
				:step="1"
			/>
		</section>
		<section class="settings-section">
			<div class="settings-section-heading">
				<div>
					<h3>Параллельная запись на диск</h3>
					<p>Снизьте значение при ошибках ввода-вывода или на медленном HDD.</p>
				</div>
				<strong>{{ settings.max_concurrent_writes }}</strong>
			</div>
			<Slider
				id="max-writes"
				v-model="settings.max_concurrent_writes"
				:min="1"
				:max="50"
				:step="1"
			/>
		</section>
	</div>
</template>
