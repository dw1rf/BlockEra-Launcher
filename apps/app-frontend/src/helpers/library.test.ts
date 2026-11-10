import assert from 'node:assert/strict'
import { test } from 'node:test'
import { libraryMatches, latestPlayedWorld, normalizedTags, libraryPlaytime } from './library.ts'

test('library search combines name, game version and organization without changing groups', () => {
	const profile = { name: 'Industrial Era', game_version: '1.20.1', groups: ['Друзья'] }
	assert.equal(libraryMatches(profile, 'Выживание', 'industrial 1.20 друзья'), true)
	assert.equal(libraryMatches(profile, 'Выживание', '1.21'), false)
	assert.deepEqual(profile.groups, ['Друзья'])
	assert.deepEqual(normalizedTags(' Друзья, ,Техника,Друзья '), ['Друзья', 'Техника'])
})

test('last world uses a valid played timestamp and preserves the input order', () => {
	const worlds = [
		{ name: 'Unknown', last_played: 'invalid' },
		{ name: 'Older', last_played: '2025-01-01T00:00:00Z' },
		{ name: 'Latest', last_played: '2026-01-01T00:00:00Z' },
		{ name: 'Never launched' },
	]
	assert.equal(latestPlayedWorld(worlds)?.name, 'Latest')
	assert.equal(worlds[0].name, 'Unknown')
	assert.equal(latestPlayedWorld([{ name: 'Never launched' }]), undefined)
})

test('playtime handles subminute and combined hour boundaries', () => {
	assert.equal(libraryPlaytime(0), '0 мин.')
	assert.equal(libraryPlaytime(59), 'Меньше минуты')
	assert.equal(libraryPlaytime(3661), '1 ч. 1 мин.')
})
