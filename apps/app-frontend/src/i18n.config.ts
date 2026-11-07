import { buildLocaleMessages, createMessageCompiler, type CrowdinMessages } from '@modrinth/ui'
import { createI18n } from 'vue-i18n'

const localeModules = import.meta.glob<{ default: CrowdinMessages }>('./locales/*/index.json', {
	eager: true,
})

// Shared UI components use defaultMessage in their locale files.
const sharedModules = import.meta.glob<{
	default: Record<string, { defaultMessage?: string; message?: string } | string>
}>('../../../packages/ui/src/locales/*/index.json', { eager: true })
const messages = buildLocaleMessages(localeModules)
for (const [path, module] of Object.entries(sharedModules)) {
	const locale = path.match(/\/([^/]+)\/index\.json$/)?.[1]
	if (!locale) continue
	const shared = Object.fromEntries(
		Object.entries(module.default).map(([key, value]) => [
			key,
			typeof value === 'string' ? value : (value.message ?? value.defaultMessage ?? key),
		]),
	)
	messages[locale] = { ...shared, ...messages[locale] }
}

const i18n = createI18n({
	legacy: false,
	locale: 'ru-RU',
	fallbackLocale: 'ru-RU',
	messageCompiler: createMessageCompiler(),
	missingWarn: false,
	fallbackWarn: false,
	messages,
})

export default i18n
