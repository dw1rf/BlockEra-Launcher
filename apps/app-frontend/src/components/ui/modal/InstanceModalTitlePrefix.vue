<script setup lang="ts">
import { ChevronRightIcon } from '@modrinth/assets'

import type { GameInstance } from '@/helpers/types'

defineProps<{
	instance: GameInstance
}>()
</script>
<template>
	<span class="flex items-center gap-2 text-lg font-semibold text-primary">
		{{ instance.name }} <ChevronRightIcon />
	</span>
</template>
