<template>
	<div ref="root" class="update-center-root">
		<button class="update-center-trigger" type="button" :aria-expanded="open" @click="open = !open">
			<DownloadIcon />
			<span v-if="totalUpdates > 0">{{ totalUpdates }}</span>
		</button>
		<transition name="update-popover">
			<section v-if="open" class="update-center-popover">
				<header>
					<div><span>ЦЕНТР ОБНОВЛЕНИЙ</span><h3>Сборки BlockEra</h3></div>
					<button type="button" aria-label="Закрыть" @click="open = false"><XIcon /></button>
				</header>
				<div class="update-summary">
					<strong>{{ loading ? 'Проверяем сборки…' : totalUpdates ? `Доступно обновлений: ${totalUpdates}` : 'Всё актуально' }}</strong>
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
					<button type="button" :disabled="loading" @click="scan"><RefreshCwIcon /> Проверить снова</button>
					<button type="button" class="update-all" :disabled="totalUpdates === 0 || updatingAll" @click="updateAllInstances">
						{{ updatingAll ? 'Обновляем…' : 'Обновить всё' }}
					</button>
				</footer>
				<p v-if="lastError" class="update-error">{{ lastError }}</p>
			</section>
		</transition>
	</div>
</template>

<script setup lang="ts">
import { DownloadIcon, RefreshCwIcon, XIcon } from '@modrinth/assets'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import { automaticWorldBackupsEnabled, backupProfileWorlds } from '@/helpers/backups'
import { get_projects, list, update_all } from '@/helpers/profile'

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
const loading = ref(false)
const updatingAll = ref(false)
const items = ref<UpdateItem[]>([])
const lastError = ref('')
const totalUpdates = computed(() => items.value.reduce((total, item) => total + item.updates, 0))

async function scan() {
	loading.value = true
	lastError.value = ''
	try {
		const profiles = await list()
		items.value = await Promise.all(
			profiles.map(async (profile) => {
				const projects = await get_projects(profile.path, 'stale_while_revalidate_skip_offline')
				const updates = Object.values(
					(projects ?? {}) as Record<string, { update_version_id?: string }>,
				).filter((project) => project.update_version_id).length
				return {
					path: profile.path,
					name: profile.name,
					gameVersion: profile.game_version,
					loader: profile.loader,
					updates,
					updating: false,
				}
			}),
		)
	} catch (error) {
		lastError.value = `Проверка не удалась: ${error instanceof Error ? error.message : String(error)}`
	} finally {
		loading.value = false
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
	} finally {
		item.updating = false
	}
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

onMounted(() => {
	document.addEventListener('click', handleOutside)
	document.addEventListener('keydown', handleKey)
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
.update-center-trigger span { position: absolute; min-width: 17px; height: 17px; right: -5px; top: -5px; display: grid; place-items: center; color: white; background: #9c42ef; border: 2px solid #0b0e16; border-radius: 999px; font-size: 9px; font-weight: 850; }
.update-center-popover { position: absolute; z-index: 9000; width: min(420px, calc(100vw - 24px)); right: 0; top: 54px; padding: 15px; color: #f7f4fb; background: rgba(11,14,23,.98); border: 1px solid rgba(177,94,255,.28); border-radius: 18px; box-shadow: 0 25px 70px rgba(0,0,0,.55); backdrop-filter: blur(24px); }
.update-center-popover header, .update-center-popover footer { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.update-center-popover header div { display: flex; flex-direction: column; }
.update-center-popover header span { color: #b96dff; font-size: 9px; font-weight: 850; letter-spacing: .13em; }
.update-center-popover h3 { margin: 3px 0 0; font-size: 18px; }
.update-center-popover button { font: inherit; color: inherit; border: 0; cursor: pointer; }
.update-center-popover header button { width: 32px; height: 32px; display: grid; place-items: center; background: rgba(255,255,255,.045); border-radius: 9px; }
.update-center-popover header svg { width: 16px; }
.update-summary { margin: 13px 0 10px; padding: 12px; display: flex; flex-direction: column; background: linear-gradient(135deg, rgba(132,54,218,.17), rgba(89,31,158,.08)); border: 1px solid rgba(178,95,255,.17); border-radius: 12px; }
.update-summary strong { font-size: 13px; }
.update-summary span { margin-top: 3px; color: #9298a6; font-size: 10px; }
.update-instance-list { max-height: min(350px, calc(100vh - 280px)); overflow: auto; display: flex; flex-direction: column; gap: 6px; }
.update-instance-row { padding: 10px; display: grid; grid-template-columns: minmax(0,1fr) auto auto; align-items: center; gap: 8px; background: rgba(255,255,255,.03); border-radius: 11px; }
.instance-copy { min-width: 0; display: flex; flex-direction: column; }
.instance-copy strong, .instance-copy span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.instance-copy strong { font-size: 12px; }
.instance-copy span { margin-top: 2px; color: #838a99; font-size: 10px; text-transform: capitalize; }
.update-count { min-width: 24px; padding: 5px; color: #e4c9ff; text-align: center; background: rgba(143,60,226,.18); border-radius: 8px; font-size: 10px; font-weight: 850; }
.update-count.empty { color: #69d897; background: rgba(50,181,111,.1); }
.update-instance-row > button { padding: 7px 9px; color: #dcbcff; background: rgba(145,59,230,.14); border-radius: 8px; font-size: 10px; font-weight: 750; }
.update-center-popover footer { margin-top: 11px; }
.update-center-popover footer button { padding: 9px 11px; display: flex; align-items: center; gap: 6px; color: #aeb3bf; background: rgba(255,255,255,.045); border-radius: 9px; font-size: 11px; }
.update-center-popover footer svg { width: 14px; }
.update-center-popover footer .update-all { color: white; background: linear-gradient(135deg,#9139ed,#6721c8); }
.update-center-popover button:disabled { opacity: .5; cursor: wait; }
.update-error { margin: 10px 0 0; padding: 9px; color: #ff9bb2; background: rgba(255,70,110,.08); border-radius: 9px; font-size: 10px; }
.update-empty { padding: 22px; color: #858c9a; text-align: center; }
.update-popover-enter-active,.update-popover-leave-active { transition: opacity 170ms ease, transform 170ms ease; }
.update-popover-enter-from,.update-popover-leave-to { opacity: 0; transform: translateY(-7px) scale(.98); }
@media (prefers-reduced-motion: reduce) { .update-popover-enter-active,.update-popover-leave-active { transition-duration: 1ms; } }
</style>
