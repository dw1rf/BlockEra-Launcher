<script setup>
import {
	DownloadIcon,
	GameIcon,
	PlayIcon,
	SpinnerIcon,
	StopCircleIcon,
	TimerIcon,
} from '@modrinth/assets'
import { ButtonStyled, injectNotificationManager, useRelativeTime } from '@modrinth/ui'
import dayjs from 'dayjs'
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { trackEvent } from '@/helpers/analytics'
import { process_listener } from '@/helpers/events'
import { instanceBackgroundFor } from '@/helpers/instance-backgrounds'
import { libraryPlaytime } from '@/helpers/library'
import { get_by_profile_path } from '@/helpers/process'
import { finish_install, kill, run } from '@/helpers/profile'
import { openProfileFolder, showProfileInFolder } from '@/helpers/utils.js'
import { handleSevereError } from '@/store/error.js'
import { useSelectedInstance } from '@/store/selected-instance'

const { handleError } = injectNotificationManager()
const selectedInstanceStore = useSelectedInstance()
const formatRelativeTime = useRelativeTime()

defineEmits(['favorite', 'organize'])
const props = defineProps({
	libraryMeta: { type: Object, default: null },
	instance: {
		type: Object,
		default() {
			return {}
		},
	},
	compact: {
		type: Boolean,
		default: false,
	},
	first: {
		type: Boolean,
		default: false,
	},
})

const playing = ref(false)
const loading = ref(false)
const modLoading = computed(
	() =>
		loading.value ||
		currentEvent.value === 'installing' ||
		(currentEvent.value === 'launched' && !playing.value),
)
const installing = computed(() => props.instance.install_stage.includes('installing'))
const installed = computed(() => props.instance.install_stage === 'installed')

const router = useRouter()

const seeInstance = async () => {
	selectedInstanceStore.setSelectedInstance(props.instance.path)
	await router.push(`/instance/${encodeURIComponent(props.instance.path)}`)
}

const checkProcess = async () => {
	const runningProcesses = await get_by_profile_path(props.instance.path).catch(handleError)

	playing.value = runningProcesses.length > 0
}

const play = async (e, context) => {
	e?.stopPropagation()
	selectedInstanceStore.setSelectedInstance(props.instance.path)
	loading.value = true
	await run(props.instance.path)
		.catch((err) => handleSevereError(err, { profilePath: props.instance.path }))
		.finally(() => {
			trackEvent('InstancePlay', {
				loader: props.instance.loader,
				game_version: props.instance.game_version,
				source: context,
			})
		})
	loading.value = false
}

const stop = async (e, context) => {
	e?.stopPropagation()
	playing.value = false

	await kill(props.instance.path).catch(handleError)

	trackEvent('InstanceStop', {
		loader: props.instance.loader,
		game_version: props.instance.game_version,
		source: context,
	})
}

const repair = async (e) => {
	e?.stopPropagation()

	await finish_install(props.instance).catch(handleError)
}

const openFolder = async (folder = 'root') => {
	if (folder === 'root') await showProfileInFolder(props.instance.path)
	else await openProfileFolder(props.instance.path, folder)
}

const openContent = async (type = '') => {
	const suffix = type ? `/projects/${type}` : ''
	await router.push(`/instance/${encodeURIComponent(props.instance.path)}${suffix}`)
}

const openSettings = async () => {
	await router.push({
		path: `/instance/${encodeURIComponent(props.instance.path)}`,
		query: { action: 'settings' },
	})
}

const exportProfile = async () => {
	await router.push({
		path: `/instance/${encodeURIComponent(props.instance.path)}`,
		query: { action: 'export' },
	})
}

const addContent = async () => {
	await router.push({
		path: `/browse/${props.instance.loader === 'vanilla' ? 'datapack' : 'mod'}`,
		query: { i: props.instance.path },
	})
}

