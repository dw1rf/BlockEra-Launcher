<script setup lang="ts">
import { injectNotificationManager, LOCALES } from '@modrinth/ui'
import { ref } from 'vue'

import { useAppSettings } from '@/composables/use-app-settings'
import { launcherText } from '@/helpers/launcher-language'
import i18n from '@/i18n.config'

const { settings, saveKey } = await useAppSettings()
const { handleError } = injectNotificationManager()
const changing = ref(false)
async function changeLanguage(event: Event) {
	const locale = (event.target as HTMLSelectElement).value
	if (!LOCALES.some((item) => item.code === locale)) return
	const previous = settings.value.locale
	changing.value = true
	try {
		await saveKey('locale', locale)
		i18n.global.locale.value = locale
		document.documentElement.lang = locale
	} catch (error) {
		settings.value.locale = previous
		handleError(error as Error)
	} finally {
		changing.value = false
	}
}
</script>
<template>
	<div class="launcher-settings-page">
		<header class="settings-page-header">
			<h2>{{ launcherText('Interface language') }}</h2>
			<span>{{ launcherText('Choose a language for menus, notifications and dialogs.') }}</span>
		</header>
		<section class="settings-section">
			<label for="launcher-language">{{ launcherText('Language') }}</label>
			<select
				id="launcher-language"
				:value="settings.locale"
				:disabled="changing"
				@change="changeLanguage"
			>
				<option v-for="locale in LOCALES" :key="locale.code" :value="locale.code">
					{{ locale.name }}
				</option>
			</select>
			<p role="status">
				{{
					launcherText(
						changing
							? 'Saving...'
							: 'Your choice is saved automatically. Some community translations may be incomplete.',
					)
				}}
			</p>
		</section>
	</div>
</template>
<style scoped>
select {
	display: block;
	width: 100%;
	max-width: 28rem;
	margin-top: 0.5rem;
}
</style>
