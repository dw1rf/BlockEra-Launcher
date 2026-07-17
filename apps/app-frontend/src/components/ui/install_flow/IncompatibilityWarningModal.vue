<template>
	<ModalWrapper
		ref="incompatibleModal"
		header="Предупреждение о совместимости"
		:on-hide="handleHide"
	>
		<div class="modal-body">
			<p>
				Проект не заявляет совместимость с выбранной сборкой. Продолжить без автоматической
				установки зависимостей?
			</p>
			<table>
				<thead>
					<tr class="header">
						<th>{{ instance?.name }}</th>
						<th>{{ project.title }}</th>
					</tr>
				</thead>
				<tbody>
					<tr class="content">
						<td class="data">{{ instance?.loader }} {{ instance?.game_version }}</td>
						<td>
							<multiselect
								v-if="versions?.length > 1"
								v-model="selectedVersion"
								:options="versions"
								:searchable="true"
								placeholder="Select version"
								open-direction="top"
								:show-labels="false"
								:custom-label="
									(version) =>
										`${version?.name} (${version?.loaders
											.map((name) => formatCategory(name))
											.join(', ')} - ${version?.game_versions.join(', ')})`
								"
								:max-height="150"
							/>
							<span v-else>
								<span>
									{{ selectedVersion?.name }} ({{
										selectedVersion?.loaders.map((name) => formatCategory(name)).join(', ')
									}}
									- {{ selectedVersion?.game_versions.join(', ') }})
								</span>
							</span>
						</td>
					</tr>
				</tbody>
			</table>
			<div class="button-group">
				<Button @click="() => incompatibleModal.hide()"><XIcon />Отмена</Button>
				<Button color="primary" :disabled="installing" @click="install()">
					<DownloadIcon /> {{ installing ? 'Устанавливаем…' : 'Установить' }}
				</Button>
			</div>
		</div>
	</ModalWrapper>
</template>

<script setup>
import { DownloadIcon, XIcon } from '@modrinth/assets'
import { Button, injectNotificationManager } from '@modrinth/ui'
import { formatCategory } from '@modrinth/utils'
import { ref } from 'vue'
import Multiselect from 'vue-multiselect'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import { trackEvent } from '@/helpers/analytics'
import { add_project_from_version as installMod } from '@/helpers/profile'

const { handleError } = injectNotificationManager()

const instance = ref(null)
const project = ref(null)
const versions = ref(null)
const selectedVersion = ref(null)
const incompatibleModal = ref(null)
const installing = ref(false)

const onPhase = ref(() => {})
let resolveResult = null
let settled = false

defineExpose({
	show: (instanceVal, projectVal, projectVersions, selected, phaseCallback) => {
		instance.value = instanceVal
		versions.value = projectVersions
		selectedVersion.value = selected ?? projectVersions[0]

		project.value = projectVal

		onPhase.value = phaseCallback ?? (() => {})
		installing.value = false
		settled = false

		incompatibleModal.value.show()

		trackEvent('ProjectInstallStart', { source: 'ProjectIncompatibilityWarningModal' })
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

const install = async () => {
	installing.value = true
	onPhase.value('installing')
	try {
		await installMod(instance.value.path, selectedVersion.value.id)
		trackEvent('ProjectInstall', {
			loader: instance.value.loader,
			game_version: instance.value.game_version,
			id: project.value,
			version_id: selectedVersion.value.id,
			project_type: project.value.project_type,
			title: project.value.title,
			source: 'ProjectIncompatibilityWarningModal',
		})
		finish({ status: 'success', versionId: selectedVersion.value.id })
		incompatibleModal.value.hide()
	} catch (error) {
		onPhase.value('error')
		handleError(error)
	} finally {
		installing.value = false
	}
}
</script>

<style lang="scss" scoped>
.data {
	text-transform: capitalize;
}

table {
	width: 100%;
	border-radius: var(--radius-lg);
	border-collapse: collapse;
	box-shadow: 0 0 0 1px var(--color-button-bg);
}

th {
	text-align: left;
	padding: 1rem;
	background-color: var(--color-bg);
	overflow: hidden;
	border-bottom: 1px solid var(--color-button-bg);
}

th:first-child {
	border-top-left-radius: var(--radius-lg);
	border-right: 1px solid var(--color-button-bg);
}

th:last-child {
	border-top-right-radius: var(--radius-lg);
}

td {
	padding: 1rem;
}

td:first-child {
	border-right: 1px solid var(--color-button-bg);
}

.button-group {
	display: flex;
	justify-content: flex-end;
	gap: 1rem;
}

.modal-body {
	display: flex;
	flex-direction: column;
	gap: 1rem;

	:deep(.animated-dropdown .options) {
		max-height: 13.375rem;
	}
}
</style>
