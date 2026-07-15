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
	<div v-if="worlds.length > 0" class="flex flex-col gap-4">
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
				<Button v-if="searchFilter" class="r-btn" @click="() => (searchFilter = '')">
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
	</div>
</template>
<script setup lang="ts">
import { PackageIcon, PlusIcon, SearchIcon, SpinnerIcon, UpdatedIcon, XIcon } from '@modrinth/assets'
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
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import type ContextMenu from '@/components/ui/ContextMenu.vue'
import ConfirmModalWrapper from '@/components/ui/modal/ConfirmModalWrapper.vue'
import AddServerModal from '@/components/ui/world/modal/AddServerModal.vue'
import EditServerModal from '@/components/ui/world/modal/EditServerModal.vue'
import EditWorldModal from '@/components/ui/world/modal/EditSingleplayerWorldModal.vue'
import WorldItem from '@/components/ui/world/WorldItem.vue'
import { profile_listener } from '@/helpers/events'
import { get_game_versions } from '@/helpers/tags'
import type { GameInstance } from '@/helpers/types'
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
const backingUp = ref(false)
const backupLabel = ref('Создать бэкап')
const hadNoWorlds = ref(true)
const startingInstance = ref(false)
const worldPlaying = ref<World>()

const worlds = ref<World[]>([])

async function backupAllWorlds() {
	if (backingUp.value) return
	const localWorlds = worlds.value.filter((world): world is SingleplayerWorld => world.type === 'singleplayer')
	if (localWorlds.length === 0) {
		backupLabel.value = 'Нет миров для копии'
		return
	}

	backingUp.value = true
	let completed = 0
	for (const world of localWorlds) {
		try {
			await backup_world(instance.value.path, world.path)
			completed += 1
		} catch (error) {
			handleError(error)
		}
	}
	backupLabel.value = completed === localWorlds.length ? `Скопировано: ${completed}` : `Готово: ${completed}/${localWorlds.length}`
	backingUp.value = false
}
const serverData = ref<Record<string, ServerData>>({})

// Track servers_updated calls on Linux to prevent server ping spam
const MAX_LINUX_REFRESHES = 3
const isLinux = platform() === 'linux'
const linuxRefreshCount = ref(0)

const protocolVersion = ref<ProtocolVersion | null>(
	await get_profile_protocol_version(instance.value.path),
)

const unlistenProfile = await profile_listener(async (e: ProfileEvent) => {
	if (e.profile_path_id !== instance.value.path) return

	console.info(`Handling profile event '${e.event}' for profile: ${e.profile_path_id}`)

	if (e.event === 'servers_updated') {
		if (isLinux && linuxRefreshCount.value >= MAX_LINUX_REFRESHES) return
		if (isLinux) linuxRefreshCount.value++

		await refreshAllWorlds()
	}

	await handleDefaultProfileUpdateEvent(worlds.value, instance.value.path, e)
})

await refreshAllWorlds()

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

	worlds.value = await refreshWorlds(instance.value.path).finally(
		() => (refreshingAll.value = false),
	)
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

function handleJoinError(err: Error) {
	handleError(err)
	startingInstance.value = false
	worldPlaying.value = undefined
}

async function joinWorld(world: World) {
	console.log(`Joining world ${getWorldIdentifier(world)}`)
	startingInstance.value = true
	worldPlaying.value = world
	if (world.type === 'server') {
		await start_join_server(instance.value.path, world.address).catch(handleJoinError)
	} else if (world.type === 'singleplayer') {
		await start_join_singleplayer_world(instance.value.path, world.path).catch(handleJoinError)
	}
	play(world)
	startingInstance.value = false
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

const gameVersions = ref<GameVersion[]>(await get_game_versions().catch(() => []))
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
.blockera-worlds {
	:deep(.iconified-input) { height: 42px; background: rgba(8,12,20,.72); border: 1px solid rgba(255,255,255,.085); border-radius: 12px; }
	:deep(.iconified-input:focus-within) { border-color: rgba(177,91,255,.48); }
	:deep(.world-item), :deep(.card) { background: rgba(255,255,255,.03); border-color: rgba(255,255,255,.07); border-radius: 13px; box-shadow: none; }
	:deep(button) { border-radius: 10px; }
}

.blockera-worlds-empty {
	min-height: 370px;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	text-align: center;
	background: radial-gradient(circle at 50% 45%, rgba(133,54,214,.12), transparent 18rem);

	.worlds-empty-icon { width: 64px; height: 64px; display: grid; place-items: center; color: #c587ff; background: rgba(150,70,235,.14); border: 1px solid rgba(184,105,255,.3); border-radius: 19px; }
	.worlds-empty-icon svg { width: 28px; }
	> span { margin-top: 18px; color: #b469f5; font-size: 10px; font-weight: 850; letter-spacing: .14em; }
	h2 { margin: 6px 0; color: #f7f4fa; font-size: 25px; }
	p { max-width: 430px; margin: 0; color: #8e95a4; line-height: 1.55; }
}
</style>
