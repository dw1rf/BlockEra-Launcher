<script setup lang="ts">
import { invoke } from '@tauri-apps/api/core'
import { computed, onMounted, ref } from 'vue'

type CleanupItem = {
	id: string
	kind: 'backup' | 'cache'
	name: string
	profile_name: string | null
	relative_path: string
	bytes: number
}
type Usage = {
	preview_id: string
	profiles: { path: string; name: string; bytes: number; backup_bytes: number }[]
	cache_bytes: number
	candidates: CleanupItem[]
	warnings: string[]
}
type CleanupResult = { removed_count: number; removed_bytes: number; failures: string[] }

const usage = ref<Usage | null>(null)
const loading = ref(false)
const cleaning = ref(false)
const error = ref('')
const selected = ref<string[]>([])
const reviewing = ref(false)
const result = ref<CleanupResult | null>(null)
const kind = ref('all')
const search = ref('')
const visibleCount = ref(100)
const selectedFiles = computed(
	() => usage.value?.candidates.filter((file) => selected.value.includes(file.id)) ?? [],
)
const selectedBytes = computed(() => selectedFiles.value.reduce((sum, file) => sum + file.bytes, 0))
const profileBytes = computed(
	() => usage.value?.profiles.reduce((sum, profile) => sum + profile.bytes, 0) ?? 0,
)
const backupBytes = computed(
	() => usage.value?.profiles.reduce((sum, profile) => sum + profile.backup_bytes, 0) ?? 0,
)
const filteredFiles = computed(() => {
	const query = search.value.trim().toLocaleLowerCase()
	return (
		usage.value?.candidates.filter(
			(file) =>
				(kind.value === 'all' || file.kind === kind.value) &&
				`${file.profile_name ?? ''} ${file.name}`.toLocaleLowerCase().includes(query),
		) ?? []
	)
})

function size(bytes: number) {
	if (bytes < 1024) return `${bytes} Б`
	const units = ['КиБ', 'МиБ', 'ГиБ', 'ТиБ']
	let value = bytes / 1024
	let unit = 0
	while (value >= 1024 && unit < units.length - 1) {
		value /= 1024
		unit++
	}
	return `${new Intl.NumberFormat('ru-RU', { maximumFractionDigits: 1 }).format(value)} ${units[unit]}`
}

function message(cause: unknown) {
	return cause && typeof cause === 'object' && 'message' in cause
		? String(cause.message)
		: String(cause)
}

async function scan() {
	if (loading.value || cleaning.value) return
	loading.value = true
	result.value = null
	error.value = ''
	reviewing.value = false
	selected.value = []
	try {
		usage.value = await invoke<Usage>('plugin:storage|storage_scan')
	} catch (cause) {
		usage.value = null
		error.value = message(cause)
	} finally {
		loading.value = false
	}
}

async function cleanup() {
	if (!usage.value || !reviewing.value || !selectedFiles.value.length || cleaning.value) return
	cleaning.value = true
	error.value = ''
	try {
		result.value = await invoke<CleanupResult>('plugin:storage|storage_cleanup', {
			previewId: usage.value.preview_id,
			selectedIds: [...selected.value],
		})
	} catch (cause) {
		error.value = message(cause)
	} finally {
		cleaning.value = false
		reviewing.value = false
		selected.value = []
		// The backend consumes a preview on cleanup, including partial failures.
		usage.value = null
	}
}

onMounted(() => void scan())
</script>

