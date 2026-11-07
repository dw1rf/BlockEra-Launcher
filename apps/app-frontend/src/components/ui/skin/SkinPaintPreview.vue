<script setup lang="ts">
import { ClassicPlayerModel, SlimPlayerModel } from '@modrinth/assets'
import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js'
import { onBeforeUnmount, onMounted, ref, useTemplateRef, watch } from 'vue'

import { type Pixel, SKIN_SIZE, type SkinLayer, type SkinPart } from '@/helpers/skin-paint'
import type { SkinModel } from '@/helpers/skins'

const props = defineProps<{
	texture: HTMLCanvasElement
	revision: number
	variant: SkinModel
	layer: SkinLayer
	showOuter: boolean
	hiddenParts: Record<SkinLayer, SkinPart[]>
	lockedPart: SkinPart | ''
	rotate: boolean
	disabled: boolean
}>()
const emit = defineEmits<{ start: [pixel: Pixel]; paint: [pixel: Pixel]; end: [] }>()
const stage = useTemplateRef('stage')
const canvas = useTemplateRef('canvas')
const failed = ref(false)
const loading = ref(true)
const scene = new THREE.Scene()
const camera = new THREE.PerspectiveCamera(36, 1, 0.05, 100)
const root = new THREE.Group()
const highlightBounds = new THREE.Box3()
const highlight = new THREE.Box3Helper(highlightBounds, 0xd8b4fe)
highlight.visible = false
scene.add(highlight)
scene.add(root, new THREE.HemisphereLight(0xf3eaff, 0x8a749e, 2.3))
const light = new THREE.DirectionalLight(0xffffff, 1.4)
light.position.set(2, 4, -3)
scene.add(light)
const texture = new THREE.CanvasTexture(props.texture)
texture.flipY = false
texture.colorSpace = THREE.SRGBColorSpace
texture.magFilter = texture.minFilter = THREE.NearestFilter
texture.generateMipmaps = false
const material = new THREE.MeshLambertMaterial({
	map: texture,
	alphaTest: 0.1,
	side: THREE.DoubleSide,
})
const raycaster = new THREE.Raycaster()
const cursor = new THREE.Vector2()
// The studio supplies the texture, so the bundled models' sample textures are unused.
const placeholder = props.texture.toDataURL()
const loader = new GLTFLoader(
	new THREE.LoadingManager().setURLModifier((url) =>
		/(?:^|\/)(?:steve\.png|sunny\.png|cape)$/.test(url) ? placeholder : url,
	),
)
const models = new Map<string, Promise<THREE.Object3D>>()
let renderer: THREE.WebGLRenderer | undefined
let controls: OrbitControls | undefined
let resize: ResizeObserver | undefined
let meshes: THREE.Mesh[] = []
let modelRequest = 0
let painting: number | undefined
let disposed = false

function draw() {
	renderer?.render(scene, camera)
}
function resizeStage() {
	if (!stage.value || !renderer) return
	const { clientWidth: width, clientHeight: height } = stage.value
	if (!width || !height) return
	renderer.setSize(width, height, false)
	camera.aspect = width / height
	camera.updateProjectionMatrix()
	draw()
}
function syncLayers() {
	for (const mesh of meshes) {
		const layer = mesh.userData.outer ? 'outer' : 'base'
		mesh.visible =
			(layer === 'base' || props.showOuter) &&
			!props.hiddenParts[layer].includes(mesh.userData.part)
	}
	const selected = meshes.find(
		(mesh) =>
			mesh.visible &&
			mesh.userData.part === props.lockedPart &&
			mesh.userData.outer === (props.layer === 'outer'),
	)
	highlight.visible = !!selected
	if (selected) highlightBounds.setFromObject(selected).expandByScalar(0.006)
	draw()
}
async function loadSkinModel() {
	const request = ++modelRequest
	loading.value = true
	failed.value = false
	try {
		const url = props.variant === 'SLIM' ? SlimPlayerModel : ClassicPlayerModel
		let pending = models.get(url)
		if (!pending) {
			pending = loader
				.loadAsync(url)
				.then(({ scene }) => scene)
				.catch((error) => {
					models.delete(url)
					throw error
				})
			models.set(url, pending)
		}
		const model = (await pending).clone(true)
		if (request !== modelRequest || disposed) return
		const body: THREE.Mesh[] = []
		const capes: THREE.Mesh[] = []
		model.traverse((node) => {
			if (!(node as THREE.Mesh).isMesh) return
			const mesh = node as THREE.Mesh
			if (mesh.name.toLowerCase().startsWith('cape')) {
				capes.push(mesh)
				return
			}
			mesh.material = material
			mesh.userData.outer = mesh.name.endsWith('_Layer')
			const parts: Record<string, SkinPart> = {
				Head: 'head',
				Hat: 'head',
				Body: 'torso',
				Right_Arm: 'right-arm',
				Left_Arm: 'left-arm',
				Right_Leg: 'right-leg',
				Left_Leg: 'left-leg',
			}
			mesh.userData.part = parts[mesh.name.replace(/_(?:Layer|2)$/, '')]
			body.push(mesh)
		})
		for (const cape of capes) cape.removeFromParent()
		const bounds = new THREE.Box3().setFromObject(model)
		const center = bounds.getCenter(new THREE.Vector3())
		const scale = 2.15 / bounds.getSize(new THREE.Vector3()).y
		model.scale.setScalar(scale)
		model.position.copy(center.multiplyScalar(-scale))
		root.clear()
		root.add(model)
		meshes = body
		syncLayers()
	} catch {
		if (request === modelRequest && !disposed) failed.value = true
	} finally {
		if (request === modelRequest && !disposed) loading.value = false
	}
}
function hit(event: PointerEvent): Pixel | undefined {
	const rect = canvas.value?.getBoundingClientRect()
	if (!rect || !rect.width || !rect.height) return
	cursor.set(
		((event.clientX - rect.left) / rect.width) * 2 - 1,
		1 - ((event.clientY - rect.top) / rect.height) * 2,
	)
	raycaster.setFromCamera(cursor, camera)
	const outer = props.layer === 'outer'
	const uv = raycaster.intersectObjects(
		meshes.filter((mesh) => mesh.userData.outer === outer && mesh.visible),
		false,
	)[0]?.uv
	if (!uv) return
	const pixel = (axis: number) => Math.max(0, Math.min(SKIN_SIZE - 1, Math.floor(axis * SKIN_SIZE)))
	return { x: pixel(uv.x), y: pixel(uv.y) }
}
function pointerDown(event: PointerEvent) {
	if (event.button !== 0 || props.rotate || props.disabled || painting !== undefined) return
	// Left drag draws. OrbitControls keeps right drag and wheel for the camera.
	event.stopImmediatePropagation()
	const pixel = hit(event)
	if (!pixel) return
	painting = event.pointerId
	canvas.value?.setPointerCapture(event.pointerId)
	emit('start', pixel)
}
function pointerMove(event: PointerEvent) {
	if (painting !== event.pointerId) return
	const pixel = hit(event)
	if (pixel) emit('paint', pixel)
}
function pointerEnd(event: PointerEvent) {
	if (painting !== event.pointerId) return
	painting = undefined
	emit('end')
}

