<template>
	<div class="blockera-content-manager">
		<div v-if="loadingProjects" class="blockera-empty-state blockera-loading-state">
			<LoadingIndicator />
			<h2>Загружаем контент сборки</h2>
			<p>Проверяем установленные моды, текстуры и шейдеры.</p>
		</div>
		<div v-else-if="projectsError" class="blockera-empty-state" role="alert">
			<div class="empty-state-icon"><UpdatedIcon /></div>
			<span>КОНТЕНТ СБОРКИ</span>
			<h2>Не удалось загрузить список</h2>
			<p>{{ projectsError }}</p>
			<div class="empty-state-actions">
				<ButtonStyled
					><button @click="refreshProjects"><UpdatedIcon /> Повторить</button></ButtonStyled
				>
				<AddContentButton :instance="instance" />
			</div>
		</div>
		<template v-else-if="projects?.length > 0">
			<section v-if="updateReport" class="update-report" aria-live="polite">
				<div>
					<strong>Итоги обновления</strong>
					<span
						>Успешно: {{ updateReport.items.filter((item) => item.status === 'success').length }} ·
						Ошибок: {{ updateReport.items.filter((item) => item.status === 'error').length }} ·
						Пропущено: {{ updateReport.items.filter((item) => item.status === 'skipped').length }} ·
						Осталось: {{ updateReport.remainingUpdates ?? 'не проверено' }}</span
					>
				</div>
				<ul>
					<li v-for="item in updateReport.items" :key="item.path" :class="`is-${item.status}`">
						{{ item.name }} —
						{{
							item.status === 'success'
								? `установлена ${item.installedVersion ?? 'новая версия'}`
								: (item.error ?? 'пропущено')
						}}
					</li>
				</ul>
				<button
					v-if="updateReport.items.some((item) => item.status === 'error')"
					type="button"
					@click="retryFailedUpdates"
				>
					Повторить неудачные
				</button>
			</section>
			<div class="flex items-center gap-2 mb-4">
				<div class="iconified-input flex-grow">
					<SearchIcon />
					<input
						v-model="searchFilter"
						type="text"
						:placeholder="`Поиск по ${filteredProjects.length} проектам…`"
						class="text-input search-input"
						autocomplete="off"
					/>
					<Button
						v-tooltip="'Очистить поиск'"
						class="r-btn"
						aria-label="Очистить поиск"
						@click="() => (searchFilter = '')"
					>
						<XIcon />
					</Button>
				</div>
				<AddContentButton :instance="instance" />
			</div>
			<div class="flex items-center justify-between">
				<div v-if="filterOptions.length > 1" class="flex flex-wrap gap-1 items-center pb-4">
					<FilterIcon class="text-secondary h-5 w-5 mr-1" />
					<button
						v-for="filter in filterOptions"
						:key="`content-filter-${filter.id}`"
						:class="`px-2 py-1 rounded-full font-semibold leading-none border-none cursor-pointer active:scale-[0.97] duration-100 transition-all ${selectedFilters.includes(filter.id) ? 'bg-brand-highlight text-brand' : 'bg-bg-raised text-secondary'}`"
						@click="toggleArray(selectedFilters, filter.id)"
					>
						{{ filter.formattedName }}
					</button>
				</div>
				<Pagination
					v-if="search.length > 0"
					:page="currentPage"
					:count="Math.ceil(search.length / 20)"
					:link-function="(page) => `?page=${page}`"
					@switch-page="(page) => (currentPage = page)"
				/>
			</div>

			<ContentListPanel
				v-model="selectedFiles"
				:locked="isPackLocked"
				:items="
					search.map((x) => {
						const item: ContentItem<any> = {
							path: x.path,
							disabled: x.disabled,
							filename: x.file_name,
							icon: x.icon ?? undefined,
							title: x.name,
							data: x,
						}

						if (x.version) {
							item.version = x.version
							item.versionId = x.version
						}

						if (x.id) {
							item.project = {
								id: x.id,
								link: {
									path: `/project/${x.id}`,
									query: { i: props.instance.path },
								},
								linkProps: {},
							}
						}

						if (x.author) {
							item.creator = {
								name: x.author.name,
								type: x.author.type,
								id: x.author.slug,
								link: `https://modrinth.com/${x.author.type}/${x.author.slug}`,
								linkProps: { target: '_blank' },
							}
						}

						return item
					})
				"
				:sort-column="sortColumn"
				:sort-ascending="ascending"
				:update-sort="sortProjects"
				:current-page="currentPage"
			>
				<template v-if="selectedProjects.length > 0" #headers>
					<div class="flex gap-2">
						<ButtonStyled
							v-if="!isPackLocked && selectedProjects.some((m) => m.outdated)"
							color="brand"
							color-fill="text"
							hover-color-fill="text"
						>
							<button @click="updateSelected()"><DownloadIcon /> Обновить</button>
						</ButtonStyled>
						<ButtonStyled>
							<OverflowMenu
								:options="[
									{
										id: 'share-names',
										action: () => shareNames(),
									},
									{
										id: 'share-file-names',
										action: () => shareFileNames(),
									},
									{
										id: 'share-urls',
										action: () => shareUrls(),
									},
									{
										id: 'share-markdown',
										action: () => shareMarkdown(),
									},
								]"
							>
								<ShareIcon /> Поделиться <DropdownIcon />
								<template #share-names> <TextInputIcon /> Project names </template>
								<template #share-file-names> <FileIcon /> File names </template>
								<template #share-urls> <LinkIcon /> Project links </template>
								<template #share-markdown> <CodeIcon /> Markdown links </template>
							</OverflowMenu>
						</ButtonStyled>
						<ButtonStyled v-if="selectedProjects.some((m) => m.disabled)">
							<button @click="enableAll()"><CheckCircleIcon /> Включить</button>
						</ButtonStyled>
						<ButtonStyled v-if="selectedProjects.some((m) => !m.disabled)">
							<button @click="disableAll()"><SlashIcon /> Отключить</button>
						</ButtonStyled>
						<ButtonStyled color="red">
							<button @click="deleteSelected()"><TrashIcon /> Удалить</button>
						</ButtonStyled>
					</div>
				</template>
				<template #header-actions>
					<ButtonStyled type="transparent" color-fill="text" hover-color-fill="text">
						<button :disabled="refreshingProjects" class="w-max" @click="refreshProjects">
							<UpdatedIcon />
							Обновить список
						</button>
					</ButtonStyled>
					<ButtonStyled
						v-if="!isPackLocked && projects.some((m) => (m as any).outdated)"
						type="transparent"
						color="brand"
						color-fill="text"
						hover-color-fill="text"
						@click="() => updateAll()"
					>
						<button class="w-max"><DownloadIcon /> Обновить всё</button>
					</ButtonStyled>
					<ButtonStyled
						v-if="canUpdatePack"
						type="transparent"
						color="brand"
						color-fill="text"
						hover-color-fill="text"
					>
						<button class="w-max" :disabled="installing" @click="modpackVersionModal?.show()">
							<DownloadIcon /> Обновить сборку
						</button>
					</ButtonStyled>
				</template>
				<template #actions="{ item }">
					<ButtonStyled
						v-if="!isPackLocked && (item.data as any).outdated"
						type="transparent"
						color="brand"
						circular
					>
						<button
							v-tooltip="`Обновить`"
							aria-label="Обновить проект"
							:disabled="(item.data as ProjectListEntry).updating"
							@click="updateProject(item.data)"
						>
							<DownloadIcon />
						</button>
					</ButtonStyled>
					<div v-else class="w-[36px]"></div>
					<Toggle
						class="!mx-2"
						:model-value="!item.data.disabled"
						:disabled="togglingFiles.has(item.data.file_name)"
						@update:model-value="toggleDisableMod(item.data)"
					/>
					<ButtonStyled type="transparent" circular>
						<button v-tooltip="'Удалить'" aria-label="Удалить проект" @click="removeMod(item)">
							<TrashIcon />
						</button>
					</ButtonStyled>

					<ButtonStyled type="transparent" circular>
						<OverflowMenu
							:options="[
								{
									id: 'show-file',
									action: () => highlightModInProfile(instance.path, item.path),
								},
								{
									id: 'copy-link',
									shown: item.data !== undefined && item.data.slug !== undefined,
									action: () => copyModLink(item),
								},
							]"
							direction="left"
						>
							<MoreVerticalIcon />
							<template #show-file> <ExternalIcon /> Показать файл </template>
							<template #copy-link> <ClipboardCopyIcon /> Копировать ссылку </template>
						</OverflowMenu>
					</ButtonStyled>
				</template>
			</ContentListPanel>
			<div class="flex justify-end mt-4">
				<Pagination
					v-if="search.length > 0"
					:page="currentPage"
					:count="Math.ceil(search.length / 20)"
					:link-function="(page) => `?page=${page}`"
					@switch-page="(page) => (currentPage = page)"
				/>
			</div>
		</template>
		<div v-else class="blockera-empty-state">
			<div class="empty-state-icon"><PlusIcon /></div>
			<span>БИБЛИОТЕКА СБОРКИ</span>
			<h2>Здесь пока нет контента</h2>
			<p>Добавьте моды, текстуры, шейдеры или датапаки из каталога BlockEra.</p>
			<div>
				<AddContentButton :instance="instance" />
			</div>
		</div>
		<ShareModalWrapper
			ref="shareModal"
			share-title="Sharing modpack content"
			share-text="Check out the projects I'm using in my modpack!"
			:open-in-new-tab="false"
		/>
		<ExportModal v-if="projects.length > 0" ref="exportModal" :instance="instance" />
		<ModpackVersionModal
			v-if="instance.linked_data"
			ref="modpackVersionModal"
			:instance="instance"
			:versions="props.versions"
		/>
	</div>
