<script setup lang="ts">
import { ClipboardCopyIcon, ExternalIcon, GlobeIcon, SearchIcon, XIcon } from '@modrinth/assets'
import type { Category, GameVersion, Platform, ProjectType, SortType, Tags } from '@modrinth/ui'
import {
	Button,
	Checkbox,
	defineMessages,
	DropdownSelect,
	injectNotificationManager,
	LoadingIndicator,
	Pagination,
	SearchFilterControl,
	SearchSidebarFilter,
	useSearch,
	useVIntl,
} from '@modrinth/ui'
import { openUrl } from '@tauri-apps/plugin-opener'
import type { Ref } from 'vue'
import { computed, nextTick, ref, shallowRef, watch } from 'vue'
import type { LocationQuery } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'

import ContextMenu from '@/components/ui/ContextMenu.vue'
import type Instance from '@/components/ui/Instance.vue'
import InstanceIndicator from '@/components/ui/InstanceIndicator.vue'
import NavTabs from '@/components/ui/NavTabs.vue'
import SearchCard from '@/components/ui/SearchCard.vue'
import { get_search_results } from '@/helpers/cache.js'
import { get as getInstance, get_projects as getInstanceProjects } from '@/helpers/profile.js'
import { get_categories, get_game_versions, get_loaders } from '@/helpers/tags'
import { useBreadcrumbs } from '@/store/breadcrumbs'

const { handleError } = injectNotificationManager()
const { formatMessage } = useVIntl()

const router = useRouter()
const route = useRoute()

const projectTypes = computed(() => {
	return [route.params.projectType as ProjectType]
})

const [categories, loaders, availableGameVersions] = await Promise.all([
	get_categories().catch(handleError).then(ref),
	get_loaders().catch(handleError).then(ref),
	get_game_versions().catch(handleError).then(ref),
])

const categoryLabels: Record<string, string> = {
	adventure: 'Приключения',
	cursed: 'Безумные',
	decoration: 'Декор',
	economy: 'Экономика',
	equipment: 'Экипировка',
	food: 'Еда',
	game_mechanics: 'Игровая механика',
	library: 'Библиотеки',
	magic: 'Магия',
	management: 'Управление',
	minigame: 'Мини-игры',
	mobs: 'Мобы',
	optimization: 'Оптимизация',
	social: 'Социальные',
	storage: 'Хранилища',
	technology: 'Технологии',
	transportation: 'Транспорт',
	utility: 'Утилиты',
	worldgen: 'Генерация мира',
}

const localizedCategories = computed(() =>
	(categories.value ?? []).map((category: Category) => ({
		...category,
		formatted_name: categoryLabels[category.name] ?? category.formatted_name,
	})),
)

const tags: Ref<Tags> = computed(() => ({
	gameVersions: availableGameVersions.value as GameVersion[],
	loaders: loaders.value as Platform[],
	categories: localizedCategories.value as Category[],
}))

function filterLabel(filter: { id: string; formatted_name: string }) {
	if (filter.id.startsWith('category')) return 'Категории'
	if (filter.id === 'game_version') return 'Версия игры'
	if (filter.id === 'mod_loader') return 'Загрузчик'
	if (filter.id === 'environment') return 'Сторона запуска'
	if (filter.id === 'license') return 'Лицензия'
	return filter.formatted_name
}

function sortLabel(label?: string) {
	return ({ Relevance: 'Релевантность', Downloads: 'Загрузки', Follows: 'Подписки', Newest: 'Новые', Updated: 'Обновлённые' } as Record<string, string>)[label ?? ''] ?? label
}

type Instance = {
	game_version: string
	loader: string
	path: string
	install_stage: string
	icon_path?: string
	name: string
}

type InstanceProject = {
	metadata: {
		project_id: string
	}
}

const instance: Ref<Instance | null> = ref(null)
const instanceProjects: Ref<InstanceProject[] | null> = ref(null)
const instanceHideInstalled = ref(false)
const newlyInstalled = ref([])

