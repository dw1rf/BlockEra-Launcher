<script setup lang="ts">
import { nextTick, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import CrashDiagnosisCard from './CrashDiagnosisCard.vue'
import ModalWrapper from './modal/ModalWrapper.vue'
import { diagnoseCrash, type CrashDiagnosis, type ProcessExit } from '@/helpers/crash-diagnostics'
import { process_listener } from '@/helpers/events.js'
import { get_output_by_filename } from '@/helpers/logs.js'
import { get } from '@/helpers/profile.js'
type Crash = { uuid: string; path: string; name: string; diagnosis: CrashDiagnosis; log: string }
type ProcessEvent = { event: string; uuid: string; profile_path_id: string; exit?: ProcessExit }
const router = useRouter()
const modal = ref<InstanceType<typeof ModalWrapper> | null>(null)
const active = ref<Crash | null>(null)
const pending: Crash[] = []
const seen = new Set<string>()
let disposed = false
let collecting = Promise.resolve()
let unlisten: (() => void) | undefined
async function showNext() {
	if (disposed || active.value || !pending.length) return
	active.value = pending.shift()!
	await nextTick()
	if (!disposed) modal.value?.show()
}
async function dismissed() {
	active.value = null
	await nextTick()
	await showNext()
}
async function collect(event: ProcessEvent) {
	let log = ''
	let name = event.profile_path_id
	try {
		log = await get_output_by_filename(event.profile_path_id, 'InfoLog', 'launcher_log.txt')
		const session = log.match(/^# Session: (.+)$/m)?.[1]?.trim()
		if (session && session !== event.uuid) log = 'Журнал этого запуска уже заменён новым запуском.'
	} catch {
		log = 'Не удалось прочитать журнал завершившегося запуска.'
	}
	try {
		name = (await get(event.profile_path_id))?.name ?? name
	} catch {
		/* profile may have been removed */
	}
	const diagnosis = diagnoseCrash(log, event.exit)
	if (!disposed && diagnosis) {
		pending.push({ uuid: event.uuid, path: event.profile_path_id, name, diagnosis, log })
		await showNext()
	}
}
process_listener((event: ProcessEvent) => {
	if (
		disposed ||
		event.event !== 'finished' ||
		!event.exit ||
		event.exit.success ||
		event.exit.user_requested_stop ||
		seen.has(event.uuid)
	)
		return
	seen.add(event.uuid)
	if (seen.size > 100) seen.delete(seen.values().next().value!)
	collecting = collecting
		.then(() => collect(event))
		.catch((error) => console.error('Crash diagnostics:', error))
})
	.then((stop: () => void) => {
		if (disposed) stop()
		else unlisten = stop
	})
	.catch((error: unknown) => console.error('Crash diagnostics listener:', error))
function openLogs() {
	if (active.value) void router.push(`/instance/${encodeURIComponent(active.value.path)}/logs`)
	modal.value?.hide()
}
onUnmounted(() => {
	disposed = true
	unlisten?.()
	pending.length = 0
})
</script>
<template>
	<ModalWrapper
		ref="modal"
		:header="`Завершение игры · ${active?.name ?? ''}`"
		:on-hide="dismissed"
	>
		<div v-if="active" class="crash-dialog">
			<CrashDiagnosisCard
				:diagnosis="active.diagnosis"
				:profile="active.name"
				:log="active.log"
			/><button type="button" @click="openLogs">Открыть журнал</button>
		</div>
	</ModalWrapper>
</template>
<style scoped>
.crash-dialog {
	width: min(38rem, 80vw);
}
button {
	margin-top: 1rem;
	padding: 0.65rem 1rem;
	border-radius: 0.5rem;
	background: var(--color-button-bg);
}
button:focus-visible {
	outline: 2px solid var(--color-brand);
	outline-offset: 3px;
}
</style>
