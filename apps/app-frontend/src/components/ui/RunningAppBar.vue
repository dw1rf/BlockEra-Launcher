<template>
	<div class="running-app-bar" :class="{ compact: props.compact }">
		<div class="action-groups">
			<button
				v-if="activeLoadingBar"
				ref="infoButton"
				type="button"
				class="download-status-button"
				:aria-expanded="showCard"
				:aria-label="`Загрузка: ${loadingTitle(activeLoadingBar)}, ${loadingProgress(activeLoadingBar)}%`"
				:style="{
					'--download-progress': loadingProgress(activeLoadingBar) / 100,
				}"
				@click="toggleCard()"
			>
				<span class="download-status-icon" aria-hidden="true">
					<DownloadIcon />
					<span v-if="currentLoadingBars.length > 1" class="download-count">{{
						currentLoadingBars.length
					}}</span>
				</span>
				<span class="download-status-copy">
					<small>ЗАГРУЗКА</small>
					<strong>{{ loadingTitle(activeLoadingBar) }}</strong>
				</span>
				<span class="download-percent"
					>{{ loadingProgress(activeLoadingBar) }}%</span
				>
				<span class="download-track" aria-hidden="true"><span /></span>
			</button>
			<div v-if="!props.compact && offline" class="status">
				<UnplugIcon />
				<div class="running-text">
					<span> Offline </span>
				</div>
			</div>
			<div v-if="!props.compact && selectedProcess" class="status">
				<span class="circle running" />
				<div ref="profileButton" class="running-text">
					<router-link
						class="text-primary"
						:to="`/instance/${encodeURIComponent(selectedProcess.profile.path)}`"
					>
						{{ selectedProcess.profile.name }}
					</router-link>
					<div
						v-if="currentProcesses.length > 1"
						class="arrow button-base"
						:class="{ rotate: showProfiles }"
						@click="toggleProfiles()"
					>
						<DropdownIcon />
					</div>
				</div>
				<Button
					v-tooltip="'Stop instance'"
					icon-only
					class="icon-button stop"
					@click="stop(selectedProcess)"
				>
					<StopCircleIcon />
				</Button>
				<Button
					v-tooltip="'View logs'"
					icon-only
					class="icon-button"
					@click="goToTerminal()"
				>
					<TerminalSquareIcon />
				</Button>
			</div>
			<div v-else-if="!props.compact" class="status">
				<span class="circle stopped" />
				<span class="running-text"> No instances running </span>
			</div>
		</div>
		<transition name="download">
			<Card
				v-if="showCard === true && currentLoadingBars.length > 0"
				ref="card"
				class="info-card"
				role="status"
				aria-live="polite"
			>
				<div
					v-for="loadingBar in currentLoadingBars"
					:key="loadingBar.id"
					class="info-text"
				>
					<h3 class="info-title">
						{{ loadingTitle(loadingBar) }}
					</h3>
					<ProgressBar :progress="loadingProgress(loadingBar)" />
					<div class="row">
						{{ loadingProgress(loadingBar) }}%
						{{ loadingBar.message }}
					</div>
				</div>
			</Card>
		</transition>
		<transition name="download">
			<Card
				v-if="showProfiles === true && currentProcesses.length > 0"
				ref="profiles"
				class="profile-card"
			>
				<Button
					v-for="process in currentProcesses"
					:key="process.uuid"
					class="profile-button"
					@click="selectProcess(process)"
				>
					<div class="text">
						<span class="circle running" /> {{ process.profile.name }}
					</div>
					<Button
						v-tooltip="'Stop instance'"
						icon-only
						class="icon-button stop"
						@click.stop="stop(process)"
					>
						<StopCircleIcon />
					</Button>
					<Button
						v-tooltip="'View logs'"
						icon-only
						class="icon-button"
						@click.stop="goToTerminal(process.profile.path)"
					>
						<TerminalSquareIcon />
					</Button>
				</Button>
			</Card>
		</transition>
	</div>
</template>

<script setup>
import {
	DownloadIcon,
	DropdownIcon,
	StopCircleIcon,
	TerminalSquareIcon,
	UnplugIcon,
} from '@modrinth/assets'
import { Button, Card, injectNotificationManager } from '@modrinth/ui'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import ProgressBar from '@/components/ui/ProgressBar.vue'
import { trackEvent } from '@/helpers/analytics'
import { loading_listener, process_listener } from '@/helpers/events'
import {
	get_all as getRunningProcesses,
	kill as killProcess,
} from '@/helpers/process'
import { get_many } from '@/helpers/profile.js'
import { progress_bars_list } from '@/helpers/state.js'