const PERSISTENT_QUERY_PARAMS = ['i', 'ai']

await updateInstanceContext()

watch(
	() => route.query.i,
	() => updateInstanceContext(),
)

async function updateInstanceContext() {
	if (route.query.i) {
		;[instance.value, instanceProjects.value] = await Promise.all([
			getInstance(route.query.i).catch(handleError),
			getInstanceProjects(route.query.i).catch(handleError),
		])
		newlyInstalled.value = []
	}

	if (route.query.ai && !(projectTypes.value.length === 1 && projectTypes.value[0] === 'modpack')) {
		instanceHideInstalled.value = route.query.ai === 'true'
	}

	if (instance.value && instance.value.path !== route.query.i && route.path.startsWith('/browse')) {
		instance.value = null
		instanceHideInstalled.value = false
	}
}

const instanceFilters = computed(() => {
	const filters = []

	if (instance.value) {
		const gameVersion = instance.value.game_version
		if (gameVersion) {
			filters.push({
				type: 'game_version',
				option: gameVersion,
			})
		}

		const platform = instance.value.loader

		const supportedModLoaders = ['fabric', 'forge', 'quilt', 'neoforge']

		if (platform && projectTypes.value.includes('mod') && supportedModLoaders.includes(platform)) {
			filters.push({
				type: 'mod_loader',
				option: platform,
			})
		}

		if (instanceHideInstalled.value && instanceProjects.value) {
			const installedMods = Object.values(instanceProjects.value)
				.filter((x) => x?.metadata?.project_id)
				.map((x) => x.metadata.project_id)

			installedMods.push(...newlyInstalled.value)

			installedMods
				?.map((x) => ({
					type: 'project_id',
					option: `project_id:${x}`,
					negative: true,
				}))
				.forEach((x) => filters.push(x))
		}
	}

	return filters
})

const {
	// Selections
	query,
	currentSortType,
	currentFilters,
	toggledGroups,
	maxResults,
	currentPage,
	overriddenProvidedFilterTypes,

	// Lists
	filters,
	sortTypes,

	// Computed
	requestParams,

	// Functions
	createPageParams,
} = useSearch(projectTypes, tags, instanceFilters)

const previousFilterState = ref('')

const offline = ref(!navigator.onLine)
window.addEventListener('offline', () => {
	offline.value = true
})
window.addEventListener('online', () => {
	offline.value = false
})

const breadcrumbs = useBreadcrumbs()
breadcrumbs.setContext({ name: 'Discover content', link: route.path, query: route.query })

const loading = ref(true)

const projectType = ref(route.params.projectType)

watch(projectType, () => {
	loading.value = true
})

type SearchResult = {
	project_id: string
}

type SearchResults = {
	total_hits: number
	limit: number
	hits: SearchResult[]
}

const results: Ref<SearchResults | null> = shallowRef(null)
let searchRequestId = 0
const pageCount = computed(() =>
	results.value ? Math.ceil(results.value.total_hits / results.value.limit) : 1,
)

watch(requestParams, () => {
	if (!route.params.projectType) return
	refreshSearch()
})

