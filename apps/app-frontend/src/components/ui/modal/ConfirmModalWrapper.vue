<script setup lang="ts">
import { ConfirmModal } from '@modrinth/ui'
import { ref } from 'vue'

// import { hide_ads_window, show_ads_window } from '@/helpers/ads.js'
import { useTheming } from '@/store/theme.ts'

const themeStore = useTheming()

defineProps({
	confirmationText: {
		type: String,
		default: '',
	},
	hasToType: {
		type: Boolean,
		default: false,
	},
	title: {
		type: String,
		default: 'Подтверждение',
		required: true,
	},
	description: {
		type: String,
		default: 'Подтвердите действие.',
		required: true,
	},
	proceedIcon: {
		type: Object,
		default: undefined,
	},
	proceedLabel: {
		type: String,
		default: 'Продолжить',
	},
	danger: {
		type: Boolean,
		default: true,
	},
	// showAdOnClose: {
	// 	type: Boolean,
	// 	default: true,
	// },
	markdown: {
		type: Boolean,
		default: true,
	},
})

const emit = defineEmits(['proceed'])
const modal = ref<{ show: () => void; hide: () => void } | null>(null)

defineExpose({
	show: () => {
		// hide_ads_window()
		modal.value?.show()
	},
	hide: () => {
		modal.value?.hide()
	},
})

// function onModalHide() {
// 	if (props.showAdOnClose) {
// 		show_ads_window()
// 	}
// }

function proceed() {
	emit('proceed')
}
</script>

<template>
	<ConfirmModal
		ref="modal"
		:confirmation-text="confirmationText"
		:has-to-type="hasToType"
		:title="title"
		:description="description"
		:proceed-icon="proceedIcon"
		:proceed-label="proceedLabel"
		:noblur="!themeStore.advancedRendering"
		:danger="danger"
		:markdown="markdown"
		@proceed="proceed"
	/>
</template>
