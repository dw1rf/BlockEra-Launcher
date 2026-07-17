type JavaDescriptor = {
	parsed_version?: number
	major_version?: number
	version?: string
}

export function formatJavaLabel(value: unknown, fallback = 'Автоматический выбор'): string {
	if (value === null || value === undefined || value === '') return fallback

	if (typeof value === 'number') return `Java ${value}`
	if (typeof value === 'string') {
		const major = value.match(/\d+/)?.[0]
		return major ? `Java ${major}` : fallback
	}

	if (typeof value === 'object') {
		const descriptor = value as JavaDescriptor
		const major = descriptor.parsed_version ?? descriptor.major_version
		if (major) return `Java ${major}`

		const version = descriptor.version?.match(/\d+/)?.[0]
		if (version) return `Java ${version}`
	}

	return fallback
}