const { handleError } = injectNotificationManager()
const props = defineProps({
	compact: {
		type: Boolean,
		default: false,
	},
})

const router = useRouter()
const card = ref(null)
const profiles = ref(null)
const infoButton = ref(null)
const profileButton = ref(null)
const showCard = ref(false)

const showProfiles = ref(false)

const currentProcesses = ref([])
const selectedProcess = ref()

const refresh = async () => {
	const processes = await getRunningProcesses().catch(handleError)
	const profiles = await get_many(processes.map((x) => x.profile_path)).catch(
		handleError,
	)

	currentProcesses.value = processes.map((x) => ({
		profile: profiles.find((prof) => x.profile_path === prof.path),
		...x,
	}))
	if (
		!selectedProcess.value ||
		!currentProcesses.value.includes(selectedProcess.value)
	) {
		selectedProcess.value = currentProcesses.value[0]
	}
}

await refresh()

const offline = ref(!navigator.onLine)
window.addEventListener('offline', () => {
	offline.value = true
})
window.addEventListener('online', () => {
	offline.value = false
})

const unlistenProcess = await process_listener(async () => {
	await refresh()
})

const stop = async (process) => {
	try {
		await killProcess(process.uuid).catch(handleError)

		trackEvent('InstanceStop', {
			loader: process.profile.loader,
			game_version: process.profile.game_version,
			source: 'AppBar',
		})
	} catch (e) {
		console.error(e)
	}
	await refresh()
}

const goToTerminal = (path) => {
	router.push(
		`/instance/${encodeURIComponent(path ?? selectedProcess.value.profile.path)}/logs`,
	)
}

const currentLoadingBars = ref([])
const activeLoadingBar = computed(() => currentLoadingBars.value[0])

const loadingProgress = (loadingBar) => {
	const current = Number(loadingBar?.current ?? 0)
	const total = Number(loadingBar?.total ?? 0)
	if (total <= 0) return 0
	return Math.min(100, Math.max(0, Math.floor((100 * current) / total)))
}

const loadingTitle = (loadingBar) =>
	loadingBar?.title || loadingBar?.message || 'Подготовка файлов'

const refreshInfo = async () => {
	const currentLoadingBarCount = currentLoadingBars.value.length
	currentLoadingBars.value = Object.values(
		await progress_bars_list().catch(handleError),
	)
		.map((x) => {
			if (x.bar_type.type === 'java_download') {
				x.title = 'Загрузка Java ' + x.bar_type.version
			}
			if (x.bar_type.profile_path) {
				x.title = x.bar_type.profile_path
			}
			if (x.bar_type.pack_name) {
				x.title = x.bar_type.pack_name
			}

			return x
		})
		.filter((bar) => bar?.bar_type?.type !== 'launcher_update')

	currentLoadingBars.value.sort((a, b) => {
		if (a.loading_bar_uuid < b.loading_bar_uuid) {
			return -1
		}
		if (a.loading_bar_uuid > b.loading_bar_uuid) {
			return 1
		}
		return 0
	})

	if (currentLoadingBars.value.length === 0) {
		showCard.value = false
	} else if (
		!props.compact &&
		currentLoadingBarCount < currentLoadingBars.value.length
	) {
		showCard.value = true
	}
}

await refreshInfo()
const unlistenLoading = await loading_listener(async () => {
	await refreshInfo()
})

const selectProcess = (process) => {
	selectedProcess.value = process
	showProfiles.value = false
}

const handleClickOutsideCard = (event) => {
	const elements = document.elementsFromPoint(event.clientX, event.clientY)
	if (
		card.value &&
		card.value.$el !== event.target &&
		!elements.includes(card.value.$el) &&
		infoButton.value &&
		!infoButton.value.contains(event.target)
	) {
		showCard.value = false
	}
}

const handleClickOutsideProfile = (event) => {
	const elements = document.elementsFromPoint(event.clientX, event.clientY)
	if (
		profiles.value &&
		profiles.value.$el !== event.target &&
		!elements.includes(profiles.value.$el) &&
		!profileButton.value.contains(event.target)
	) {
		showProfiles.value = false
	}
}

const toggleCard = async () => {
	showCard.value = !showCard.value
	showProfiles.value = false
	await refreshInfo()
}

const toggleProfiles = async () => {
	if (currentProcesses.value.length === 1) return
	showProfiles.value = !showProfiles.value
	showCard.value = false
}

onMounted(() => {
	window.addEventListener('click', handleClickOutsideCard)
	window.addEventListener('click', handleClickOutsideProfile)
})

