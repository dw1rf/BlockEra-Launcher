export const formatDownloadBytes = (bytes: unknown): string => {
	const value = Number(bytes ?? 0)
	if (!Number.isFinite(value) || value <= 0) return '0 Б'

	const units = ['Б', 'КБ', 'МБ', 'ГБ']
	const unitIndex = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
	const scaled = value / 1024 ** unitIndex
	const digits = unitIndex === 0 || scaled >= 100 ? 0 : scaled >= 10 ? 1 : 2
	return `${scaled.toFixed(digits)} ${units[unitIndex]}`
}

export const formatDownloadSpeed = (bytesPerSecond: unknown): string => {
	const value = Number(bytesPerSecond ?? 0)
	return Number.isFinite(value) && value > 0 ? `${formatDownloadBytes(value)}/с` : '—'
}

type AnchorRect = Pick<DOMRect, 'bottom' | 'right'>

export const calculateDownloadPopoverPosition = (
	anchor: AnchorRect,
	viewportWidth: number,
	viewportHeight: number,
	preferredWidth = 320,
	viewportMargin = 12,
	gap = 10,
) => {
	const width = Math.max(0, Math.min(preferredWidth, viewportWidth - viewportMargin * 2))
	const left = Math.min(
		Math.max(viewportMargin, anchor.right - width),
		viewportWidth - width - viewportMargin,
	)
	const viewportBottom = Math.max(viewportMargin, viewportHeight - viewportMargin)
	const top = Math.min(Math.max(viewportMargin, anchor.bottom + gap), viewportBottom)

	return {
		top,
		left,
		width,
		maxHeight: Math.max(0, viewportBottom - top),
	}
}
