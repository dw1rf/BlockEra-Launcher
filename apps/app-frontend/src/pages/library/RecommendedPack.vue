<script setup lang="ts">
import {
	ArrowLeftIcon,
	CheckCircleIcon,
	DownloadIcon,
	FolderOpenIcon,
	LinkIcon,
	PackageIcon,
	PlayIcon,
	RefreshCwIcon,
	SpinnerIcon,
	TrashIcon,
	UserIcon,
	XIcon,
} from '@modrinth/assets'
import { injectNotificationManager } from '@modrinth/ui'
import { openUrl } from '@tauri-apps/plugin-opener'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import industrialEraCover from '@/assets/launcher/industrial-era-2-cover.png'
import { recommendedPackBySlug } from '@/data/recommended-packs'
import { get_project, get_version } from '@/helpers/cache'
import {
	cancel_catalog_install,
	get_catalog,
	get_catalog_install_status,
	install_catalog_pack,
	verify_catalog_install,
} from '@/helpers/pack'
import { list as listProfiles, remove, run } from '@/helpers/profile'
import { openProfileFolder } from '@/helpers/utils'
import { install } from '@/store/install'

type CatalogPack = {
	id: string
	title: string
	summary: string
	description: string[]
	mods: string[]
	author: { displayName: string; channelName: string }
	officialUrl: string
	originalAuthor: string
	unofficialIntegration: boolean
	minecraft: string
	forge: string
	version: string
	revision: string
	size: number
	coverUrl?: string
	permission: string
	download: { url: string }
}

type Profile = {
	path: string
	install_stage: string
	external_pack?: { catalogId: string }
}

type InstallStatus = {
	jobId: string
	packId: string
	state: 'downloading' | 'installing' | 'installed' | 'cancelled' | 'error'
	downloaded: number
	total: number
	profilePath?: string
	error?: string
}

type ModrinthProject = {
	id: string
	title: string
	description: string
	icon_url?: string
	downloads: number
	versions: string[]
}

type ModrinthVersion = { id: string; version_number: string }

const route = useRoute()
const router = useRouter()
const { handleError } = injectNotificationManager()
const slug = String(route.params.slug)
const popularPack = recommendedPackBySlug(slug)
const creatorPack = ref<CatalogPack>()
const project = ref<ModrinthProject>()
const latestVersion = ref<ModrinthVersion>()
const profiles = ref<Profile[]>([])
const loading = ref(true)
const installingPopular = ref(false)
const installJobId = ref<string>()
const installStatus = ref<InstallStatus>()
const verification = ref<'idle' | 'checking' | 'valid' | 'corrupted'>('idle')
let pollTimer: ReturnType<typeof setTimeout> | undefined

const installedProfile = computed(() => {
	const packId = creatorPack.value?.id
	return profiles.value.find((profile) => profile.external_pack?.catalogId === packId)
})

const activeProfilePath = computed(
	() => installStatus.value?.profilePath ?? installedProfile.value?.path,
)

const progress = computed(() => {
	if (!installStatus.value?.total) return 0
	return Math.min(100, (installStatus.value.downloaded / installStatus.value.total) * 100)
})

const creatorStateLabel = computed(() => {
	if (verification.value === 'corrupted') return 'Повреждена'
	switch (installStatus.value?.state) {
		case 'downloading':
			return `Загружается · ${Math.round(progress.value)}%`
		case 'installing':
			return 'Устанавливается'
		case 'error':
			return 'Ошибка'
		case 'cancelled':
			return 'Не установлена'
		case 'installed':
			return 'Установлена'
		default:
			return installedProfile.value ? 'Установлена' : 'Не установлена'
	}
})

function formatBytes(value: number) {
	return new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value / 1024 / 1024)
}

function compactNumber(value?: number) {
	return new Intl.NumberFormat('ru-RU', { notation: 'compact', maximumFractionDigits: 1 }).format(
		value ?? 0,
	)
}

async function refreshProfiles() {
	profiles.value = await listProfiles().catch(() => [])
}

async function pollInstall() {
	if (!installJobId.value) return
	try {
		installStatus.value = await get_catalog_install_status(installJobId.value)
		if (installStatus.value.state === 'installed') {
			await refreshProfiles()
			return
		}
		if (installStatus.value.state === 'error' || installStatus.value.state === 'cancelled') return
		pollTimer = setTimeout(pollInstall, 750)
	} catch (error) {
		handleError(error)
	}
}

async function installCreatorPack() {
	if (!creatorPack.value) return
	verification.value = 'idle'
	try {
		installJobId.value = await install_catalog_pack(creatorPack.value.id)
		await pollInstall()
	} catch (error) {
		handleError(error)
	}
}

