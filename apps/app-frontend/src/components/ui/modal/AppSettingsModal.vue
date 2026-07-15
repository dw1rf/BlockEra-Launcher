<script setup lang="ts">
import {
	BlockEraLogo,
	CoffeeIcon,
	DownloadIcon,
	GameIcon,
	GaugeIcon,
	LanguagesIcon,
	PaintbrushIcon,
	ReportIcon,
	SettingsIcon,
	ShieldIcon,
	SpinnerIcon,
} from '@modrinth/assets'
import {
	Button,
	commonMessages,
	defineMessage,
	defineMessages,
	ProgressBar,
	TabbedModal,
	useVIntl,
} from '@modrinth/ui'
import { getVersion } from '@tauri-apps/api/app'
import { platform as getOsPlatform, version as getOsVersion } from '@tauri-apps/plugin-os'
import { computed, ref, watch } from 'vue'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import AppearanceSettings from '@/components/ui/settings/AppearanceSettings.vue'
import DefaultInstanceSettings from '@/components/ui/settings/DefaultInstanceSettings.vue'
import FeatureFlagSettings from '@/components/ui/settings/FeatureFlagSettings.vue'
import JavaSettings from '@/components/ui/settings/JavaSettings.vue'
import LanguageSettings from '@/components/ui/settings/LanguageSettings.vue'
import PrivacySettings from '@/components/ui/settings/PrivacySettings.vue'
import ResourceManagementSettings from '@/components/ui/settings/ResourceManagementSettings.vue'
import { get, set } from '@/helpers/settings.ts'
import {
	availableUpdate,
	checkingState,
	checkLauncherUpdate,
	formatUpdaterError,
	installLauncherUpdate,
	installState,
	updateError,
	updateState,
} from '@/helpers/update.js'
import { injectAppUpdateDownloadProgress } from '@/providers/download-progress.ts'
import { useTheming } from '@/store/state'

const updateModalView = ref(null)

const initUpdateModal = async () => {
	updateModalView.value.show()
	await checkLauncherUpdate()
}

const initDownload = async () => {
	try {
		await installLauncherUpdate()
	} catch (error) {
		if (!updateError.value) updateError.value = formatUpdaterError(error)
	}
}

const themeStore = useTheming()

const { formatMessage } = useVIntl()

const devModeCounter = ref(0)

const developerModeEnabled = defineMessage({
	id: 'app.settings.developer-mode-enabled',
	defaultMessage: 'Developer mode enabled.',
})

const tabs = [
	{
		name: defineMessage({
			id: 'blockera.settings.tabs.interface',
			defaultMessage: 'Интерфейс',
		}),
		icon: PaintbrushIcon,
		content: AppearanceSettings,
	},
	{
		name: defineMessage({
			id: 'blockera.settings.tabs.language',
			defaultMessage: 'Язык',
		}),
		icon: LanguagesIcon,
		content: LanguageSettings,
		badge: commonMessages.beta,
	},
	{
		name: defineMessage({
			id: 'blockera.settings.tabs.privacy',
			defaultMessage: 'Конфиденциальность',
		}),
		icon: ShieldIcon,
		content: PrivacySettings,
	},
	{
		name: defineMessage({
			id: 'blockera.settings.tabs.java',
			defaultMessage: 'Java',
		}),
		icon: CoffeeIcon,
		content: JavaSettings,
	},
	{
		name: defineMessage({
			id: 'blockera.settings.tabs.game-launch',
			defaultMessage: 'Запуск игры',
		}),
		icon: GameIcon,
		content: DefaultInstanceSettings,
	},
	{
		name: defineMessage({
			id: 'blockera.settings.tabs.storage',
			defaultMessage: 'Файлы и загрузки',
		}),
		icon: GaugeIcon,
		content: ResourceManagementSettings,
	},
	{
		name: defineMessage({
			id: 'app.settings.tabs.feature-flags',
			defaultMessage: 'Feature flags',
		}),
		icon: ReportIcon,
		content: FeatureFlagSettings,
		developerOnly: true,
	},
]

