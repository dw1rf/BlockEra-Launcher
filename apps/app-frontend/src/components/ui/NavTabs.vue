<template>
	<nav class="blockera-nav-tabs experimental-styles-within" aria-label="Вкладки раздела">
		<RouterLink
			v-for="(link, index) in filteredLinks"
			v-show="link.shown === undefined ? true : link.shown"
			:key="index"
			:to="query ? (link.href ? `?${query}=${link.href}` : '?') : link.href"
			class="blockera-nav-tab"
			:class="{ active: activeIndex === index, subpage: activeIndex === index && subpageSelected }"
			:aria-current="activeIndex === index ? 'page' : undefined"
		>
			<component :is="link.icon" v-if="link.icon" class="size-5" />
			<span class="text-nowrap">{{ link.label }}</span>
		</RouterLink>
	</nav>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { RouteLocationRaw } from 'vue-router'
import { RouterLink, useRoute } from 'vue-router'

const route = useRoute()

interface Tab {
	label: string
	href: string | RouteLocationRaw
	shown?: boolean
	icon?: unknown
	subpages?: string[]
}

const props = defineProps<{
	links: Tab[]
	query?: string
}>()

const activeIndex = ref(-1)
const subpageSelected = ref(false)

const filteredLinks = computed(() =>
	props.links.filter((x) => (x.shown === undefined ? true : x.shown)),
)

function pickLink() {
	let index = -1
	subpageSelected.value = false
	for (let i = filteredLinks.value.length - 1; i >= 0; i--) {
		const link = filteredLinks.value[i]

		if (route.path === (typeof link.href === 'string' ? link.href : link.href.path)) {
			index = i
			break
		} else if (link.subpages && link.subpages.some((subpage) => route.path.includes(subpage))) {
			index = i
			subpageSelected.value = true
			break
		}
	}
	activeIndex.value = index
}
watch(() => route.fullPath, pickLink, { immediate: true })
</script>
<style scoped lang="scss">
.blockera-nav-tabs {
	display: flex;
	width: fit-content;
	max-width: 100%;
	gap: 0.25rem;
	padding: 0.3rem;
	overflow-x: auto;
	border: 1px solid var(--blockera-glass-border, rgba(255, 255, 255, 0.1));
	border-radius: var(--blockera-radius-pill, 999px);
	background: var(--blockera-glass-surface, rgba(23, 27, 39, 0.72));
	box-shadow: inset 0 1px var(--blockera-glass-highlight, rgba(255, 255, 255, 0.08));
	backdrop-filter: blur(var(--blockera-glass-blur, 18px)) saturate(125%);
}

.blockera-nav-tab {
	display: inline-flex;
	align-items: center;
	gap: 0.5rem;
	padding: 0.55rem 1rem;
	border-radius: var(--blockera-radius-pill, 999px);
	color: var(--color-base);
	font-size: 0.875rem;
	font-weight: 700;
	white-space: nowrap;
	transition:
		transform var(--blockera-motion-fast, 180ms) var(--blockera-ease, ease-out),
		color var(--blockera-motion-fast, 180ms) var(--blockera-ease, ease-out),
		background-color var(--blockera-motion-fast, 180ms) var(--blockera-ease, ease-out),
		box-shadow var(--blockera-motion-fast, 180ms) var(--blockera-ease, ease-out);

	&:hover {
		color: var(--color-contrast);
		background: rgba(255, 255, 255, 0.07);
		transform: translateY(-1px);
	}

	&:active {
		transform: scale(0.98);
	}

	&.active {
		color: #f3e8ff;
		background: var(--blockera-glass-accent, rgba(126, 44, 220, 0.28));
		box-shadow:
			inset 0 1px rgba(255, 255, 255, 0.11),
			0 7px 18px rgba(73, 23, 120, 0.18);
	}

	&.subpage {
		background: rgba(255, 255, 255, 0.08);
	}
}
</style>
