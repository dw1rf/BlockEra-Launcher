<script setup lang="ts">
import { ClipboardCopyIcon, ExternalIcon, GlobeIcon, SearchIcon, XIcon } from '@modrinth/assets'
import type { Category, GameVersion, Platform, ProjectType, SortType, Tags } from '@modrinth/ui'
import {
	Button,
	Checkbox,
	defineMessages,
	DropdownSelect,
	injectNotificationManager,
	Pagination,
	SearchFilterControl,
	SearchSidebarFilter,
	useSearch,
	useVIntl,
} from '@modrinth/ui'
import { openUrl } from '@tauri-apps/plugin-opener'
import type { Ref } from 'vue'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import type { LocationQuery } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'

import ContextMenu from '@/components/ui/ContextMenu.vue'
import type Instance from '@/components/ui/Instance.vue'
import InstanceIndicator from '@/components/ui/InstanceIndicator.vue'
import NavTabs from '@/components/ui/NavTabs.vue'
import SearchCard from '@/components/ui/SearchCard.vue'
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
	return (
		(
			{
				Relevance: 'Релевантность',
				Downloads: 'Загрузки',
				Follows: 'Подписки',
				Newest: 'Новые',
				Updated: 'Обновлённые',
			} as Record<string, string>
		)[label ?? ''] ?? label
	)
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
type SearchState = 'idle' | 'loading' | 'success' | 'error'
const searchState = ref<SearchState>('idle')
const searchError = ref('')
const SEARCH_TIMEOUT_MS = 15_000

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
let activeSearchController: AbortController | null = null
const pageCount = computed(() =>
	results.value ? Math.ceil(results.value.total_hits / results.value.limit) : 1,
)

const filterState = computed(() =>
	JSON.stringify({
		query: query.value,
		filters: currentFilters.value,
		sort: currentSortType.value,
		maxResults: maxResults.value,
		projectTypes: projectTypes.value,
	}),
)

watch(filterState, (nextState, previousState) => {
	if (previousState && nextState !== previousState && currentPage.value !== 1) {
		currentPage.value = 1
	}
})

watch(requestParams, () => {
	if (!route.params.projectType) return
	void refreshSearch()
})

watch(
	() => [route.query.page, route.query.o],
	([page, legacyOffset]) => {
		const routePage = page
			? Number(Array.isArray(page) ? page[0] : page)
			: legacyOffset
				? Math.floor(
						Number(Array.isArray(legacyOffset) ? legacyOffset[0] : legacyOffset) / maxResults.value,
					) + 1
				: 1
		if (Number.isFinite(routePage) && routePage > 0 && routePage !== currentPage.value) {
			currentPage.value = routePage
		}
	},
)

function getSearchErrorMessage(error: unknown) {
	if (error instanceof Error && error.message) return error.message
	if (typeof error === 'string') return error
	try {
		return JSON.stringify(error)
	} catch {
		return String(error)
	}
}

function syncSearchUrl() {
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
	delete params.o

	breadcrumbs.setContext({
		name: 'Discover content',
		link: `/browse/${projectType.value}`,
		query: params,
	})
	const nextRoute = router.resolve({ path: route.path, query: params })
	if (nextRoute.fullPath !== route.fullPath) {
		window.history.replaceState(
			{ ...(window.history.state ?? {}), current: nextRoute.fullPath, replaced: true },
			'',
			nextRoute.href,
		)
	}
}

async function fetchSearchResults(params: string, requestId: number): Promise<SearchResults> {
	activeSearchController?.abort()
	const controller = new AbortController()
	activeSearchController = controller
	let timeoutId: ReturnType<typeof setTimeout> | undefined
	const timeout = new Promise<never>((_, reject) => {
		timeoutId = setTimeout(() => {
			controller.abort()
			reject(new Error('Сервер каталога не ответил за 15 секунд.'))
		}, SEARCH_TIMEOUT_MS)
	})

	try {
		const response = await Promise.race([
			fetch(`https://api.modrinth.com/v2/search${params}`, {
				method: 'GET',
				headers: { Accept: 'application/json' },
				signal: controller.signal,
			}).then(async (response) => {
				if (!response.ok) {
					throw new Error(`Сервер каталога вернул ошибку ${response.status}.`)
				}
				return (await response.json()) as SearchResults
			}),
			timeout,
		])
		return response
	} catch (error) {
		if (controller.signal.aborted && requestId === searchRequestId) {
			throw new Error('Сервер каталога не ответил за 15 секунд.')
		}
		throw error
	} finally {
		if (timeoutId) clearTimeout(timeoutId)
		if (activeSearchController === controller) activeSearchController = null
	}
}

