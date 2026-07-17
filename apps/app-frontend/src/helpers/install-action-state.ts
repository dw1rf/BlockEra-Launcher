export type InstallActionState =
	| 'idle'
	| 'awaiting-confirmation'
	| 'installing'
	| 'success'
	| 'error'

export type InstallActionEvent = 'request' | 'confirm' | 'succeed' | 'fail' | 'cancel' | 'retry'

export function nextInstallActionState(
	state: InstallActionState,
	event: InstallActionEvent,
): InstallActionState {
	switch (event) {
		case 'request':
		case 'retry':
			return 'awaiting-confirmation'
		case 'confirm':
			return 'installing'
		case 'succeed':
			return 'success'
		case 'fail':
			return 'error'
		case 'cancel':
			return 'idle'
		default:
			return state
	}
}

export function installActionPending(state: InstallActionState) {
	return state === 'awaiting-confirmation' || state === 'installing'
}
