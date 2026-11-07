<script setup lang="ts">
import {
	DownloadIcon,
	PaintbrushIcon,
	RedoIcon,
	RotateClockwiseIcon,
	SaveIcon,
	UndoIcon,
	UploadIcon,
} from '@modrinth/assets'
import { computed, onMounted, ref, shallowRef, useTemplateRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { onBeforeRouteLeave } from 'vue-router'

import steveSkin from '@/assets/skins/steve.png'
import SkinPaintPreview from '@/components/ui/skin/SkinPaintPreview.vue'
import {
	fillSkinFace,
	partForRegion,
	type Pixel,
	resizeSkinArms,
	SKIN_PARTS,
	SKIN_SIZE,
	skinFaceMask,
	type SkinLayer,
	type SkinPart,
	visitLine,
} from '@/helpers/skin-paint'
import {
	add_and_equip_custom_skin,
	determineModelType,
	get_normalized_skin_texture,
	normalize_skin_texture,
	type Skin,
	type SkinModel,
} from '@/helpers/skins'

const props = defineProps<{
	initialSkin?: Skin
	templates: Skin[]
	accountId?: string
	activeAccountId?: string
}>()
const emit = defineEmits<{ close: []; saved: [] }>()
const { t } = useI18n()
const namespace = 'blockera.skin-studio.'
const tr = (key: string) => t(namespace + key)
const texture = document.createElement('canvas')
texture.width = texture.height = SKIN_SIZE
const ctx = texture.getContext('2d', { willReadFrequently: true })!
const atlas = useTemplateRef('atlas')
const upload = useTemplateRef('upload')
const revision = ref(0)
const variant = ref<SkinModel>('CLASSIC')
const layer = ref<SkinLayer>('base')
const outerVisible = ref(true)
const hiddenParts = ref<Record<SkinLayer, SkinPart[]>>({ base: [], outer: [] })
const lockedPart = ref<SkinPart | ''>('')
const grid = ref(true)
const view = ref<'split' | 'model' | 'texture'>('split')
const tool = ref<'brush' | 'fill' | 'erase' | 'sample' | 'rotate'>('brush')
const color = ref('#a855f7')
const brushSize = ref(1)
const error = ref('')
const loading = ref(true)
const saving = ref(false)
const saved = ref(false)
const templateIndex = ref('')
const position = ref<Pixel>({ x: 8, y: 8 })
const palette = [
	'#f6e6d3',
	'#bf8b6b',
	'#875942',
	'#33243d',
	'#100d18',
	'#46316e',
	'#7427a6',
	'#a855f7',
	'#d8b4fe',
	'#f472b6',
	'#48c9c0',
	'#f7c85f',
]
const neutral = [215, 198, 232, 255]
const mask = computed(() =>
	skinFaceMask(variant.value, layer.value).map((region) => {
		const part = partForRegion(region)
		return part &&
			!hiddenParts.value[layer.value].includes(part) &&
			(!lockedPart.value || lockedPart.value === part)
			? region
			: 0
	}),
)

function togglePart(which: SkinLayer, part: SkinPart) {
	if (which === 'outer' && !outerVisible.value && layer.value !== 'outer') {
		hiddenParts.value = { ...hiddenParts.value, outer: SKIN_PARTS.filter((item) => item !== part) }
		outerVisible.value = true
		return
	}
	const hidden = hiddenParts.value[which]
	hiddenParts.value = {
		...hiddenParts.value,
		[which]: hidden.includes(part) ? hidden.filter((item) => item !== part) : [...hidden, part],
	}
	if (which === 'outer') outerVisible.value = true
}

function showAllParts() {
	hiddenParts.value = { base: [], outer: [] }
	outerVisible.value = true
}
const busy = computed(() => loading.value || saving.value)
const canApply = computed(() => !!props.accountId && props.accountId === props.activeAccountId)

type Snapshot = { pixels: ImageData; variant: SkinModel }
const undoStack = shallowRef<Snapshot[]>([])
const redoStack = shallowRef<Snapshot[]>([])
const baseline = shallowRef<Snapshot>()
let before: Snapshot | undefined
let last: Pixel | undefined
let strokeChanged = false
let atlasPointer: number | undefined

function snapshot(): Snapshot {
	return { pixels: ctx.getImageData(0, 0, SKIN_SIZE, SKIN_SIZE), variant: variant.value }
}
const dirty = computed(() => {
	if (!baseline.value || !revision.value) return false
	const current = snapshot()
	return (
		current.variant !== baseline.value.variant ||
		current.pixels.data.some((value, i) => value !== baseline.value!.pixels.data[i])
	)
})
function refreshAtlas() {
	const preview = atlas.value?.getContext('2d')
	if (!preview) return
	const pixels = ctx.getImageData(0, 0, SKIN_SIZE, SKIN_SIZE)
	for (let index = 0; index < mask.value.length; index++) {
		if (!mask.value[index]) pixels.data[index * 4 + 3] *= 0.36
	}
	preview.putImageData(pixels, 0, 0)
}
function updated() {
	saved.value = false
	revision.value++
	refreshAtlas()
}
function remember(previous: Snapshot) {
	undoStack.value = [...undoStack.value.slice(-49), previous]
	redoStack.value = []
}
function restore(state: Snapshot) {
	variant.value = state.variant
	ctx.putImageData(state.pixels, 0, 0)
	updated()
}
function history(direction: 'undo' | 'redo') {
	if (busy.value || before) return
	const from = direction === 'undo' ? undoStack : redoStack
	const to = direction === 'undo' ? redoStack : undoStack
	const item = from.value.at(-1)
	if (!item) return
	from.value = from.value.slice(0, -1)
	to.value = [...to.value, snapshot()]
	restore(item)
}
function changeModel(event: Event) {
	const model = (event.target as HTMLSelectElement).value as SkinModel
	if (variant.value === model) return
	remember(snapshot())
	const pixels = ctx.getImageData(0, 0, SKIN_SIZE, SKIN_SIZE)
	resizeSkinArms(pixels.data, variant.value, model)
	ctx.putImageData(pixels, 0, 0)
	variant.value = model
	updated()
}
function rgba() {
	if (tool.value === 'erase') return layer.value === 'outer' ? [0, 0, 0, 0] : neutral
	return [1, 3, 5].map((index) => parseInt(color.value.slice(index, index + 2), 16)).concat(255)
}
function drawPixel(pixel: Pixel) {
	if (!before || busy.value) return
	position.value = pixel
	const region = mask.value[pixel.y * SKIN_SIZE + pixel.x]
	if (!region) {
		last = undefined
		return
	}
	const pixels = ctx.getImageData(0, 0, SKIN_SIZE, SKIN_SIZE)
	const paint = rgba()
	let changed = false
	if (tool.value === 'fill') changed = fillSkinFace(pixels.data, mask.value, pixel, paint)
	else {
		const start = last && mask.value[last.y * SKIN_SIZE + last.x] === region ? last : pixel
		const low = -Math.floor(brushSize.value / 2)
		visitLine(start, pixel, (x, y) => {
			for (let dy = low; dy < low + brushSize.value; dy++) {
				for (let dx = low; dx < low + brushSize.value; dx++) {
					const px = x + dx
					const py = y + dy
					if (px < 0 || px >= SKIN_SIZE || py < 0 || py >= SKIN_SIZE) continue
					const index = py * SKIN_SIZE + px
					if (
						mask.value[index] !== region ||
						paint.every((value, i) => value === pixels.data[index * 4 + i])
					)
						continue
					pixels.data.set(paint, index * 4)
					changed = true
				}
			}
		})
	}
	last = pixel
	if (changed) {
		ctx.putImageData(pixels, 0, 0)
		strokeChanged = true
		updated()
	}
}
function startStroke(pixel: Pixel) {
	if (busy.value || before || tool.value === 'rotate') return
	position.value = pixel
	if (tool.value === 'sample') {
		if (!mask.value[pixel.y * SKIN_SIZE + pixel.x]) return
		const [r, g, b, a] = ctx.getImageData(pixel.x, pixel.y, 1, 1).data
		if (a)
			color.value = '#' + [r, g, b].map((channel) => channel.toString(16).padStart(2, '0')).join('')
		return
	}
	before = snapshot()
	last = undefined
	strokeChanged = false
	drawPixel(pixel)
	if (tool.value === 'fill') endStroke()
}
function endStroke() {
	if (before && strokeChanged) remember(before)
	before = undefined
	last = undefined
}
function atlasPixel(event: PointerEvent): Pixel {
	const bounds = atlas.value!.getBoundingClientRect()
	const axis = (value: number) => Math.max(0, Math.min(63, Math.floor(value * SKIN_SIZE)))
	return {
		x: axis((event.clientX - bounds.left) / bounds.width),
		y: axis((event.clientY - bounds.top) / bounds.height),
	}
}
function startAtlas(event: PointerEvent) {
	if (event.button !== 0 || busy.value || atlasPointer !== undefined) return
	atlasPointer = event.pointerId
	atlas.value?.setPointerCapture(event.pointerId)
	startStroke(atlasPixel(event))
}
function moveAtlas(event: PointerEvent) {
	if (atlasPointer !== event.pointerId) return
	position.value = atlasPixel(event)
	drawPixel(position.value)
}
function endAtlas(event: PointerEvent) {
	if (atlasPointer !== event.pointerId) return
	atlasPointer = undefined
	endStroke()
}
function atlasKeys(event: KeyboardEvent) {
	if (busy.value) return
	const delta: Record<string, Pixel> = {
		ArrowLeft: { x: -1, y: 0 },
		ArrowRight: { x: 1, y: 0 },
		ArrowUp: { x: 0, y: -1 },
		ArrowDown: { x: 0, y: 1 },
	}
	const move = delta[event.key]
	if (move) {
		event.preventDefault()
		const clamp = (value: number) => Math.max(0, Math.min(63, value))
		position.value = { x: clamp(position.value.x + move.x), y: clamp(position.value.y + move.y) }
	} else if (event.key === ' ' || event.key === 'Enter') {
		event.preventDefault()
		startStroke(position.value)
		endStroke()
	}
}
async function imageData(url: string) {
	const image = new Image()
	image.src = url
	await image.decode()
	if (image.width !== 64 || image.height !== 64) throw new Error(tr('png-size'))
	const temporary = document.createElement('canvas')
	temporary.width = temporary.height = SKIN_SIZE
	const context = temporary.getContext('2d')!
	context.drawImage(image, 0, 0)
	return context.getImageData(0, 0, SKIN_SIZE, SKIN_SIZE)
}
async function replaceSkin(getSource: () => Promise<{ pixels: ImageData; variant: SkinModel }>) {
	if (busy.value) return
	loading.value = true
	error.value = ''
	try {
		const source = await getSource()
		remember(snapshot())
		restore(source)
	} catch (err) {
		error.value = err instanceof Error ? err.message : tr('load-error')
	} finally {
		loading.value = false
	}
}
function useTemplate(skin?: Skin) {
	void replaceSkin(async () => ({
		pixels: await imageData(skin ? await get_normalized_skin_texture(skin) : steveSkin),
		variant: skin?.variant === 'SLIM' ? 'SLIM' : 'CLASSIC',
	}))
}
function blankSkin() {
	void replaceSkin(async () => {
		const pixels = ctx.createImageData(SKIN_SIZE, SKIN_SIZE)
		skinFaceMask(variant.value, 'base').forEach((region, i) => {
			if (region) pixels.data.set(neutral, i * 4)
		})
		return { pixels, variant: variant.value }
	})
}
async function importSkin(event: Event) {
	const input = event.target as HTMLInputElement
	const file = input.files?.[0]
	input.value = ''
	if (!file) return
	await replaceSkin(async () => {
		if (!file.name.toLowerCase().endsWith('.png') || file.size > 2 * 1024 * 1024)
			throw new Error(tr('png-size'))
		const bytes = await normalize_skin_texture(new Uint8Array(await file.arrayBuffer()))
		const url = URL.createObjectURL(new Blob([new Uint8Array(bytes)], { type: 'image/png' }))
		try {
			return { pixels: await imageData(url), variant: await determineModelType(url) }
		} finally {
			URL.revokeObjectURL(url)
		}
	})
}
function exportSkin() {
	const link = document.createElement('a')
	link.href = texture.toDataURL('image/png')
	link.download = 'blockera-skin.png'
	link.click()
}
async function saveSkin() {
	if (busy.value || !canApply.value) return
	saving.value = true
	error.value = ''
	try {
		const blob = await new Promise<Blob>((resolve, reject) =>
			texture.toBlob(
				(result) => (result ? resolve(result) : reject(new Error(tr('save-error')))),
				'image/png',
			),
		)
		await add_and_equip_custom_skin(
			new Uint8Array(await blob.arrayBuffer()),
			variant.value,
			undefined,
			props.accountId,
		)
		baseline.value = snapshot()
		saved.value = true
		emit('saved')
	} catch (err) {
		error.value = err instanceof Error ? err.message : tr('save-error')
	} finally {
		saving.value = false
	}
}
function mayLeave() {
	return !saving.value && (!dirty.value || window.confirm(tr('discard')))
}
function close() {
	if (mayLeave()) emit('close')
}
function shortcuts(event: KeyboardEvent) {
	if (
		!(event.ctrlKey || event.metaKey) ||
		event.target instanceof HTMLInputElement ||
		event.target instanceof HTMLSelectElement
	)
		return
	const key = event.key.toLowerCase()
	if (key !== 'z' && key !== 'y') return
	event.preventDefault()
	history(key === 'y' || event.shiftKey ? 'redo' : 'undo')
}
watch([mask, view], refreshAtlas, { flush: 'post' })
watch(layer, (value) => {
	if (value === 'outer') outerVisible.value = true
})
onBeforeRouteLeave(mayLeave)
onMounted(async () => {
	try {
		const skin = props.initialSkin
		restore({
			pixels: await imageData(skin ? await get_normalized_skin_texture(skin) : steveSkin),
			variant: skin?.variant === 'SLIM' ? 'SLIM' : 'CLASSIC',
		})
	} catch {
		restore({ pixels: await imageData(steveSkin), variant: 'CLASSIC' })
		error.value = tr('load-error')
	} finally {
		baseline.value = snapshot()
		loading.value = false
	}
})
</script>

<template>
	<main class="skin-workshop" @keydown="shortcuts">
		<header class="workshop-header">
			<div class="workshop-title">
				<button type="button" :disabled="saving" @click="close">← {{ tr('wardrobe') }}</button>
				<div>
					<small>BLOCKERA STUDIO</small>
					<h1>{{ tr('title') }}</h1>
				</div>
			</div>
			<div class="workshop-actions">
				<input ref="upload" type="file" accept="image/png,.png" hidden @change="importSkin" />
				<button type="button" :disabled="busy" @click="upload?.click()">
					<UploadIcon /> {{ tr('import') }}
				</button>
				<button type="button" :disabled="busy" @click="exportSkin">
					<DownloadIcon /> {{ tr('export') }}
				</button>
				<button v-if="canApply" type="button" class="primary" :disabled="busy" @click="saveSkin">
					<SaveIcon /> {{ tr(saving ? 'saving' : 'apply') }}
				</button>
			</div>
		</header>
		<p v-if="error" class="studio-error" role="alert">{{ error }}</p>
		<p v-else-if="saved" class="studio-success" role="status">{{ tr('saved') }}</p>
		<div class="workshop-layout">
			<aside class="tool-panel">
				<h2>{{ tr('tools') }}</h2>
				<div class="tools">
					<button
						v-for="item in ['brush', 'fill', 'erase', 'sample', 'rotate'] as const"
						:key="item"
						type="button"
						:aria-pressed="tool === item"
						:disabled="busy"
						@click="tool = item"
					>
						<PaintbrushIcon v-if="item === 'brush'" /><RotateClockwiseIcon
							v-else-if="item === 'rotate'"
						/>
						<span v-else class="tool-symbol" aria-hidden="true">{{
							{ fill: '◩', erase: '▱', sample: '◉' }[item]
						}}</span
						>{{ tr(item) }}
					</button>
				</div>
				<label class="field"
					>{{ tr('brush-size')
					}}<select v-model.number="brushSize" :disabled="busy">
						<option v-for="size in [1, 2, 3, 4]" :key="size" :value="size">{{ size }} px</option>
					</select></label
				>
				<label class="color-field"
					><input v-model="color" type="color" :disabled="busy" />{{ color.toUpperCase() }}</label
				>
				<div class="swatches">
					<button
						v-for="swatch in palette"
						:key="swatch"
						type="button"
						:disabled="busy"
						:aria-label="swatch"
						:aria-pressed="color === swatch"
						:style="{ background: swatch }"
						@click="color = swatch"
					/>
				</div>
				<div class="history-tools">
					<button
						type="button"
						:aria-label="tr('undo')"
						:title="tr('undo')"
						:disabled="busy || !undoStack.length"
						@click="history('undo')"
					>
						<UndoIcon />
					</button>
					<button
						type="button"
						:aria-label="tr('redo')"
						:title="tr('redo')"
						:disabled="busy || !redoStack.length"
						@click="history('redo')"
					>
						<RedoIcon />
					</button>
				</div>
			</aside>
			<section class="workshop-canvas" :aria-label="tr('workspace')">
				<div class="canvas-toolbar">
					<div class="segments">
						<button
							v-for="mode in ['split', 'model', 'texture'] as const"
							:key="mode"
							type="button"
							:aria-pressed="view === mode"
							@click="view = mode"
						>
							{{ tr(mode) }}
						</button>
					</div>
					<span>64 × 64 · PNG</span>
				</div>
				<div class="canvas-panes" :class="{ split: view === 'split' }">
					<div v-show="view !== 'texture'" class="model-pane">
						<SkinPaintPreview
							:texture="texture"
							:revision="revision"
							:variant="variant"
							:layer="layer"
							:show-outer="outerVisible || layer === 'outer'"
							:hidden-parts="hiddenParts"
							:locked-part="lockedPart"
							:rotate="tool === 'rotate'"
							:disabled="busy"
							@start="startStroke"
							@paint="drawPixel"
							@end="endStroke"
						/>
						<span class="pane-hint">{{ tr('rotate-hint') }}</span>
					</div>
					<div v-show="view !== 'model'" class="atlas-pane">
						<div class="atlas-wrap" :class="{ grid }">
							<canvas
								ref="atlas"
								width="64"
								height="64"
								tabindex="0"
								:aria-label="tr('atlas-label')"
								@keydown="atlasKeys"
								@pointerdown="startAtlas"
								@pointermove="moveAtlas"
								@pointerup="endAtlas"
								@pointercancel="endAtlas"
								@lostpointercapture="endAtlas"
							/>
							<span
								class="pixel-cursor"
								:style="{ left: `${(position.x * 100) / 64}%`, top: `${(position.y * 100) / 64}%` }"
								aria-hidden="true"
							/>
						</div>
					</div>
				</div>
				<footer class="canvas-footer">
					<span
						>{{ tr(layer === 'base' ? 'base' : 'outer')
						}}<template v-if="lockedPart"> · {{ tr(lockedPart) }}</template></span
					><span>X {{ position.x }} · Y {{ position.y }}</span>
				</footer>
			</section>
			<aside class="settings-panel">
				<h2>{{ tr('parameters') }}</h2>
				<label class="field"
					>{{ tr('model')
					}}<select :value="variant" :disabled="busy" @change="changeModel">
						<option value="CLASSIC">{{ tr('classic') }}</option>
						<option value="SLIM">{{ tr('slim') }}</option>
					</select></label
				>
				<label class="field"
					>{{ tr('layer')
					}}<select v-model="layer" :disabled="busy">
						<option value="base">{{ tr('base') }}</option>
						<option value="outer">{{ tr('outer') }}</option>
					</select></label
				>
				<label class="toggle"
					><input v-model="outerVisible" type="checkbox" :disabled="layer === 'outer'" />
					{{ tr('show-outer') }}</label
				>
				<label class="toggle"><input v-model="grid" type="checkbox" /> {{ tr('grid') }}</label>
				<section class="parts-panel">
					<h2>{{ tr('body-parts') }}</h2>
					<div class="body-diagrams">
						<div
							v-for="which in ['base', 'outer'] as const"
							:key="which"
							class="body-diagram-group"
						>
							<div class="body-diagram" :aria-label="tr(which)" role="group">
								<button
									v-for="part in SKIN_PARTS"
									:key="part"
									type="button"
									:class="part"
									:title="tr(part)"
									:aria-label="`${tr(which)}: ${tr(part)}`"
									:aria-pressed="
										!hiddenParts[which].includes(part) && (which === 'base' || outerVisible)
									"
									:disabled="busy"
									@click="togglePart(which, part)"
								/>
							</div>
							<span>{{ tr(which) }}</span>
						</div>
					</div>
					<p class="account-hint">{{ tr('parts-hint') }}</p>
					<label class="field"
						>{{ tr('lock-part') }}
						<select v-model="lockedPart" :disabled="busy">
							<option value="">{{ tr('all-parts') }}</option>
							<option v-for="part in SKIN_PARTS" :key="part" :value="part">{{ tr(part) }}</option>
						</select>
					</label>
					<button type="button" :disabled="busy" @click="showAllParts">
						{{ tr('show-all-parts') }}
					</button>
				</section>
				<div class="templates">
					<h2>{{ tr('templates') }}</h2>
					<div class="template-tiles">
						<button type="button" :disabled="busy" @click="useTemplate()">
							<img :src="steveSkin" alt="" />{{ tr('steve') }}</button
						><button type="button" :disabled="busy" @click="blankSkin">
							<span aria-hidden="true">+</span>{{ tr('blank') }}
						</button>
					</div>
					<button
						v-if="initialSkin"
						type="button"
						:disabled="busy"
						@click="useTemplate(initialSkin)"
					>
						{{ tr('original') }}
					</button>
					<label v-if="templates.length" class="field"
						>{{ tr('standard')
						}}<select
							v-model="templateIndex"
							:disabled="busy"
							@change="templateIndex !== '' && useTemplate(templates[Number(templateIndex)])"
						>
							<option value="">{{ tr('choose') }}</option>
							<option
								v-for="(skin, index) in templates"
								:key="skin.texture_key + skin.variant"
								:value="String(index)"
							>
								{{ skin.name || tr('standard') }} ·
								{{ tr(skin.variant === 'SLIM' ? 'slim' : 'classic') }}
							</option>
						</select></label
					>
				</div>
				<p v-if="!canApply" class="account-hint">
					{{ tr(accountId ? 'account-changed' : 'sign-in') }}
				</p>
			</aside>
		</div>
	</main>
</template>

<style scoped>
.skin-workshop {
	padding: 1.25rem clamp(1rem, 2vw, 2rem) 2rem;
	display: grid;
	gap: 1rem;
	color: #f5effe;
}
.workshop-header,
.tool-panel,
.settings-panel,
.workshop-canvas {
	border: 1px solid #d8b4fe26;
	border-radius: 0.95rem;
	background: #101521;
}
.workshop-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
	flex-wrap: wrap;
	padding: 1rem 1.15rem;
	background: linear-gradient(110deg, #24143d, #101521 68%);
}
.workshop-title,
.workshop-actions,
.history-tools,
.segments {
	display: flex;
	align-items: center;
	gap: 0.45rem;
	flex-wrap: wrap;
}
.workshop-title {
	gap: 1rem;
}
.workshop-title small {
	font-size: 0.65rem;
	font-weight: 800;
	color: #c99cf9;
	letter-spacing: 0.12em;
}
h1,
h2,
p {
	margin: 0;
}
h1,
h2 {
	color: #f5effe;
	font-weight: 750;
}
h1 {
	font-size: 1.45rem;
	line-height: 1.25;
}
h2 {
	font-size: 0.86rem;
}
button,
select {
	border: 1px solid #d8b4fe29;
	border-radius: 0.55rem;
	background: #1a2030;
	color: #eee8f8;
	font: inherit;
}
button {
	min-height: 2.2rem;
	padding: 0.42rem 0.65rem;
	display: inline-flex;
	gap: 0.4rem;
	align-items: center;
	justify-content: center;
	cursor: pointer;
}
button:hover:not(:disabled) {
	background: #2c2142;
}
button:disabled {
	opacity: 0.5;
	cursor: default;
}
button[aria-pressed='true'],
button.primary {
	background: #532784;
	border-color: #ae75e6;
	color: #fff;
}
button:focus-visible,
select:focus-visible,
canvas:focus-visible,
input:focus-visible {
	outline: 2px solid #d8b4fe;
	outline-offset: 2px;
}
button svg {
	width: 1rem;
	height: 1rem;
	flex: none;
}
.workshop-layout {
	display: grid;
	grid-template-columns: clamp(10.75rem, 13vw, 13.5rem) minmax(0, 1fr) clamp(15.25rem, 19vw, 20rem);
	gap: 0.75rem;
	align-items: start;
}
.tool-panel,
.settings-panel {
	padding: 0.95rem;
	display: grid;
	gap: 0.7rem;
	min-width: 0;
}
.tools {
	display: grid;
	gap: 0.35rem;
}
.tools button {
	justify-content: flex-start;
	min-height: 2.65rem;
	font-size: 0.88rem;
}
.tool-symbol {
	width: 1rem;
	text-align: center;
}
.field {
	display: grid;
	gap: 0.35rem;
	font-size: 0.76rem;
	color: #c8bad9;
}
select {
	padding: 0.42rem;
	min-width: 0;
	width: 100%;
}
.color-field {
	display: flex;
	align-items: center;
	gap: 0.35rem;
	font: 0.73rem monospace;
}
.color-field input {
	width: 1.85rem;
	height: 1.85rem;
	padding: 0;
	border: 0;
	background: transparent;
}
.swatches {
	display: grid;
	grid-template-columns: repeat(4, 1fr);
	gap: 0.28rem;
}
.swatches button {
	min-height: 0;
	aspect-ratio: 1;
	padding: 0;
	border-radius: 0.35rem;
}
.swatches button[aria-pressed='true'] {
	outline: 2px solid #f6eaff;
	outline-offset: 1px;
}
.workshop-canvas {
	min-width: 0;
	overflow: hidden;
}
.canvas-toolbar,
.canvas-footer {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 0.4rem;
	flex-wrap: wrap;
	padding: 0.6rem;
	font-size: 0.73rem;
	color: #c8bad9;
}
.canvas-toolbar {
	border-bottom: 1px solid #d8b4fe20;
}
.canvas-toolbar button {
	font-size: 0.78rem;
}
.canvas-panes {
	display: grid;
	min-height: clamp(22rem, 47vh, 37rem);
}
.canvas-panes.split {
	grid-template-columns: 1fr 1fr;
}
.model-pane {
	position: relative;
	min-width: 0;
	background: radial-gradient(ellipse at 50% 44%, #302048, #0b101b 68%);
}
.pane-hint {
	position: absolute;
	bottom: 0.7rem;
	left: 0;
	right: 0;
	text-align: center;
	font-size: 0.68rem;
	color: #c8bad9;
	pointer-events: none;
}
.atlas-pane {
	display: grid;
	place-items: center;
	padding: 0.75rem;
	min-width: 0;
	background: #080c15;
}
.atlas-wrap {
	width: min(100%, 26rem);
	aspect-ratio: 1;
	position: relative;
	background: repeating-conic-gradient(#272034 0 25%, #161220 0 50%) 50% / 12.5% 12.5%;
	box-shadow: 0 0 0 1px #c99cf94d;
}
.atlas-wrap canvas {
	width: 100%;
	height: 100%;
	display: block;
	image-rendering: pixelated;
	cursor: crosshair;
	touch-action: none;
}
.atlas-wrap.grid::after {
	content: '';
	position: absolute;
	inset: 0;
	pointer-events: none;
	background:
		linear-gradient(to right, #aaa0c42e 1px, transparent 1px),
		linear-gradient(to bottom, #aaa0c42e 1px, transparent 1px);
	background-size: calc(100% / 64) calc(100% / 64);
}
.pixel-cursor {
	position: absolute;
	width: calc(100% / 64);
	height: calc(100% / 64);
	outline: 1px solid white;
	display: none;
	pointer-events: none;
}
.atlas-wrap:focus-within .pixel-cursor {
	display: block;
}
.canvas-footer {
	border-top: 1px solid #d8b4fe20;
}
.toggle {
	display: flex;
	align-items: center;
	gap: 0.3rem;
	font-size: 0.75rem;
	color: #d8cae5;
}
.toggle input {
	accent-color: #9a53db;
}
.templates {
	border-top: 1px solid #d8b4fe20;
	padding-top: 0.65rem;
	display: grid;
	gap: 0.55rem;
}
.parts-panel {
	border-top: 1px solid #d8b4fe20;
	padding-top: 0.75rem;
	display: grid;
	gap: 0.65rem;
}
.body-diagrams {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 0.5rem;
}
.body-diagram-group {
	display: grid;
	justify-items: center;
	gap: 0.45rem;
	font-size: 0.72rem;
	color: #d8cae5;
}
.body-diagram {
	display: grid;
	grid-template-columns: 1rem 1rem 1rem 1rem;
	grid-template-rows: 1.8rem 2.55rem 2.55rem;
	gap: 3px;
	grid-template-areas: '. head head .' 'right-arm torso torso left-arm' '. right-leg left-leg .';
}
.body-diagram button {
	min-height: 0;
	padding: 0;
	border-radius: 0.25rem;
}
.body-diagram button[aria-pressed='false'] {
	border-style: dashed;
	background: #131725;
}
.body-diagram .head {
	grid-area: head;
}
.body-diagram .torso {
	grid-area: torso;
}
.body-diagram .right-arm {
	grid-area: right-arm;
}
.body-diagram .left-arm {
	grid-area: left-arm;
}
.body-diagram .right-leg {
	grid-area: right-leg;
}
.body-diagram .left-leg {
	grid-area: left-leg;
}
.template-tiles {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 0.35rem;
}
.template-tiles button {
	flex-direction: column;
	font-size: 0.72rem;
	padding: 0.35rem;
}
.template-tiles img,
.template-tiles span {
	width: 2rem;
	height: 2rem;
	image-rendering: pixelated;
	display: grid;
	place-items: center;
}
.template-tiles span {
	font-size: 1.4rem;
	color: #d5a9ff;
}
.account-hint,
.studio-error {
	color: #dfc9f4;
	font-size: 0.76rem;
	line-height: 1.4;
}
.studio-success {
	border: 1px solid #9d64d775;
	background: #211633;
	border-radius: 0.6rem;
	padding: 0.65rem;
	color: #e9d5ff;
}
.studio-error {
	border: 1px solid #ec789c55;
	background: #3c1c33;
	border-radius: 0.6rem;
	padding: 0.65rem;
}
@media (max-width: 950px) {
	.workshop-layout {
		grid-template-columns: 8rem minmax(0, 1fr);
	}
	.settings-panel {
		grid-column: 1 / -1;
	}
}
@media (max-width: 620px) {
	.workshop-layout,
	.canvas-panes.split {
		grid-template-columns: 1fr;
	}
	.tools {
		grid-template-columns: repeat(2, 1fr);
	}
	.model-pane {
		min-height: 20rem;
	}
}
</style>
