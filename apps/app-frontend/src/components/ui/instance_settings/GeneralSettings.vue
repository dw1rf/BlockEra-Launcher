<script setup lang="ts">
import { CopyIcon, MonitorIcon, PlusIcon, SpinnerIcon, TrashIcon } from '@modrinth/assets'
import {
	ButtonStyled,
	Checkbox,
	defineMessages,
	injectNotificationManager,
	useVIntl,
} from '@modrinth/ui'
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import BackgroundPicker from '@/components/ui/BackgroundPicker.vue'
import ConfirmModalWrapper from '@/components/ui/modal/ConfirmModalWrapper.vue'
import { trackEvent } from '@/helpers/analytics'
import { duplicate, edit, list, remove } from '@/helpers/profile'
import { createDesktopShortcut } from '@/helpers/utils'

import type { GameInstance, InstanceSettingsTabProps } from '../../../helpers/types'

const { handleError } = injectNotificationManager()
const { formatMessage } = useVIntl()
const router = useRouter()

const deleteConfirmModal = ref()

const props = defineProps<InstanceSettingsTabProps>()

const title = ref(props.instance.name)
const groups = ref(props.instance.groups)

const newCategoryInput = ref('')
const shortcutPending = ref(false)
const shortcutCreated = ref(false)

const installing = computed(() => props.instance.install_stage !== 'installed')

async function duplicateProfile() {
	await duplicate(props.instance.path).catch(handleError)
	trackEvent('InstanceDuplicate', {
		loader: props.instance.loader,
		game_version: props.instance.game_version,
	})
}

async function addDesktopShortcut() {
	shortcutPending.value = true
	shortcutCreated.value = false
	try {
		await createDesktopShortcut(props.instance.path, props.instance.name)
		shortcutCreated.value = true
	} catch (error) {
		handleError(error)
	} finally {
		shortcutPending.value = false
	}
}

const allInstances = ref((await list()) as GameInstance[])
const availableGroups = computed(() => [
	...new Set([...allInstances.value.flatMap((instance) => instance.groups), ...groups.value]),
])

const editProfileObject = computed(() => ({
	name: title.value.trim().substring(0, 32) ?? 'Instance',
	groups: groups.value.map((x) => x.trim().substring(0, 32)).filter((x) => x.length > 0),
}))

const toggleGroup = (group: string) => {
	if (groups.value.includes(group)) {
		groups.value = groups.value.filter((x) => x !== group)
	} else {
		groups.value.push(group)
	}
}

const addCategory = () => {
	const text = newCategoryInput.value.trim()

	if (text.length > 0) {
		groups.value.push(text.substring(0, 32))
		newCategoryInput.value = ''
	}
}

watch(
	[title, groups, groups],
	async () => {
		await edit(props.instance.path, editProfileObject.value)
	},
	{ deep: true },
)

const removing = ref(false)
async function removeProfile() {
	removing.value = true
	await remove(props.instance.path).catch(handleError)
	removing.value = false

	trackEvent('InstanceRemove', {
		loader: props.instance.loader,
		game_version: props.instance.game_version,
	})

	await router.push({ path: '/' })
}

const messages = defineMessages({
	name: {
		id: 'instance.settings.tabs.general.name',
		defaultMessage: 'Name',
	},
	libraryGroups: {
		id: 'instance.settings.tabs.general.library-groups',
		defaultMessage: 'Library groups',
	},
	libraryGroupsDescription: {
		id: 'instance.settings.tabs.general.library-groups.description',
		defaultMessage:
			'Library groups allow you to organize your instances into different sections in your library.',
	},
	libraryGroupsEnterName: {
		id: 'instance.settings.tabs.general.library-groups.enter-name',
		defaultMessage: 'Enter group name',
	},
	libraryGroupsCreate: {
		id: 'instance.settings.tabs.general.library-groups.create',
		defaultMessage: 'Create new group',
	},
	duplicateInstance: {
		id: 'instance.settings.tabs.general.duplicate-instance',
		defaultMessage: 'Duplicate instance',
	},
	duplicateInstanceDescription: {
		id: 'instance.settings.tabs.general.duplicate-instance.description',
		defaultMessage: 'Creates a copy of this instance, including worlds, configs, mods, etc.',
	},
	duplicateButtonTooltipInstalling: {
		id: 'instance.settings.tabs.general.duplicate-button.tooltip.installing',
		defaultMessage: 'Cannot duplicate while installing.',
	},
	duplicateButton: {
		id: 'instance.settings.tabs.general.duplicate-button',
		defaultMessage: 'Duplicate',
	},
	deleteInstance: {
		id: 'instance.settings.tabs.general.delete',
		defaultMessage: 'Delete instance',
	},
	deleteInstanceDescription: {
		id: 'instance.settings.tabs.general.delete.description',
		defaultMessage:
			'Permanently deletes an instance from your device, including your worlds, configs, and all installed content. Be careful, as once you delete a instance there is no way to recover it.',
	},
	deleteInstanceButton: {
		id: 'instance.settings.tabs.general.delete.button',
		defaultMessage: 'Delete instance',
	},
	deletingInstanceButton: {
		id: 'instance.settings.tabs.general.deleting.button',
		defaultMessage: 'Deleting...',
	},
})
</script>

