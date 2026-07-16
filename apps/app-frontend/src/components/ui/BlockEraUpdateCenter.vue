<template>
	<div ref="root" class="update-center-root">
		<button class="update-center-trigger" type="button" :aria-expanded="open" @click="open = !open">
			<DownloadIcon />
			<span v-if="totalUpdates > 0">{{ totalUpdates }}</span>
		</button>
		<transition name="update-popover">
			<section v-if="open" class="update-center-popover">
				<header>
					<div><span>ЦЕНТР ОБНОВЛЕНИЙ</span><h3>BlockEra</h3></div>
					<button type="button" aria-label="Закрыть" @click="open = false"><XIcon /></button>
				</header>

				<nav class="update-tabs" aria-label="Разделы центра обновлений">
					<button :class="{ active: activeTab === 'updates' }" @click="activeTab = 'updates'">Обновления</button>
					<button :class="{ active: activeTab === 'notes' }" @click="activeTab = 'notes'">Что нового</button>
				</nav>

				<div v-if="activeTab === 'updates'" class="update-tab-panel">
					<div class="launcher-update" :class="{ available: updateState }">
						<div class="launcher-update-copy">
							<span>ЛАУНЧЕР</span>
							<strong v-if="updateState">Доступна версия {{ availableUpdate?.version }}</strong>
							<strong v-else-if="checkingState">Проверяем обновление…</strong>
							<strong v-else>Установлена актуальная версия {{ currentVersion }}</strong>
							<small v-if="availableUpdate?.body">{{ availableUpdate.body }}</small>
						</div>
						<button
							v-if="updateState"
							type="button"
							class="install-launcher-update"
							:disabled="installState"
							@click="installUpdate"
						>
							<RefreshCwIcon v-if="installState" class="spin" />
							<DownloadIcon v-else />
							{{ installState ? 'Установка…' : 'Обновить' }}
						</button>
					</div>
					<p v-if="updateError" class="update-error">Проверка лаунчера: {{ updateError }}</p>
					<div class="update-summary">
						<strong>{{ loading ? 'Проверяем сборки…' : instanceUpdates ? `Обновлений сборок: ${instanceUpdates}` : 'Сборки актуальны' }}</strong>
						<span>Обновления устанавливаются только по вашей команде.</span>
					</div>
					<div class="update-instance-list">
						<div v-for="item in items" :key="item.path" class="update-instance-row">
							<div class="instance-copy"><strong>{{ item.name }}</strong><span>{{ item.gameVersion }} · {{ item.loader }}</span></div>
							<span class="update-count" :class="{ empty: item.updates === 0 }">{{ item.updates || 'OK' }}</span>
							<button v-if="item.updates" type="button" :disabled="item.updating" @click="updateInstance(item)">
								{{ item.updating ? 'Обновляем…' : 'Обновить' }}
							</button>
						</div>
						<div v-if="!loading && items.length === 0" class="update-empty">Сборки пока не созданы.</div>
					</div>
					<footer>
						<button type="button" :disabled="loading || checkingState" @click="scan"><RefreshCwIcon /> Проверить снова</button>
						<button type="button" class="update-all" :disabled="instanceUpdates === 0 || updatingAll" @click="updateAllInstances">
							{{ updatingAll ? 'Обновляем…' : 'Обновить всё' }}
						</button>
					</footer>
					<p v-if="lastError" class="update-error">{{ lastError }}</p>
				</div>

				<div v-else class="update-tab-panel release-notes-panel">
					<article v-for="note in releaseNotes" :key="note.version" class="release-note">
						<header class="release-note-heading">
							<div><span>ВЕРСИЯ</span><h4>{{ note.version }}</h4></div>
							<time>{{ note.date }}</time>
						</header>
						<section v-if="note.highlights.length"><strong>Новое</strong><ul><li v-for="item in note.highlights" :key="item">{{ item }}</li></ul></section>
						<section v-if="note.fixes.length"><strong>Исправления</strong><ul><li v-for="item in note.fixes" :key="item">{{ item }}</li></ul></section>
						<section v-if="note.improvements.length"><strong>Улучшения</strong><ul><li v-for="item in note.improvements" :key="item">{{ item }}</li></ul></section>
					</article>
					<a class="boosty-card" href="https://boosty.to/blockera/donate" target="_blank" rel="noopener noreferrer">
						<div><strong>Поддержать BlockEra</strong><span>Помогите развитию лаунчера — все функции останутся бесплатными.</span></div>
						<b>Boosty</b>
					</a>
				</div>
			</section>
		</transition>
	</div>
