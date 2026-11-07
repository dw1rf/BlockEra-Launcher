import {
	AbstractWebNotificationManager,
	type NotificationPanelLocation,
	type WebNotification,
} from '@modrinth/ui'
import { type Ref, ref } from 'vue'

import { launcherText } from '@/helpers/launcher-language'
import i18n from '@/i18n.config'

export class AppNotificationManager extends AbstractWebNotificationManager {
	private readonly state: Ref<WebNotification[]>
	private readonly locationState: Ref<NotificationPanelLocation>

	public constructor() {
		super()
		this.state = ref<WebNotification[]>([])
		this.locationState = ref<NotificationPanelLocation>('right')
	}

	public override handleError = (error: Error): void => {
		const original = typeof error === 'string' ? error : (error.message ?? String(error))
		let text = launcherText(original)
		if (i18n.global.locale.value.startsWith('ru')) {
			text = text
				.replace(/^Error launching Minecraft:\s*/, 'Не удалось запустить Minecraft: ')
				.replace(/^Minecraft authentication error:\s*/, 'Ошибка авторизации Minecraft: ')
				.replace(/^User is not logged in.*$/, 'Выберите аккаунт для запуска игры.')
		}
		this.addNotification({
			title: launcherText('An error occurred'),
			text,
			type: 'error',
			supportData: { originalMessage: original },
		})
	}

	public getNotificationLocation(): NotificationPanelLocation {
		return this.locationState.value
	}

	public setNotificationLocation(location: NotificationPanelLocation): void {
		this.locationState.value = location
	}

	public getNotifications(): WebNotification[] {
		return this.state.value
	}

	protected addNotificationToStorage(notification: WebNotification): void {
		this.state.value.push(notification)
	}

	protected removeNotificationFromStorage(id: string | number): void {
		const index = this.state.value.findIndex((n) => n.id === id)
		if (index > -1) {
			this.state.value.splice(index, 1)
		}
	}

	protected removeNotificationFromStorageByIndex(index: number): void {
		this.state.value.splice(index, 1)
	}

	protected clearAllNotificationsFromStorage(): void {
		this.state.value.splice(0)
	}
}
