<script setup lang="ts">
import { DownloadIcon, PackageIcon, SearchIcon, UpdatedIcon, XIcon } from '@modrinth/assets'
import { ProjectPageDescription } from '@modrinth/ui'
import { invoke } from '@tauri-apps/api/core'
import { openUrl } from '@tauri-apps/plugin-opener'
import { computed, nextTick, onMounted, onUnmounted, ref, useTemplateRef } from 'vue'
import { useI18n } from 'vue-i18n'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import russianDetails from '@/helpers/horizon-details-ru.json'
import russianSummaries from '@/helpers/horizon-mods-ru.json'
import type { GameInstance } from '@/helpers/types'

const props = defineProps<{ instance: GameInstance; playing: boolean }>()
const { locale, t } = useI18n()
type Mod = {
	id: string
	name: string
	summary?: string
	authors?: string[]
	source: string
	icon?: string | null
	description?: string | null
	license?: string | null
	kind: string
	version: string
	enabled?: boolean
	signed?: boolean
}
type State = {
	installed: Mod[]
	native_enabled: number
	needs_build: boolean
	built_version: string | null
	busy: boolean
}
const state = ref<State>({
	installed: [],
	native_enabled: 0,
	needs_build: false,
	built_version: null,
	busy: false,
})
const projects = ref<Mod[]>([])
const failedIcons = ref(new Set<string>())
const catalogById = computed(() => new Map(projects.value.map((mod) => [mod.id, mod])))
const detailsModal = useTemplateRef('detailsModal')
const detailTarget = ref<Mod | null>(null)
const showOriginal = ref(false)
const details = computed(() => {
	const mod = detailTarget.value
	return mod ? { ...catalogById.value.get(mod.id), ...mod } : null
})
const russian = computed(() => locale.value.startsWith('ru'))
const russianDetail = computed(
	() =>
		details.value &&
		((russianDetails as Record<string, string>)[details.value.id] ||
			(russianSummaries as Record<string, string>)[details.value.id]),
)
const detailText = computed(() =>
	russian.value && !showOriginal.value && russianDetail.value
		? russianDetail.value
		: details.value?.description || details.value?.summary,
)
const isSummaryTranslation = computed(
	() =>
		!!details.value &&
		russian.value &&
		!showOriginal.value &&
		!!russianDetail.value &&
		!(details.value.id in russianDetails),
)
const search = ref('')
const filter = ref('all')
const tab = ref('catalog')
const loading = ref(true)
const busy = ref('')
const error = ref('')
const log = ref('')
const showLog = ref(false)
const confirmBuild = ref(false)
const trusted = ref(false)
const retryMismatch = ref(false)
const versionMismatch = ref<{ base: string; mods: { id: string; revision: string }[] } | null>(null)
const removeTarget = ref<Mod | null>(null)
const filtered = computed(() =>
	(tab.value === 'installed' ? state.value.installed : projects.value).filter(
		(mod) =>
			(filter.value === 'all' || mod.kind === filter.value) &&
			`${mod.name} ${mod.summary ?? ''} ${summaryFor(mod) ?? ''} ${mod.authors?.join(' ') ?? ''}`
				.toLowerCase()
				.includes(search.value.toLowerCase()),
	),
)
const nativeMods = computed(() =>
	state.value.installed.filter((mod) => mod.kind === 'native-mod' && mod.enabled),
)
const mismatchedMods = computed(
	() =>
		versionMismatch.value?.mods.filter(({ id }) => nativeMods.value.some((mod) => mod.id === id)) ??
		[],
)
const disabled = computed(() => Boolean(busy.value || state.value.busy || props.playing))
let timer: ReturnType<typeof setInterval> | undefined
let stateTimer: ReturnType<typeof setInterval> | undefined

function errorMessage(err: unknown) {
	const message =
		err && typeof err === 'object' && 'message' in err ? String(err.message) : String(err)
	return message.replace(/^Error launching Minecraft:\s*/, '')
}

