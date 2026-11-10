import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { runInNewContext } from 'node:vm'

const require = createRequire(import.meta.url)
const vue = require('vue')
const { parse, compileScript } = require('@vue/compiler-sfc')
const ts = require('typescript')

test('creation modal opens, selects Horizon releases and cleans up on header dismissal', async () => {
	let opened = 0
	let hidden = 0
	let createError = null
	let createPending = null
	const created = []
	const installs = []
	const reported = []
	let finishInstallation
	const installation = new Promise((resolve) => {
		finishInstallation = resolve
	})
	let stopped = 0
	let closedSelect = 0
	let state
	const errors = []
	const dialog = {
		setup(_, { expose }) {
			expose({ show: () => opened++, hide: () => hidden++ })
			return () => vue.h('div')
		},
	}
	const source = readFileSync(
		new URL('../src/components/ui/InstanceCreationModal.vue', import.meta.url),
		'utf8',
	)
	const { descriptor } = parse(source)
	const script = compileScript(descriptor, { id: 'creation-modal-test' })
	const code = ts.transpileModule(script.content, {
		compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
	}).outputText
	const exports = {}
	const versions = [
		{ id: 'nightly-a', name: 'Horizon A', game_version: '26.3' },
		{ id: 'nightly-b', name: 'Horizon B', game_version: '26.2' },
	]
	const metadata = {
		get_loader_versions: async () => ({ gameVersions: [] }),
		get_game_versions: async () => ({ versions: [] }),
		get_horizon_versions: async () => versions,
	}
	runInNewContext(code, {
		exports,
		require(id) {
			if (id === 'vue') return vue
			if (id === '@modrinth/ui')
				return {
					injectNotificationManager: () => ({
						handleError: (error) => {
							reported.push(error)
						},
					}),
				}
			if (id === '@tauri-apps/api/webview')
				return { getCurrentWebview: () => ({ onDragDropEvent: async () => () => stopped++ }) }
			if (id.endsWith('/profile'))
				return {
					create: async (...args) => {
						created.push(args)
						if (createError) throw createError
						if (createPending) await createPending
						return 'fixture-profile'
					},
					install: (path, force) => {
						installs.push({ path, force, hidden })
						return installation
					},
				}
			if (id.endsWith('/metadata')) return metadata
			if (id.endsWith('/tags')) return { get_loaders: async () => [] }
			if (id.endsWith('/import.js')) return { get_default_launcher_path: async () => '' }
			if (id.endsWith('/analytics')) return { trackEvent() {} }
			if (id.endsWith('/instance-backgrounds')) return { instanceBackgroundFor: () => '' }
			return {}
		},
	})
	const component = exports.default
	const setup = component.setup
	component.setup = async (props, context) => {
		state = await setup(props, context)
		return state
	}
	component.render = function () {
		return vue.h(dialog, { ref: 'modal' })
	}
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
	const reference = vue.ref(null)
	const app = renderer.createApp({
		render: () =>
			vue.h(vue.Suspense, null, { default: () => vue.h(component, { ref: reference }) }),
	})
	app.config.errorHandler = (error) => errors.push(error)
	app.config.warnHandler = () => {}
	app.mount({})
	let mounted = true
	try {
		for (let attempt = 0; attempt < 10; attempt++)
			await new Promise((resolve) => setImmediate(resolve))
		assert.deepEqual(errors, [], 'component setup must not throw')
		assert.equal(typeof reference.value?.show, 'function')
		await reference.value.show()
		assert.equal(opened, 1)
		state.loader.value = 'horizon'
		await new Promise((resolve) => setImmediate(resolve))
		await vue.nextTick()
		assert.equal(state.horizon_version.value.id, 'nightly-a')
		state.horizonSelect.value = {
			isOpen: true,
			deactivate() {
				closedSelect++
				this.isOpen = false
			},
		}
		const optionClick = {
			target: { closest: () => ({ classList: { contains: () => false } }) },
		}
		// Capture runs before multiselect's update:modelValue listener.
		state.onHorizonOptionClick(optionClick)
		state.horizon_version.value = versions[1]
		await vue.nextTick()
		assert.equal(state.game_version.value, '26.2')
		assert.equal(state.horizon_version.value.id, 'nightly-b')
		assert.equal(closedSelect, 1)
		// Clicking the current option emits no model update with allow-empty=false.
		state.onHorizonOptionClick(optionClick)
		await vue.nextTick()
		assert.equal(closedSelect, 2)
		assert.equal(state.horizon_version.value.id, 'nightly-b')
		state.horizonSelect.value.isOpen = true
		state.onHorizonOptionKeypress()
		await vue.nextTick()
		assert.equal(closedSelect, 3)
		assert.equal(state.horizon_version.value.id, 'nightly-b')
		state.onHorizonOptionKeypress()
		await vue.nextTick()
		assert.equal(closedSelect, 3, 'Enter on a closed picker must not schedule another close')
		state.horizonSelect.value.isOpen = true
		state.onHorizonOptionKeypress()
		state.horizon_version.value = versions[0]
		await vue.nextTick()
		assert.equal(state.game_version.value, '26.3')
		assert.equal(closedSelect, 4)
		state.onModalHidden()
		assert.equal(state.isShowing.value, false)
		assert.equal(stopped, 1)
		assert.equal(closedSelect, 5)
		state.onModalHidden()
		assert.equal(stopped, 1, 'header dismissal must release the drop listener only once')
		await reference.value.show()
		await state.create_instance()
		assert.equal(created.length, 0, 'invalid form must not create or dismiss')
		assert.equal(hidden, 0)
		state.loader.value = 'horizon'
		state.profile_name.value = 'Horizon fixture'
		await vue.nextTick()
		createError = new Error('Profile creation failed')
		await state.create_instance()
		assert.equal(hidden, 0, 'creation failure keeps the form open')
		assert.equal(installs.length, 0)
		assert.equal(reported[0], createError)
		createError = null
		await state.create_instance()
		assert.equal(created.at(-1)[5], true, 'profile creation must skip the long installation')
		assert.equal(hidden, 1)
		assert.equal(state.isShowing.value, false)
		assert.equal(state.creating.value, false)
		assert.deepEqual(
			installs,
			[{ path: 'fixture-profile', force: false, hidden: 1 }],
			'background install starts after dismissal and does not block create completion',
		)
		finishInstallation()
		await reference.value.show()
		state.loader.value = 'horizon'
		state.profile_name.value = 'Pending profile'
		await vue.nextTick()
		let finishCreate
		createPending = new Promise((resolve) => {
			finishCreate = resolve
		})
		const pendingCreation = state.create_instance()
		app.unmount()
		mounted = false
		finishCreate()
		await pendingCreation
		assert.equal(hidden, 1, 'late completion must not dereference a destroyed modal')
		assert.equal(installs.length, 2, 'accepted profiles still install after the form is gone')
		assert.equal(reported.length, 1, 'unmount must not produce a null hide error')
	} finally {
		if (mounted) app.unmount()
	}
})

