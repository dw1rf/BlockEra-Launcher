<template>
	<div v-if="instance" class="blockera-instance">
		<section
			class="instance-hero"
			@contextmenu.prevent.stop="(event) => handleRightClick(event, instance.path)"
		>
			<ExportModal ref="exportModal" :instance="instance" />
			<InstanceSettingsModal ref="settingsModal" :instance="instance" :offline="offline" />
			<div class="instance-hero-main">
				<Avatar :src="icon" :alt="instance.name" size="88px" :tint-by="instance.path" />
				<div class="instance-identity">
					<span class="instance-eyebrow">ТЕКУЩАЯ СБОРКА</span>
					<h1>{{ instance.name }}</h1>
					<div class="instance-meta">
						<span><GameIcon /> {{ instance.game_version }}</span>
						<span class="capitalize">{{ instance.loader }}</span>
						<span><TimerIcon /> {{ timePlayed > 0 ? timePlayedHumanized : 'Ещё не запускалась' }}</span>
					</div>
				</div>
			</div>
			<div class="instance-primary-actions">
				<button
					v-if="instance.install_stage !== 'installed'"
					class="instance-play repair"
					:disabled="repairing"
					@click="repairInstance()"
				><DownloadIcon /> {{ repairing ? 'Исправляем…' : 'Исправить сборку' }}</button>
				<button v-else-if="playing" class="instance-play stop" @click="stopInstance('InstancePage')"><StopCircleIcon /> Остановить</button>
				<button v-else class="instance-play" :disabled="loading" @click="startInstance('InstancePage')"><PlayIcon /> {{ loading ? 'Запуск…' : 'Играть' }}</button>
				<button class="instance-icon-action" aria-label="Настройки сборки" @click="settingsModal.show()"><SettingsIcon /></button>
				<button class="instance-icon-action" aria-label="Экспорт сборки" @click="$refs.exportModal.show()"><PackageIcon /></button>
			</div>
		</section>

		<section class="instance-health-strip">
			<div><span class="health-dot" :class="healthState.tone"></span><span><small>СОСТОЯНИЕ</small><strong>{{ healthState.label }}</strong></span></div>
			<div><DownloadIcon /><span><small>ОБНОВЛЕНИЯ</small><strong>{{ updateSummary }}</strong></span></div>
			<div><GameIcon /><span><small>JAVA</small><strong>{{ optimalJavaLabel }}</strong></span></div>
			<div><PackageIcon /><span><small>БЭКАПЫ</small><strong>Перед важными действиями</strong></span></div>
		</section>

		<div class="instance-layout">
			<main class="instance-content-card">
				<nav class="instance-tabs" aria-label="Разделы сборки">
					<router-link v-for="tab in tabs" :key="tab.href" :to="tab.href">{{ tab.label }}</router-link>
				</nav>
			<RouterView v-slot="{ Component }" :key="instance.path">
				<template v-if="Component">
					<Suspense
						:key="instance.path"
						@pending="loadingBar.startLoading()"
						@resolve="loadingBar.stopLoading()"
					>
						<component
							:is="Component"
							:instance="instance"
							:options="options"
							:offline="offline"
							:playing="playing"
							:versions="modrinthVersions"
							:installed="instance.install_stage !== 'installed'"
							@play="updatePlayState"
							@stop="() => stopInstance('InstanceSubpage')"
						></component>
						<template #fallback>
							<LoadingIndicator />
						</template>
					</Suspense>
				</template>
			</RouterView>
			</main>
			<aside class="instance-quick-panel">
				<div class="quick-panel-heading"><span>БЫСТРЫЙ ДОСТУП</span><strong>Файлы сборки</strong></div>
				<button v-for="folder in quickFolders" :key="folder.id" @click="openProfileFolder(instance.path, folder.id)">
					<FolderOpenIcon /><span><strong>{{ folder.label }}</strong><small>{{ folder.description }}</small></span>
				</button>
				<button class="backup-toggle" @click="toggleAutomaticBackups">
					<span class="toggle-indicator" :class="{ enabled: automaticBackups }"></span>
					<span><strong>Автоматические бэкапы</strong><small>{{ automaticBackups ? 'Включены' : 'Выключены' }}</small></span>
				</button>
				<button class="quick-repair" :disabled="repairing" @click="repairInstance"><DownloadIcon /><span><strong>Проверить файлы</strong><small>Переустановить повреждённые компоненты</small></span></button>
			</aside>
		</div>
		<ContextMenu ref="options" @option-clicked="handleOptionsClick">
			<template #play> <PlayIcon /> Play </template>
			<template #stop> <StopCircleIcon /> Stop </template>
			<template #add_content> <PlusIcon /> Add content </template>
			<template #edit> <EditIcon /> Edit </template>
			<template #copy_path> <ClipboardCopyIcon /> Copy path </template>
			<template #open_folder> <FolderOpenIcon /> Open folder </template>
			<template #copy_link> <ClipboardCopyIcon /> Copy link </template>
			<template #open_link> <GlobeIcon /> Open in Modrinth <ExternalIcon /> </template>
			<template #copy_names><EditIcon />Copy names</template>
			<template #copy_slugs><HashIcon />Copy slugs</template>
			<template #copy_links><GlobeIcon />Copy links</template>
			<template #toggle><EditIcon />Toggle selected</template>
			<template #disable><XIcon />Disable selected</template>
			<template #enable><CheckCircleIcon />Enable selected</template>
			<template #hide_show><EyeIcon />Show/Hide unselected</template>
			<template #update_all><UpdatedIcon />Обновить выбранное</template>
			<template #filter_update><UpdatedIcon />Select Updatable</template>
		</ContextMenu>
	</div>
