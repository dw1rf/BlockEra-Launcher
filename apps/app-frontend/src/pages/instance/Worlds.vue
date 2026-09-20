<template>
	<AddServerModal
		ref="addServerModal"
		:instance="instance"
		@submit="
			(server, start) => {
				addServer(server)
				if (start) {
					joinWorld(server)
				}
			}
		"
	/>
	<EditServerModal ref="editServerModal" :instance="instance" @submit="editServer" />
	<EditWorldModal ref="editWorldModal" :instance="instance" @submit="editWorld" />
	<ConfirmModalWrapper
		ref="removeServerModal"
		:title="`Удалить сервер ${serverToRemove?.name ?? ''}?`"
		:description="`Сервер будет удалён из списка этой сборки.`"
		:markdown="false"
		@proceed="proceedRemoveServer"
	/>
	<ConfirmModalWrapper
		ref="deleteWorldModal"
		:title="`Удалить мир без возможности восстановления?`"
		:description="`Мир '${worldToDelete?.name}' будет удалён навсегда. Перед удалением рекомендуется создать резервную копию.`"
		@proceed="proceedDeleteWorld"
	/>
	<div class="blockera-worlds">
		<div v-if="joinError" class="world-launch-error" role="alert">
			<div>
				<strong>Не удалось запустить мир</strong><span>{{ joinError }}</span>
			</div>
			<button
				v-if="failedWorld"
				type="button"
				:disabled="startingInstance"
				@click="joinWorld(failedWorld)"
			>
				Повторить
			</button>
		</div>
		<div v-if="!worldsLoaded" class="worlds-local-skeleton" aria-label="Загрузка миров">
			<span></span><span></span><span></span>
		</div>
		<div v-else-if="worlds.length > 0" class="flex flex-col gap-4">
			<div class="flex flex-wrap gap-2 items-center">
				<div class="iconified-input flex-grow">
					<SearchIcon />
					<input
						v-model="searchFilter"
						type="text"
						placeholder="Поиск миров и серверов…"
						class="text-input search-input"
						autocomplete="off"
					/>
					<Button
						v-if="searchFilter"
						v-tooltip="'Очистить поиск'"
						class="r-btn"
						aria-label="Очистить поиск"
						@click="() => (searchFilter = '')"
					>
						<XIcon />
					</Button>
				</div>
				<ButtonStyled>
					<button :disabled="refreshingAll" @click="refreshAllWorlds">
						<template v-if="refreshingAll">
							<SpinnerIcon class="animate-spin" />
							Обновляем…
						</template>
						<template v-else>
							<UpdatedIcon />
							Обновить
						</template>
					</button>
				</ButtonStyled>
				<ButtonStyled>
					<button @click="addServerModal?.show()">
						<PlusIcon />
						Добавить сервер
					</button>
				</ButtonStyled>
				<ButtonStyled>
					<button :disabled="backingUp" @click="backupAllWorlds">
						<PackageIcon /> {{ backingUp ? 'Создаём копии…' : backupLabel }}
					</button>
				</ButtonStyled>
			</div>
			<FilterBar v-model="filters" :options="filterOptions" show-all-options />
			<div class="flex flex-col w-full gap-2">
				<WorldItem
					v-for="world in filteredWorlds"
					:key="`world-${world.type}-${world.type == 'singleplayer' ? world.path : `${world.address}-${world.index}`}`"
					:world="world"
					:highlighted="highlightedWorld === getWorldIdentifier(world)"
					:supports-server-quick-play="supportsServerQuickPlay"
					:supports-world-quick-play="supportsWorldQuickPlay"
					:current-protocol="protocolVersion"
					:playing-instance="playing"
					:playing-world="worldsMatch(world, worldPlaying)"
					:starting-instance="startingInstance"
					:refreshing="world.type === 'server' ? serverData[world.address]?.refreshing : undefined"
					:server-status="world.type === 'server' ? serverData[world.address]?.status : undefined"
					:rendered-motd="
						world.type === 'server' ? serverData[world.address]?.renderedMotd : undefined
					"
					:game-mode="world.type === 'singleplayer' ? GAME_MODES[world.game_mode] : undefined"
					@play="() => joinWorld(world)"
					@stop="() => emit('stop')"
					@refresh="() => refreshServer((world as ServerWorld).address)"
					@edit="
						() =>
							world.type === 'server' ? editServerModal?.show(world) : editWorldModal?.show(world)
					"
					@delete="() => promptToRemoveWorld(world)"
					@backup="() => backupSingleWorld(world)"
					@open-folder="(world: SingleplayerWorld) => showWorldInFolder(instance.path, world.path)"
				/>
			</div>
		</div>
		<div v-else class="blockera-worlds-empty">
			<div class="worlds-empty-icon"><PackageIcon /></div>
			<span>МИРЫ И СЕРВЕРЫ</span>
			<h2>Миров пока нет</h2>
			<p>Создайте мир в Minecraft или добавьте сервер для быстрого подключения.</p>
			<div class="flex gap-2 mt-4 mx-auto">
				<ButtonStyled>
					<button @click="addServerModal?.show()">
						<PlusIcon aria-hidden="true" />
						Добавить сервер
					</button>
				</ButtonStyled>
				<ButtonStyled>
					<button :disabled="refreshingAll" @click="refreshAllWorlds">
						<template v-if="refreshingAll">
							<SpinnerIcon aria-hidden="true" class="animate-spin" />
							Обновляем…
						</template>
						<template v-else>
							<UpdatedIcon aria-hidden="true" />
							Обновить
						</template>
					</button>
				</ButtonStyled>
			</div>
		</div>
		<section class="backup-library">
			<div class="backup-library-heading">
				<div>
					<span>РЕЗЕРВНЫЕ КОПИИ</span>
					<h3>Бэкапы миров</h3>
				</div>
				<ButtonStyled>
					<button @click="openProfileFolder(instance.path, 'backups')">
						<FolderOpenIcon /> Открыть папку
					</button>
				</ButtonStyled>
			</div>
			<p v-if="backupStatus" class="backup-status">{{ backupStatus }}</p>
			<div v-if="!backupListLoaded" class="backup-list backup-list-skeleton">
				<span></span><span></span>
			</div>
			<div v-else-if="worldBackups.length" class="backup-list">
				<article v-for="backup in worldBackups" :key="backup.id" class="backup-row">
					<div>
						<strong>{{ backup.world }}</strong>
						<span
							>{{ formatBackupDate(backup.createdAt) }} · {{ formatBackupSize(backup.size) }}</span
						>
					</div>
					<div class="backup-actions">
						<button
							:disabled="playing || restoringBackup === backup.id"
							@click="restoreBackup(backup)"
						>
							{{ restoringBackup === backup.id ? 'Восстанавливаем…' : 'Восстановить' }}
						</button>
						<button :disabled="deletingBackup === backup.id" @click="removeBackup(backup)">
							Удалить
						</button>
					</div>
				</article>
			</div>
			<p v-else class="backup-empty">Резервных копий пока нет.</p>
		</section>
	</div>
