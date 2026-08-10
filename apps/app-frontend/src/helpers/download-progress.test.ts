import { describe, expect, it } from 'vitest'

import {
	calculateDownloadPopoverPosition,
	formatDownloadBytes,
	formatDownloadSpeed,
} from './download-progress'

describe('download progress formatting', () => {
	it('formats transferred bytes and speed using binary units', () => {
		expect(formatDownloadBytes(0)).toBe('0 Б')
		expect(formatDownloadBytes(1536)).toBe('1.50 КБ')
		expect(formatDownloadSpeed(2 * 1024 * 1024)).toBe('2.00 МБ/с')
		expect(formatDownloadSpeed(undefined)).toBe('—')
	})

	it('keeps the popover inside the viewport', () => {
		expect(calculateDownloadPopoverPosition({ right: 790, bottom: 70 }, 800, 600)).toEqual({
			top: 80,
			left: 468,
			width: 320,
			maxHeight: 508,
		})

		expect(calculateDownloadPopoverPosition({ right: 50, bottom: 580 }, 300, 600)).toEqual({
			top: 588,
			left: 12,
			width: 276,
			maxHeight: 0,
		})

		const bottomEdge = calculateDownloadPopoverPosition({ right: 50, bottom: 580 }, 300, 600)
		expect(bottomEdge.top + bottomEdge.maxHeight).toBeLessThanOrEqual(588)
	})
})
