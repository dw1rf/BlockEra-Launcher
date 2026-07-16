<script setup>
import { PlusIcon } from '@modrinth/assets'
import { Button, injectNotificationManager } from '@modrinth/ui'
import { computed, onUnmounted, ref, shallowRef } from 'vue'
import { useRoute } from 'vue-router'

import { NewInstanceImage } from '@/assets/icons'
import heroVibrant from '@/assets/launcher/hero-vibrant.jpg'
import InstanceCreationModal from '@/components/ui/InstanceCreationModal.vue'
import NavTabs from '@/components/ui/NavTabs.vue'
import { profile_listener } from '@/helpers/events.js'
import { list } from '@/helpers/profile.js'
import { useBreadcrumbs } from '@/store/breadcrumbs.js'

const { handleError } = injectNotificationManager()
const route = useRoute()
const breadcrumbs = useBreadcrumbs()

breadcrumbs.setRootContext({ name: 'Library', link: route.path })

const instances = shallowRef(await list().catch(handleError))
const downloadedCount = computed(
	() => instances.value.filter((instance) => instance.linked_data).length,
)
const customCount = computed(
	() => instances.value.filter((instance) => !instance.linked_data).length,
)

const offline = ref(!navigator.onLine)
window.addEventListener('offline', () => {
	offline.value = true
})
window.addEventListener('online', () => {
	offline.value = false
})

const unlistenProfile = await profile_listener(async () => {
	instances.value = await list().catch(handleError)
})
onUnmounted(() => {
	unlistenProfile()
})
</script>

<template>
	<main class="library-page" :style="{ '--library-hero': `url(${heroVibrant})` }">
		<section class="library-hero">
			<div>
				<p class="eyebrow">БИБЛИОТЕКА</p>
				<h1>Мои сборки</h1>
				<p class="library-subtitle">Все ваши миры, модпаки и профили запуска — в одном месте.</p>
			</div>
			<div class="library-stats" aria-label="Статистика библиотеки">
				<div>
					<strong>{{ instances.length }}</strong
					><span>всего</span>
				</div>
				<div>
					<strong>{{ downloadedCount }}</strong
					><span>готовых</span>
				</div>
				<div>
					<strong>{{ customCount }}</strong
					><span>своих</span>
				</div>
			</div>
			<Button color="primary" :disabled="offline" @click="$refs.installationModal.show()">
				<PlusIcon />
				Новая сборка
			</Button>
		</section>

		<section class="library-workspace">
			<NavTabs
				class="library-tabs"
				:links="[
					{ label: 'Все сборки', href: `/library` },
					{ label: 'Загруженные', href: `/library/downloaded` },
					{ label: 'Пользовательские', href: `/library/custom` },
					{ label: 'Рекомендуемые', href: `/library/recommended` },
					{ label: 'Shared with me', href: `/library/shared`, shown: false },
					{ label: 'Saved', href: `/library/saved`, shown: false },
				]"
			/>
			<template v-if="instances.length > 0 || route.path.startsWith('/library/recommended')">
				<RouterView :instances="instances" />
			</template>
			<div v-else class="no-instance">
				<div class="icon"><NewInstanceImage /></div>
				<h3>Сборок пока нет</h3>
				<p>Создайте первую сборку или установите готовую из каталога.</p>
				<Button color="primary" :disabled="offline" @click="$refs.installationModal.show()">
					<PlusIcon /> Создать сборку
				</Button>
			</div>
		</section>
		<InstanceCreationModal ref="installationModal" />
	</main>
</template>

<style lang="scss" scoped>
.library-page {
	min-height: 100%;
	padding: 1.5rem 2rem 2rem;
	box-sizing: border-box;
	overflow-y: auto;
	background:
		radial-gradient(circle at 82% 4%, rgba(126, 34, 206, 0.16), transparent 28rem), #060a12;
}