</template>
<script setup lang="ts">
import {
	CheckCircleIcon,
	ClipboardCopyIcon,
	CodeIcon,
	DownloadIcon,
	DropdownIcon,
	ExternalIcon,
	FileIcon,
	FilterIcon,
	LinkIcon,
	MoreVerticalIcon,
	PlusIcon,
	SearchIcon,
	ShareIcon,
	SlashIcon,
	TrashIcon,
	UpdatedIcon,
	XIcon,
} from '@modrinth/assets'
import {
	Button,
	ButtonStyled,
	ContentListPanel,
	injectNotificationManager,
	LoadingIndicator,
	OverflowMenu,
	Pagination,
	Toggle,
} from '@modrinth/ui'
import type { ContentItem } from '@modrinth/ui/src/components/content/ContentListItem.vue'
import type { Organization, Project, TeamMember, Version } from '@modrinth/utils'
import { formatProjectType } from '@modrinth/utils'
import { getCurrentWebview } from '@tauri-apps/api/webview'
import { useStorage } from '@vueuse/core'
import dayjs from 'dayjs'
import type { ComputedRef } from 'vue'
import { computed, onUnmounted, ref, watch } from 'vue'

import { TextInputIcon } from '@/assets/icons'
import AddContentButton from '@/components/ui/AddContentButton.vue'
import type ContextMenu from '@/components/ui/ContextMenu.vue'
import ExportModal from '@/components/ui/ExportModal.vue'
import ShareModalWrapper from '@/components/ui/modal/ShareModalWrapper.vue'
import ModpackVersionModal from '@/components/ui/ModpackVersionModal.vue'
import { trackEvent } from '@/helpers/analytics'
import {
	get_organization_many,
	get_project_many,
	get_team_many,
	get_version_many,
} from '@/helpers/cache.js'
import { profile_listener } from '@/helpers/events.js'
import { type InstanceUpdateReport,runInstanceUpdate } from '@/helpers/instance-update'
import {
	add_project_from_path,
	get_projects,
	remove_project,
	toggle_disable_project,
} from '@/helpers/profile.js'
import type { CacheBehaviour, ContentFile, GameInstance } from '@/helpers/types'
import { highlightModInProfile } from '@/helpers/utils.js'

