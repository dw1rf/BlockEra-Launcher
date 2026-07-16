<script setup lang="ts">
import {
	ArrowLeftIcon,
	DownloadIcon,
	LinkIcon,
	PackageIcon,
	SearchIcon,
	SparklesIcon,
	SpinnerIcon,
	UserIcon,
} from '@modrinth/assets'
import { injectNotificationManager } from '@modrinth/ui'
import { openUrl } from '@tauri-apps/plugin-opener'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { recommendedPackBySlug } from '@/data/recommended-packs'
import { get_project, get_project_many, get_version } from '@/helpers/cache'
import { install } from '@/store/install'

type ProjectDependency = {
	project_id?: string
}

type ModrinthVersion = {
	id: string
	version_number: string
	dependencies: ProjectDependency[]
}

type ModrinthProject = {
	id: string
	title: string
	description: string
	icon_url?: string
	downloads: number
	versions: string[]
	project_type: string
	slug: string
}

const route = useRoute()
const router = useRouter()
const { handleError } = injectNotificationManager()
const pack = recommendedPackBySlug(String(route.params.slug))

const project = ref<ModrinthProject>()
const latestVersion = ref<ModrinthVersion>()
const dependencies = ref<ModrinthProject[]>([])
const loading = ref(true)
const installing = ref(false)
const search = ref('')
const showAll = ref(false)

const filteredDependencies = computed(() => {
	const query = search.value.trim().toLocaleLowerCase('ru-RU')
	const values = query
		? dependencies.value.filter((dependency) =>
				`${dependency.title} ${dependency.description}`.toLocaleLowerCase('ru-RU').includes(query),
			)
		: dependencies.value
	return showAll.value || query ? values : values.slice(0, 48)
})

const dependencyCount = computed(
	() =>
		latestVersion.value?.dependencies?.filter((dependency) => dependency.project_id).length ?? 0,
)

function compactNumber(value?: number) {
	return new Intl.NumberFormat('ru-RU', { notation: 'compact', maximumFractionDigits: 1 }).format(
		value ?? 0,
	)
}

async function loadDependencies(ids: string[]) {
	const chunks: string[][] = []
	for (let index = 0; index < ids.length; index += 80) chunks.push(ids.slice(index, index + 80))
	const results = await Promise.all(
		chunks.map((chunk) => get_project_many(chunk, 'stale_while_revalidate').catch(() => [])),
	)
	return results.flat().sort((left, right) => left.title.localeCompare(right.title, 'ru'))
}

async function installPack() {
	if (!pack || installing.value) return
	installing.value = true
	try {
		await install(pack.projectId, latestVersion.value?.id ?? null, null, 'BlockEraRecommended')
	} catch (error) {
		handleError(error)
	} finally {
		installing.value = false
	}
}

onMounted(async () => {
	if (!pack) {
		await router.replace('/library/recommended')
		return
	}
	try {
		project.value = await get_project(pack.projectId, 'stale_while_revalidate')
		const versionId = project.value.versions.at(-1)
		latestVersion.value = versionId ? await get_version(versionId, 'stale_while_revalidate') : null
		const ids = [
			...new Set(
				(latestVersion.value?.dependencies ?? [])
					.map((dependency) => dependency.project_id)
					.filter(Boolean),
			),
		] as string[]
		dependencies.value = await loadDependencies(ids)
	} catch (error) {
		handleError(error)
	} finally {
		loading.value = false
	}
})
</script>

<template>
	<section v-if="pack" class="pack-dossier" :style="{ '--pack-accent': pack.accent }">
		<button class="back-button" @click="router.push('/library/recommended')">
			<ArrowLeftIcon /> Все рекомендации
		</button>

		<div class="pack-hero">
			<span class="hero-glow"></span>
			<img :src="project?.icon_url ?? pack.iconUrl" alt="" />
			<div class="hero-copy">
				<p><SparklesIcon /> РЕКОМЕНДУЕТ BLOCKERA</p>
				<h1>{{ project?.title ?? pack.title }}</h1>
				<span>{{ pack.tagline }}</span>
				<div class="hero-meta">
					<span><UserIcon /> {{ pack.ownerKind }}: {{ pack.owner }}</span>
					<span><DownloadIcon /> {{ compactNumber(project?.downloads) }} загрузок</span>
					<span><PackageIcon /> {{ latestVersion?.version_number ?? 'актуальная версия' }}</span>
				</div>
			</div>
			<button class="install-button" :disabled="installing || loading" @click="installPack">
				<SpinnerIcon v-if="installing" class="spin" />
				<DownloadIcon v-else />
				{{ installing ? 'Устанавливаем…' : 'Установить сборку' }}
			</button>
		</div>

		<div class="dossier-grid">
			<article class="story-card">
				<p class="section-label">О СБОРКЕ</p>
				<p v-for="paragraph in pack.description" :key="paragraph">{{ paragraph }}</p>
				<div class="author-card">
					<div>
						<small>{{ pack.ownerKind }}</small
						><strong>{{ pack.owner }}</strong>
					</div>
					<div class="author-links">
						<button v-for="link in pack.links" :key="link.url" @click="openUrl(link.url)">
							<LinkIcon /> {{ link.label }}
						</button>
					</div>
				</div>
			</article>

			<article class="mods-card">
				<div class="mods-heading">
					<div>
						<p class="section-label">СОСТАВ ПОСЛЕДНЕЙ ВЕРСИИ</p>
						<h2>{{ dependencyCount }} модов и компонентов</h2>
					</div>
					<label
						><SearchIcon /><input v-model="search" type="search" placeholder="Найти мод"
					/></label>
				</div>
				<div v-if="loading" class="mods-loading">
					<SpinnerIcon class="spin" /> Загружаем состав сборки…
				</div>
				<div v-else-if="filteredDependencies.length" class="mods-list">
					<button
						v-for="dependency in filteredDependencies"
						:key="dependency.id"
						@click="openUrl(`https://modrinth.com/${dependency.project_type}/${dependency.slug}`)"
					>
						<img v-if="dependency.icon_url" :src="dependency.icon_url" alt="" />
						<span v-else class="mod-placeholder"><PackageIcon /></span>
						<span
							><strong>{{ dependency.title }}</strong
							><small>{{ dependency.description }}</small></span
						>
						<LinkIcon />
					</button>
				</div>
				<p v-else class="empty-mods">Список компонентов для этой версии не опубликован.</p>
				<button
					v-if="!showAll && !search && dependencies.length > 48"
					class="show-all"
					@click="showAll = true"
				>
					Показать весь состав — {{ dependencies.length }}
				</button>
			</article>
		</div>
	</section>
