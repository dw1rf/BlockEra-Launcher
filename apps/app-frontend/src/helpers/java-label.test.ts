import { describe, expect, it } from 'vitest'

import { formatJavaLabel } from './java-label'

describe('formatJavaLabel', () => {
	it('formats the descriptor returned by the desktop backend', () => {
		expect(
			formatJavaLabel({
				parsed_version: 21,
				version: '21.0.7',
				architecture: 'x64',
				path: 'C:\\Java\\bin\\java.exe',
			}),
		).toBe('Java 21')
	})

	it('formats primitive versions and keeps a safe fallback', () => {
		expect(formatJavaLabel('17.0.12')).toBe('Java 17')
		expect(formatJavaLabel(null)).toBe('Автоматический выбор')
		expect(formatJavaLabel({ version: 'unknown' })).toBe('Автоматический выбор')
	})
})