const { handleError } = injectNotificationManager()

const props = defineProps<{
	instance: GameInstance
	options: InstanceType<typeof ContextMenu>
	offline: boolean
	playing: boolean
	versions: Version[]
	installed: boolean
}>()

type ProjectListEntryAuthor = {
	name: string
	slug: string
	type: 'user' | 'organization'
}

type ProjectListEntry = {
	path: string
	name: string
	slug?: string
	author: ProjectListEntryAuthor | null
	version: string | null
	file_name: string
	icon: string | undefined
	disabled: boolean
	updateVersion?: string
	outdated: boolean
	updated: dayjs.Dayjs
	project_type: string
	id?: string
	updating?: boolean
	selected?: boolean
}

const isPackLocked = computed(() => {
	return props.instance.linked_data && props.instance.linked_data.locked
})
const canUpdatePack = computed(() => {
	if (!props.instance.linked_data || !props.versions || !props.versions[0]) return false
	return props.instance.linked_data.version_id !== props.versions[0].id
})
const exportModal = ref(null)

const projects = ref<ProjectListEntry[]>([])
const updateReport = ref<InstanceUpdateReport | null>(null)
const loadingProjects = ref(true)
const projectsError = ref('')
const PROJECTS_TIMEOUT_MS = 15_000
const selectedFiles = ref<string[]>([])
const selectedProjects = computed(() =>
	projects.value.filter((x) => selectedFiles.value.includes(x.file_name)),
)

const selectionMap = ref(new Map())