async function refreshSearch() {
	const requestId = ++searchRequestId
	let rawResults
	try {
		rawResults = await get_search_results(requestParams.value)
	} catch (error) {
		if (requestId !== searchRequestId) return
		handleError(error)
		rawResults = null
	}
	if (requestId !== searchRequestId) return
	if (!rawResults) {
		rawResults = {
			result: {
				hits: [],
				total_hits: 0,
				limit: 1,
			},
		}
	}
	if (instance.value) {
		for (const val of rawResults.result.hits) {
			val.installed =
				newlyInstalled.value.includes(val.project_id) ||
				Object.values(instanceProjects.value ?? {}).some(
					(x) => x?.metadata?.project_id === val.project_id,
				)
		}
	}
	results.value = rawResults.result

	const currentFilterState = JSON.stringify({
		query: query.value,
		filters: currentFilters.value,
		sort: currentSortType.value,
		maxResults: maxResults.value,
		projectTypes: projectTypes.value,
	})

	if (previousFilterState.value && previousFilterState.value !== currentFilterState) {
		currentPage.value = 1
	}

	previousFilterState.value = currentFilterState

	const persistentParams: LocationQuery = {}

	for (const [key, value] of Object.entries(route.query)) {
		if (PERSISTENT_QUERY_PARAMS.includes(key)) {
			persistentParams[key] = value
		}
	}

	if (instanceHideInstalled.value) {
		persistentParams.ai = 'true'
	} else {
		delete persistentParams.ai
	}

	const params = {
		...persistentParams,
		...createPageParams(),
	}
	delete params.page
	delete params.o

	breadcrumbs.setContext({
		name: 'Discover content',
		link: `/browse/${projectType.value}`,
		query: params,
	})
	const nextRoute = router.resolve({ path: route.path, query: params })
	if (nextRoute.fullPath !== route.fullPath) {
		await router.replace({ path: route.path, query: params })
	}
	if (requestId !== searchRequestId) return
	loading.value = false
}

async function setPage(newPageNumber: number) {
	if (newPageNumber === currentPage.value || loading.value) return
	loading.value = true
	currentPage.value = newPageNumber

	await onSearchChangeToTop()
}

const searchWrapper: Ref<HTMLElement | null> = ref(null)

async function onSearchChangeToTop() {
	await nextTick()

	searchWrapper.value?.scrollTo({ top: 0, behavior: 'auto' })
}

function clearSearch() {
	query.value = ''
	currentPage.value = 1
}

watch(
	() => route.params.projectType,
	async (newType) => {
		// Check if the newType is not the same as the current value
		if (!newType || newType === projectType.value) return

		projectType.value = newType

		currentSortType.value = { display: 'Relevance', name: 'relevance' }
		query.value = ''
	},
)

const selectableProjectTypes = computed(() => {
	let dataPacks = false,
		mods = false,
		modpacks = false

	if (instance.value) {
		if (
			availableGameVersions.value.findIndex((x) => x.version === instance.value.game_version) <=
			availableGameVersions.value.findIndex((x) => x.version === '1.13')
		) {
			dataPacks = true
		}

		if (instance.value.loader !== 'vanilla') {
			mods = true
		}
	} else {
		dataPacks = true
		mods = true
		modpacks = true
	}

	const params: LocationQuery = {}

	if (route.query.i) {
		params.i = route.query.i
	}
	if (route.query.ai) {
		params.ai = route.query.ai
	}

	const links = [
		{ label: 'Modpacks', href: `/browse/modpack`, shown: modpacks },
		{ label: 'Mods', href: `/browse/mod`, shown: mods },
		{ label: 'Resource Packs', href: `/browse/resourcepack` },
		{ label: 'Data Packs', href: `/browse/datapack`, shown: dataPacks },
		{ label: 'Shaders', href: `/browse/shader` },
	]

	if (params) {
		return links.map((link) => {
			return {
				...link,
				href: {
					path: link.href,
					query: params,
				},
			}
		})
	}

	return links
})

const messages = defineMessages({
	gameVersionProvidedByInstance: {
		id: 'search.filter.locked.instance-game-version.title',
		defaultMessage: 'Game version is provided by the instance',
	},
	modLoaderProvidedByInstance: {
		id: 'search.filter.locked.instance-loader.title',
		defaultMessage: 'Loader is provided by the instance',
	},
	providedByInstance: {
		id: 'search.filter.locked.instance',
		defaultMessage: 'Provided by the instance',
	},
	syncFilterButton: {
		id: 'search.filter.locked.instance.sync',
		defaultMessage: 'Sync with instance',
	},
})