function summaryFor(mod: Mod) {
	return (
		(russian.value ? (russianSummaries as Record<string, string>)[mod.id] : undefined) ||
		mod.summary ||
		catalogById.value.get(mod.id)?.summary
	)
}

function readVersionMismatch(message: string) {
	const match = message.match(
		/Made for other Horizon versions:\s*([^;]+);\s*the build uses\s+([a-f0-9]+)/i,
	)
	if (!match) return null
	const mods = [...match[1].matchAll(/([a-z0-9_]+)\s*(?:→|->)\s*([a-f0-9]+)/gi)].map(
		([, id, revision]) => ({ id, revision }),
	)
	return mods.length ? { base: match[2], mods } : null
}

function buildErrorMessage(message: string) {
	const dependency = message.match(/failed to run custom build command for `([^ `]+) /i)?.[1]
	if (dependency) {
		return t('blockera.horizon.build-dependency-failed', { dependency })
	}
	const missingTargets = new Set(
		[...message.matchAll(/^([a-z0-9_]+) \([^\r\n]+\): E_TARGET_NOT_FOUND:/gim)].map(
			([, id]) => installed(id)?.name ?? id,
		),
	)
	if (missingTargets.size) {
		return t('blockera.horizon.build-missing-target', { mods: [...missingTargets].join(', ') })
	}
	return t('blockera.horizon.build-failed')
}

function openInstalled() {
	tab.value = 'installed'
	filter.value = 'all'
	search.value = ''
}

async function refresh() {
	loading.value = true
	error.value = ''
	try {
		const [catalog, installed] = await Promise.all([
			invoke<{ projects: Mod[] }>('plugin:profile|profile_horizon_catalog', {
				path: props.instance.path,
			}),
			invoke<State>('plugin:profile|profile_horizon_mods', { path: props.instance.path }),
		])
		projects.value = catalog.projects
		failedIcons.value.clear()
		state.value = installed
	} catch (err) {
		error.value = errorMessage(err)
	} finally {
		loading.value = false
	}
}

async function readLog() {
	try {
		log.value = await invoke<string>('plugin:profile|profile_horizon_mod_logs', {
			path: props.instance.path,
		})
	} catch (err) {
		log.value = errorMessage(err)
	}
}

async function act(action: string, mod?: Mod) {
	if (disabled.value) return
	if (action === 'build' && !trusted.value) return
	const allowVersionMismatch = action === 'build' && retryMismatch.value
	if (action === 'build') versionMismatch.value = null
	busy.value =
		action === 'build' ? 'Собираем клиент с модами…' : `${mod?.name ?? 'Моды'}: выполняем…`
	error.value = ''
	confirmBuild.value = false
	removeTarget.value = null
	showLog.value = true
	log.value = 'Подготовка инструментов Horizon…'
	timer = setInterval(readLog, 2500)
	try {
		state.value = await invoke<State>('plugin:profile|profile_horizon_mod_action', {
			path: props.instance.path,
			action,
			id: mod?.id ?? null,
			trustNativeCode: action === 'build' && trusted.value,
			allowVersionMismatch,
		})
	} catch (err) {
		const message = errorMessage(err)
		if (action === 'build') versionMismatch.value = readVersionMismatch(message)
		error.value =
			versionMismatch.value && action === 'build'
				? ''
				: action === 'build'
					? buildErrorMessage(message)
					: message
	} finally {
		if (timer) clearInterval(timer)
		busy.value = ''
		trusted.value = false
		retryMismatch.value = false
		await readLog()
		try {
			state.value = await invoke<State>('plugin:profile|profile_horizon_mods', {
				path: props.instance.path,
			})
		} catch {
			/* Keep the original error. */
		}
	}
}

