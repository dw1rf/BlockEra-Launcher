<script setup lang="ts">
import { CheckIcon, CoffeeIcon, GameIcon, RightArrowIcon, UserIcon } from '@modrinth/assets'
import { Button } from '@modrinth/ui'
import { computed, onMounted, ref } from 'vue'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import { useAppSettings } from '@/composables/use-app-settings'

const emit = defineEmits<{
	accounts: []
	create: []
	java: []
	close: []
}>()

const modal = ref<InstanceType<typeof ModalWrapper>>()
const currentStep = ref(0)
const completed = ref(new Set<number>())
defineExpose({ show })
onMounted(show)
const { saveKey } = await useAppSettings()

const steps = [
	{
		title: 'Добавьте аккаунт',
		description: 'Войдите через Microsoft или Ely.by либо создайте офлайн-профиль.',
		action: 'Открыть аккаунты',
		icon: UserIcon,
		event: 'accounts' as const,
	},
	{
		title: 'Выберите первую сборку',
		description: 'Создайте свою, импортируйте .mrpack или выберите готовую сборку из каталога.',
		action: 'Создать или импортировать',
		icon: GameIcon,
		event: 'create' as const,
	},
	{
		title: 'Проверьте Java и запустите игру',
		description:
			'BlockEra подберёт Java автоматически и покажет диагностику перед первым запуском.',
		action: 'Открыть настройки Java',
		icon: CoffeeIcon,
		event: 'java' as const,
	},
]

const progress = computed(() => Math.round((completed.value.size / steps.length) * 100))

function runStep() {
	const step = steps[currentStep.value]
	switch (step.event) {
		case 'accounts':
			emit('accounts')
			break
		case 'create':
			emit('create')
			break
		case 'java':
			emit('java')
			break
	}
	completed.value.add(currentStep.value)
	completed.value = new Set(completed.value)
	if (currentStep.value < steps.length - 1) currentStep.value += 1
}

function skipStep() {
	completed.value.add(currentStep.value)
	completed.value = new Set(completed.value)
	if (currentStep.value < steps.length - 1) currentStep.value += 1
}

async function finish() {
	await saveKey('onboarded', true)
	modal.value?.hide()
	emit('close')
}

function show() {
	currentStep.value = 0
	completed.value = new Set()
	modal.value?.show()
}

</script>

<template>
	<ModalWrapper
		ref="modal"
		class="onboarding-modal"
		header="Добро пожаловать в BlockEra"
		:on-hide="() => emit('close')"
	>
		<div class="onboarding-shell">
			<header>
				<span>ПЕРВЫЙ ЗАПУСК</span>
				<h2>Три шага до первого мира</h2>
				<p>Можно пропустить любой шаг и вернуться к мастеру из раздела «О BlockEra».</p>
				<div class="onboarding-progress" :aria-label="`Выполнено ${progress}%`">
					<i :style="{ width: `${progress}%` }" />
				</div>
			</header>

			<ol class="onboarding-steps">
				<li
					v-for="(step, index) in steps"
					:key="step.title"
					:class="{ active: currentStep === index, complete: completed.has(index) }"
				>
					<button type="button" @click="currentStep = index">
						<component :is="completed.has(index) ? CheckIcon : step.icon" aria-hidden="true" />
						<span
							><strong>{{ index + 1 }}. {{ step.title }}</strong
							><small>{{ step.description }}</small></span
						>
					</button>
				</li>
			</ol>

			<footer>
				<Button @click="skipStep">Пропустить шаг</Button>
				<Button v-if="completed.size < steps.length" color="primary" @click="runStep">
					{{ steps[currentStep].action }} <RightArrowIcon />
				</Button>
				<Button v-else color="primary" @click="finish"><CheckIcon /> Завершить</Button>
			</footer>
		</div>
	</ModalWrapper>
</template>

<style scoped lang="scss">
.onboarding-shell {
	width: min(46rem, calc(100vw - 4rem));
	display: grid;
	gap: 1.25rem;
	padding: 1.25rem;
}
.onboarding-shell header > span {
	color: var(--blockera-accent, #c084fc);
	font-size: 0.7rem;
	font-weight: 800;
	letter-spacing: 0.14em;
}
.onboarding-shell h2 {
	margin: 0.35rem 0;
}
.onboarding-shell p {
	margin: 0;
	color: var(--color-secondary);
}
.onboarding-progress {
	height: 0.35rem;
	margin-top: 1rem;
	overflow: hidden;
	border-radius: 999px;
	background: var(--color-button-bg);
}
.onboarding-progress i {
	display: block;
	height: 100%;
	background: var(--blockera-accent, #a855f7);
	transition: width 180ms ease;
}
.onboarding-steps {
	display: grid;
	gap: 0.65rem;
	margin: 0;
	padding: 0;
	list-style: none;
}
.onboarding-steps button {
	width: 100%;
	min-height: 4.5rem;
	display: flex;
	align-items: center;
	gap: 0.85rem;
	padding: 0.85rem;
	color: var(--color-base);
	text-align: left;
	border: 1px solid var(--color-button-bg);
	border-radius: var(--radius-lg);
	background: var(--color-raised-bg);
	cursor: pointer;
}
.onboarding-steps li.active button {
	border-color: var(--blockera-focus, #e879f9);
	box-shadow: 0 0 0 1px var(--blockera-focus, #e879f9);
}
.onboarding-steps li.complete button {
	border-color: var(--blockera-success, #62d49b);
}
.onboarding-steps svg {
	width: 1.5rem;
	color: var(--blockera-accent, #c084fc);
}
.onboarding-steps span {
	display: grid;
	gap: 0.25rem;
}
.onboarding-steps small {
	color: var(--color-secondary);
}
.onboarding-shell footer {
	display: flex;
	justify-content: flex-end;
	gap: 0.75rem;
}
</style>
