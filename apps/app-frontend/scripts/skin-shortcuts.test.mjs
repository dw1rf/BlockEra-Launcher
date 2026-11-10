import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { runInNewContext } from 'node:vm'

const require = createRequire(import.meta.url)
const vue = require('vue')
const ts = require('typescript')
const { parse, compileScript } = require('@vue/compiler-sfc')
const source = readFileSync(
	new URL('../src/components/ui/skin/SkinWorkshop.vue', import.meta.url),
	'utf8',
)
const { descriptor } = parse(source)
const code = ts.transpileModule(compileScript(descriptor, { id: 'skin-shortcuts' }).content, {
	compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText

class Element {
	constructor(tagName = 'CANVAS', editable = false) {
		this.tagName = tagName
		this.isContentEditable = editable
		this.focusCount = 0
	}
	closest() {
		return ['INPUT', 'TEXTAREA', 'SELECT', 'BUTTON'].includes(this.tagName) ? this : null
	}
	focus() {
		this.focusCount++
	}
}

function editor() {
	let pixels = new Uint8ClampedArray(64 * 64 * 4)
	const context = {
		getImageData: () => ({ data: pixels.slice(), width: 64, height: 64 }),
		putImageData: (image) => {
			pixels = image.data.slice()
		},
	}
	const exports = {}
	runInNewContext(code, {
		exports,
		Element,
		HTMLElement: Element,
		Uint8ClampedArray,
		document: { createElement: () => ({ getContext: () => context }) },
		require(id) {
			if (id === 'vue')
				return { ...vue, useTemplateRef: () => vue.shallowRef(null), onMounted() {} }
			if (id === 'vue-i18n') return { useI18n: () => ({ t: (key) => key }) }
			if (id === 'vue-router') return { onBeforeRouteLeave() {} }
			if (id.endsWith('/skin-paint'))
				return {
					SKIN_SIZE: 64,
					SKIN_PARTS: ['head'],
					skinFaceMask: () => Array(64 * 64).fill(1),
					partForRegion: () => 'head',
					visitLine: (_, end, visit) => visit(end.x, end.y),
				}
			return {}
		},
	})
	const state = exports.default.setup({ templates: [] }, { expose() {}, emit() {} })
	state.loading.value = false
	return { state, pixels: () => pixels }
}

function key(overrides = {}) {
	const target = new Element()
	return {
		key: 'я',
		code: 'KeyZ',
		ctrlKey: true,
		metaKey: false,
		shiftKey: false,
		altKey: false,
		defaultPrevented: false,
		isComposing: false,
		target,
		composedPath() {
			return [this.target]
		},
		preventDefault() {
			this.defaultPrevented = true
		},
		...overrides,
	}
}

test('physical Ctrl+Z undoes a held stroke and Ctrl+Shift+Z restores it once', () => {
	const { state, pixels } = editor()
	state.startStroke({ x: 8, y: 8 })
	const painted = pixels().slice()
	assert.ok(painted.some((value) => value !== 0))
	const undo = key()
	state.shortcuts(undo)
	assert.equal(undo.defaultPrevented, true)
	assert.ok(pixels().every((value) => value === 0))
	state.drawPixel({ x: 9, y: 8 })
	state.endStroke()
	assert.ok(
		pixels().every((value) => value === 0),
		'held pointer cannot repaint after undo',
	)
	assert.equal(
		state.redoStack.value.length,
		1,
		'pointer release does not add a second history entry',
	)
	state.shortcuts(key({ shiftKey: true }))
	assert.deepEqual(pixels(), painted)
})

test('editing controls, composition, AltGr and handled keys retain their native undo', () => {
	const { state } = editor()
	state.startStroke({ x: 8, y: 8 })
	state.endStroke()
	for (const event of [
		key({ target: new Element('INPUT') }),
		key({ target: new Element('TEXTAREA') }),
		key({ target: new Element('SELECT') }),
		key({ target: new Element('SPAN', true) }),
		key({ isComposing: true }),
		key({ altKey: true }),
		key({ ctrlKey: false }),
		key({ code: 'KeyA', key: 'ф' }),
	]) {
		state.shortcuts(event)
		assert.equal(event.defaultPrevented, false)
		assert.equal(state.undoStack.value.length, 1)
	}
	state.shortcuts(key({ defaultPrevented: true }))
	assert.equal(state.undoStack.value.length, 1)
})

test('command key and physical Russian redo use the existing history', () => {
	const { state, pixels } = editor()
	state.startStroke({ x: 8, y: 8 })
	state.endStroke()
	state.shortcuts(key({ ctrlKey: false, metaKey: true }))
	assert.ok(pixels().every((value) => value === 0))
	state.shortcuts(key({ code: 'KeyY', key: 'н' }))
	assert.ok(pixels().some((value) => value !== 0))
})

test('canvas interaction focuses the scoped editor without stealing control focus', () => {
	const { state } = editor()
	const root = new Element('MAIN')
	state.workshop.value = root
	state.focusWorkshop({ target: new Element('CANVAS') })
	assert.equal(root.focusCount, 1)
	state.focusWorkshop({ target: new Element('INPUT') })
	state.focusWorkshop({ target: new Element('BUTTON') })
	assert.equal(root.focusCount, 1)
	assert.match(descriptor.template.content, /@keydown="shortcuts"/)
	assert.doesNotMatch(source, /(?:window|document)\.addEventListener\(['"]keydown/)
})
