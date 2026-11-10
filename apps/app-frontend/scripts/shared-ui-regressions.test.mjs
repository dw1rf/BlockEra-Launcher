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

function evaluate(source, imports) {
	const exports = {}
	const code = ts.transpileModule(source, {
		compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
	}).outputText
	runInNewContext(code, { exports, require: (id) => imports[id] ?? {}, Error, Date, Intl })
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
			'@modrinth/ui': { injectNotificationManager: () => ({ handleError: (error) => errors.push(error) }) },
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
			'@/composables/useMemorySlider': { default: async () => ({ maxMemory: 8192, snapPoints: [] }) },
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
