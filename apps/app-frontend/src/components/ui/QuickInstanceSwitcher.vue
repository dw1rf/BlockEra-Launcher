<script setup>
import { SpinnerIcon } from '@modrinth/assets'
import { injectNotificationManager } from '@modrinth/ui'
import dayjs from 'dayjs'
import { onUnmounted, ref } from 'vue'

import NavButton from '@/components/ui/NavButton.vue'
import { profile_listener } from '@/helpers/events.js'
import { instanceBackgroundFor } from '@/helpers/instance-backgrounds'
import { list } from '@/helpers/profile'

const { handleError } = injectNotificationManager()

const recentInstances = ref([])
const getInstances = async () => {
	const profiles = await list().catch(handleError)

	recentInstances.value = profiles
		.sort((a, b) => {
			const dateACreated = dayjs(a.created)
			const dateAPlayed = a.last_played ? dayjs(a.last_played) : dayjs(0)

			const dateBCreated = dayjs(b.created)
			const dateBPlayed = b.last_played ? dayjs(b.last_played) : dayjs(0)

			const dateA = dateACreated.isAfter(dateAPlayed) ? dateACreated : dateAPlayed
			const dateB = dateBCreated.isAfter(dateBPlayed) ? dateBCreated : dateBPlayed

			if (dateA.isSame(dateB)) {
				return a.name.localeCompare(b.name)
			}

			return dateB - dateA
		})
		.slice(0, 3)
}

await getInstances()

const unlistenProfile = await profile_listener(async (event) => {
	if (event.event !== 'synced') {
		await getInstances()
	}
})

onUnmounted(() => {
	unlistenProfile()
})
</script>

<template>
	<NavButton
		v-for="instance in recentInstances"
		:key="instance.id"
		v-tooltip.right="instance.name"
		:to="`/instance/${encodeURIComponent(instance.path)}`"
		class="relative"
	>
		<div
			class="quick-instance-background"
			:class="{ 'is-installing': instance.install_stage !== 'installed' }"
			:style="{ backgroundImage: `url(${instanceBackgroundFor(instance.path)})` }"
		/>
		<div
			v-if="instance.install_stage !== 'installed'"
			class="absolute inset-0 flex items-center justify-center z-10"
		>
			<SpinnerIcon class="animate-spin w-4 h-4" />
		</div>
	</NavButton>
	<div v-if="recentInstances.length > 0" class="h-px w-6 mx-auto my-2 bg-divider"></div>
</template>

<style scoped lang="scss">
.quick-instance-background {
	width: 1.75rem;
	height: 1.75rem;
	border: 1px solid var(--blockera-glass-border);
	border-radius: 0.55rem;
	background-color: var(--blockera-glass-surface);
	background-position: center;
	background-size: cover;
	box-shadow: inset 0 1px var(--blockera-glass-highlight);
	transition:
		transform var(--blockera-motion-fast) var(--blockera-ease),
		opacity var(--blockera-motion-fast) var(--blockera-ease);
}

.group:hover .quick-instance-background {
	transform: translateY(-1px);
	opacity: 0.82;
}

.quick-instance-background.is-installing {
	transform: scale(0.85);
	opacity: 0.3;
}
</style>