</template>
<script setup>
import {
	CheckCircleIcon,
	ClipboardCopyIcon,
	DownloadIcon,
	EditIcon,
	ExternalIcon,
	EyeIcon,
	FolderOpenIcon,
	GameIcon,
	GlobeIcon,
	HashIcon,
	PackageIcon,
	PlayIcon,
	PlusIcon,
	SettingsIcon,
	StopCircleIcon,
	TimerIcon,
	UpdatedIcon,
	XIcon,
} from '@modrinth/assets'
import { Avatar, injectNotificationManager, LoadingIndicator } from '@modrinth/ui'
import { convertFileSrc } from '@tauri-apps/api/core'
import dayjs from 'dayjs'
import duration from 'dayjs/plugin/duration'
import relativeTime from 'dayjs/plugin/relativeTime'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import ContextMenu from '@/components/ui/ContextMenu.vue'
import ExportModal from '@/components/ui/ExportModal.vue'
import InstanceSettingsModal from '@/components/ui/modal/InstanceSettingsModal.vue'
import { trackEvent } from '@/helpers/analytics'
import {
	automaticWorldBackupsEnabled,
	backupProfileWorlds,
	setAutomaticWorldBackups,
} from '@/helpers/backups'
import { get_project, get_version_many } from '@/helpers/cache.js'
import { process_listener, profile_listener } from '@/helpers/events'
import { get_by_profile_path } from '@/helpers/process'
import { finish_install, get, get_full_path, get_optimal_jre_key, kill, run } from '@/helpers/profile'
import { openProfileFolder, showProfileInFolder } from '@/helpers/utils.js'
import { handleSevereError } from '@/store/error.js'
import { useSelectedInstance } from '@/store/selected-instance'
import { useBreadcrumbs, useLoading } from '@/store/state'

dayjs.extend(duration)
dayjs.extend(relativeTime)

const { handleError } = injectNotificationManager()
const route = useRoute()

const router = useRouter()
const breadcrumbs = useBreadcrumbs()
const selectedInstanceStore = useSelectedInstance()

