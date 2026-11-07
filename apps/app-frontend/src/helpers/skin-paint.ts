import type { SkinModel } from './skins'

export type SkinLayer = 'base' | 'outer'
export type Pixel = { x: number; y: number }
export const SKIN_SIZE = 64
export const SKIN_PARTS = [
	'head',
	'torso',
	'right-arm',
	'left-arm',
	'right-leg',
	'left-leg',
] as const
export type SkinPart = (typeof SKIN_PARTS)[number]

export function partForRegion(region: number): SkinPart | undefined {
	return SKIN_PARTS[Math.floor((region - 1) / 6)]
}

function faces(x: number, y: number, width: number, height: number, depth: number) {
	return [
		[x + depth, y, width, depth],
		[x + depth + width, y, width, depth],
		[x, y + depth, depth, height],
		[x + depth, y + depth, width, height],
		[x + depth + width, y + depth, depth, height],
		[x + 2 * depth + width, y + depth, width, height],
	]
}

export function resizeSkinArms(data: Uint8ClampedArray, from: SkinModel, to: SkinModel) {
	const oldWidth = from === 'SLIM' ? 3 : 4
	const newWidth = to === 'SLIM' ? 3 : 4
	if (oldWidth === newWidth) return
	const original = data.slice()
	for (const [x, y] of [
		[40, 16],
		[32, 48],
		[40, 32],
		[48, 48],
	]) {
		for (let row = y; row < y + 16; row++) {
			data.fill(0, 4 * (row * SKIN_SIZE + x), 4 * (row * SKIN_SIZE + x + 16))
		}
		const previous = faces(x, y, oldWidth, 12, 4)
		faces(x, y, newWidth, 12, 4).forEach(([left, top, width, height], i) => {
			const [sourceX, sourceY, sourceWidth] = previous[i]
			for (let row = 0; row < height; row++) {
				for (let col = 0; col < width; col++) {
					const from = 4 * ((sourceY + row) * SKIN_SIZE + sourceX + Math.min(col, sourceWidth - 1))
					const to = 4 * ((top + row) * SKIN_SIZE + left + col)
					data.set(original.subarray(from, from + 4), to)
				}
			}
		})
	}
}

// Each face gets its own region so a fill cannot leak across UV seams.
export function skinFaceMask(variant: SkinModel, layer: SkinLayer): Uint8Array {
	const mask = new Uint8Array(SKIN_SIZE * SKIN_SIZE)
	const arm = variant === 'SLIM' ? 3 : 4
	const parts =
		layer === 'base'
			? [
					[0, 0, 8, 8, 8],
					[16, 16, 8, 12, 4],
					[40, 16, arm, 12, 4],
					[32, 48, arm, 12, 4],
					[0, 16, 4, 12, 4],
					[16, 48, 4, 12, 4],
				]
			: [
					[32, 0, 8, 8, 8],
					[16, 32, 8, 12, 4],
					[40, 32, arm, 12, 4],
					[48, 48, arm, 12, 4],
					[0, 32, 4, 12, 4],
					[0, 48, 4, 12, 4],
				]
	let region = 0
	for (const [x, y, width, height, depth] of parts) {
		for (const [left, top, w, h] of faces(x, y, width, height, depth)) {
			region++
			for (let row = top; row < top + h; row++) {
				mask.fill(region, row * SKIN_SIZE + left, row * SKIN_SIZE + left + w)
			}
		}
	}
	return mask
}

export function visitLine(from: Pixel, to: Pixel, visit: (x: number, y: number) => void) {
	let { x, y } = from
	const dx = Math.abs(to.x - x)
	const dy = -Math.abs(to.y - y)
	const sx = x < to.x ? 1 : -1
	const sy = y < to.y ? 1 : -1
	let error = dx + dy
	while (true) {
		visit(x, y)
		if (x === to.x && y === to.y) return
		const step = error * 2
		if (step >= dy) {
			error += dy
			x += sx
		}
		if (step <= dx) {
			error += dx
			y += sy
		}
	}
}

export function fillSkinFace(
	data: Uint8ClampedArray,
	mask: Uint8Array,
	start: Pixel,
	color: number[],
) {
	const origin = start.y * SKIN_SIZE + start.x
	const region = mask[origin]
	if (!region) return false
	const target = data.slice(origin * 4, origin * 4 + 4)
	if (color.every((channel, i) => channel === target[i])) return false
	const pending = [origin]
	while (pending.length) {
		const index = pending.pop()!
		if (mask[index] !== region || !target.every((channel, i) => channel === data[index * 4 + i]))
			continue
		data.set(color, index * 4)
		if (index % SKIN_SIZE > 0) pending.push(index - 1)
		if (index % SKIN_SIZE < SKIN_SIZE - 1) pending.push(index + 1)
		if (index >= SKIN_SIZE) pending.push(index - SKIN_SIZE)
		if (index < SKIN_SIZE * (SKIN_SIZE - 1)) pending.push(index + SKIN_SIZE)
	}
	return true
}
