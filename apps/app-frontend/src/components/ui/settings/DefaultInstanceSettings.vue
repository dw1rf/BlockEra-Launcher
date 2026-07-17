<script setup lang="ts">
import { injectNotificationManager, Slider, Toggle } from '@modrinth/ui'
import { ref, watch } from 'vue'

import { useAppSettings } from '@/composables/use-app-settings'
import useMemorySlider from '@/composables/useMemorySlider'

const { handleError } = injectNotificationManager()

const { settings, saveKey } = await useAppSettings()
const launchArgs = ref(settings.value.extra_launch_args.join(' '))
const envVars = ref(settings.value.custom_env_vars.map((x) => x.join('=')).join(' '))

const { maxMemory, snapPoints } = (await useMemorySlider().catch(handleError)) as unknown as {
	maxMemory: number
	snapPoints: number[]
}

watch(
	() => settings.value.force_fullscreen,
	(value) => void saveKey('force_fullscreen', value),
)
watch(
	() => [settings.value.game_resolution.width, settings.value.game_resolution.height],
	() => void saveKey('game_resolution', { ...settings.value.game_resolution }, 250),
)
watch(
	() => settings.value.memory.maximum,
	() => void saveKey('memory', { ...settings.value.memory }, 250),
)
watch(launchArgs, (value) => {
	void saveKey('extra_launch_args', value.trim().split(/\s+/).filter(Boolean), 250)
})
watch(envVars, (value) => {
	const parsed = value
		.trim()
		.split(/\s+/)
		.filter(Boolean)
		.map((entry) => {
			const separator = entry.indexOf('=')
			return separator > 0 ? [entry.slice(0, separator), entry.slice(separator + 1)] : null
		})
		.filter((entry): entry is [string, string] => !!entry)
	void saveKey('custom_env_vars', parsed, 250)
})
watch(
	() => [
		settings.value.hooks.pre_launch,
		settings.value.hooks.wrapper,
		settings.value.hooks.post_exit,
	],
	() =>
		void saveKey(
			'hooks',
			{
				...settings.value.hooks,
				pre_launch: settings.value.hooks.pre_launch || undefined,
				wrapper: settings.value.hooks.wrapper || undefined,
				post_exit: settings.value.hooks.post_exit || undefined,
			},
			250,
		),
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
			<div class="settings-section-heading">
				<div>
					<h3>Окно игры</h3>
					<p>Размер и режим окна после запуска.</p>
				</div>
			</div>
			<div class="settings-row">
				<div>
					<h3>Полноэкранный режим</h3>
					<p>Всегда запускать Minecraft на весь экран.</p>
				</div>
				<Toggle id="fullscreen" v-model="settings.force_fullscreen" />
			</div>
			<div class="settings-resolution">
				<label
					><span>Ширина</span
					><input
						id="width"
						v-model="settings.game_resolution.width"
						:disabled="settings.force_fullscreen"
						autocomplete="off"
						type="number"
						placeholder="1920"
				/></label>
				<label
					><span>Высота</span
					><input
						id="height"
						v-model="settings.game_resolution.height"
						:disabled="settings.force_fullscreen"
						autocomplete="off"
						type="number"
						placeholder="1080"
				/></label>
			</div>
		</section>

		<section class="settings-section">
			<div class="settings-section-heading">
				<div>
					<h3>Оперативная память</h3>
					<p>Максимальный объём RAM для одной запущенной сборки.</p>
				</div>
				<strong>{{ settings.memory.maximum }} MB</strong>
			</div>
			<Slider
				id="max-memory"
				v-model="settings.memory.maximum"
				:min="512"
				:max="maxMemory"
				:step="64"
				:snap-points="snapPoints"
				:snap-range="512"
				unit="MB"
			/>
		</section>

		<section class="settings-section settings-fields">
			<div class="settings-section-heading">
				<div>
					<h3>Дополнительные параметры</h3>
					<p>Для опытных пользователей и нестандартных конфигураций.</p>
				</div>
			</div>
			<label
				><span>Аргументы Java</span
				><input
					id="java-args"
					v-model="launchArgs"
					autocomplete="off"
					type="text"
					placeholder="Например: -XX:+UseG1GC"
			/></label>
			<label
				><span>Переменные окружения</span
				><input
					id="env-vars"
					v-model="envVars"
					autocomplete="off"
					type="text"
					placeholder="Например: NAME=value"
			/></label>
		</section>

		<section class="settings-section settings-fields">
			<div class="settings-section-heading">
				<div>
					<h3>Команды запуска</h3>
					<p>Сценарии до запуска, обёртка процесса и команда после закрытия.</p>
				</div>
			</div>
			<label
				><span>Перед запуском</span
				><input
					id="pre-launch"
					v-model="settings.hooks.pre_launch"
					autocomplete="off"
					type="text"
					placeholder="Команда перед запуском"
			/></label>
			<label
				><span>Обёртка</span
				><input
					id="wrapper"
					v-model="settings.hooks.wrapper"
					autocomplete="off"
					type="text"
					placeholder="Команда-обёртка"
			/></label>
			<label
				><span>После закрытия</span
				><input
					id="post-exit"
					v-model="settings.hooks.post_exit"
					autocomplete="off"
					type="text"
					placeholder="Команда после выхода"
			/></label>
		</section>
	</div>
</template>
