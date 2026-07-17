<script setup>
import { Button, injectNotificationManager } from '@modrinth/ui'
import { ref } from 'vue'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import SearchCard from '@/components/ui/SearchCard.vue'
import { get_project, get_version } from '@/helpers/cache.js'
import { get_categories } from '@/helpers/tags.js'
import { install as installVersion } from '@/store/install.js'

const { handleError } = injectNotificationManager()

const confirmModal = ref(null)
const project = ref(null)
const version = ref(null)
const categories = ref(null)
const installing = ref(false)
const installError = ref('')

defineExpose({
	async show(event) {
		installError.value = ''
		try {
			if (event.event === 'InstallVersion') {
				version.value = await get_version(event.id, 'must_revalidate')
				project.value = await get_project(version.value.project_id, 'must_revalidate')
			} else {
				project.value = await get_project(event.id, 'must_revalidate')
				version.value = await get_version(
					project.value.versions[project.value.versions.length - 1],
					'must_revalidate',
				)
			}
			categories.value = (await get_categories()).filter(
				(cat) => project.value.categories.includes(cat.name) && cat.project_type === 'mod',
			)
			confirmModal.value.show()
		} catch (error) {
			handleError(error)
		}
	},
})

async function install() {
	installing.value = true
	installError.value = ''
	try {
		const result = await installVersion(
			project.value.id,
			version.value.id,
			null,
			'URLConfirmModal',
			() => {},
			() => {},
		)
		if (result?.status === 'success') confirmModal.value.hide()
		else if (result?.status !== 'cancelled') throw new Error('Backend не подтвердил установку.')
	} catch (error) {
		installError.value = error instanceof Error ? error.message : String(error)
		handleError(error)
	} finally {
		installing.value = false
	}
}
</script>

<template>
	<ModalWrapper ref="confirmModal" :header="`Установить ${project?.title ?? 'проект'}`">
		<div class="modal-body">
			<SearchCard
				:project="project"
				class="project-card"
				:categories="categories"
				@open="confirmModal.hide()"
			/>
			<div class="button-row">
				<div class="markdown-body">
					<p>
						Установка версии <code>{{ version?.id }}</code> из Modrinth
					</p>
				</div>
				<div class="button-group">
					<Button :loading="installing" color="primary" @click="install">{{
						installing ? 'Устанавливаем…' : 'Установить'
					}}</Button>
				</div>
			</div>
			<p v-if="installError" class="text-danger" role="alert">{{ installError }}</p>
		</div>
	</ModalWrapper>
</template>

<style scoped lang="scss">
.modal-body {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	gap: var(--gap-md);
}

.button-row {
	width: 100%;
	display: flex;
	flex-direction: row;
	justify-content: space-between;
	align-items: center;
	gap: var(--gap-md);
}

.button-group {
	display: flex;
	flex-direction: row;
	gap: var(--gap-sm);
}

.project-card {
	background-color: var(--color-bg);
	width: 100%;

	:deep(.badge) {
		border: 1px solid var(--color-raised-bg);
		background-color: var(--color-accent-contrast);
	}
}
</style>