const initProjects = async (cacheBehaviour?: CacheBehaviour, request?: { active: boolean }) => {
	const newProjects: ProjectListEntry[] = []

	const profileProjects = ((await get_projects(props.instance.path, cacheBehaviour)) ??
		{}) as Record<string, ContentFile>
	const fetchProjects = []
	const fetchVersions = []

	for (const value of Object.values(profileProjects)) {
		if (value?.metadata) {
			fetchProjects.push(value.metadata.project_id)
			fetchVersions.push(value.metadata.version_id)
		}
	}

	const [rawModrinthProjects, rawModrinthVersions] = await Promise.all([
		get_project_many(fetchProjects).catch((error) => {
			console.error('Не удалось получить данные проектов Modrinth:', error)
			return [] as Project[]
		}),
		get_version_many(fetchVersions).catch((error) => {
			console.error('Не удалось получить версии проектов Modrinth:', error)
			return [] as Version[]
		}),
	])
	const modrinthProjects = (rawModrinthProjects ?? []) as Project[]
	const modrinthVersions = (rawModrinthVersions ?? []) as Version[]

	const [rawModrinthTeams, rawModrinthOrganizations] = await Promise.all([
		get_team_many(modrinthProjects.map((x) => x.team)).catch((error) => {
			console.error('Не удалось получить авторов проектов Modrinth:', error)
			return [] as TeamMember[][]
		}),
		get_organization_many(modrinthProjects.map((x) => x.organization).filter((x) => !!x)).catch(
			(error) => {
				console.error('Не удалось получить организации Modrinth:', error)
				return [] as Organization[]
			},
		),
	])
	const modrinthTeams = (rawModrinthTeams ?? []) as TeamMember[][]
	const modrinthOrganizations = (rawModrinthOrganizations ?? []) as Organization[]

	for (const [path, file] of Object.entries(profileProjects)) {
		if (file.metadata) {
			const project = modrinthProjects.find((x) => file.metadata?.project_id === x.id)
			const version = modrinthVersions.find((x) => file.metadata?.version_id === x.id)

			if (project && version) {
				const org = project.organization
					? modrinthOrganizations.find((x) => x.id === project.organization)
					: null

				const team = modrinthTeams.find((x) => x[0]?.team_id === project.team)

				let author: ProjectListEntryAuthor | null = null
				if (org) {
					author = {
						name: org.name,
						slug: org.slug,
						type: 'organization',
					}
				} else if (team) {
					const teamMember = team.find((x) => x.is_owner)
					if (teamMember) {
						author = {
							name: teamMember.user.username,
							slug: teamMember.user.username,
							type: 'user',
						}
					}
				}

				newProjects.push({
					path,
					name: project.title,
					slug: project.slug,
					author,
					version: version.version_number,
					file_name: file.file_name,
					icon: project.icon_url,
					disabled: file.file_name.endsWith('.disabled'),
					updateVersion: file.update_version_id,
					updated: dayjs(version.date_published),
					outdated: !!file.update_version_id,
					project_type: project.project_type,
					id: project.id,
				})
			}

			continue
		}

		newProjects.push({
			path,
			name: file.file_name.replace('.disabled', ''),
			author: null,
			version: null,
			file_name: file.file_name,
			icon: undefined,
			disabled: file.file_name.endsWith('.disabled'),
			outdated: false,
			updated: dayjs(0),
			project_type: file.project_type === 'shaderpack' ? 'shader' : file.project_type,
		})
	}

	if (request && !request.active) return

	projects.value = newProjects ?? []

	const newSelectionMap = new Map()
	for (const project of projects.value) {
		newSelectionMap.set(
			project.path,
			selectionMap.value.get(project.path) ??
				selectionMap.value.get(project.path.slice(0, -9)) ??
				selectionMap.value.get(project.path + '.disabled') ??
				false,
		)
	}
	selectionMap.value = newSelectionMap
}

function getProjectsErrorMessage(error: unknown) {
	if (error instanceof Error && error.message) return error.message
	if (typeof error === 'string') return error
	try {
		return JSON.stringify(error)
	} catch {
		return String(error)
	}
}

let activeProjectsRequest: { active: boolean } | null = null

async function loadProjects(cacheBehaviour?: CacheBehaviour) {
	if (activeProjectsRequest) activeProjectsRequest.active = false
	const request = { active: true }
	activeProjectsRequest = request
	let timeoutId: ReturnType<typeof setTimeout> | undefined
	loadingProjects.value = true
	projectsError.value = ''
	try {
		await Promise.race([
			initProjects(cacheBehaviour, request),
			new Promise<never>(
				(_, reject) =>
					(timeoutId = setTimeout(
						() => reject(new Error('Анализ файлов сборки не завершился за 15 секунд.')),
						PROJECTS_TIMEOUT_MS,
					)),
			),
		])
	} catch (error) {
		request.active = false
		if (activeProjectsRequest === request) {
			projectsError.value = getProjectsErrorMessage(error)
			console.error('Не удалось загрузить контент сборки:', error)
		}
	} finally {
		if (timeoutId) clearTimeout(timeoutId)
		if (activeProjectsRequest === request) loadingProjects.value = false
	}
}

