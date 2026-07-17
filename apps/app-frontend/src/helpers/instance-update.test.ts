import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
	automaticWorldBackupsEnabled: vi.fn(() => false),
	backupProfileWorlds: vi.fn(),
	getVersion: vi.fn(),
	getProcess: vi.fn(),
	getProfile: vi.fn(),
	getProjects: vi.fn(),
	updateProject: vi.fn(),
	installDependencies: vi.fn(),
}))

vi.mock('@/helpers/backups', () => ({
	automaticWorldBackupsEnabled: mocks.automaticWorldBackupsEnabled,
	backupProfileWorlds: mocks.backupProfileWorlds,
}))
vi.mock('@/helpers/cache', () => ({ get_version: mocks.getVersion }))
vi.mock('@/helpers/process', () => ({ get_by_profile_path: mocks.getProcess }))
vi.mock('@/helpers/profile', () => ({
	get: mocks.getProfile,
	get_projects: mocks.getProjects,
	update_project: mocks.updateProject,
}))
vi.mock('@/store/install', () => ({ installVersionDependencies: mocks.installDependencies }))

// Vitest hoists the mocks above this import before evaluating the module.
// eslint-disable-next-line import/first
import { runInstanceUpdate } from './instance-update'

describe('runInstanceUpdate', () => {
	beforeEach(() => {
		vi.clearAllMocks()
		mocks.automaticWorldBackupsEnabled.mockReturnValue(false)
		mocks.getProfile.mockResolvedValue({ install_stage: 'installed' })
		mocks.getProcess.mockResolvedValue([])
		mocks.getVersion.mockImplementation(async (id: string) => ({
			id,
			version_number: `version-${id}`,
		}))
	})

	it('preserves the update count when a preflight check fails', async () => {
		mocks.getProfile.mockResolvedValue({ install_stage: 'installing' })

		const report = await runInstanceUpdate('profile-a')

		expect(report.success).toBe(false)
		expect(report.remainingUpdates).toBeNull()
		expect(mocks.updateProject).not.toHaveBeenCalled()
	})

	it('reports each partial result and verifies the installed version', async () => {
		mocks.getProjects
			.mockResolvedValueOnce({
				'a.jar': { file_name: 'a.jar', update_version_id: 'a2' },
				'b.jar': { file_name: 'b.jar', update_version_id: 'b2' },
			})
			.mockResolvedValueOnce({
				'a.jar': { file_name: 'a.jar', metadata: { version_id: 'a2' } },
				'b.jar': { file_name: 'b.jar', update_version_id: 'b2' },
			})
		mocks.updateProject.mockImplementation(async (_profile: string, path: string) => {
			if (path === 'b.jar') throw new Error('network failure')
			return path
		})

		const report = await runInstanceUpdate('profile-a')

		expect(report.success).toBe(false)
		expect(report.remainingUpdates).toBe(1)
		expect(report.items).toMatchObject([
			{ path: 'a.jar', status: 'success', installedVersion: 'version-a2' },
			{ path: 'b.jar', status: 'error', error: 'network failure' },
		])
		expect(mocks.installDependencies).toHaveBeenCalledTimes(1)
	})

	it('retries only requested failed paths', async () => {
		mocks.getProjects
			.mockResolvedValueOnce({
				'a.jar': { file_name: 'a.jar', update_version_id: 'a2' },
				'b.jar': { file_name: 'b.jar', update_version_id: 'b2' },
			})
			.mockResolvedValueOnce({
				'a.jar': { file_name: 'a.jar', update_version_id: 'a2' },
				'b.jar': { file_name: 'b.jar', metadata: { version_id: 'b2' } },
			})
		mocks.updateProject.mockResolvedValue('b.jar')

		const report = await runInstanceUpdate('profile-a', { onlyPaths: ['b.jar'] })

		expect(mocks.updateProject).toHaveBeenCalledOnce()
		expect(mocks.updateProject).toHaveBeenCalledWith('profile-a', 'b.jar')
		expect(report.items).toHaveLength(1)
		expect(report.items[0]).toMatchObject({ path: 'b.jar', status: 'success' })
	})
})
