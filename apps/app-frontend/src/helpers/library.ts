type LibraryProfile = { name: string; game_version: string; groups?: string[] }
type PlayedWorld = { name: string; last_played?: string }

export function libraryMatches(profile: LibraryProfile, folder: string, query: string) {
	const haystack = [profile.name, profile.game_version, folder, ...(profile.groups ?? [])]
		.join(' ')
		.toLocaleLowerCase()
	return query
		.trim()
		.toLocaleLowerCase()
		.split(/\s+/)
		.every((word) => haystack.includes(word))
}

export function normalizedTags(text: string) {
	return [
		...new Set(
			text
				.split(',')
				.map((tag) => tag.trim().slice(0, 32))
				.filter(Boolean),
		),
	]
}

export function latestPlayedWorld<T extends PlayedWorld>(worlds: T[]): T | undefined {
	return worlds.reduce<T | undefined>((latest, world) => {
		const played = Date.parse(world.last_played ?? '')
		if (!Number.isFinite(played) || played <= 0) return latest
		return !latest || played > Date.parse(latest.last_played!) ? world : latest
	}, undefined)
}

export function libraryPlaytime(seconds: number) {
	const minutes = Math.floor(Math.max(0, seconds || 0) / 60)
	if (minutes < 1) return seconds > 0 ? 'Меньше минуты' : '0 мин.'
	if (minutes < 60) return `${minutes} мин.`
	return `${Math.floor(minutes / 60)} ч. ${minutes % 60} мин.`
}