void loadProjects()

const modpackVersionModal = ref<InstanceType<typeof ModpackVersionModal> | null>()
const installing = computed(() => props.instance.install_stage !== 'installed')

type FilterOption = {
	id: string
	formattedName: string
}

const projectTypeLabels: Record<string, string> = {
	mod: 'Моды',
	resourcepack: 'Текстуры',
	shader: 'Шейдеры',
	datapack: 'Датапаки',
}

const filterOptions: ComputedRef<FilterOption[]> = computed(() => {
	const options: FilterOption[] = []

	const frequency = projects.value.reduce((map: Record<string, number>, item) => {
		map[item.project_type] = (map[item.project_type] || 0) + 1
		return map
	}, {})

	const types = Object.keys(frequency).sort((a, b) => frequency[b] - frequency[a])

	types.forEach((type) => {
		options.push({
			id: type,
			formattedName: projectTypeLabels[type] ?? formatProjectType(type),
		})
	})

	if (!isPackLocked.value && projects.value.some((m) => m.outdated)) {
		options.push({
			id: 'updates',
			formattedName: 'Есть обновления',
		})
	}

	if (projects.value.some((m) => m.disabled)) {
		options.push({
			id: 'disabled',
			formattedName: 'Отключённые',
		})
	}

	return options
})

const selectedFilters = useStorage<string[]>(
	`${props.instance.name}-mod-selected-filters`,
	[],
	sessionStorage,
	{ mergeDefaults: true },
)

const filteredProjects = computed(() => {
	const updatesFilter = selectedFilters.value.includes('updates')
	const disabledFilter = selectedFilters.value.includes('disabled')

	const typeFilters = selectedFilters.value.filter(
		(filter) => filter !== 'updates' && filter !== 'disabled',
	)

	return projects.value.filter((project) => {
		return (
			(typeFilters.length === 0 || typeFilters.includes(project.project_type)) &&
			(!updatesFilter || project.outdated) &&
			(!disabledFilter || project.disabled)
		)
	})
})

watch(filterOptions, () => {
	for (let i = 0; i < selectedFilters.value.length; i++) {
		const option = selectedFilters.value[i]
		if (!filterOptions.value.some((x) => x.id === option)) {
			selectedFilters.value.splice(i, 1)
		}
	}
})

function toggleArray<T>(array: T[], value: T) {
	if (array.includes(value)) {
		array.splice(array.indexOf(value), 1)
	} else {
		array.push(value)
	}
}

const searchFilter = ref('')
const selectAll = ref(false)
const shareModal = ref<InstanceType<typeof ShareModalWrapper> | null>()
const ascending = ref(true)
const sortColumn = ref('Name')
const currentPage = ref(1)

const functionValues = computed(() =>
	selectedProjects.value.length > 0 ? selectedProjects.value : Array.from(projects.value.values()),
)

const search = computed(() => {
	const filtered = filteredProjects.value.filter((mod) => {
		return mod.name.toLowerCase().includes(searchFilter.value.toLowerCase())
	})

	switch (sortColumn.value) {
		case 'Updated':
			return filtered.slice().sort((a, b) => {
				const updated = a.updated.isAfter(b.updated) ? 1 : -1
				return ascending.value ? -updated : updated
			})
		default:
			return filtered
				.slice()
				.sort((a, b) =>
					ascending.value ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name),
				)
	}
})

watch([sortColumn, ascending, selectedFilters.value, searchFilter], () => (currentPage.value = 1))

const sortProjects = (filter: string) => {
	if (sortColumn.value === filter) {
		ascending.value = !ascending.value
	} else {
		sortColumn.value = filter
		ascending.value = true
	}
}

const updateAll = async (targets = projects.value.filter((project) => project.outdated)) => {
	const outdated = targets.filter((project) => project.outdated && !project.updating)
	if (outdated.length === 0) return
	for (const project of outdated) project.updating = true
	try {
		updateReport.value = await runInstanceUpdate(props.instance.path, {
			onlyPaths: outdated.map((project) => project.path),
		})
		await loadProjects('must_revalidate')
		trackEvent('InstanceUpdateAll', {
			loader: props.instance.loader,
			game_version: props.instance.game_version,
			count: updateReport.value.items.filter((item) => item.status === 'success').length,
			selected: targets.length !== projects.value.length,
		})
	} catch (error) {
		handleError(error instanceof Error ? error : new Error(String(error)))
	} finally {
		for (const project of outdated) project.updating = false
	}
}

const updateProject = async (mod: ProjectListEntry) => {
	await updateAll([mod])
	if (
		updateReport.value?.items.some((item) => item.path === mod.path && item.status === 'success')
	) {
		trackEvent('InstanceProjectUpdate', {
			loader: props.instance.loader,
			game_version: props.instance.game_version,
			id: mod.id,
			name: mod.name,
			project_type: mod.project_type,
		})
	}
}