const options = ref(null)
const handleRightClick = (event, result) => {
	options.value.showMenu(event, result, [
		{
			name: 'open_link',
		},
		{
			name: 'copy_link',
		},
	])
}
const handleOptionsClick = (args) => {
	switch (args.option) {
		case 'open_link':
			openUrl(`https://modrinth.com/${args.item.project_type}/${args.item.slug}`)
			break
		case 'copy_link':
			navigator.clipboard.writeText(
				`https://modrinth.com/${args.item.project_type}/${args.item.slug}`,
			)
			break
	}
}

await refreshSearch()

// Initialize previousFilterState after first search
previousFilterState.value = JSON.stringify({
	query: query.value,
	filters: currentFilters.value,
	sort: currentSortType.value,
	maxResults: maxResults.value,
	projectTypes: projectTypes.value,
})
</script>

<template>
	<Teleport v-if="filters && instance" to="#sidebar-teleport-target">
		<div
			v-if="instance"
			class="border-0 border-b-[1px] p-4 last:border-b-0 border-[--brand-gradient-border] border-solid"
		>
			<Checkbox
				v-model="instanceHideInstalled"
				label="Скрыть установленное"
				class="filter-checkbox"
				@update:model-value="onSearchChangeToTop()"
				@click.prevent.stop
			/>
		</div>
		<SearchSidebarFilter
			v-for="filter in filters.filter((f) => f.display !== 'none')"
			:key="`filter-${filter.id}`"
			v-model:selected-filters="currentFilters"
			v-model:toggled-groups="toggledGroups"
			v-model:overridden-provided-filter-types="overriddenProvidedFilterTypes"
			:provided-filters="instanceFilters"
			:filter-type="filter"
			class="border-0 border-b-[1px] [&:first-child>button]:pt-4 last:border-b-0 border-[--brand-gradient-border] border-solid"
			button-class="button-animation flex flex-col gap-1 px-4 py-3 w-full bg-transparent cursor-pointer border-none hover:bg-button-bg"
			content-class="mb-3"
			inner-panel-class="ml-2 mr-3"
			:open-by-default="
				filter.id.startsWith('category') || filter.id === 'environment' || filter.id === 'license'
			"
		>
			<template #header>
				<h3 class="text-base m-0">{{ filterLabel(filter) }}</h3>
			</template>
			<template #locked-game_version>
				{{ formatMessage(messages.gameVersionProvidedByInstance) }}
			</template>
			<template #locked-mod_loader>
				{{ formatMessage(messages.modLoaderProvidedByInstance) }}
			</template>
			<template #sync-button> {{ formatMessage(messages.syncFilterButton) }} </template>
		</SearchSidebarFilter>
	</Teleport>
	<main ref="searchWrapper" class="browse-page">
		<template v-if="instance">
			<InstanceIndicator :instance="instance" />
			<h1 class="m-0 mb-1 text-xl">Установка контента в сборку</h1>
		</template>
		<section class="browse-hero">
			<div class="browse-heading">
				<p>КАТАЛОГ КОНТЕНТА</p>
				<h1>{{ instance ? 'Контент для сборки' : 'Моды' }}</h1>
				<span>Дополняйте игру проверенными модами, шейдерами и ресурсами.</span>
			</div>
			<NavTabs class="browse-tabs" :links="selectableProjectTypes" />
			<div class="iconified-input browse-search">
				<SearchIcon aria-hidden="true" class="text-lg" />
				<input
					v-model="query"
					class="h-12"
					autocomplete="off"
					spellcheck="false"
					type="text"
					:placeholder="`Найти ${projectType === 'mod' ? 'мод' : 'контент'}...`"
				/>
				<Button v-if="query" class="r-btn" @click="() => clearSearch()"><XIcon /></Button>
			</div>
		</section>

		<div class="browse-layout">
			<aside v-if="filters" class="browse-filters">
				<div class="filter-title"><span>Фильтры</span><small>{{ results?.total_hits ?? 0 }} проектов</small></div>
				<SearchSidebarFilter
					v-for="filter in filters.filter((item) => item.display !== 'none')"
					:key="`main-filter-${filter.id}`"
					v-model:selected-filters="currentFilters"
					v-model:toggled-groups="toggledGroups"
					v-model:overridden-provided-filter-types="overriddenProvidedFilterTypes"
					:provided-filters="instanceFilters"
					:filter-type="filter"
					class="cinematic-filter"
					button-class="button-animation flex flex-col gap-1 px-3 py-3 w-full bg-transparent cursor-pointer border-none hover:bg-button-bg"
					content-class="mb-3"
					inner-panel-class="mx-3"
					:open-by-default="filter.id.startsWith('category') || filter.id === 'environment'"
				>
					<template #header><h3 class="text-sm m-0">{{ filterLabel(filter) }}</h3></template>
					<template #locked-game_version>{{ formatMessage(messages.gameVersionProvidedByInstance) }}</template>
					<template #locked-mod_loader>{{ formatMessage(messages.modLoaderProvidedByInstance) }}</template>
					<template #sync-button>{{ formatMessage(messages.syncFilterButton) }}</template>
				</SearchSidebarFilter>
			</aside>

			<section class="browse-results">
		<div class="browse-toolbar">
			<DropdownSelect
				v-slot="{ selected }"
				v-model="currentSortType"
				class="max-w-[16rem]"
				name="Сортировка"
				:options="sortTypes as any"
				:display-name="(option: SortType | undefined) => sortLabel(option?.display)"
			>
				<span class="font-semibold text-primary">Сортировка: </span>
				<span class="font-semibold text-secondary">{{ sortLabel(selected) }}</span>
			</DropdownSelect>
			<DropdownSelect
				v-slot="{ selected }"
				v-model="maxResults"
				name="Количество результатов"
				:options="[5, 10, 15, 20, 50, 100]"
				class="max-w-[9rem]"
			>
				<span class="font-semibold text-primary">Показывать: </span>
				<span class="font-semibold text-secondary">{{ selected }}</span>
			</DropdownSelect>
			<Pagination :page="currentPage" :count="pageCount" class="ml-auto" @switch-page="setPage" />
		</div>
		<SearchFilterControl
			v-model:selected-filters="currentFilters"
			:filters="filters.filter((f) => f.display !== 'none')"
			:provided-filters="instanceFilters"
			:overridden-provided-filter-types="overriddenProvidedFilterTypes"
			:provided-message="messages.providedByInstance"
		/>
		<div class="search browse-search-results">
			<section v-if="loading" class="offline">
				<LoadingIndicator />
			</section>
			<section v-else-if="offline && results.total_hits === 0" class="offline">
				Каталог недоступен без сети. Проверьте подключение к интернету и повторите попытку.
			</section>
			<section v-else class="project-list display-mode--list instance-results" role="list">
				<SearchCard
					v-for="result in results.hits"
					:key="result?.project_id"
					:project="result"
					:instance="instance"
					:categories="[
						...localizedCategories.filter(
							(cat) =>
								result?.display_categories.includes(cat.name) && cat.project_type === projectType,
						),
						...loaders.filter(
							(loader) =>
								result?.display_categories.includes(loader.name) &&
								loader.supported_project_types?.includes(projectType),
						),
					]"
					:installed="result.installed || newlyInstalled.includes(result.project_id)"
					@install="
						(id) => {
							newlyInstalled.push(id)
						}
					"
					@contextmenu.prevent.stop="(event) => handleRightClick(event, result)"
				/>
				<ContextMenu ref="options" @option-clicked="handleOptionsClick">
					<template #open_link> <GlobeIcon /> Открыть страницу <ExternalIcon /> </template>
					<template #copy_link> <ClipboardCopyIcon /> Копировать ссылку </template>
				</ContextMenu>
			</section>
			<div class="flex justify-end">
				<pagination
					:page="currentPage"
					:count="pageCount"
					class="pagination-after"
					@switch-page="setPage"
				/>
			</div>
		</div>
			</section>
		</div>
	</main>
