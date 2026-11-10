import assert from 'node:assert/strict'
import { test } from 'node:test'
import { diagnoseCrash, redactDiagnosticReport, crashReport } from './crash-diagnostics.ts'
const failed = { code: 1, success: false, user_requested_stop: false }
test('normal exit and intentional stop never report a crash', () => {
	for (const exit of [
		{ ...failed, success: true },
		{ ...failed, user_requested_stop: true },
	])
		assert.equal(diagnoseCrash('OutOfMemoryError', exit), null)
	assert.equal(diagnoseCrash('OutOfMemoryError\n# Process exited with status: exit code: 0'), null)
	assert.equal(diagnoseCrash('# Process exited with status: 1\n# User requested stop: true'), null)
	assert.equal(diagnoseCrash('[ERROR] ordinary handled error'), null)
})
test('unknown failures do not accuse informational mod mentions', () => {
	const result = diagnoseCrash('[INFO] loaded entrypoint provided by healthy_mod', failed)!
	assert.equal(result.confidence, 'Низкая')
	assert.deepEqual(result.suspectedMods, [])
})
test('fatal section wins over historical errors, attribution remains conditional', () => {
	const result = diagnoseCrash(
		'OutOfMemoryError handled\nException in thread "main" Could not execute entrypoint provided by broken_mod',
		failed,
	)!
	assert.equal(result.confidence, 'Средняя')
	assert.deepEqual(result.suspectedMods, ['broken_mod'])
	assert.equal(
		diagnoseCrash('java.lang.OutOfMemoryError: Java heap space', failed)?.confidence,
		'Высокая',
	)
})
test('reports redact tokens, credentials, usernames and network details', () => {
	const raw = String.raw`--accessToken secret123 password="two words" Authorization: Bearer bearerSecret token=genericSecret C:\Users\Alex Doe\logs /home/alex/log 192.168.1.2 alex@example.com`
	const report = crashReport(diagnoseCrash('', failed)!, 'Test', raw)
	for (const secret of [
		'secret123',
		'two words',
		'bearerSecret',
		'genericSecret',
		'Alex Doe',
		'/alex/',
		'192.168.1.2',
		'alex@example.com',
	])
		assert.equal(report.includes(secret), false, secret)
	assert.equal(redactDiagnosticReport('ordinary error'), 'ordinary error')
})
