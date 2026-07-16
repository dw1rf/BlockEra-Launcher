<script setup lang="ts">
import {
	CheckIcon,
	ImageIcon,
	PaletteIcon,
	RotateCounterClockwiseIcon,
	SpinnerIcon,
} from '@modrinth/assets'
import { injectNotificationManager } from '@modrinth/ui'
import { open } from '@tauri-apps/plugin-dialog'
import { onMounted, onUnmounted, ref } from 'vue'

import {
	BACKGROUND_CHANGED_EVENT,
	customBackgroundFor,
	launcherBackgrounds,
	selectedBackgroundId,
	setCustomBackground,
	setSelectedBackground,
} from '@/helpers/instance-backgrounds'
import { saveCustomBackground } from '@/helpers/utils'

const props = withDefaults(
	defineProps<{
		scope: string
		compact?: boolean
		label?: string
	}>(),
	{ compact: false, label: 'Фон' },
)

const selectedId = ref(selectedBackgroundId(props.scope))
const customPreview = ref(customBackgroundFor(props.scope))
const choosingCustom = ref(false)
const { handleError } = injectNotificationManager()

function select(backgroundId: string) {
	setSelectedBackground(props.scope, backgroundId)
	selectedId.value = backgroundId
}

function sync(event: Event) {
	const detail = (event as CustomEvent<{ scope?: string }>).detail
	if (!detail?.scope || detail.scope === props.scope) {
		selectedId.value = selectedBackgroundId(props.scope)
		customPreview.value = customBackgroundFor(props.scope)
	}
}

async function chooseCustomBackground() {
	const sourcePath = await open({
		multiple: false,
		filters: [{ name: 'Изображение', extensions: ['png', 'jpg', 'jpeg', 'webp'] }],
	})
	if (!sourcePath || Array.isArray(sourcePath)) return

	choosingCustom.value = true
	try {
		const savedPath = await saveCustomBackground(sourcePath, props.scope)
		setCustomBackground(props.scope, savedPath)
		customPreview.value = customBackgroundFor(props.scope)
		selectedId.value = 'custom'
	} catch (error) {
		handleError(error)
	} finally {
		choosingCustom.value = false
	}
}

onMounted(() => window.addEventListener(BACKGROUND_CHANGED_EVENT, sync))
onUnmounted(() => window.removeEventListener(BACKGROUND_CHANGED_EVENT, sync))
</script>

<template>
	<details class="background-picker" :class="{ compact }">
		<summary v-tooltip="compact ? 'Сменить фон главной' : null">
			<PaletteIcon />
			<span>{{ label }}</span>
		</summary>
		<div class="background-panel">
			<div class="panel-heading">
				<div>
					<strong>Выберите фон</strong><small>Настройка сохраняется на этом компьютере</small>
				</div>
				<button
					v-tooltip="'Автоматический фон'"
					:class="{ active: selectedId === 'auto' }"
					@click="select('auto')"
				>
					<RotateCounterClockwiseIcon />
				</button>
			</div>
			<div class="background-grid">
				<button
					v-for="background in launcherBackgrounds"
					:key="background.id"
					class="background-option"
					:class="{ selected: selectedId === background.id }"
					@click="select(background.id)"
				>
					<span class="preview" :style="{ backgroundImage: `url(${background.src})` }"></span>
					<span class="option-copy"
						><strong>{{ background.label }}</strong
						><small>{{ background.description }}</small></span
					>
					<CheckIcon v-if="selectedId === background.id" />
				</button>
				<button
					class="background-option custom-option"
					:class="{ selected: selectedId === 'custom' }"
					:disabled="choosingCustom"
					@click="chooseCustomBackground"
				>
					<span
						class="preview custom-preview"
						:class="{ empty: !customPreview }"
						:style="customPreview ? { backgroundImage: `url(${customPreview})` } : undefined"
					>
						<SpinnerIcon v-if="choosingCustom" class="spin" />
						<ImageIcon v-else-if="!customPreview" />
					</span>
					<span class="option-copy">
						<strong>{{ customPreview ? 'Своя картинка' : 'Добавить свою картинку' }}</strong>
						<small>{{
							customPreview
								? 'Нажмите, чтобы заменить изображение'
								: 'PNG, JPG или WebP с компьютера'
						}}</small>
					</span>
					<CheckIcon v-if="selectedId === 'custom'" />
				</button>
			</div>
		</div>
	</details>
