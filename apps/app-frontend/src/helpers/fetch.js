import { getVersion } from '@tauri-apps/api/app'
import { fetch } from '@tauri-apps/plugin-http'

export const MODRINTH_CONNECTION_ERROR =
	'Не удалось подключиться к Modrinth. Проверьте интернет-соединение или повторите попытку позже.'
export const MODRINTH_SERVICE_ERROR =
	'Сервис Modrinth временно недоступен. Повторите попытку позже.'

function isModrinthUrl(url) {
	try {
		const source = new URL(url)
		return (
			source.protocol === 'https:' && ['api.modrinth.com', 'cdn.modrinth.com'].includes(source.host)
		)
	} catch {
		return false
	}
}

function isAbortError(error) {
	return error instanceof Error && error.name === 'AbortError'
}

export const blockeraFetch = async (url, options = {}) => {
	const version = await getVersion()
	const headers = new Headers(options.headers ?? {})
	if (!headers.has('User-Agent')) {
		headers.set('User-Agent', `modrinth/theseus/${version} (support@modrinth.com)`)
	}
	const requestOptions = { ...options, headers }

	try {
		return await fetch(url, requestOptions)
	} catch (error) {
		if (isAbortError(error) || !isModrinthUrl(url)) throw error
		console.error('[BlockEra] Не удалось выполнить прямой запрос к Modrinth:', error)
		throw new Error(MODRINTH_CONNECTION_ERROR, { cause: error })
	}
}

export const useFetch = async (url, item, isSilent) => {
	try {
		return await blockeraFetch(url, {
			method: 'GET',
		})
	} catch (err) {
		if (!isSilent) {
			throw err
		} else {
			console.error(err)
		}
	}
}
