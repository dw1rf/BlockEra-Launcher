import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import test from 'node:test'
import { runInNewContext } from 'node:vm'

const require = createRequire(import.meta.url)
const ts = require('typescript')
function helpers(savePath = 'C:/skins/edited.png', failure) {
	const calls = []
	const dialogs = []
	const exports = {}
	const source = readFileSync(new URL('../src/helpers/skins.ts', import.meta.url), 'utf8')
	const code = ts.transpileModule(source, {
		compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
	}).outputText
	runInNewContext(code, {
		exports,
		Uint8Array,
		Error,
		require(id) {
			if (id === '@tauri-apps/plugin-dialog')
				return {
					save: async (options) => {
						dialogs.push(options)
						return savePath
					},
				}
			if (id === '@tauri-apps/api/core')
				return {
					invoke: async (command, args) => {
						calls.push({ command, args: JSON.parse(JSON.stringify(args)) })
						if (failure) throw failure
						if (command.endsWith('normalize_skin_texture')) return [137, 80, 78, 71]
					},
				}
			return {}
		},
	})
	return { ...exports, calls, dialogs }
}

test('skin apply uses JSON byte arrays and retains expected account identity', async () => {
	const api = helpers()
	await api.add_and_equip_custom_skin(
		new Uint8Array([137, 80, 0, 255]),
		'SLIM',
		undefined,
		'account-id',
	)
	assert.deepEqual(api.calls, [
		{
			command: 'plugin:minecraft-skins|add_and_equip_custom_skin',
			args: {
				textureBlob: [137, 80, 0, 255],
				variant: 'SLIM',
				expectedAccount: 'account-id',
			},
		},
	])
	const normalized = await api.normalize_skin_texture(new Uint8Array([137, 80, 78, 71]))
	assert.ok(normalized instanceof Uint8Array)
	assert.deepEqual(api.calls[1].args.texture, [137, 80, 78, 71])
})

test('PNG export invokes native saving only after a path is selected', async () => {
	const cancelled = helpers(null)
	assert.equal(await cancelled.export_skin_png(new Uint8Array([1])), false)
	assert.equal(cancelled.calls.length, 0)
	const api = helpers()
	assert.equal(await api.export_skin_png(new Uint8Array([137, 80])), true)
	assert.equal(api.dialogs[0].filters[0].extensions[0], 'png')
	assert.deepEqual(api.calls, [
		{
			command: 'plugin:minecraft-skins|export_skin_png',
			args: {
				path: 'C:/skins/edited.png',
				textureBlob: [137, 80],
			},
		},
	])
})

test('write/apply failures propagate and serialized backend messages are visible', async () => {
	const failure = { message: 'Account changed' }
	const api = helpers(undefined, failure)
	await assert.rejects(
		api.add_and_equip_custom_skin(new Uint8Array([1]), 'CLASSIC'),
		(error) => error === failure,
	)
	await assert.rejects(api.export_skin_png(new Uint8Array([1])), (error) => error === failure)
	assert.equal(api.skin_error_message(failure, 'fallback'), 'Account changed')
	assert.equal(api.skin_error_message('Permission denied', 'fallback'), 'Permission denied')
	assert.equal(api.skin_error_message(new Error('Disk full'), 'fallback'), 'Disk full')
	assert.equal(api.skin_error_message(null, 'fallback'), 'fallback')
})
