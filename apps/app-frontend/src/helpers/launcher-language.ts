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

/** Translate only recognized errors; callers retain the original for diagnostics. */
export function localizedError(message: string): string {
	const translated = launcherText(message)
	if (!i18n.global.locale.value.startsWith('ru')) return translated
	if (
		/^(?:TypeError:\s*)?Cannot read properties of (?:null|undefined) \(reading ['"]hide['"]\)$/.test(
			message,
		)
	) {
		return 'Не удалось закрыть окно: оно уже недоступно. Откройте нужный раздел заново и повторите действие.'
	}
	if (message === '[AR] Network error: No local injector & remote unavailable') {
		return 'Не удалось загрузить компонент для скинов: локальная копия отсутствует, а удалённый источник недоступен. Проверьте подключение и повторите загрузку.'
	}
	if (/^(?:I\/O\(std\):?\s*)?Failed to fetch player profile$/.test(message)) {
		return 'Не удалось получить профиль Minecraft при входе Microsoft. Повторите вход; подробности доступны в исходной ошибке.'
	}
	// Translate known wrappers without discarding their concrete backend details.
	return translated
		.replace(/^Error launching Minecraft:\s*/, 'Не удалось запустить Minecraft: ')
		.replace(/^Minecraft authentication error:\s*/, 'Ошибка авторизации Minecraft: ')
		.replace(/^User is not logged in\.?$/, 'Выберите аккаунт для запуска игры.')
}
