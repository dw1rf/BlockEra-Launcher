<template>
	<div ref="root" class="update-center-root">
		<button
			ref="trigger"
			v-tooltip="'Центр обновлений'"
			class="update-center-trigger"
			type="button"
			aria-label="Открыть центр обновлений"
			aria-controls="blockera-update-center-dialog"
			:aria-expanded="open"
			@click="toggleCenter"
		>
			<DownloadIcon />
			<span v-if="updateState">1</span>
		</button>
	</div>
	<Teleport to="#teleports">
		<transition name="update-popover">
			<section
				v-if="open"
				id="blockera-update-center-dialog"
				ref="panel"
				class="update-center-popover"
				role="dialog"
				aria-modal="false"
				aria-labelledby="blockera-update-center-title"
				tabindex="-1"
				:style="popoverStyle"
			>
				<header>
					<div>
						<span>ЦЕНТР ОБНОВЛЕНИЙ</span>
						<h3 id="blockera-update-center-title">BlockEra</h3>
					</div>
					<button
						type="button"
						:aria-label="$t('blockera.ui.bbfa773e5a63')"
						@click="closeCenter(true)"
					>
						<XIcon />
					</button>
				</header>

				<nav class="update-tabs" role="tablist" aria-label="Разделы центра обновлений">
					<button
						role="tab"
						:aria-selected="activeTab === 'updates'"
						:class="{ active: activeTab === 'updates' }"
						@click="activeTab = 'updates'"
					>
						Обновления
					</button>
					<button
						role="tab"
						:aria-selected="activeTab === 'notes'"
						:class="{ active: activeTab === 'notes' }"
						@click="activeTab = 'notes'"
					>
						Что нового
					</button>
				</nav>

				<div v-if="activeTab === 'updates'" class="update-tab-panel">
					<div class="launcher-update" :class="{ available: updateState }">
						<div class="launcher-update-copy">
							<span>ЛАУНЧЕР</span>
							<strong v-if="updateState">Доступна версия {{ availableUpdate?.version }}</strong>
							<strong v-else-if="checkingState">Проверяем обновление…</strong>
							<strong v-else-if="updateError">Установлена версия {{ currentVersion }}</strong>
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
					<footer>
						<button
							type="button"
							:disabled="checkingState || installState"
							@click="checkLauncherUpdate"
						>
							<RefreshCwIcon /> Проверить снова
						</button>
					</footer>
				</div>

				<div v-else class="update-tab-panel release-notes-panel">
					<article v-for="note in releaseNotes" :key="note.version" class="release-note">
						<header class="release-note-heading">
							<div>
								<span>ВЕРСИЯ</span>
								<h4>{{ note.version }}</h4>
							</div>
							<time>{{ note.date }}</time>
						</header>
						<section v-if="note.highlights.length">
							<strong>Новое</strong>
							<ul>
								<li v-for="item in note.highlights" :key="item">{{ item }}</li>
							</ul>
						</section>
						<section v-if="note.fixes.length">
							<strong>Исправления</strong>
							<ul>
								<li v-for="item in note.fixes" :key="item">{{ item }}</li>
							</ul>
						</section>
						<section v-if="note.improvements.length">
							<strong>Улучшения</strong>
							<ul>
								<li v-for="item in note.improvements" :key="item">{{ item }}</li>
							</ul>
						</section>
					</article>
					<a
						class="boosty-card"
						href="https://boosty.to/blockera/donate"
						target="_blank"
						rel="noopener noreferrer"
					>
						<div>
							<strong>Поддержать BlockEra</strong
							><span>Помогите развитию лаунчера — все функции останутся бесплатными.</span>
						</div>
						<b>Boosty</b>
					</a>
				</div>
			</section>
		</transition>
	</Teleport>
</template>

