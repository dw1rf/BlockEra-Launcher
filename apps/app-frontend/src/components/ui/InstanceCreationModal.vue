<template>
	<ModalWrapper
		ref="modal"
		class="blockera-creation-modal"
		header="Новая сборка BlockEra"
		width="min(920px, calc(100vw - 40px))"
		max-width="920px"
	>
		<div class="creation-shell">
			<section class="creation-intro">
				<div class="intro-copy">
					<span>МАСТЕР СБОРКИ</span>
					<h2>Соберите свой мир</h2>
					<p>Выберите удобный способ — BlockEra подготовит файлы и покажет прогресс установки.</p>
				</div>
				<div class="cover-preview" :style="{ backgroundImage: `url(${creationBackdrop})` }">
					<span>Обложка подберётся автоматически</span>
				</div>
			</section>

			<nav class="creation-tabs" aria-label="Способ создания сборки">
				<button :class="{ active: creationType === 'custom' }" @click="creationType = 'custom'">
					<PlusIcon />
					<span><strong>Новая</strong><small>Настроить вручную</small></span>
				</button>
				<button
					:class="{ active: creationType === 'from file' }"
					@click="creationType = 'from file'"
				>
					<FolderOpenIcon />
					<span><strong>Из файла</strong><small>Открыть .mrpack</small></span>
				</button>
				<button
					:class="{ active: creationType === 'import from launcher' }"
					@click="creationType = 'import from launcher'"
				>
					<FolderSearchIcon />
					<span><strong>Перенос</strong><small>Из другого лаунчера</small></span>
				</button>
			</nav>

			<Transition name="creation-view" mode="out-in">
				<div v-if="creationType === 'custom'" key="custom" class="creation-view custom-view">
					<section class="creation-panel identity-panel">
						<div class="panel-heading">
							<span>01</span>
							<div>
								<h3>Название и значок</h3>
								<p>Так сборка будет выглядеть в вашей библиотеке.</p>
							</div>
						</div>
						<div class="identity-fields">
							<div class="image-upload">
								<Avatar :src="display_icon" size="72px" :rounded="true" />
								<button class="icon-upload" @click="upload_icon()">
									<UploadIcon /> Выбрать значок
								</button>
								<button
									v-if="display_icon"
									class="icon-reset"
									aria-label="Удалить значок"
									@click="reset_icon"
								>
									<XIcon />
								</button>
							</div>
							<label class="field-control">
								<span>Название сборки</span>
								<input
									v-model="profile_name"
									autocomplete="off"
									type="text"
									maxlength="100"
									placeholder="Например, Survival+"
								/>
							</label>
						</div>
					</section>

					<section class="creation-panel runtime-panel">
						<div class="panel-heading">
							<span>02</span>
							<div>
								<h3>Версия и загрузчик</h3>
								<p>Можно изменить позже в настройках сборки.</p>
							</div>
						</div>
						<div class="loader-grid">
							<button
								v-for="loaderName in loaders"
								:key="loaderName"
								:class="{ active: loader === loaderName }"
								@click="loader = loaderName"
							>
								<strong>{{ formatLoader(loaderName) }}</strong>
								<small>{{ loaderDescription(loaderName) }}</small>
							</button>
						</div>
						<div class="version-row">
							<label class="field-control version-select">
								<span>Версия Minecraft</span>
								<multiselect
									v-model="game_version"
									:options="game_versions"
									:multiple="false"
									:searchable="true"
									placeholder="Выберите версию"
									:show-labels="false"
								/>
							</label>
							<Checkbox
								v-model="showSnapshots"
								class="snapshot-toggle"
								label="Показывать снапшоты"
							/>
						</div>
						<div v-if="loader !== 'vanilla'" class="loader-version-row">
							<span>Версия загрузчика</span>
							<div class="mini-tabs">
								<button
									:class="{ active: loader_version === 'stable' }"
									@click="loader_version = 'stable'"
								>
									Стабильная
								</button>
								<button
									:class="{ active: loader_version === 'latest' }"
									@click="loader_version = 'latest'"
								>
									Последняя
								</button>
								<button
									:class="{ active: loader_version === 'other' }"
									@click="loader_version = 'other'"
								>
									Выбрать
								</button>
							</div>
							<multiselect
								v-if="loader_version === 'other' && game_version"
								v-model="specified_loader_version"
								:options="selectable_versions"
								:searchable="true"
								placeholder="Точная версия загрузчика"
								:show-labels="false"
							/>
							<p v-else-if="loader_version === 'other'" class="warning">
								Сначала выберите версию Minecraft.
							</p>
						</div>
					</section>

					<footer class="creation-actions">
						<div>
							<strong>{{ profile_name.trim() || 'Новая сборка' }}</strong
							><span>{{ game_version || 'Версия не выбрана' }} · {{ formatLoader(loader) }}</span>
						</div>
						<Button @click="hide()"><XIcon /> Отмена</Button>
						<Button color="primary" :disabled="!check_valid || creating" @click="create_instance()"
							><PlusIcon v-if="!creating" /> {{ creating ? 'Создаём…' : 'Создать сборку' }}</Button
						>
					</footer>
				</div>

				<div v-else-if="creationType === 'from file'" key="file" class="creation-view file-view">
					<button class="file-dropzone" :disabled="fileImporting" @click="openFile">
						<span class="drop-icon"><UploadIcon /></span>
						<strong>{{
							fileImporting ? 'Проверяем и устанавливаем…' : 'Перетащите сюда файл сборки'
						}}</strong>
						<p>Поддерживается формат Modrinth <code>.mrpack</code></p>
						<span class="browse-file"><FolderOpenIcon /> Выбрать файл</span>
					</button>
					<div class="import-note">
						<InfoIcon /><span
							>BlockEra проверит файл и покажет все этапы установки в центре загрузок.</span
						>
					</div>
				</div>

				<div v-else key="launcher" class="creation-view launcher-view">
					<div v-if="showImportSummary" class="import-summary" aria-live="polite">
						<div class="panel-heading">
							<span>✓</span>
							<div>
								<h3>Итоги переноса</h3>
								<p>
									Успешно: {{ importCounts.success }}, ошибок: {{ importCounts.error }}, пропущено:
									{{ importCounts.skipped }}
								</p>
							</div>
						</div>
						<ul class="import-results">
							<li
								v-for="result in importResults"
								:key="`${result.launcher}:${result.name}`"
								:class="`is-${result.status}`"
							>
								<strong>{{ result.name }}</strong>
								<span>{{ result.launcher }} · {{ importStatusLabel(result.status) }}</span>
							</li>
						</ul>
						<div class="button-row">
							<Button
								v-if="importCounts.error > 0 || importCounts.skipped > 0"
								:disabled="loading"
								@click="retryFailedImports"
								>Повторить неудачные</Button
							>
							<Button color="primary" @click="hide">Готово</Button>
						</div>
					</div>
					<template v-else>
						<Chips
							v-model="selectedProfileType"
							:items="profileOptions"
							:format-label="(profile) => profile?.name"
						/>
						<div class="path-selection">
							<h3>Папка {{ selectedProfileType.name }}</h3>
							<div class="path-input">
								<div class="iconified-input">
									<FolderOpenIcon />
									<input
										v-model="selectedProfileType.path"
										type="text"
										placeholder="Путь к лаунчеру"
										@change="setPath"
									/>
									<Button class="r-btn" @click="() => (selectedProfileType.path = '')">
										<XIcon />
									</Button>
								</div>
								<Button
									v-tooltip="'Выбрать папку'"
									icon-only
									aria-label="Выбрать папку лаунчера"
									@click="selectLauncherPath"
								>
									<FolderSearchIcon />
								</Button>
								<Button
									v-tooltip="'Обновить список'"
									icon-only
									aria-label="Обновить список сборок"
									@click="reload"
								>
									<UpdatedIcon />
								</Button>
							</div>
						</div>
						<div class="table">
							<div class="table-head table-row">
								<div class="toggle-all table-cell">
									<Checkbox
										class="select-checkbox"
										:model-value="
											profiles.get(selectedProfileType.name)?.every((child) => child.selected)
										"
										@update:model-value="
											(newValue) =>
												profiles
													.get(selectedProfileType.name)
													?.forEach((child) => (child.selected = newValue))
										"
									/>
								</div>
								<div class="name-cell table-cell">Название сборки</div>
							</div>
							<div
								v-if="
									profiles.get(selectedProfileType.name) &&
									profiles.get(selectedProfileType.name).length > 0
								"
								class="table-content"
							>
								<div
									v-for="(profile, index) in profiles.get(selectedProfileType.name)"
									:key="index"
									class="table-row"
								>
									<div class="checkbox-cell table-cell">
										<Checkbox v-model="profile.selected" class="select-checkbox" />
									</div>
									<div class="name-cell table-cell">
										{{ profile.name }}
									</div>
								</div>
							</div>
							<div v-else class="table-content empty">Сборки не найдены</div>
						</div>
						<div class="button-row">
							<Button
								v-if="selectedProfileType.name === 'Curseforge'"
								:disabled="loading"
								@click="showCurseForgeProfileModal"
							>
								<CodeIcon />
								Импорт по коду профиля
							</Button>
							<Button
								:disabled="
									loading ||
									!Array.from(profiles.values())
										.flatMap((e) => e)
										.some((e) => e.selected)
								"
								color="primary"
								@click="next"
							>
								{{
									loading
										? 'Импортируем…'
										: Array.from(profiles.values())
													.flatMap((e) => e)
													.some((e) => e.selected)
											? `Импортировать: ${
													Array.from(profiles.values())
														.flatMap((e) => e)
														.filter((e) => e.selected).length
												} profiles`
											: 'Выберите сборки для импорта'
								}}
							</Button>
							<ProgressBar
								v-if="loading"
								:progress="(importedProfiles / (totalProfiles + 0.0001)) * 100"
							/>
						</div>
					</template>
				</div>
			</Transition>
		</div>
	</ModalWrapper>
	<CurseForgeProfileImportModal ref="curseforgeProfileModal" :close-parent="hide" />
