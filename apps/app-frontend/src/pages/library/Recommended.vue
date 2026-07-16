<script setup lang="ts">
import { ChevronRightIcon, DownloadIcon, SparklesIcon, UserIcon } from '@modrinth/assets'
import { injectNotificationManager } from '@modrinth/ui'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { recommendedPacks } from '@/data/recommended-packs'
import { get_project_many } from '@/helpers/cache'

type ModrinthProject = {
	id: string
	title: string
	downloads: number
	followers: number
	icon_url?: string
}

const router = useRouter()
const { handleError } = injectNotificationManager()
const projects = ref<ModrinthProject[]>([])

const cards = computed(() =>
	recommendedPacks.map((pack) => ({
		...pack,
		project: projects.value.find((project) => project.id === pack.projectId),
	})),
)

function compactNumber(value?: number) {
	return new Intl.NumberFormat('ru-RU', { notation: 'compact', maximumFractionDigits: 1 }).format(
		value ?? 0,
	)
}

onMounted(async () => {
	projects.value =
		(await get_project_many(
			recommendedPacks.map((pack) => pack.projectId),
			'stale_while_revalidate',
		).catch(handleError)) ?? []
})
</script>

<template>
	<section class="recommended-catalog">
		<header>
			<div>
				<p><SparklesIcon /> ВЫБОР BLOCKERA</p>
				<h2>Сборки, с которых хочется начать новый мир</h2>
				<span
					>Проверенные популярные модпаки. Скоро здесь появятся авторские сборки стримеров и
					сообществ.</span
				>
			</div>
		</header>
		<div class="pack-grid">
			<button
				v-for="pack in cards"
				:key="pack.projectId"
				class="pack-card"
				:style="{ '--pack-accent': pack.accent }"
				@click="router.push(`/library/recommended/${pack.slug}`)"
			>
				<span class="pack-glow"></span>
				<img :src="pack.project?.icon_url ?? pack.iconUrl" alt="" />
				<span class="pack-copy">
					<small>{{ pack.ownerKind }} · {{ pack.owner }}</small>
					<strong>{{ pack.project?.title ?? pack.title }}</strong>
					<span>{{ pack.tagline }}</span>
					<span class="pack-meta">
						<span><DownloadIcon /> {{ compactNumber(pack.project?.downloads) }}</span>
						<span><UserIcon /> {{ pack.project?.followers?.toLocaleString('ru-RU') ?? '—' }}</span>
					</span>
				</span>
				<ChevronRightIcon class="arrow" />
			</button>
		</div>
	</section>
</template>

<style scoped lang="scss">
.recommended-catalog {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	color: #f8fafc;
}
header {
	display: flex;
	justify-content: space-between;
	padding: 0.35rem 0.15rem 0.2rem;
}
header p {
	display: flex;
	align-items: center;
	gap: 0.45rem;
	margin: 0 0 0.45rem;
	color: #c084fc;
	font-size: 0.7rem;
	font-weight: 850;
	letter-spacing: 0.14em;
}
header p svg {
	width: 1rem;
}
header h2 {
	margin: 0;
	font-size: clamp(1.5rem, 2.4vw, 2.25rem);
	letter-spacing: -0.035em;
}
header span {
	display: block;
	max-width: 46rem;
	margin-top: 0.55rem;
	color: rgba(226, 232, 240, 0.62);
	line-height: 1.45;
}
.pack-grid {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 0.75rem;
}
.pack-card {
	--pack-accent: #c084fc;
	position: relative;
	isolation: isolate;
	display: grid;
	grid-template-columns: 5.2rem minmax(0, 1fr) auto;
	align-items: center;
	gap: 1rem;
	min-height: 8.3rem;
	padding: 1rem;
	overflow: hidden;
	border: 1px solid rgba(255, 255, 255, 0.08);
	border-radius: 0.95rem;
	background: linear-gradient(135deg, rgba(25, 31, 45, 0.96), rgba(10, 14, 23, 0.98));
	color: inherit;
	text-align: left;
	cursor: pointer;
}
.pack-card:hover {
	transform: translateY(-2px);
	border-color: color-mix(in srgb, var(--pack-accent) 48%, transparent);
}
.pack-glow {
	position: absolute;
	z-index: -1;
	inset: auto auto -5rem -4rem;
	width: 13rem;
	height: 13rem;
	border-radius: 50%;
	background: var(--pack-accent);
	opacity: 0.12;
	filter: blur(35px);
}
.pack-card img {
	width: 5.2rem;
	height: 5.2rem;
	border-radius: 1rem;
	object-fit: cover;
	box-shadow: 0 12px 32px rgba(0, 0, 0, 0.3);
}
.pack-copy {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 0.3rem;
}
.pack-copy > small {
	color: var(--pack-accent);
	font-size: 0.68rem;
	font-weight: 750;
}
.pack-copy > strong {
	font-size: 1.1rem;
}
.pack-copy > span:not(.pack-meta) {
	display: -webkit-box;
	overflow: hidden;
	color: rgba(226, 232, 240, 0.63);
	font-size: 0.78rem;
	line-height: 1.4;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 2;
}
.pack-meta {
	display: flex;
	gap: 0.8rem;
	margin-top: 0.2rem;
}
.pack-meta span {
	display: flex;
	align-items: center;
	gap: 0.3rem;
	color: rgba(226, 232, 240, 0.48);
	font-size: 0.68rem;
}
.pack-meta svg,
.arrow {
	width: 0.9rem;
}
.arrow {
	color: var(--pack-accent);
}
@media (max-width: 900px) {
	.pack-grid {
		grid-template-columns: 1fr;
	}
}
@media (prefers-reduced-motion: no-preference) {
	.pack-card {
		transition:
			transform 180ms ease,
			border-color 180ms ease;
	}
}
</style>
