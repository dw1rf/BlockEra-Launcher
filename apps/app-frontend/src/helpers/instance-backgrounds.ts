import heroVibrant from '@/assets/launcher/hero-vibrant.jpg'
import autumnVillage from '@/assets/launcher/instance-backgrounds/autumn-village.jpg'
import jungleRuins from '@/assets/launcher/instance-backgrounds/jungle-ruins.jpg'
import skyIslands from '@/assets/launcher/instance-backgrounds/sky-islands.jpg'
import violetCastle from '@/assets/launcher/instance-backgrounds/violet-castle.jpg'

export const instanceBackgrounds = [
	violetCastle,
	autumnVillage,
	skyIslands,
	jungleRuins,
	heroVibrant,
]

export function instanceBackgroundFor(seed: string) {
	let hash = 2166136261
	for (let index = 0; index < seed.length; index += 1) {
		hash ^= seed.charCodeAt(index)
		hash = Math.imul(hash, 16777619)
	}
	return instanceBackgrounds[Math.abs(hash) % instanceBackgrounds.length]
}