</template>

<script setup>
import {
	CodeIcon,
	FolderOpenIcon,
	FolderSearchIcon,
	InfoIcon,
	PlusIcon,
	UpdatedIcon,
	UploadIcon,
	XIcon,
} from '@modrinth/assets'
import { Avatar, Button, Checkbox, Chips, injectNotificationManager } from '@modrinth/ui'
import { convertFileSrc } from '@tauri-apps/api/core'
import { getCurrentWebview } from '@tauri-apps/api/webview'
import { open } from '@tauri-apps/plugin-dialog'
import { computed, onUnmounted, ref, shallowRef } from 'vue'
import Multiselect from 'vue-multiselect'

import CurseForgeProfileImportModal from '@/components/ui/CurseForgeProfileImportModal.vue'
import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import ProgressBar from '@/components/ui/ProgressBar.vue'
import { trackEvent } from '@/helpers/analytics'
import {
	get_default_launcher_path,
	get_importable_instances,
	import_instance,
} from '@/helpers/import.js'
import { instanceBackgroundFor } from '@/helpers/instance-backgrounds'
import { get_game_versions, get_loader_versions } from '@/helpers/metadata'
import { create_profile_and_install_from_file } from '@/helpers/pack.js'
import { create } from '@/helpers/profile'
import { get_loaders } from '@/helpers/tags'

