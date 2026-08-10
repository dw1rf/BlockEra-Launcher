import { afterEach, describe, expect, it, vi } from 'vitest'

import { blockeraFetch, MODRINTH_CONNECTION_ERROR } from './fetch.js'

describe('blockeraFetch', () => {
	afterEach(() => {
		vi.unstubAllGlobals()
	})

	it('uses the WebView fetch implementation', async () => {
		const response = new Response('{}', { status: 200 })
		const fetch = vi.fn().mockResolvedValue(response)
		vi.stubGlobal('fetch', fetch)

		await expect(blockeraFetch('https://api.modrinth.com/v2/search')).resolves.toBe(response)
		expect(fetch).toHaveBeenCalledWith('https://api.modrinth.com/v2/search', {})
	})

	it('keeps Modrinth connection errors user friendly', async () => {
		vi.spyOn(console, 'error').mockImplementation(() => undefined)
		vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('network timeout')))

		await expect(blockeraFetch('https://api.modrinth.com/v2/search')).rejects.toThrow(
			MODRINTH_CONNECTION_ERROR,
		)
	})
})
