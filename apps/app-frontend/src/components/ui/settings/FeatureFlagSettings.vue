<script setup lang="ts">
import { Toggle } from '@modrinth/ui'
import { ref } from 'vue'

import { useAppSettings } from '@/composables/use-app-settings'
import { useTheming } from '@/store/state'
import { DEFAULT_FEATURE_FLAGS, type FeatureFlag } from '@/store/theme.ts'

const themeStore = useTheming()

const { settings, saveKey } = await useAppSettings()
const options = ref<FeatureFlag[]>(Object.keys(DEFAULT_FEATURE_FLAGS) as FeatureFlag[])

function setFeatureFlag(key: FeatureFlag, value: boolean) {
	themeStore.featureFlags[key] = value
	settings.value.feature_flags[key] = value
	void saveKey('feature_flags', { ...settings.value.feature_flags })
}
</script>
<template>
	<div v-for="option in options" :key="option" class="mt-4 flex items-center justify-between">
		<div>
			<h2 class="m-0 text-lg font-extrabold text-contrast capitalize">
				{{ option.replace(/_/g, ' ') }}
			</h2>
		</div>

		<Toggle
			id="advanced-rendering"
			:model-value="themeStore.getFeatureFlag(option)"
			@update:model-value="() => setFeatureFlag(option, !themeStore.getFeatureFlag(option))"
		/>
	</div>
</template>