const { handleError } = injectNotificationManager()

const profile_name = ref('')
const game_version = ref('')
const loader = ref('vanilla')
const loader_version = ref('stable')
const specified_loader_version = ref('')
const icon = ref(null)
const display_icon = ref(null)
const creating = ref(false)
const fileImporting = ref(false)
const showSnapshots = ref(false)
const creationType = ref('custom')
const isShowing = ref(false)
const creationBackdrop = computed(() =>
	instanceBackgroundFor(
		profile_name.value.trim() || `${loader.value}-${game_version.value || 'latest'}`,
	),
)

function formatLoader(value) {
	const labels = {
		vanilla: 'Vanilla',
		fabric: 'Fabric',
		forge: 'Forge',
		neoforge: 'NeoForge',
		quilt: 'Quilt',
	}
	return labels[value] ?? value
}

function loaderDescription(value) {
	const descriptions = {
		vanilla: 'Чистая игра',
		fabric: 'Лёгкий и быстрый',
		forge: 'Большие модпаки',
		neoforge: 'Современный Forge',
		quilt: 'Гибкая экосистема',
	}
	return descriptions[value] ?? 'Загрузчик модов'
}

defineExpose({
	show: async () => {
		game_version.value = ''
		specified_loader_version.value = ''
		profile_name.value = ''
		creating.value = false
		fileImporting.value = false
		showImportSummary.value = false
		importResults.value = []
		showSnapshots.value = false
		loader.value = 'vanilla'
		loader_version.value = 'stable'
		icon.value = null
		display_icon.value = null
		isShowing.value = true
		modal.value.show()

		unlistener.value = await getCurrentWebview().onDragDropEvent(async (event) => {
			// Only if modal is showing
			if (!isShowing.value) return
			if (event.payload.type !== 'drop') return
			if (creationType.value !== 'from file') return
			hide()
			const { paths } = event.payload
			if (!paths || paths.length !== 1 || !isSupportedPackFile(paths[0])) {
				handleError(new Error('Выберите один файл сборки в формате .mrpack.'))
				return
			}
			await importPackFile(paths[0], 'CreationModalFileDrop')
		})

		trackEvent('InstanceCreateStart', { source: 'CreationModal' })
	},
})

const unlistener = ref(null)
const hide = () => {
	isShowing.value = false
	modal.value.hide()
	if (unlistener.value) {
		unlistener.value()
		unlistener.value = null
	}
}

const showCurseForgeProfileModal = () => {
	curseforgeProfileModal.value?.show()
}

onUnmounted(() => {
	if (unlistener.value) {
		unlistener.value()
		unlistener.value = null
	}
})

const [
	fabric_versions,
	forge_versions,
	quilt_versions,
	neoforge_versions,
	all_game_versions,
	loaders,
] = await Promise.all([
	get_loader_versions('fabric').then(shallowRef).catch(handleError),
	get_loader_versions('forge').then(shallowRef).catch(handleError),
	get_loader_versions('quilt').then(shallowRef).catch(handleError),
	get_loader_versions('neo').then(shallowRef).catch(handleError),
	get_game_versions().then(shallowRef).catch(handleError),
	get_loaders()
		.then((value) =>
			ref(
				value
					.filter((item) => item.supported_project_types.includes('modpack'))
					.map((item) => item.name.toLowerCase()),
			),
		)
		.catch((err) => {
			handleError(err)
			return ref([])
		}),
])
loaders.value.unshift('vanilla')

const game_versions = computed(() => {
	return all_game_versions.value.versions
		.filter((item) => {
			let defaultVal = item.type === 'release' || showSnapshots.value
			if (loader.value === 'fabric') {
				defaultVal &= fabric_versions.value.gameVersions.some((x) => item.id === x.id)
			} else if (loader.value === 'forge') {
				defaultVal &= forge_versions.value.gameVersions.some((x) => item.id === x.id)
			} else if (loader.value === 'quilt') {
				defaultVal &= quilt_versions.value.gameVersions.some((x) => item.id === x.id)
			} else if (loader.value === 'neoforge') {
				defaultVal &= neoforge_versions.value.gameVersions.some((x) => item.id === x.id)
			}

			return defaultVal
		})
		.map((item) => item.id)
})