</template>

<style scoped lang="scss">
.background-picker {
	position: relative;
}
summary {
	display: inline-flex;
	align-items: center;
	gap: 0.55rem;
	min-height: 2.5rem;
	padding: 0 0.85rem;
	border: 1px solid rgba(216, 180, 254, 0.28);
	border-radius: 0.65rem;
	background: rgba(12, 17, 28, 0.78);
	color: #e9d5ff;
	font-size: 0.82rem;
	font-weight: 750;
	cursor: pointer;
	list-style: none;
}
summary::-webkit-details-marker {
	display: none;
}
summary svg {
	width: 1rem;
}
.background-panel {
	position: absolute;
	z-index: 30;
	top: calc(100% + 0.55rem);
	right: 0;
	width: min(34rem, calc(100vw - 5rem));
	padding: 0.9rem;
	border: 1px solid rgba(196, 181, 253, 0.22);
	border-radius: 0.9rem;
	background: rgba(7, 10, 18, 0.97);
	box-shadow: 0 24px 70px rgba(0, 0, 0, 0.48);
	backdrop-filter: blur(24px);
}
.panel-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
	margin-bottom: 0.7rem;
}
.panel-heading div {
	display: flex;
	flex-direction: column;
}
.panel-heading strong {
	color: #fff;
}
.panel-heading small,
.option-copy small {
	color: rgba(226, 232, 240, 0.56);
}
.panel-heading button {
	display: grid;
	place-items: center;
	width: 2.3rem;
	height: 2.3rem;
	padding: 0;
	border-radius: 0.55rem;
	background: rgba(255, 255, 255, 0.06);
}
.panel-heading button.active {
	color: #d8b4fe;
	border-color: rgba(192, 132, 252, 0.45);
}
.panel-heading svg {
	width: 1rem;
}
.background-grid {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 0.5rem;
}
.background-option {
	position: relative;
	display: grid;
	grid-template-columns: 4.7rem minmax(0, 1fr) auto;
	align-items: center;
	gap: 0.65rem;
	min-height: 4.25rem;
	padding: 0.4rem;
	border: 1px solid rgba(255, 255, 255, 0.07);
	border-radius: 0.7rem;
	background: rgba(255, 255, 255, 0.035);
	color: rgba(241, 245, 249, 0.88);
	text-align: left;
	cursor: pointer;
}
.background-option:hover,
.background-option.selected {
	border-color: rgba(192, 132, 252, 0.42);
	background: rgba(126, 34, 206, 0.14);
}
.preview {
	width: 4.7rem;
	height: 3.35rem;
	border-radius: 0.48rem;
	background-position: center;
	background-size: cover;
}
.custom-preview.empty {
	display: grid;
	place-items: center;
	border: 1px dashed rgba(192, 132, 252, 0.35);
	background: rgba(126, 34, 206, 0.1);
	color: #c084fc;
}
.custom-preview svg {
	width: 1.2rem;
}
.background-option:disabled {
	opacity: 0.65;
	cursor: wait;
}
.spin {
	animation: picker-spin 0.8s linear infinite;
}
@keyframes picker-spin {
	to {
		transform: rotate(360deg);
	}
}
.option-copy {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 0.2rem;
}
.option-copy strong {
	overflow: hidden;
	font-size: 0.77rem;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.option-copy small {
	font-size: 0.65rem;
	line-height: 1.25;
}
.background-option > svg {
	width: 1rem;
	margin-right: 0.25rem;
	color: #c084fc;
}
.compact summary {
	width: 2.65rem;
	height: 2.65rem;
	min-height: 0;
	justify-content: center;
	padding: 0;
	border-radius: 50%;
	background: rgba(5, 9, 17, 0.68);
	backdrop-filter: blur(14px);
}
.compact summary span {
	display: none;
}
@media (max-width: 720px) {
	.background-grid {
		grid-template-columns: 1fr;
	}
}
</style>
