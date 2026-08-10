<script setup lang="ts">
import { GameIcon, LeftArrowIcon } from '@modrinth/assets'
import { ButtonStyled } from '@modrinth/ui'
import { formatCategory } from '@modrinth/utils'
import { useRouter } from 'vue-router'

import { navigateHistory } from '@/helpers/navigation'

type Instance = {
	game_version: string
	loader: string
	path: string
	install_stage: string
	name: string
}

const props = defineProps<{
	instance: Instance
}>()

const router = useRouter()

function backToInstance() {
	navigateHistory(router, 'back', `/instance/${encodeURIComponent(props.instance.path)}/content`)
}
</script>

<template>
	<div class="flex justify-between items-center border-0 border-b border-solid border-divider pb-4">
		<router-link
			:to="`/instance/${encodeURIComponent(instance.path)}`"
			tabindex="-1"
			class="flex flex-col gap-4 text-primary"
		>
			<span class="flex items-center gap-2">
				<span class="flex flex-col gap-2">
					<span class="font-extrabold bold text-contrast">
						{{ instance.name }}
					</span>
					<span class="text-secondary flex items-center gap-2 font-semibold">
						<GameIcon class="h-5 w-5 text-secondary" />
						{{ formatCategory(instance.loader) }} {{ instance.game_version }}
					</span>
				</span>
			</span>
		</router-link>
		<ButtonStyled>
			<button type="button" @click="backToInstance"><LeftArrowIcon /> Back to instance</button>
		</ButtonStyled>
	</div>
</template>

<style scoped lang="scss"></style>