const modal = ref(null)
const curseforgeProfileModal = ref(null)

const check_valid = computed(() => {
	return (
		profile_name.value.trim() &&
		game_version.value &&
		game_versions.value.includes(game_version.value)
	)
})

const create_instance = async () => {
	if (creating.value || !check_valid.value) return
	creating.value = true
	const loader_version_value =
		loader_version.value === 'other' ? specified_loader_version.value : loader_version.value
	const loaderVersion = loader.value === 'vanilla' ? null : (loader_version_value ?? 'stable')

	try {
		await create(
			profile_name.value,
			game_version.value,
			loader.value,
			loader.value === 'vanilla' ? null : (loader_version_value ?? 'stable'),
			icon.value,
		)

		trackEvent('InstanceCreate', {
			profile_name: profile_name.value,
			game_version: game_version.value,
			loader: loader.value,
			loader_version: loaderVersion,
			has_icon: !!icon.value,
			source: 'CreationModal',
		})
		hide()
	} catch (error) {
		handleError(error)
	} finally {
		creating.value = false
	}
}

const upload_icon = async () => {
	const res = await open({
		multiple: false,
		filters: [
			{
				name: 'Image',
				extensions: ['png', 'jpeg', 'svg', 'webp', 'gif', 'jpg'],
			},
		],
	})

	icon.value = res.path ?? res

	if (!icon.value) return
	display_icon.value = convertFileSrc(icon.value)
}

const reset_icon = () => {
	icon.value = null
	display_icon.value = null
}

const selectable_versions = computed(() => {
	if (game_version.value) {
		if (loader.value === 'fabric') {
			return fabric_versions.value.gameVersions[0].loaders.map((item) => item.id)
		} else if (loader.value === 'forge') {
			return forge_versions.value.gameVersions
				.find((item) => item.id === game_version.value)
				.loaders.map((item) => item.id)
		} else if (loader.value === 'quilt') {
			return quilt_versions.value.gameVersions[0].loaders.map((item) => item.id)
		} else if (loader.value === 'neoforge') {
			return neoforge_versions.value.gameVersions
				.find((item) => item.id === game_version.value)
				.loaders.map((item) => item.id)
		}
	}
	return []
})

const openFile = async () => {
	const newProject = await open({
		multiple: false,
		filters: [{ name: 'Сборка Modrinth', extensions: ['mrpack'] }],
	})
	if (!newProject) return
	const path = newProject.path ?? newProject
	if (typeof path !== 'string' || !isSupportedPackFile(path)) {
		handleError(new Error('Выбранный файл не поддерживается. Используйте файл .mrpack.'))
		return
	}
	await importPackFile(path, 'CreationModalFileOpen')
}

function isSupportedPackFile(path) {
	return typeof path === 'string' && path.toLowerCase().endsWith('.mrpack')
}

async function importPackFile(path, source) {
	if (fileImporting.value) return
	fileImporting.value = true
	try {
		await create_profile_and_install_from_file(path)
		trackEvent('InstanceCreate', { source })
		hide()
	} catch (error) {
		handleError(error)
	} finally {
		fileImporting.value = false
	}
}

const profiles = ref(
	new Map([
		['MultiMC', []],
		['GDLauncher', []],
		['ATLauncher', []],
		['Curseforge', []],
		['PrismLauncher', []],
	]),
)

const loading = ref(false)
const importedProfiles = ref(0)
const totalProfiles = ref(0)

const profileOptions = ref([
	{ name: 'MultiMC', path: '' },
	{ name: 'GDLauncher', path: '' },
	{ name: 'ATLauncher', path: '' },
	{ name: 'Curseforge', path: '' },
	{ name: 'PrismLauncher', path: '' },
])
const selectedProfileType = ref(profileOptions.value[0])

const importResults = ref([])
const showImportSummary = ref(false)
const importCounts = computed(() => ({
	success: importResults.value.filter((result) => result.status === 'success').length,
	error: importResults.value.filter((result) => result.status === 'error').length,
	skipped: importResults.value.filter((result) => result.status === 'skipped').length,
}))

function importStatusLabel(status) {
	return status === 'success' ? 'Успешно' : status === 'error' ? 'Ошибка' : 'Пропущено'
}

// Attempt to get import profiles on default paths
const promises = profileOptions.value.map(async (option) => {
	const path = await get_default_launcher_path(option.name).catch(handleError)
	if (!path || path === '') return

	// Try catch to allow failure and simply ignore default path attempt
	try {
		const instances = await get_importable_instances(option.name, path)

		if (!instances) return
		profileOptions.value.find((profile) => profile.name === option.name).path = path
		profiles.value.set(
			option.name,
			instances.map((name) => ({ name, selected: false })),
		)
	} catch {
		// Allow failure silently
	}
})
await Promise.all(promises)

const selectLauncherPath = async () => {
	selectedProfileType.value.path = await open({ multiple: false, directory: true })

	if (selectedProfileType.value.path) {
		await reload()
	}
}