</template>

<style scoped lang="scss">
.pack-dossier {
	--pack-accent: #c084fc;
	display: flex;
	flex-direction: column;
	gap: 0.8rem;
	color: #f8fafc;
}
.back-button {
	align-self: flex-start;
	display: flex;
	align-items: center;
	gap: 0.45rem;
	padding: 0.35rem 0;
	border: 0;
	background: transparent;
	color: rgba(226, 232, 240, 0.62);
	cursor: pointer;
}
.back-button:hover {
	color: #fff;
}
.back-button svg {
	width: 1rem;
}
.pack-hero {
	position: relative;
	isolation: isolate;
	display: grid;
	grid-template-columns: 7rem minmax(0, 1fr) auto;
	align-items: center;
	gap: 1.4rem;
	min-height: 10.5rem;
	padding: 1.35rem;
	overflow: hidden;
	border: 1px solid color-mix(in srgb, var(--pack-accent) 30%, transparent);
	border-radius: 1rem;
	background: linear-gradient(120deg, rgba(27, 32, 47, 0.98), rgba(8, 12, 20, 0.98));
}
.hero-glow {
	position: absolute;
	z-index: -1;
	left: -5rem;
	width: 22rem;
	height: 22rem;
	border-radius: 50%;
	background: var(--pack-accent);
	opacity: 0.12;
	filter: blur(55px);
}
.pack-hero > img {
	width: 7rem;
	height: 7rem;
	border-radius: 1.25rem;
	object-fit: cover;
	box-shadow: 0 18px 42px rgba(0, 0, 0, 0.35);
}
.hero-copy {
	min-width: 0;
}
.hero-copy > p,
.section-label {
	display: flex;
	align-items: center;
	gap: 0.4rem;
	margin: 0 0 0.4rem;
	color: var(--pack-accent);
	font-size: 0.67rem;
	font-weight: 850;
	letter-spacing: 0.12em;
}
.hero-copy > p svg {
	width: 0.9rem;
}
.hero-copy h1 {
	margin: 0;
	font-size: clamp(1.8rem, 3vw, 3rem);
	letter-spacing: -0.04em;
	line-height: 1;
}
.hero-copy > span {
	display: block;
	max-width: 42rem;
	margin-top: 0.55rem;
	color: rgba(226, 232, 240, 0.68);
	line-height: 1.4;
}
.hero-meta {
	display: flex;
	flex-wrap: wrap;
	gap: 0.5rem;
	margin-top: 0.75rem;
}
.hero-meta span {
	display: flex;
	align-items: center;
	gap: 0.35rem;
	padding: 0.35rem 0.55rem;
	border: 1px solid rgba(255, 255, 255, 0.07);
	border-radius: 0.45rem;
	background: rgba(0, 0, 0, 0.2);
	color: rgba(226, 232, 240, 0.68);
	font-size: 0.68rem;
}
.hero-meta svg {
	width: 0.85rem;
}
.install-button {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 0.55rem;
	min-width: 12rem;
	min-height: 3rem;
	padding: 0 1rem;
	border: 1px solid color-mix(in srgb, var(--pack-accent) 55%, white 5%);
	border-radius: 0.65rem;
	background: color-mix(in srgb, var(--pack-accent) 72%, #4c1d95);
	color: #fff;
	font-weight: 800;
	cursor: pointer;
}
.install-button:disabled {
	opacity: 0.65;
	cursor: wait;
}
.install-button svg {
	width: 1.05rem;
}
.dossier-grid {
	display: grid;
	grid-template-columns: minmax(16rem, 0.62fr) minmax(24rem, 1.38fr);
	gap: 0.8rem;
}
.story-card,
.mods-card {
	padding: 1.15rem;
	border: 1px solid rgba(255, 255, 255, 0.075);
	border-radius: 1rem;
	background: rgba(13, 18, 28, 0.92);
}
.story-card > p:not(.section-label) {
	margin: 0.7rem 0 0;
	color: rgba(226, 232, 240, 0.72);
	line-height: 1.58;
}
.author-card {
	display: flex;
	flex-direction: column;
	gap: 0.8rem;
	margin-top: 1.2rem;
	padding: 1rem;
	border-left: 2px solid var(--pack-accent);
	background: rgba(255, 255, 255, 0.035);
}
.author-card div:first-child {
	display: flex;
	flex-direction: column;
}
.author-card small {
	color: var(--pack-accent);
}
.author-links {
	display: flex;
	flex-wrap: wrap;
	gap: 0.45rem;
}
.author-links button {
	display: flex;
	align-items: center;
	gap: 0.35rem;
	padding: 0.45rem 0.55rem;
	border: 1px solid rgba(255, 255, 255, 0.08);
	border-radius: 0.5rem;
	background: rgba(255, 255, 255, 0.045);
	color: rgba(241, 245, 249, 0.8);
	font-size: 0.72rem;
	cursor: pointer;
}
.author-links svg {
	width: 0.8rem;
}
.mods-heading {
	display: flex;
	align-items: flex-end;
	justify-content: space-between;
	gap: 1rem;
	margin-bottom: 0.8rem;
}
.mods-heading h2 {
	margin: 0;
	font-size: 1.15rem;
}
.mods-heading label {
	display: flex;
	align-items: center;
	gap: 0.4rem;
	min-width: 13rem;
	padding: 0 0.6rem;
	border: 1px solid rgba(255, 255, 255, 0.08);
	border-radius: 0.55rem;
	background: rgba(4, 7, 13, 0.55);
}
.mods-heading label svg {
	width: 0.9rem;
	color: rgba(226, 232, 240, 0.45);
}
.mods-heading input {
	width: 100%;
	min-height: 2.35rem;
	padding: 0;
	border: 0;
	background: transparent;
	box-shadow: none;
}
.mods-list {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 0.4rem;
	max-height: 24rem;
	overflow: auto;
	padding-right: 0.2rem;
}
.mods-list > button {
	display: grid;
	grid-template-columns: 2.2rem minmax(0, 1fr) auto;
	align-items: center;
	gap: 0.55rem;
	min-height: 3rem;
	padding: 0.38rem;
	border: 1px solid rgba(255, 255, 255, 0.055);
	border-radius: 0.55rem;
	background: rgba(255, 255, 255, 0.025);
	color: inherit;
	text-align: left;
	cursor: pointer;
}
.mods-list > button:hover {
	border-color: color-mix(in srgb, var(--pack-accent) 35%, transparent);
	background: rgba(255, 255, 255, 0.05);
}
.mods-list img,
.mod-placeholder {
	display: grid;
	place-items: center;
	width: 2.2rem;
	height: 2.2rem;
	border-radius: 0.45rem;
	object-fit: cover;
	background: rgba(255, 255, 255, 0.05);
}
.mods-list span:not(.mod-placeholder) {
	min-width: 0;
	display: flex;
	flex-direction: column;
	gap: 0.1rem;
}
.mods-list strong,
.mods-list small {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.mods-list strong {
	font-size: 0.72rem;
}
.mods-list small {
	color: rgba(226, 232, 240, 0.46);
	font-size: 0.6rem;
}
.mods-list > button > svg {
	width: 0.75rem;
	color: rgba(226, 232, 240, 0.32);
}
.mod-placeholder svg {
	width: 1rem;
}
.mods-loading,
.empty-mods {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 0.6rem;
	min-height: 10rem;
	color: rgba(226, 232, 240, 0.55);
}
.mods-loading svg {
	width: 1rem;
}
.show-all {
	width: 100%;
	margin-top: 0.65rem;
	padding: 0.55rem;
	border: 1px solid rgba(192, 132, 252, 0.2);
	border-radius: 0.55rem;
	background: rgba(126, 34, 206, 0.08);
	color: #d8b4fe;
	cursor: pointer;
}
.spin {
	animation: spin 0.8s linear infinite;
}
@keyframes spin {
	to {
		transform: rotate(360deg);
	}
}
@media (max-width: 1000px) {
	.dossier-grid {
		grid-template-columns: 1fr;
	}
	.pack-hero {
		grid-template-columns: 5.5rem 1fr;
	}
	.pack-hero > img {
		width: 5.5rem;
		height: 5.5rem;
	}
	.install-button {
		grid-column: 1/-1;
	}
}
@media (max-width: 700px) {
	.mods-list {
		grid-template-columns: 1fr;
	}
	.mods-heading {
		align-items: stretch;
		flex-direction: column;
	}
}
@media (prefers-reduced-motion: reduce) {
	.spin {
		animation: none;
	}
}
</style>
