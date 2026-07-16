<script setup lang="ts">
import { BlockEraLogo } from '@modrinth/assets'
import { getVersion } from '@tauri-apps/api/app'

const version = await getVersion()
</script>

<template>
	<div class="launcher-settings-page about-blockera">
		<header class="settings-page-header">
			<p>О ПРОЕКТЕ</p>
			<h2>BlockEra Launcher</h2>
			<span>Версия {{ version }} · Бесплатный лаунчер для ваших миров и сборок.</span>
		</header>

		<section class="settings-section support-card">
			<BlockEraLogo aria-hidden="true" />
			<div>
				<h3>Поддержать развитие BlockEra</h3>
				<p>Добровольная поддержка помогает развивать лаунчер. Все игровые функции остаются бесплатными.</p>
			</div>
			<a href="https://boosty.to/blockera/donate" target="_blank" rel="noopener noreferrer">
				Поддержать на Boosty
			</a>
		</section>
	</div>
</template>

<style scoped lang="scss">
.support-card {
	display: grid;
	grid-template-columns: auto minmax(0, 1fr) auto;
	align-items: center;
	gap: 1rem;
}

.support-card > svg {
	width: 2.75rem;
	height: 2.75rem;
	color: #c084fc;
}

.support-card a {
	padding: 0.72rem 0.9rem;
	border: 1px solid rgba(216, 180, 254, 0.34);
	border-radius: 0.7rem;
	color: white;
	background: linear-gradient(135deg, #9139ed, #6721c8);
	font-size: 0.78rem;
	font-weight: 800;
	text-decoration: none;
	white-space: nowrap;
}

@media (max-width: 720px) {
	.support-card { grid-template-columns: auto minmax(0, 1fr); }
	.support-card a { grid-column: 1 / -1; text-align: center; }
}
</style>