async function cancelInstall() {
	if (!installJobId.value) return
	try {
		await cancel_catalog_install(installJobId.value)
	} catch (error) {
		handleError(error)
	}
}

async function verifyPack() {
	if (!activeProfilePath.value) return
	verification.value = 'checking'
	try {
		verification.value = (await verify_catalog_install(activeProfilePath.value))
			? 'valid'
			: 'corrupted'
	} catch (error) {
		verification.value = 'corrupted'
		handleError(error)
	}
}

async function playPack() {
	if (!activeProfilePath.value) return
	try {
		await run(activeProfilePath.value)
	} catch (error) {
		handleError(error)
	}
}

async function deletePack() {
	if (!activeProfilePath.value) return
	if (!window.confirm('Удалить профиль этой сборки и все его локальные файлы?')) return
	try {
		await remove(activeProfilePath.value)
		installStatus.value = undefined
		installJobId.value = undefined
		verification.value = 'idle'
		await refreshProfiles()
	} catch (error) {
		handleError(error)
	}
}

async function reinstallPack() {
	await deletePack()
	if (!activeProfilePath.value) await installCreatorPack()
}

async function installPopularPack() {
	if (!popularPack || installingPopular.value) return
	installingPopular.value = true
	try {
		await install(
			popularPack.projectId,
			latestVersion.value?.id ?? null,
			null,
			'BlockEraRecommended',
		)
	} catch (error) {
		handleError(error)
	} finally {
		installingPopular.value = false
	}
}

onMounted(async () => {
	try {
		const response = await get_catalog().catch(() => null)
		creatorPack.value = response?.catalog?.packs?.find((pack: CatalogPack) => pack.id === slug)
		if (creatorPack.value) {
			await refreshProfiles()
			return
		}
		if (!popularPack) {
			await router.replace('/library/recommended')
			return
		}
		project.value = await get_project(popularPack.projectId, 'stale_while_revalidate')
		const versionId = project.value.versions.at(-1)
		latestVersion.value = versionId
			? await get_version(versionId, 'stale_while_revalidate')
			: undefined
	} catch (error) {
		handleError(error)
	} finally {
		loading.value = false
	}
})

onBeforeUnmount(() => {
	if (pollTimer) clearTimeout(pollTimer)
})
</script>

