<script setup lang="ts">
import { LogInIcon, SpinnerIcon } from '@modrinth/assets'
import { ref } from 'vue'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'

defineProps({
	onFlowCancel: {
		type: Function,
		default() {
			return async () => {}
		},
	},
})

const modal = ref()

function show() {
	modal.value.show()
}

function hide() {
	modal.value.hide()
}

defineExpose({ show, hide })
</script>
<template>
	<ModalWrapper ref="modal" @hide="onFlowCancel">
		<template #title>
			<span class="items-center gap-2 text-lg font-extrabold text-contrast">
				<LogInIcon /> {{ $t('blockera.ui.ada2e9e96fa9') }}
			</span>
		</template>

		<div class="flex justify-center gap-2">
			<SpinnerIcon class="w-12 h-12 animate-spin" />
		</div>
		<p class="text-sm text-secondary">
			{{ $t('blockera.ui.2f9dc55094a8') }}
		</p>
	</ModalWrapper>
</template>
