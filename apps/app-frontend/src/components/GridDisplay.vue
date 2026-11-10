<script setup>
import {
	ClipboardCopyIcon,
	DownloadIcon,
	EyeIcon,
	FolderOpenIcon,
	PlayIcon,
	PlusIcon,
	SearchIcon,
	StopCircleIcon,
	TrashIcon,
	XIcon,
} from '@modrinth/assets'
import { Button, DropdownSelect, injectNotificationManager } from '@modrinth/ui'
import { formatCategoryHeader } from '@modrinth/utils'
import { useStorage } from '@vueuse/core'
import dayjs from 'dayjs'
import { computed, ref, watch } from 'vue'

import ContextMenu from '@/components/ui/ContextMenu.vue'
import Instance from '@/components/ui/Instance.vue'
import ConfirmModalWrapper from '@/components/ui/modal/ConfirmModalWrapper.vue'
import { launcherText } from '@/helpers/launcher-language'
import { duplicate, edit, remove } from '@/helpers/profile.js'
import { get_profile_worlds } from '@/helpers/worlds'
import { libraryMatches, latestPlayedWorld, normalizedTags } from '@/helpers/library'

const { handleError } = injectNotificationManager()

const props = defineProps({
	instances: {
		type: Array,
		default() {
			return []
		},
	},
	label: {
		type: String,
		default: '',
	},
})
const instanceOptions = ref(null)
const instanceComponents = ref(null)

const currentDeleteInstance = ref(null)
const confirmModal = ref(null)

async function deleteProfile() {
	if (currentDeleteInstance.value) {
		instanceComponents.value = instanceComponents.value.filter(
			(x) => x.instance.path !== currentDeleteInstance.value,
		)
		try {
			await remove(currentDeleteInstance.value)
			const next = { ...organization.value }
			delete next[currentDeleteInstance.value]
			organization.value = next
			worldCache.delete(currentDeleteInstance.value)
		} catch (error) {
			handleError(error)
		}
	}
}

async function duplicateProfile(p) {
	await duplicate(p).catch(handleError)
}

const handleRightClick = (event, profilePathId) => {
	const item = instanceComponents.value.find((x) => x.instance.path === profilePathId)
	const baseOptions = [
		{ name: 'add_content' },
		{ type: 'divider' },
		{ name: 'settings' },
		{ name: 'mods' },
		{ name: 'resource_packs' },
		{ name: 'shader_packs' },
		{ name: 'saves' },
		{ name: 'export' },
		{ name: 'repair' },
		{ type: 'divider' },
		{ name: 'duplicate' },
		{ name: 'open' },
		{ name: 'copy' },
		{ type: 'divider' },
		{
			name: 'delete',
			color: 'danger',
		},
	]

	instanceOptions.value.showMenu(
		event,
		item,
		item.playing
			? [
					{
						name: 'stop',
						color: 'danger',
					},
					...baseOptions,
				]
			: [
					{
						name: 'play',
						color: 'primary',
					},
					...baseOptions,
				],
	)
}

const handleOptionsClick = async (args) => {
	switch (args.option) {
		case 'play':
			args.item.play(null, 'InstanceGridContextMenu')
			break
		case 'stop':
			args.item.stop(null, 'InstanceGridContextMenu')
			break
		case 'add_content':
			await args.item.addContent()
			break
		case 'settings':
			await args.item.openSettings()
			break
		case 'mods':
			await args.item.openContent('mod')
			break
		case 'resource_packs':
			await args.item.openFolder('resource_packs')
			break
		case 'shader_packs':
			await args.item.openFolder('shader_packs')
			break
		case 'saves':
			await args.item.openFolder('saves')
			break
		case 'export':
			await args.item.exportProfile()
			break
		case 'repair':
			await args.item.repair(null)
			break
		case 'duplicate':
			if (args.item.instance.install_stage == 'installed')
				await duplicateProfile(args.item.instance.path)
			break
		case 'open':
			await args.item.openFolder()
			break
		case 'copy':
			await navigator.clipboard.writeText(args.item.instance.path)
			break
		case 'delete':
			currentDeleteInstance.value = args.item.instance.path
			confirmModal.value.show()
			break
	}
}

