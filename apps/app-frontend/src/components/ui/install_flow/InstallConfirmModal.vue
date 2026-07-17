<script setup>
import { DownloadIcon, XIcon } from '@modrinth/assets'
import { Button, injectNotificationManager } from '@modrinth/ui'
import { ref } from 'vue'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import { trackEvent } from '@/helpers/analytics'
import { create_profile_and_install as pack_install } from '@/helpers/pack'

const { handleError } = injectNotificationManager()

const versionId = ref()
const project = ref()
const confirmModal = ref(null)
const installing = ref(false)

const onCreateInstance = ref(() => {})
const onPhase = ref(() => {})
let resolveResult = null
let settled = false

defineExpose({
	show: (projectVal, versionIdVal, createInstanceCallback, phaseCallback) => {
		project.value = projectVal
		versionId.value = versionIdVal
		installing.value = false
		confirmModal.value.show()

		onCreateInstance.value = createInstanceCallback
		onPhase.value = phaseCallback ?? (() => {})
		settled = false

		trackEvent('PackInstallStart')
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

async function install() {
	installing.value = true
	onPhase.value('installing')
	try {
		await pack_install(
			project.value.id,
			versionId.value,
			project.value.title,
			project.value.icon_url,
			onCreateInstance.value,
		)
		trackEvent('PackInstall', {
			id: project.value.id,
			version_id: versionId.value,
			title: project.value.title,
			source: 'ConfirmModal',
		})
		finish({ status: 'success', versionId: versionId.value })
		confirmModal.value.hide()
	} catch (error) {
		onPhase.value('error')
		handleError(error)
	} finally {
		installing.value = false
	}
}
</script>

<template>
	<ModalWrapper ref="confirmModal" header="Повторная установка" :on-hide="handleHide">
		<div class="modal-body">
			<p>Эта сборка уже установлена. Установить её ещё раз?</p>
			<div class="input-group push-right">
				<Button @click="() => $refs.confirmModal.hide()"><XIcon />Отмена</Button>
				<Button color="primary" :disabled="installing" @click="install()"
					><DownloadIcon /> {{ installing ? 'Устанавливаем…' : 'Установить' }}</Button
				>
			</div>
		</div>
	</ModalWrapper>
</template>

<style lang="scss" scoped>
.modal-body {
	display: flex;
	flex-direction: column;
	gap: 1rem;
}
</style>
