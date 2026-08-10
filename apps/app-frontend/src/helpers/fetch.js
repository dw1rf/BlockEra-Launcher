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
	try {
		// WebView2 uses the Windows networking configuration, including the
		// per-user system proxy used by browsers. The Tauri HTTP plugin bypasses
		// that configuration and times out on networks where Modrinth is only
		// reachable through the Windows proxy.
		return await globalThis.fetch(url, options)
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