async function refreshSearch() {
	const requestId = ++searchRequestId
	loading.value = true
	searchState.value = 'loading'
	searchError.value = ''
	try {
		syncSearchUrl()
		const rawResults = await fetchSearchResults(requestParams.value, requestId)
		if (requestId !== searchRequestId) return
		const normalizedResults = rawResults ?? {
			hits: [],
			total_hits: 0,
			limit: maxResults.value,
		}

		if (instance.value) {
			for (const val of normalizedResults.hits) {
				val.installed =
					newlyInstalled.value.includes(val.project_id) ||
					Object.values(instanceProjects.value ?? {}).some(
						(x) => x?.metadata?.project_id === val.project_id,
					)
			}
		}
		results.value = normalizedResults
		searchState.value = 'success'
	} catch (error) {
		if (requestId !== searchRequestId) return
		searchState.value = 'error'
		searchError.value = getSearchErrorMessage(error)
		console.error('Не удалось загрузить каталог:', error)
	} finally {
		if (requestId === searchRequestId) loading.value = false
	}
}

onBeforeUnmount(() => {
	searchRequestId += 1
	activeSearchController?.abort()
})

async function setPage(newPageNumber: number) {
	if (newPageNumber === currentPage.value || newPageNumber < 1) return
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

void refreshSearch()
</script>

<template>
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
				<div v-if="instance" class="instance-filter-toggle">
					<Checkbox
						v-model="instanceHideInstalled"
						label="Скрыть установленное"
						class="filter-checkbox"
						@update:model-value="onSearchChangeToTop()"
					/>
				</div>
				<div class="filter-title">
					<span>Фильтры</span><small>{{ results?.total_hits ?? 0 }} проектов</small>
				</div>
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
					<template #header
						><h3 class="text-sm m-0">{{ filterLabel(filter) }}</h3></template
					>
					<template #locked-game_version>{{
						formatMessage(messages.gameVersionProvidedByInstance)
					}}</template>
					<template #locked-mod_loader>{{
						formatMessage(messages.modLoaderProvidedByInstance)
					}}</template>
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
					<Pagination
						:page="currentPage"
						:count="pageCount"
						class="ml-auto"
						@switch-page="setPage"
					/>
				</div>
				<SearchFilterControl
					v-model:selected-filters="currentFilters"
					:filters="filters.filter((f) => f.display !== 'none')"
					:provided-filters="instanceFilters"
					:overridden-provided-filter-types="overriddenProvidedFilterTypes"
					:provided-message="messages.providedByInstance"
				/>
				<div class="search browse-search-results">
					<section v-if="loading" class="browse-loading" role="status" aria-live="polite">
						<div class="browse-loading-heading">
							<span class="browse-loading-spinner" aria-hidden="true"></span>
							<div>
								<strong>Загружаем каталог</strong>
								<small>Подбираем совместимый контент для вашей сборки</small>
							</div>
						</div>
						<div v-for="index in 3" :key="index" class="browse-loading-card" aria-hidden="true">
							<span class="browse-loading-icon"></span>
							<div><i></i><i></i><i></i></div>
						</div>
					</section>
					<section v-else-if="searchState === 'error'" class="offline browse-error" role="alert">
						<strong>Не удалось загрузить каталог</strong>
						<span>{{ searchError }}</span>
						<Button @click="refreshSearch">Повторить</Button>
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
										result?.display_categories.includes(cat.name) &&
										cat.project_type === projectType,
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
		radial-gradient(circle at 74% -8%, rgba(126, 34, 206, 0.2), transparent 34rem), #060a12;
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

.browse-heading {
	grid-row: span 2;
	align-self: center;
}
.browse-heading p {
	margin: 0 0 0.45rem;
	color: #c084fc;
	font-size: 0.7rem;
	font-weight: 750;
	letter-spacing: 0.14em;
}
.browse-heading h1 {
	margin: 0;
	color: #fff;
	font-size: 2.55rem;
	line-height: 1;
}
.browse-heading span {
	display: block;
	max-width: 28rem;
	margin-top: 0.7rem;
	color: rgba(226, 232, 240, 0.68);
}
.browse-tabs {
	justify-self: end;
}

.browse-search {
	width: 100%;
}
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