const reload = async () => {
	const instances = await get_importable_instances(
		selectedProfileType.value.name,
		selectedProfileType.value.path,
	).catch(handleError)
	if (instances) {
		profiles.value.set(
			selectedProfileType.value.name,
			instances.map((name) => ({ name, selected: false })),
		)
	} else {
		profiles.value.set(selectedProfileType.value.name, [])
	}
}

const setPath = () => {
	profileOptions.value.find((profile) => profile.name === selectedProfileType.value.name).path =
		selectedProfileType.value.path
}

const next = async () => {
	if (loading.value) return
	showImportSummary.value = false
	importResults.value = []
	importedProfiles.value = 0
	totalProfiles.value = Array.from(profiles.value.values())
		.map((profiles) => profiles.filter((profile) => profile.selected).length)
		.reduce((a, b) => a + b, 0)
	loading.value = true
	try {
		for (const launcher of Array.from(profiles.value.entries()).map(
			([launcher, launcherProfiles]) => ({
				launcher,
				path: profileOptions.value.find((option) => option.name === launcher)?.path,
				profiles: launcherProfiles,
			}),
		)) {
			for (const profile of launcher.profiles.filter((profile) => profile.selected)) {
				if (!launcher.path) {
					importResults.value.push({
						launcher: launcher.launcher,
						name: profile.name,
						status: 'skipped',
					})
					importedProfiles.value++
					continue
				}

				try {
					await import_instance(launcher.launcher, launcher.path, profile.name)
					profile.selected = false
					importResults.value.push({
						launcher: launcher.launcher,
						name: profile.name,
						status: 'success',
					})
				} catch (error) {
					handleError(error)
					importResults.value.push({
						launcher: launcher.launcher,
						name: profile.name,
						status: 'error',
					})
				} finally {
					importedProfiles.value++
				}
			}
		}
	} finally {
		loading.value = false
		showImportSummary.value = true
	}
}

async function retryFailedImports() {
	for (const result of importResults.value.filter((item) => item.status !== 'success')) {
		const profile = profiles.value.get(result.launcher)?.find((item) => item.name === result.name)
		if (profile) profile.selected = true
	}
	await next()
}
</script>

<style lang="scss" scoped>
.import-summary {
	display: grid;
	gap: 1rem;
	padding: 1rem;
	border: 1px solid var(--blockera-border);
	border-radius: var(--blockera-radius-lg);
	background: var(--blockera-surface);
}
.import-results {
	max-height: 18rem;
	display: grid;
	gap: 0.5rem;
	margin: 0;
	padding: 0;
	overflow: auto;
	list-style: none;
}
.import-results li {
	display: flex;
	justify-content: space-between;
	gap: 1rem;
	padding: 0.65rem;
	border-left: 0.2rem solid var(--blockera-warning);
	background: rgba(255, 255, 255, 0.025);
}
.import-results li.is-success {
	border-left-color: var(--blockera-success);
}
.import-results li.is-error {
	border-left-color: var(--blockera-danger);
}
.import-results span {
	color: var(--color-secondary);
}
.modal-body {
	display: flex;
	flex-direction: column;
	gap: var(--gap-md);
	margin-top: var(--gap-lg);
}

.input-label {
	font-size: 1rem;
	font-weight: bolder;
	color: var(--color-contrast);
	margin-bottom: 0.5rem;
}

.text-input {
	width: 20rem;
}

.image-upload {
	display: flex;
	gap: 1rem;
}

.image-input {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	justify-content: center;
}

.warning {
	font-style: italic;
}

:deep(button.checkbox) {
	border: none;
}

.selector {
	max-width: 20rem;
}

.labeled-divider {
	text-align: center;
}

.labeled-divider:after {
	background-color: var(--color-raised-bg);
	content: 'Or';
	color: var(--color-base);
	padding: var(--gap-sm);
	position: relative;
	top: -0.5rem;
}

.info {
	display: flex;
	flex-direction: row;
	gap: 0.5rem;
	align-items: center;
}

.modal-header {
	display: flex;
	flex-direction: row;
	justify-content: space-between;
	align-items: center;
	padding-bottom: 0;
}

.path-selection {
	padding: var(--gap-xl);
	background-color: var(--color-bg);
	border-radius: var(--radius-lg);
	display: flex;
	flex-direction: column;
	gap: var(--gap-md);

	h3 {
		margin: 0;
	}

	.path-input {
		display: flex;
		align-items: center;
		width: 100%;
		flex-direction: row;
		gap: var(--gap-sm);

		.iconified-input {
			flex-grow: 1;
			:deep(input) {
				width: 100%;
				flex-basis: auto;
			}
		}
	}
}

.table {
	border: 1px solid var(--color-bg);
}

.table-row {
	grid-template-columns: min-content auto;
}

.table-content {
	max-height: calc(5 * (18px + 2rem));
	height: calc(5 * (18px + 2rem));
	overflow-y: auto;
}

.select-checkbox {
	button.checkbox {
		border: none;
	}
}

.button-row {
	display: flex;
	flex-direction: row;
	justify-content: space-between;
	align-items: center;
	gap: var(--gap-md);

	.transparent {
		padding: var(--gap-sm) 0;
	}
}