test('Horizon options avoid label activation and modal close controls stay outside drag regions', () => {
	const { parse: parseTemplate } = require('@vue/compiler-dom')
	function elements(template) {
		const result = []
		function walk(node, ancestors = []) {
			if (node.type === 1) result.push({ node, ancestors })
			for (const child of node.children ?? [])
				walk(child, node.type === 1 ? [...ancestors, node] : ancestors)
		}
		walk(parseTemplate(template))
		return result
	}
	const hasAttribute = (node, name, value) =>
		node.props.some(
			(prop) =>
				prop.type === 6 &&
				prop.name === name &&
				(value === undefined || prop.value?.content === value),
		)
	const hasHandler = (node, event, modifier) =>
		node.props.some(
			(prop) =>
				prop.type === 7 &&
				prop.name === 'on' &&
				prop.arg?.content === event &&
				prop.modifiers.some(
					(item) => (typeof item === 'string' ? item : item.content) === modifier,
				),
		)
	const creation = parse(
		readFileSync(
			new URL('../src/components/ui/InstanceCreationModal.vue', import.meta.url),
			'utf8',
		),
	).descriptor.template.content
	const picker = elements(creation).find(
		({ node }) =>
			node.tag === 'multiselect' && hasAttribute(node, 'id', 'creation-horizon-version'),
	)
	assert.ok(picker)
	assert.equal(
		picker.ancestors.some((node) => node.tag === 'label'),
		false,
		'wrapping label would synthesize another input click after choosing a release',
	)
	assert.ok(picker.ancestors.some((node) => hasHandler(node, 'click', 'capture')))
	assert.ok(picker.ancestors.some((node) => hasHandler(node, 'keypress', 'capture')))
	const dialog = elements(creation).find(({ node }) => node.tag === 'ModalWrapper').node
	assert.ok(
		dialog.props.some(
			(prop) =>
				prop.type === 7 &&
				prop.name === 'bind' &&
				prop.arg?.content === 'on-hide' &&
				prop.exp?.content === 'onModalHidden',
		),
	)

	const modal = parse(
		readFileSync(
			new URL('../../../packages/ui/src/components/modal/NewModal.vue', import.meta.url),
			'utf8',
		),
	).descriptor.template.content
	const closeButtons = elements(modal).filter(
		({ node }) => node.tag === 'button' && hasAttribute(node, 'aria-label', 'Закрыть'),
	)
	assert.equal(closeButtons.length, 2)
	for (const { node, ancestors } of closeButtons) {
		assert.equal(
			ancestors.some((parent) => hasAttribute(parent, 'data-tauri-drag-region')),
			false,
		)
		assert.ok(hasAttribute(node, 'data-tauri-drag-region-exclude'))
		assert.ok(hasHandler(node, 'mousedown', 'stop'))
		assert.ok(hasHandler(node, 'pointerdown', 'stop'))
		assert.ok(hasHandler(node, 'click', 'stop'))
	}
})