const offline = ref(!navigator.onLine)
window.addEventListener('offline', () => {
	offline.value = true
})
window.addEventListener('online', () => {
	offline.value = false
})

const instance = ref()
const modrinthVersions = ref([])
const playing = ref(false)
const loading = ref(false)
const repairing = ref(false)
const optimalJavaLabel = ref('Определяем…')
const automaticBackups = ref(automaticWorldBackupsEnabled())

async function fetchInstance() {
	instance.value = await get(route.params.id).catch(handleError)
	if (instance.value?.path) selectedInstanceStore.setSelectedInstance(instance.value.path)

	if (!offline.value && instance.value.linked_data && instance.value.linked_data.project_id) {
		get_project(instance.value.linked_data.project_id, 'must_revalidate')
			.catch(handleError)
			.then((project) => {
				if (project && project.versions) {
					get_version_many(project.versions, 'must_revalidate')
						.catch(handleError)
						.then((versions) => {
							modrinthVersions.value = versions.sort(
								(a, b) => dayjs(b.date_published) - dayjs(a.date_published),
							)
						})
				}
			})
	}

	await updatePlayState()
	const optimalJava = await get_optimal_jre_key(route.params.id).catch(() => null)
	optimalJavaLabel.value = optimalJava
		? `Java ${String(optimalJava).replace(/\D/g, '') || optimalJava}`
		: 'Автоматический выбор'
}

async function updatePlayState() {
	const runningProcesses = await get_by_profile_path(route.params.id).catch(handleError)

	playing.value = runningProcesses.length > 0
}

await fetchInstance()
watch(
	() => route.params.id,
	async () => {
		if (route.params.id && route.path.startsWith('/instance')) {
			await fetchInstance()
		}
	},
)

const basePath = computed(() => `/instance/${encodeURIComponent(route.params.id)}`)

const tabs = computed(() => [
	{
		label: 'Контент',
		href: `${basePath.value}`,
	},
	{
		label: 'Миры',
		href: `${basePath.value}/worlds`,
	},
	{
		label: 'Логи',
		href: `${basePath.value}/logs`,
	},
])

breadcrumbs.setName(
	'Instance',
	instance.value.name.length > 40
		? instance.value.name.substring(0, 40) + '...'
		: instance.value.name,
)

breadcrumbs.setContext({
	name: instance.value.name,
	link: route.path,
	query: route.query,
})

const loadingBar = useLoading()

const options = ref(null)

const startInstance = async (context) => {
	loading.value = true
	try {
		await run(route.params.id)
		playing.value = true
	} catch (err) {
		handleSevereError(err, { profilePath: route.params.id })
	}
	loading.value = false

	trackEvent('InstanceStart', {
		loader: instance.value.loader,
		game_version: instance.value.game_version,
		source: context,
	})
}

const stopInstance = async (context) => {
	playing.value = false
	await kill(route.params.id).catch(handleError)

	trackEvent('InstanceStop', {
		loader: instance.value.loader,
		game_version: instance.value.game_version,
		source: context,
	})
}

const repairInstance = async () => {
	if (repairing.value) return
	repairing.value = true
	if (automaticBackups.value) {
		try {
			const backup = await backupProfileWorlds(instance.value.path)
			if (
				backup.failures.length > 0 &&
				!window.confirm(`Не удалось создать ${backup.failures.length} резервных копий. Продолжить ремонт без них?`)
			) {
				repairing.value = false
				return
			}
		} catch {
			if (!window.confirm('Не удалось создать резервные копии миров. Продолжить ремонт без них?')) {
				repairing.value = false
				return
			}
		}
	}
	await finish_install(instance.value).catch(handleError)
	repairing.value = false
}

function toggleAutomaticBackups() {
	automaticBackups.value = !automaticBackups.value
	setAutomaticWorldBackups(automaticBackups.value)
}

