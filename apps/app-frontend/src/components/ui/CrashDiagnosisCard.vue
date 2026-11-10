<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { crashReport, type CrashDiagnosis } from '@/helpers/crash-diagnostics'
const props = defineProps<{ diagnosis: CrashDiagnosis; profile: string; log: string }>()
const report = computed(() => crashReport(props.diagnosis, props.profile, props.log))
const copied = ref(false)
const copyError = ref('')
watch(report, () => {
	copied.value = false
	copyError.value = ''
})
async function copy() {
	try {
		await navigator.clipboard.writeText(report.value)
		copied.value = true
	} catch {
		copyError.value = 'Не удалось скопировать. Выделите текст отчёта ниже.'
	}
}
</script>
<template>
	<section class="crash-diagnosis" aria-label="Диагностика завершения игры">
		<h3>{{ diagnosis.title }}</h3>
		<p>
			<strong>Уверенность: {{ diagnosis.confidence.toLowerCase() }}</strong>
		</p>
		<p>{{ diagnosis.explanation }}</p>
		<p>{{ diagnosis.suggestion }}</p>
		<p v-if="diagnosis.exitCode !== null">Код завершения: {{ diagnosis.exitCode }}</p>
		<template v-if="diagnosis.suspectedMods.length"
			><p>
				Моды, упомянутые рядом со сбоем: <strong>{{ diagnosis.suspectedMods.join(', ') }}</strong>
			</p>
			<p>Это кандидаты для проверки, а не доказанные виновники.</p></template
		>
		<p v-else>Подозреваемые моды не определены.</p>
		<details v-if="diagnosis.evidence.length">
			<summary>На чём основан вывод</summary>
			<pre>{{ diagnosis.evidence.join('\n') }}</pre>
		</details>
		<button type="button" @click="copy">{{ copied ? 'Скопировано' : 'Копировать отчёт' }}</button>
		<p v-if="copyError" role="alert">{{ copyError }}</p>
		<details>
			<summary>Посмотреть отчёт</summary>
			<pre>{{ report }}</pre>
		</details>
		<small
			>Типичные токены, пользовательские пути и адреса скрыты. Проверьте отчёт перед
			отправкой.</small
		>
	</section>
</template>
<style scoped>
.crash-diagnosis {
	padding: 1rem;
	border: 1px solid var(--color-divider);
	border-radius: 1rem;
	background: var(--color-bg);
}
h3 {
	margin-top: 0;
}
pre {
	white-space: pre-wrap;
	overflow-wrap: anywhere;
	max-height: 16rem;
	overflow: auto;
}
button {
	margin: 0.75rem 0;
	padding: 0.65rem 1rem;
	border-radius: 0.5rem;
	background: var(--color-button-bg);
	font-weight: 600;
}
button:focus-visible,
summary:focus-visible {
	outline: 2px solid var(--color-brand);
	outline-offset: 3px;
}
small {
	display: block;
	margin-top: 0.75rem;
}
</style>
