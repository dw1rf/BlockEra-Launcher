<script setup lang="ts">
import { Button } from '@modrinth/ui'
import { getVersion } from '@tauri-apps/api/app'

defineProps<{ onOpenOnboarding?: () => void }>()
const version = await getVersion()
</script>

<template>
	<div class="launcher-settings-page about-blockera">
		<header class="settings-page-header">
			<p>О ПРОЕКТЕ</p>
			<h2>BlockEra Launcher</h2>
			<span>Версия {{ version }} · Бесплатный лаунчер для ваших миров и сборок.</span>
			<div class="about-onboarding-action">
				<div>
					<strong>Мастер первого запуска</strong>
					<small>Аккаунт, первая сборка и проверка Java.</small>
				</div>
				<Button @click="onOpenOnboarding?.()">Открыть мастер</Button>
			</div>
		</header>
	</div>
</template>

<style scoped>
.about-onboarding-action {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
	margin-top: 1.25rem;
	padding: 1rem;
	border: 1px solid var(--blockera-border);
	border-radius: var(--blockera-radius-md);
	background: var(--blockera-surface);
}

.about-onboarding-action > div {
	display: flex;
	flex-direction: column;
	gap: 0.25rem;
}

.about-onboarding-action small {
	color: var(--blockera-text-secondary);
}
</style>