const quickFolders = [
	{ id: 'root', label: 'Корень сборки', description: 'Все файлы профиля' },
	{ id: 'mods', label: 'Моды', description: 'Установленные модификации' },
	{ id: 'resource_packs', label: 'Текстуры', description: 'Наборы ресурсов' },
	{ id: 'shader_packs', label: 'Шейдеры', description: 'Графические наборы' },
	{ id: 'saves', label: 'Миры', description: 'Сохранения Minecraft' },
	{ id: 'backups', label: 'Резервные копии', description: 'Архивы миров' },
]

const healthState = computed(() => {
	if (offline.value) return { label: 'Офлайн-режим', tone: 'warning' }
	if (instance.value?.install_stage !== 'installed') return { label: 'Требуется ремонт', tone: 'danger' }
	return { label: 'Готова к запуску', tone: 'success' }
})

const updateSummary = computed(() => {
	if (offline.value) return 'Проверка недоступна'
	if (instance.value?.linked_data && modrinthVersions.value[0]?.id !== instance.value.linked_data.version_id) {
		return 'Доступна новая версия'
	}
	return 'Всё актуально'
})

const handleRightClick = (event) => {
	const baseOptions = [
		{ name: 'add_content' },
		{ type: 'divider' },
		{ name: 'edit' },
		{ name: 'open_folder' },
		{ name: 'copy_path' },
	]

	options.value.showMenu(
		event,
		instance.value,
		playing.value
			? [
					{
						name: 'stop',
						color: 'danger',
					},
					...baseOptions,
				]
			: [
					{
						name: 'play',
						color: 'primary',
					},
					...baseOptions,
				],
	)
}

const handleOptionsClick = async (args) => {
	switch (args.option) {
		case 'play':
			await startInstance('InstancePageContextMenu')
			break
		case 'stop':
			await stopInstance('InstancePageContextMenu')
			break
		case 'add_content':
			await router.push({
				path: `/browse/${instance.value.loader === 'vanilla' ? 'datapack' : 'mod'}`,
				query: { i: route.params.id },
			})
			break
		case 'edit':
			await router.push({
				path: `/instance/${encodeURIComponent(route.params.id)}/options`,
			})
			break
		case 'open_folder':
			await showProfileInFolder(instance.value.path)
			break
		case 'copy_path': {
			const fullPath = await get_full_path(instance.value.path)
			await navigator.clipboard.writeText(fullPath)
			break
		}
	}
}

const unlistenProfiles = await profile_listener(async (event) => {
	if (event.profile_path_id === route.params.id) {
		if (event.event === 'removed') {
			await router.push({
				path: '/',
			})
			return
		}
		instance.value = await get(route.params.id).catch(handleError)
	}
})

const unlistenProcesses = await process_listener((e) => {
	if (e.event === 'finished' && e.profile_path_id === route.params.id) {
		playing.value = false
	}
})

const icon = computed(() =>
	instance.value.icon_path ? convertFileSrc(instance.value.icon_path) : null,
)

const settingsModal = ref()
const exportModal = ref()

watch(
	() => route.query.action,
	async (action) => {
		if (!action) return
		await new Promise((resolve) => setTimeout(resolve, 0))
		if (action === 'settings') settingsModal.value?.show()
		if (action === 'export') exportModal.value?.show()
		await router.replace({ path: route.path, query: {} })
	},
	{ immediate: true, flush: 'post' },
)

const timePlayed = computed(() => {
	return instance.value.recent_time_played + instance.value.submitted_time_played
})

const timePlayedHumanized = computed(() => {
	const duration = dayjs.duration(timePlayed.value, 'seconds')
	const hours = Math.floor(duration.asHours())
	if (hours >= 1) {
		return `${hours} ч.`
	}

	const minutes = Math.floor(duration.asMinutes())
	if (minutes >= 1) {
		return `${minutes} мин.`
	}

	const seconds = Math.floor(duration.asSeconds())
	return `${seconds} сек.`
})

onUnmounted(() => {
	unlistenProcesses()
	unlistenProfiles()
})
</script>

