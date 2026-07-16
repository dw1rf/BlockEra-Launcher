<script setup lang="ts">
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
	</div>
</template>
