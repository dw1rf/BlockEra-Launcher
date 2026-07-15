<script setup>
import { injectNotificationManager } from '@modrinth/ui'
import { ref } from 'vue'

import JavaSelector from '@/components/ui/JavaSelector.vue'
import { get_java_versions, set_java_version } from '@/helpers/jre'

const { handleError } = injectNotificationManager()

const javaVersions = ref(await get_java_versions().catch(handleError))
async function updateJavaVersion(version) {
	if (version?.path === '') {
		version.path = undefined
	}

	if (version?.path) {
		version.path = version.path.replace('java.exe', 'javaw.exe')
	}

	await set_java_version(version).catch(handleError)
}
</script>
<template>
	<div class="launcher-settings-page">
		<header class="settings-page-header">
			<p>СРЕДА ЗАПУСКА</p>
			<h2>Установки Java</h2>
			<span>BlockEra Launcher автоматически подбирает подходящую Java для каждой версии Minecraft.</span>
		</header>
		<section class="settings-section java-stack">
			<div v-for="javaVersion in [25, 21, 17, 8]" :key="`java-${javaVersion}`" class="java-version-row">
				<div><strong>Java {{ javaVersion }}</strong><span>{{ javaVersion >= 21 ? 'Новые версии Minecraft' : javaVersion === 17 ? 'Minecraft 1.18–1.20.4' : 'Старые версии Minecraft' }}</span></div>
				<JavaSelector :id="'java-selector-' + javaVersion" v-model="javaVersions[javaVersion]" :version="javaVersion" @update:model-value="updateJavaVersion" />
			</div>
		</section>
	</div>
</template>
