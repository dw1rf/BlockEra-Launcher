import { describe, expect, it } from 'vitest'

import { installActionPending, nextInstallActionState } from './install-action-state'

describe('install action state', () => {
	it('never treats cancellation as success or leaves loading active', () => {
		let state = nextInstallActionState('idle', 'request')
		expect(installActionPending(state)).toBe(true)
		state = nextInstallActionState(state, 'cancel')
		expect(state).toBe('idle')
		expect(installActionPending(state)).toBe(false)
	})

	it('supports an error and retry cycle', () => {
		let state = nextInstallActionState('awaiting-confirmation', 'confirm')
		expect(state).toBe('installing')
		state = nextInstallActionState(state, 'fail')
		expect(state).toBe('error')
		expect(installActionPending(state)).toBe(false)
		state = nextInstallActionState(state, 'retry')
		expect(state).toBe('awaiting-confirmation')
	})

	it('only reaches success after backend confirmation', () => {
		const installing = nextInstallActionState('awaiting-confirmation', 'confirm')
		expect(installing).toBe('installing')
		expect(nextInstallActionState(installing, 'succeed')).toBe('success')
	})
})