</template>
<script setup lang="ts">
import {
	FolderOpenIcon,
	PackageIcon,
	PlusIcon,
	SearchIcon,
	SpinnerIcon,
	UpdatedIcon,
	XIcon,
} from '@modrinth/assets'
import {
	Button,
	ButtonStyled,
	defineMessages,
	FilterBar,
	type FilterBarOption,
	GAME_MODES,
	type GameVersion,
	injectNotificationManager,
} from '@modrinth/ui'
import type { Version } from '@modrinth/utils'
import { platform } from '@tauri-apps/plugin-os'
import dayjs from 'dayjs'
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import type ContextMenu from '@/components/ui/ContextMenu.vue'
import ConfirmModalWrapper from '@/components/ui/modal/ConfirmModalWrapper.vue'
import AddServerModal from '@/components/ui/world/modal/AddServerModal.vue'
import EditServerModal from '@/components/ui/world/modal/EditServerModal.vue'
import EditWorldModal from '@/components/ui/world/modal/EditSingleplayerWorldModal.vue'
import WorldItem from '@/components/ui/world/WorldItem.vue'
import {
	backupProfileWorlds,
	deleteWorldBackup,
	listWorldBackups,
	restoreWorldBackup,
	type WorldBackup,
} from '@/helpers/backups'
import { profile_listener, world_backup_listener } from '@/helpers/events'
import { get_game_versions } from '@/helpers/tags'
import type { GameInstance } from '@/helpers/types'
import { openProfileFolder } from '@/helpers/utils'
import {
	backup_world,
	delete_world,
	get_profile_protocol_version,
	getWorldIdentifier,
	handleDefaultProfileUpdateEvent,
	hasServerQuickPlaySupport,
	hasWorldQuickPlaySupport,
	type ProfileEvent,
	type ProtocolVersion,
	refreshServerData,
	refreshServers,
	refreshWorld,
	refreshWorlds,
	remove_server_from_profile,
	type ServerData,
	type ServerWorld,
	showWorldInFolder,
	type SingleplayerWorld,
	sortWorlds,
	start_join_server,
	start_join_singleplayer_world,
	type World,
} from '@/helpers/worlds.ts'

