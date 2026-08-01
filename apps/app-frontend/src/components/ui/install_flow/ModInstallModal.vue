<script setup>
import { CheckIcon, DownloadIcon, PlusIcon, RightArrowIcon } from '@modrinth/assets'
import { Button, Card, injectNotificationManager } from '@modrinth/ui'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import { trackEvent } from '@/helpers/analytics'
import {
	add_project_from_version as installMod,
	check_installed,
	create,
	get,
	list,
} from '@/helpers/profile'
import {
	findPreferredVersion,
	installVersionDependencies,
	isVersionCompatible,
} from '@/store/install.js'

const { handleError } = injectNotificationManager()
const router = useRouter()

const versions = ref()
const project = ref()

const installModal = ref()
const searchFilter = ref('')

const showCreation = ref(false)
const name = ref(null)
const loader = ref(null)
const gameVersion = ref(null)
const creatingInstance = ref(false)

const profiles = ref([])

const shownProfiles = computed(() =>
	profiles.value
		.filter((profile) => {
			return profile.name.toLowerCase().includes(searchFilter.value.toLowerCase())
		})
		.filter((profile) => {
			const version = {
				game_versions: versions.value.flatMap((v) => v.game_versions),
				loaders: versions.value.flatMap((v) => v.loaders),
			}
			return isVersionCompatible(version, project.value, profile)
		}),
)

const onPhase = ref(() => {})
let resolveResult = null
let settled = false

defineExpose({
	show: async (projectVal, versionsVal, phaseCallback) => {
		project.value = projectVal
		versions.value = versionsVal
		searchFilter.value = ''

		showCreation.value = false
		name.value = null
		gameVersion.value = null
		loader.value = null

		onPhase.value = phaseCallback ?? (() => {})
		settled = false

		const profilesVal =
			(await list().catch((error) => {
				handleError(error)
				return []
			})) ?? []
		for (const profile of profilesVal) {
			profile.installing = false
			profile.installedMod = await check_installed(profile.path, project.value.id).catch(
				handleError,
			)
		}
		profiles.value = profilesVal

		installModal.value.show()

		trackEvent('ProjectInstallStart', { source: 'ProjectInstallModal' })
		return new Promise((resolve) => {
			resolveResult = resolve
		})
	},
})

function finish(result) {
	if (settled) return
	settled = true
	resolveResult?.(result)
	resolveResult = null
}

function handleHide() {
	finish({ status: 'cancelled' })
}

async function install(instance) {
	instance.installing = true
	onPhase.value('installing')
	const version = findPreferredVersion(versions.value, project.value, instance)

	if (!version) {
		instance.installing = false
		handleError('No compatible version found')
		return
	}

	try {
		await installMod(instance.path, version.id)
		await installVersionDependencies(instance, version)
		instance.installedMod = true
		trackEvent('ProjectInstall', {
			loader: instance.loader,
			game_version: instance.game_version,
			id: project.value.id,
			version_id: version.id,
			project_type: project.value.project_type,
			title: project.value.title,
			source: 'ProjectInstallModal',
		})
		finish({ status: 'success', versionId: version.id })
		installModal.value?.hide()
	} catch (error) {
		onPhase.value('error')
		handleError(error)
	} finally {
		instance.installing = false
	}
}

const toggleCreation = () => {
	showCreation.value = !showCreation.value
	name.value = null
	gameVersion.value = null
	loader.value = null

	if (showCreation.value) {
		trackEvent('InstanceCreateStart', { source: 'ProjectInstallModal' })
	}
}

