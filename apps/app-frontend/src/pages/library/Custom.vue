<script setup>
import GridDisplay from '@/components/GridDisplay.vue'

defineProps({
	instances: {
		type: Array,
		required: true,
	},
})
</script>
<template>
	<GridDisplay
		v-if="instances.length > 0"
		:label="$t('blockera.ui.86d67413e7f1')"
		:instances="instances.filter((i) => !i.linked_data)"
	/>
</template>