const { handleError } = injectNotificationManager()
const route = useRoute()

const addServerModal = ref<InstanceType<typeof AddServerModal>>()
const editServerModal = ref<InstanceType<typeof EditServerModal>>()
const editWorldModal = ref<InstanceType<typeof EditWorldModal>>()
const removeServerModal = ref<InstanceType<typeof ConfirmModalWrapper>>()
const deleteWorldModal = ref<InstanceType<typeof ConfirmModalWrapper>>()

const serverToRemove = ref<ServerWorld>()
const worldToDelete = ref<SingleplayerWorld>()

const emit = defineEmits<{
	(event: 'play', world: World): void
	(event: 'stop'): void
}>()

const props = defineProps<{
	instance: GameInstance
	options: InstanceType<typeof ContextMenu> | null
	offline: boolean
	playing: boolean
	versions: Version[]
	installed: boolean
}>()

const instance = computed(() => props.instance)
const playing = computed(() => props.playing)

function play(world: World) {
	emit('play', world)
}

const filters = ref<string[]>([])
const searchFilter = ref('')

const refreshingAll = ref(false)
const worldsLoaded = ref(false)
const backingUp = ref(false)
const backupLabel = ref('Создать бэкап')
const backupStatus = ref('')
const worldBackups = ref<WorldBackup[]>([])
const backupListLoaded = ref(false)
const restoringBackup = ref<string>()
const deletingBackup = ref<string>()
const hadNoWorlds = ref(true)
const startingInstance = ref(false)
const worldPlaying = ref<World>()
const failedWorld = ref<World>()
const joinError = ref('')

const worlds = ref<World[]>([])

async function backupAllWorlds() {
	if (backingUp.value) return
	const localWorlds = worlds.value.filter((world) => world.type === 'singleplayer')
	if (localWorlds.length === 0) {
		backupLabel.value = 'Нет миров для копии'
		return
	}

	backingUp.value = true
	try {
		const result = await backupProfileWorlds(instance.value.path, 'manual')
		backupLabel.value = result.failures.length
			? `Готово: ${result.count}/${localWorlds.length}`
			: `Скопировано: ${result.count}`
		backupStatus.value = result.failures.length
			? result.failures.map((failure) => `${failure.world}: ${failure.error}`).join('; ')
			: `Создано копий: ${result.count}`
		await refreshBackupList()
	} catch (error) {
		handleError(error instanceof Error ? error : new Error(String(error)))
	} finally {
		backingUp.value = false
	}
}

async function backupSingleWorld(world: World) {
	if (world.type !== 'singleplayer') return
	try {
		await backup_world(instance.value.path, world.path)
		backupStatus.value = `Резервная копия мира «${world.name}» создана.`
		await refreshBackupList()
	} catch (error) {
		handleError(error instanceof Error ? error : new Error(String(error)))
	}
}

async function refreshBackupList() {
	worldBackups.value = await listWorldBackups(instance.value.path).catch((error) => {
		handleError(error instanceof Error ? error : new Error(String(error)))
		return []
	})
	backupListLoaded.value = true
}

function formatBackupDate(value: string) {
	return dayjs(value).format('DD.MM.YYYY HH:mm')
}