const locks: Record<string, Promise<void> | undefined> = {}
const togglingFiles = ref(new Set<string>())

const toggleDisableMod = async (mod: ProjectListEntry) => {
	const key = mod.file_name
	const previousOperation = locks[key] ?? Promise.resolve()
	const operation = previousOperation
		.catch(() => undefined)
		.then(async () => {
			const previousPath = mod.path
			const previousDisabled = mod.disabled
			togglingFiles.value.add(key)
			togglingFiles.value = new Set(togglingFiles.value)
			mod.disabled = !previousDisabled
			try {
				mod.path = await toggle_disable_project(props.instance.path, previousPath)
				trackEvent('InstanceProjectDisable', {
					loader: props.instance.loader,
					game_version: props.instance.game_version,
					id: mod.id,
					name: mod.name,
					project_type: mod.project_type,
					disabled: mod.disabled,
				})
			} catch (error) {
				mod.path = previousPath
				mod.disabled = previousDisabled
				handleError(error instanceof Error ? error : new Error(String(error)))
			} finally {
				togglingFiles.value.delete(key)
				togglingFiles.value = new Set(togglingFiles.value)
			}
		})
	locks[key] = operation
	await operation.finally(() => {
		if (locks[key] === operation) Reflect.deleteProperty(locks, key)
	})
}

const removeMod = async (mod: ContentItem<ProjectListEntry>) => {
	try {
		await remove_project(props.instance.path, mod.path)
		projects.value = projects.value.filter((x) => mod.path !== x.path)
		trackEvent('InstanceProjectRemove', {
			loader: props.instance.loader,
			game_version: props.instance.game_version,
			id: mod.data.id,
			name: mod.data.name,
			project_type: mod.data.project_type,
		})
	} catch (error) {
		handleError(error instanceof Error ? error : new Error(String(error)))
	}
}

const copyModLink = async (mod: ContentItem<ProjectListEntry>) => {
	await navigator.clipboard.writeText(
		`https://modrinth.com/${mod.data.project_type}/${mod.data.slug}`,
	)
}

const deleteSelected = async () => {
	const selectedForDeletion = [...functionValues.value]
	if (selectedForDeletion.length === 0) return
	const selectedPaths = new Set(selectedForDeletion.map((project) => project.path))
	const previousProjects = projects.value
	projects.value = projects.value.filter((project) => !selectedPaths.has(project.path))

	const results = await Promise.allSettled(
		selectedForDeletion.map((project) => remove_project(props.instance.path, project.path)),
	)
	const failed = selectedForDeletion.filter((_, index) => results[index].status === 'rejected')
	if (failed.length > 0) {
		const failedPaths = new Set(failed.map((project) => project.path))
		projects.value = previousProjects.filter(
			(project) => !selectedPaths.has(project.path) || failedPaths.has(project.path),
		)
		for (const result of results) if (result.status === 'rejected') handleError(result.reason)
	}
	selectedFiles.value = failed.map((project) => project.file_name)
}

const shareNames = async () => {
	await shareModal.value?.show(functionValues.value.map((x) => x.name).join('\n'))
}

const shareFileNames = async () => {
	await shareModal.value?.show(functionValues.value.map((x) => x.file_name).join('\n'))
}

const shareUrls = async () => {
	await shareModal.value?.show(
		functionValues.value
			.filter((x) => x.slug)
			.map((x) => `https://modrinth.com/${x.project_type}/${x.slug}`)
			.join('\n'),
	)
}

const shareMarkdown = async () => {
	await shareModal.value?.show(
		functionValues.value
			.map((x) => {
				if (x.slug) {
					return `[${x.name}](https://modrinth.com/${x.project_type}/${x.slug})`
				}
				return x.name
			})
			.join('\n'),
	)
}

const updateSelected = async () => {
	await updateAll(functionValues.value)
}

async function retryFailedUpdates() {
	const failedPaths = updateReport.value?.items
		.filter((item) => item.status === 'error')
		.map((item) => item.path)
	if (!failedPaths?.length) return
	const failedProjects = projects.value.filter((project) => failedPaths.includes(project.path))
	await updateAll(failedProjects)
}

const enableAll = async () => {
	const promises = []
	for (const project of functionValues.value) {
		if (project.disabled) {
			promises.push(toggleDisableMod(project))
		}
	}
	await Promise.all(promises).catch(handleError)
}

const disableAll = async () => {
	const promises = []
	for (const project of functionValues.value) {
		if (!project.disabled) {
			promises.push(toggleDisableMod(project))
		}
	}
	await Promise.all(promises).catch(handleError)
}