<template>
	<section v-if="creatorPack" class="pack-dossier creator-dossier">
		<button class="back-button" @click="router.push('/library/recommended')">
			<ArrowLeftIcon /> Все рекомендации
		</button>

		<div class="pack-hero">
			<span class="hero-glow"></span>
			<img
				:src="creatorPack.coverUrl ?? industrialEraCover"
				:alt="`Обложка ${creatorPack.title}`"
			/>
			<div class="hero-copy">
				<p>СБОРКА АВТОРА · {{ creatorPack.author.channelName }}</p>
				<h1>{{ creatorPack.title }}</h1>
				<span>{{ creatorPack.summary }}</span>
				<div class="hero-meta">
					<span><UserIcon /> {{ creatorPack.author.displayName }}</span>
					<span><PackageIcon /> Minecraft {{ creatorPack.minecraft }}</span>
					<span>Forge {{ creatorPack.forge }}</span>
					<span>Версия {{ creatorPack.version }}</span>
					<span>{{ formatBytes(creatorPack.size) }} МБ</span>
					<span class="state-pill">{{ creatorStateLabel }}</span>
				</div>
			</div>
			<div class="primary-actions">
				<button
					v-if="
						!activeProfilePath &&
						!['downloading', 'installing'].includes(installStatus?.state ?? '')
					"
					class="primary-button"
					@click="installCreatorPack"
				>
					<DownloadIcon /> Установить
				</button>
				<button
					v-if="installStatus?.state === 'downloading'"
					class="danger-button"
					@click="cancelInstall"
				>
					<XIcon /> Отменить
				</button>
				<button v-if="installStatus?.state === 'installing'" class="primary-button" disabled>
					<SpinnerIcon class="spin" /> Устанавливается…
				</button>
				<button v-if="activeProfilePath" class="primary-button" @click="playPack">
					<PlayIcon /> Играть
				</button>
			</div>
		</div>

		<div v-if="installStatus?.state === 'downloading'" class="progress-track">
			<span :style="{ width: `${progress}%` }"></span>
		</div>
		<p v-if="installStatus?.error" class="error-message">{{ installStatus.error }}</p>

		<div class="dossier-grid">
			<article class="story-card">
				<p class="section-label">О СБОРКЕ</p>
				<p v-for="paragraph in creatorPack.description" :key="paragraph">{{ paragraph }}</p>
				<div class="author-contact">
					<div>
						<span>Автор сборки</span>
						<strong>{{ creatorPack.author.displayName }}</strong>
						<small>{{ creatorPack.author.channelName }}</small>
					</div>
					<button @click="openUrl(creatorPack.officialUrl)"><LinkIcon /> Discord автора</button>
				</div>
			</article>

			<article class="mods-card">
				<div class="mods-heading">
					<div>
						<p class="section-label">МОДЫ В СБОРКЕ</p>
						<strong>Основные технологии и дополнения</strong>
					</div>
					<span>{{ creatorPack.mods.length }}</span>
				</div>
				<div class="mods-list">
					<span v-for="mod in creatorPack.mods" :key="mod" class="mod-chip">
						<PackageIcon /> {{ mod }}
					</span>
				</div>
				<div v-if="activeProfilePath" class="secondary-actions instance-actions">
					<button v-if="activeProfilePath" @click="openProfileFolder(activeProfilePath)">
						<FolderOpenIcon /> Открыть папку
					</button>
					<button
						v-if="activeProfilePath"
						:disabled="verification === 'checking'"
						@click="verifyPack"
					>
						<SpinnerIcon v-if="verification === 'checking'" class="spin" />
						<CheckCircleIcon v-else /> Проверить файлы
					</button>
					<button v-if="activeProfilePath" class="delete-action" @click="deletePack">
						<TrashIcon /> Удалить
					</button>
					<button
						v-if="activeProfilePath"
						@click="router.push(`/instance/${encodeURIComponent(activeProfilePath)}`)"
					>
						<PackageIcon /> Открыть профиль
					</button>
					<button v-if="verification === 'corrupted'" @click="reinstallPack">
						<RefreshCwIcon /> Переустановить после удаления
					</button>
				</div>
			</article>
		</div>
	</section>

	<section
		v-else-if="popularPack"
		class="pack-dossier"
		:style="{ '--pack-accent': popularPack.accent }"
	>
		<button class="back-button" @click="router.push('/library/recommended')">
			<ArrowLeftIcon /> Все рекомендации
		</button>
		<div class="pack-hero">
			<span class="hero-glow"></span>
			<img :src="project?.icon_url ?? popularPack.iconUrl" alt="" />
			<div class="hero-copy">
				<p>ПОПУЛЯРНОЕ С MODRINTH</p>
				<h1>{{ project?.title ?? popularPack.title }}</h1>
				<span>{{ popularPack.tagline }}</span>
				<div class="hero-meta">
					<span><UserIcon /> {{ popularPack.ownerKind }}: {{ popularPack.owner }}</span>
					<span><DownloadIcon /> {{ compactNumber(project?.downloads) }} загрузок</span>
					<span><PackageIcon /> {{ latestVersion?.version_number ?? 'актуальная версия' }}</span>
				</div>
			</div>
			<button
				class="primary-button"
				:disabled="installingPopular || loading"
				@click="installPopularPack"
			>
				<SpinnerIcon v-if="installingPopular" class="spin" /><DownloadIcon v-else />
				{{ installingPopular ? 'Устанавливается…' : 'Установить сборку' }}
			</button>
		</div>
		<article class="story-card">
			<p class="section-label">О СБОРКЕ</p>
			<p v-for="paragraph in popularPack.description" :key="paragraph">{{ paragraph }}</p>
			<div class="secondary-actions">
				<button v-for="link in popularPack.links" :key="link.url" @click="openUrl(link.url)">
					<LinkIcon /> {{ link.label }}
				</button>
			</div>
		</article>
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
.creator-dossier {
	--pack-accent: #f59e0b;
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
.back-button svg,
button svg {
	width: 1rem;
}
.pack-hero {
	position: relative;
	isolation: isolate;
	display: grid;
	grid-template-columns: 8rem minmax(0, 1fr) auto;
	align-items: center;
	gap: 1.4rem;
	padding: 1.35rem;
	overflow: hidden;
	border: 1px solid color-mix(in srgb, var(--pack-accent) 32%, transparent);
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
	width: 8rem;
	height: 8rem;
	border-radius: 1.25rem;
	object-fit: cover;
}
.hero-copy p,
.section-label {
	margin: 0 0 0.45rem;
	color: var(--pack-accent);
	font-size: 0.67rem;
	font-weight: 850;
	letter-spacing: 0.12em;
}
.hero-copy h1 {
	margin: 0;
	font-size: clamp(1.8rem, 3vw, 3rem);
	letter-spacing: -0.04em;
	line-height: 1;
}
.hero-copy > span {
	display: block;
	max-width: 44rem;
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
.hero-meta .state-pill {
	color: #fbbf24;
}
.primary-actions,
.secondary-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 0.5rem;
}
.primary-actions {
	flex-direction: column;
}
.primary-button,
.danger-button,
.author-contact button,
.secondary-actions button {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 0.5rem;
	min-height: 2.7rem;
	padding: 0 0.9rem;
	border: 1px solid color-mix(in srgb, var(--pack-accent) 48%, white 5%);
	border-radius: 0.65rem;
	background: color-mix(in srgb, var(--pack-accent) 68%, #4c1d95);
	color: #fff;
	font-weight: 750;
	cursor: pointer;
}
.danger-button,
.secondary-actions .delete-action {
	border-color: rgba(248, 113, 113, 0.35);
	background: rgba(127, 29, 29, 0.45);
}
button:disabled {
	opacity: 0.6;
	cursor: wait;
}
.progress-track {
	height: 0.45rem;
	overflow: hidden;
	border-radius: 1rem;
	background: rgba(255, 255, 255, 0.07);
}
.progress-track span {
	display: block;
	height: 100%;
	background: var(--pack-accent);
	transition: width 200ms ease;
}
.error-message {
	margin: 0;
	padding: 0.75rem;
	border: 1px solid rgba(248, 113, 113, 0.22);
	border-radius: 0.7rem;
	background: rgba(127, 29, 29, 0.18);
	color: #fecaca;
}
.dossier-grid {
	display: grid;
	grid-template-columns: minmax(20rem, 1fr) minmax(20rem, 1fr);
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
	color: rgba(226, 232, 240, 0.72);
	line-height: 1.58;
}
.author-contact {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
	margin-top: 1rem;
	padding: 0.9rem;
	border-left: 2px solid var(--pack-accent);
	background: rgba(255, 255, 255, 0.035);
}
.author-contact > div {
	display: flex;
	flex-direction: column;
	gap: 0.2rem;
}
.author-contact span,
.author-contact small {
	color: rgba(226, 232, 240, 0.58);
}
.author-contact button {
	flex: none;
	border-color: rgba(255, 255, 255, 0.09);
	background: rgba(255, 255, 255, 0.045);
	font-size: 0.72rem;
}
.mods-card {
	display: flex;
	min-height: 0;
	flex-direction: column;
}
.mods-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
}
.mods-heading .section-label {
	margin-bottom: 0.25rem;
}
.mods-heading > span {
	display: grid;
	width: 2.2rem;
	height: 2.2rem;
	place-items: center;
	border: 1px solid color-mix(in srgb, var(--pack-accent) 40%, transparent);
	border-radius: 0.55rem;
	background: color-mix(in srgb, var(--pack-accent) 10%, transparent);
	color: var(--pack-accent);
	font-size: 0.72rem;
	font-weight: 850;
}
.mods-list {
	display: grid;
	grid-template-columns: repeat(2, minmax(0, 1fr));
	gap: 0.45rem;
	max-height: 16.5rem;
	margin-top: 0.9rem;
	padding-right: 0.3rem;
	overflow-y: auto;
	scrollbar-color: color-mix(in srgb, var(--pack-accent) 45%, transparent) transparent;
}
.mod-chip {
	display: flex;
	align-items: center;
	gap: 0.45rem;
	min-width: 0;
	padding: 0.48rem 0.55rem;
	border: 1px solid rgba(255, 255, 255, 0.065);
	border-radius: 0.5rem;
	background: rgba(255, 255, 255, 0.025);
	color: rgba(226, 232, 240, 0.78);
	font-size: 0.7rem;
}
.mod-chip svg {
	width: 0.82rem;
	flex: none;
	color: var(--pack-accent);
}
.instance-actions {
	margin-top: 0.9rem;
	padding-top: 0.9rem;
	border-top: 1px solid rgba(255, 255, 255, 0.06);
}
.secondary-actions button {
	border-color: rgba(255, 255, 255, 0.09);
	background: rgba(255, 255, 255, 0.045);
	font-size: 0.72rem;
}
.spin {
	animation: spin 0.8s linear infinite;
}
@keyframes spin {
	to {
		transform: rotate(360deg);
	}
}
@media (max-width: 980px) {
	.pack-hero {
		grid-template-columns: 6rem 1fr;
	}
	.pack-hero > img {
		width: 6rem;
		height: 6rem;
	}
	.primary-actions,
	.pack-hero > .primary-button {
		grid-column: 1 / -1;
	}
	.dossier-grid {
		grid-template-columns: 1fr;
	}
	.author-contact {
		align-items: flex-start;
		flex-direction: column;
	}
}
@media (prefers-reduced-motion: reduce) {
	.spin {
		animation: none;
	}
}
</style>
