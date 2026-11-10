import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { runInNewContext } from 'node:vm'

const require = createRequire(import.meta.url)
const vue = require('vue')
const ts = require('typescript')
const { parse, compileScript } = require('@vue/compiler-sfc')
const sharedRoot = new URL('../../../packages/ui/src/', import.meta.url)

function evaluate(source, imports, globals = {}) {
	const exports = {}
	const code = ts.transpileModule(source, {
		compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
	}).outputText
	runInNewContext(code, {
		exports,
		require: (id) => imports[id] ?? {},
		Error,
		Date,
		Intl,
		...globals,
	})
	return exports
}

test('notification errors normalize thrown strings and serialized backend messages', () => {
	const source = readFileSync(new URL('providers/web-notifications.ts', sharedRoot), 'utf8')
	const { AbstractWebNotificationManager } = evaluate(source, { '.': { createContext: () => [] } })
	const manager = new AbstractWebNotificationManager()
	const notifications = []
	manager.addNotification = (notification) => notifications.push(notification)
	for (const error of ['failed download', { message: 'file locked' }, new Error('network'), null])
		manager.handleError(error)
	assert.deepEqual(
		notifications.map((item) => item.text),
		['failed download', 'file locked', 'network', 'null'],
	)
})

function mountPayment(stripe) {
	const source = readFileSync(
		new URL('components/billing/AddPaymentMethod.vue', sharedRoot),
		'utf8',
	)
	const { descriptor } = parse(source)
	const { default: component } = evaluate(
		compileScript(descriptor, { id: 'payment-test' }).content,
		{
			vue,
			'@stripe/stripe-js': { loadStripe: async () => stripe },
			'@modrinth/utils': {
				createStripeElements: () => ({
					elements: {},
					paymentElement: { on() {} },
					addressElement: { on() {} },
				}),
			},
		},
	)
	component.render = () => vue.h('div')
	const errors = []
	const events = []
	const renderer = vue.createRenderer({
		createElement: () => ({}),
		createText: () => ({}),
		createComment: () => ({}),
		insert() {},
		remove() {},
		setText() {},
		setElementText() {},
		patchProp() {},
		parentNode: () => null,
		nextSibling: () => null,
	})
	const app = renderer.createApp(component, {
		publishableKey: 'test',
		returnUrl: 'https://example.invalid/return',
		createSetupIntent: async () => ({ client_secret: 'fixture' }),
		onError: (error) => errors.push(error),
		onStartLoading: () => events.push('start'),
		onStopLoading: () => events.push('stop'),
	})
	app.mount({})
	return { app, state: app._instance.setupState, errors, events }
}

test('unavailable payment SDK reports an Error and marks the form failed', async () => {
	const { app, state, errors } = mountPayment(null)
	try {
		await state.reload([])
		assert.equal(state.error, true)
		assert.equal(errors.length, 1)
		assert.ok(errors[0] instanceof Error)
		assert.equal(await state.submit(), false)
	} finally {
		app.unmount()
	}
})

test('payment validation and network failures return false and stop loading', async () => {
	for (const outcome of [{ error: { type: 'validation_error' } }, new Error('offline')]) {
		const { app, state, events } = mountPayment({
			confirmSetup: async () => {
				if (outcome instanceof Error) throw outcome
				return outcome
			},
		})
		try {
			await state.reload([])
			assert.equal(await state.submit(), false)
			assert.deepEqual(events, ['start', 'stop'])
		} finally {
			app.unmount()
		}
	}
})

test('default window and hook settings save once when saveKey replaces their containers', async () => {
	const settings = vue.ref({
		extra_launch_args: [],
		custom_env_vars: [],
		force_fullscreen: false,
		game_resolution: [1280, 720],
		memory: { maximum: 4096 },
		hooks: {},
	})
	const saves = []
	const errors = []
	const source = readFileSync(
		new URL('../src/components/ui/settings/DefaultInstanceSettings.vue', import.meta.url),
		'utf8',
	)
	const { descriptor } = parse(source)
	const { default: component } = evaluate(
		compileScript(descriptor, { id: 'default-settings-test' }).content,
		{
			vue,
			'@modrinth/ui': {
				injectNotificationManager: () => ({ handleError: (error) => errors.push(error) }),
			},
			'@/composables/use-app-settings': {
				useAppSettings: async () => ({
					settings,
					saveKey: async (key, value) => {
						saves.push({ key, value: JSON.parse(JSON.stringify(value)) })
						// Production saveKey replaces the reactive container before debouncing.
						settings.value[key] = value
					},
				}),
			},
			'@/composables/useMemorySlider': {
				default: async () => ({ maxMemory: 8192, snapPoints: [] }),
			},
		},
	)
	component.render = () => vue.h('div')
	const renderer = vue.createRenderer({
		createElement: () => ({}),
		createText: () => ({}),
		createComment: () => ({}),
		insert() {},
		remove() {},
		setText() {},
		setElementText() {},
		patchProp() {},
		parentNode: () => null,
		nextSibling: () => null,
	})
	const app = renderer.createApp({
		render: () => vue.h(vue.Suspense, null, { default: () => vue.h(component) }),
	})
	app.config.errorHandler = (error) => errors.push(error)
	app.config.warnHandler = () => {}
	app.mount({})
	try {
		for (let i = 0; i < 5; i++) await new Promise((resolve) => setImmediate(resolve))
		assert.deepEqual(errors, [])
		assert.equal(saves.length, 0)
		settings.value.game_resolution[0] = 1920
		await vue.nextTick()
		assert.deepEqual(saves, [{ key: 'game_resolution', value: [1920, 720] }])
		settings.value.game_resolution[1] = 1080
		await vue.nextTick()
		assert.equal(saves.length, 2)
		assert.deepEqual(saves[1], { key: 'game_resolution', value: [1920, 1080] })
		settings.value.hooks.pre_launch = 'echo launch'
		await vue.nextTick()
		assert.equal(saves.length, 3)
		assert.deepEqual(saves[2], { key: 'hooks', value: { pre_launch: 'echo launch' } })
		settings.value.hooks.pre_launch = ''
		await vue.nextTick()
		assert.equal(saves.length, 4, 'normalizing an empty hook must not schedule another save')
		assert.deepEqual(saves[3], { key: 'hooks', value: {} })
		settings.value.game_resolution = [1920, 1080]
		settings.value.hooks = {}
		await vue.nextTick()
		assert.equal(saves.length, 4, 'equal replacement containers must not trigger writes')
		assert.deepEqual(errors, [])
	} finally {
		app.unmount()
	}
})