.empty {
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 1.5rem;
	font-weight: bolder;
	color: var(--color-contrast);
}

.card-divider {
	margin: var(--gap-md) var(--gap-lg) 0 var(--gap-lg);
}

.creation-shell {
	width: min(52rem, calc(100vw - 7rem));
	color: #f7f4fb;
}

.creation-intro {
	min-height: 8.5rem;
	display: grid;
	grid-template-columns: minmax(0, 1fr) 15rem;
	gap: 1.25rem;
	padding: 1.15rem;
	border: 1px solid rgba(179, 95, 255, 0.2);
	border-radius: 1.15rem;
	background:
		radial-gradient(circle at 18% 0, rgba(144, 56, 226, 0.22), transparent 45%),
		rgba(10, 13, 22, 0.76);
}

.intro-copy {
	display: flex;
	flex-direction: column;
	justify-content: center;
	padding-left: 0.35rem;
}

.intro-copy > span {
	color: #c47bff;
	font-size: 0.68rem;
	font-weight: 850;
	letter-spacing: 0.14em;
}

.intro-copy h2 {
	margin: 0.28rem 0 0.35rem;
	font-size: clamp(1.55rem, 3vw, 2.25rem);
	line-height: 1;
}

.intro-copy p {
	max-width: 31rem;
	margin: 0;
	color: #aeb3c0;
	font-size: 0.83rem;
	line-height: 1.45;
}

.cover-preview {
	position: relative;
	overflow: hidden;
	min-height: 6.2rem;
	border: 1px solid rgba(255, 255, 255, 0.13);
	border-radius: 0.9rem;
	background-position: center;
	background-size: cover;
	box-shadow: 0 14px 32px rgba(0, 0, 0, 0.28);
	transition: background-image 180ms ease;
}

.cover-preview::after {
	content: '';
	position: absolute;
	inset: 0;
	background: linear-gradient(180deg, transparent 22%, rgba(4, 7, 13, 0.86));
}

.cover-preview span {
	position: absolute;
	z-index: 1;
	left: 0.8rem;
	right: 0.8rem;
	bottom: 0.65rem;
	color: #e8dcf4;
	font-size: 0.66rem;
	font-weight: 700;
}

.creation-tabs {
	display: grid;
	grid-template-columns: repeat(3, 1fr);
	gap: 0.65rem;
	margin: 0.8rem 0;
}

.creation-tabs button {
	min-width: 0;
	padding: 0.72rem 0.85rem;
	display: flex;
	align-items: center;
	gap: 0.68rem;
	color: #a9afbd;
	text-align: left;
	border: 1px solid rgba(255, 255, 255, 0.065);
	border-radius: 0.9rem;
	background: rgba(255, 255, 255, 0.032);
	cursor: pointer;
	transition:
		transform 160ms cubic-bezier(0.22, 1, 0.36, 1),
		border-color 160ms ease,
		background 160ms ease;
}

.creation-tabs button:hover {
	transform: translateY(-1px);
	border-color: rgba(179, 93, 255, 0.25);
	background: rgba(132, 54, 215, 0.09);
}

.creation-tabs button.active {
	color: #f7f1ff;
	border-color: rgba(190, 110, 255, 0.5);
	background: linear-gradient(135deg, rgba(137, 55, 226, 0.25), rgba(79, 28, 143, 0.13));
	box-shadow:
		inset 0 0 0 1px rgba(212, 157, 255, 0.07),
		0 8px 28px rgba(101, 32, 182, 0.12);
}

.creation-tabs svg {
	width: 1.2rem;
	flex: none;
	color: #bd75ff;
}

.creation-tabs span {
	min-width: 0;
	display: flex;
	flex-direction: column;
}

.creation-tabs strong {
	font-size: 0.82rem;
}
.creation-tabs small {
	margin-top: 0.12rem;
	color: #818897;
	font-size: 0.66rem;
}

.creation-view {
	display: flex;
	flex-direction: column;
	gap: 0.7rem;
}

.creation-panel {
	padding: 1rem;
	border: 1px solid rgba(255, 255, 255, 0.07);
	border-radius: 1rem;
	background: rgba(13, 18, 29, 0.88);
}

.panel-heading {
	display: flex;
	align-items: center;
	gap: 0.7rem;
	margin-bottom: 0.85rem;
}

.panel-heading > span {
	width: 2rem;
	height: 2rem;
	display: grid;
	place-items: center;
	flex: none;
	color: #d7a7ff;
	border: 1px solid rgba(178, 91, 255, 0.28);
	border-radius: 0.65rem;
	background: rgba(135, 54, 218, 0.16);
	font-size: 0.67rem;
	font-weight: 850;
}

.panel-heading h3 {
	margin: 0;
	font-size: 0.9rem;
}
.panel-heading p {
	margin: 0.15rem 0 0;
	color: #848b9a;
	font-size: 0.68rem;
}

.identity-fields {
	display: grid;
	grid-template-columns: auto minmax(0, 1fr);
	align-items: end;
	gap: 0.9rem;
}

