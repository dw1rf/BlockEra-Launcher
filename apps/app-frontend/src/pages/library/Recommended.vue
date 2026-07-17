<script setup lang="ts">
import {
	ChevronRightIcon,
	DownloadIcon,
	PackageIcon,
	SparklesIcon,
	UserIcon,
} from '@modrinth/assets'
import { injectNotificationManager } from '@modrinth/ui'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import industrialEraCover from '@/assets/launcher/industrial-era-2-cover.png'
import { recommendedPacks } from '@/data/recommended-packs'
import { get_project_many } from '@/helpers/cache'
import { get_catalog } from '@/helpers/pack'
import { list as listProfiles } from '@/helpers/profile'

type CatalogPack = {
	id: string
	title: string
	summary: string
	author: { displayName: string; channelName: string }
	officialUrl: string
	minecraft: string
	forge: string
	version: string
	revision: string
	size: number
	coverUrl?: string
	status: 'ready'
}

type ModrinthProject = {
	id: string
	title: string
	downloads: number
	followers: number
	icon_url?: string
}

type Profile = {
	path: string
	install_stage: string
	external_pack?: { catalogId: string }
}

const router = useRouter()
const { handleError } = injectNotificationManager()
const projects = ref<ModrinthProject[]>([])
const creatorPacks = ref<CatalogPack[]>([])
const profiles = ref<Profile[]>([])

const popularCards = computed(() =>
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

function formatBytes(value: number) {
	return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value / 1024 / 1024)
}

function installedProfile(packId: string) {
	return profiles.value.find((profile) => profile.external_pack?.catalogId === packId)
}

onMounted(async () => {
	const [catalog, modrinthProjects, profileList] = await Promise.all([
		get_catalog().catch((error) => {
			handleError(error)
			return null
		}),
		get_project_many(
			recommendedPacks.map((pack) => pack.projectId),
			'stale_while_revalidate',
		).catch((error) => {
			handleError(error)
			return []
		}),
		listProfiles().catch(() => []),
	])
	creatorPacks.value = catalog?.catalog?.packs ?? []
	projects.value = modrinthProjects
	profiles.value = profileList
})
</script>

<template>
	<section class="recommended-catalog">
		<header class="catalog-heading">
			<p><SparklesIcon /> ВЫБОР BLOCKERA</p>
			<h2>Проверенные сборки для нового мира</h2>
			<span
				>Авторские публикации отделены от проектов Modrinth, а автор и официальный канал всегда
				указаны явно.</span
			>
		</header>

		<section class="catalog-group">
			<div class="group-heading">
				<div>
					<p class="eyebrow">СБОРКИ АВТОРОВ</p>
					<h3>Готовые авторские сборки</h3>
				</div>
				<span>Только проверенные .mrpack</span>
			</div>
			<div class="pack-grid creator-grid">
				<button
					v-for="pack in creatorPacks"
					:key="pack.id"
					class="pack-card creator-card"
					@click="router.push(`/library/recommended/${pack.id}`)"
				>
					<span class="pack-glow"></span>
					<img :src="pack.coverUrl ?? industrialEraCover" :alt="`Обложка ${pack.title}`" />
					<span class="pack-copy">
						<small>{{ pack.author.channelName }} · {{ pack.author.displayName }}</small>
						<strong>{{ pack.title }}</strong>
						<span>{{ pack.summary }}</span>
						<span class="pack-meta">
							<span><PackageIcon /> Minecraft {{ pack.minecraft }}</span>
							<span>Forge {{ pack.forge }}</span>
							<span>v{{ pack.version }}</span>
							<span>{{ formatBytes(pack.size) }} МБ</span>
							<span>GitHub Release · .mrpack</span>
							<span class="status">{{
								installedProfile(pack.id) ? 'Установлена' : 'Не установлена'
							}}</span>
						</span>
						<em
							>Автор сборки — {{ pack.author.displayName }}. BlockEra не является создателем
							сборки.</em
						>
					</span>
					<ChevronRightIcon class="arrow" />
				</button>
			</div>
		</section>

		<section class="catalog-group">
			<div class="group-heading">
				<div>
					<p class="eyebrow">MODRINTH</p>
					<h3>Популярное с Modrinth</h3>
				</div>
			</div>
			<div class="pack-grid">
				<button
					v-for="pack in popularCards"
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
							<span
								><UserIcon /> {{ pack.project?.followers?.toLocaleString('ru-RU') ?? '—' }}</span
							>
						</span>
					</span>
					<ChevronRightIcon class="arrow" />
				</button>
			</div>
		</section>
	</section>
</template>

<style scoped lang="scss">
.recommended-catalog,
.catalog-group {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	color: #f8fafc;
}
.catalog-heading {
	padding: 0.35rem 0.15rem 0.2rem;
}
.catalog-heading p,
.eyebrow {
	display: flex;
	align-items: center;
	gap: 0.45rem;
	margin: 0 0 0.45rem;
	color: #c084fc;
	font-size: 0.7rem;
	font-weight: 850;
	letter-spacing: 0.14em;
}
.catalog-heading p svg {
	width: 1rem;
}
.catalog-heading h2,
.group-heading h3 {
	margin: 0;
	letter-spacing: -0.035em;
}
.catalog-heading h2 {
	font-size: clamp(1.5rem, 2.4vw, 2.25rem);
}
.catalog-heading > span {
	display: block;
	max-width: 48rem;
	margin-top: 0.55rem;
	color: rgba(226, 232, 240, 0.62);
	line-height: 1.45;
}
.group-heading {
	display: flex;
	align-items: flex-end;
	justify-content: space-between;
	padding: 0 0.15rem;
}
.group-heading > span {
	color: rgba(226, 232, 240, 0.42);
	font-size: 0.72rem;
}
.pack-grid {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 0.75rem;
}
.creator-grid {
	grid-template-columns: 1fr;
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
.creator-card {
	--pack-accent: #f59e0b;
	grid-template-columns: 8rem minmax(0, 1fr) auto;
	min-height: 10rem;
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
.creator-card img {
	width: 8rem;
	height: 8rem;
}
.pack-copy {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 0.32rem;
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
.pack-copy em {
	color: rgba(226, 232, 240, 0.48);
	font-size: 0.66rem;
	font-style: normal;
}
.pack-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 0.45rem 0.8rem;
	margin-top: 0.2rem;
}
.pack-meta span {
	display: flex;
	align-items: center;
	gap: 0.3rem;
	color: rgba(226, 232, 240, 0.52);
	font-size: 0.68rem;
}
.pack-meta .status {
	color: #fbbf24;
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
@media (max-width: 620px) {
	.creator-card {
		grid-template-columns: 5rem minmax(0, 1fr);
	}
	.creator-card img {
		width: 5rem;
		height: 5rem;
	}
	.arrow {
		display: none;
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
