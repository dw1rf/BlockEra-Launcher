<script setup lang="ts">
import { DownloadIcon, SearchIcon, UpdatedIcon, XIcon } from '@modrinth/assets'
import { invoke } from '@tauri-apps/api/core'
import { openUrl } from '@tauri-apps/plugin-opener'
import { computed, onMounted, onUnmounted, ref } from 'vue'

import type { GameInstance } from '@/helpers/types'

const props = defineProps<{ instance: GameInstance; playing: boolean }>()
type Mod = {
	id: string
	name: string
	summary?: string
	authors?: string[]
	source: string
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
const removeTarget = ref<Mod | null>(null)
const filtered = computed(() =>
	(tab.value === 'installed' ? state.value.installed : projects.value).filter(
		(mod) =>
			(filter.value === 'all' || mod.kind === filter.value) &&
			`${mod.name} ${mod.summary ?? ''} ${mod.authors?.join(' ') ?? ''}`
				.toLowerCase()
				.includes(search.value.toLowerCase()),
	),
)
const nativeMods = computed(() =>
	state.value.installed.filter((mod) => mod.kind === 'native-mod' && mod.enabled),
)
const disabled = computed(() => Boolean(busy.value || state.value.busy || props.playing))
let timer: ReturnType<typeof setInterval> | undefined
let stateTimer: ReturnType<typeof setInterval> | undefined

function errorMessage(err: unknown) {
	if (err && typeof err === 'object' && 'message' in err) return String(err.message)
	return String(err)
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
		})
	} catch (err) {
		error.value = errorMessage(err)
	} finally {
		if (timer) clearInterval(timer)
		busy.value = ''
		trusted.value = false
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
function openSource(url: string) {
	if (url.startsWith('https://')) void openUrl(url)
}
function openBuildConfirm() {
	trusted.value = false
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
			<button :disabled="loading" @click="refresh"><UpdatedIcon /> Обновить</button>
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
				@click="openBuildConfirm"
			>
				Собрать клиент
			</button>
		</div>
		<p v-if="playing" class="note">Остановите игру, чтобы изменить моды или собрать клиент.</p>
		<p v-if="busy" class="note" role="status">
			{{ busy }} Прогресс — в журнале ниже. Первый нативный билд может занять 20–30 минут.
		</p>
		<p v-if="error" class="error" role="alert">{{ error }}</p>
		<div class="toolbar">
			<nav aria-label="Моды Horizon">
				<button :class="{ active: tab === 'catalog' }" @click="tab = 'catalog'">Каталог</button
				><button :class="{ active: tab === 'installed' }" @click="tab = 'installed'">
					Установленные · {{ state.installed.length }}
				</button>
			</nav>
			<label class="search"
				><SearchIcon /><input
					v-model="search"
					aria-label="Поиск модов Horizon"
					placeholder="Zoom, Minimap, FPS…"
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
				<div class="mod-title">
					<h3>{{ mod.name }}</h3>
					<span>{{ mod.kind === 'native-mod' ? 'Rust · нативный' : 'Контентный' }}</span>
				</div>
				<p v-if="mod.summary">{{ mod.summary }}</p>
				<small>{{ mod.authors?.join(', ') }} · {{ mod.version }}</small>
				<div class="mod-actions">
					<button @click="openSource(mod.source)">Исходный код</button>
					<template v-if="installed(mod.id)">
						<button
							v-if="tab === 'catalog' && installed(mod.id)?.version !== mod.version"
							:disabled="disabled"
							@click="act('update', mod)"
						>
							<UpdatedIcon /> Обновить
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
						<DownloadIcon /> Установить
					</button>
				</div>
			</article>
		</div>
		<div class="log-panel">
			<button @click="toggleLog">
				{{ showLog ? 'Скрыть журнал Horizon' : 'Показать журнал Horizon' }}
			</button>
			<pre v-if="showLog" aria-live="polite">{{ log }}</pre>
		</div>
		<div
			v-if="confirmBuild"
			class="confirm-panel"
			role="dialog"
			aria-modal="true"
			aria-labelledby="native-confirm-title"
		>
			<h3 id="native-confirm-title">Сборка клиента с нативными модами</h3>
			<p>
				Нативные моды — Rust-код с доступом к вашему компьютеру. Проверьте их исходники. Horizon
				загрузит отдельный Rust и инструменты сборки; первый билд занимает 20–30 минут и несколько
				ГБ на диске.
			</p>
			<ul>
				<li v-for="mod in nativeMods" :key="mod.id">
					{{ mod.name }} {{ mod.version }}
					<button @click="openSource(mod.source)">Исходный код</button>
				</li>
			</ul>
			<label><input v-model="trusted" type="checkbox" /> Я доверяю коду перечисленных модов</label>
			<div class="mod-actions">
				<button @click="confirmBuild = false">Отмена</button
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
			<h3 id="remove-mod-title">Удалить {{ removeTarget.name }}?</h3>
			<p>Миры и настройки сборки сохранятся. Для нативных модов потребуется пересборка клиента.</p>
			<div class="mod-actions">
				<button @click="removeTarget = null">Отмена</button
				><button :disabled="disabled" @click="act('remove', removeTarget ?? undefined)">
					Удалить мод
				</button>
			</div>
		</div>
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
.mod-actions,
.mod-title {
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
.log-panel pre {
	max-height: 20rem;
	overflow: auto;
	background: #070b13;
	color: #b9c9dc;
	padding: 1rem;
	border-radius: 0.7rem;
	white-space: pre-wrap;
	font-size: 0.72rem;
}
.confirm-panel {
	position: fixed;
	z-index: 1100;
	left: 50%;
	top: 50%;
	transform: translate(-50%, -50%);
	width: min(600px, calc(100vw - 40px));
	max-height: calc(100vh - 80px);
	overflow: auto;
	padding: 1.5rem;
	border-radius: 1rem;
	background: #151321;
	border: 1px solid #bd82f4;
	box-shadow: 0 0 0 100vmax #030510c9;
	display: grid;
	gap: 1rem;
}
.confirm-panel label {
	display: flex;
	align-items: center;
	gap: 0.6rem;
}
</style>