<style scoped lang="scss">
.instance-card {
	display: flex;
	flex-direction: column;
	gap: 1rem;
}

Button {
	width: 100%;
}

.button-group {
	display: flex;
	flex-direction: row;
	gap: 0.5rem;
}

.side-cards {
	position: fixed;
	width: 300px;
	display: flex;
	flex-direction: column;

	min-height: calc(100vh - 3.25rem);
	max-height: calc(100vh - 3.25rem);
	overflow-y: auto;
	-ms-overflow-style: none;
	scrollbar-width: none;

	&::-webkit-scrollbar {
		width: 0;
		background: transparent;
	}

	.card {
		min-height: unset;
		margin-bottom: 0;
	}
}

.instance-nav {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	justify-content: center;
	padding: 1rem;
	gap: 0.5rem;
	background: var(--color-raised-bg);
	height: 100%;
}

.name {
	font-size: 1.25rem;
	color: var(--color-contrast);
	overflow: hidden;
	text-overflow: ellipsis;
}

.metadata {
	text-transform: capitalize;
}

.instance-container {
	display: flex;
	flex-direction: row;
	overflow: auto;
	gap: 1rem;
	min-height: 100%;
	padding: 1rem;
}

.instance-info {
	display: flex;
	flex-direction: column;
	width: 100%;
}

.badge {
	display: flex;
	align-items: center;
	font-weight: bold;
	width: fit-content;
	color: var(--color-orange);
}

.pages-list {
	display: flex;
	flex-direction: column;
	gap: var(--gap-xs);

	.btn {
		font-size: 100%;
		font-weight: 400;
		background: inherit;
		transition: all ease-in-out 0.1s;
		width: 100%;
		color: var(--color-primary);
		box-shadow: none;

		&.router-link-exact-active {
			box-shadow: var(--shadow-inset-lg);
			background: var(--color-button-bg);
			color: var(--color-contrast);
		}

		&:hover {
			background-color: var(--color-button-bg);
			color: var(--color-contrast);
			box-shadow: var(--shadow-inset-lg);
			text-decoration: none;
		}

		svg {
			width: 1.3rem;
			height: 1.3rem;
		}
	}
}

.instance-nav {
	display: flex;
	flex-direction: row;
	align-items: flex-start;
	justify-content: left;
	padding: 1rem;
	gap: 0.5rem;
	height: min-content;
	width: 100%;
}

.instance-button {
	width: fit-content;
}

.actions {
	display: flex;
	flex-direction: column;
	justify-content: flex-start;
	gap: 0.5rem;
}

.content {
	margin: 0 1rem 0.5rem 20rem;
	width: calc(100% - 20rem);
	display: flex;
	flex-direction: column;
	overflow: auto;
}

.stats {
	grid-area: stats;
	display: flex;
	flex-direction: column;
	flex-wrap: wrap;
	gap: var(--gap-md);

	.stat {
		display: flex;
		flex-direction: row;
		align-items: center;
		width: fit-content;
		gap: var(--gap-xs);
		--stat-strong-size: 1.25rem;

		strong {
			font-size: var(--stat-strong-size);
		}

		p {
			margin: 0;
		}

		svg {
			height: var(--stat-strong-size);
			width: var(--stat-strong-size);
		}
	}

	.date {
		margin-top: auto;
	}

	@media screen and (max-width: 750px) {
		flex-direction: row;
		column-gap: var(--gap-md);
		margin-top: var(--gap-xs);
	}

	@media screen and (max-width: 600px) {
		margin-top: 0;

		.stat-label {
			display: none;
		}
	}
}

