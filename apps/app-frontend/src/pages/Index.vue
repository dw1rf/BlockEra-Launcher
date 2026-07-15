<script setup lang="ts">
import {
	ChangeSkinIcon,
	CheckCircleIcon,
	ChevronRightIcon,
	DownloadIcon,
	EditIcon,
	FolderOpenIcon,
	GameIcon,
	ImageIcon,
	PackageIcon,
	PaletteIcon,
	PlayIcon,
	SettingsIcon,
	SpinnerIcon,
	StopCircleIcon,
	WorldIcon,
	WrenchIcon,
} from '@modrinth/assets'
import { Avatar, injectNotificationManager, SkinPreviewRenderer } from '@modrinth/ui'
import { convertFileSrc } from '@tauri-apps/api/core'
import dayjs from 'dayjs'
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import heroVibrant from '@/assets/launcher/hero-vibrant.jpg'
import steveSkin from '@/assets/skins/steve.png'
import ContextMenu from '@/components/ui/ContextMenu.vue'
import { trackEvent } from '@/helpers/analytics'
import { get_default_user, users } from '@/helpers/auth'
import { loading_listener, process_listener, profile_listener } from '@/helpers/events'
import { get_by_profile_path } from '@/helpers/process'
import { finish_install, kill, list, run } from '@/helpers/profile.js'
import { get_available_skins, get_normalized_skin_texture, type Skin } from '@/helpers/skins'
import { progress_bars_list } from '@/helpers/state.js'
import type { GameInstance } from '@/helpers/types'
import { openProfileFolder } from '@/helpers/utils'
import { useBreadcrumbs } from '@/store/breadcrumbs'
import { handleSevereError } from '@/store/error.js'
import { useSelectedInstance } from '@/store/selected-instance'

type LoadingBar = {
	id?: string
	title?: string
	message?: string
	current?: number
	total?: number
	bar_type?: {
		type?: string
		version?: number
		profile_path?: string
		pack_name?: string
	}
}

const { handleError } = injectNotificationManager()
const route = useRoute()
const router = useRouter()
const breadcrumbs = useBreadcrumbs()
const selectedInstanceStore = useSelectedInstance()

breadcrumbs.setRootContext({ name: 'Home', link: route.path })

const instances = ref<GameInstance[]>([])
const selectedPath = ref(localStorage.getItem('blockera-selected-instance') ?? '')
const playing = ref(false)
const actionPending = ref(false)
const installingPaths = ref(new Set<string>())
const loadingBars = ref<LoadingBar[]>([])
const instanceFolderMenu = ref<InstanceType<typeof ContextMenu> | null>(null)
const activeSkin = ref<Skin | null>(null)
const skinTexture = ref(steveSkin)
const playerName = ref('Minecraft игрок')

const selectedInstance = computed(() => {
	return instances.value.find((instance) => instance.path === selectedPath.value) ?? instances.value[0]
})

const visibleInstances = computed(() => instances.value.slice(0, 8))
const installed = computed(() => selectedInstance.value?.install_stage === 'installed')
const installing = computed(() =>
	selectedInstance.value
		? installingPaths.value.has(selectedInstance.value.path) ||
			selectedInstance.value.install_stage.includes('installing')
		: false,
)

const primaryActionLabel = computed(() => {
	if (actionPending.value) return 'Подготовка...'
	if (installing.value) return 'Установка в фоне...'
	if (playing.value) return 'Остановить'
	if (!installed.value) return 'Завершить установку'
	return 'Играть'
})

const loaderLabel = computed(() => {
	const loader = selectedInstance.value?.loader
	if (!loader) return 'Vanilla'
	return loader.charAt(0).toUpperCase() + loader.slice(1)
})

const selectedDescription = computed(() => {
	if (!selectedInstance.value) return ''
	if (selectedInstance.value.linked_data) {
		return 'Готовая сборка с синхронизацией контента, модов и настроек запуска.'
	}
	return 'Ваш личный профиль Minecraft — все миры, моды и настройки всегда под рукой.'
})