watch(selectAll, () => {
	for (const [key, value] of Array.from(selectionMap.value)) {
		if (value !== selectAll.value) {
			selectionMap.value.set(key, selectAll.value)
		}
	}
})

const refreshingProjects = ref(false)
async function refreshProjects() {
	refreshingProjects.value = true
	try {
		await loadProjects('bypass')
	} finally {
		refreshingProjects.value = false
	}
}

let unlisten: (() => void) | undefined
let unlistenProfiles: (() => void) | undefined

async function registerProjectListeners() {
	unlisten = await getCurrentWebview().onDragDropEvent(async (event) => {
		if (event.payload.type !== 'drop') return

		for (const file of event.payload.paths) {
			if (file.endsWith('.mrpack')) continue
			await add_project_from_path(props.instance.path, file).catch(handleError)
		}
		await loadProjects()
	})

	unlistenProfiles = await profile_listener(
		async (event: { event: string; profile_path_id: string }) => {
			if (
				event.profile_path_id === props.instance.path &&
				event.event === 'synced' &&
				props.instance.install_stage !== 'pack_installing'
			) {
				await loadProjects()
			}
		},
	)
}

void registerProjectListeners().catch((error) => {
	console.error('Не удалось подключить события контента сборки:', error)
})

onUnmounted(() => {
	unlisten?.()
	unlistenProfiles?.()
})
</script>

<style scoped lang="scss">
.update-report {
	margin-bottom: 1rem;
	padding: 0.9rem;
	border: 1px solid var(--blockera-border);
	border-radius: var(--blockera-radius-lg);
	background: var(--blockera-surface);
}
.update-report > div {
	display: flex;
	justify-content: space-between;
	gap: 1rem;
}
.update-report span {
	color: var(--color-secondary);
}
.update-report ul {
	max-height: 10rem;
	margin: 0.75rem 0;
	overflow: auto;
}
.update-report li.is-success {
	color: var(--blockera-success);
}
.update-report li.is-error {
	color: var(--blockera-danger);
}
.blockera-content-manager {
	:deep(.iconified-input) {
		height: 42px;
		background: rgba(8, 12, 20, 0.72);
		border: 1px solid rgba(255, 255, 255, 0.085);
		border-radius: 12px;
		box-shadow: none;
	}
	:deep(.iconified-input:focus-within) {
		border-color: rgba(177, 91, 255, 0.48);
	}
	:deep(.iconified-input input) {
		color: #f4f1f8;
		background: transparent;
	}
	:deep(.content-list-panel),
	:deep(.card) {
		background: transparent;
		border-color: rgba(255, 255, 255, 0.07);
		box-shadow: none;
	}
	:deep(.content-list-item) {
		margin-bottom: 7px;
		background: rgba(255, 255, 255, 0.03);
		border: 1px solid rgba(255, 255, 255, 0.065);
		border-radius: 13px;
	}
	:deep(button) {
		border-radius: 10px;
	}
}

.blockera-empty-state {
	min-height: 370px;
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	text-align: center;
	background: radial-gradient(circle at 50% 45%, rgba(133, 54, 214, 0.12), transparent 18rem);

	.empty-state-icon {
		width: 64px;
		height: 64px;
		display: grid;
		place-items: center;
		color: #c587ff;
		background: linear-gradient(145deg, rgba(150, 70, 235, 0.22), rgba(79, 30, 132, 0.12));
		border: 1px solid rgba(184, 105, 255, 0.3);
		border-radius: 19px;
	}
	.empty-state-icon svg {
		width: 28px;
		height: 28px;
	}
	> span {
		margin-top: 18px;
		color: #b469f5;
		font-size: 10px;
		font-weight: 850;
		letter-spacing: 0.14em;
	}
	h2 {
		margin: 6px 0;
		color: #f7f4fa;
		font-size: 25px;
	}
	p {
		max-width: 430px;
		margin: 0 0 18px;
		color: #8e95a4;
		line-height: 1.55;
	}
}

.blockera-loading-state {
	gap: 0.75rem;
}
.blockera-loading-state h2,
.blockera-loading-state p {
	margin: 0;
}
.empty-state-actions {
	display: flex;
	flex-wrap: wrap;
	align-items: center;
	justify-content: center;
	gap: 0.75rem;
}

.text-input {
	width: 100%;
}

.manage {
	display: flex;
	gap: 0.5rem;
}

.table {
	margin-block-start: 0;
	border-radius: var(--radius-lg);
	border: 2px solid var(--color-bg);
}