const createInstance = async () => {
	creatingInstance.value = true
	onPhase.value('installing')

	const gameVersions = versions.value[0].game_versions
	const gameVersion = gameVersions[0]

	const loaders = versions.value[0].loaders
	const loader = loaders.includes('fabric')
		? 'fabric'
		: loaders.includes('neoforge')
			? 'neoforge'
			: loaders.includes('forge')
				? 'forge'
				: loaders.includes('quilt')
					? 'quilt'
					: 'vanilla'

	try {
		const id = await create(name.value, gameVersion, loader, 'latest', null)
		await installMod(id, versions.value[0].id)
		const instance = await get(id, true)
		await installVersionDependencies(instance, versions.value[0])
		await router.push(`/instance/${encodeURIComponent(id)}/`)

		trackEvent('InstanceCreate', {
			profile_name: name.value,
			game_version: versions.value[0].game_versions[0],
			loader: loader,
			loader_version: 'latest',
			has_icon: false,
			source: 'ProjectInstallModal',
		})

		trackEvent('ProjectInstall', {
			loader: loader,
			game_version: versions.value[0].game_versions[0],
			id: project.value,
			version_id: versions.value[0].id,
			project_type: project.value.project_type,
			title: project.value.title,
			source: 'ProjectInstallModal',
		})
		finish({ status: 'success', versionId: versions.value[0].id })
		installModal.value?.hide()
	} catch (error) {
		onPhase.value('error')
		handleError(error)
	} finally {
		creatingInstance.value = false
	}
}
</script>

<template>
	<ModalWrapper ref="installModal" header="Установить проект в сборку" :on-hide="handleHide">
		<div class="modal-body">
			<input
				v-model="searchFilter"
				autocomplete="off"
				type="text"
				class="search"
				placeholder="Найти сборку"
			/>
			<div class="profiles" :class="{ 'hide-creation': !showCreation }">
				<div v-for="profile in shownProfiles" :key="profile.name" class="option">
					<router-link
						class="btn btn-transparent profile-button"
						:to="`/instance/${encodeURIComponent(profile.path)}`"
						@click="installModal.hide()"
					>
						{{ profile.name }}
					</router-link>
					<div
						v-tooltip="
							profile.linked_data?.locked && !profile.installedMod
								? 'Unpair or unlock an instance to add mods.'
								: ''
						"
					>
						<Button
							:disabled="profile.installedMod || profile.installing"
							@click="install(profile)"
						>
							<DownloadIcon v-if="!profile.installedMod && !profile.installing" />
							<CheckIcon v-else-if="profile.installedMod" />
							{{
								profile.installing
									? 'Устанавливаем…'
									: profile.installedMod
										? 'Установлено'
										: 'Установить'
							}}
						</Button>
					</div>
				</div>
			</div>
			<Card v-if="showCreation" class="creation-card">
				<div class="creation-container">
					<div class="creation-settings">
						<input
							v-model="name"
							autocomplete="off"
							type="text"
							placeholder="Название"
							class="creation-input"
						/>
						<Button :disabled="creatingInstance === true || !name" @click="createInstance()">
							<RightArrowIcon />
							{{ creatingInstance ? 'Создаём…' : 'Создать' }}
						</Button>
					</div>
				</div>
			</Card>
			<div class="input-group push-right">
				<Button :color="showCreation ? '' : 'primary'" @click="toggleCreation()">
					<PlusIcon />
					{{ showCreation ? 'Скрыть создание' : 'Создать новую сборку' }}
				</Button>
				<Button @click="installModal.hide()">Отмена</Button>
			</div>
		</div>
	</ModalWrapper>
</template>

<style scoped lang="scss">
.creation-card {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	margin: 0;
	background-color: var(--color-bg);
}

.creation-container {
	display: flex;
	flex-direction: row;
	gap: 1rem;
}

.creation-input {
	width: 100%;
}

.creation-dropdown {
	width: min-content !important;
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
}

.creation-settings {
	width: 100%;
	margin-left: 0.5rem;
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	justify-content: center;
}

.modal-body {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	min-width: 350px;
}

.profiles {
	max-height: 12rem;
	overflow-y: auto;

	&.hide-creation {
		max-height: 21rem;
	}
}

.option {
	width: calc(100%);
	background: var(--color-raised-bg);
	color: var(--color-base);
	box-shadow: none;
	display: flex;
	flex-direction: row;
	justify-content: space-between;
	align-items: center;
	gap: 0.5rem;

	img {
		margin-right: 0.5rem;
	}

	.name {
		display: flex;
		flex-direction: column;
		justify-content: center;
	}

	.profile-button {
		align-content: start;
		padding: 0.5rem;
		text-align: left;
	}
}

.profile-image {
	--size: 2rem !important;
}
</style>