const activeDownloads = computed(() =>
	loadingBars.value.map((bar) => {
		const total = Number(bar.total ?? 0)
		const current = Number(bar.current ?? 0)
		const progress = total > 0 ? Math.min(100, Math.max(0, Math.round((current / total) * 100))) : 0
		let title = bar.title || bar.bar_type?.pack_name || bar.bar_type?.profile_path || 'Загрузка'
		if (bar.bar_type?.type === 'java_download') title = `Java ${bar.bar_type.version ?? ''}`.trim()
		return { ...bar, title, progress }
	}),
)

const lastPlayedText = computed(() => {
	if (!selectedInstance.value?.last_played) return 'Ещё не запускалась'
	return dayjs(selectedInstance.value.last_played).fromNow?.() ?? dayjs(selectedInstance.value.last_played).format('DD.MM.YYYY')
})

watch(
	selectedInstance,
	async (instance) => {
		if (!instance) return
		selectedPath.value = instance.path
		selectedInstanceStore.setSelectedInstance(instance.path)
		await refreshPlaying()
	},
	{ immediate: true },
)

async function fetchInstances() {
	instances.value = (await list().catch(handleError)) ?? []
	if (!instances.value.some((instance) => instance.path === selectedPath.value)) {
		selectedPath.value = instances.value[0]?.path ?? ''
	}
}

async function refreshPlaying() {
	if (!selectedInstance.value) {
		playing.value = false
		return
	}
	const processes = (await get_by_profile_path(selectedInstance.value.path).catch(handleError)) ?? []
	playing.value = processes.length > 0
}

async function refreshDownloads() {
	const bars = (await progress_bars_list().catch(handleError)) ?? {}
	loadingBars.value = Object.values(bars).filter(
		(bar: LoadingBar) => bar?.bar_type?.type !== 'launcher_update',
	) as LoadingBar[]
}

async function refreshPlayerLook() {
	try {
		const [defaultUserId, availableUsers] = await Promise.all([get_default_user(), users()])
		const currentUser = availableUsers.find((user) => user.profile.id === defaultUserId)
		playerName.value = currentUser?.profile?.name ?? 'Minecraft игрок'
	} catch (error) {
		console.warn('Не удалось загрузить имя игрока для главной страницы.', error)
	}

	try {
		const availableSkins = await get_available_skins()
		activeSkin.value = availableSkins.find((skin) => skin.is_equipped) ?? null
		if (activeSkin.value) {
			skinTexture.value = await get_normalized_skin_texture(activeSkin.value)
		}
	} catch (error) {
		console.warn('Не удалось загрузить скин для главной страницы.', error)
	}
}

function selectInstance(instance: GameInstance) {
	selectedPath.value = instance.path
}

function openInstanceFolderMenu(event: MouseEvent, instance: GameInstance) {
	selectInstance(instance)
	instanceFolderMenu.value?.showMenu(event, instance, [
		{ name: 'settings' },
		{ name: 'content' },
		{ name: 'export' },
		{ name: 'repair' },
		{ type: 'divider' },
		{ name: 'root' },
		{ type: 'divider' },
		{ name: 'mods' },
		{ name: 'resource_packs' },
		{ name: 'shader_packs' },
		{ name: 'saves' },
	])
}

async function handleFolderMenu({ item, option }: { item: GameInstance; option: string }) {
	if (option === 'settings' || option === 'export') {
		await router.push({
			path: `/instance/${encodeURIComponent(item.path)}`,
			query: { action: option },
		})
		return
	}
	if (option === 'content') {
		await router.push(`/instance/${encodeURIComponent(item.path)}`)
		return
	}
	if (option === 'repair') {
		await finish_install(item).catch(handleError)
		return
	}
	await openProfileFolder(item.path, option).catch(handleError)
}

async function runPrimaryAction() {
	const instance = selectedInstance.value
	if (!instance || actionPending.value || installingPaths.value.has(instance.path)) return

	if (!installed.value) {
		installingPaths.value = new Set(installingPaths.value).add(instance.path)
		void finish_install(instance)
			.catch((error) => handleSevereError(error, { profilePath: instance.path }))
			.finally(async () => {
				const nextInstallingPaths = new Set(installingPaths.value)
				nextInstallingPaths.delete(instance.path)
				installingPaths.value = nextInstallingPaths
				await Promise.allSettled([fetchInstances(), refreshPlaying(), refreshDownloads()])
			})
		await refreshDownloads()
		return
	}

	actionPending.value = true
	try {
		if (playing.value) {
			await kill(instance.path).catch(handleError)
			playing.value = false
			trackEvent('InstanceStop', {
				loader: instance.loader,
				game_version: instance.game_version,
				source: 'CinematicHome',
			})
		} else {
			await run(instance.path).catch((error) =>
				handleSevereError(error, { profilePath: instance.path }),
			)
			trackEvent('InstancePlay', {
				loader: instance.loader,
				game_version: instance.game_version,
				source: 'CinematicHome',
			})
		}
	} finally {
		actionPending.value = false
		await Promise.allSettled([fetchInstances(), refreshPlaying(), refreshDownloads()])
	}
}