const state = useStorage(
	`${props.label}-grid-display-state`,
	{
		group: 'Папка',
		sortBy: 'Last played',
	},
	localStorage,
	{ mergeDefaults: true },
)

const search = ref('')
const organization = useStorage('blockera-library-organization', {})
const favoritesOnly = ref(false)
const folderFilter = ref('')
const tagFilter = ref('')
const folders = computed(() =>
	[
		...new Set(props.instances.map((i) => organization.value[i.path]?.folder).filter(Boolean)),
	].sort(),
)
const tags = computed(() => [...new Set(props.instances.flatMap((i) => i.groups ?? []))].sort())
watch(folders, (values) => {
	if (folderFilter.value && !values.includes(folderFilter.value)) folderFilter.value = ''
})
watch(tags, (values) => {
	if (tagFilter.value && !values.includes(tagFilter.value)) tagFilter.value = ''
})
const worldSummaries = ref({})
const worldCache = new Map()
watch(
	() =>
		props.instances.map((i) => [
			i.path,
			`${i.last_played ?? ''}:${i.recent_time_played ?? 0}:${i.submitted_time_played ?? 0}`,
		]),
	async (profiles, _, onCleanup) => {
		let cancelled = false
		onCleanup(() => {
			cancelled = true
		})
		const pending = profiles.filter(
			([path, played]) => worldCache.get(path)?.played !== played || !worldCache.has(path),
		)
		async function worker() {
			while (!cancelled && pending.length) {
				const [path, played] = pending.shift()
				try {
					const worlds = await get_profile_worlds(path)
					if (cancelled) return
					const world = latestPlayedWorld(
						worlds.filter((world) => world.display_status !== 'hidden'),
					)
					worldCache.set(path, { played })
					worldSummaries.value[path] = world?.name ?? 'Нет данных о последнем мире'
				} catch {
					if (!cancelled) worldSummaries.value[path] = 'Не удалось прочитать миры'
				}
			}
		}
		await Promise.all(Array.from({ length: Math.min(3, pending.length) }, worker))
	},
	{ immediate: true },
)
function resetFilters() {
	search.value = ''
	favoritesOnly.value = false
	folderFilter.value = ''
	tagFilter.value = ''
}

function toggleFavorite(path) {
	organization.value = {
		...organization.value,
		[path]: { ...organization.value[path], favorite: !organization.value[path]?.favorite },
	}
}
const organizeDialog = ref(null)
const organizeTarget = ref(null)
const draftFolder = ref('')
const draftTags = ref('')
const savingOrganization = ref(false)
function organize(instance) {
	organizeTarget.value = instance
	draftFolder.value = organization.value[instance.path]?.folder ?? ''
	draftTags.value = (instance.groups ?? []).join(', ')
	organizeDialog.value.showModal()
}
async function saveOrganization() {
	if (savingOrganization.value || !organizeTarget.value) return
	savingOrganization.value = true
	try {
		const path = organizeTarget.value.path
		await edit(path, { groups: normalizedTags(draftTags.value) })
		organization.value = {
			...organization.value,
			[path]: { ...organization.value[path], folder: draftFolder.value.trim().slice(0, 64) },
		}
		organizeDialog.value.close()
	} catch (error) {
		handleError(error)
	} finally {
		savingOrganization.value = false
	}
}