.blockera-instance {
	min-height: 100%;
	padding: 22px 28px 34px;
	box-sizing: border-box;
	color: #f8f7fc;
	background:
		radial-gradient(circle at 16% -10%, rgba(118, 50, 211, .18), transparent 31rem),
		linear-gradient(180deg, #090d16 0%, #0b111b 100%);
}

.instance-hero {
	position: relative;
	padding: 25px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 20px;
	background: linear-gradient(125deg, rgba(21, 27, 39, .98), rgba(17, 18, 31, .96));
	border: 1px solid rgba(171, 91, 255, .26);
	border-radius: 20px;
	box-shadow: 0 24px 55px rgba(0, 0, 0, .26);
	overflow: hidden;

	&::after {
		content: '';
		position: absolute;
		width: 360px;
		height: 220px;
		right: -90px;
		top: -100px;
		background: radial-gradient(circle, rgba(151, 63, 241, .23), transparent 68%);
		pointer-events: none;
	}
}

.instance-hero-main,
.instance-primary-actions,
.instance-meta,
.instance-health-strip > div,
.instance-quick-panel button { display: flex; align-items: center; }
.instance-hero-main { min-width: 0; gap: 17px; position: relative; z-index: 1; }
.instance-identity { min-width: 0; }
.instance-eyebrow { color: #c47cff; font-size: 10px; font-weight: 850; letter-spacing: .13em; }
.instance-identity h1 { max-width: 620px; margin: 5px 0 11px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: clamp(28px, 3vw, 42px); line-height: 1; }
.instance-meta { flex-wrap: wrap; gap: 8px; }
.instance-meta span { min-height: 27px; padding: 0 10px; display: inline-flex; align-items: center; gap: 6px; color: #c4c7d0; background: rgba(255,255,255,.045); border: 1px solid rgba(255,255,255,.075); border-radius: 9px; font-size: 12px; }
.instance-meta svg { width: 14px; height: 14px; color: #be72ff; }
.instance-primary-actions { position: relative; z-index: 1; gap: 9px; }
.instance-play,
.instance-icon-action {
	width: auto !important;
	height: 46px;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	gap: 9px;
	color: white;
	border: 1px solid rgba(255,255,255,.1);
	border-radius: 12px;
	cursor: pointer;
	transition: transform 170ms ease, filter 170ms ease, border-color 170ms ease;
}
.instance-play { min-width: 154px; padding: 0 21px; background: linear-gradient(135deg, #8d35ef, #6520c8); font-size: 15px; font-weight: 800; box-shadow: 0 13px 28px rgba(108, 34, 209, .26); }
.instance-play.stop { background: linear-gradient(135deg, #d83c67, #9d2448); }
.instance-play.repair { background: linear-gradient(135deg, #a347ec, #6b27c4); }
.instance-icon-action { width: 46px !important; padding: 0; background: rgba(255,255,255,.055); }
.instance-play:hover:not(:disabled), .instance-icon-action:hover { transform: translateY(-1px); filter: brightness(1.12); border-color: rgba(196,119,255,.5); }
.instance-play:disabled { opacity: .58; cursor: wait; }
.instance-play svg, .instance-icon-action svg { width: 19px; height: 19px; }

.instance-health-strip {
	margin: 13px 0;
	display: grid;
	grid-template-columns: repeat(4, minmax(0, 1fr));
	background: rgba(16, 22, 33, .84);
	border: 1px solid rgba(255,255,255,.07);
	border-radius: 16px;
	overflow: hidden;
}
.instance-health-strip > div { min-width: 0; gap: 10px; padding: 14px 16px; border-right: 1px solid rgba(255,255,255,.065); }
.instance-health-strip > div:last-child { border-right: 0; }
.instance-health-strip svg { width: 19px; color: #af63f6; }
.instance-health-strip span:not(.health-dot) { min-width: 0; display: flex; flex-direction: column; }
.instance-health-strip small { color: #777e8e; font-size: 9px; font-weight: 800; letter-spacing: .11em; }
.instance-health-strip strong { margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 12px; }
.health-dot { width: 10px; height: 10px; border-radius: 999px; box-shadow: 0 0 12px currentColor; }
.health-dot.success { color: #55d489; background: currentColor; }
.health-dot.warning { color: #f0b45d; background: currentColor; }
.health-dot.danger { color: #ff6688; background: currentColor; }

.instance-layout { display: grid; grid-template-columns: minmax(0, 1fr) 268px; gap: 13px; align-items: start; }
.instance-content-card,
.instance-quick-panel { background: rgba(14, 20, 30, .88); border: 1px solid rgba(255,255,255,.075); border-radius: 18px; }
.instance-content-card { min-width: 0; padding: 0 18px 18px; overflow: hidden; }
.instance-tabs { margin: 0 -18px 18px; padding: 0 18px; display: flex; gap: 6px; border-bottom: 1px solid rgba(255,255,255,.07); }
.instance-tabs a { position: relative; padding: 16px 14px 14px; color: #8f96a5; text-decoration: none; font-size: 13px; font-weight: 750; }
.instance-tabs a.router-link-exact-active { color: #f7eeff; }
.instance-tabs a.router-link-exact-active::after { content: ''; position: absolute; height: 2px; left: 12px; right: 12px; bottom: -1px; background: #a94fff; box-shadow: 0 0 12px #9a3eef; }
.instance-quick-panel { padding: 14px; display: flex; flex-direction: column; gap: 6px; }
.quick-panel-heading { padding: 5px 5px 10px; display: flex; flex-direction: column; }
.quick-panel-heading span { color: #aa65ed; font-size: 9px; font-weight: 850; letter-spacing: .13em; }
.quick-panel-heading strong { margin-top: 2px; font-size: 16px; }
.instance-quick-panel button { width: 100%; padding: 10px; gap: 10px; color: #eef0f5; text-align: left; background: rgba(255,255,255,.032); border: 1px solid transparent; border-radius: 11px; cursor: pointer; transition: background 160ms ease, border-color 160ms ease; }
.instance-quick-panel button:hover { background: rgba(149,72,231,.12); border-color: rgba(175,96,255,.25); }
.instance-quick-panel button > svg { width: 18px; color: #a95af2; }
.instance-quick-panel button span { min-width: 0; display: flex; flex-direction: column; }
.instance-quick-panel button strong { font-size: 12px; }
.instance-quick-panel button small { margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #868d9c; font-size: 10px; }
.instance-quick-panel .quick-repair { margin-top: 5px; color: #e8d2ff; background: rgba(139,59,218,.1); border-color: rgba(176,89,255,.18); }
.instance-quick-panel .backup-toggle { margin-top: 5px; }
.toggle-indicator { width: 30px; height: 17px; flex: 0 0 auto; position: relative; background: #313746; border-radius: 999px; transition: background 160ms ease; }
.toggle-indicator::after { content: ''; position: absolute; width: 11px; height: 11px; left: 3px; top: 3px; background: #a4a9b5; border-radius: 50%; transition: transform 160ms ease, background 160ms ease; }
.toggle-indicator.enabled { background: rgba(151,65,238,.58); }
.toggle-indicator.enabled::after { transform: translateX(13px); background: #e8cfff; }

@media (max-width: 1120px) {
	.instance-hero { align-items: flex-start; flex-direction: column; }
	.instance-health-strip { grid-template-columns: repeat(2, 1fr); }
	.instance-layout { grid-template-columns: 1fr; }
	.instance-quick-panel { display: grid; grid-template-columns: repeat(2, 1fr); }
	.quick-panel-heading { grid-column: 1 / -1; }
}

@media (max-width: 720px) {
	.blockera-instance { padding: 14px; }
	.instance-hero-main { align-items: flex-start; }
	.instance-health-strip { grid-template-columns: 1fr; }
	.instance-health-strip > div { border-right: 0; border-bottom: 1px solid rgba(255,255,255,.065); }
	.instance-quick-panel { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
	.instance-play, .instance-icon-action, .instance-quick-panel button { transition-duration: 1ms !important; }
}
</style>