async function openSelectedInstance() {
	if (!selectedInstance.value) return
	await router.push(`/instance/${encodeURIComponent(selectedInstance.value.path)}`)
}

function cardBackgroundPosition(index: number) {
	const positions = ['42% 40%', '62% 54%', '28% 48%', '74% 38%', '50% 70%', '18% 60%']
	return positions[index % positions.length]
}

await Promise.all([fetchInstances(), refreshDownloads()])
await refreshPlaying()
void refreshPlayerLook()

const unlistenProfile = await profile_listener(async () => {
	await fetchInstances()
})
const unlistenProcess = await process_listener(async () => {
	await refreshPlaying()
})
const unlistenLoading = await loading_listener(async () => {
	await refreshDownloads()
})

onUnmounted(() => {
	unlistenProfile()
	unlistenProcess()
	unlistenLoading()
})
</script>

<template>
	<main class="cinematic-home" :style="{ '--cinematic-hero': `url(${heroVibrant})` }">
		<div class="cinematic-scene" aria-hidden="true"></div>
		<div class="cinematic-shade" aria-hidden="true"></div>

		<section v-if="selectedInstance" class="hero-content" aria-labelledby="selected-instance-title">
			<div class="hero-copy">
				<p class="eyebrow">ТЕКУЩАЯ СБОРКА</p>
				<div class="title-row">
					<h1 id="selected-instance-title">{{ selectedInstance.name }}</h1>
					<button v-tooltip="'Открыть настройки сборки'" class="icon-action" @click="openSelectedInstance">
						<EditIcon />
					</button>
				</div>
				<p class="hero-description">{{ selectedDescription }}</p>
				<div class="instance-meta" aria-label="Параметры сборки">
					<span><GameIcon /> {{ selectedInstance.game_version }}</span>
					<span><WrenchIcon /> {{ loaderLabel }}</span>
					<span :class="{ installed }">
						<CheckCircleIcon v-if="installed" />
						<DownloadIcon v-else />
						{{ installed ? 'Готова к запуску' : 'Требуется установка' }}
					</span>
				</div>
				<button
					class="play-button"
					:class="{ danger: playing }"
					:disabled="actionPending || installing"
					@click="runPrimaryAction"
				>
					<span>{{ primaryActionLabel }}</span>
					<SpinnerIcon v-if="actionPending" class="spin" />
					<DownloadIcon v-else-if="installing" />
					<StopCircleIcon v-else-if="playing" />
					<DownloadIcon v-else-if="!installed" />
					<PlayIcon v-else />
				</button>
			</div>

			<aside class="character-hud" aria-label="Персонаж и активность">
				<div class="character-hud-top">
					<div>
						<span>ВАШ ПЕРСОНАЖ</span>
						<strong>{{ playerName }}</strong>
					</div>
					<button v-tooltip="'Настроить скин'" aria-label="Настроить скин" @click="router.push('/skins')">
						<ChangeSkinIcon />
					</button>
				</div>
				<div class="character-stage">
					<SkinPreviewRenderer
						:texture-src="skinTexture"
						:variant="activeSkin?.variant ?? 'CLASSIC'"
						:initial-rotation="Math.PI / 7"
						:scale="1.16"
						:fov="34"
						:hint="null"
					/>
				</div>
				<div class="hud-status">
					<div v-if="activeDownloads.length" class="activity-body">
						<div class="activity-row">
							<strong>{{ activeDownloads[0].title }}</strong>
							<span>{{ activeDownloads[0].progress }}%</span>
						</div>
						<div class="progress-track"><span :style="{ transform: `scaleX(${activeDownloads[0].progress / 100})` }"></span></div>
					</div>
					<template v-else>
						<span class="status-dot" :class="{ active: playing }"></span>
						<div>
							<strong>{{ playing ? `${selectedInstance.name} запущена` : 'Готов к игре' }}</strong>
							<small>{{ playing ? 'Minecraft работает' : `Последний запуск: ${lastPlayedText}` }}</small>
						</div>
					</template>
				</div>
			</aside>
		</section>

		<section v-else class="empty-home">
			<p class="eyebrow">ДОБРО ПОЖАЛОВАТЬ</p>
			<h1>Создайте первую сборку</h1>
			<p>Выберите версию Minecraft, загрузчик и любимые моды — остальное сделает BlockEra Launcher.</p>
			<button class="play-button" @click="router.push('/library')">
				<span>Открыть библиотеку</span>
				<ChevronRightIcon />
			</button>
		</section>

		<section v-if="visibleInstances.length" class="instance-dock" aria-labelledby="instance-dock-title">
			<div class="dock-heading">
				<h2 id="instance-dock-title">МОИ СБОРКИ</h2>
				<button @click="router.push('/library')">Все сборки <ChevronRightIcon /></button>
			</div>
			<div class="instance-carousel">
				<button
					v-for="(instance, index) in visibleInstances"
					:key="instance.path"
					class="instance-card"
					:class="{ selected: instance.path === selectedInstance?.path }"
					:style="{ backgroundPosition: cardBackgroundPosition(index) }"
					@click="selectInstance(instance)"
					@contextmenu.prevent.stop="openInstanceFolderMenu($event, instance)"
				>
					<span class="card-image" :style="{ backgroundImage: `url(${heroVibrant})` }"></span>
					<span class="card-shade"></span>
					<Avatar
						class="instance-avatar"
						size="42px"
						:src="instance.icon_path ? convertFileSrc(instance.icon_path) : null"
						:tint-by="instance.path"
						alt=""
					/>
					<span class="card-copy">
						<strong>{{ instance.name }}</strong>
						<small>{{ instance.game_version }} · {{ instance.loader }}</small>
					</span>
					<CheckCircleIcon v-if="instance.path === selectedInstance?.path" class="selected-check" />
				</button>
			</div>
		</section>

		<ContextMenu ref="instanceFolderMenu" @option-clicked="handleFolderMenu">
			<template #settings><SettingsIcon /> Настройки сборки</template>
			<template #content><PackageIcon /> Установленные моды</template>
			<template #export><PackageIcon /> Экспорт сборки</template>
			<template #repair><WrenchIcon /> Ремонт сборки</template>
			<template #root><FolderOpenIcon /> Открыть папку сборки</template>
			<template #mods><PackageIcon /> Моды</template>
			<template #resource_packs><ImageIcon /> Текстуры</template>
			<template #shader_packs><PaletteIcon /> Шейдеры</template>
			<template #saves><WorldIcon /> Карты</template>
		</ContextMenu>
	</main>