onBeforeUnmount(() => {
	window.removeEventListener('click', handleClickOutsideCard)
	window.removeEventListener('click', handleClickOutsideProfile)
	unlistenProcess()
	unlistenLoading()
})
</script>

<style scoped lang="scss">
.running-app-bar {
	position: relative;
	display: flex;
	align-items: center;
	min-width: 0;
}

.action-groups {
	display: flex;
	flex-direction: row;
	align-items: center;
	gap: var(--gap-md);
}

.download-status-button {
	--download-progress: 0;
	position: relative;
	display: flex;
	align-items: center;
	gap: 0.65rem;
	width: min(15rem, 24vw);
	min-width: 10rem;
	height: 2.75rem;
	overflow: hidden;
	padding: 0.42rem 0.72rem;
	border: 1px solid rgba(177, 94, 255, 0.28);
	border-radius: 0.75rem;
	background: linear-gradient(
		135deg,
		rgba(126, 46, 208, 0.2),
		rgba(26, 19, 43, 0.78)
	);
	box-shadow: 0 8px 24px rgba(70, 20, 115, 0.16);
	color: #f5effb;
	font: inherit;
	text-align: left;
	cursor: pointer;
	transition:
		transform 180ms cubic-bezier(0.22, 1, 0.36, 1),
		border-color 180ms ease,
		background-color 180ms ease;
}

.download-status-button:hover {
	transform: translateY(-1px);
	border-color: rgba(198, 126, 255, 0.5);
}

.download-status-icon {
	position: relative;
	display: grid;
	flex: 0 0 auto;
	width: 1.8rem;
	height: 1.8rem;
	place-items: center;
	border-radius: 0.55rem;
	background: rgba(169, 79, 244, 0.15);
	color: #d9b5ff;
}

.download-status-icon svg {
	width: 1rem;
	height: 1rem;
	animation: download-breathe 1.4s ease-in-out infinite;
}

.download-count {
	position: absolute;
	top: -0.3rem;
	right: -0.3rem;
	display: grid;
	min-width: 1rem;
	height: 1rem;
	padding-inline: 0.18rem;
	place-items: center;
	border: 1px solid #17101f;
	border-radius: 999px;
	background: #9c42ef;
	color: white;
	font-size: 0.52rem;
	font-weight: 850;
}

.download-status-copy {
	display: flex;
	min-width: 0;
	flex: 1;
	flex-direction: column;
}

.download-status-copy small {
	color: #b96dff;
	font-size: 0.5rem;
	font-weight: 850;
	letter-spacing: 0.13em;
}

