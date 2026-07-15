<script setup lang="ts">
import { Toggle } from '@modrinth/ui'
import { ref, watch } from 'vue'

import { optInAnalytics, optOutAnalytics } from '@/helpers/analytics'
import { get, set } from '@/helpers/settings.ts'

const settings = ref(await get())

watch(
	settings,
	async () => {
		if (settings.value.telemetry) {
			optInAnalytics()
		} else {
			optOutAnalytics()
		}

		await set(settings.value)
	},
	{ deep: true },
)
</script>

<template>
	<div class="launcher-settings-page">
		<header class="settings-page-header"><p>ПРИВАТНОСТЬ</p><h2>Данные и интеграции</h2><span>Управляйте аналитикой и внешними сервисами.</span></header>
		<section class="settings-section settings-list">
			<div class="settings-row"><div><h3>Персонализированная реклама</h3><p>Использовать интересы для подбора рекламных материалов.</p></div><Toggle id="personalized-ads" v-model="settings.personalized_ads" :disabled="!settings.personalized_ads" /></div>
			<div class="settings-row"><div><h3>Анонимная аналитика</h3><p>Отправлять обезличенные данные об использовании для улучшения лаунчера.</p></div><Toggle id="opt-out-analytics" v-model="settings.telemetry" :disabled="!settings.telemetry" /></div>
			<div class="settings-row"><div><h3>Статус в Discord</h3><p>Показывать в профиле Discord, что вы используете BlockEra Launcher. Потребуется перезапуск.</p></div><Toggle id="disable-discord-rpc" v-model="settings.discord_rpc" /></div>
		</section>
	</div>
</template>
