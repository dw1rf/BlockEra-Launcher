<script setup lang="ts">
import { Combobox, Toggle } from '@modrinth/ui'
import { ref, watch } from 'vue'

import { get, set } from '@/helpers/settings.ts'
import { getOS } from '@/helpers/utils'
import { useTheming } from '@/store/state'

const themeStore = useTheming()

const os = ref(await getOS())
const settings = ref(await get())
settings.value.theme = 'dark'
themeStore.setThemeState('dark')

watch(
	settings,
	async () => {
		await set(settings.value)
	},
	{ deep: true },
)
</script>
<template>
	<div class="launcher-settings-page">
		<header class="settings-page-header">
			<p>ВНЕШНИЙ ВИД</p>
			<h2>Интерфейс лаунчера</h2>
			<span>Настройте оформление, поведение окна и стартовый экран.</span>
		</header>

		<section class="settings-section">
			<div class="settings-section-heading"><div><h3>Фирменная тема BlockEra</h3><p>Тёмное фиолетовое оформление оптимизировано для всех экранов лаунчера.</p></div></div>
			<div class="settings-row"><div><h3>BlockEra Dark</h3><p>Единая контрастная тема без светлых и системных конфликтов.</p></div><span class="theme-active-badge">Активна</span></div>
		</section>

		<section class="settings-section settings-list">
		<div class="settings-row">
		<div><h3>Эффекты интерфейса</h3><p>Размытие, прозрачность и плавные переходы. Отключите на слабых видеокартах.</p></div>

		<Toggle
			id="advanced-rendering"
			:model-value="themeStore.advancedRendering"
			@update:model-value="
				(e) => {
					themeStore.advancedRendering = !!e
					settings.advanced_rendering = themeStore.advancedRendering
				}
			"
		/>
	</div>

	<div class="settings-row">
		<div><h3>Скрывать ник в примерочной</h3><p>Не показывать подпись над персонажем на странице скинов.</p></div>
		<Toggle id="hide-nametag-skins-page" v-model="settings.hide_nametag_skins_page" />
	</div>

	<div v-if="os !== 'MacOS'" class="settings-row">
		<div><h3>Системная рамка окна</h3><p>Использовать стандартное оформление Windows. Потребуется перезапуск.</p></div>
		<Toggle id="native-decorations" v-model="settings.native_decorations" />
	</div>

	<div class="settings-row">
		<div><h3>Сворачивать при запуске</h3><p>Автоматически скрывать лаунчер после запуска Minecraft.</p></div>
		<Toggle id="minimize-launcher" v-model="settings.hide_on_process_start" />
	</div>
		</section>

		<section class="settings-section settings-list">
	<div class="settings-row">
		<div><h3>Стартовая вкладка</h3><p>Экран, который открывается сразу после запуска BlockEra Launcher.</p></div>
		<Combobox
			id="opening-page"
			v-model="settings.default_page"
			name="Opening page dropdown"
			class="w-40"
			:options="['Home', 'Library'].map((v) => ({ value: v, label: v }))"
			:display-value="settings.default_page ?? 'Select an option'"
		/>
	</div>

	<div class="settings-row">
		<div><h3>Недавние миры на главной</h3><p>Показывать быстрый переход к недавно запущенным мирам.</p></div>
		<Toggle
			:model-value="themeStore.getFeatureFlag('worlds_in_home')"
			@update:model-value="
				() => {
					const newValue = !themeStore.getFeatureFlag('worlds_in_home')
					themeStore.featureFlags['worlds_in_home'] = newValue
					settings.feature_flags['worlds_in_home'] = newValue
				}
			"
		/>
	</div>

	<div class="settings-row">
		<div><h3>Сворачиваемая боковая панель</h3><p>Разрешить скрывать боковую панель на детальных страницах.</p></div>
		<Toggle
			id="toggle-sidebar"
			:model-value="settings.toggle_sidebar"
			@update:model-value="
				(e) => {
					settings.toggle_sidebar = !!e
					themeStore.toggleSidebar = settings.toggle_sidebar
				}
			"
		/>
	</div>
		</section>
	</div>
</template>

<style scoped>
.theme-active-badge {
	padding: 0.45rem 0.75rem;
	border: 1px solid rgba(192, 132, 252, 0.38);
	border-radius: 999px;
	background: rgba(126, 34, 206, 0.2);
	color: #d8b4fe;
	font-weight: 700;
}
</style>