function formatBackupSize(bytes: number) {
	if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} КБ`
	return `${(bytes / 1024 / 1024).toFixed(1)} МБ`
}

async function restoreBackup(backup: WorldBackup) {
	if (
		playing.value ||
		!window.confirm(
			`Восстановить мир «${backup.world}» из этой копии? Текущее состояние будет сохранено отдельно.`,
		)
	)
		return
	restoringBackup.value = backup.id
	try {
		await restoreWorldBackup(instance.value.path, backup.id)
		backupStatus.value = `Мир «${backup.world}» восстановлен.`
		await Promise.all([refreshAllWorlds(), refreshBackupList()])
	} catch (error) {
		handleError(error instanceof Error ? error : new Error(String(error)))
	} finally {
		restoringBackup.value = undefined
	}
}

async function removeBackup(backup: WorldBackup) {
	if (!window.confirm(`Удалить резервную копию мира «${backup.world}»?`)) return
	deletingBackup.value = backup.id
	try {
		await deleteWorldBackup(instance.value.path, backup.id)
		backupStatus.value = 'Резервная копия удалена.'
		await refreshBackupList()
	} catch (error) {
		handleError(error instanceof Error ? error : new Error(String(error)))
	} finally {
		deletingBackup.value = undefined
	}
}
const serverData = ref<Record<string, ServerData>>({})

// Track servers_updated calls on Linux to prevent server ping spam
const MAX_LINUX_REFRESHES = 3
const isLinux = platform() === 'linux'
const linuxRefreshCount = ref(0)

const protocolVersion = ref<ProtocolVersion | null>(null)
let unlistenProfile = () => {}
let unlistenBackups = () => {}

async function refreshServer(address: string) {
	if (!serverData.value[address]) {
		serverData.value[address] = {
			refreshing: true,
		}
	}
	await refreshServerData(serverData.value[address], protocolVersion.value, address)
}

async function refreshAllWorlds() {
	if (refreshingAll.value) {
		console.log(`Already refreshing, cancelling refresh.`)
		return
	}

	refreshingAll.value = true

	worlds.value = await refreshWorlds(instance.value.path).finally(() => {
		refreshingAll.value = false
		worldsLoaded.value = true
	})
	refreshServers(worlds.value, serverData.value, protocolVersion.value)

	const hasNoWorlds = worlds.value.length === 0

	if (hadNoWorlds.value && hasNoWorlds) {
		setTimeout(() => {
			refreshingAll.value = false
		}, 1000)
	} else {
		refreshingAll.value = false
	}

	hadNoWorlds.value = hasNoWorlds
}

async function addServer(server: ServerWorld) {
	worlds.value.push(server)
	sortWorlds(worlds.value)
	await refreshServer(server.address)
}

async function editServer(server: ServerWorld) {
	const index = worlds.value.findIndex((w) => w.type === 'server' && w.index === server.index)
	if (index !== -1) {
		const oldServer = worlds.value[index] as ServerWorld
		worlds.value[index] = server
		sortWorlds(worlds.value)
		if (oldServer.address !== server.address) {
			await refreshServer(server.address)
		}
	} else {
		handleError(new Error(`Error refreshing server, refreshing all worlds`))
		await refreshAllWorlds()
	}
}

async function removeServer(server: ServerWorld) {
	await remove_server_from_profile(instance.value.path, server.index).catch(handleError)
	worlds.value = worlds.value.filter((w) => w.type !== 'server' || w.index !== server.index)
}

async function editWorld(path: string, name: string, removeIcon: boolean) {
	const world = worlds.value.find((world) => world.type === 'singleplayer' && world.path === path)
	if (world) {
		world.name = name
		if (removeIcon) {
			world.icon = undefined
		}
		sortWorlds(worlds.value)
	} else {
		handleError(new Error(`Error finding world in list, refreshing all worlds`))
		await refreshAllWorlds()
	}
}

async function deleteWorld(world: SingleplayerWorld) {
	await delete_world(instance.value.path, world.path).catch(handleError)
	worlds.value = worlds.value.filter((w) => w.type !== 'singleplayer' || w.path !== world.path)
}

async function joinWorld(world: World) {
	if (startingInstance.value) return
	startingInstance.value = true
	worldPlaying.value = world
	failedWorld.value = undefined
	joinError.value = ''
	try {
		if (world.type === 'server') {
			await start_join_server(instance.value.path, world.address)
		} else if (world.type === 'singleplayer') {
			await start_join_singleplayer_world(instance.value.path, world.path)
		}
		play(world)
	} catch (error) {
		failedWorld.value = world
		worldPlaying.value = undefined
		joinError.value = error instanceof Error ? error.message : String(error)
		handleError(error instanceof Error ? error : new Error(String(error)))
	} finally {
		startingInstance.value = false
	}
}

watch(
	() => playing.value,
	(playing) => {
		if (!playing) {
			worldPlaying.value = undefined

			setTimeout(async () => {
				for (const world of worlds.value) {
					if (world.type === 'singleplayer' && world.locked) {
						await refreshWorld(worlds.value, instance.value.path, world.path)
					}
				}
			}, 1000)
		}
	},
)

function worldsMatch(world: World, other: World | undefined) {
	if (world.type === 'server' && other?.type === 'server') {
		return world.address === other.address
	} else if (world.type === 'singleplayer' && other?.type === 'singleplayer') {
		return world.path === other.path
	}
	return false
}

const gameVersions = ref<GameVersion[]>([])
const supportsServerQuickPlay = computed(() =>
	hasServerQuickPlaySupport(gameVersions.value, instance.value.game_version),
)
const supportsWorldQuickPlay = computed(() =>
	hasWorldQuickPlaySupport(gameVersions.value, instance.value.game_version),
)

const filterOptions = computed(() => {
	const options: FilterBarOption[] = []

	const hasServer = worlds.value.some((x) => x.type === 'server')

	if (worlds.value.some((x) => x.type === 'singleplayer') && hasServer) {
		options.push({
			id: 'singleplayer',
			message: messages.singleplayer,
		})
		options.push({
			id: 'server',
			message: messages.server,
		})
	}

	if (hasServer) {
		// add available filter if there's any offline ("unavailable") servers AND there's any singleplayer worlds or available servers
		if (
			worlds.value.some(
				(x) =>
					x.type === 'server' &&
					!serverData.value[x.address]?.status &&
					!serverData.value[x.address]?.refreshing,
			) &&
			worlds.value.some(
				(x) =>
					x.type === 'singleplayer' ||
					(x.type === 'server' &&
						serverData.value[x.address]?.status &&
						!serverData.value[x.address]?.refreshing),
			)
		) {
			options.push({
				id: 'available',
				message: messages.available,
			})
		}
	}

	return options
})

const filteredWorlds = computed(() =>
	worlds.value.filter((x) => {
		const availableFilter = filters.value.includes('available')
		const typeFilter = filters.value.includes('server') || filters.value.includes('singleplayer')

		return (
			(!typeFilter || filters.value.includes(x.type)) &&
			(!availableFilter || x.type !== 'server' || serverData.value[x.address]?.status) &&
			(!searchFilter.value || x.name.toLowerCase().includes(searchFilter.value.toLowerCase()))
		)
	}),
)

const highlightedWorld = ref(route.query.highlight)

function promptToRemoveWorld(world: World): boolean {
	if (world.type === 'server') {
		serverToRemove.value = world
		removeServerModal.value?.show()
		return !!removeServerModal.value
	} else {
		worldToDelete.value = world
		deleteWorldModal.value?.show()
		return !!deleteWorldModal.value
	}
}

async function proceedRemoveServer() {
	if (!serverToRemove.value) {
		handleError(new Error(`Error removing server, no server marked for removal.`))
		return
	}
	await removeServer(serverToRemove.value)
	serverToRemove.value = undefined
}

async function proceedDeleteWorld() {
	if (!worldToDelete.value) {
		handleError(new Error(`Error deleting world, no world marked for removal.`))
		return
	}
	await deleteWorld(worldToDelete.value)
	worldToDelete.value = undefined
}

onUnmounted(() => {
	unlistenProfile()
	unlistenBackups()
})

onMounted(async () => {
	void Promise.all([
		get_profile_protocol_version(instance.value.path)
			.then((value) => (protocolVersion.value = value))
			.catch(() => {}),
		get_game_versions()
			.then((versions) => (gameVersions.value = versions))
			.catch(() => {}),
		refreshAllWorlds(),
		refreshBackupList(),
	])
	unlistenProfile = await profile_listener(async (event: ProfileEvent) => {
		if (event.profile_path_id !== instance.value.path) return
		if (event.event === 'servers_updated') {
			if (isLinux && linuxRefreshCount.value >= MAX_LINUX_REFRESHES) return
			if (isLinux) linuxRefreshCount.value++
			await refreshAllWorlds()
		}
		await handleDefaultProfileUpdateEvent(worlds.value, instance.value.path, event)
	})
	unlistenBackups = await world_backup_listener((event) => {
		if (event.profile !== instance.value.path) return
		backingUp.value = event.state === 'started'
		if (event.state === 'completed') {
			backupStatus.value = `Резервная копия «${event.world}» создана.`
			void refreshBackupList()
		} else if (event.state === 'cancelled') {
			backupStatus.value = 'Автобэкап отменён перед запуском игры.'
		} else if (event.state === 'failed') {
			backupStatus.value = event.error || 'Не удалось создать резервную копию.'
		}
	})
})

const messages = defineMessages({
	singleplayer: {
		id: 'instance.worlds.type.singleplayer',
		defaultMessage: 'Singleplayer',
	},
	server: {
		id: 'instance.worlds.type.server',
		defaultMessage: 'Server',
	},
	available: {
		id: 'instance.worlds.filter.available',
		defaultMessage: 'Available',
	},
})
</script>

<style scoped lang="scss">
.worlds-local-skeleton,
.backup-list-skeleton {
	display: grid;
	gap: 10px;
}

.worlds-local-skeleton span,
.backup-list-skeleton span {
	display: block;
	height: 82px;
	border: 1px solid rgba(255, 255, 255, 0.06);
	border-radius: 12px;
	background: rgba(255, 255, 255, 0.045);
}

.worlds-local-skeleton {
	min-height: 300px;
}

.world-launch-error {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
	margin-bottom: 1rem;
	padding: 0.85rem;
	border: 1px solid var(--blockera-danger);
	border-radius: var(--blockera-radius-lg);
	background: color-mix(in srgb, var(--blockera-danger) 9%, transparent);
}
.world-launch-error > div {
	display: grid;
	gap: 0.25rem;
}
.world-launch-error span {
	color: var(--color-secondary);
}
.blockera-worlds {
	:deep(.iconified-input) {
		height: 42px;
		background: rgba(8, 12, 20, 0.72);
		border: 1px solid rgba(255, 255, 255, 0.085);
		border-radius: 12px;
	}
	:deep(.iconified-input:focus-within) {
		border-color: rgba(177, 91, 255, 0.48);
	}
	:deep(.world-item),
	:deep(.card) {
		background: rgba(255, 255, 255, 0.03);
		border-color: rgba(255, 255, 255, 0.07);
		border-radius: 13px;
		box-shadow: none;
	}
	:deep(button) {
		border-radius: 10px;
	}
}

.backup-library {
	margin-top: 1.25rem;
	padding: 1rem;
	border: 1px solid rgba(255, 255, 255, 0.08);
	border-radius: 14px;
	background: rgba(255, 255, 255, 0.025);
}
.backup-library-heading,
.backup-row,
.backup-actions {
	display: flex;
	align-items: center;
}
.backup-library-heading,
.backup-row {
	justify-content: space-between;
	gap: 1rem;
}
.backup-library-heading span {
	color: #b469f5;
	font-size: 10px;
	font-weight: 850;
	letter-spacing: 0.12em;
}
.backup-library-heading h3 {
	margin: 0.2rem 0 0;
}
.backup-list {
	display: grid;
	gap: 0.5rem;
	margin-top: 0.85rem;
}
.backup-row {
	padding: 0.75rem;
	border: 1px solid rgba(255, 255, 255, 0.07);
	border-radius: 10px;
	background: rgba(7, 10, 17, 0.45);
}
.backup-row > div:first-child {
	display: grid;
	gap: 0.2rem;
}
.backup-row span,
.backup-status,
.backup-empty {
	color: var(--color-secondary);
	font-size: 0.85rem;
}
.backup-actions {
	gap: 0.4rem;
}
.backup-actions button {
	padding: 0.45rem 0.7rem;
	border: 1px solid rgba(255, 255, 255, 0.1);
	background: rgba(255, 255, 255, 0.05);
	color: inherit;
}
.backup-status {
	margin: 0.75rem 0 0;
}
.backup-empty {
	margin: 0.9rem 0 0;
}

.blockera-worlds-empty {
	min-height: 370px;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	text-align: center;
	background: radial-gradient(circle at 50% 45%, rgba(133, 54, 214, 0.12), transparent 18rem);

	.worlds-empty-icon {
		width: 64px;
		height: 64px;
		display: grid;
		place-items: center;
		color: #c587ff;
		background: rgba(150, 70, 235, 0.14);
		border: 1px solid rgba(184, 105, 255, 0.3);
		border-radius: 19px;
	}
	.worlds-empty-icon svg {
		width: 28px;
	}
	> span {
		margin-top: 18px;
		color: #b469f5;
		font-size: 10px;
		font-weight: 850;
		letter-spacing: 0.14em;
	}
	h2 {
		margin: 6px 0;
		color: #f7f4fa;
		font-size: 25px;
	}
	p {
		max-width: 430px;
		margin: 0;
		color: #8e95a4;
		line-height: 1.55;
	}
}
</style>