onMounted(() => {
	if (!canvas.value || !stage.value) return
	try {
		renderer = new THREE.WebGLRenderer({ canvas: canvas.value, alpha: true, antialias: true })
	} catch {
		failed.value = true
		loading.value = false
		return
	}
	renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
	camera.position.set(1.35, 0.65, 4.2)
	controls = new OrbitControls(camera, canvas.value)
	controls.enablePan = false
	controls.minDistance = 1.3
	controls.maxDistance = 7
	controls.mouseButtons.RIGHT = THREE.MOUSE.ROTATE
	controls.addEventListener('change', draw)
	controls.update()
	resize = new ResizeObserver(resizeStage)
	resize.observe(stage.value)
	resizeStage()
	void loadSkinModel()
})
watch(
	() => props.variant,
	() => {
		if (renderer) void loadSkinModel()
	},
)
watch(() => [props.showOuter, props.hiddenParts, props.lockedPart, props.layer], syncLayers)
watch(
	() => props.revision,
	() => {
		texture.needsUpdate = true
		draw()
	},
)
onBeforeUnmount(() => {
	disposed = true
	resize?.disconnect()
	controls?.dispose()
	texture.dispose()
	material.dispose()
	highlight.dispose()
	renderer?.dispose()
	for (const model of models.values()) {
		void model
			.then((scene) =>
				scene.traverse((node) => {
					if (!(node as THREE.Mesh).isMesh) return
					const mesh = node as THREE.Mesh<THREE.BufferGeometry, THREE.MeshStandardMaterial>
					mesh.geometry.dispose()
					mesh.material.map?.dispose()
					mesh.material.dispose()
				}),
			)
			.catch(() => {})
	}
})
</script>

<template>
	<div ref="stage" class="paint-stage" :class="{ rotating: rotate }">
		<canvas
			ref="canvas"
			:aria-label="$t('blockera.skin-studio.model-view')"
			@pointerdown="pointerDown"
			@pointermove="pointerMove"
			@pointerup="pointerEnd"
			@pointercancel="pointerEnd"
			@lostpointercapture="pointerEnd"
			@contextmenu.prevent
		/>
		<p v-if="loading || failed" role="status">
			{{ $t(failed ? 'blockera.skin-studio.preview-unavailable' : 'blockera.skin-studio.loading') }}
		</p>
	</div>
</template>

<style scoped>
.paint-stage {
	position: relative;
	height: 100%;
	min-height: 16rem;
}
canvas {
	display: block;
	width: 100%;
	height: 100%;
	cursor: crosshair;
	touch-action: none;
}
.rotating canvas {
	cursor: grab;
}
.rotating canvas:active {
	cursor: grabbing;
}
p {
	position: absolute;
	inset: 0;
	display: grid;
	place-content: center;
	padding: 1rem;
	color: #c8b7dc;
	text-align: center;
	pointer-events: none;
}
</style>