</template>

<style scoped lang="scss">
.cinematic-home {
	position: relative;
	isolation: isolate;
	display: grid;
	grid-template-rows: minmax(0, 1fr) auto;
	gap: 1rem;
	width: 100%;
	height: 100%;
	min-height: 37rem;
	overflow: hidden;
	box-sizing: border-box;
	padding: 0 clamp(2.25rem, 3.1vw, 3.75rem) 1.25rem;
	background: #050912;
	color: #f8fafc;
	--purple: #8b3dff;
	--purple-bright: #a855f7;
	--smooth: cubic-bezier(0.22, 1, 0.36, 1);
}

.cinematic-scene,
.cinematic-shade {
	position: absolute;
	inset: 0;
	pointer-events: none;
}

.cinematic-scene {
	z-index: -3;
	background-image: var(--cinematic-hero);
	background-size: cover;
	background-position: center 48%;
	filter: saturate(0.92) contrast(1.06) brightness(0.78);
	transform: scale(1.02);
	transition: transform 600ms var(--smooth), filter 600ms var(--smooth);
}

.cinematic-shade {
	z-index: -2;
	background:
		linear-gradient(180deg, rgba(4, 7, 13, 0.12) 0%, rgba(4, 8, 15, 0.04) 34%, rgba(4, 8, 15, 0.95) 84%, #050912 100%),
		linear-gradient(90deg, rgba(2, 6, 13, 0.84) 0%, rgba(3, 7, 14, 0.38) 34%, transparent 66%, rgba(2, 6, 13, 0.22) 100%);
}

.hero-content {
	position: relative;
	align-self: end;
	display: flex;
	align-items: flex-end;
	justify-content: space-between;
	gap: 3rem;
}

.hero-copy {
	width: min(39rem, 54vw);
}

.eyebrow {
	margin: 0 0 0.65rem;
	color: #c084fc;
	font-size: 0.72rem;
	font-weight: 750;
	letter-spacing: 0.04em;
}

.title-row {
	display: flex;
	align-items: center;
	gap: 1.1rem;
}

.title-row h1,
.empty-home h1 {
	margin: 0;
	font-size: clamp(2.6rem, 4.3vw, 4.1rem);
	font-weight: 850;
	letter-spacing: -0.045em;
	line-height: 0.98;
	text-shadow: 0 4px 22px rgba(0, 0, 0, 0.35);
}

.icon-action {
	display: grid;
	place-items: center;
	width: 2.8rem;
	height: 2.8rem;
	padding: 0;
	border: 1px solid rgba(196, 181, 253, 0.34);
	border-radius: 0.55rem;
	background: rgba(19, 14, 36, 0.72);
	color: #d8b4fe;
	box-shadow: none;
	cursor: pointer;
	transition: transform 180ms var(--smooth), background-color 180ms ease;
}

.icon-action:hover {
	transform: translateY(-2px);
	background: rgba(91, 33, 182, 0.5);
}

.icon-action svg {
	width: 1.15rem;
}

.hero-description,
.empty-home > p:not(.eyebrow) {
	max-width: 35rem;
	margin: 0.85rem 0 0;
	color: rgba(226, 232, 240, 0.84);
	font-size: 0.98rem;
	line-height: 1.48;
	text-shadow: 0 2px 12px rgba(0, 0, 0, 0.5);
}

.instance-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 0.6rem;
	margin-top: 0.9rem;
}