<script setup lang="ts">
import { DownloadIcon, RefreshCwIcon, XIcon } from '@modrinth/assets'
import { getVersion } from '@tauri-apps/api/app'
import { type CSSProperties, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { releaseNotes } from '@/data/release-notes'
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

const root = ref<HTMLElement>()
const trigger = ref<HTMLButtonElement>()
const panel = ref<HTMLElement>()
const open = ref(false)
const openedFromTrigger = ref(false)
const popoverStyle = ref<CSSProperties>({})
const activeTab = ref<'updates' | 'notes'>('updates')
const currentVersion = ref('')

async function installUpdate() {
	try {
		await installLauncherUpdate()
	} catch (error) {
		if (!updateError.value) updateError.value = formatUpdaterError(error)
	}
}

function positionPopover() {
	if (!trigger.value) return
	const margin = 12
	const gap = 10
	const triggerBounds = trigger.value.getBoundingClientRect()
	const width = Math.min(430, window.innerWidth - margin * 2)
	const left = Math.min(
		Math.max(margin, triggerBounds.right - width),
		window.innerWidth - width - margin,
	)
	const top = triggerBounds.bottom + gap
	const maxHeight = Math.max(240, window.innerHeight - top - margin)
	popoverStyle.value = {
		left: `${Math.round(left)}px`,
		top: `${Math.round(top)}px`,
		width: `${Math.round(width)}px`,
		maxHeight: `${Math.round(maxHeight)}px`,
	}
}

async function closeCenter(restoreFocus = false) {
	if (!open.value) return
	open.value = false
	await nextTick()
	if (restoreFocus && openedFromTrigger.value) trigger.value?.focus({ preventScroll: true })
	openedFromTrigger.value = false
}

function toggleCenter() {
	if (open.value) {
		void closeCenter(true)
		return
	}
	openedFromTrigger.value = true
	positionPopover()
	open.value = true
}

function handleOutside(event: MouseEvent) {
	const target = event.target as Node
	if (
		open.value &&
		root.value &&
		!root.value.contains(target) &&
		panel.value &&
		!panel.value.contains(target)
	) {
		void closeCenter(false)
	}
}

function handleKey(event: KeyboardEvent) {
	if (event.key === 'Escape' && open.value) {
		event.stopPropagation()
		void closeCenter(true)
	}
}

watch(open, async (isOpen) => {
	if (!isOpen) return
	await nextTick()
	positionPopover()
	if (openedFromTrigger.value) panel.value?.focus({ preventScroll: true })
})

onMounted(async () => {
	document.addEventListener('click', handleOutside)
	document.addEventListener('keydown', handleKey)
	window.addEventListener('resize', positionPopover)
	window.addEventListener('scroll', positionPopover, true)
	currentVersion.value = await getVersion()
	const seenKey = 'blockera-release-notes-seen'
	if (
		releaseNotes.some((note) => note.version === currentVersion.value) &&
		localStorage.getItem(seenKey) !== currentVersion.value
	) {
		activeTab.value = 'notes'
		positionPopover()
		open.value = true
		localStorage.setItem(seenKey, currentVersion.value)
	}
	void checkLauncherUpdate()
})

onBeforeUnmount(() => {
	document.removeEventListener('click', handleOutside)
	document.removeEventListener('keydown', handleKey)
	window.removeEventListener('resize', positionPopover)
	window.removeEventListener('scroll', positionPopover, true)
})
</script>

<style scoped lang="scss">
.update-center-root {
	position: relative;
}
.update-center-trigger {
	position: relative;
	width: 44px;
	height: 44px;
	display: grid;
	place-items: center;
	color: #d8b3ff;
	background: rgba(255, 255, 255, 0.035);
	border: 1px solid rgba(173, 92, 255, 0.18);
	border-radius: 13px;
	cursor: pointer;
}
.update-center-trigger:hover {
	background: rgba(145, 63, 224, 0.14);
	border-color: rgba(183, 103, 255, 0.4);
}
.update-center-trigger svg {
	width: 19px;
}
.update-center-trigger > span {
	position: absolute;
	min-width: 17px;
	height: 17px;
	right: -5px;
	top: -5px;
	display: grid;
	place-items: center;
	color: white;
	background: #9c42ef;
	border: 2px solid #0b0e16;
	border-radius: 999px;
	font-size: 9px;
	font-weight: 850;
}
.update-center-popover {
	position: fixed;
	top: calc(4.75rem + 10px);
	right: 12px;
	z-index: 1;
	display: grid;
	grid-template-rows: auto auto minmax(0, 1fr);
	width: min(430px, calc(100vw - 24px));
	max-height: calc(100vh - 4.75rem - 22px);
	box-sizing: border-box;
	overflow: hidden;
	padding: 15px;
	color: #f7f4fb;
	background: var(--blockera-glass-surface-overlay, rgba(11, 14, 23, 0.9));
	border: 1px solid var(--blockera-glass-border-active, rgba(177, 94, 255, 0.28));
	border-radius: 18px;
	box-shadow:
		inset 0 1px var(--blockera-glass-highlight, rgba(255, 255, 255, 0.09)),
		0 25px 70px rgba(0, 0, 0, 0.55);
	backdrop-filter: blur(var(--blockera-glass-blur-strong, 22px)) saturate(135%);
	outline: none;
}
.update-center-popover > header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
}
.update-center-popover > header div {
	display: flex;
	flex-direction: column;
}
.update-center-popover > header span,
.release-note-heading span {
	color: #b96dff;
	font-size: 9px;
	font-weight: 850;
	letter-spacing: 0.13em;
}
.update-center-popover h3 {
	margin: 3px 0 0;
	font-size: 18px;
}
.update-center-popover button {
	font: inherit;
	color: inherit;
	border: 0;
	cursor: pointer;
}
.update-center-popover > header button {
	width: 32px;
	height: 32px;
	display: grid;
	place-items: center;
	background: rgba(255, 255, 255, 0.045);
	border-radius: 9px;
}
.update-center-popover > header svg {
	width: 16px;
}
.update-tabs {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 5px;
	margin-top: 12px;
	padding: 4px;
	background: rgba(255, 255, 255, 0.035);
	border-radius: 11px;
}
.update-tabs button {
	padding: 8px;
	color: #9399a7;
	background: transparent;
	border-radius: 8px;
	font-size: 11px;
	font-weight: 750;
}
.update-tabs button.active {
	color: #f2e8ff;
	background: rgba(145, 59, 230, 0.2);
	box-shadow: inset 0 0 0 1px rgba(190, 111, 255, 0.2);
}
.update-tab-panel {
	margin-top: 11px;
	min-height: 0;
	overflow-y: auto;
	overflow-x: hidden;
	overscroll-behavior: contain;
	scrollbar-gutter: stable;
}
.launcher-update {
	padding: 12px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	background: rgba(255, 255, 255, 0.035);
	border: 1px solid rgba(255, 255, 255, 0.07);
	border-radius: 12px;
}
.launcher-update.available {
	background: linear-gradient(135deg, rgba(141, 57, 231, 0.24), rgba(89, 31, 158, 0.1));
	border-color: rgba(190, 111, 255, 0.32);
}
.launcher-update-copy {
	min-width: 0;
	display: flex;
	flex-direction: column;
}
.launcher-update-copy > span {
	color: #bd76ff;
	font-size: 9px;
	font-weight: 850;
	letter-spacing: 0.12em;
}
.launcher-update-copy strong {
	margin-top: 3px;
	font-size: 13px;
}
.launcher-update-copy small {
	max-width: 260px;
	margin-top: 3px;
	overflow: hidden;
	color: #9ba1ae;
	font-size: 10px;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.install-launcher-update {
	flex: none;
	padding: 8px 10px;
	display: flex;
	align-items: center;
	gap: 6px;
	color: white !important;
	background: linear-gradient(135deg, #973ef0, #6822c9);
	border-radius: 9px;
	font-size: 10px !important;
	font-weight: 800 !important;
}
.install-launcher-update svg {
	width: 14px;
}
.spin {
	animation: update-spin 900ms linear infinite;
}
@keyframes update-spin {
	to {
		transform: rotate(360deg);
	}
}
.release-notes-panel {
	display: flex;
	flex-direction: column;
	gap: 6px;
}
.update-tab-panel > footer {
	margin-top: 11px;
}
.update-tab-panel > footer button {
	padding: 9px 11px;
	display: flex;
	align-items: center;
	gap: 6px;
	color: #aeb3bf;
	background: rgba(255, 255, 255, 0.045);
	border-radius: 9px;
	font-size: 11px;
}
.update-tab-panel > footer svg {
	width: 14px;
}
.update-center-popover button:disabled {
	opacity: 0.5;
	cursor: wait;
}
.update-error {
	margin: 10px 0 0;
	padding: 9px;
	color: #ff9bb2;
	background: rgba(255, 70, 110, 0.08);
	border-radius: 9px;
	font-size: 10px;
	white-space: pre-wrap;
	word-break: break-word;
}
.release-note {
	padding: 12px;
	border: 1px solid rgba(255, 255, 255, 0.07);
	border-radius: 13px;
	background: rgba(255, 255, 255, 0.03);
}
.release-note-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
}
.release-note-heading div {
	display: flex;
	flex-direction: column;
}
.release-note-heading h4 {
	margin: 2px 0 0;
	font-size: 15px;
}
.release-note-heading time {
	color: #858c9a;
	font-size: 10px;
}
.release-note section {
	margin-top: 10px;
}
.release-note section > strong {
	color: #dfc5ff;
	font-size: 11px;
}
.release-note ul {
	margin: 5px 0 0;
	padding-left: 17px;
	color: #aeb3bf;
	font-size: 10px;
	line-height: 1.45;
}
.boosty-card {
	margin-top: 4px;
	padding: 12px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12px;
	color: inherit;
	background: linear-gradient(135deg, rgba(145, 57, 237, 0.24), rgba(103, 33, 200, 0.12));
	border: 1px solid rgba(196, 130, 255, 0.26);
	border-radius: 13px;
	text-decoration: none;
}
.boosty-card div {
	display: flex;
	flex-direction: column;
}
.boosty-card strong {
	font-size: 12px;
}
.boosty-card span {
	margin-top: 3px;
	color: #9da3b1;
	font-size: 10px;
}
.boosty-card b {
	padding: 7px 9px;
	color: white;
	background: #f15f2c;
	border-radius: 8px;
	font-size: 10px;
}
.update-popover-enter-active,
.update-popover-leave-active {
	transition:
		opacity 170ms ease,
		transform 170ms ease;
}
.update-popover-enter-from,
.update-popover-leave-to {
	opacity: 0;
	transform: translateY(-7px) scale(0.98);
}
@media (prefers-reduced-motion: reduce) {
	.update-popover-enter-active,
	.update-popover-leave-active {
		transition-duration: 1ms;
	}
}
</style>