.library-hero {
	position: relative;
	display: grid;
	grid-template-columns: minmax(0, 1fr) auto auto;
	align-items: center;
	gap: 2rem;
	min-height: 11rem;
	padding: 1.65rem 1.8rem;
	overflow: hidden;
	border: 1px solid rgba(168, 85, 247, 0.28);
	border-radius: 1.15rem;
	background:
		linear-gradient(
			90deg,
			rgba(5, 9, 18, 0.98) 0%,
			rgba(7, 11, 20, 0.86) 50%,
			rgba(8, 10, 17, 0.36) 100%
		),
		var(--library-hero) center 57% / cover;
	box-shadow: 0 22px 60px rgba(0, 0, 0, 0.3);
}

.eyebrow {
	margin: 0 0 0.55rem;
	color: #c084fc;
	font-size: 0.72rem;
	font-weight: 750;
	letter-spacing: 0.14em;
}

.library-hero h1 {
	margin: 0;
	color: #fff;
	font-size: clamp(2rem, 3vw, 3rem);
	line-height: 1;
}

.library-subtitle {
	max-width: 34rem;
	margin: 0.75rem 0 0;
	color: rgba(226, 232, 240, 0.72);
	font-size: 1rem;
}

.library-stats {
	display: flex;
	gap: 0.55rem;
}

.library-stats div {
	display: flex;
	flex-direction: column;
	align-items: center;
	min-width: 4.7rem;
	padding: 0.75rem 0.9rem;
	border: 1px solid rgba(255, 255, 255, 0.08);
	border-radius: 0.85rem;
	background: rgba(5, 8, 15, 0.66);
	backdrop-filter: blur(14px);
}

.library-stats strong {
	color: #fff;
	font-size: 1.3rem;
}
.library-stats span {
	color: rgba(226, 232, 240, 0.58);
	font-size: 0.72rem;
}

.library-workspace {
	display: flex;
	flex-direction: column;
	gap: 1rem;
	margin-top: 1rem;
	padding: 1.1rem;
	border: 1px solid rgba(255, 255, 255, 0.08);
	border-radius: 1.15rem;
	background: rgba(12, 17, 27, 0.86);
	box-shadow: 0 20px 48px rgba(0, 0, 0, 0.18);
}

.library-tabs {
	align-self: flex-start;
}

:deep(.library-workspace > .flex.gap-2) {
	padding: 0.25rem;
	border-radius: 0.9rem;
	background: rgba(4, 7, 13, 0.48);
}

:deep(.library-workspace .iconified-input input),
:deep(.library-workspace button) {
	transition:
		transform 180ms cubic-bezier(0.22, 1, 0.36, 1),
		border-color 180ms cubic-bezier(0.22, 1, 0.36, 1),
		background-color 180ms cubic-bezier(0.22, 1, 0.36, 1);
}

:deep(.library-workspace .instances) {
	grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
	gap: 0.8rem;
}

:deep(.library-workspace .instances > div > div) {
	min-height: 4.6rem;
	border: 1px solid rgba(255, 255, 255, 0.07);
	background: linear-gradient(135deg, rgba(22, 29, 43, 0.96), rgba(13, 18, 28, 0.96)) !important;
	box-shadow: none;
}

:deep(.library-workspace .instances > div > div:hover) {
	transform: translateY(-2px);
	border-color: rgba(168, 85, 247, 0.34);
}

.no-instance {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	height: 100%;
	gap: var(--gap-md);
	min-height: 22rem;
	color: var(--color-secondary);

	p,
	h3 {
		margin: 0;
	}

	.icon {
		svg {
			width: 10rem;
			height: 10rem;
		}
	}
}

@media (max-width: 980px) {
	.library-hero {
		grid-template-columns: 1fr auto;
	}
	.library-stats {
		display: none;
	}
}

@media (prefers-reduced-motion: no-preference) {
	.library-hero,
	.library-workspace {
		animation: library-enter 350ms cubic-bezier(0.22, 1, 0.36, 1) both;
	}
	.library-workspace {
		animation-delay: 70ms;
	}
}

@keyframes library-enter {
	from {
		opacity: 0;
		transform: translateY(8px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}
</style>
