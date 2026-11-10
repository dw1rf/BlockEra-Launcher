export type ProcessExit = {
	code: number | null
	success: boolean
	user_requested_stop: boolean
}

export type CrashDiagnosis = {
	title: string
	explanation: string
	suggestion: string
	confidence: 'Высокая' | 'Средняя' | 'Низкая'
	suspectedMods: string[]
	evidence: string[]
	exitCode: number | null
}

export function redactDiagnosticReport(text: string): string {
	return text
		.replace(/(?:Bearer\s+)[A-Za-z0-9._~+\/-]+=*/gi, 'Bearer [СКРЫТО]')
		.replace(/\b[A-Za-z0-9_-]{16,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\b/g, '[СКРЫТ ТОКЕН]')
		.replace(
			/((?:--)?(?:access[_-]?token|refresh[_-]?token|client[_-]?secret|token|password|authorization)\b["']?\s*(?:[:=]\s*|\s+))(?:"[^"\r\n]*"|'[^'\r\n]*'|[^\s,;]+)/gi,
			'$1[СКРЫТО]',
		)
		.replace(/(?:[A-Z]:[\\/]Users[\\/]|\/(?:home|Users)\/)[^\\/\r\n]+/gi, '[ПОЛЬЗОВАТЕЛЬСКИЙ ПУТЬ]')
		.replace(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g, '[IP-АДРЕС]')
		.replace(/\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b/gi, '[EMAIL]')
}

export function diagnoseCrash(text: string, exit?: ProcessExit | null): CrashDiagnosis | null {
	if (exit?.success || exit?.user_requested_stop) return null
	const clean = text
		.slice(-400_000)
		.split(/\r?\n/)
		.filter((line) => !line.includes('[CHAT]'))
	let log = clean.join('\n')
	if (!exit && /# User requested stop: true/.test(log)) return null
	const status = [
		...log.matchAll(/# Process exited with status:\s*(?:exit (?:code|status):\s*)?(-?\d+)/gi),
	].at(-1)
	if (!exit && status?.[1] === '0') return null
	const fatal =
		/---- Minecraft Crash Report ----|A fatal error has been detected by the Java Runtime|# Process exited with status:.*(?:signal|exception)|Exception in thread "main"|Failed to start the minecraft server|Could not execute entrypoint|Incompatible mods found/i
	if (!exit && !(status && status[1] !== '0') && !fatal.test(log)) return null

	let fatalStart = -1
	for (let index = clean.length - 1; index >= 0; index--) {
		if (
			/---- Minecraft Crash Report ----|Exception in thread "main"|A fatal error has been detected by the Java Runtime/i.test(
				clean[index],
			)
		) {
			fatalStart = index
			break
		}
	}
	log = (fatalStart >= 0 ? clean.slice(fatalStart) : clean.slice(-160)).join('\n')
	const relevant = log
		.split(/\r?\n/)
		.filter((line) => /error|exception|failed|failure|could not execute entrypoint/i.test(line))
		.join('\n')
	const suspectedMods = new Set<string>()
	for (const match of relevant.matchAll(/(?:from mod\s+|provided by\s+)["']?([a-z0-9_.-]+)/gi)) {
		suspectedMods.add(match[1])
	}
	for (const match of log.matchAll(
		/(?:Mod File|Failure message):[^\r\n]*?([a-z0-9_.+-]+\.jar)\b/gi,
	)) {
		suspectedMods.add(match[1])
	}
	const diagnosis: CrashDiagnosis = {
		title: 'Игра завершилась с ошибкой',
		explanation: 'Журнал не содержит достаточно данных для определения причины.',
		suggestion: 'Откройте журнал или скопируйте отчёт для разбора.',
		confidence: 'Низкая',
		suspectedMods: [...suspectedMods].slice(0, 8),
		evidence: [],
		exitCode: exit?.code ?? (status ? Number(status[1]) : null),
	}
	const rules = [
		{
			pattern: /OutOfMemoryError|Could not reserve enough space|Native memory allocation.*failed/i,
			title: 'Не хватило памяти',
			explanation: 'Java сообщила об ошибке выделения памяти.',
			suggestion:
				'Проверьте лимит памяти профиля и свободную память компьютера. Слишком большой лимит тоже может мешать запуску.',
			confidence: 'Высокая' as const,
		},
		{
			pattern:
				/UnsupportedClassVersionError|compiled by a more recent version of the Java Runtime/i,
			title: 'Не подходит версия Java',
			explanation: 'Игра или один из модов требует более новую Java.',
			suggestion: 'Выберите Java, подходящую версии Minecraft и загрузчику, в настройках сборки.',
			confidence: 'Высокая' as const,
		},
		{
			pattern:
				/Incompatible mods found|ModResolutionException|Missing or unsupported mandatory dependencies|requires.*(?:version|mod).*missing/i,
			title: 'Проблема с зависимостями модов',
			explanation: 'Загрузчик обнаружил несовместимые версии или отсутствующие зависимости.',
			suggestion: 'Сверьте перечисленные в журнале требования с версиями установленных модов.',
			confidence: 'Высокая' as const,
		},
		{
			pattern:
				/MixinApplyError|MixinTransformerError|InvalidMixinException|InjectionError|Could not execute entrypoint/i,
			title: 'Ошибка при загрузке модов',
			explanation:
				'Сбой произошёл при применении изменений мода или вызове его кода. Взаимодействие нескольких модов также возможно.',
			suggestion:
				'Проверьте версии указанных модов и загрузчика. Изменения удобнее проверять на копии сборки.',
			confidence: 'Средняя' as const,
		},
		{
			pattern:
				/GLFW error 65542|Failed to create (?:a )?(?:window|OpenGL context)|does not appear to support OpenGL|WGL.*driver/i,
			title: 'Не удалось запустить графику',
			explanation: 'В журнале есть ошибка создания графического контекста.',
			suggestion: 'Проверьте драйвер видеокарты и выбранный графический адаптер.',
			confidence: 'Средняя' as const,
		},
	]
	const rule = rules.find(({ pattern }) => pattern.test(log))
	if (rule) {
		Object.assign(diagnosis, {
			title: rule.title,
			explanation: rule.explanation,
			suggestion: rule.suggestion,
			confidence: rule.confidence,
		})
		diagnosis.evidence = log
			.split(/\r?\n/)
			.filter((line) => rule.pattern.test(line))
			.slice(-3)
			.map((line) => redactDiagnosticReport(line).slice(0, 500))
	}
	return diagnosis
}

export function crashReport(diagnosis: CrashDiagnosis, profile: string, log: string): string {
	return redactDiagnosticReport(
		[
			'Отчёт BlockEra',
			`Сборка — ${profile}`,
			diagnosis.title,
			`Уверенность — ${diagnosis.confidence.toLowerCase()}`,
			`Код завершения — ${diagnosis.exitCode ?? 'неизвестен'}`,
			diagnosis.explanation,
			diagnosis.suggestion,
			`Моды, упомянутые рядом со сбоем — ${diagnosis.suspectedMods.join(', ') || 'не определены'}`,
			...diagnosis.evidence,
			'Последние строки журнала',
			...log.slice(-40_000).split(/\r?\n/).slice(-80),
		].join('\n'),
	)
}
