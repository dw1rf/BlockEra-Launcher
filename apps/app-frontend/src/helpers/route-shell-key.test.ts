import { describe, expect, it } from 'vitest'

import { getRouteShellKey } from './route-shell-key'

describe('getRouteShellKey', () => {
	it('keeps one instance shell across its tabs', () => {
		expect(getRouteShellKey({ path: '/instance/industrial', params: { id: 'industrial' } })).toBe(
			getRouteShellKey({ path: '/instance/industrial/worlds', params: { id: 'industrial' } }),
		)
	})

	it('separates an instance shell from a project shell', () => {
		expect(
			getRouteShellKey({ path: '/instance/industrial', params: { id: 'industrial' } }),
		).not.toBe(getRouteShellKey({ path: '/project/fabric-api', params: { id: 'fabric-api' } }))
	})
})
