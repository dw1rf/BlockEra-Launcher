import { defineStore } from 'pinia'

const STORAGE_KEY = 'blockera-selected-instance'

export const useSelectedInstance = defineStore('selectedInstance', {
	state: () => ({ selectedInstanceId: localStorage.getItem(STORAGE_KEY) ?? '' }),
	actions: {
		setSelectedInstance(instanceId: string) {
			this.selectedInstanceId = instanceId
			if (instanceId) localStorage.setItem(STORAGE_KEY, instanceId)
			else localStorage.removeItem(STORAGE_KEY)
		},
	},
})