.image-upload {
	position: relative;
	display: grid;
	grid-template-columns: auto auto;
	align-items: center;
	gap: 0.55rem;
}

.image-upload :deep(.avatar) {
	border: 1px solid rgba(191, 111, 255, 0.26);
	background: rgba(255, 255, 255, 0.04);
}

.icon-upload,
.icon-reset {
	min-height: 2.65rem;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	gap: 0.45rem;
	color: #d9c1eb;
	border: 1px solid rgba(255, 255, 255, 0.08);
	border-radius: 0.72rem;
	background: rgba(255, 255, 255, 0.045);
	cursor: pointer;
}

.icon-upload {
	padding: 0 0.8rem;
	font-size: 0.73rem;
	font-weight: 700;
}
.icon-upload svg,
.icon-reset svg {
	width: 0.95rem;
}
.icon-reset {
	position: absolute;
	width: 1.65rem;
	min-height: 1.65rem;
	left: 3.4rem;
	top: -0.4rem;
	color: #ff9ab3;
}

.field-control {
	display: flex;
	flex-direction: column;
	gap: 0.38rem;
}

.field-control > span,
.loader-version-row > span {
	color: #c6cad3;
	font-size: 0.7rem;
	font-weight: 750;
}

.field-control input,
.path-input input {
	min-height: 2.65rem;
	box-sizing: border-box;
	color: #f5f2f8;
	border: 1px solid rgba(255, 255, 255, 0.085);
	border-radius: 0.75rem;
	outline: none;
	background: rgba(5, 9, 16, 0.7);
}

.field-control input {
	width: 100%;
	padding: 0 0.85rem;
}
.field-control input:focus {
	border-color: rgba(184, 100, 255, 0.55);
	box-shadow: 0 0 0 3px rgba(144, 57, 226, 0.11);
}

.loader-grid {
	display: grid;
	grid-template-columns: repeat(5, minmax(0, 1fr));
	gap: 0.48rem;
}

.loader-grid button {
	min-width: 0;
	min-height: 3.35rem;
	padding: 0.55rem;
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	justify-content: center;
	color: #adb3c0;
	border: 1px solid rgba(255, 255, 255, 0.065);
	border-radius: 0.72rem;
	background: rgba(255, 255, 255, 0.03);
	cursor: pointer;
	transition:
		transform 150ms cubic-bezier(0.22, 1, 0.36, 1),
		border-color 150ms ease,
		background 150ms ease;
}

