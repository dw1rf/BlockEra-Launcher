import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { runInNewContext } from 'node:vm'

const require = createRequire(import.meta.url)
const vue = require('vue')
const { parse, compileScript } = require('@vue/compiler-sfc')
const ts = require('typescript')
const source = readFileSync(
	new URL('../src/components/ui/settings/StorageUsagePanel.vue', import.meta.url),
	'utf8',
)
const { descriptor } = parse(source)
const script = compileScript(descriptor, { id: 'storage-test' })
const code = ts.transpileModule(script.content, {
	compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText

async function mount(cleanupResult) {
	const calls = []
	const exports = {}
	runInNewContext(code, {
		exports,
		require(id) {
			if (id === 'vue') return vue
			if (id === '@tauri-apps/api/core')
				return {
					invoke: async (command, args) => {
						calls.push({ command, args })
						if (command.endsWith('storage_scan'))
							return {
								preview_id: 'preview-token',
								profiles: [],
								cache_bytes: 50,
								warnings: [],
								candidates: [
									{
										id: 'backup-1',
										kind: 'backup',
										name: 'world.zip',
										profile_name: 'My world',
										relative_path: 'profiles/world/backups/world.zip',
										bytes: 100,
									},
									{
										id: 'cache-2',
										kind: 'cache',
										name: 'archive.mrpack',
										profile_name: null,
										relative_path: 'caches/external-packs/archive.mrpack',
										bytes: 50,
									},
								],
							}
						if (cleanupResult instanceof Error) throw cleanupResult
						return cleanupResult
					},
				}
			throw new Error(`Unexpected import ${id}`)
		},
	})
	const component = exports.default
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
	const app = renderer.createApp(component)
	app.mount({})
	await new Promise((resolve) => setImmediate(resolve))
	return { app, state: app._instance.setupState, calls }
}

test('cleanup requires a selection and review, then submits only selected preview IDs', async () => {
	const { app, state, calls } = await mount({ removed_count: 1, removed_bytes: 100, failures: [] })
	try {
		assert.equal(state.selected.length, 0)
		await state.cleanup()
		assert.equal(calls.length, 1)
		state.selected = ['backup-1']
		await state.cleanup()
		assert.equal(calls.length, 1, 'selection alone does not authorize deletion')
		state.reviewing = true
		state.search = 'archive'
		assert.equal(
			state.selectedFiles[0].id,
			'backup-1',
			'review preserves selected files hidden by filters',
		)
		await state.cleanup()
		assert.equal(calls.length, 2)
		assert.equal(calls[1].command, 'plugin:storage|storage_cleanup')
		assert.equal(calls[1].args.previewId, 'preview-token')
		assert.deepEqual(Array.from(calls[1].args.selectedIds), ['backup-1'])
		assert.equal(state.usage, null)
		assert.equal(state.selected.length, 0)
		assert.equal(state.result.removed_count, 1)
		await state.cleanup()
		assert.equal(calls.length, 2, 'consumed preview cannot be resubmitted')
	} finally {
		app.unmount()
	}
})

for (const outcome of [
	new Error('changed file'),
	{ removed_count: 0, removed_bytes: 0, failures: ['file changed'] },
]) {
	test(`cleanup ${outcome instanceof Error ? 'failure' : 'partial result'} invalidates the preview`, async () => {
		const { app, state, calls } = await mount(outcome)
		try {
			state.selected = ['cache-2']
			state.reviewing = true
			await state.cleanup()
			assert.equal(state.usage, null)
			assert.equal(state.reviewing, false)
			assert.equal(state.cleaning, false)
			assert.equal(state.selected.length, 0)
			if (outcome instanceof Error) assert.match(state.error, /changed file/)
			else assert.equal(state.result.failures.length, 1)
			await state.scan()
			assert.equal(state.result, null)
			assert.equal(state.selected.length, 0)
			assert.equal(calls.length, 3)
		} finally {
			app.unmount()
		}
	})
}