const filteredResults = computed(() => {
	const { group = 'Папка', sortBy = 'Last played' } = state.value

	const instances = props.instances.filter((instance) => {
		const meta = organization.value[instance.path] ?? {}
		return (
			(!favoritesOnly.value || meta.favorite) &&
			(!folderFilter.value || meta.folder === folderFilter.value) &&
			(!tagFilter.value || instance.groups?.includes(tagFilter.value)) &&
			libraryMatches(instance, meta.folder ?? '', search.value)
		)
	})

	if (sortBy === 'Name') {
		instances.sort((a, b) => {
			return a.name.localeCompare(b.name)
		})
	}

	if (sortBy === 'Game version') {
		instances.sort((a, b) => {
			return a.game_version.localeCompare(b.game_version, undefined, { numeric: true })
		})
	}

	if (sortBy === 'Last played') {
		instances.sort((a, b) => {
			return dayjs(b.last_played ?? 0).diff(dayjs(a.last_played ?? 0))
		})
	}

	if (sortBy === 'Date created') {
		instances.sort((a, b) => {
			return dayjs(b.created ?? b.date_created).diff(dayjs(a.created ?? a.date_created))
		})
	}

	if (sortBy === 'Date modified') {
		instances.sort((a, b) => {
			return dayjs(b.modified ?? b.date_modified).diff(dayjs(a.modified ?? a.date_modified))
		})
	}

	const instanceMap = new Map()

	if (group === 'Loader') {
		instances.forEach((instance) => {
			const loader = formatCategoryHeader(instance.loader)
			if (!instanceMap.has(loader)) {
				instanceMap.set(loader, [])
			}

			instanceMap.get(loader).push(instance)
		})
	} else if (group === 'Game version') {
		instances.forEach((instance) => {
			if (!instanceMap.has(instance.game_version)) {
				instanceMap.set(instance.game_version, [])
			}

			instanceMap.get(instance.game_version).push(instance)
		})
	} else if (group === 'Папка') {
		instances.forEach((instance) => {
			const folder = organization.value[instance.path]?.folder || 'Без папки'
			if (!instanceMap.has(folder)) instanceMap.set(folder, [])
			instanceMap.get(folder).push(instance)
		})
	} else if (group === 'Group') {
		instances.forEach((instance) => {
			const groups = instance.groups?.length ? instance.groups : ['None']

			for (const category of groups) {
				if (!instanceMap.has(category)) {
					instanceMap.set(category, [])
				}

				instanceMap.get(category).push(instance)
			}
		})
	} else {
		return instanceMap.set('None', instances)
	}

	// For 'name', we intuitively expect the sorting to apply to the name of the group first, not just the name of the instance
	// ie: Category A should come before B, even if the first instance in B comes before the first instance in A
	if (sortBy === 'Name') {
		const sortedEntries = [...instanceMap.entries()].sort((a, b) => {
			// None should always be first
			if (a[0] === 'None' && b[0] !== 'None') {
				return -1
			}
			if (a[0] !== 'None' && b[0] === 'None') {
				return 1
			}
			return a[0].localeCompare(b[0])
		})
		instanceMap.clear()
		sortedEntries.forEach((entry) => {
			instanceMap.set(entry[0], entry[1])
		})
	}
	// default sorting would do 1.20.4 < 1.8.9 because 2 < 8
	// localeCompare with numeric=true puts 1.8.9 < 1.20.4 because 8 < 20
	if (group === 'Game version') {
		const sortedEntries = [...instanceMap.entries()].sort((a, b) => {
			return a[0].localeCompare(b[0], undefined, { numeric: true })
		})
		instanceMap.clear()
		sortedEntries.forEach((entry) => {
			instanceMap.set(entry[0], entry[1])
		})
	}

	return instanceMap
})
</script>
<template>
	<div class="flex gap-2 flex-wrap">
		<div class="iconified-input flex-1">
			<SearchIcon />
			<input
				v-model="search"
				type="text"
				placeholder="Название, версия игры или тег"
				aria-label="Поиск сборок"
			/>
			<Button class="r-btn" @click="() => (search = '')">
				<XIcon />
			</Button>
		</div>
		<DropdownSelect
			v-slot="{ selected }"
			v-model="state.sortBy"
			name="Sort Dropdown"
			class="max-w-[16rem]"
			:display-name="launcherText"
			:options="['Name', 'Last played', 'Date created', 'Date modified', 'Game version']"
			:placeholder="$t('blockera.ui.b92e64f8b332')"
		>
			<span class="font-semibold text-primary">{{ $t('blockera.ui.9bb640e5a014') }} </span>
			<span class="font-semibold text-secondary">{{ launcherText(selected) }}</span>
		</DropdownSelect>
		<DropdownSelect
			v-slot="{ selected }"
			v-model="state.group"
			class="max-w-[16rem]"
			name="Group Dropdown"
			:display-name="launcherText"
			:options="['Папка', 'Group', 'Loader', 'Game version', 'None']"
			:placeholder="$t('blockera.ui.b92e64f8b332')"
		>
			<span class="font-semibold text-primary">{{ $t('blockera.ui.e357306c9e50') }} </span>
			<span class="font-semibold text-secondary">{{ launcherText(selected) }}</span>
		</DropdownSelect>
	</div>
	<div class="library-filters">
		<button
			type="button"
			:aria-pressed="favoritesOnly"
			:class="{ active: favoritesOnly }"
			@click="favoritesOnly = !favoritesOnly"
		>
			★ Избранные
		</button>
		<select v-model="folderFilter" aria-label="Фильтр по папке">
			<option value="">Все папки</option>
			<option v-for="folder in folders" :key="folder" :value="folder">{{ folder }}</option>
		</select>
		<select v-model="tagFilter" aria-label="Фильтр по тегу">
			<option value="">Все теги</option>
			<option v-for="tag in tags" :key="tag" :value="tag">{{ tag }}</option>
		</select>
		<button
			v-if="search || favoritesOnly || folderFilter || tagFilter"
			type="button"
			@click="resetFilters"
		>
			Сбросить фильтры
		</button>
	</div>
	<p v-if="![...filteredResults.values()].some((items) => items.length)" role="status">
		Сборки не найдены. Измените поиск или фильтры.
	</p>
	<dialog
		ref="organizeDialog"
		class="organize-dialog"
		aria-labelledby="organize-title"
		@cancel="savingOrganization && $event.preventDefault()"
	>
		<form @submit.prevent="saveOrganization">
			<h2 id="organize-title">{{ organizeTarget?.name }} · Организация</h2>
			<label
				>Папка<input
					v-model="draftFolder"
					list="library-folder-names"
					maxlength="64"
					placeholder="Например, Выживание"
			/></label>
			<datalist id="library-folder-names">
				<option v-for="folder in folders" :key="folder" :value="folder" />
			</datalist>
			<label
				>Теги через запятую<input v-model="draftTags" maxlength="700" placeholder="Друзья, Техника"
			/></label>
			<p>Папки организуют библиотеку. Теги доступны также в настройках сборки.</p>
			<div class="library-filters">
				<button type="button" :disabled="savingOrganization" @click="organizeDialog.close()">
					Отмена</button
				><button type="submit" :disabled="savingOrganization">
					{{ savingOrganization ? 'Сохраняем…' : 'Сохранить' }}
				</button>
			</div>
		</form>
	</dialog>
	<div
		v-for="instanceSection in Array.from(filteredResults, ([key, value]) => ({
			key,
			value,
		}))"
		:key="instanceSection.key"
		class="row"
	>
		<div v-if="instanceSection.key !== 'None'" class="divider">
			<p>{{ instanceSection.key }}</p>
			<hr aria-hidden="true" />
		</div>
		<section class="instances">
			<Instance
				v-for="instance in instanceSection.value"
				ref="instanceComponents"
				:key="instance.path + instance.install_stage"
				:instance="instance"
				:library-meta="{
					...organization[instance.path],
					tags: instance.groups ?? [],
					world: worldSummaries[instance.path] ?? 'Читаем миры…',
				}"
				@favorite="toggleFavorite(instance.path)"
				@organize="organize(instance)"
				@contextmenu.prevent.stop="(event) => handleRightClick(event, instance.path)"
			/>
		</section>
	</div>
	<ConfirmModalWrapper
		ref="confirmModal"
		:title="$t('blockera.ui.ce48c5ce17ea')"
		:description="$t('blockera.ui.5d501fa5d0c8')"
		:has-to-type="false"
		:proceed-label="$t('blockera.ui.f6fdbe48dc54')"
		@proceed="deleteProfile"
	/>
	<ContextMenu ref="instanceOptions" @option-clicked="handleOptionsClick">
		<template #play> <PlayIcon /> {{ $t('blockera.ui.5d12bd53552c') }} </template>
		<template #stop> <StopCircleIcon /> {{ $t('blockera.ui.9e253470c876') }} </template>
		<template #add_content> <PlusIcon /> {{ $t('blockera.ui.3ba372ed53e9') }} </template>
		<template #settings> <EyeIcon /> Настройки сборки </template>
		<template #mods> <PlusIcon /> Установленные моды </template>
		<template #resource_packs> <FolderOpenIcon /> {{ $t('blockera.ui.5aa8824d5c30') }} </template>
		<template #shader_packs> <FolderOpenIcon /> {{ $t('blockera.ui.92973587e76f') }} </template>
		<template #saves> <FolderOpenIcon /> {{ $t('blockera.ui.8e4d9728828a') }} </template>
		<template #export> <ClipboardCopyIcon /> Экспорт </template>
		<template #repair> <DownloadIcon /> Ремонт </template>
		<template #duplicate> <ClipboardCopyIcon /> Дублировать</template>
		<template #delete> <TrashIcon /> {{ $t('blockera.ui.e963907dac5c') }} </template>
		<template #open> <FolderOpenIcon /> {{ $t('blockera.ui.f963014162aa') }} </template>
		<template #copy> <ClipboardCopyIcon /> Копировать путь </template>
	</ContextMenu>
