import { defineStore } from 'pinia'

export const DEFAULT_FEATURE_FLAGS = {
	project_background: false,
	page_path: false,
	worlds_tab: false,
	worlds_in_home: true,
	servers_in_app: false,
}

export const THEME_OPTIONS = ['dark', 'light', 'oled', 'system'] as const
export const BLOCKERA_THEME_OPTIONS = ['dark'] as const

export type FeatureFlag = keyof typeof DEFAULT_FEATURE_FLAGS
export type FeatureFlags = Record<FeatureFlag, boolean>
export type ColorTheme = (typeof THEME_OPTIONS)[number]

export type ThemeStore = {
	selectedTheme: ColorTheme
	advancedRendering: boolean
	toggleSidebar: boolean

	devMode: boolean
	featureFlags: FeatureFlags
}

export const DEFAULT_THEME_STORE: ThemeStore = {
	selectedTheme: 'dark',
	advancedRendering: true,
	toggleSidebar: false,

	devMode: false,
	featureFlags: DEFAULT_FEATURE_FLAGS,
}

export const useTheming = defineStore('themeStore', {
	state: () => DEFAULT_THEME_STORE,
	actions: {
		setThemeState(newTheme: ColorTheme) {
			if (newTheme !== 'dark') console.info('[BlockEra] Используется фирменная тёмная тема.')
			this.selectedTheme = 'dark'
			this.setThemeClass()
		},
		setThemeClass() {
			for (const theme of THEME_OPTIONS) {
				document.getElementsByTagName('html')[0].classList.remove(`${theme}-mode`)
			}

			document.getElementsByTagName('html')[0].classList.add('dark-mode')
		},
		getFeatureFlag(key: FeatureFlag) {
			return this.featureFlags[key] ?? DEFAULT_FEATURE_FLAGS[key]
		},
		getThemeOptions() {
			return BLOCKERA_THEME_OPTIONS
		},
	},
})