const modal = ref()

function show() {
	modal.value.show()
}

const isOpen = computed(() => modal.value?.isOpen)

defineExpose({ show, isOpen })

const { progress, version: downloadingVersion } = injectAppUpdateDownloadProgress()

const version = await getVersion()
const osPlatform = getOsPlatform()
const osVersion = getOsVersion()
const settings = ref(await get())

watch(
	settings,
	async () => {
		await set(settings.value)
	},
	{ deep: true },
)

function devModeCount() {
	devModeCounter.value++
	if (devModeCounter.value > 5) {
		themeStore.devMode = !themeStore.devMode
		settings.value.developer_mode = !!themeStore.devMode
		devModeCounter.value = 0

		if (!themeStore.devMode && tabs[modal.value.selectedTab].developerOnly) {
			modal.value.setTab(0)
		}
	}
}

const messages = defineMessages({
	downloading: {
		id: 'app.settings.downloading',
		defaultMessage: 'Downloading v{version}',
	},
})
</script>
<template>
	<ModalWrapper
		ref="modal"
		class="settings-cinematic"
		max-width="72rem"
		width="min(72rem, calc(100vw - 3rem))"
	>
		<template #title>
			<div class="settings-title">
				<span class="settings-title-icon"><SettingsIcon /></span>
				<span><strong>Настройки BlockEra Launcher</strong><small>Персонализируйте запуск, Java и ресурсы игры</small></span>
			</div>
		</template>

		<TabbedModal :tabs="tabs.filter((t) => !t.developerOnly || themeStore.devMode)">
			<template #footer>
				<div class="mt-auto text-secondary text-sm">
					<div class="mb-3">
						<template v-if="progress > 0 && progress < 1">
							<p class="m-0 mb-2">
								{{ formatMessage(messages.downloading, { version: downloadingVersion }) }}
							</p>
							<ProgressBar :progress="progress" />
						</template>
					</div>
					<p v-if="themeStore.devMode" class="text-brand font-semibold m-0 mb-2">
						{{ formatMessage(developerModeEnabled) }}
					</p>
					<div class="flex items-center gap-3">
						<button
							class="p-0 m-0 bg-transparent border-none cursor-pointer button-animation"
							:class="{
								'text-brand': themeStore.devMode,
								'text-secondary': !themeStore.devMode,
							}"
							@click="devModeCount"
						>
							<BlockEraLogo class="w-6 h-6" />
						</button>
						<div>
							<p class="m-0">BlockEra Launcher {{ version }}</p>
							<p class="m-0">
								<span v-if="osPlatform === 'macos'">macOS</span>
								<span v-else class="capitalize">{{ osPlatform }}</span>
								{{ osVersion }}
							</p>
						</div>
						<div v-if="updateState" class="w-8 h-8 cursor-pointer hover:brightness-75 neon-icon pulse">
							<template v-if="installState">
								<SpinnerIcon v-tooltip.bottom="'Устанавливаем обновление…'" class="size-6 animate-spin" />
							</template>
							<template v-else>
								<DownloadIcon v-tooltip.bottom="'Открыть обновление'" class="size-6" @click="!installState && initUpdateModal()" />
							</template>
						</div>
					</div>
				</div>
			</template>
		</TabbedModal>
		<!-- [AR] Feature -->
		<ModalWrapper ref="updateModalView" :has-to-type="false" header="Обновление BlockEra Launcher">
			<div class="space-y-4 pb-12">
				<div class="space-y-2">
					<strong v-if="availableUpdate">Доступна версия {{ availableUpdate.version }}</strong>
					<strong v-else-if="checkingState">Проверяем обновление…</strong>
					<strong v-else>Установлена актуальная версия</strong>
					<p v-if="availableUpdate?.body" class="text-secondary">{{ availableUpdate.body }}</p>
					<p v-else class="text-secondary">Текущая версия: {{ version }}</p>
					<p v-if="updateError" class="update-request-error">{{ updateError }}</p>
				</div>
				<div class="absolute bottom-4 right-4 flex items-center gap-4 neon-button neon">
					<Button class="bordered" @click="updateModalView.hide()">Закрыть</Button>
					<Button v-if="availableUpdate" class="bordered" :disabled="installState" @click="initDownload()">
						{{ installState ? 'Устанавливаем…' : 'Скачать и перезапустить' }}
					</Button>
				</div>
			</div>
		</ModalWrapper>
	</ModalWrapper>
