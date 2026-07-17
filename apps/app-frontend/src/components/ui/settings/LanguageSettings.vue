<script setup lang="ts">
import {
	Admonition,
	AutoLink,
	IntlFormatted,
	LanguageSelector,
	languageSelectorMessages,
	LOCALES,
	useVIntl,
} from '@modrinth/ui'
import { ref } from 'vue'

import { useAppSettings } from '@/composables/use-app-settings'
import i18n from '@/i18n.config'

const { formatMessage } = useVIntl()

const platform = formatMessage(languageSelectorMessages.platformApp)

const { settings, saveKey } = await useAppSettings()

const $isChanging = ref(false)

async function onLocaleChange(newLocale: string) {
	if (settings.value.locale === newLocale) return

	$isChanging.value = true
	try {
		i18n.global.locale.value = newLocale
		await saveKey('locale', newLocale)
	} finally {
		$isChanging.value = false
	}
}
</script>

<template>
	<div class="launcher-settings-page">
		<header class="settings-page-header">
			<p>ЛОКАЛИЗАЦИЯ</p>
			<h2>Язык интерфейса</h2>
			<span>Выберите язык меню, уведомлений и служебных сообщений.</span>
		</header>
		<section class="settings-section">
			<Admonition type="warning" class="mt-2 mb-4">
				{{ formatMessage(languageSelectorMessages.languageWarning, { platform }) }}
			</Admonition>

			<p class="m-0 mb-4">
				<IntlFormatted
					:message-id="languageSelectorMessages.languagesDescription"
					:values="{ platform }"
				>
					<template #~crowdin-link="{ children }">
						<AutoLink to="https://translate.modrinth.com">
							<component :is="() => children" />
						</AutoLink>
					</template>
				</IntlFormatted>
			</p>

			<LanguageSelector
				:current-locale="settings.locale"
				:locales="LOCALES"
				:on-locale-change="onLocaleChange"
				:is-changing="$isChanging"
			/>
		</section>
	</div>
</template>
