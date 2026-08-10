type RouteShellInput = {
	path: string
	params: Record<string, unknown>
	matched?: Array<{ name?: unknown }>
}

export function getRouteShellKey(route: RouteShellInput): string {
	if (route.path.startsWith('/instance/')) return `instance:${String(route.params.id ?? '')}`
	if (route.path.startsWith('/project/')) return `project:${String(route.params.id ?? '')}`
	if (route.path.startsWith('/browse/')) {
		return `browse:${String(route.params.projectType ?? '')}`
	}
	if (route.path.startsWith('/library')) return 'library'
	return `${String(route.matched?.[0]?.name ?? route.path)}:${JSON.stringify(route.params)}`
}