.browse-filters {
	align-self: start;
	overflow: hidden;
}
.instance-filter-toggle {
	padding: 0.9rem 1rem;
	border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.filter-title {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 1rem;
	border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}
.filter-title span {
	color: #fff;
	font-weight: 750;
}
.filter-title small {
	color: rgba(226, 232, 240, 0.52);
}
.cinematic-filter {
	border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}
.browse-results {
	min-width: 0;
	padding: 1rem;
}
.browse-toolbar {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	gap: 0.5rem;
}
.browse-search-results {
	margin-top: 0.75rem;
}
.browse-loading {
	display: grid;
	gap: 0.7rem;
	min-height: 24rem;
}
.browse-loading-heading {
	display: flex;
	align-items: center;
	gap: 0.8rem;
	padding: 0.25rem 0.15rem 0.45rem;
}
.browse-loading-heading div {
	display: flex;
	flex-direction: column;
	gap: 0.2rem;
}
.browse-loading-heading strong {
	color: #f8fafc;
	font-size: 0.95rem;
}
.browse-loading-heading small {
	color: rgba(226, 232, 240, 0.56);
}
.browse-loading-spinner {
	width: 1.25rem;
	height: 1.25rem;
	border: 2px solid rgba(192, 132, 252, 0.22);
	border-top-color: #c084fc;
	border-radius: 50%;
	animation: browse-spin 700ms linear infinite;
}
.browse-loading-card {
	display: grid;
	grid-template-columns: 5.4rem minmax(0, 1fr);
	gap: 1rem;
	min-height: 7.2rem;
	padding: 1rem;
	box-sizing: border-box;
	border: 1px solid rgba(255, 255, 255, 0.055);
	border-radius: 0.9rem;
	background: rgba(18, 24, 36, 0.72);
	overflow: hidden;
}
.browse-loading-icon,
.browse-loading-card i {
	position: relative;
	display: block;
	overflow: hidden;
	border-radius: 0.55rem;
	background: rgba(255, 255, 255, 0.055);
}
.browse-loading-icon::after,
.browse-loading-card i::after {
	position: absolute;
	inset: 0;
	content: '';
	background: linear-gradient(
		100deg,
		transparent 20%,
		rgba(192, 132, 252, 0.12) 50%,
		transparent 80%
	);
	transform: translateX(-100%);
	animation: browse-shimmer 1.4s ease-in-out infinite;
}
.browse-loading-card > div {
	display: flex;
	flex-direction: column;
	gap: 0.65rem;
	padding-top: 0.15rem;
}
.browse-loading-card i:nth-child(1) {
	width: min(15rem, 55%);
	height: 1.05rem;
}
.browse-loading-card i:nth-child(2) {
	width: min(34rem, 90%);
	height: 0.75rem;
}
.browse-loading-card i:nth-child(3) {
	width: min(22rem, 65%);
	height: 0.75rem;
}
.browse-error {
	display: flex;
	min-height: 13rem;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: 0.75rem;
	text-align: center;
}
.browse-error strong {
	color: #fff;
	font-size: 1.05rem;
}
.browse-error span {
	max-width: 34rem;
	color: rgba(226, 232, 240, 0.66);
	overflow-wrap: anywhere;
}

:deep(.instance-results) {
	display: grid;
	grid-template-columns: minmax(0, 1fr);
	gap: 0.7rem;
}
:deep(.instance-results > div) {
	min-height: 8rem;
	border: 1px solid rgba(255, 255, 255, 0.07);
	background: rgba(18, 24, 36, 0.92) !important;
	box-shadow: none;
}

@media (max-width: 1120px) {
	.browse-page {
		padding-inline: 1rem;
	}
	.browse-hero {
		grid-template-columns: 1fr;
	}
	.browse-heading {
		grid-row: auto;
	}
	.browse-tabs {
		justify-self: start;
	}
	.browse-layout {
		grid-template-columns: 13rem minmax(0, 1fr);
	}
}

@media (prefers-reduced-motion: no-preference) {
	.browse-hero,
	.browse-layout {
		animation: browse-enter 350ms cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.browse-layout {
		animation-delay: 70ms;
	}
}

@keyframes browse-enter {
	from {
		opacity: 0;
		transform: translateY(8px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}
@keyframes browse-spin {
	to {
		transform: rotate(360deg);
	}
}
@keyframes browse-shimmer {
	to {
		transform: translateX(100%);
	}
}

@media (prefers-reduced-motion: reduce) {
	.browse-loading-spinner,
	.browse-loading-icon::after,
	.browse-loading-card i::after {
		animation: none;
	}
}
</style>
