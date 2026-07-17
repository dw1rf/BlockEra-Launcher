export type RecommendedPackLink = {
	label: string
	url: string
}

export type RecommendedPack = {
	slug: string
	projectId: string
	title: string
	owner: string
	ownerKind: 'Автор' | 'Команда' | 'Стример'
	tagline: string
	description: string[]
	accent: string
	iconUrl: string
	links: RecommendedPackLink[]
}

// Популярные проекты Modrinth. Готовые авторские .mrpack загружаются из
// встроенного/удалённого каталога Theseus и намеренно хранятся отдельно.
export const recommendedPacks: RecommendedPack[] = [
	{
		slug: 'fabulously-optimized',
		projectId: '1KVo5zza',
		title: 'Fabulously Optimized',
		owner: 'robotkoer и команда FO',
		ownerKind: 'Команда',
		tagline: 'Максимум FPS, красивая графика и знакомые возможности OptiFine без лишней сложности.',
		description: [
			'Fabulously Optimized — лёгкая клиентская сборка для тех, кто хочет современный Minecraft с высокой производительностью. Она объединяет Sodium, Iris и набор аккуратно настроенных улучшений интерфейса.',
			'Сборка подходит для обычных миров и большинства серверов: она не меняет игровой баланс, быстро обновляется и сохраняет ощущение ванильной игры.',
		],
		accent: '#67e8f9',
		iconUrl:
			'https://cdn.modrinth.com/data/1KVo5zza/d8152911f8fd5d7e9a8c499fe89045af81fe816e_96.webp',
		links: [
			{ label: 'Страница сборки', url: 'https://modrinth.com/modpack/fabulously-optimized' },
			{ label: 'Команда авторов', url: 'https://wiki.download.fo/team' },
		],
	},
	{
		slug: 'cobblemon-official',
		projectId: '5FFgwNNP',
		title: 'Cobblemon Official',
		owner: 'Cobbled Studios',
		ownerKind: 'Команда',
		tagline:
			'Официальное приключение Cobblemon: исследование мира, коллекционирование и сражения существ.',
		description: [
			'Официальная Fabric-сборка Cobblemon превращает Minecraft в большое приключение с ловлей, развитием и сражениями существ, не ломая привычную свободу песочницы.',
			'В комплекте уже есть оптимизация и совместимые дополнения. Это удобная отправная точка для одиночной игры, сервера с друзьями или будущей авторской сборки.',
		],
		accent: '#fb7185',
		iconUrl: 'https://cdn.modrinth.com/data/5FFgwNNP/e7f9ee2e9d361623847853fe2ddce42f519ee64f.png',
		links: [
			{ label: 'Страница сборки', url: 'https://modrinth.com/modpack/cobblemon-fabric' },
			{ label: 'Сайт Cobblemon', url: 'https://cobblemon.com' },
		],
	},
	{
		slug: 'better-mc-fabric',
		projectId: 'shFhR8Vx',
		title: 'Better MC [Fabric]',
		owner: 'LunaPixelStudios',
		ownerKind: 'Команда',
		tagline:
			'Большая Vanilla+ сборка с новыми биомами, структурами, боссами и улучшенным исследованием.',
		description: [
			'Better MC расширяет почти каждую часть ванильного выживания: мир становится разнообразнее, путешествия — содержательнее, а прогресс получает больше целей.',
			'Это крупная сборка для длинного прохождения. Она подойдёт игрокам и авторам контента, которым нужен узнаваемый Minecraft, но с заметно большим количеством приключений.',
		],
		accent: '#fbbf24',
		iconUrl:
			'https://cdn.modrinth.com/data/shFhR8Vx/a19c2bcb51d38f32f138d3607e91cb2b7b8e387f_96.webp',
		links: [
			{ label: 'Страница сборки', url: 'https://modrinth.com/modpack/better-mc-fabric-bmc2' },
			{ label: 'Авторы', url: 'https://lunapixel.studio' },
		],
	},
	{
		slug: 'prominence-ii',
		projectId: 'EGs3lC8D',
		title: 'Prominence II: Hasturian Era',
		owner: 'LunaPixelStudios',
		ownerKind: 'Команда',
		tagline: 'Масштабная RPG-кампания с сюжетом, древом талантов, артефактами и сильными боссами.',
		description: [
			'Prominence II — сюжетная RPG-сборка для тех, кто хочет не просто выживать, а проходить большую кампанию. Таланты, классы, уникальное оружие и боссы образуют связанный путь развития.',
			'Сборка особенно хорошо подходит для сериалов, совместных прохождений и стримов: в ней много долгосрочных целей и зрелищных моментов.',
		],
		accent: '#c084fc',
		iconUrl:
			'https://cdn.modrinth.com/data/EGs3lC8D/00f31f1b678ed4cf3aee8c3aee79889afb4b8a1c_96.webp',
		links: [
			{ label: 'Страница сборки', url: 'https://modrinth.com/modpack/prominence-2-fabric' },
			{ label: 'Авторы', url: 'https://lunapixel.studio' },
		],
	},
]

export function recommendedPackBySlug(slug: string) {
	return recommendedPacks.find((pack) => pack.slug === slug)
}
