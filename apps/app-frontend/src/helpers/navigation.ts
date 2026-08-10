import type { Router } from 'vue-router'

export function navigateHistory(router: Router, direction: 'back' | 'forward', fallback?: string) {
	const state = window.history.state as { back?: string | null; forward?: string | null } | null
	const historyTarget = direction === 'back' ? state?.back : state?.forward
	if (historyTarget) {
		if (direction === 'back') router.back()
		else router.forward()
	} else if (fallback) {
		void router.replace(fallback)
	}
}