function installed(id: string) {
	return state.value.installed.find((mod) => mod.id === id)
}
function iconFor(mod: Mod) {
	const icon = catalogById.value.get(mod.id)?.icon
	return icon && !failedIcons.value.has(icon) ? icon : undefined
}
async function showDetails(mod: Mod, event: MouseEvent) {
	showOriginal.value = false
	detailTarget.value = mod
	await nextTick()
	detailsModal.value?.show(event)
}
function openSource(url: string) {
	if (url.startsWith('https://')) void openUrl(url)
}
function openBuildConfirm(retry = false) {
	trusted.value = false
	retryMismatch.value = retry
	confirmBuild.value = true
}
function toggleLog() {
	showLog.value = !showLog.value
	if (showLog.value) void readLog()
}
onMounted(() => {
	void refresh()
	stateTimer = setInterval(async () => {
		if (!state.value.busy || busy.value) return
		try {
			state.value = await invoke<State>('plugin:profile|profile_horizon_mods', {
				path: props.instance.path,
			})
			if (showLog.value) await readLog()
		} catch {
			/* A manual refresh can retry. */
		}
	}, 2500)
})
onUnmounted(() => {
	if (timer) clearInterval(timer)
	if (stateTimer) clearInterval(stateTimer)
})
</script>

<template>
	<section class="horizon-content">
		<header class="horizon-heading">
			<div>
				<span>ЭКОСИСТЕМА HORIZON</span>
				<h2>Моды и плагины</h2>
				<p>
					Каталог Horizon для Minecraft {{ instance.game_version }}. Все файлы проверяются
					официальными инструментами Horizon.
				</p>
			</div>
			<button :disabled="loading" @click="refresh">
				<UpdatedIcon /> {{ $t('blockera.ui.fb91e24fa52d') }}
			</button>
		</header>
		<div class="native-panel">
			<div>
				<strong>Клиент с нативными модами</strong>
				<p>
					{{
						state.native_enabled
							? `${state.native_enabled} включено · ${state.needs_build ? 'нужна сборка клиента' : 'клиент собран'}`
							: 'Установите и включите нативные моды, затем соберите клиент.'
					}}
				</p>
			</div>
			<button
				class="primary"
				:disabled="disabled || !state.native_enabled"
				@click="openBuildConfirm()"
			>
				Собрать клиент
			</button>
		</div>
		<p v-if="playing" class="note">Остановите игру, чтобы изменить моды или собрать клиент.</p>
		<p v-if="busy" class="note" role="status">
			{{ busy }} Прогресс — в журнале над вкладками. Первый нативный билд может занять 20–30 минут.
		</p>
		<p v-if="error" class="error" role="alert">{{ error }}</p>
		<div v-if="versionMismatch && mismatchedMods.length" class="compatibility-note" role="alert">
			<strong>{{ t('blockera.horizon.version-mismatch') }}</strong>
			<p>{{ t('blockera.horizon.version-mismatch-body', { revision: versionMismatch.base }) }}</p>
			<ul>
				<li v-for="mod in mismatchedMods" :key="mod.id">
					{{ installed(mod.id)?.name || mod.id }} · <code>{{ mod.revision }}</code>
				</li>
			</ul>
			<div class="mod-actions">
				<button type="button" @click="openInstalled">
					{{ t('blockera.horizon.open-installed') }}
				</button>
				<button type="button" :disabled="disabled" @click="openBuildConfirm(true)">
					{{ t('blockera.horizon.try-build') }}
				</button>
			</div>
		</div>
		<div class="toolbar">
			<nav aria-label="Моды Horizon">
				<button :class="{ active: tab === 'catalog' }" @click="tab = 'catalog'">
					{{ $t('blockera.ui.4a88d27bbae1') }}</button
				><button :class="{ active: tab === 'installed' }" @click="tab = 'installed'">
					Установленные · {{ state.installed.length }}
				</button>
			</nav>
			<label class="search"
				><SearchIcon /><input
					v-model="search"
					aria-label="Поиск модов Horizon"
					:placeholder="$t('blockera.ui.44db19896dea')"
			/></label>
			<select v-model="filter" aria-label="Тип модов">
				<option value="all">Все моды</option>
				<option value="native-mod">Нативные · Rust</option>
				<option value="mod">Контентные</option>
			</select>
		</div>
		<p v-if="loading" role="status">Загружаем каталог Horizon…</p>
		<p v-else-if="!filtered.length" class="empty">
			{{
				tab === 'installed'
					? 'Пока нет установленных модов. Выберите их в каталоге.'
					: 'Моды не найдены. Измените запрос или обновите каталог.'
			}}
		</p>
		<div class="mod-grid">
			<article v-for="mod in filtered" :key="mod.id" class="mod-card">
				<button type="button" class="mod-title" @click="showDetails(mod, $event)">
					<div class="mod-icon" aria-hidden="true">
						<img
							v-if="iconFor(mod)"
							:src="iconFor(mod)"
							alt=""
							width="48"
							height="48"
							loading="lazy"
							decoding="async"
							referrerpolicy="no-referrer"
							@error="failedIcons.add(($event.currentTarget as HTMLImageElement).src)"
						/>
						<PackageIcon v-else />
					</div>
					<div>
						<h3>{{ mod.name }}</h3>
						<span v-if="mod.kind === 'mod'">Контентный</span>
					</div>
					<span class="details-arrow" aria-hidden="true">↗</span>
				</button>
				<p v-if="summaryFor(mod)">{{ summaryFor(mod) }}</p>
				<small>{{ mod.authors?.join(', ') }} · {{ mod.version }}</small>
				<div class="mod-actions">
					<button @click="openSource(mod.source)">Исходный код</button>
					<template v-if="installed(mod.id)">
						<button
							v-if="tab === 'catalog' && installed(mod.id)?.version !== mod.version"
							:disabled="disabled"
							@click="act('update', mod)"
						>
							<UpdatedIcon /> {{ $t('blockera.ui.fb91e24fa52d') }}
						</button>
						<button
							:disabled="disabled"
							:class="{ active: installed(mod.id)?.enabled }"
							@click="act(installed(mod.id)?.enabled ? 'disable' : 'enable', mod)"
						>
							{{ installed(mod.id)?.enabled ? 'Включён' : 'Выключен' }}
						</button>
						<button
							:disabled="disabled"
							:aria-label="`Удалить ${mod.name}`"
							@click="removeTarget = mod"
						>
							<XIcon />
						</button>
					</template>
					<button v-else class="primary" :disabled="disabled" @click="act('install', mod)">
						<DownloadIcon /> {{ $t('blockera.ui.fd6c3ebf7bef') }}
					</button>
				</div>
			</article>
		</div>
		<ModalWrapper
			ref="detailsModal"
			:header="details?.name"
			width="min(44rem, 90vw)"
			:on-hide="() => (detailTarget = null)"
		>
			<div v-if="details" class="mod-details">
				<div class="details-meta">
					<img
						v-if="iconFor(details)"
						:src="iconFor(details)"
						alt=""
						width="56"
						height="56"
						referrerpolicy="no-referrer"
						@error="failedIcons.add(($event.currentTarget as HTMLImageElement).src)"
					/>
					<div>
						<strong>{{ details.authors?.join(', ') || 'Horizon' }}</strong>
						<p>
							{{ details.version
							}}<template v-if="details.license"> · {{ details.license }}</template>
						</p>
					</div>
					<button v-if="details.source" class="details-source" @click="openSource(details.source)">
						Исходный код
					</button>
				</div>
				<div class="details-heading">
					<h3>{{ $t('blockera.horizon.what-it-adds') }}</h3>
					<button
						v-if="russian && russianDetail && (details.description || details.summary)"
						type="button"
						@click="showOriginal = !showOriginal"
					>
						{{
							t(
								showOriginal
									? 'blockera.horizon.russian-description'
									: 'blockera.horizon.original-description',
							)
						}}
					</button>
				</div>
				<small v-if="isSummaryTranslation">{{ t('blockera.horizon.short-translation') }}</small>
				<ProjectPageDescription v-if="detailText" :description="detailText" />
				<p v-else>{{ $t('blockera.horizon.no-description') }}</p>
			</div>
		</ModalWrapper>
		<Teleport defer to="#horizon-log-slot">
			<div class="log-panel">
				<button @click="toggleLog">
					{{ showLog ? 'Скрыть журнал Horizon' : 'Показать журнал Horizon' }}
				</button>
				<pre v-if="showLog" aria-live="polite">{{ log }}</pre>
			</div>
		</Teleport>
		<Teleport to="body">
			<div v-if="confirmBuild || removeTarget" class="confirm-overlay">
				<div
					v-if="confirmBuild"
					class="confirm-panel"
					role="dialog"
					aria-modal="true"
					aria-labelledby="native-confirm-title"
				>
					<h3 id="native-confirm-title">Сборка клиента с нативными модами</h3>
					<p v-if="retryMismatch" class="compatibility-note">
						{{ t('blockera.horizon.retry-note') }}
					</p>
					<p>
						Нативные моды — Rust-код с доступом к вашему компьютеру. Проверьте их исходники. Horizon
						загрузит отдельный Rust и инструменты сборки; первый билд занимает 20–30 минут и
						несколько ГБ на диске.
					</p>
					<ul class="native-confirm-mods">
						<li v-for="mod in nativeMods" :key="mod.id">
							{{ mod.name }} {{ mod.version }}
							<button @click="openSource(mod.source)">Исходный код</button>
						</li>
					</ul>
					<label
						><input v-model="trusted" type="checkbox" /> Я доверяю коду перечисленных модов</label
					>
					<div class="mod-actions">
						<button @click="confirmBuild = false">{{ $t('blockera.ui.77dfd2135f4d') }}</button
						><button class="primary" :disabled="!trusted || disabled" @click="act('build')">
							Доверяю — собрать клиент
						</button>
					</div>
				</div>
				<div
					v-if="removeTarget"
					class="confirm-panel"
					role="dialog"
					aria-modal="true"
					aria-labelledby="remove-mod-title"
				>
					<h3 id="remove-mod-title">
						{{ $t('blockera.ui.e963907dac5c') }} {{ removeTarget.name }}?
					</h3>
					<p>
						Миры и настройки сборки сохранятся. Для нативных модов потребуется пересборка клиента.
					</p>
					<div class="mod-actions">
						<button @click="removeTarget = null">{{ $t('blockera.ui.77dfd2135f4d') }}</button
						><button :disabled="disabled" @click="act('remove', removeTarget ?? undefined)">
							Удалить мод
						</button>
					</div>
				</div>
			</div>
		</Teleport>
	</section>