<template>
	<section class="settings-section storage-panel" aria-labelledby="storage-usage-title">
		<div class="settings-section-heading">
			<div>
				<h3 id="storage-usage-title">Место на диске</h3>
				<p>Размеры файлов сборок, резервных копий и кэша лаунчера.</p>
			</div>
			<button class="btn" :disabled="loading || cleaning || reviewing" @click="scan">
				{{ loading ? 'Подсчёт…' : 'Обновить размеры' }}
			</button>
		</div>
		<p v-if="loading" role="status">
			Читаем размеры файлов. Большие сборки могут потребовать больше времени.
		</p>
		<p v-if="error" class="text-red" role="alert">{{ error }}</p>
		<div v-if="result" class="storage-result" role="status">
			<p>
				Удалено файлов {{ result.removed_count }} · общий размер {{ size(result.removed_bytes) }}.
			</p>
			<ul v-if="result.failures.length">
				<li v-for="failure in result.failures" :key="failure">{{ failure }}</li>
			</ul>
			<p>Обновите размеры, чтобы получить новый список.</p>
		</div>
		<template v-if="usage">
			<div class="storage-totals">
				<div>
					<span>Файлы сборок</span><strong>{{ size(profileBytes) }}</strong>
				</div>
				<div>
					<span>Резервные копии</span><strong>{{ size(backupBytes) }}</strong>
				</div>
				<div>
					<span>Кэш на диске</span><strong>{{ size(usage.cache_bytes) }}</strong>
				</div>
			</div>
			<p class="storage-note">
				Указана сумма размеров файлов. Жёсткие ссылки, сжатие и общие библиотеки могут влиять на
				фактически занятое место.
			</p>
			<div v-if="usage.profiles.length" class="storage-table-scroll">
				<table>
					<caption class="sr-only">
						Размеры сборок и резервных копий
					</caption>
					<thead>
						<tr>
							<th scope="col">Сборка</th>
							<th scope="col">Файлы</th>
							<th scope="col">Бэкапы</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="profile in usage.profiles" :key="profile.path">
							<th scope="row">{{ profile.name }}</th>
							<td>{{ size(profile.bytes) }}</td>
							<td>{{ size(profile.backup_bytes) }}</td>
						</tr>
					</tbody>
				</table>
			</div>
			<details v-if="usage.warnings.length" class="storage-warnings">
				<summary>Часть файлов пропущена · {{ usage.warnings.length }}</summary>
				<ul>
					<li v-for="(warning, index) in usage.warnings" :key="index">{{ warning }}</li>
				</ul>
			</details>
			<div class="storage-cleanup">
				<h4>{{ reviewing ? 'Подтверждение очистки' : 'Файлы для очистки' }}</h4>
				<p>
					Выберите резервные копии и скачанные архивы сборок. Архивы кэша доступны для очистки через
					сутки после записи. Фоны, значки и инструменты Horizon учитываются в размере кэша, но
					недоступны для очистки здесь.
				</p>
				<template v-if="reviewing">
					<p class="storage-danger">
						Будут окончательно удалены {{ selectedFiles.length }} файлов общим размером
						{{ size(selectedBytes) }}. Удалённые резервные копии станут недоступны для
						восстановления.
					</p>
					<ul class="storage-review">
						<li v-for="file in selectedFiles" :key="file.id">
							<span>{{ file.relative_path }}</span
							><strong>{{ size(file.bytes) }}</strong>
						</li>
					</ul>
					<div class="storage-actions">
						<button class="btn" :disabled="cleaning" @click="reviewing = false">
							Назад к выбору
						</button>
						<button class="btn storage-delete" :disabled="cleaning" @click="cleanup">
							{{ cleaning ? 'Удаляем…' : 'Подтвердить удаление' }}
						</button>
					</div>
				</template>
				<template v-else>
					<div v-if="usage.candidates.length" class="storage-filters">
						<input
							v-model="search"
							class="input"
							type="search"
							aria-label="Поиск файлов для очистки"
							placeholder="Название файла или сборки"
						/>
						<select v-model="kind" aria-label="Тип файлов для очистки">
							<option value="all">Все файлы</option>
							<option value="backup">Резервные копии</option>
							<option value="cache">Архивы кэша</option>
						</select>
					</div>
					<p v-if="!usage.candidates.length">Подходящих файлов для очистки пока нет.</p>
					<p v-else-if="!filteredFiles.length">По этому запросу файлы не найдены.</p>
					<div class="storage-files">
						<label v-for="file in filteredFiles.slice(0, visibleCount)" :key="file.id">
							<input v-model="selected" type="checkbox" :value="file.id" :disabled="loading" />
							<span
								><strong>{{ file.name }}</strong
								><small
									>{{ file.profile_name ?? 'Кэш архивов' }} ·
									{{ file.kind === 'backup' ? 'Резервная копия' : 'Скачанный архив' }}</small
								><small>{{ file.relative_path }}</small></span
							>
							<b>{{ size(file.bytes) }}</b>
						</label>
					</div>
					<button
						v-if="filteredFiles.length > visibleCount"
						class="btn"
						@click="visibleCount += 100"
					>
						Показать ещё
					</button>
					<div v-if="usage.candidates.length" class="storage-actions">
						<span>Выбрано {{ selectedFiles.length }} · {{ size(selectedBytes) }}</span>
						<button
							class="btn"
							:disabled="!selectedFiles.length || loading"
							@click="reviewing = true"
						>
							Посмотреть перед удалением
						</button>
					</div>
				</template>
			</div>
		</template>
	</section>
</template>

<style scoped>
.storage-panel {
	display: grid;
	gap: 1rem;
}
.storage-totals {
	display: grid;
	grid-template-columns: repeat(3, minmax(0, 1fr));
	gap: 0.7rem;
}
.storage-totals > div {
	padding: 1rem;
	display: grid;
	gap: 0.35rem;
	background: var(--color-bg);
	border-radius: 0.75rem;
}
.storage-totals strong {
	font-size: 1.25rem;
}
.storage-note,
.storage-files small {
	color: var(--color-secondary);
	font-size: 0.8rem;
}
.storage-table-scroll {
	overflow: auto;
	max-height: 20rem;
}
table {
	width: 100%;
	border-collapse: collapse;
	text-align: left;
}
th,
td {
	padding: 0.65rem;
	border-bottom: 1px solid var(--color-divider);
}
td {
	white-space: nowrap;
}
.storage-cleanup {
	display: grid;
	gap: 0.8rem;
}
.storage-cleanup h4,
.storage-cleanup p {
	margin: 0;
}
.storage-filters,
.storage-actions {
	display: flex;
	gap: 0.6rem;
	align-items: center;
	flex-wrap: wrap;
}
.storage-filters input {
	flex: 1;
	min-width: 10rem;
}
.storage-filters select {
	padding: 0.7rem;
	border-radius: 0.5rem;
	background: var(--color-bg);
	color: var(--color-base);
}
.storage-files {
	max-height: 25rem;
	overflow: auto;
	display: grid;
	gap: 0.4rem;
}
.storage-files label {
	display: flex;
	gap: 0.7rem;
	padding: 0.7rem;
	align-items: center;
	border: 1px solid var(--color-divider);
	border-radius: 0.5rem;
}
.storage-files label > span {
	display: grid;
	gap: 0.2rem;
	min-width: 0;
	flex: 1;
	overflow-wrap: anywhere;
}
.storage-files b {
	white-space: nowrap;
	font-size: 0.85rem;
}
.storage-files input {
	flex: none;
}
.storage-review {
	max-height: 25rem;
	overflow: auto;
	padding-left: 1.2rem;
}
.storage-review li {
	padding: 0.35rem;
	overflow-wrap: anywhere;
}
.storage-review strong {
	display: block;
}
.storage-actions {
	justify-content: space-between;
}
.storage-danger,
.storage-delete {
	color: var(--color-red);
}
.storage-result,
.storage-warnings {
	overflow-wrap: anywhere;
}
@media (max-width: 600px) {
	.storage-totals {
		grid-template-columns: 1fr;
	}
}
</style>