for (const insideModal of [true, false])
	test(`combobox opens in its owning layer and selects an option (modal=${insideModal})`, async () => {
		const source = readFileSync(new URL('components/base/Combobox.vue', sharedRoot), 'utf8')
		const { descriptor } = parse(source)
		const { default: component } = evaluate(
			compileScript(descriptor, { id: 'combobox-layer-test' }).content,
			{ vue, '@vueuse/core': { onClickOutside() {} } },
			{
				window: {
					innerWidth: 1200,
					innerHeight: 800,
					addEventListener() {},
					removeEventListener() {},
				},
				requestAnimationFrame: () => 1,
				cancelAnimationFrame() {},
			},
		)
		component.render = () => vue.h('div')
		const renderer = vue.createRenderer({
			createElement: () => ({}),
			createText: () => ({}),
			createComment: () => ({}),
			insert() {},
			remove() {},
			setText() {},
			setElementText() {},
			patchProp() {},
			parentNode: () => null,
			nextSibling: () => null,
		})
		const selected = []
		const errors = []
		const options = [
			{ value: 'home', label: 'Главная' },
			{ value: 'library', label: 'Библиотека' },
		]
		const app = renderer.createApp(component, {
			modelValue: 'home',
			options,
			'onUpdate:modelValue': (value) => selected.push(value),
		})
		app.config.errorHandler = (error) => errors.push(error)
		app.mount({})
		const state = app._instance.setupState
		const modal = vue.markRaw({})
		state.containerRef = {
			closest: (selector) => {
				assert.equal(selector, '.modal-container')
				return insideModal ? modal : null
			},
		}
		state.triggerRef = {
			getBoundingClientRect: () => ({ top: 300, bottom: 340, left: 600, width: 160 }),
			focus() {},
		}
		state.dropdownRef = { getBoundingClientRect: () => ({ height: 130, width: 160 }) }
		try {
			await state.openDropdown()
			assert.equal(state.isOpen, true)
			assert.equal(state.teleportTarget, insideModal ? modal : '#teleports')
			assert.deepEqual(
				[...state.filteredOptions].map((option) => option.label),
				['Главная', 'Библиотека'],
			)
			assert.equal(state.dropdownStyle.top, '340px')
			assert.equal(state.dropdownStyle.left, '600px')
			state.handleDropdownKeydown({ key: 'ArrowDown', preventDefault() {} })
			state.handleDropdownKeydown({ key: 'Enter', preventDefault() {} })
			await vue.nextTick()
			assert.deepEqual(selected, ['library'])
			assert.equal(state.isOpen, false)
			await state.openDropdown()
			state.handleOptionClick(options[0], 0)
			await vue.nextTick()
			assert.deepEqual(selected, ['library', 'home'])
			assert.equal(state.isOpen, false)
			assert.deepEqual(errors, [])
		} finally {
			app.unmount()
		}
	})

test('native drag overlay is below modal controls and modal combobox escapes the global popover layer', () => {
	const globals = readFileSync(
		new URL('../src/assets/stylesheets/global.scss', import.meta.url),
		'utf8',
	)
	const variables = new Map(
		[...globals.matchAll(/(--blockera-layer-[\w-]+):\s*(\d+)/g)].map((match) => [
			match[1],
			Number(match[2]),
		]),
	)
	function layer(expression) {
		const resolved = expression.replace(/var\((--[\w-]+),\s*(\d+)\)/g, (_, name, fallback) =>
			String(variables.get(name) ?? Number(fallback)),
		)
		const subtraction = resolved.match(/^calc\((\d+)\s*-\s*(\d+)\)$/)
		return subtraction ? Number(subtraction[1]) - Number(subtraction[2]) : Number(resolved)
	}
	const modal = readFileSync(new URL('components/modal/NewModal.vue', sharedRoot), 'utf8')
	const overlayLayer = modal.match(/\.tauri-overlay\s*\{[\s\S]*?z-index:\s*([^;]+);/)[1]
	assert.ok(
		layer(overlayLayer) < variables.get('--blockera-layer-modal'),
		'the native dragging overlay must not intercept close buttons in the top 100 pixels',
	)
	assert.ok(layer(overlayLayer) > variables.get('--blockera-layer-modal-backdrop'))
	assert.ok(variables.get('--blockera-layer-popover') < variables.get('--blockera-layer-modal'))
	const combobox = parse(
		readFileSync(new URL('components/base/Combobox.vue', sharedRoot), 'utf8'),
	).descriptor
	assert.match(combobox.template.content, /<Teleport\s+:to="teleportTarget"/)
	assert.match(
		combobox.template.content,
		/class="pointer-events-auto fixed/,
		'popup must restore pointer events inherited from modal-container',
	)
})