</template>

<script setup lang="ts">
import { DownloadIcon, RefreshCwIcon, XIcon } from '@modrinth/assets'
import { getVersion } from '@tauri-apps/api/app'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { releaseNotes } from '@/data/release-notes'
import { automaticWorldBackupsEnabled, backupProfileWorlds } from '@/helpers/backups'
import { get_projects, list, update_all } from '@/helpers/profile'
import {
	availableUpdate,
	checkingState,
	checkLauncherUpdate,
	formatUpdaterError,
	installLauncherUpdate,
	installState,
	updateError,
	updateState,
} from '@/helpers/update'

type UpdateItem = {
	path: string
	name: string
	gameVersion: string
	loader: string
	updates: number
	updating: boolean
}

const root = ref<HTMLElement>()
const open = ref(false)
const activeTab = ref<'updates' | 'notes'>('updates')
const loading = ref(false)
const updatingAll = ref(false)
const items = ref<UpdateItem[]>([])
const lastError = ref('')
const currentVersion = ref('')
const instanceUpdates = computed(() => items.value.reduce((total, item) => total + item.updates, 0))
const totalUpdates = computed(() => instanceUpdates.value + (updateState.value ? 1 : 0))

async function scan() {
	loading.value = true
	lastError.value = ''
	const launcherCheck = checkLauncherUpdate()
	try {
		const profiles = await list()
		const failures: string[] = []
		items.value = await Promise.all(
			profiles.map(async (profile) => {
				let updates = 0
				try {
					const projects = await get_projects(profile.path, 'stale_while_revalidate_skip_offline')
					updates = Object.values((projects ?? {}) as Record<string, { update_version_id?: string }>).filter(
						(project) => project?.update_version_id,
					).length
				} catch (error) {
					failures.push(`${profile.name}: ${formatUpdaterError(error)}`)
				}
				return { path: profile.path, name: profile.name, gameVersion: profile.game_version, loader: profile.loader, updates, updating: false }
			}),
		)
		lastError.value = failures.join('\n')
	} catch (error) {
		lastError.value = `Проверка не удалась: ${formatUpdaterError(error)}`
	} finally {
		await launcherCheck
		loading.value = false
	}
}

async function installUpdate() {
	try { await installLauncherUpdate() } catch (error) {
		if (!updateError.value) updateError.value = formatUpdaterError(error)
	}
}

async function updateInstance(item: UpdateItem) {
	item.updating = true
	lastError.value = ''
	try {
		if (automaticWorldBackupsEnabled()) {
			const backup = await backupProfileWorlds(item.path)
			if (backup.failures.length > 0) throw new Error(`не скопировано миров: ${backup.failures.length}`)
		}
		await update_all(item.path)
		item.updates = 0
	} catch (error) {
		lastError.value = `${item.name}: ${error instanceof Error ? error.message : String(error)}`
	} finally { item.updating = false }
}

async function updateAllInstances() {
	updatingAll.value = true
	for (const item of items.value.filter((entry) => entry.updates > 0)) await updateInstance(item)
	updatingAll.value = false
}

function handleOutside(event: MouseEvent) {
	if (open.value && root.value && !root.value.contains(event.target as Node)) open.value = false
}

function handleKey(event: KeyboardEvent) {
	if (event.key === 'Escape') open.value = false
}

