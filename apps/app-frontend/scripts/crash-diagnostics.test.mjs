import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { runInNewContext } from 'node:vm'
const require = createRequire(import.meta.url)
const vue = require('vue')
const { parse, compileScript } = require('@vue/compiler-sfc')
const ts = require('typescript')
const flush = async () => {
	for (let i = 0; i < 8; i++) await new Promise((resolve) => setImmediate(resolve))
}
for (const delayed of [false, true])
	test(`global crash component mounts and cleans listener (delayed=${delayed})`, async () => {
		let callback,
			resolveListener,
			stopped = 0,
			opened = 0,
			read = 0
		const errors = []
		const source = readFileSync(
			new URL('../src/components/ui/CrashDiagnostics.vue', import.meta.url),
			'utf8',
		)
		const script = compileScript(parse(source).descriptor, { id: 'crash-smoke' })
		const code = ts.transpileModule(script.content, {
			compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
		}).outputText
		const exports = {}
		runInNewContext(code, {
			exports,
			console,
			require(id) {
				if (id === 'vue') return vue
				if (id === 'vue-router') return { useRouter: () => ({ push() {} }) }
				if (id.endsWith('events.js'))
					return {
						process_listener: (cb) => {
							callback = cb
							return delayed
								? new Promise((resolve) => {
										resolveListener = resolve
									})
								: Promise.resolve(() => stopped++)
						},
					}
				if (id.endsWith('logs.js'))
					return {
						get_output_by_filename: async () => {
							read++
							return ''
						},
					}
				if (id.endsWith('profile.js')) return { get: async () => ({ name: 'Test' }) }
				if (id.endsWith('crash-diagnostics')) return { diagnoseCrash: () => ({ title: 'Failure' }) }
				return {}
			},
		})
		const dialog = {
			setup(_, { expose }) {
				expose({ show: () => opened++, hide() {} })
				return () => vue.h('div')
			},
		}
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
		const app = renderer.createApp(component)
		app.config.errorHandler = (error) => errors.push(error)
		app.config.warnHandler = () => {}
		app.mount({})
		await flush()
		assert.equal(typeof callback, 'function')
		for (const exit of [undefined, { success: true }, { user_requested_stop: true }])
			callback({ event: 'finished', uuid: 'ignored', exit })
		await flush()
		assert.equal(read, 0)
		const failure = {
			event: 'finished',
			uuid: 'first',
			profile_path_id: 'profile',
			exit: { code: 1, success: false, user_requested_stop: false },
		}
		callback(failure)
		callback(failure)
		await flush()
		assert.equal(opened, 1)
		assert.equal(read, 1)
		callback({ ...failure, uuid: 'second' })
		await flush()
		assert.equal(opened, 1, 'second crash queues while first dialog open')
		assert.equal(read, 2)
		assert.deepEqual(errors, [])
		app.unmount()
		if (delayed) resolveListener(() => stopped++)
		await flush()
		assert.equal(stopped, 1)
		callback({ ...failure, uuid: 'after-unmount' })
		await flush()
		assert.equal(read, 2)
	})