.instance-meta span {
	display: inline-flex;
	align-items: center;
	gap: 0.48rem;
	min-height: 2.15rem;
	padding: 0 0.8rem;
	border: 1px solid rgba(255, 255, 255, 0.09);
	border-radius: 0.52rem;
	background: rgba(10, 15, 25, 0.72);
	color: rgba(226, 232, 240, 0.86);
	font-size: 0.78rem;
	backdrop-filter: blur(12px);
}

.instance-meta span.installed {
	color: #d8b4fe;
}

.instance-meta svg {
	width: 1rem;
	height: 1rem;
	color: #c084fc;
}

.play-button {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 2.5rem;
	min-width: 15.7rem;
	height: 4rem;
	margin-top: 1rem;
	padding: 0 2rem;
	border: 1px solid rgba(216, 180, 254, 0.48);
	border-radius: 0.55rem;
	background: linear-gradient(135deg, #8b3dff 0%, #5b21b6 100%);
	color: white;
	box-shadow: 0 16px 38px rgba(76, 29, 149, 0.3);
	font: inherit;
	font-size: 1.28rem;
	font-weight: 750;
	cursor: pointer;
	transition: transform 180ms var(--smooth), filter 180ms ease, box-shadow 180ms ease;
}

.play-button:hover:not(:disabled) {
	transform: translateY(-2px);
	filter: brightness(1.08);
	box-shadow: 0 20px 44px rgba(126, 34, 206, 0.38);
}

.play-button:active:not(:disabled) {
	transform: translateY(0) scale(0.985);
}

.play-button:disabled {
	cursor: wait;
	opacity: 0.78;
}

.play-button.danger {
	background: linear-gradient(135deg, #dc2626, #991b1b);
	border-color: rgba(254, 202, 202, 0.45);
}

.play-button svg {
	width: 1.35rem;
	height: 1.35rem;
}

.spin {
	animation: spin 900ms linear infinite;
}

.character-hud {
	position: relative;
	width: min(23rem, 32vw);
	height: min(25rem, 46vh);
	min-height: 20rem;
	overflow: hidden;
	padding: 1.05rem 1.15rem;
	border: 1px solid rgba(255, 255, 255, 0.12);
	border-radius: 0.85rem;
	background:
		radial-gradient(circle at 50% 38%, rgba(147, 51, 234, 0.2), transparent 54%),
		linear-gradient(180deg, rgba(8, 11, 22, 0.52), rgba(6, 10, 18, 0.88));
	box-shadow: 0 18px 45px rgba(0, 0, 0, 0.2);
	backdrop-filter: blur(18px);
}

.character-hud::after {
	position: absolute;
	inset: 0;
	background: linear-gradient(180deg, transparent 52%, rgba(3, 7, 14, 0.76) 100%);
	pointer-events: none;
	content: '';
}

.character-hud-top {
	position: relative;
	z-index: 3;
	display: flex;
	align-items: flex-start;
	justify-content: space-between;
}

.character-hud-top > div {
	display: flex;
	flex-direction: column;
	gap: 0.2rem;
}

.character-hud-top span {
	color: rgba(226, 232, 240, 0.62);
	font-size: 0.64rem;
	font-weight: 700;
	letter-spacing: 0.1em;
}

.character-hud-top strong {
	font-size: 0.92rem;
}

.character-hud-top button {
	display: grid;
	place-items: center;
	width: 2.65rem;
	height: 2.65rem;
	padding: 0;
	border: 1px solid rgba(216, 180, 254, 0.35);
	border-radius: 0.65rem;
	background: rgba(76, 29, 149, 0.45);
	box-shadow: 0 9px 24px rgba(30, 12, 60, 0.3);
	color: #e9d5ff;
	cursor: pointer;
	transition: transform 180ms var(--smooth), background-color 180ms ease;
}

.character-hud-top button:hover {
	transform: translateY(-2px);
	background: rgba(126, 34, 206, 0.68);
}

.character-hud-top svg {
	width: 1.3rem;
	height: 1.3rem;
}

.character-stage {
	position: absolute;
	z-index: 1;
	inset: 1.5rem 0 2.5rem;
}

.hud-status {
	position: absolute;
	z-index: 3;
	right: 1rem;
	bottom: 0.9rem;
	left: 1rem;
	display: flex;
	align-items: center;
	gap: 0.65rem;
	min-height: 2.8rem;
	padding: 0.65rem 0.75rem;
	border: 1px solid rgba(255, 255, 255, 0.1);
	border-radius: 0.65rem;
	background: rgba(5, 8, 15, 0.72);
	backdrop-filter: blur(12px);
}

.hud-status > div:not(.activity-body) {
	display: flex;
	min-width: 0;
	flex-direction: column;
}

.hud-status strong {
	font-size: 0.75rem;
}

.hud-status small {
	margin-top: 0.12rem;
	color: rgba(203, 213, 225, 0.66);
	font-size: 0.62rem;
}

.activity-row {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
}

.status-dot {
	flex: 0 0 auto;
	width: 0.48rem;
	height: 0.48rem;
	border-radius: 999px;
	background: #64748b;
}

.status-dot.active {
	background: #a855f7;
	box-shadow: 0 0 12px #a855f7;
}

.activity-body {
	width: 100%;
	min-width: 0;
}

.activity-body strong {
	display: block;
	overflow: hidden;
	font-size: 0.78rem;
	font-weight: 650;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.activity-row span,
.activity-body small {
	color: rgba(203, 213, 225, 0.7);
	font-size: 0.65rem;
}

.activity-body small {
	display: block;
	margin-top: 0.3rem;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.progress-track {
	height: 0.18rem;
	margin-top: 0.45rem;
	overflow: hidden;
	border-radius: 99px;
	background: rgba(255, 255, 255, 0.12);
}

.progress-track span {
	display: block;
	width: 100%;
	height: 100%;
	border-radius: inherit;
	background: linear-gradient(90deg, #9333ea, #c084fc);
	transform-origin: left center;
	transition: transform 350ms var(--smooth);
}

.instance-dock {
	position: relative;
	z-index: 4;
	padding: 1rem 1.2rem 1.15rem;
	border: 1px solid rgba(255, 255, 255, 0.13);
	border-radius: 0.8rem;
	background: rgba(5, 10, 18, 0.78);
	box-shadow: 0 20px 50px rgba(0, 0, 0, 0.28);
	backdrop-filter: blur(20px);
}

.dock-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 0.7rem;
}

.dock-heading h2 {
	margin: 0;
	color: rgba(226, 232, 240, 0.78);
	font-size: 0.72rem;
	font-weight: 700;
	letter-spacing: 0.04em;
}

.dock-heading button {
	display: inline-flex;
	align-items: center;
	gap: 0.3rem;
	padding: 0;
	border: 0;
	background: transparent;
	box-shadow: none;
	color: rgba(203, 213, 225, 0.72);
	font: inherit;
	font-size: 0.72rem;
	cursor: pointer;
}

.dock-heading button:hover {
	color: #e9d5ff;
}

.dock-heading svg {
	width: 0.9rem;
	height: 0.9rem;
}

.instance-carousel {
	display: grid;
	grid-auto-columns: 18.5rem;
	grid-auto-flow: column;
	gap: 0.8rem;
	overflow-x: auto;
	padding: 0.15rem;
	scrollbar-width: thin;
}

.instance-card {
	position: relative;
	display: flex;
	align-items: flex-end;
	min-width: 0;
	height: 7.8rem;
	overflow: hidden;
	padding: 0.9rem;
	border: 1px solid rgba(255, 255, 255, 0.14);
	border-radius: 0.68rem;
	background: #111827;
	box-shadow: none;
	color: white;
	text-align: left;
	cursor: pointer;
	transition: transform 180ms var(--smooth), border-color 180ms ease, box-shadow 180ms ease;
}

.instance-card:hover {
	transform: translateY(-2px);
	border-color: rgba(216, 180, 254, 0.42);
}

.instance-card.selected {
	border-color: #a855f7;
	box-shadow: 0 0 0 1px rgba(168, 85, 247, 0.45), 0 10px 28px rgba(88, 28, 135, 0.25);
}

.card-image,
.card-shade {
	position: absolute;
	inset: 0;
}

.card-image {
	background-position: inherit;
	background-size: 190%;
	filter: saturate(0.86) brightness(0.72);
	transition: transform 350ms var(--smooth), filter 350ms var(--smooth);
}

.instance-card:hover .card-image,
.instance-card.selected .card-image {
	transform: scale(1.045);
	filter: saturate(1) brightness(0.82);
}

.card-shade {
	background: linear-gradient(180deg, transparent 18%, rgba(3, 7, 13, 0.22) 46%, rgba(3, 7, 13, 0.96) 100%);
}

.instance-avatar {
	position: relative;
	z-index: 1;
	flex: 0 0 auto;
	margin-right: 0.65rem;
}

.card-copy {
	position: relative;
	z-index: 1;
	display: flex;
	min-width: 0;
	flex-direction: column;
}

.card-copy strong,
.card-copy small {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.card-copy strong {
	font-size: 0.94rem;
	font-weight: 700;
}

.card-copy small {
	margin-top: 0.18rem;
	color: rgba(203, 213, 225, 0.76);
	font-size: 0.66rem;
	text-transform: capitalize;
}

.selected-check {
	position: absolute;
	z-index: 2;
	top: 0.55rem;
	right: 0.55rem;
	width: 1.45rem;
	height: 1.45rem;
	color: #d8b4fe;
	filter: drop-shadow(0 3px 8px rgba(0, 0, 0, 0.5));
}

.empty-home {
	position: relative;
	align-self: center;
	width: min(38rem, 72vw);
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

@media (max-width: 960px) {
	.hero-content {
		padding-bottom: 1rem;
	}

	.character-hud {
		display: none;
	}

	.hero-copy {
		width: min(42rem, 82vw);
	}

	.instance-card {
		height: 7rem;
	}
}

@media (max-height: 760px) {
	.hero-content {
		padding-bottom: 0;
	}

	.title-row h1 {
		font-size: 2.7rem;
	}

	.hero-description {
		display: none;
	}

	.instance-meta {
		margin-top: 0.65rem;
	}

	.play-button {
		height: 3.35rem;
		margin-top: 0.75rem;
	}

	.instance-card {
		height: 6.5rem;
	}
}

@media (prefers-reduced-motion: reduce) {
	*,
	*::before,
	*::after {
		animation-duration: 1ms !important;
		transition-duration: 1ms !important;
	}
}
</style>