onMounted(async () => {
	document.addEventListener('click', handleOutside)
	document.addEventListener('keydown', handleKey)
	currentVersion.value = await getVersion()
	const seenKey = 'blockera-release-notes-seen'
	if (releaseNotes.some((note) => note.version === currentVersion.value) && localStorage.getItem(seenKey) !== currentVersion.value) {
		activeTab.value = 'notes'
		open.value = true
		localStorage.setItem(seenKey, currentVersion.value)
	}
	void scan()
})

onBeforeUnmount(() => {
	document.removeEventListener('click', handleOutside)
	document.removeEventListener('keydown', handleKey)
})
</script>

<style scoped lang="scss">
.update-center-root { position: relative; }
.update-center-trigger { position: relative; width: 44px; height: 44px; display: grid; place-items: center; color: #d8b3ff; background: rgba(255,255,255,.035); border: 1px solid rgba(173,92,255,.18); border-radius: 13px; cursor: pointer; }
.update-center-trigger:hover { background: rgba(145,63,224,.14); border-color: rgba(183,103,255,.4); }
.update-center-trigger svg { width: 19px; }
.update-center-trigger > span { position: absolute; min-width: 17px; height: 17px; right: -5px; top: -5px; display: grid; place-items: center; color: white; background: #9c42ef; border: 2px solid #0b0e16; border-radius: 999px; font-size: 9px; font-weight: 850; }
.update-center-popover { position: absolute; z-index: 9000; width: min(430px, calc(100vw - 24px)); right: 0; top: 54px; padding: 15px; color: #f7f4fb; background: rgba(11,14,23,.98); border: 1px solid rgba(177,94,255,.28); border-radius: 18px; box-shadow: 0 25px 70px rgba(0,0,0,.55); backdrop-filter: blur(24px); }
.update-center-popover > header, .update-tab-panel > footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.update-center-popover > header div { display: flex; flex-direction: column; }
.update-center-popover > header span, .release-note-heading span { color: #b96dff; font-size: 9px; font-weight: 850; letter-spacing: .13em; }
.update-center-popover h3 { margin: 3px 0 0; font-size: 18px; }
.update-center-popover button { font: inherit; color: inherit; border: 0; cursor: pointer; }
.update-center-popover > header button { width: 32px; height: 32px; display: grid; place-items: center; background: rgba(255,255,255,.045); border-radius: 9px; }
.update-center-popover > header svg { width: 16px; }
.update-tabs { display: grid; grid-template-columns: 1fr 1fr; gap: 5px; margin-top: 12px; padding: 4px; background: rgba(255,255,255,.035); border-radius: 11px; }
.update-tabs button { padding: 8px; color: #9399a7; background: transparent; border-radius: 8px; font-size: 11px; font-weight: 750; }
.update-tabs button.active { color: #f2e8ff; background: rgba(145,59,230,.2); box-shadow: inset 0 0 0 1px rgba(190,111,255,.2); }
.update-tab-panel { margin-top: 11px; }
.launcher-update { padding: 12px; display: flex; align-items: center; justify-content: space-between; gap: 12px; background: rgba(255,255,255,.035); border: 1px solid rgba(255,255,255,.07); border-radius: 12px; }
.launcher-update.available, .update-summary { background: linear-gradient(135deg, rgba(141,57,231,.24), rgba(89,31,158,.1)); border-color: rgba(190,111,255,.32); }
.launcher-update-copy { min-width: 0; display: flex; flex-direction: column; }
.launcher-update-copy > span { color: #bd76ff; font-size: 9px; font-weight: 850; letter-spacing: .12em; }
.launcher-update-copy strong { margin-top: 3px; font-size: 13px; }
.launcher-update-copy small { max-width: 260px; margin-top: 3px; overflow: hidden; color: #9ba1ae; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.install-launcher-update { flex: none; padding: 8px 10px; display: flex; align-items: center; gap: 6px; color: white !important; background: linear-gradient(135deg,#973ef0,#6822c9); border-radius: 9px; font-size: 10px !important; font-weight: 800 !important; }
.install-launcher-update svg { width: 14px; }
.spin { animation: update-spin 900ms linear infinite; }
@keyframes update-spin { to { transform: rotate(360deg); } }
.update-summary { margin: 11px 0 9px; padding: 12px; display: flex; flex-direction: column; border: 1px solid rgba(178,95,255,.17); border-radius: 12px; }
.update-summary strong { font-size: 13px; }
.update-summary span { margin-top: 3px; color: #9298a6; font-size: 10px; }
.update-instance-list, .release-notes-panel { max-height: min(390px, calc(100vh - 260px)); overflow: auto; display: flex; flex-direction: column; gap: 6px; }
.update-instance-row { padding: 10px; display: grid; grid-template-columns: minmax(0,1fr) auto auto; align-items: center; gap: 8px; background: rgba(255,255,255,.03); border-radius: 11px; }
.instance-copy { min-width: 0; display: flex; flex-direction: column; }
.instance-copy strong, .instance-copy span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.instance-copy strong { font-size: 12px; }
.instance-copy span { margin-top: 2px; color: #838a99; font-size: 10px; text-transform: capitalize; }
.update-count { min-width: 24px; padding: 5px; color: #e4c9ff; text-align: center; background: rgba(143,60,226,.18); border-radius: 8px; font-size: 10px; font-weight: 850; }
.update-count.empty { color: #69d897; background: rgba(50,181,111,.1); }
.update-instance-row > button { padding: 7px 9px; color: #dcbcff; background: rgba(145,59,230,.14); border-radius: 8px; font-size: 10px; font-weight: 750; }
.update-tab-panel > footer { margin-top: 11px; }
.update-tab-panel > footer button { padding: 9px 11px; display: flex; align-items: center; gap: 6px; color: #aeb3bf; background: rgba(255,255,255,.045); border-radius: 9px; font-size: 11px; }
.update-tab-panel > footer svg { width: 14px; }
.update-tab-panel > footer .update-all { color: white; background: linear-gradient(135deg,#9139ed,#6721c8); }
.update-center-popover button:disabled { opacity: .5; cursor: wait; }
.update-error { margin: 10px 0 0; padding: 9px; color: #ff9bb2; background: rgba(255,70,110,.08); border-radius: 9px; font-size: 10px; white-space: pre-wrap; word-break: break-word; }
.update-empty { padding: 22px; color: #858c9a; text-align: center; }
.release-note { padding: 12px; border: 1px solid rgba(255,255,255,.07); border-radius: 13px; background: rgba(255,255,255,.03); }
.release-note-heading { display: flex; align-items: center; justify-content: space-between; }
.release-note-heading div { display: flex; flex-direction: column; }
.release-note-heading h4 { margin: 2px 0 0; font-size: 15px; }
.release-note-heading time { color: #858c9a; font-size: 10px; }
.release-note section { margin-top: 10px; }
.release-note section > strong { color: #dfc5ff; font-size: 11px; }
.release-note ul { margin: 5px 0 0; padding-left: 17px; color: #aeb3bf; font-size: 10px; line-height: 1.45; }
.boosty-card { margin-top: 4px; padding: 12px; display: flex; align-items: center; justify-content: space-between; gap: 12px; color: inherit; background: linear-gradient(135deg,rgba(145,57,237,.24),rgba(103,33,200,.12)); border: 1px solid rgba(196,130,255,.26); border-radius: 13px; text-decoration: none; }
.boosty-card div { display: flex; flex-direction: column; }
.boosty-card strong { font-size: 12px; }
.boosty-card span { margin-top: 3px; color: #9da3b1; font-size: 10px; }
.boosty-card b { padding: 7px 9px; color: white; background: #f15f2c; border-radius: 8px; font-size: 10px; }
.update-popover-enter-active,.update-popover-leave-active { transition: opacity 170ms ease, transform 170ms ease; }
.update-popover-enter-from,.update-popover-leave-to { opacity: 0; transform: translateY(-7px) scale(.98); }
@media (prefers-reduced-motion: reduce) { .update-popover-enter-active,.update-popover-leave-active { transition-duration: 1ms; } }
</style>
