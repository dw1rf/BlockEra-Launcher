import { afterEach, describe, expect, it } from 'vitest'

import i18n from '@/i18n.config'

import { downloadMessage, launcherText } from './launcher-language'

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
