<script setup lang="ts">
import { NewModal as Modal } from '@modrinth/ui'
import { ref, useTemplateRef } from 'vue'

// import { hide_ads_window, show_ads_window } from '@/helpers/ads.js'
import { useTheming } from '@/store/theme.ts'

const themeStore = useTheming()

const props = defineProps({
	header: {
		type: String,
		default: null,
	},
	hideHeader: {
		type: Boolean,
		default: false,
	},
	closable: {
		type: Boolean,
		default: true,
	},
	onHide: {
		type: Function,
		default() {
			return () => {}
		},
	},
	// showAdOnClose: {
	// 	type: Boolean,
	// 	default: true,
	// },
})
const modal = useTemplateRef('modal')
const isOpen = ref(false)

function show(e?: MouseEvent) {
	isOpen.value = true
	modal.value?.show(e)
}

function hide() {
	modal.value?.hide()
}

function toggle(e?: MouseEvent) {
	if (isOpen.value) hide()
	else show(e)
}

defineExpose({
	show,
	hide,
	toggle,
	isOpen,
})

function onModalHide() {
	isOpen.value = false
	// if (props.showAdOnClose) {
	// 	show_ads_window()
	// }
	props.onHide?.()
}
</script>

<template>
	<Modal
		ref="modal"
		:header="header"
		:noblur="!themeStore.advancedRendering"
		:closable="closable"
		:hide-header="hideHeader"
		@hide="onModalHide"
	>
		<template #title>
			<slot name="title" />
		</template>
		<slot />
	</Modal>
</template>