</template>

<style lang="scss" scoped>
@import '../../../../../../packages/assets/styles/neon-icon.scss';
@import '../../../../../../packages/assets/styles/neon-button.scss';
@import '../../../../../../packages/assets/styles/neon-text.scss';

code {
  background: linear-gradient(90deg, #005eff, #00cfff);
  background-clip: text;
  -webkit-background-clip: text;
  color: transparent;
}

.update-request-error {
	padding: 0.75rem;
	border: 1px solid rgba(255, 83, 123, 0.24);
	border-radius: 0.75rem;
	color: #ff9bb2;
	background: rgba(255, 70, 110, 0.08);
	white-space: pre-wrap;
	word-break: break-word;
}

.settings-title {
	display: flex;
	align-items: center;
	gap: 0.85rem;
	color: #fff;
}

.settings-title-icon {
	display: grid;
	place-items: center;
	width: 2.5rem;
	height: 2.5rem;
	border: 1px solid rgba(168, 85, 247, 0.38);
	border-radius: 0.75rem;
	color: #c084fc;
	background: rgba(126, 34, 206, 0.16);
}

.settings-title-icon :deep(svg) { width: 1.25rem; height: 1.25rem; }
.settings-title > span:last-child { display: flex; flex-direction: column; gap: 0.2rem; }
.settings-title strong { font-size: 1.05rem; }
.settings-title small { color: rgba(226, 232, 240, 0.58); font-size: 0.76rem; font-weight: 500; }

:deep(.settings-cinematic .modal-overlay.standard) {
	background: rgba(2, 5, 11, 0.76);
	backdrop-filter: blur(14px);
}

:deep(.settings-cinematic .modal-body) {
	border: 1px solid rgba(168, 85, 247, 0.24);
	background:
		radial-gradient(circle at 14% 0%, rgba(126, 34, 206, 0.16), transparent 23rem),
		#0b101a !important;
	box-shadow: 0 32px 100px rgba(0, 0, 0, 0.55) !important;
}

:deep(.settings-cinematic .modal-body > div:first-child) {
	border-color: rgba(255, 255, 255, 0.08) !important;
	background: rgba(5, 8, 15, 0.45);
}

:deep(.settings-cinematic .modal-body > div:nth-child(2)) { padding: 1rem 1.25rem 1.25rem; }
:deep(.settings-cinematic .modal-body > div:nth-child(2) > div > div:first-child) {
	min-width: 14.5rem;
	padding: 0.35rem 0.9rem 0.35rem 0;
	border-color: rgba(255, 255, 255, 0.08) !important;
}

:deep(.settings-cinematic .modal-body > div:nth-child(2) > div > div:first-child button) {
	min-height: 2.65rem;
	border: 1px solid transparent;
	border-radius: 0.7rem;
	transition:
		transform 180ms cubic-bezier(0.22, 1, 0.36, 1),
		background-color 180ms cubic-bezier(0.22, 1, 0.36, 1),
		border-color 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

:deep(.settings-cinematic .modal-body > div:nth-child(2) > div > div:first-child button:hover) {
	transform: translateX(2px);
}

:deep(.settings-cinematic .modal-body > div:nth-child(2) > div > div:first-child button.bg-button-bgSelected) {
	border-color: rgba(168, 85, 247, 0.34);
	background: rgba(126, 34, 206, 0.18) !important;
	color: #e9d5ff !important;
}

:deep(.settings-cinematic .modal-body > div:nth-child(2) > div > div:last-child > div.overflow-y-auto) {
	width: min(46rem, calc(100vw - 24rem));
	height: min(36rem, calc(100vh - 12rem));
	padding: 0.2rem 0.5rem 1.5rem 1.25rem;
}

:deep(.launcher-settings-page) {
	display: flex;
	flex-direction: column;
	gap: 0.85rem;
	padding-right: 0.55rem;
}

:deep(.settings-page-header) {
	padding: 0.35rem 0 0.55rem;
}

:deep(.settings-page-header > p) {
	margin: 0 0 0.45rem;
	color: #c084fc;
	font-size: 0.68rem;
	font-weight: 800;
	letter-spacing: 0.14em;
}

:deep(.settings-page-header h2) {
	margin: 0;
	color: #fff;
	font-size: 1.55rem;
	line-height: 1.1;
}

:deep(.settings-page-header > span) {
	display: block;
	margin-top: 0.45rem;
	color: rgba(226, 232, 240, 0.62);
	font-size: 0.88rem;
}

:deep(.settings-section) {
	padding: 1rem;
	border: 1px solid rgba(255, 255, 255, 0.075);
	border-radius: 0.9rem;
	background: linear-gradient(135deg, rgba(21, 27, 40, 0.92), rgba(13, 18, 28, 0.92));
	box-shadow: inset 0 1px rgba(255, 255, 255, 0.025);
}

:deep(.settings-section-heading) {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
	margin-bottom: 0.85rem;
}

:deep(.settings-section h3),
:deep(.settings-row h3) {
	margin: 0;
	color: #f8fafc;
	font-size: 0.92rem;
	font-weight: 750;
}

:deep(.settings-section p),
:deep(.settings-row p) {
	margin: 0.25rem 0 0;
	color: rgba(203, 213, 225, 0.58);
	font-size: 0.78rem;
	line-height: 1.35;
}

:deep(.settings-section-heading > strong) {
	min-width: 4rem;
	padding: 0.45rem 0.7rem;
	border-radius: 0.6rem;
	color: #d8b4fe;
	background: rgba(126, 34, 206, 0.16);
	text-align: center;
}

:deep(.settings-list) { padding-block: 0.3rem; }
:deep(.settings-row) {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1.5rem;
	min-height: 3.45rem;
	padding: 0.75rem 0.7rem;
	border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

:deep(.settings-row:last-child) { border-bottom: 0; }
:deep(.settings-row > div:first-child) { min-width: 0; }

:deep(.settings-resolution) {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 0.7rem;
	padding: 0.4rem 0.7rem 0.7rem;
}

:deep(.settings-fields) { display: grid; gap: 0.75rem; }
:deep(.settings-fields label),
:deep(.settings-resolution label) { display: grid; gap: 0.38rem; color: rgba(226, 232, 240, 0.72); font-size: 0.75rem; font-weight: 650; }
:deep(.settings-fields input),
:deep(.settings-resolution input) { width: 100%; box-sizing: border-box; background: rgba(5, 8, 15, 0.68); border-color: rgba(255, 255, 255, 0.09); }

:deep(.java-stack) { padding: 0.35rem; }
:deep(.java-version-row) {
	display: grid;
	grid-template-columns: 11rem minmax(0, 1fr);
	align-items: center;
	gap: 1rem;
	padding: 0.8rem;
	border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

:deep(.java-version-row:last-child) { border-bottom: 0; }
:deep(.java-version-row > div:first-child) { display: flex; flex-direction: column; gap: 0.25rem; }
:deep(.java-version-row strong) { color: #fff; font-size: 0.9rem; }
:deep(.java-version-row span) { color: rgba(203, 213, 225, 0.52); font-size: 0.72rem; }

@media (prefers-reduced-motion: no-preference) {
	:deep(.launcher-settings-page) { animation: settings-page-enter 350ms cubic-bezier(0.22, 1, 0.36, 1) both; }
}

@keyframes settings-page-enter {
	from { opacity: 0; transform: translateY(6px); }
	to { opacity: 1; transform: translateY(0); }
}

@media (prefers-reduced-motion: reduce) {
	:deep(.settings-cinematic *) { transition: none !important; }
}
</style>
