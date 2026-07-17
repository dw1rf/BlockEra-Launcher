<script setup lang="ts">
import { DownloadIcon, GameIcon, PackageIcon, PlayIcon, ReportIcon } from '@modrinth/assets'
import type { Version } from '@modrinth/utils'
import dayjs from 'dayjs'
import { computed, ref, watch } from 'vue'

import type ContextMenu from '@/components/ui/ContextMenu.vue'
import { formatJavaLabel } from '@/helpers/java-label'
import { get_optimal_jre_key, get_projects } from '@/helpers/profile'
import type { GameInstance } from '@/helpers/types'

const props = defineProps<{
	instance: GameInstance
	options: InstanceType<typeof ContextMenu>
	offline: boolean
	playing: boolean
	versions: Version[]
	installed: boolean
}>()
const emit = defineEmits<{ play: [] }>()

const modCount = ref(0)
const updateCount = ref(0)
const javaLabel = ref('Автоматический выбор')
const loading = ref(false)
const loadError = ref('')

const basePath = computed(() => `/instance/${encodeURIComponent(props.instance.path)}`)
const installLabel = computed(() => {
	switch (props.instance.install_stage) {
		case 'installed':
			return 'Готова к запуску'
		case 'minecraft_installing':
		case 'pack_installing':
			return 'Установка продолжается'
		case 'pack_installed':
			return 'Требуется завершить установку'
		default:
			return 'Требуется восстановление'
	}
})
const diagnostics = computed(() => {
	const issues: string[] = []
	if (props.instance.install_stage !== 'installed') issues.push('Установка сборки не завершена.')
	if (!props.instance.game_version) issues.push('Не выбрана версия Minecraft.')
	if (!props.instance.java_path && javaLabel.value === 'Автоматический выбор')
		issues.push('Java будет проверена при запуске.')
	if (loadError.value) issues.push(loadError.value)
	return issues
})

async function loadSummary() {
	loading.value = true
	loadError.value = ''
	try {
		const [projects, java] = await Promise.all([
			get_projects(props.instance.path, 'stale_while_revalidate_skip_offline'),
			get_optimal_jre_key(props.instance.path).catch(() => null),
		])
		const files = Object.values(projects ?? {}) as Array<{ update_version_id?: string }>
		modCount.value = files.length
		updateCount.value = files.filter((file) => file.update_version_id).length
		javaLabel.value = formatJavaLabel(java)
	} catch (error) {
		loadError.value = error instanceof Error ? error.message : String(error)
	} finally {
		loading.value = false
	}
}

watch(() => props.instance.path, loadSummary, { immediate: true })
</script>

<template>
	<div class="instance-overview" :aria-busy="loading">
		<section class="overview-grid">
			<article>
				<GameIcon /><span
					><small>MINECRAFT</small><strong>{{ instance.game_version }}</strong></span
				>
			</article>
			<article>
				<PackageIcon /><span
					><small>ЗАГРУЗЧИК</small
					><strong class="capitalize"
						>{{ instance.loader }} {{ instance.loader_version ?? '' }}</strong
					></span
				>
			</article>
			<article>
				<GameIcon /><span
					><small>JAVA</small><strong>{{ javaLabel }}</strong></span
				>
			</article>
			<article>
				<PackageIcon /><span
					><small>КОНТЕНТ</small><strong>{{ modCount }} файлов</strong></span
				>
			</article>
			<article>
				<DownloadIcon /><span
					><small>ОБНОВЛЕНИЯ</small><strong>{{ updateCount || 'Нет' }}</strong></span
				>
			</article>
			<article>
				<PlayIcon /><span
					><small>ПОСЛЕДНИЙ ЗАПУСК</small
					><strong>{{
						instance.last_played
							? dayjs(instance.last_played).format('DD.MM.YYYY HH:mm')
							: 'Ещё не запускалась'
					}}</strong></span
				>
			</article>
		</section>

		<section class="overview-status">
			<div>
				<span>СОСТОЯНИЕ УСТАНОВКИ</span>
				<h2>{{ installLabel }}</h2>
			</div>
			<div class="overview-actions">
				<button
					type="button"
					class="primary"
					:disabled="playing || instance.install_stage !== 'installed'"
					@click="emit('play')"
				>
					<PlayIcon /> {{ playing ? 'Игра запущена' : 'Играть' }}
				</button>
				<router-link :to="`${basePath}/content`">Открыть контент</router-link>
				<router-link :to="`${basePath}/worlds`">Миры</router-link>
				<router-link :to="`${basePath}/logs`">Логи</router-link>
			</div>
		</section>

		<section class="overview-diagnostics" :class="{ healthy: diagnostics.length === 0 }">
			<ReportIcon aria-hidden="true" />
			<div>
				<strong>{{ diagnostics.length ? 'Нужна проверка' : 'Проблем не обнаружено' }}</strong>
				<ul v-if="diagnostics.length">
					<li v-for="issue in diagnostics" :key="issue">{{ issue }}</li>
				</ul>
				<p v-else>Сборка установлена, Java подобрана, критических ошибок нет.</p>
			</div>
			<button type="button" :disabled="loading" @click="loadSummary">
				{{ loading ? 'Проверяем…' : 'Проверить снова' }}
			</button>
		</section>
	</div>