defineExpose({
	play,
	stop,
	seeInstance,
	openFolder,
	openContent,
	openSettings,
	exportProfile,
	repair,
	addContent,
	instance: props.instance,
})

const currentEvent = ref(null)

const unlisten = await process_listener((e) => {
	if (e.profile_path_id === props.instance.path) {
		currentEvent.value = e.event
		if (e.event === 'finished') {
			playing.value = false
		}
	}
})

onMounted(() => checkProcess())
onUnmounted(() => unlisten())
</script>

<template>
	<template v-if="compact">
		<div
			class="card-shadow grid grid-cols-[1fr_auto] bg-bg-raised rounded-xl p-3 pl-4 gap-2 cursor-pointer hover:brightness-90 transition-colors"
			@click="seeInstance"
			@mouseenter="checkProcess"
		>
			<div class="h-full flex items-center font-bold text-contrast leading-normal">
				<span class="line-clamp-2">{{ instance.name }}</span>
			</div>
			<div class="flex items-center">
				<ButtonStyled v-if="playing" color="red" circular @mousehover="checkProcess">
					<button
						v-tooltip="$t('blockera.ui.9e253470c876')"
						@click="(e) => stop(e, 'InstanceCard')"
					>
						<StopCircleIcon />
					</button>
				</ButtonStyled>
				<ButtonStyled v-else-if="modLoading" color="standard" circular>
					<button v-tooltip="'Instance is loading...'" disabled>
						<SpinnerIcon class="animate-spin" />
					</button>
				</ButtonStyled>
				<ButtonStyled v-else :color="first ? 'brand' : 'standard'" circular>
					<button
						v-tooltip="$t('blockera.ui.5d12bd53552c')"
						@click="(e) => play(e, 'InstanceCard')"
						@mousehover="checkProcess"
					>
						<!-- Translate for optical centering -->
						<PlayIcon class="translate-x-[1px]" />
					</button>
				</ButtonStyled>
			</div>
			<div class="flex items-center col-span-2 gap-1 text-secondary font-semibold">
				<TimerIcon />
				<span class="text-sm">
					<template v-if="instance.last_played">
						{{ $t('blockera.ui.91d5082c1dc5') }}
						{{ formatRelativeTime(dayjs(instance.last_played).toISOString()) }}
					</template>
					<template v-else> {{ $t('blockera.ui.6e46a0f17494') }} </template>
				</span>
			</div>
		</div>
	</template>
	<div v-else>
		<div
			class="instance-library-card button-base bg-bg-raised p-4 rounded-xl flex gap-3 group"
			:style="{ '--instance-card-background': `url(${instanceBackgroundFor(instance.path)})` }"
			@click="seeInstance"
			@mouseenter="checkProcess"
		>
			<div class="relative z-[1] ml-auto flex items-center justify-center order-2">
				<div class="flex items-center justify-center">
					<ButtonStyled v-if="playing" size="large" color="red" circular>
						<button
							v-tooltip="$t('blockera.ui.9e253470c876')"
							:class="{ 'scale-100 opacity-100': playing }"
							class="transition-all scale-75 origin-bottom opacity-0 card-shadow"
							@click="(e) => stop(e, 'InstanceCard')"
							@mousehover="checkProcess"
						>
							<StopCircleIcon />
						</button>
					</ButtonStyled>
					<SpinnerIcon
						v-else-if="modLoading || installing"
						v-tooltip="modLoading ? 'Instance is loading...' : 'Installing...'"
						class="animate-spin w-8 h-8"
						tabindex="-1"
					/>
					<ButtonStyled v-else-if="!installed" size="large" color="brand" circular>
						<button
							v-tooltip="'Repair'"
							class="transition-all scale-75 group-hover:scale-100 group-focus-within:scale-100 origin-bottom opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 card-shadow"
							@click="(e) => repair(e)"
						>
							<DownloadIcon />
						</button>
					</ButtonStyled>
					<ButtonStyled v-else size="large" color="brand" circular>
						<button
							v-tooltip="$t('blockera.ui.5d12bd53552c')"
							class="transition-all scale-75 group-hover:scale-100 group-focus-within:scale-100 origin-bottom opacity-0 group-hover:opacity-100 group-focus-within:opacity-100 card-shadow"
							@click="(e) => play(e, 'InstanceCard')"
							@mousehover="checkProcess"
						>
							<PlayIcon class="translate-x-[2px]" />
						</button>
					</ButtonStyled>
				</div>
			</div>
			<div class="relative z-[1] flex flex-col gap-1 min-w-0 flex-1">
				<div v-if="libraryMeta" class="library-card-actions">
					<button
						type="button"
						:aria-label="libraryMeta.favorite ? 'Убрать из избранного' : 'Добавить в избранное'"
						:aria-pressed="!!libraryMeta.favorite"
						:class="{ favorite: libraryMeta.favorite }"
						@click.stop="$emit('favorite')"
					>
						{{ libraryMeta.favorite ? '★' : '☆' }}
					</button>
					<button type="button" @click.stop="$emit('organize')">Папка и теги</button>
				</div>
				<p class="m-0 text-md font-bold text-contrast leading-tight line-clamp-1">
					{{ instance.name }}
				</p>
				<div class="flex items-center col-span-3 gap-1 text-secondary font-semibold mt-auto">
					<GameIcon class="shrink-0" />
					<span class="text-sm capitalize">
						{{ instance.loader }} {{ instance.game_version }}
					</span>
				</div>
				<template v-if="libraryMeta">
					<small
						>{{
							libraryPlaytime(
								(instance.recent_time_played ?? 0) + (instance.submitted_time_played ?? 0),
							)
						}}
						в игре</small
					>
					<small class="library-world" :title="libraryMeta.world"
						>Последний мир · {{ libraryMeta.world }}</small
					>
					<small v-if="libraryMeta.folder">Папка · {{ libraryMeta.folder }}</small>
					<div v-if="libraryMeta.tags.length" class="library-tags">
						<span v-for="tag in libraryMeta.tags" :key="tag">{{ tag }}</span>
					</div>
				</template>
			</div>
		</div>
	</div>
