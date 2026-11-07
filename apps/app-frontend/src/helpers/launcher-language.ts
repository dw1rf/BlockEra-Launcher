import i18n from '@/i18n.config'

import messageKeys from './launcher-messages.json'

export function launcherText(message: string): string {
	const key = (messageKeys as Record<string, string>)[message]
	return key ? i18n.global.t(key) : message
}

export function downloadMessage(message: string): string {
	const translated = launcherText(message)
	if (translated !== message || !i18n.global.locale.value.startsWith('ru')) return translated
	const patterns: [RegExp, string][] = [
		[/^Extracting override (.*)$/, 'Распаковка файлов сборки $1'],
		[/^Running forge processor (.*)$/, 'Подготовка Forge $1'],
		[/^Downloading mod (.*)$/, 'Загрузка мода $1'],
		[/^Downloaded mod (.*)$/, 'Загружен мод $1'],
		[/^Downloading (.*)$/, 'Загрузка $1'],
		[/^Fetching (.*)$/, 'Получение данных $1'],
		[/^Extracting (.*)$/, 'Распаковка $1'],
	]
	for (const [pattern, replacement] of patterns) {
		if (pattern.test(message)) return message.replace(pattern, replacement).replace(/ of /g, ' из ')
	}
	return message
}
