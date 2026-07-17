import { automaticWorldBackupsEnabled, backupProfileWorlds } from '@/helpers/backups'
import { get_version } from '@/helpers/cache'
import { get_by_profile_path } from '@/helpers/process'
import { get, get_projects, update_project } from '@/helpers/profile'
import { installVersionDependencies } from '@/store/install'

export type UpdateResultStatus = 'success' | 'error' | 'skipped'

export type InstanceUpdateItemResult = {
	path: string
	newPath?: string
	name: string
	targetVersionId?: string
	installedVersion?: string
	status: UpdateResultStatus
	error?: string
}

export type InstanceUpdateStep = {
	id: 'state' | 'space' | 'backup' | 'projects' | 'dependencies' | 'verify'
	label: string
	status: UpdateResultStatus
	detail?: string
}

export type InstanceUpdateReport = {
	instancePath: string
	steps: InstanceUpdateStep[]
	items: InstanceUpdateItemResult[]
	remainingUpdates: number | null
	success: boolean
}

type ContentFile = {
	file_name?: string
	update_version_id?: string
	metadata?: { version_id?: string }
}

export async function runInstanceUpdate(
	instancePath: string,
	options: {
		onItem?: (result: InstanceUpdateItemResult) => void
		onlyPaths?: string[]
		backup?: boolean
	} = {},
): Promise<InstanceUpdateReport> {
	const steps: InstanceUpdateStep[] = []
	const items: InstanceUpdateItemResult[] = []
	const profile = await get(instancePath)
	const running = await get_by_profile_path(instancePath)

	if (profile.install_stage !== 'installed') {
		steps.push({
			id: 'state',
			label: 'Проверка состояния сборки',
			status: 'error',
			detail: 'Установка сборки не завершена.',
		})
		return { instancePath, steps, items, remainingUpdates: null, success: false }
	}
	if (running.length > 0) {
		steps.push({
			id: 'state',
			label: 'Проверка состояния сборки',
			status: 'error',
			detail: 'Сначала остановите Minecraft.',
		})
		return { instancePath, steps, items, remainingUpdates: null, success: false }
	}
	steps.push({ id: 'state', label: 'Проверка состояния сборки', status: 'success' })

	// В текущем IPC нет безопасного API оценки свободного места для произвольной сборки.
	steps.push({
		id: 'space',
		label: 'Проверка свободного места',
		status: 'skipped',
		detail: 'Проверка выполняется установщиком при записи файлов.',
	})

	if (options.backup ?? automaticWorldBackupsEnabled()) {
		try {
			const backup = await backupProfileWorlds(instancePath)
			if (backup.failures.length > 0)
				throw new Error(`не удалось скопировать миров: ${backup.failures.length}`)
			steps.push({
				id: 'backup',
				label: 'Точка восстановления миров',
				status: 'success',
				detail: `Создано копий: ${backup.count}`,
			})
		} catch (error) {
			steps.push({
				id: 'backup',
				label: 'Точка восстановления миров',
				status: 'error',
				detail: error instanceof Error ? error.message : String(error),
			})
			return { instancePath, steps, items, remainingUpdates: null, success: false }
		}
	} else {
		steps.push({
			id: 'backup',
			label: 'Точка восстановления миров',
			status: 'skipped',
			detail: 'Автоматические резервные копии отключены.',
		})
	}

	const before = (await get_projects(instancePath, 'must_revalidate')) as Record<
		string,
		ContentFile
	>
	const candidates = Object.entries(before).filter(
		([path, file]) =>
			!!file.update_version_id &&
			(!options.onlyPaths ||
				options.onlyPaths.includes(path) ||
				options.onlyPaths.includes(file.file_name ?? '')),
	)
	if (options.onlyPaths) {
		const candidatePaths = new Set(
			candidates.flatMap(([path, file]) => [path, file.file_name ?? '']),
		)
		for (const requestedPath of options.onlyPaths.filter((path) => !candidatePaths.has(path))) {
			const result: InstanceUpdateItemResult = {
				path: requestedPath,
				name: requestedPath.split(/[\\/]/).pop() ?? requestedPath,
				status: 'skipped',
				error: 'Обновление больше не доступно.',
			}
			items.push(result)
			options.onItem?.(result)
		}
	}

	if (candidates.length === 0) {
		steps.push({
			id: 'projects',
			label: 'Обновление проектов',
			status: 'skipped',
			detail: 'Доступных обновлений нет.',
		})
		steps.push({ id: 'dependencies', label: 'Обязательные зависимости', status: 'skipped' })
		steps.push({ id: 'verify', label: 'Повторная проверка', status: 'success' })
		return { instancePath, steps, items, remainingUpdates: 0, success: true }
	}

	for (const [path, file] of candidates) {
		const targetVersionId = file.update_version_id
		const result: InstanceUpdateItemResult = {
			path,
			name: file.file_name ?? path.split(/[\\/]/).pop() ?? path,
			targetVersionId,
			status: 'error',
		}
		try {
			result.newPath = await update_project(instancePath, path)
			if (targetVersionId) {
				const version = await get_version(targetVersionId, 'must_revalidate')
				await installVersionDependencies(profile, version)
				result.installedVersion = version.version_number
			}
			result.status = 'success'
		} catch (error) {
			result.error = error instanceof Error ? error.message : String(error)
		}
		items.push(result)
		options.onItem?.(result)
	}

	const failed = items.filter((item) => item.status === 'error')
	const skipped = items.filter((item) => item.status === 'skipped')
	steps.push({
		id: 'projects',
		label: 'Обновление проектов',
		status: failed.length > 0 ? 'error' : 'success',
		detail: `Успешно: ${items.length - failed.length - skipped.length}, ошибок: ${failed.length}, пропущено: ${skipped.length}`,
	})
	steps.push({
		id: 'dependencies',
		label: 'Обязательные зависимости',
		status: failed.length > 0 ? 'error' : 'success',
		detail: failed.length > 0 ? 'Для неудачных обновлений зависимости не изменялись.' : undefined,
	})

	const after = (await get_projects(instancePath, 'must_revalidate')) as Record<string, ContentFile>
	const remainingUpdates = Object.values(after).filter((file) => file.update_version_id).length
	let verificationFailed = false
	for (const item of items) {
		if (item.status !== 'success') continue
		const installed =
			after[item.newPath ?? item.path] ??
			Object.values(after).find((file) => file.file_name === item.name)
		if (!installed || installed.update_version_id) {
			verificationFailed = true
			item.status = 'error'
			item.error = !installed
				? 'Файл не найден после обновления.'
				: 'Backend всё ещё сообщает о доступном обновлении.'
			continue
		}
		if (installed.metadata?.version_id) {
			try {
				item.installedVersion = (
					await get_version(installed.metadata.version_id, 'must_revalidate')
				).version_number
			} catch {
				item.installedVersion = installed.metadata.version_id
			}
		}
	}
	steps.push({
		id: 'verify',
		label: 'Повторная проверка',
		status: verificationFailed ? 'error' : 'success',
		detail: `Осталось обновлений: ${remainingUpdates}`,
	})

	return {
		instancePath,
		steps,
		items,
		remainingUpdates,
		success: items.every((item) => item.status !== 'error') && !verificationFailed,
	}
}