</template>

<style scoped>
.library-card-actions,
.library-tags {
	display: flex;
	flex-wrap: wrap;
	gap: 0.4rem;
}
.library-card-actions button {
	background: #ffffff0a;
	color: var(--color-secondary);
	border: 1px solid #ffffff1c;
	border-radius: 0.4rem;
	padding: 0.2rem 0.45rem;
	cursor: pointer;
	font-size: 0.75rem;
}
.library-card-actions button.favorite {
	color: #e3b8ff;
	border-color: #c18aef88;
}
.library-tags span {
	background: #b371ec1c;
	border-radius: 0.3rem;
	padding: 0.15rem 0.4rem;
	font-size: 0.7rem;
}
.library-world {
	overflow: hidden;
	text-overflow: ellipsis;
	max-width: 20rem;
}

.instance-library-card {
	position: relative;
	isolation: isolate;
	overflow: hidden;
	min-height: 5.4rem;
	align-items: center;
}

.instance-library-card::before {
	content: '';
	position: absolute;
	z-index: 0;
	inset: 0;
	background:
		linear-gradient(
			90deg,
			rgba(8, 12, 20, 0.96) 0%,
			rgba(8, 12, 20, 0.78) 58%,
			rgba(8, 12, 20, 0.42) 100%
		),
		var(--instance-card-background) center / cover;
	transition:
		transform 220ms cubic-bezier(0.22, 1, 0.36, 1),
		filter 180ms ease;
}

.instance-library-card:hover::before {
	transform: scale(1.035);
	filter: saturate(1.12);
}

@media (prefers-reduced-motion: reduce) {
	.instance-library-card::before {
		transition: none;
	}
	.instance-library-card:hover::before {
		transform: none;
	}
}
</style>