<template>
	<ConfirmModalWrapper
		ref="deleteConfirmModal"
		title="Are you sure you want to delete this instance?"
		description="If you proceed, all data for your instance will be permanently erased, including your worlds. You will not be able to recover it."
		:has-to-type="false"
		proceed-label="Delete"
		:show-ad-on-close="false"
		@proceed="removeProfile"
	/>
	<div class="block">
		<label for="instance-name" class="m-0 mb-1 text-lg font-extrabold text-contrast block">
			{{ formatMessage(messages.name) }}
		</label>
		<div class="flex">
			<input
				id="instance-name"
				v-model="title"
				autocomplete="off"
				maxlength="80"
				class="flex-grow"
				type="text"
			/>
		</div>
		<template v-if="instance.install_stage == 'installed'">
			<div>
				<h2
					id="duplicate-instance-label"
					class="m-0 mt-4 mb-1 text-lg font-extrabold text-contrast block"
				>
					{{ formatMessage(messages.duplicateInstance) }}
				</h2>
				<p class="m-0 mb-2">
					{{ formatMessage(messages.duplicateInstanceDescription) }}
				</p>
			</div>
			<ButtonStyled>
				<button
					v-tooltip="installing ? formatMessage(messages.duplicateButtonTooltipInstalling) : null"
					aria-labelledby="duplicate-instance-label"
					:disabled="installing"
					@click="duplicateProfile"
				>
					<CopyIcon /> {{ formatMessage(messages.duplicateButton) }}
				</button>
			</ButtonStyled>
		</template>
		<h2 class="m-0 mt-4 mb-1 text-lg font-extrabold text-contrast block">Фон сборки</h2>
		<p class="m-0 mb-2">
			Выбранный кадр используется на главной странице и в карточках этой сборки.
		</p>
		<BackgroundPicker :scope="`instance:${props.instance.path}`" label="Выбрать фон сборки" />
		<template v-if="instance.install_stage == 'installed'">
			<h2 class="m-0 mt-4 mb-1 text-lg font-extrabold text-contrast block">Быстрый запуск</h2>
			<p class="m-0 mb-2">
				Создайте ярлык на рабочем столе. Он откроет BlockEra Launcher и сразу запустит эту сборку.
			</p>
			<ButtonStyled>
				<button :disabled="shortcutPending" @click="addDesktopShortcut">
					<SpinnerIcon v-if="shortcutPending" class="animate-spin" />
					<MonitorIcon v-else />
					{{
						shortcutCreated
							? 'Ярлык создан'
							: shortcutPending
								? 'Создаём ярлык…'
								: 'Добавить на рабочий стол'
					}}
				</button>
			</ButtonStyled>
		</template>
		<h2 class="m-0 mt-4 mb-1 text-lg font-extrabold text-contrast block">
			{{ formatMessage(messages.libraryGroups) }}
		</h2>
		<p class="m-0 mb-2">
			{{ formatMessage(messages.libraryGroupsDescription) }}
		</p>
		<div class="flex flex-col gap-1">
			<Checkbox
				v-for="group in availableGroups"
				:key="group"
				:model-value="groups.includes(group)"
				:label="group"
				@click="toggleGroup(group)"
			/>
			<div class="flex gap-2 items-center">
				<input
					v-model="newCategoryInput"
					type="text"
					:placeholder="formatMessage(messages.libraryGroupsEnterName)"
					@submit="() => addCategory"
				/>
				<ButtonStyled>
					<button class="w-fit" @click="() => addCategory()">
						<PlusIcon /> {{ formatMessage(messages.libraryGroupsCreate) }}
					</button>
				</ButtonStyled>
			</div>
		</div>
		<h2 id="delete-instance-label" class="m-0 mt-4 mb-1 text-lg font-extrabold text-contrast block">
			{{ formatMessage(messages.deleteInstance) }}
		</h2>
		<p class="m-0 mb-2">
			{{ formatMessage(messages.deleteInstanceDescription) }}
		</p>
		<ButtonStyled color="red">
			<button
				aria-labelledby="delete-instance-label"
				:disabled="removing"
				@click="deleteConfirmModal.show()"
			>
				<SpinnerIcon v-if="removing" class="animate-spin" />
				<TrashIcon v-else />
				{{
					removing
						? formatMessage(messages.deletingInstanceButton)
						: formatMessage(messages.deleteInstanceButton)
				}}
			</button>
		</ButtonStyled>
	</div>
</template>