.loader-grid button:hover {
	transform: translateY(-1px);
}
.loader-grid button.active {
	color: #fff;
	border-color: rgba(189, 107, 255, 0.46);
	background: rgba(132, 50, 219, 0.18);
}
.loader-grid strong {
	font-size: 0.72rem;
}
.loader-grid small {
	margin-top: 0.18rem;
	overflow: hidden;
	color: #747c8b;
	font-size: 0.58rem;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.version-row {
	display: grid;
	grid-template-columns: minmax(0, 1fr) auto;
	align-items: end;
	gap: 0.9rem;
	margin-top: 0.75rem;
}

.version-select :deep(.multiselect),
.loader-version-row :deep(.multiselect) {
	min-height: 2.65rem;
}
.version-select :deep(.multiselect__tags),
.loader-version-row :deep(.multiselect__tags) {
	min-height: 2.65rem;
	padding-top: 0.65rem;
	border: 1px solid rgba(255, 255, 255, 0.085);
	border-radius: 0.75rem;
	background: rgba(5, 9, 16, 0.7);
}
.version-select :deep(.multiselect__content-wrapper),
.loader-version-row :deep(.multiselect__content-wrapper) {
	border-color: rgba(182, 99, 255, 0.25);
	background: #111622;
}
.version-select :deep(.multiselect__option--highlight),
.loader-version-row :deep(.multiselect__option--highlight) {
	background: #7627d1;
}

.snapshot-toggle {
	min-height: 2.65rem;
	padding-bottom: 0.1rem;
}
.snapshot-toggle :deep(label) {
	color: #aeb4c0;
	font-size: 0.7rem;
}

.loader-version-row {
	margin-top: 0.7rem;
	display: grid;
	grid-template-columns: auto 1fr;
	align-items: center;
	gap: 0.6rem 0.8rem;
}

.loader-version-row > :deep(.multiselect),
.loader-version-row .warning {
	grid-column: 2;
}

.mini-tabs {
	display: flex;
	gap: 0.35rem;
}

.mini-tabs button {
	padding: 0.42rem 0.65rem;
	color: #9299a7;
	border: 1px solid rgba(255, 255, 255, 0.06);
	border-radius: 0.6rem;
	background: rgba(255, 255, 255, 0.03);
	cursor: pointer;
	font-size: 0.66rem;
}

.mini-tabs button.active {
	color: #e9d5ff;
	border-color: rgba(184, 99, 255, 0.38);
	background: rgba(131, 51, 215, 0.16);
}
.warning {
	margin: 0;
	color: #f4b567;
	font-size: 0.7rem;
}

.creation-actions {
	display: grid;
	grid-template-columns: minmax(0, 1fr) auto auto;
	align-items: center;
	gap: 0.55rem;
	padding: 0.7rem 0.15rem 0;
}

.creation-actions > div {
	min-width: 0;
	display: flex;
	flex-direction: column;
}
.creation-actions > div strong {
	overflow: hidden;
	font-size: 0.78rem;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.creation-actions > div span {
	margin-top: 0.12rem;
	color: #7f8795;
	font-size: 0.65rem;
}
.creation-actions :deep(button) {
	min-height: 2.55rem;
	border-radius: 0.72rem;
}

.file-dropzone {
	min-height: 19rem;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	color: #f5effb;
	border: 1px dashed rgba(190, 110, 255, 0.45);
	border-radius: 1.1rem;
	background:
		radial-gradient(circle at 50% 40%, rgba(144, 57, 226, 0.17), transparent 34%),
		rgba(11, 15, 24, 0.86);
	cursor: pointer;
	transition:
		transform 180ms cubic-bezier(0.22, 1, 0.36, 1),
		border-color 180ms ease,
		background 180ms ease;
}

.file-dropzone:hover {
	transform: translateY(-2px);
	border-color: rgba(207, 145, 255, 0.75);
	background-color: rgba(19, 18, 33, 0.92);
}
.drop-icon {
	width: 3.5rem;
	height: 3.5rem;
	display: grid;
	place-items: center;
	color: #d6a5ff;
	border-radius: 1rem;
	background: rgba(139, 54, 224, 0.18);
}
.drop-icon svg {
	width: 1.7rem;
}
.file-dropzone strong {
	margin-top: 1rem;
	font-size: 1rem;
}
.file-dropzone p {
	margin: 0.35rem 0 1rem;
	color: #8f96a4;
	font-size: 0.75rem;
}
.file-dropzone code {
	color: #ca8cff;
}
.browse-file {
	padding: 0.65rem 0.9rem;
	display: flex;
	align-items: center;
	gap: 0.45rem;
	border-radius: 0.7rem;
	background: linear-gradient(135deg, #8b35ec, #6120bd);
	font-size: 0.72rem;
	font-weight: 780;
}
.browse-file svg {
	width: 0.95rem;
}
.import-note {
	padding: 0.75rem 0.9rem;
	display: flex;
	align-items: center;
	gap: 0.55rem;
	color: #8f96a4;
	border: 1px solid rgba(255, 255, 255, 0.055);
	border-radius: 0.75rem;
	background: rgba(255, 255, 255, 0.025);
	font-size: 0.68rem;
}
.import-note svg {
	width: 1rem;
	color: #ad65ef;
}

.launcher-view {
	padding: 0.1rem;
}
.launcher-view :deep(.chips) {
	margin-bottom: 0.2rem;
}
.launcher-view .path-selection {
	padding: 0.9rem;
	border: 1px solid rgba(255, 255, 255, 0.065);
	background: rgba(12, 17, 27, 0.88);
}
.launcher-view .table {
	overflow: hidden;
	border-color: rgba(255, 255, 255, 0.07);
	border-radius: 0.85rem;
}
.launcher-view .table-head {
	color: #c9cdd6;
	background: rgba(137, 54, 219, 0.12);
}
.launcher-view .table-content {
	background: rgba(9, 13, 21, 0.78);
}
.launcher-view .button-row {
	margin-top: 0.1rem;
}

.creation-view-enter-active,
.creation-view-leave-active {
	transition:
		opacity 160ms ease,
		transform 160ms cubic-bezier(0.22, 1, 0.36, 1);
}
.creation-view-enter-from {
	opacity: 0;
	transform: translateY(8px);
}
.creation-view-leave-to {
	opacity: 0;
	transform: translateY(-5px);
}

.blockera-creation-modal :deep(.modal-overlay.standard) {
	background: radial-gradient(circle at 50% 0, rgba(53, 20, 91, 0.42), rgba(3, 6, 11, 0.92) 58%);
}

.blockera-creation-modal :deep(.modal-body) {
	border: 1px solid rgba(180, 96, 255, 0.24);
	background: linear-gradient(145deg, rgba(14, 18, 28, 0.99), rgba(8, 11, 19, 0.99));
	box-shadow:
		0 34px 100px rgba(0, 0, 0, 0.58),
		0 0 46px rgba(119, 38, 203, 0.08);
}

@media (max-width: 780px) {
	.creation-shell {
		width: calc(100vw - 6rem);
	}
	.creation-intro {
		grid-template-columns: 1fr;
	}
	.cover-preview {
		display: none;
	}
	.loader-grid {
		grid-template-columns: repeat(3, minmax(0, 1fr));
	}
	.identity-fields {
		grid-template-columns: 1fr;
	}
	.version-row {
		grid-template-columns: 1fr;
	}
	.creation-actions {
		grid-template-columns: 1fr 1fr;
	}
	.creation-actions > div {
		grid-column: 1 / -1;
	}
}

@media (prefers-reduced-motion: reduce) {
	.creation-tabs button,
	.loader-grid button,
	.file-dropzone,
	.creation-view-enter-active,
	.creation-view-leave-active {
		transition: none !important;
	}
	.creation-tabs button:hover,
	.loader-grid button:hover,
	.file-dropzone:hover {
		transform: none;
	}
}
</style>
