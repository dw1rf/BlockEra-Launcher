import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { runInNewContext } from 'node:vm'

const require = createRequire(import.meta.url)
const vue = require('vue')
const { parse, compileScript } = require('@vue/compiler-sfc')
const ts = require('typescript')

test('creation modal initializes and its exposed show method opens the dialog', async () => {
	let opened = 0
	const errors = []
	const dialog = {
		setup(_, { expose }) {
			expose({ show: () => opened++, hide() {} })
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
	const metadata = {
		get_loader_versions: async () => ({ gameVersions: [] }),
		get_game_versions: async () => ({ versions: [] }),
		get_horizon_versions: async () => [],
	}
	runInNewContext(code, {
		exports,
		require(id) {
			if (id === 'vue') return vue
			if (id === '@modrinth/ui')
				return {
					injectNotificationManager: () => ({
						handleError: (error) => {
							throw error
						},
					}),
				}
			if (id === '@tauri-apps/api/webview')
				return { getCurrentWebview: () => ({ onDragDropEvent: async () => () => {} }) }
			if (id.endsWith('/metadata')) return metadata
			if (id.endsWith('/tags')) return { get_loaders: async () => [] }
			if (id.endsWith('/import.js')) return { get_default_launcher_path: async () => '' }
			if (id.endsWith('/analytics')) return { trackEvent() {} }
			if (id.endsWith('/instance-backgrounds')) return { instanceBackgroundFor: () => '' }
			return {}
		},
	})
	const component = exports.default
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
	try {
		for (let attempt = 0; attempt < 10; attempt++)
			await new Promise((resolve) => setImmediate(resolve))
		assert.deepEqual(errors, [], 'component setup must not throw')
		assert.equal(typeof reference.value?.show, 'function')
		await reference.value.show()
		assert.equal(opened, 1)
	} finally {
		app.unmount()
	}
})
