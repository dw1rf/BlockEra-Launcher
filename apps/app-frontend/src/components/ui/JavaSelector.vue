<template>
	<JavaDetectionModal ref="detectJavaModal" @submit="(val) => emit('update:modelValue', val)" />
	<div class="toggle-setting" :class="{ compact }">
		<input
			autocomplete="off"
			:disabled="props.disabled"
			:value="props.modelValue ? props.modelValue.path : ''"
			type="text"
			class="installation-input"
			:placeholder="placeholder ?? '/path/to/java'"
			@input="
				(val) => {
					emit('update:modelValue', {
						...props.modelValue,
						path: val.target.value,
					})
				}
			"
		/>
		<span class="installation-buttons">
			<Button
				v-if="props.version"
				:disabled="props.disabled || installingJava"
				@click="reinstallJava"
			>
				<DownloadIcon />
				{{ installingJava ? 'Устанавливаем…' : 'Установить рекомендуемую' }}
			</Button>
			<Button :disabled="props.disabled" @click="autoDetect">
				<SearchIcon />
				Найти
			</Button>
			<Button :disabled="props.disabled" @click="handleJavaFileInput()">
				<FolderSearchIcon />
				Выбрать
			</Button>
			<Button v-if="testingJava" disabled> Проверяем… </Button>
			<Button v-else-if="testingJavaSuccess === true">
				<CheckIcon class="test-success" />
				Работает
			</Button>
			<Button v-else-if="testingJavaSuccess === false">
				<XIcon class="test-fail" />
				Ошибка
			</Button>
			<Button v-else :disabled="props.disabled" @click="testJava">
				<PlayIcon />
				Проверить
			</Button>
		</span>
	</div>
</template>

<script setup>
import {
	CheckIcon,
	DownloadIcon,
	FolderSearchIcon,
	PlayIcon,
	SearchIcon,
	XIcon,
} from '@modrinth/assets'
import { Button, injectNotificationManager } from '@modrinth/ui'
import { open } from '@tauri-apps/plugin-dialog'
import { onBeforeUnmount, ref } from 'vue'

import JavaDetectionModal from '@/components/ui/JavaDetectionModal.vue'
import { trackEvent } from '@/helpers/analytics'
import { auto_install_java, find_filtered_jres, get_jre, test_jre } from '@/helpers/jre.js'

const { handleError } = injectNotificationManager()

const props = defineProps({
	version: {
		type: Number,
		required: false,
		default: null,
	},
	modelValue: {
		type: Object,
		default: () => ({
			path: '',
			version: '',
		}),
	},
	disabled: {
		type: Boolean,
		required: false,
		default: false,
	},
	placeholder: {
		type: String,
		required: false,
		default: null,
	},
	compact: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['update:modelValue'])

const testingJava = ref(false)
const testingJavaSuccess = ref(null)
let testResetTimer

const installingJava = ref(false)

async function testJava() {
	testingJava.value = true
	try {
		testingJavaSuccess.value = await test_jre(
			props.modelValue ? props.modelValue.path : '',
			props.version,
		)
		trackEvent('JavaTest', {
			path: props.modelValue ? props.modelValue.path : '',
			success: testingJavaSuccess.value,
		})
	} catch (error) {
		testingJavaSuccess.value = false
		handleError(error)
	} finally {
		testingJava.value = false
		clearTimeout(testResetTimer)
		testResetTimer = setTimeout(() => {
			testingJavaSuccess.value = null
		}, 2000)
	}
}

async function handleJavaFileInput() {
	try {
		const filePath = await open()
		if (!filePath) return
		let result = await get_jre(filePath.path ?? filePath)
		if (!result) {
			result = {
				path: filePath.path ?? filePath,
				version: String(props.version ?? ''),
				architecture: 'x86',
			}
		}

		trackEvent('JavaManualSelect', {
			version: props.version,
		})

		emit('update:modelValue', result)
	} catch (error) {
		handleError(error)
	}
}

const detectJavaModal = ref(null)
async function autoDetect() {
	if (!props.compact) {
		detectJavaModal.value.show(props.version, props.modelValue)
	} else {
		const versions =
			(await find_filtered_jres(props.version).catch((error) => {
				handleError(error)
				return []
			})) ?? []
		if (versions.length > 0) {
			emit('update:modelValue', versions[0])
		}
	}
}

async function reinstallJava() {
	installingJava.value = true
	try {
		const path = await auto_install_java(props.version)
		let result = await get_jre(path)

		if (!result) {
			result = {
				path,
				version: props.version.toString(),
				architecture: 'x86',
			}
		}

		trackEvent('JavaReInstall', { path, version: props.version })
		emit('update:modelValue', result)
	} catch (error) {
		handleError(error)
	} finally {
		installingJava.value = false
	}
}

onBeforeUnmount(() => clearTimeout(testResetTimer))
</script>

<style lang="scss" scoped>
.installation-input {
	width: 100% !important;
	flex-grow: 1;
}

.toggle-setting {
	display: flex;
	flex-wrap: wrap;
	flex-direction: row;
	justify-content: space-between;
	align-items: center;
	gap: 0.5rem;

	&.compact {
		flex-wrap: wrap;
	}
}

.installation-buttons {
	display: flex;
	flex-direction: row;
	align-items: center;
	gap: 0.5rem;
	margin: 0;

	.btn {
		width: max-content;
	}
}

.test-success {
	color: var(--color-green);
}

.test-fail {
	color: var(--color-red);
}
</style>
