<script setup lang="ts">
import { Toggle } from '@modrinth/ui'
import { watch } from 'vue'

import { useAppSettings } from '@/composables/use-app-settings'

const { settings, saveKey } = await useAppSettings()

watch(
	() => settings.value.discord_rpc,
	(value) => void saveKey('discord_rpc', value),
)
</script>

<template>
	<div class="launcher-settings-page">
		<header class="settings-page-header">
			<p>ПРИВАТНОСТЬ</p>
			<h2>Данные и интеграции</h2>
			<span>Управляйте аналитикой и внешними сервисами.</span>
		</header>
		<section class="settings-section settings-list">
			<div class="settings-row">
				<div>
					<h3>Персонализированная реклама</h3>
					<p>BlockEra не использует персонализированную рекламу.</p>
				</div>
				<strong>Всегда отключено</strong>
			</div>
			<div class="settings-row">
				<div>
					<h3>Анонимная аналитика</h3>
					<p>Сбор аналитики отключён политикой BlockEra.</p>
				</div>
				<strong>Всегда отключено</strong>
			</div>
			<div class="settings-row">
				<div>
					<h3>Статус в Discord</h3>
					<p>
						Показывать в профиле Discord, что вы используете BlockEra Launcher. Потребуется
						перезапуск.
					</p>
				</div>
				<Toggle id="disable-discord-rpc" v-model="settings.discord_rpc" />
			</div>
		</section>
	</div>
</template>
