import { convertFileSrc } from '@tauri-apps/api/core'

import heroVibrant from '@/assets/launcher/hero-vibrant.jpg'
import autumnVillage from '@/assets/launcher/instance-backgrounds/autumn-village.jpg'
import jungleRuins from '@/assets/launcher/instance-backgrounds/jungle-ruins.jpg'
import skyIslands from '@/assets/launcher/instance-backgrounds/sky-islands.jpg'
import violetCastle from '@/assets/launcher/instance-backgrounds/violet-castle.jpg'

export type LauncherBackground = {
	id: string
	label: string
	description: string
	src: string
}

export const launcherBackgrounds: LauncherBackground[] = [
	{
		id: 'violet-castle',
		label: 'Фиолетовый замок',
		description: 'Ночной замок и магическое небо',
		src: violetCastle,
	},
	{
		id: 'autumn-village',
		label: 'Осенняя деревня',
		description: 'Тёплый свет и уютное поселение',
		src: autumnVillage,
	},
	{
		id: 'sky-islands',
		label: 'Небесные острова',
		description: 'Парящие острова над облаками',
		src: skyIslands,
	},
	{
		id: 'jungle-ruins',
		label: 'Руины в джунглях',
		description: 'Затерянный храм среди зелени',
		src: jungleRuins,
	},
	{
		id: 'blockera',
		label: 'Мир BlockEra',
		description: 'Фирменная панорама лаунчера',
		src: heroVibrant,
	},
]

export const BACKGROUND_CHANGED_EVENT = 'blockera-background-changed'

function storageKey(scope: string) {
	return `blockera-background:${scope}`
}

function customStorageKey(scope: string) {
	return `blockera-background-custom:${scope}`
}

function automaticBackground(seed: string) {
	let hash = 2166136261
	for (let index = 0; index < seed.length; index += 1) {
		hash ^= seed.charCodeAt(index)
		hash = Math.imul(hash, 16777619)
	}
	return launcherBackgrounds[Math.abs(hash) % launcherBackgrounds.length].src
}

export function selectedBackgroundId(scope: string) {
	return localStorage.getItem(storageKey(scope)) ?? 'auto'
}

export function setSelectedBackground(scope: string, backgroundId: string) {
	if (backgroundId === 'auto') {
		localStorage.removeItem(storageKey(scope))
	} else {
		localStorage.setItem(storageKey(scope), backgroundId)
	}
	window.dispatchEvent(new CustomEvent(BACKGROUND_CHANGED_EVENT, { detail: { scope } }))
}

export function setCustomBackground(scope: string, path: string) {
	localStorage.setItem(customStorageKey(scope), path)
	setSelectedBackground(scope, 'custom')
}

export function customBackgroundFor(scope: string) {
	const path = localStorage.getItem(customStorageKey(scope))
	return path ? convertFileSrc(path) : null
}

export function instanceBackgroundFor(seed: string) {
	const scope = `instance:${seed}`
	const selected = selectedBackgroundId(scope)
	if (selected === 'custom') return customBackgroundFor(scope) ?? automaticBackground(seed)
	return (
		launcherBackgrounds.find((background) => background.id === selected)?.src ??
		automaticBackground(seed)
	)
}

export function homeBackgroundFor(seed: string) {
	const selected = selectedBackgroundId('home')
	if (selected === 'custom') return customBackgroundFor('home') ?? instanceBackgroundFor(seed)
	return (
		launcherBackgrounds.find((background) => background.id === selected)?.src ??
		instanceBackgroundFor(seed)
	)
}
