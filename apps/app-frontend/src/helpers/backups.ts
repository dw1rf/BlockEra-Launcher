import { invoke } from '@tauri-apps/api/core'

export type WorldBackupReason = 'manual' | 'scheduled' | 'pre_update' | 'pre_repair' | 'pre_restore'

export type WorldBackupSettings = {
	enabled: boolean
	intervalMinutes: number
	retentionPerWorld: number
}

export type WorldBackup = {
	id: string
	profile: string
	world: string
	createdAt: string
	size: number
	reason: WorldBackupReason
	path: string
}

export type WorldBackupFailure = {
	world: string
	error: string
}

export type WorldBackupSummary = {
	count: number
	totalBytes: number
	backups: WorldBackup[]
	failures: WorldBackupFailure[]
}

export const AUTO_BACKUP_STORAGE_KEY = 'blockera:auto-world-backups'

export async function getWorldBackupSettings(): Promise<WorldBackupSettings> {
	return await invoke('plugin:worlds|get_world_backup_settings')
}

export async function setWorldBackupSettings(settings: WorldBackupSettings): Promise<void> {
	await invoke('plugin:worlds|set_world_backup_settings', { settings })
}

export async function migrateLegacyWorldBackupSettings(): Promise<WorldBackupSettings> {
	const settings = await getWorldBackupSettings()
	const legacy = localStorage.getItem(AUTO_BACKUP_STORAGE_KEY)
	if (legacy !== null) {
		settings.enabled = legacy === 'true'
		await setWorldBackupSettings(settings)
		localStorage.removeItem(AUTO_BACKUP_STORAGE_KEY)
	}
	return settings
}

export async function backupProfileWorlds(
	profile: string,
	reason: WorldBackupReason = 'manual',
): Promise<WorldBackupSummary> {
	return await invoke('plugin:worlds|backup_profile_worlds', { profile, reason })
}

export async function listWorldBackups(profile: string, world?: string): Promise<WorldBackup[]> {
	return await invoke('plugin:worlds|list_world_backups', { profile, world })
}

export async function restoreWorldBackup(profile: string, backupId: string): Promise<void> {
	await invoke('plugin:worlds|restore_world_backup', { profile, backupId })
}

export async function deleteWorldBackup(profile: string, backupId: string): Promise<void> {
	await invoke('plugin:worlds|delete_world_backup', { profile, backupId })
}