.table-row {
	grid-template-columns: min-content 2fr 1fr 13.25rem;

	&.show-options {
		grid-template-columns: min-content auto;

		.options {
			display: flex;
			flex-direction: row;
			align-items: center;
			gap: var(--gap-md);
		}
	}
}

.static {
	.table-row {
		grid-template-areas: 'manage name version';
		grid-template-columns: 4.25rem 1fr 1fr;
	}

	.name-cell {
		grid-area: name;
	}

	.version {
		grid-area: version;
	}

	.manage {
		justify-content: center;
		grid-area: manage;
	}
}

.table-cell {
	align-items: center;
}

.card-row {
	display: flex;
	align-items: center;
	gap: var(--gap-md);
	justify-content: space-between;
	background-color: var(--color-raised-bg);
}

.mod-card {
	display: flex;
	flex-direction: row;
	flex-wrap: wrap;
	gap: var(--gap-sm);
	justify-content: flex-start;
	margin-bottom: 0.5rem;
	white-space: nowrap;
	align-items: center;

	:deep(.dropdown-row) {
		.btn {
			height: 2.5rem !important;
		}
	}

	:deep(.btn) {
		height: 2.5rem;
	}

	.dropdown-input {
		flex-grow: 1;

		.animated-dropdown {
			width: unset;

			:deep(.selected) {
				border-radius: var(--radius-md) 0 0 var(--radius-md);
			}
		}

		.iconified-input {
			width: 100%;

			input {
				flex-basis: unset;
			}
		}

		:deep(.animated-dropdown) {
			.render-down {
				border-radius: var(--radius-md) 0 0 var(--radius-md) !important;
			}

			.options-wrapper {
				margin-top: 0.25rem;
				width: unset;
				border-radius: var(--radius-md);
			}

			.options {
				border-radius: var(--radius-md);
				border: 1px solid var(--color);
			}
		}
	}
}

.list-card {
	margin-top: 0.5rem;
}

.text-combo {
	display: flex;
	align-items: center;
	gap: 0.5rem;
}

.name-cell {
	padding-left: 0;

	.btn {
		margin-left: var(--gap-sm);
		min-width: unset;
	}
}

.dropdown {
	width: 7rem !important;
}

.sort {
	padding-left: 0.5rem;
}

.second-row {
	display: flex;
	align-items: flex-start;
	flex-wrap: wrap;
	gap: var(--gap-sm);

	.chips {
		flex-grow: 1;
	}
}

.modal-body {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	padding: var(--gap-lg);

	.button-group {
		display: flex;
		justify-content: flex-end;
		gap: 0.5rem;
	}

	strong {
		color: var(--color-contrast);
	}
}

.mod-content {
	display: flex;
	align-items: center;
	gap: 1rem;

	.mod-text {
		display: flex;
		flex-direction: column;
	}

	.title {
		color: var(--color-contrast);
		font-weight: bolder;
	}
}

.actions-cell {
	display: flex;
	align-items: center;
	gap: 0.5rem;

	.btn {
		height: unset;
		width: unset;
		padding: 0;

		&.trash {
			color: var(--color-red);
		}

		&.update {
			color: var(--color-green);
		}

		&.share {
			color: var(--color-blue);
		}
	}
}

.more-box {
	display: flex;
	background-color: var(--color-bg);
	padding: var(--gap-lg);

	.options {
		display: flex;
		flex-wrap: wrap;
		flex-direction: row;
		gap: var(--gap-md);
		flex-grow: 1;
	}
}

.btn {
	&.transparent {
		height: unset;
		width: unset;
		padding: 0;
		color: var(--color-base);
		gap: var(--gap-xs);
		white-space: nowrap;

		svg {
			margin-right: 0 !important;
			transition: transform 0.2s ease-in-out;

			&.open {
				transform: rotate(90deg);
			}

			&.down {
				transform: rotate(180deg);
			}
		}
	}
}
.empty-prompt {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: var(--gap-md);
	height: 100%;
	width: 100%;
	margin: auto;

	.empty-icon {
		svg {
			width: 10rem;
			height: 10rem;
			color: var(--color-contrast);
		}
	}

	p,
	h3 {
		margin: 0;
	}
}
</style>

<style lang="scss">
.select-checkbox {
	button.checkbox {
		border: none;
		margin: 0;
	}
}

.search-input {
	min-height: 2.25rem;
	background-color: var(--color-raised-bg);
}

.top-box {
	background-image: radial-gradient(
		50% 100% at 50% 100%,
		var(--color-brand-highlight) 10%,
		#ffffff00 100%
	);
}

.top-box-divider {
	background-image: linear-gradient(90deg, #ffffff00 0%, var(--color-brand) 50%, #ffffff00 100%);
	width: 100%;
	height: 1px;
	opacity: 0.8;
}
</style>
