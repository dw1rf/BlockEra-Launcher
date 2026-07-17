import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const backend = vi.hoisted(() => ({ get: vi.fn(), set: vi.fn() }))

vi.mock('@/helpers/settings', () => ({ get: backend.get, set: backend.set }))

const initialSettings = {
	locale: 'en-US',
	theme: 'dark',
	default_page: 'home',
}

describe('useAppSettings', () => {
	beforeEach(() => {
		vi.resetModules()
		vi.clearAllMocks()
		backend.get.mockResolvedValue(structuredClone(initialSettings))
	})

	afterEach(() => vi.useRealTimers())

	it('debounces rapid writes of the same key and persists the latest value', async () => {
		vi.useFakeTimers()
		const { useAppSettings } = await import('./use-app-settings')
		const { saveKey } = await useAppSettings()

		const first = saveKey('locale', 'ru', 250)
		const second = saveKey('locale', 'ru-RU', 250)
		await vi.advanceTimersByTimeAsync(250)
		await Promise.all([first, second])

		expect(backend.set).toHaveBeenCalledOnce()
		expect(backend.set.mock.calls[0][0]).toMatchObject({ locale: 'ru-RU' })
	})

	it('serializes writes so backend snapshots never race', async () => {
		let activeWrites = 0
		let maximumActiveWrites = 0
		backend.set.mockImplementation(async () => {
			activeWrites += 1
			maximumActiveWrites = Math.max(maximumActiveWrites, activeWrites)
			await Promise.resolve()
			activeWrites -= 1
		})
		const { useAppSettings } = await import('./use-app-settings')
		const { saveKey } = await useAppSettings()

		await Promise.all([saveKey('locale', 'ru-RU'), saveKey('theme', 'oled')])

		expect(maximumActiveWrites).toBe(1)
		expect(backend.set).toHaveBeenCalledTimes(2)
		expect(backend.set.mock.calls[1][0]).toMatchObject({ locale: 'ru-RU', theme: 'oled' })
	})
})