.download-status-copy strong {
	overflow: hidden;
	margin-top: 0.1rem;
	font-size: 0.72rem;
	font-weight: 750;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.download-percent {
	flex: 0 0 auto;
	color: #d9b5ff;
	font-size: 0.67rem;
	font-weight: 800;
}

.download-track {
	position: absolute;
	right: 0.5rem;
	bottom: 0.22rem;
	left: 0.5rem;
	height: 2px;
	overflow: hidden;
	border-radius: 999px;
	background: rgba(255, 255, 255, 0.09);
}

.download-track span {
	display: block;
	width: 100%;
	height: 100%;
	border-radius: inherit;
	background: linear-gradient(90deg, #a947f2, #d095ff);
	box-shadow: 0 0 10px rgba(184, 91, 255, 0.7);
	transform: scaleX(var(--download-progress));
	transform-origin: left;
	transition: transform 220ms cubic-bezier(0.4, 0, 0.2, 1);
}

.compact .download-status-button {
	width: 2.75rem;
	min-width: 2.75rem;
	padding-inline: 0.45rem;
	justify-content: center;
}

.compact .download-status-copy,
.compact .download-percent,
.compact .download-track {
	display: none;
}

.compact .download-status-icon {
	background:
		linear-gradient(rgba(20, 14, 34, 0.96), rgba(20, 14, 34, 0.96)) padding-box,
		conic-gradient(
			#a947f2 calc(var(--download-progress) * 1turn),
			rgba(255, 255, 255, 0.1) 0
		) border-box;
	border: 2px solid transparent;
}

@keyframes download-breathe {
	50% {
		transform: translateY(2px);
		opacity: 0.72;
	}
}

.arrow {
	transition: transform 0.2s ease-in-out;
	display: flex;
	align-items: center;
	&.rotate {
		transform: rotate(180deg);
	}
}

.status {
	display: flex;
	flex-direction: row;
	align-items: center;
	gap: 0.5rem;
	border-radius: var(--radius-md);
	border: 1px solid var(--color-divider);
	padding: var(--gap-sm) var(--gap-lg);
}

.running-text {
	display: flex;
	flex-direction: row;
	gap: var(--gap-xs);
	white-space: nowrap;
	overflow: hidden;
	-webkit-user-select: none; /* Safari */
	-ms-user-select: none; /* IE 10 and IE 11 */
	user-select: none;

	&.clickable:hover {
		cursor: pointer;
	}
}

.circle {
	width: 0.5rem;
	height: 0.5rem;
	border-radius: 50%;
	display: inline-block;
	margin-right: 0.25rem;

	&.running {
		background-color: var(--color-brand);
	}

	&.stopped {
		background-color: var(--color-base);
	}
}

.icon-button {
	background-color: rgba(0, 0, 0, 0);
	box-shadow: none;
	width: 1.25rem !important;
	height: 1.25rem !important;

	svg {
		min-width: 1.25rem;
	}

	&.stop {
		color: var(--color-red);
	}
}

.info-card {
	position: absolute;
	top: calc(100% + 0.65rem);
	right: 0;
	z-index: 9;
	width: min(20rem, calc(100vw - 1.5rem));
	max-height: min(24rem, calc(100vh - 6rem));
	padding: 0.85rem;
	border-radius: 1rem;
	background: rgba(10, 12, 21, 0.96);
	box-shadow:
		0 24px 65px rgba(0, 0, 0, 0.5),
		0 0 35px rgba(126, 45, 205, 0.1);
	backdrop-filter: blur(22px);
	display: flex;
	flex-direction: column;
	gap: 1rem;
	overflow: auto;
	transition:
		opacity 180ms ease,
		transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
	border: 1px solid rgba(177, 94, 255, 0.26);

	&.hidden {
		transform: translateY(-100%);
	}
}

.loading-option {
	display: flex;
	flex-direction: row;
	align-items: center;
	gap: 0.5rem;
	margin: 0;
	padding: 0;

	:hover {
		background-color: var(--color-raised-bg-hover);
	}
}

.loading-text {
	display: flex;
	flex-direction: column;
	margin: 0;
	padding: 0;

	.row {
		display: flex;
		flex-direction: row;
		align-items: center;
		gap: 0.5rem;
	}
}

.loading-icon {
	width: 2.25rem;
	height: 2.25rem;
	display: block;

	:deep(svg) {
		left: 1rem;
		width: 2.25rem;
		height: 2.25rem;
	}
}

.download-enter-active,
.download-leave-active {
	transition:
		opacity 180ms ease,
		transform 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

.download-enter-from,
.download-leave-to {
	opacity: 0;
	transform: translateY(-6px) scale(0.98);
}

.progress-bar {
	width: 100%;
}

.info-text {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: 0.55rem;
	margin: 0;
	padding: 0.75rem;
	border: 1px solid rgba(255, 255, 255, 0.06);
	border-radius: 0.75rem;
	background: rgba(255, 255, 255, 0.035);
}

.info-title {
	max-width: 100%;
	overflow: hidden;
	margin: 0;
	color: #f6effc;
	font-size: 0.82rem;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.info-text .row {
	color: #9da3b1;
	font-size: 0.68rem;
}

.profile-button {
	display: flex;
	flex-direction: row;
	align-items: center;
	gap: var(--gap-sm);
	width: 100%;
	background-color: var(--color-raised-bg);
	box-shadow: none;

	.text {
		margin-right: auto;
	}
}

.profile-card {
	position: absolute;
	top: calc(100% + 0.65rem);
	right: 0;
	z-index: 9;
	background-color: var(--color-raised-bg);
	box-shadow: var(--shadow-raised);
	display: flex;
	flex-direction: column;
	overflow: auto;
	transition: all 0.2s ease-in-out;
	border: 1px solid var(--color-divider);
	padding: var(--gap-md);

	&.hidden {
		transform: translateY(-100%);
	}
}

.link {
	display: flex;
	flex-direction: row;
	align-items: center;
	gap: var(--gap-sm);
	margin: 0;
	color: var(--color-text);
	text-decoration: none;
}

@media (max-width: 800px) {
	.download-status-button {
		width: 2.75rem;
		min-width: 2.75rem;
		padding-inline: 0.45rem;
		justify-content: center;
	}

	.download-status-copy,
	.download-percent {
		display: none;
	}
}

@media (prefers-reduced-motion: reduce) {
	.download-status-button,
	.download-track span,
	.download-enter-active,
	.download-leave-active {
		transition-duration: 1ms;
	}

	.download-status-icon svg {
		animation: none;
	}
}
</style>