</template>

<style scoped lang="scss">
.instance-overview {
	display: grid;
	gap: 1rem;
	padding: 1rem;
}
.overview-grid {
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	gap: 0.75rem;
}
.overview-grid article {
	min-height: 5.5rem;
	display: flex;
	align-items: center;
	gap: 0.75rem;
	padding: 1rem;
	border: 1px solid var(--blockera-border, rgba(255, 255, 255, 0.08));
	border-radius: var(--blockera-radius-lg, 0.9rem);
	background: var(--blockera-surface, rgba(20, 24, 36, 0.9));
}
.overview-grid svg {
	width: 1.25rem;
	color: var(--blockera-accent, #c084fc);
}
.overview-grid span {
	display: grid;
	gap: 0.25rem;
}
.overview-grid small {
	color: var(--color-secondary);
	font-size: 0.65rem;
	font-weight: 800;
	letter-spacing: 0.09em;
}
.overview-status,
.overview-diagnostics {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
	padding: 1.15rem;
	border-radius: var(--blockera-radius-lg, 0.9rem);
	background: var(--blockera-surface-raised, rgba(28, 31, 46, 0.92));
}
.overview-status span {
	color: var(--blockera-accent, #c084fc);
	font-size: 0.67rem;
	font-weight: 800;
	letter-spacing: 0.1em;
}
.overview-status h2 {
	margin: 0.25rem 0 0;
}
.overview-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 0.5rem;
}
.overview-actions button,
.overview-actions a,
.overview-diagnostics button {
	min-height: 2.25rem;
	display: inline-flex;
	align-items: center;
	gap: 0.4rem;
	padding: 0.55rem 0.8rem;
	color: var(--color-base);
	border: 1px solid var(--blockera-border, rgba(255, 255, 255, 0.1));
	border-radius: 0.65rem;
	background: var(--color-button-bg);
	cursor: pointer;
}
.overview-actions .primary {
	color: white;
	border-color: transparent;
	background: var(--blockera-accent-strong, #8b36e0);
}
.overview-diagnostics {
	justify-content: flex-start;
	border: 1px solid var(--blockera-warning, #e9b949);
}
.overview-diagnostics.healthy {
	border-color: var(--blockera-success, #62d49b);
}
.overview-diagnostics > svg {
	width: 1.5rem;
}
.overview-diagnostics > div {
	flex: 1;
}
.overview-diagnostics p,
.overview-diagnostics ul {
	margin: 0.35rem 0 0;
	color: var(--color-secondary);
}
@media (max-width: 1180px) {
	.overview-grid {
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}
	.overview-status {
		align-items: flex-start;
		flex-direction: column;
	}
}
</style>