</template>

<style scoped>
.horizon-content {
	padding: 1.4rem;
	display: grid;
	gap: 1.1rem;
	position: relative;
}
.horizon-heading,
.native-panel,
.toolbar,
.mod-actions {
	display: flex;
	align-items: center;
	gap: 0.65rem;
	justify-content: space-between;
	flex-wrap: wrap;
}
.horizon-heading span {
	color: #c289ff;
	font-size: 0.65rem;
	font-weight: 800;
	letter-spacing: 0.1em;
}
h2,
h3,
p {
	margin: 0;
}
h2 {
	margin: 0.4rem 0;
	font-size: 1.5rem;
}
h3 {
	font-size: 1rem;
}
p,
small {
	color: var(--color-secondary);
	line-height: 1.5;
}
p {
	font-size: 0.85rem;
}
button,
select {
	border: 1px solid #ffffff16;
	background: #ffffff09;
	color: var(--color-contrast);
	border-radius: 0.65rem;
	padding: 0.6rem 0.8rem;
	cursor: pointer;
	display: inline-flex;
	align-items: center;
	gap: 0.4rem;
}
button:disabled {
	opacity: 0.45;
	cursor: default;
}
button:not(:disabled):hover {
	background: #ab67ef25;
}
button:focus-visible,
input:focus-visible,
select:focus-visible {
	outline: 2px solid #c289ff;
	outline-offset: 3px;
}
svg {
	width: 1rem;
	height: 1rem;
}
.primary,
.active {
	background: #a563df30;
	border-color: #b371ec80;
}
.native-panel {
	background: linear-gradient(110deg, #9358cf20, #111520);
	border: 1px solid #b371ec40;
	border-radius: 0.9rem;
	padding: 1rem;
}
nav {
	display: flex;
	gap: 0.4rem;
}
.search {
	display: flex;
	gap: 0.5rem;
	align-items: center;
	flex: 1;
	min-width: 10rem;
	padding: 0.4rem 0.7rem;
	background: #080c15;
	border: 1px solid #ffffff16;
	border-radius: 0.65rem;
}
.search input {
	width: 100%;
	border: 0;
	background: transparent;
	padding: 0.3rem;
	color: inherit;
}
.mod-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(min(100%, 300px), 1fr));
	gap: 0.8rem;
}
.mod-card {
	border: 1px solid #ffffff12;
	background: #111722;
	padding: 1rem;
	border-radius: 0.85rem;
	display: flex;
	flex-direction: column;
	gap: 0.7rem;
}
.mod-card p {
	flex: 1;
}
.mod-title {
	display: flex;
	align-items: center;
	gap: 0.75rem;
	overflow-wrap: anywhere;
	padding: 0;
	border: 0;
	background: transparent;
	color: inherit;
	text-align: left;
	cursor: pointer;
}
.mod-title:hover h3,
.mod-title:focus-visible h3 {
	color: #d9b7ff;
}
.details-arrow {
	margin-left: auto;
}
.mod-details {
	display: grid;
	gap: 1rem;
	min-width: 0;
	overflow-wrap: anywhere;
}
.details-meta {
	display: flex;
	align-items: center;
	gap: 0.8rem;
}
.details-meta img {
	object-fit: contain;
	border-radius: 0.65rem;
}
.details-source {
	margin-left: auto;
}
.details-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 0.65rem;
}
.compatibility-note {
	display: grid;
	gap: 0.5rem;
	padding: 0.85rem;
	border: 1px solid #d9a45d66;
	border-radius: 0.75rem;
	background: #35271b;
	color: #f8dfbe;
}
.compatibility-note ul {
	margin: 0;
	padding-left: 1.3rem;
}
.mod-icon {
	display: grid;
	place-items: center;
	flex: 0 0 48px;
	width: 48px;
	height: 48px;
	border-radius: 0.65rem;
	overflow: hidden;
	background: #c289ff12;
	color: #cba3f7;
}
.mod-icon img {
	width: 100%;
	height: 100%;
	object-fit: contain;
}
.mod-icon svg {
	width: 24px;
	height: 24px;
}
.mod-title span {
	color: #cba3f7;
	font-size: 0.65rem;
}
.mod-actions {
	justify-content: flex-start;
}
.mod-actions button {
	font-size: 0.75rem;
}
.note,
.empty {
	padding: 0.75rem;
	background: #c289ff10;
	border-radius: 0.65rem;
}
.error {
	color: #ff9bb1;
	white-space: pre-wrap;
	overflow-wrap: anywhere;
}
.log-panel {
	padding: 0.75rem 0;
}
.log-panel pre {
	margin: 0.6rem 0 0;
	max-height: 20rem;
	overflow: auto;
	background: #070b13;
	color: #b9c9dc;
	padding: 1rem;
	border-radius: 0.7rem;
	white-space: pre-wrap;
	font-size: 0.72rem;
}
.confirm-overlay {
	position: fixed;
	z-index: 1100;
	inset: 0;
	display: grid;
	place-items: center;
	padding: 20px;
	background: #030510c9;
	overscroll-behavior: contain;
}
.confirm-panel {
	box-sizing: border-box;
	width: min(600px, 100%);
	min-width: 0;
	max-height: calc(100dvh - 40px);
	overflow: auto;
	overscroll-behavior: contain;
	padding: 1.5rem;
	border-radius: 1rem;
	background: #151321;
	border: 1px solid #bd82f4;
	display: grid;
	gap: 1rem;
	overflow-wrap: anywhere;
}
.native-confirm-mods {
	min-height: 0;
	max-height: min(35dvh, 18rem);
	overflow-y: auto;
	overscroll-behavior: contain;
	margin: 0;
	padding-left: 1.3rem;
}
.confirm-panel label {
	display: flex;
	align-items: center;
	gap: 0.6rem;
}
</style>