</template>

<style lang="scss" scoped>
.browse-page {
	min-height: 100%;
	padding: 1.5rem 2rem 2rem;
	box-sizing: border-box;
	overflow-y: auto;
	background:
		radial-gradient(circle at 74% -8%, rgba(126, 34, 206, 0.2), transparent 34rem),
		#060a12;
}

.browse-hero {
	display: grid;
	grid-template-columns: minmax(13rem, 0.7fr) minmax(26rem, 1.3fr);
	gap: 1rem 2rem;
	align-items: end;
	padding: 1.35rem 1.5rem;
	border: 1px solid rgba(168, 85, 247, 0.24);
	border-radius: 1.15rem;
	background:
		linear-gradient(115deg, rgba(22, 13, 38, 0.88), rgba(10, 14, 24, 0.92)),
		radial-gradient(circle at 86% 20%, rgba(168, 85, 247, 0.3), transparent 24rem);
	box-shadow: 0 20px 54px rgba(0, 0, 0, 0.26);
}

.browse-heading { grid-row: span 2; align-self: center; }
.browse-heading p { margin: 0 0 0.45rem; color: #c084fc; font-size: 0.7rem; font-weight: 750; letter-spacing: 0.14em; }
.browse-heading h1 { margin: 0; color: #fff; font-size: 2.55rem; line-height: 1; }
.browse-heading span { display: block; max-width: 28rem; margin-top: 0.7rem; color: rgba(226, 232, 240, 0.68); }
.browse-tabs { justify-self: end; }

.browse-search { width: 100%; }
.browse-search input {
	border: 1px solid rgba(255, 255, 255, 0.1) !important;
	background: rgba(3, 6, 12, 0.74) !important;
	box-shadow: 0 10px 28px rgba(0, 0, 0, 0.2);
}

.browse-layout {
	display: grid;
	grid-template-columns: 15.5rem minmax(0, 1fr);
	gap: 1rem;
	margin-top: 1rem;
}

.browse-filters,
.browse-results {
	border: 1px solid rgba(255, 255, 255, 0.08);
	border-radius: 1.05rem;
	background: rgba(12, 17, 27, 0.88);
}

.browse-filters { align-self: start; overflow: hidden; }
.filter-title { display: flex; align-items: center; justify-content: space-between; padding: 1rem; border-bottom: 1px solid rgba(255, 255, 255, 0.08); }
.filter-title span { color: #fff; font-weight: 750; }
.filter-title small { color: rgba(226, 232, 240, 0.52); }
.cinematic-filter { border-bottom: 1px solid rgba(255, 255, 255, 0.06); }
.browse-results { min-width: 0; padding: 1rem; }
.browse-toolbar { display: flex; gap: 0.5rem; }
.browse-search-results { margin-top: 0.75rem; }

:deep(.instance-results) { display: grid; grid-template-columns: minmax(0, 1fr); gap: 0.7rem; }
:deep(.instance-results > div) { min-height: 8rem; border: 1px solid rgba(255, 255, 255, 0.07); background: rgba(18, 24, 36, 0.92) !important; box-shadow: none; }

@media (max-width: 1120px) {
	.browse-page { padding-inline: 1rem; }
	.browse-hero { grid-template-columns: 1fr; }
	.browse-heading { grid-row: auto; }
	.browse-tabs { justify-self: start; }
	.browse-layout { grid-template-columns: 13rem minmax(0, 1fr); }
}

@media (prefers-reduced-motion: no-preference) {
	.browse-hero,
	.browse-layout { animation: browse-enter 350ms cubic-bezier(0.22, 1, 0.36, 1) both; }
	.browse-layout { animation-delay: 70ms; }
}

@keyframes browse-enter {
	from { opacity: 0; transform: translateY(8px); }
	to { opacity: 1; transform: translateY(0); }
}
</style>
