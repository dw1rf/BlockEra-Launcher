<script setup lang="ts">
import { type Component, computed, nextTick, ref, useTemplateRef } from 'vue'

import { type MessageDescriptor, useVIntl } from '../../composables/i18n'
import { useScrollIndicator } from '../../composables/scroll-indicator'

const { formatMessage } = useVIntl()

export type Tab<Props> = {
	name: MessageDescriptor
	icon: Component
	content: Component<Props>
	props?: Props
	badge?: MessageDescriptor
}

const props = defineProps<{
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	tabs: Tab<any>[]
}>()

const selectedTab = ref(0)
const tabButtons = useTemplateRef<HTMLButtonElement[]>('tabButtons')
const tabsLength = computed(() => props.tabs.length)

const scrollContainer = ref<HTMLElement | null>(null)
const { showTopFade, showBottomFade, checkScrollState, forceCheck } =
	useScrollIndicator(scrollContainer)

function setTab(index: number) {
	if (index < 0 || index >= tabsLength.value) return
	selectedTab.value = index
	nextTick(() => forceCheck())
}

function handleTabKeydown(event: KeyboardEvent, index: number) {
	if (!['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'].includes(event.key))
		return
	event.preventDefault()
	let nextIndex = index
	if (event.key === 'Home') nextIndex = 0
	else if (event.key === 'End') nextIndex = tabsLength.value - 1
	else if (event.key === 'ArrowLeft' || event.key === 'ArrowUp')
		nextIndex = (index - 1 + tabsLength.value) % tabsLength.value
	else nextIndex = (index + 1) % tabsLength.value
	setTab(nextIndex)
	nextTick(() => tabButtons.value?.[nextIndex]?.focus())
}

defineExpose({ selectedTab, setTab })
</script>
<template>
	<div class="tabbed-modal-layout">
		<div
			class="tabbed-modal-tabs"
			role="tablist"
			aria-orientation="vertical"
			aria-label="Разделы настроек"
		>
			<button
				v-for="(tab, index) in tabs"
				:id="`settings-tab-${index}`"
				:key="index"
				ref="tabButtons"
				type="button"
				role="tab"
				:aria-controls="`settings-panel-${index}`"
				:aria-selected="selectedTab === index"
				:tabindex="selectedTab === index ? 0 : -1"
				:class="`flex gap-2 items-center text-left rounded-xl px-4 py-2 border-none text-nowrap font-semibold cursor-pointer active:scale-[0.97] transition-all ${selectedTab === index ? 'bg-button-bgSelected text-button-textSelected' : 'bg-transparent text-button-text hover:bg-button-bg hover:text-contrast'}`"
				@click="() => setTab(index)"
				@keydown="handleTabKeydown($event, index)"
			>
				<component :is="tab.icon" class="w-4 h-4" />
				<span>{{ formatMessage(tab.name) }}</span>
				<span
					v-if="tab.badge"
					class="rounded-full px-1.5 py-0.5 text-xs font-bold bg-brand-highlight text-brand-green"
				>
					{{ formatMessage(tab.badge) }}
				</span>
			</button>

			<slot name="footer" />
		</div>
		<div class="relative">
			<Transition
				enter-active-class="transition-all duration-200 ease-out"
				enter-from-class="opacity-0 max-h-0"
				enter-to-class="opacity-100 max-h-24"
				leave-active-class="transition-all duration-200 ease-in"
				leave-from-class="opacity-100 max-h-24"
				leave-to-class="opacity-0 max-h-0"
			>
				<div
					v-if="showTopFade"
					class="pointer-events-none absolute left-0 right-0 top-0 z-10 h-24 bg-gradient-to-b from-bg-raised to-transparent"
				/>
			</Transition>

			<div
				:id="`settings-panel-${selectedTab}`"
				ref="scrollContainer"
				class="tabbed-modal-content"
				role="tabpanel"
				:aria-labelledby="`settings-tab-${selectedTab}`"
				tabindex="0"
				@scroll="checkScrollState"
			>
				<component :is="tabs[selectedTab].content" v-bind="tabs[selectedTab].props ?? {}" />
			</div>

			<Transition
				enter-active-class="transition-all duration-200 ease-out"
				enter-from-class="opacity-0 max-h-0"
				enter-to-class="opacity-100 max-h-24"
				leave-active-class="transition-all duration-200 ease-in"
				leave-from-class="opacity-100 max-h-24"
				leave-to-class="opacity-0 max-h-0"
			>
				<div
					v-if="showBottomFade"
					class="pointer-events-none absolute bottom-0 left-0 right-0 z-10 h-24 bg-gradient-to-t from-bg-raised to-transparent"
				/>
			</Transition>
		</div>
	</div>
</template>

<style scoped>
.tabbed-modal-layout {
	display: grid;
	grid-template-columns: minmax(12.5rem, auto) minmax(0, 1fr);
	width: clamp(42.5rem, 75vw, 62.5rem);
	max-width: calc(100vw - 3rem);
	max-height: min(80vh, 47.5rem);
}

.tabbed-modal-tabs {
	display: flex;
	min-width: 12.5rem;
	flex-direction: column;
	gap: 0.25rem;
	padding-right: 1rem;
	border: 0;
	border-right: 1px solid var(--color-divider);
}

.tabbed-modal-content {
	width: 100%;
	height: min(80vh, 47.5rem);
	max-height: calc(100vh - 10rem);
	overflow-y: auto;
	padding-inline: 1rem;
}

@media (max-width: 760px) {
	.tabbed-modal-layout {
		grid-template-columns: minmax(0, 1fr);
		width: calc(100vw - 2rem);
	}

	.tabbed-modal-tabs {
		min-width: 0;
		flex-direction: row;
		overflow-x: auto;
		padding: 0 0 0.75rem;
		border-right: 0;
		border-bottom: 1px solid var(--color-divider);
	}

	.tabbed-modal-content {
		height: min(70vh, 42rem);
		padding: 0.75rem 0.25rem 0;
	}
}
</style>
