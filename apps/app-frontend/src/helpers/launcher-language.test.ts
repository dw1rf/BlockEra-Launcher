import { afterEach, describe, expect, it } from 'vitest'

import i18n from '@/i18n.config'

import { downloadMessage, launcherText, localizedError } from './launcher-language'

afterEach(() => {
	i18n.global.locale.value = 'ru-RU'
})
describe('launcher language', () => {
	it('defaults to Russian and includes shared UI translations', () => {
		expect(i18n.global.locale.value).toBe('ru-RU')
		expect(i18n.global.t('app.auth-servers.unreachable.header')).not.toContain('Cannot reach')
		expect(i18n.global.t('button.cancel')).toBe('Отмена')
		expect(launcherText('Are you sure you want to delete this instance?')).toBe('Удалить сборку?')
	})
	it('switches messages without restarting and preserves technical names', () => {
		expect(downloadMessage('Downloading modpack')).toBe('Загрузка сборки')
		expect(downloadMessage('Extracting override 2/10')).toBe('Распаковка файлов сборки 2/10')
		expect(launcherText('Horizon')).toBe('Horizon')
		i18n.global.locale.value = 'en-US'
		expect(launcherText('Cancel download')).toBe('Cancel download')
		expect(downloadMessage('Downloading modpack')).toBe('Downloading modpack')
	})
})

describe('localized error summaries', () => {
	it('explains the missing dialog without guessing other JavaScript failures', () => {
		expect(localizedError("Cannot read properties of null (reading 'hide')")).toContain(
			'Не удалось закрыть окно',
		)
		expect(
			localizedError("TypeError: Cannot read properties of undefined (reading 'hide')"),
		).toContain('Откройте нужный раздел')
		const unknown = "Cannot read properties of null (reading 'customField')"
		expect(localizedError(unknown)).toBe(unknown)
	})
	it('keeps technical detail in known wrappers and uses original English when selected', () => {
		expect(localizedError('Error launching Minecraft: failed XYZ-42')).toBe(
			'Не удалось запустить Minecraft: failed XYZ-42',
		)
		const injector = '[AR] Network error: No local injector & remote unavailable'
		expect(localizedError(injector)).toContain('локальная копия отсутствует')
		expect(localizedError('I/O (std) error: Failed to fetch player profile')).toContain('при входе Microsoft')
		i18n.global.locale.value = 'en-US'
		expect(localizedError(injector)).toBe(injector)
		const original = "Cannot read properties of null (reading 'hide')"
		expect(localizedError(original)).toBe(original)
	})
})
