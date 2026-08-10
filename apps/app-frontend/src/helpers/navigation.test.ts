import { afterEach, describe, expect, it, vi } from 'vitest'

import { navigateHistory } from './navigation'

const router = () => ({
	back: vi.fn(),
	forward: vi.fn(),
	replace: vi.fn(),
})

afterEach(() => vi.unstubAllGlobals())

describe('navigateHistory', () => {
	it('performs exactly one native history action when an entry exists', () => {
		vi.stubGlobal('window', { history: { state: { back: '/instance/a/content' } } })
		const target = router()

		navigateHistory(target as never, 'back', '/browse/mod')

		expect(target.back).toHaveBeenCalledOnce()
		expect(target.forward).not.toHaveBeenCalled()
		expect(target.replace).not.toHaveBeenCalled()
	})

	it('uses the safe fallback without attempting an empty history move', () => {
		vi.stubGlobal('window', { history: { state: { back: null } } })
		const target = router()

		navigateHistory(target as never, 'back', '/instance/a/content')

		expect(target.back).not.toHaveBeenCalled()
		expect(target.replace).toHaveBeenCalledOnce()
		expect(target.replace).toHaveBeenCalledWith('/instance/a/content')
	})

	it('does nothing when forward history and a fallback are both absent', () => {
		vi.stubGlobal('window', { history: { state: {} } })
		const target = router()

		navigateHistory(target as never, 'forward')

		expect(target.forward).not.toHaveBeenCalled()
		expect(target.replace).not.toHaveBeenCalled()
	})
})