</template>
<style lang="scss" scoped>
.library-filters {
	display: flex;
	flex-wrap: wrap;
	gap: 0.5rem;
}
.library-filters button,
.library-filters select,
.organize-dialog input {
	padding: 0.55rem 0.75rem;
	border: 1px solid var(--color-divider);
	border-radius: 0.6rem;
	background: var(--color-bg);
	color: var(--color-contrast);
}
.library-filters .active {
	border-color: #b371ec;
	background: #a563df30;
}
.organize-dialog {
	width: min(32rem, calc(100vw - 3rem));
	border: 1px solid #b371ec80;
	border-radius: 1rem;
	padding: 1.5rem;
	background: var(--color-bg-raised);
	color: var(--color-contrast);
}
.organize-dialog::backdrop {
	background: #030510b0;
}
.organize-dialog form,
.organize-dialog label {
	display: flex;
	flex-direction: column;
	gap: 0.75rem;
}
.organize-dialog h2 {
	font-size: 1.2rem;
	margin: 0;
}
.organize-dialog p {
	color: var(--color-secondary);
	font-size: 0.85rem;
}

.row {
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	width: 100%;

	.divider {
		display: flex;
		justify-content: space-between;
		align-items: center;
		width: 100%;
		gap: 1rem;
		margin-bottom: 1rem;

		p {
			margin: 0;
			font-size: 1rem;
			white-space: nowrap;
			color: var(--color-contrast);
		}

		hr {
			background-color: var(--color-gray);
			height: 1px;
			width: 100%;
			border: none;
		}
	}
}

.header {
	display: flex;
	flex-direction: row;
	flex-wrap: wrap;
	justify-content: space-between;
	gap: 1rem;
	align-items: inherit;
	margin: 1rem 1rem 0 !important;
	padding: 1rem;
	width: calc(100% - 2rem);

	.iconified-input {
		flex-grow: 1;

		input {
			min-width: 100%;
		}
	}

	.sort-dropdown {
		width: 10rem;
	}

	.filter-dropdown {
		width: 15rem;
	}

	.group-dropdown {
		width: 10rem;
	}

	.labeled_button {
		display: flex;
		flex-direction: row;
		align-items: center;
		gap: 0.5rem;
		white-space: nowrap;
	}
}

.instances {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(16rem, 1fr));
	width: 100%;
	gap: 0.75rem;
	margin-right: auto;
	scroll-behavior: smooth;
	overflow-y: auto;
}
</style>
