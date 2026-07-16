import { backup_world, get_profile_worlds, type SingleplayerWorld } from '@/helpers/worlds'

export type WorldBackupFailure = {
	world: string
	error: string
}

export type WorldBackupSummary = {
	count: number
	totalBytes: number
	failures: WorldBackupFailure[]
}

export const AUTO_BACKUP_STORAGE_KEY = 'blockera:auto-world-backups'

export function automaticWorldBackupsEnabled(): boolean {
	return localStorage.getItem(AUTO_BACKUP_STORAGE_KEY) === 'true'
}

export function setAutomaticWorldBackups(enabled: boolean): void {
	localStorage.setItem(AUTO_BACKUP_STORAGE_KEY, String(enabled))
}

export async function backupProfileWorlds(instancePath: string): Promise<WorldBackupSummary> {
	const worlds = await get_profile_worlds(instancePath)
	const localWorlds = worlds.filter(
		(world): world is SingleplayerWorld => world.type === 'singleplayer',
	)
	const summary: WorldBackupSummary = { count: 0, totalBytes: 0, failures: [] }

	for (const world of localWorlds) {
		try {
			summary.totalBytes += await backup_world(instancePath, world.path)
			summary.count += 1
		} catch (error) {
			summary.failures.push({
				world: world.name,
				error: error instanceof Error ? error.message : String(error),
			})
		}
	}

	return summary
}
