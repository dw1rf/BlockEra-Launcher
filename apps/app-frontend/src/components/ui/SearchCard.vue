<template>
	<article
		class="search-card card-shadow p-4 bg-bg-raised rounded-xl flex gap-3 group hover:brightness-90 transition-all"
	>
		<button
			class="search-card-open"
			type="button"
			:aria-label="`Открыть проект ${project.title}`"
			@click="openProject"
		/>
		<div class="icon w-[96px] h-[96px] relative">
			<Avatar :src="project.icon_url" size="96px" class="search-icon origin-top transition-all" />
		</div>
		<div class="flex flex-col gap-2 overflow-hidden">
			<div class="gap-2 overflow-hidden no-wrap text-ellipsis">
				<span class="text-lg font-extrabold text-contrast m-0 leading-none">
					{{ project.title }}
				</span>
				<span v-if="project.author" class="text-secondary"> · автор {{ project.author }}</span>
			</div>
			<div class="m-0 line-clamp-2">
				{{ project.description }}
			</div>
			<div v-if="categories.length > 0" class="mt-auto flex items-center gap-1 no-wrap">
				<TagsIcon class="h-4 w-4 shrink-0" />
				<div
					v-if="project.project_type === 'mod' || project.project_type === 'modpack'"
					class="text-sm font-semibold text-secondary flex gap-1 px-[0.375rem] py-0.5 bg-button-bg rounded-full"
				>
					<template v-if="project.client_side === 'optional' && project.server_side === 'optional'">
						Клиент или сервер
					</template>
					<template
						v-else-if="
							(project.client_side === 'optional' || project.client_side === 'required') &&
							(project.server_side === 'optional' || project.server_side === 'unsupported')
						"
					>
						Клиент
					</template>
					<template
						v-else-if="
							(project.server_side === 'optional' || project.server_side === 'required') &&
							(project.client_side === 'optional' || project.client_side === 'unsupported')
						"
					>
						Сервер
					</template>
					<template
						v-else-if="
							project.client_side === 'unsupported' && project.server_side === 'unsupported'
						"
					>
						Не поддерживается
					</template>
					<template
						v-else-if="project.client_side === 'required' && project.server_side === 'required'"
					>
						Клиент и сервер
					</template>
				</div>
				<div
					v-for="tag in categories"
					:key="tag"
					class="text-sm font-semibold text-secondary flex gap-1 px-[0.375rem] py-0.5 bg-button-bg rounded-full"
				>
					{{ formatCategory(tag.name) }}
				</div>
			</div>
		</div>
		<div class="flex flex-col gap-2 items-end shrink-0 ml-auto">
			<div class="flex items-center gap-2">
				<DownloadIcon class="shrink-0" />
				<span>
					{{ formatNumber(project.downloads) }}
					<span class="text-secondary">загрузок</span>
				</span>
			</div>
			<div class="flex items-center gap-2">
				<HeartIcon class="shrink-0" />
				<span>
					{{ formatNumber(project.follows ?? project.followers) }}
					<span class="text-secondary">подписчиков</span>
				</span>
			</div>
			<div class="mt-auto relative">
				<div class="absolute bottom-0 right-0 w-fit">
					<ButtonStyled color="brand" type="outlined">
						<button
							:disabled="effectivelyInstalled || installing"
							class="shrink-0 no-wrap"
							@click.stop="install()"
						>
							<template v-if="!effectivelyInstalled">
								<DownloadIcon v-if="modpack || instance" />
								<PlusIcon v-else />
							</template>
							<CheckIcon v-else />
							{{ installLabel }}
						</button>
					</ButtonStyled>
				</div>
			</div>
		</div>
		<p v-if="installError" class="search-card-error" role="alert">{{ installError }}</p>
	</article>
</template>

<script setup>
import { CheckIcon, DownloadIcon, HeartIcon, PlusIcon, TagsIcon } from '@modrinth/assets'
import { Avatar, ButtonStyled, injectNotificationManager } from '@modrinth/ui'
import { formatCategory, formatNumber } from '@modrinth/utils'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { installActionPending, nextInstallActionState } from '@/helpers/install-action-state'
import { install as installVersion } from '@/store/install.js'
dayjs.extend(relativeTime)

const { handleError } = injectNotificationManager()

const router = useRouter()

const props = defineProps({
	backgroundImage: {
		type: String,
		default: null,
	},
	project: {
		type: Object,
		required: true,
	},
	categories: {
		type: Array,
		required: true,
	},
	instance: {
		type: Object,
		default: null,
	},
	featured: {
		type: Boolean,
		default: false,
	},
	installed: {
		type: Boolean,
		default: false,
	},
})

const emit = defineEmits(['open', 'install'])

const installState = ref('idle')
const installError = ref('')
const installing = computed(() => installActionPending(installState.value))
const effectivelyInstalled = computed(() => props.installed || installState.value === 'success')
const installLabel = computed(() => {
	if (installState.value === 'awaiting-confirmation') return 'Подтвердите установку'
	if (installState.value === 'installing') return 'Устанавливаем…'
	if (effectivelyInstalled.value) return 'Установлено'
	if (installState.value === 'error') return 'Повторить'
	return modpack.value || props.instance ? 'Установить' : 'Добавить в сборку'
})

function openProject() {
	emit('open')
	void router.push({
		path: `/project/${props.project.project_id ?? props.project.id}`,
		query: { i: props.instance ? props.instance.path : undefined },
	})
}

async function install() {
	if (installing.value || effectivelyInstalled.value) return
	installState.value = nextInstallActionState(
		installState.value,
		installState.value === 'error' ? 'retry' : 'request',
	)
	installError.value = ''
	try {
		const result = await installVersion(
			props.project.project_id ?? props.project.id,
			null,
			props.instance ? props.instance.path : null,
			'SearchCard',
			() => {},
			(profile) => {
				void router.push(`/instance/${profile}`)
			},
			(phase) => {
				installState.value = nextInstallActionState(
					installState.value,
					phase === 'installing' ? 'confirm' : 'fail',
				)
			},
		)
		if (result?.status === 'cancelled') {
			installState.value = nextInstallActionState(installState.value, 'cancel')
			return
		}
		if (result?.status !== 'success') throw new Error('Backend не подтвердил установку.')
		installState.value = nextInstallActionState(installState.value, 'succeed')
		emit('install', props.project.project_id ?? props.project.id)
	} catch (error) {
		installState.value = nextInstallActionState(installState.value, 'fail')
		installError.value = error instanceof Error ? error.message : String(error)
		handleError(error)
	}
}

const modpack = computed(() => props.project.project_type === 'modpack')
</script>

<style scoped>
.search-card {
	position: relative;
}

.search-card-open {
	position: absolute;
	inset: 0;
	z-index: 0;
	border: 0;
	border-radius: inherit;
	background: transparent;
	cursor: pointer;
}

.search-card > :not(.search-card-open) {
	position: relative;
	z-index: 1;
	pointer-events: none;
}

.search-card :deep(button),
.search-card a {
	pointer-events: auto;
}

.search-card-error {
	position: absolute !important;
	right: 1rem;
	bottom: 0.25rem;
	max-width: 18rem;
	color: var(--blockera-danger, #ff8da8);
	font-size: 0.75rem;
}
</style>
