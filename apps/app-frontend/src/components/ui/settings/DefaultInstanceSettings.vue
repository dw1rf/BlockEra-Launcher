<script setup lang="ts">
import { injectNotificationManager, Slider, Toggle } from '@modrinth/ui'
import { ref, watch } from 'vue'

import useMemorySlider from '@/composables/useMemorySlider'
import { get, set } from '@/helpers/settings.ts'

const { handleError } = injectNotificationManager()

const fetchSettings = await get()
fetchSettings.launchArgs = fetchSettings.extra_launch_args.join(' ')
fetchSettings.envVars = fetchSettings.custom_env_vars.map((x) => x.join('=')).join(' ')

const settings = ref(fetchSettings)

const { maxMemory, snapPoints } = (await useMemorySlider().catch(handleError)) as unknown as {
	maxMemory: number
	snapPoints: number[]
}

watch(
	settings,
	async () => {
		const setSettings = JSON.parse(JSON.stringify(settings.value))

		setSettings.extra_launch_args = setSettings.launchArgs.trim().split(/\s+/).filter(Boolean)
		setSettings.custom_env_vars = setSettings.envVars
			.trim()
			.split(/\s+/)
			.filter(Boolean)
			.map((x) => x.split('=').filter(Boolean))

		if (!setSettings.hooks.pre_launch) {
			setSettings.hooks.pre_launch = null
		}
		if (!setSettings.hooks.wrapper) {
			setSettings.hooks.wrapper = null
		}
		if (!setSettings.hooks.post_exit) {
			setSettings.hooks.post_exit = null
		}

		if (!setSettings.custom_dir) {
			setSettings.custom_dir = null
		}

		await set(setSettings)
	},
	{ deep: true },
)
</script>

<template>
	<div class="launcher-settings-page">
		<header class="settings-page-header">
			<p>ИГРОВОЙ ПРОЦЕСС</p>
			<h2>Запуск Minecraft</h2>
			<span>Параметры применяются ко всем новым сборкам по умолчанию.</span>
		</header>

		<section class="settings-section settings-list">
			<div class="settings-section-heading"><div><h3>Окно игры</h3><p>Размер и режим окна после запуска.</p></div></div>
			<div class="settings-row"><div><h3>Полноэкранный режим</h3><p>Всегда запускать Minecraft на весь экран.</p></div><Toggle id="fullscreen" v-model="settings.force_fullscreen" /></div>
			<div class="settings-resolution">
				<label><span>Ширина</span><input id="width" v-model="settings.game_resolution[0]" :disabled="settings.force_fullscreen" autocomplete="off" type="number" placeholder="1920" /></label>
				<label><span>Высота</span><input id="height" v-model="settings.game_resolution[1]" :disabled="settings.force_fullscreen" autocomplete="off" type="number" placeholder="1080" /></label>
			</div>
		</section>

		<section class="settings-section">
			<div class="settings-section-heading"><div><h3>Оперативная память</h3><p>Максимальный объём RAM для одной запущенной сборки.</p></div><strong>{{ settings.memory.maximum }} MB</strong></div>
			<Slider id="max-memory" v-model="settings.memory.maximum" :min="512" :max="maxMemory" :step="64" :snap-points="snapPoints" :snap-range="512" unit="MB" />
		</section>

		<section class="settings-section settings-fields">
			<div class="settings-section-heading"><div><h3>Дополнительные параметры</h3><p>Для опытных пользователей и нестандартных конфигураций.</p></div></div>
			<label><span>Аргументы Java</span><input id="java-args" v-model="settings.launchArgs" autocomplete="off" type="text" placeholder="Например: -XX:+UseG1GC" /></label>
			<label><span>Переменные окружения</span><input id="env-vars" v-model="settings.envVars" autocomplete="off" type="text" placeholder="Например: NAME=value" /></label>
		</section>

		<section class="settings-section settings-fields">
			<div class="settings-section-heading"><div><h3>Команды запуска</h3><p>Сценарии до запуска, обёртка процесса и команда после закрытия.</p></div></div>
			<label><span>Перед запуском</span><input id="pre-launch" v-model="settings.hooks.pre_launch" autocomplete="off" type="text" placeholder="Команда перед запуском" /></label>
			<label><span>Обёртка</span><input id="wrapper" v-model="settings.hooks.wrapper" autocomplete="off" type="text" placeholder="Команда-обёртка" /></label>
			<label><span>После закрытия</span><input id="post-exit" v-model="settings.hooks.post_exit" autocomplete="off" type="text" placeholder="Команда после выхода" /></label>
		</section>
	</div>
</template>
