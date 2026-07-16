<script setup>
import { AuthFeature, PanelVersionFeature, TauriModrinthClient } from '@modrinth/api-client'
import {
	BlockEraLogo,
	BlocksIcon,
	ChangeSkinIcon,
	CollectionIcon,
	CompassIcon,
	DownloadIcon,
	ExternalIcon,
	HomeIcon,
	LeftArrowIcon,
	LibraryIcon,
	LogInIcon,
	LogOutIcon,
	MaximizeIcon,
	MinimizeIcon,
	NewspaperIcon,
	NotepadTextIcon,
	PlusIcon,
	RestoreIcon,
	RightArrowIcon,
	ServerIcon,
	SettingsIcon,
	UserIcon,
	WorldIcon,
	XIcon,
} from '@modrinth/assets'
import {
	Admonition,
	Avatar,
	Button,
	ButtonStyled,
	commonMessages,
	defineMessages,
	NewsArticleCard,
	NotificationPanel,
	OverflowMenu,
	provideModrinthClient,
	provideNotificationManager,
	providePageContext,
	useDebugLogger,
	useVIntl,
} from '@modrinth/ui'
import { renderString } from '@modrinth/utils'
import { useQuery } from '@tanstack/vue-query'
import { getVersion } from '@tauri-apps/api/app'
import { invoke } from '@tauri-apps/api/core'
import { getCurrentWindow } from '@tauri-apps/api/window'
import { openUrl } from '@tauri-apps/plugin-opener'
import { type } from '@tauri-apps/plugin-os'
import { saveWindowState, StateFlags } from '@tauri-apps/plugin-window-state'
import { $fetch } from 'ofetch'
import { computed, onMounted, onUnmounted, provide, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'

import ModrinthLoadingIndicator from '@/components/LoadingIndicatorBar.vue'
import AccountsCard from '@/components/ui/AccountsCard.vue'
import BlockEraUpdateCenter from '@/components/ui/BlockEraUpdateCenter.vue'
import Breadcrumbs from '@/components/ui/Breadcrumbs.vue'
import ErrorModal from '@/components/ui/ErrorModal.vue'
import FriendsList from '@/components/ui/friends/FriendsList.vue'
import IncompatibilityWarningModal from '@/components/ui/install_flow/IncompatibilityWarningModal.vue'
import InstallConfirmModal from '@/components/ui/install_flow/InstallConfirmModal.vue'
import ModInstallModal from '@/components/ui/install_flow/ModInstallModal.vue'
import InstanceCreationModal from '@/components/ui/InstanceCreationModal.vue'
import AppSettingsModal from '@/components/ui/modal/AppSettingsModal.vue'
import AuthGrantFlowWaitModal from '@/components/ui/modal/AuthGrantFlowWaitModal.vue'
import NavButton from '@/components/ui/NavButton.vue'
import QuickInstanceSwitcher from '@/components/ui/QuickInstanceSwitcher.vue'
import RunningAppBar from '@/components/ui/RunningAppBar.vue'
import SplashScreen from '@/components/ui/SplashScreen.vue'
import URLConfirmModal from '@/components/ui/URLConfirmModal.vue'
import { useCheckDisableMouseover } from '@/composables/macCssFix.js'
import { debugAnalytics, optOutAnalytics, trackEvent } from '@/helpers/analytics'
import { check_reachable } from '@/helpers/auth.js'
import { get_user } from '@/helpers/cache.js'
import { command_listener, info_listener, warning_listener } from '@/helpers/events.js'
import { useFetch } from '@/helpers/fetch.js'
import { cancelLogin, get as getCreds, login, logout } from '@/helpers/mr_auth.ts'
import { list, run } from '@/helpers/profile.js'
import { get as getSettings, set as setSettings } from '@/helpers/settings.ts'
import { get_opening_command, initialize_state } from '@/helpers/state'
import { getRemote, updateState } from '@/helpers/update.js'
import { getOS, isDev } from '@/helpers/utils.js'
import i18n from '@/i18n.config'
import { provideAppUpdateDownloadProgress } from '@/providers/download-progress.ts'
import { useError } from '@/store/error.js'
import { useInstall } from '@/store/install.js'
import { useLoading, useSelectedInstance, useTheming } from '@/store/state'

import { create_profile_and_install_from_file } from './helpers/pack'
import { generateSkinPreviews } from './helpers/rendering/batch-skin-renderer'
import { get_available_capes, get_available_skins } from './helpers/skins'
import { AppNotificationManager } from './providers/app-notifications'

const themeStore = useTheming()
const selectedInstanceStore = useSelectedInstance()
const modsRoute = computed(() =>
	selectedInstanceStore.selectedInstanceId
		? `/instance/${encodeURIComponent(selectedInstanceStore.selectedInstanceId)}`
		: '/library',
)

const notificationManager = new AppNotificationManager()
provideNotificationManager(notificationManager)
const { handleError, addNotification } = notificationManager

const tauriApiClient = new TauriModrinthClient({
	userAgent: `modrinth/theseus/${getVersion()} (support@modrinth.com)`,
	features: [
		new AuthFeature({
			token: async () => (await getCreds()).session,
		}),
		new PanelVersionFeature(),
	],
})
provideModrinthClient(tauriApiClient)
providePageContext({
	hierarchicalSidebarAvailable: ref(true),
	showAds: ref(false),
})
const news = ref([])
const availableSurvey = ref(false)

const urlModal = ref(null)

const offline = ref(!navigator.onLine)
window.addEventListener('offline', () => {
	offline.value = true
})
window.addEventListener('online', () => {
	offline.value = false
})

const showOnboarding = ref(false)
const nativeDecorations = ref(false)

const os = ref('')

const stateInitialized = ref(false)

const criticalErrorMessage = ref()

const isMaximized = ref(false)

const authUnreachableDebug = useDebugLogger('AuthReachableChecker')
const authServerQuery = useQuery({
	queryKey: ['authServerReachability'],
	queryFn: async () => {
		await check_reachable()
		authUnreachableDebug('Auth servers are reachable')
		return true
	},
	refetchInterval: 5 * 60 * 1000, // 5 minutes
	retry: false,
	refetchOnWindowFocus: false,
})

const authUnreachable = computed(() => {
	if (authServerQuery.isError.value && !authServerQuery.isLoading.value) {
		console.warn('Failed to reach auth servers', authServerQuery.error.value)
		return true
	}
	return false
})

// This code is modified by AstralRinth
onMounted(async () => {
	await useCheckDisableMouseover()
	void getRemote(false)
	updateCheckInterval = window.setInterval(() => void getRemote(false), 30 * 60 * 1000)

	document.querySelector('body').addEventListener('click', handleClick)
	document.querySelector('body').addEventListener('auxclick', handleAuxClick)
})

onUnmounted(async () => {
	if (updateCheckInterval) window.clearInterval(updateCheckInterval)
	document.querySelector('body').removeEventListener('click', handleClick)
	document.querySelector('body').removeEventListener('auxclick', handleAuxClick)
})

const { formatMessage } = useVIntl()
const messages = defineMessages({
	updateInstalledToastTitle: {
		id: 'app.update.complete-toast.title',
		defaultMessage: 'Version {version} was successfully installed!',
	},
	updateInstalledToastText: {
		id: 'app.update.complete-toast.text',
		defaultMessage: 'Click here to view the changelog.',
	},
	reloadToUpdate: {
		id: 'app.update.reload-to-update',
		defaultMessage: 'Reload to install update',
	},
	downloadUpdate: {
		id: 'app.update.download-update',
		defaultMessage: 'Download update',
	},
	downloadingUpdate: {
		id: 'app.update.downloading-update',
		defaultMessage: 'Downloading update ({percent}%)',
	},
	authUnreachableHeader: {
		id: 'app.auth-servers.unreachable.header',
		defaultMessage: 'Cannot reach authentication servers',
	},
	authUnreachableBody: {
		id: 'app.auth-servers.unreachable.body',
		defaultMessage:
			'Minecraft authentication servers may be down right now. Check your internet connection and try again later.',
	},
})

// This code is modified by AstralRinth
async function setupApp() {
	const settings = await getSettings()
	settings.personalized_ads = false
	settings.telemetry = false
	settings.theme = 'dark'
	await setSettings(settings)

	const {
		native_decorations,
		theme,
		locale,
		telemetry,
		personalized_ads,
		collapsed_navigation,
		advanced_rendering,
		onboarded,
		default_page,
		toggle_sidebar,
		developer_mode,
		feature_flags,
		pending_update_toast_for_version,
	} = await getSettings()

	// Initialize locale from saved settings
	if (locale) {
		i18n.global.locale.value = locale
	}

	if (default_page === 'Library') {
		await router.push('/library')
	}

	os.value = await getOS()
	const dev = await isDev()
	const version = await getVersion()
	showOnboarding.value = !onboarded

	nativeDecorations.value = native_decorations
	if (os.value !== 'MacOS') await getCurrentWindow().setDecorations(native_decorations)

	themeStore.setThemeState(theme)
	themeStore.collapsedNavigation = collapsed_navigation
	themeStore.advancedRendering = advanced_rendering
	themeStore.toggleSidebar = toggle_sidebar
	themeStore.devMode = developer_mode
	themeStore.featureFlags = feature_flags
	stateInitialized.value = true

	isMaximized.value = await getCurrentWindow().isMaximized()

	await getCurrentWindow().onResized(async () => {
		isMaximized.value = await getCurrentWindow().isMaximized()
	})

	// This code is modified by AstralRinth
	if (!telemetry) {
		console.info('[BlockEra] Телеметрия отключена по умолчанию.')
		optOutAnalytics()
	}
	if (!personalized_ads) {
		console.info('[BlockEra] Персонализированная реклама отключена по умолчанию.')
	}
	if (dev) debugAnalytics()
	trackEvent('Launched', { version, dev, onboarded })

	if (!dev) document.addEventListener('contextmenu', (event) => event.preventDefault())

	const osType = await type()
	if (osType === 'macos') {
		document.getElementsByTagName('html')[0].classList.add('mac')
	} else {
		document.getElementsByTagName('html')[0].classList.add('windows')
	}

	await warning_listener((e) =>
		addNotification({
			title: 'Warning',
			text: e.message,
			type: 'warn',
		}),
	)

	// This code is modified by AstralRinth
	await info_listener((e) =>
		addNotification({
			title: 'Info',
			text: e.message,
			type: 'info',
		}),
	)

	useFetch(
		`https://api.modrinth.com/appCriticalAnnouncement.json?version=${version}`,
		'criticalAnnouncements',
		true,
	)
		.then((response) => response.json())
		.then((res) => {
			if (res && res.header && res.body) {
				criticalErrorMessage.value = res
			}
		})
		.catch(() => {
			console.log(
				`No critical announcement found at https://api.modrinth.com/appCriticalAnnouncement.json?version=${version}`,
			)
		})

	useFetch(`https://modrinth.com/news/feed/articles.json`, 'news', true)
		.then((response) => response.json())
		.then((res) => {
			if (res && res.articles) {
				// Format expected by NewsArticleCard component.
				news.value = res.articles
					.map((article) => ({
						...article,
						path: article.link,
						thumbnail: article.thumbnail,
						title: article.title,
						summary: article.summary,
						date: article.date,
					}))
					.slice(0, 4)
			}
		})

	get_opening_command().then(handleCommand)
	fetchCredentials()

	try {
		const skins = (await get_available_skins()) ?? []
		const capes = (await get_available_capes()) ?? []
		generateSkinPreviews(skins, capes)
	} catch (error) {
		console.warn('Failed to generate skin previews in app setup.', error)
	}

	if (pending_update_toast_for_version !== null) {
		const settings = await getSettings()
		settings.pending_update_toast_for_version = null
		await setSettings(settings)
	}

	if (osType === 'windows') {
		await processPendingSurveys()
	} else {
		console.info('Skipping user surveys on non-Windows platforms')
	}
}

const stateFailed = ref(false)
initialize_state()
	.then(() => {
		setupApp().catch((err) => {
			stateFailed.value = true
			console.error(err)
			error.showError(err, null, false, 'state_init')
		})
	})
	.catch((err) => {
		stateFailed.value = true
		console.error('Failed to initialize app', err)
		error.showError(err, null, false, 'state_init')
	})

const handleClose = async () => {
	await saveWindowState(StateFlags.ALL)
	await getCurrentWindow().close()
}

const router = useRouter()
router.afterEach((to, from, failure) => {
	trackEvent('PageView', {
		path: to.path,
		fromPath: from.path,
		failed: failure,
	})
})
const route = useRoute()
const cinematicShell = computed(
	() =>
		route.path === '/' ||
		route.path.startsWith('/library') ||
		route.path.startsWith('/instance/') ||
		route.path.startsWith('/browse/') ||
		route.path.startsWith('/project/') ||
		route.path === '/skins',
)

const loading = useLoading()
loading.setEnabled(false)

const error = useError()
const errorModal = ref()

const install = useInstall()
const modInstallModal = ref()
const installConfirmModal = ref()
const incompatibilityWarningModal = ref()

const credentials = ref()

const modrinthLoginFlowWaitModal = ref()

async function fetchCredentials() {
	const creds = await getCreds().catch(handleError)
	if (creds && creds.user_id) {
		creds.user = await get_user(creds.user_id).catch(handleError)
	}
	credentials.value = creds ?? null
}

async function signIn() {
	modrinthLoginFlowWaitModal.value.show()

	try {
		await login()
		await fetchCredentials()
	} catch (error) {
		if (
			typeof error === 'object' &&
			typeof error['message'] === 'string' &&
			error.message.includes('Login canceled')
		) {
			// Not really an error due to being a result of user interaction, show nothing
		} else {
			handleError(error)
		}
	} finally {
		modrinthLoginFlowWaitModal.value.hide()
	}
}

async function logOut() {
	await logout().catch(handleError)
	await fetchCredentials()
}

const MIDAS_BITFLAG = 1 << 0
const hasPlus = computed(
	() =>
		credentials.value &&
		credentials.value.user &&
		(credentials.value.user.badges & MIDAS_BITFLAG) === MIDAS_BITFLAG,
)

const sidebarToggled = ref(true)

themeStore.$subscribe(() => {
	sidebarToggled.value = !themeStore.toggleSidebar
})

const forceSidebar = computed(
	() => route.path.startsWith('/browse') || route.path.startsWith('/project'),
)
const sidebarVisible = computed(() => sidebarToggled.value || forceSidebar.value)

onMounted(() => {
	invoke('show_window')

	error.setErrorModal(errorModal.value)

	install.setIncompatibilityWarningModal(incompatibilityWarningModal)
	install.setInstallConfirmModal(installConfirmModal)
	install.setModInstallModal(modInstallModal)
})

const accounts = ref(null)
provide('accountsCard', accounts)
const accountRevision = ref(0)
provide('accountRevision', accountRevision)

function handleAccountChange() {
	accountRevision.value += 1
}

command_listener(handleCommand)
async function handleCommand(e) {
	if (!e) return

	if (e.event === 'LaunchProfile') {
		await run(e.path).catch(handleError)
	} else if (e.event === 'RunMRPack') {
		// RunMRPack should directly install a local mrpack given a path
		if (e.path.endsWith('.mrpack')) {
			await create_profile_and_install_from_file(e.path).catch(handleError)
			trackEvent('InstanceCreate', {
				source: 'CreationModalFileDrop',
			})
		}
	} else {
		// Other commands are URL-based (deep linking)
		urlModal.value.show(e)
	}
}

const appUpdateDownload = {
	progress: ref(0),
	version: ref(),
}

let updateCheckInterval

function handleClick(e) {
	let target = e.target
	while (target != null) {
		if (target.matches('a')) {
			const isExternal =
				target.href &&
				['http://', 'https://', 'mailto:', 'tel:'].some((v) => target.href.startsWith(v)) &&
				!target.classList.contains('router-link-active') &&
				!target.href.startsWith('http://localhost') &&
				!target.href.startsWith('https://tauri.localhost') &&
				!target.href.startsWith('http://tauri.localhost')
			if (isExternal) {
				e.preventDefault()
				void openUrl(target.href)
			}
			break
		}
		target = target.parentElement
	}
}

function handleAuxClick(e) {
	// disables middle click -> new tab
	if (e.button === 1) {
		e.preventDefault()
		// instead do a left click
		const event = new MouseEvent('click', {
			view: window,
			bubbles: true,
			cancelable: true,
		})
		e.target.dispatchEvent(event)
	}
}

function cleanupOldSurveyDisplayData() {
	const threeWeeksAgo = new Date()
	threeWeeksAgo.setDate(threeWeeksAgo.getDate() - 21)

	for (let i = 0; i < localStorage.length; i++) {
		const key = localStorage.key(i)

		if (key.startsWith('survey-') && key.endsWith('-display')) {
			const dateValue = new Date(localStorage.getItem(key))
			if (dateValue < threeWeeksAgo) {
				localStorage.removeItem(key)
			}
		}
	}
}

async function openSurvey() {
	if (!availableSurvey.value) {
		console.error('No survey to open')
		return
	}

	const creds = await getCreds().catch(handleError)
	const userId = creds?.user_id

	const formId = availableSurvey.value.tally_id

	const popupOptions = {
		layout: 'modal',
		width: 700,
		autoClose: 2000,
		hideTitle: true,
		hiddenFields: {
			user_id: userId,
		},
		onOpen: () => console.info('Opened user survey'),
		onClose: () => {
			console.info('Closed user survey')
		},
		onSubmit: () => console.info('Active user survey submitted'),
	}

	try {
		if (window.Tally?.openPopup) {
			console.info(`Opening Tally popup for user survey (form ID: ${formId})`)
			dismissSurvey()
			window.Tally.openPopup(formId, popupOptions)
		} else {
			console.warn('Tally script not yet loaded')
		}
	} catch (e) {
		console.error('Error opening Tally popup:', e)
	}

	console.info(`Found user survey to show with tally_id: ${formId}`)
	window.Tally.openPopup(formId, popupOptions)
}

function dismissSurvey() {
	localStorage.setItem(`survey-${availableSurvey.value.id}-display`, new Date())
	availableSurvey.value = undefined
}

async function processPendingSurveys() {
	function isWithinLastTwoWeeks(date) {
		const twoWeeksAgo = new Date()
		twoWeeksAgo.setDate(twoWeeksAgo.getDate() - 14)
		return date >= twoWeeksAgo
	}

	cleanupOldSurveyDisplayData()

	const creds = await getCreds().catch(handleError)
	const userId = creds?.user_id

	const instances = await list().catch(handleError)
	const isActivePlayer =
		instances.findIndex(
			(instance) =>
				isWithinLastTwoWeeks(instance.last_played) && !isWithinLastTwoWeeks(instance.created),
		) >= 0

	let surveys = []
	try {
		surveys = await $fetch('https://api.modrinth.com/v2/surveys')
	} catch (e) {
		console.error('Error fetching surveys:', e)
	}

	const surveyToShow = surveys.find(
		(survey) =>
			!!(
				localStorage.getItem(`survey-${survey.id}-display`) === null &&
				survey.type === 'tally_app' &&
				((survey.condition === 'active_player' && isActivePlayer) ||
					(survey.assigned_users?.includes(userId) && !survey.dismissed_users?.includes(userId)))
			),
	)

	if (surveyToShow) {
		availableSurvey.value = surveyToShow
	} else {
		console.info('No user survey to show')
	}
}

provideAppUpdateDownloadProgress(appUpdateDownload) // [AR Note] If delete this shit line -> SettingsModal will not work.
</script>

<template>
	<SplashScreen v-if="!stateFailed" ref="splashScreen" data-tauri-drag-region />
	<div id="teleports"></div>
	<div
		v-if="stateInitialized"
		class="app-grid-layout experimental-styles-within relative"
		:class="{ 'disable-advanced-rendering': !themeStore.advancedRendering }"
	>
		<Suspense>
			<AppSettingsModal ref="settingsModal" />
		</Suspense>
		<Suspense>
			<AuthGrantFlowWaitModal ref="modrinthLoginFlowWaitModal" @flow-cancel="cancelLogin" />
		</Suspense>
		<Suspense>
			<InstanceCreationModal ref="installationModal" />
		</Suspense>
		<div
			v-if="!cinematicShell"
			class="app-grid-navbar bg-bg-raised flex flex-col p-[0.5rem] pt-0 gap-[0.5rem] w-[--left-bar-width]"
		>
			<NavButton v-tooltip.right="'Home'" to="/">
				<HomeIcon />
			</NavButton>
			<NavButton v-if="themeStore.featureFlags.worlds_tab" v-tooltip.right="'Worlds'" to="/worlds">
				<WorldIcon />
			</NavButton>
			<NavButton
				v-if="themeStore.featureFlags.servers_in_app"
				v-tooltip.right="'Servers'"
				to="/hosting/manage"
			>
				<ServerIcon />
			</NavButton>
			<NavButton
				v-tooltip.right="'Discover content'"
				to="/browse/modpack"
				:is-primary="() => route.path.startsWith('/browse') && !route.query.i"
				:is-subpage="(route) => route.path.startsWith('/project') && !route.query.i"
			>
				<CompassIcon />
			</NavButton>
			<NavButton v-tooltip.right="'Skins (Beta)'" to="/skins">
				<ChangeSkinIcon />
			</NavButton>
			<NavButton
				v-tooltip.right="'Library'"
				to="/library"
				:is-subpage="
					() =>
						route.path.startsWith('/instance') ||
						((route.path.startsWith('/browse') || route.path.startsWith('/project')) &&
							route.query.i)
				"
			>
				<LibraryIcon />
			</NavButton>
			<div class="h-px w-6 mx-auto my-2 bg-surface-5"></div>
			<suspense>
				<QuickInstanceSwitcher />
			</suspense>
			<NavButton
				v-tooltip.right="'Create new instance'"
				:to="() => $refs.installationModal.show()"
				:disabled="offline"
			>
				<PlusIcon />
			</NavButton>
			<div class="flex flex-grow"></div>
			<Transition name="nav-button-animated">
				<div v-if="updateState">
					<NavButton
						v-tooltip.right="'Доступно обновление BlockEra Launcher'"
						:to="() => $refs.settingsModal.show()"
					>
						<DownloadIcon class="text-brand" />
					</NavButton>
				</div>
			</Transition>
			<template v-if="updateState">
				<NavButton
					v-tooltip.right="formatMessage(commonMessages.settingsLabel)"
					class="neon-icon pulse"
					:to="() => $refs.settingsModal.show()"
				>
					<SettingsIcon />
				</NavButton>
			</template>
			<template v-else>
				<NavButton
					v-tooltip.right="formatMessage(commonMessages.settingsLabel)"
					:to="() => $refs.settingsModal.show()"
				>
					<SettingsIcon />
				</NavButton>
			</template>
			<OverflowMenu
				v-if="credentials"
				v-tooltip.right="`Modrinth account`"
				class="w-12 h-12 text-primary rounded-full flex items-center justify-center text-2xl transition-all bg-transparent hover:bg-button-bg hover:text-contrast border-0 cursor-pointer"
				:options="[
					{
						id: 'view-profile',
						action: () => openUrl('https://modrinth.com/user/' + credentials.user.username),
					},
					{
						id: 'sign-out',
						action: () => logOut(),
						color: 'danger',
					},
				]"
				placement="right-end"
			>
				<Avatar :src="credentials.user.avatar_url" alt="" size="32px" circle />
				<template #view-profile>
					<UserIcon />
					<span class="inline-flex items-center gap-1">
						Signed in as
						<span class="inline-flex items-center gap-1 text-contrast font-semibold">
							<Avatar :src="credentials.user.avatar_url" alt="" size="20px" circle />
							{{ credentials.user.username }}
						</span>
					</span>
					<ExternalIcon />
				</template>
				<template #sign-out> <LogOutIcon /> Sign out </template>
			</OverflowMenu>
			<NavButton v-else v-tooltip.right="'Sign in to a Modrinth account'" :to="() => signIn()">
				<LogInIcon class="text-brand" />
			</NavButton>
		</div>
		<div
			v-if="!cinematicShell"
			data-tauri-drag-region
			class="app-grid-statusbar bg-bg-raised h-[--top-bar-height] flex"
		>
			<div data-tauri-drag-region class="flex p-3">
				<div data-tauri-drag-region class="flex items-center gap-1 ml-3">
					<button
						class="cursor-pointer p-0 m-0 text-contrast border-none outline-none bg-button-bg rounded-full flex items-center justify-center w-6 h-6 hover:brightness-75 transition-all"
						@click="router.back()"
					>
						<LeftArrowIcon />
					</button>
					<button
						class="cursor-pointer p-0 m-0 text-contrast border-none outline-none bg-button-bg rounded-full flex items-center justify-center w-6 h-6 hover:brightness-75 transition-all"
						@click="router.forward()"
					>
						<RightArrowIcon />
					</button>
				</div>
				<Breadcrumbs class="pt-[2px]" />
			</div>
			<section data-tauri-drag-region class="flex ml-auto items-center">
				<ButtonStyled
					v-if="!forceSidebar && themeStore.toggleSidebar"
					:type="sidebarToggled ? 'standard' : 'transparent'"
					circular
				>
					<button
						class="mr-3 transition-transform"
						:class="{ 'rotate-180': !sidebarToggled }"
						@click="sidebarToggled = !sidebarToggled"
					>
						<RightArrowIcon />
					</button>
				</ButtonStyled>
				<div class="flex mr-3">
					<Suspense>
						<RunningAppBar />
					</Suspense>
				</div>
				<section v-if="!nativeDecorations" class="window-controls" data-tauri-drag-region-exclude>
					<Button class="titlebar-button" icon-only @click="() => getCurrentWindow().minimize()">
						<MinimizeIcon />
					</Button>
					<Button
						class="titlebar-button"
						icon-only
						@click="() => getCurrentWindow().toggleMaximize()"
					>
						<RestoreIcon v-if="isMaximized" />
						<MaximizeIcon v-else />
					</Button>
					<Button class="titlebar-button close" icon-only @click="handleClose">
						<XIcon />
					</Button>
				</section>
			</section>
		</div>
		<header v-else data-tauri-drag-region class="cinematic-topbar">
			<router-link to="/" class="cinematic-brand" data-tauri-drag-region-exclude>
				<BlockEraLogo aria-hidden="true" />
				<span class="cinematic-brand-copy">
					<strong>BLOCKERA</strong>
					<small>GAME LAUNCHER</small>
				</span>
			</router-link>
			<nav class="cinematic-nav" aria-label="Основная навигация" data-tauri-drag-region-exclude>
				<router-link to="/" class="cinematic-nav-link">
					<HomeIcon />
					<span>Главная</span>
				</router-link>
				<router-link to="/library" class="cinematic-nav-link">
					<CollectionIcon />
					<span>Сборки</span>
				</router-link>
				<router-link :to="modsRoute" class="cinematic-nav-link">
					<BlocksIcon />
					<span>Моды</span>
				</router-link>
				<router-link to="/skins" class="cinematic-nav-link">
					<ChangeSkinIcon />
					<span>Скины</span>
				</router-link>
				<button class="cinematic-nav-link" @click="$refs.settingsModal.show()">
					<SettingsIcon />
					<span>Настройки</span>
				</button>
			</nav>
			<div class="cinematic-topbar-actions" data-tauri-drag-region-exclude>
				<Suspense>
					<RunningAppBar compact />
				</Suspense>
				<Suspense>
					<BlockEraUpdateCenter />
				</Suspense>
				<Suspense>
					<AccountsCard
						mode="small"
						class="cinematic-account"
						@change="handleAccountChange"
					/>
				</Suspense>
				<section v-if="!nativeDecorations" class="window-controls cinematic-window-controls">
					<Button class="titlebar-button" icon-only @click="() => getCurrentWindow().minimize()">
						<MinimizeIcon />
					</Button>
					<Button
						class="titlebar-button"
						icon-only
						@click="() => getCurrentWindow().toggleMaximize()"
					>
						<RestoreIcon v-if="isMaximized" />
						<MaximizeIcon v-else />
					</Button>
					<Button class="titlebar-button close" icon-only @click="handleClose">
						<XIcon />
					</Button>
				</section>
			</div>
		</header>
	</div>
	<div
		v-if="stateInitialized"
		class="app-contents experimental-styles-within"
		:class="{
			'sidebar-enabled': sidebarVisible && !cinematicShell,
			'cinematic-shell-contents': cinematicShell,
			'disable-advanced-rendering': !themeStore.advancedRendering,
		}"
	>
		<div class="app-viewport flex-grow router-view">
			<transition name="popup-survey">
				<div
					v-if="availableSurvey"
					class="w-[400px] z-20 fixed -bottom-12 pb-16 right-[--right-bar-width] mr-4 rounded-t-2xl card-shadow bg-bg-raised border-divider border-[1px] border-solid border-b-0 p-4"
				>
					<h2 class="text-lg font-extrabold mt-0 mb-2">Hey there Modrinth user!</h2>
					<p class="m-0 leading-tight">
						Would you mind answering a few questions about your experience with BlockEra Launcher?
					</p>
					<p class="mt-3 mb-4 leading-tight">
						This feedback will go directly to the Modrinth team and help guide future updates!
					</p>
					<div class="flex gap-2">
						<ButtonStyled color="brand">
							<button @click="openSurvey"><NotepadTextIcon /> Take survey</button>
						</ButtonStyled>
						<ButtonStyled>
							<button @click="dismissSurvey"><XIcon /> No thanks</button>
						</ButtonStyled>
					</div>
				</div>
			</transition>
			<div
				class="loading-indicator-container h-8 fixed z-50"
				:style="{
					top: 'calc(var(--top-bar-height))',
					left: 'calc(var(--left-bar-width))',
					width: 'calc(100% - var(--left-bar-width) - var(--right-bar-width))',
				}"
			>
				<ModrinthLoadingIndicator />
			</div>
			<div
				v-if="themeStore.featureFlags.page_path"
				class="absolute bottom-0 left-0 m-2 bg-tooltip-bg text-tooltip-text font-semibold rounded-full px-2 py-1 text-xs z-50"
			>
				{{ route.fullPath }}
			</div>
			<div
				id="background-teleport-target"
				class="absolute h-full -z-10 rounded-tl-[--radius-xl] overflow-hidden"
				:style="{
					width: 'calc(100% - var(--right-bar-width))',
				}"
			></div>
			<Admonition
				v-if="criticalErrorMessage"
				type="critical"
				:header="criticalErrorMessage.header"
				class="m-6 mb-0"
			>
				<div
					class="markdown-body text-primary"
					v-html="renderString(criticalErrorMessage.body ?? '')"
				></div>
			</Admonition>
			<Admonition
				v-if="authUnreachable"
				type="warning"
				:header="formatMessage(messages.authUnreachableHeader)"
				class="m-6 mb-0"
			>
				{{ formatMessage(messages.authUnreachableBody) }}
			</Admonition>
			<RouterView v-slot="{ Component }">
				<template v-if="Component">
					<Suspense @pending="loading.startLoading()" @resolve="loading.stopLoading()">
						<component :is="Component"></component>
					</Suspense>
				</template>
			</RouterView>
		</div>
		<div
			v-if="!cinematicShell"
			class="app-sidebar mt-px shrink-0 flex flex-col border-0 border-l-[1px] border-[--brand-gradient-border] border-solid overflow-auto"
			:class="{ 'has-plus': hasPlus }"
		>
			<div
				class="app-sidebar-scrollable flex-grow shrink overflow-y-auto relative"
				:class="{ 'pb-12': !hasPlus }"
			>
				<div id="sidebar-teleport-target" class="sidebar-teleport-content"></div>
				<div class="sidebar-default-content" :class="{ 'sidebar-enabled': sidebarVisible }">
					<div
						class="p-4 pr-1 border-0 border-b-[1px] border-[--brand-gradient-border] border-solid"
					>
						<h3 class="text-base text-primary font-medium m-0">Playing as</h3>
						<suspense>
							<AccountsCard ref="accounts" mode="small" @change="handleAccountChange" />
						</suspense>
					</div>
					<div class="py-4 border-0 border-b-[1px] border-[--brand-gradient-border] border-solid">
						<suspense>
							<FriendsList
								:credentials="credentials"
								:sign-in="() => signIn()"
								:refresh-credentials="fetchCredentials"
							/>
						</suspense>
					</div>
					<div v-if="news && news.length > 0" class="p-4 pr-1 flex flex-col items-center">
						<h3 class="text-base mb-4 text-primary font-medium m-0 text-left w-full">News</h3>
						<div class="space-y-4 flex flex-col items-center w-full">
							<NewsArticleCard
								v-for="(item, index) in news"
								:key="`news-${index}`"
								:article="item"
							/>
							<ButtonStyled color="brand" size="large">
								<a href="https://modrinth.com/news" target="_blank" class="my-4">
									<NewspaperIcon /> View all news
								</a>
							</ButtonStyled>
						</div>
					</div>
				</div>
			</div>
		</div>
	</div>
	<URLConfirmModal ref="urlModal" />
	<NotificationPanel has-sidebar />
	<ErrorModal ref="errorModal" />
	<ModInstallModal ref="modInstallModal" />
	<IncompatibilityWarningModal ref="incompatibilityWarningModal" />
	<InstallConfirmModal ref="installConfirmModal" />
</template>

<style lang="scss" scoped>
@import '../../../packages/assets/styles/neon-icon.scss';
@import '../../../packages/assets/styles/neon-text.scss';
.window-controls {
	z-index: 20;
	display: none;
	flex-direction: row;
	align-items: center;

	.titlebar-button {
		display: flex;
		align-items: center;
		justify-content: center;
		cursor: pointer;
		transition: all ease-in-out 0.1s;
		background-color: transparent;
		color: var(--color-base);
		height: 100%;
		width: 3rem;
		position: relative;
		box-shadow: none;

		&:last-child {
			padding-right: 0.75rem;
			width: 3.75rem;
		}

		svg {
			width: 1.25rem;
			height: 1.25rem;
		}

		&::before {
			content: '';
			border-radius: 999999px;
			width: 3rem;
			height: 3rem;
			aspect-ratio: 1 / 1;
			margin-block: auto;
			position: absolute;
			background-color: transparent;
			scale: 0.9;
			transition: all ease-in-out 0.2s;
			z-index: -1;
		}

		&.close {
			&:hover,
			&:active {
				color: var(--color-accent-contrast);

				&::before {
					background-color: var(--color-red);
				}
			}
		}

		&:hover,
		&:active {
			color: var(--color-contrast);

			&::before {
				background-color: var(--color-button-bg);
				scale: 1;
			}
		}
	}
}

.app-grid-layout,
.app-contents {
	--top-bar-height: 3rem;
	--left-bar-width: 4rem;
	--right-bar-width: 300px;
}

.app-grid-layout:has(.cinematic-topbar),
.app-contents.cinematic-shell-contents {
	--top-bar-height: 4.75rem;
	--left-bar-width: 0rem;
	--right-bar-width: 0rem;
}

.cinematic-topbar {
	grid-area: status;
	z-index: 30;
	display: flex;
	align-items: center;
	min-width: 0;
	height: var(--top-bar-height);
	padding-left: 1.25rem;
	overflow: hidden;
	background: rgba(7, 10, 17, 0.94);
	border-bottom: 1px solid rgba(255, 255, 255, 0.08);
	box-shadow: 0 12px 30px rgba(0, 0, 0, 0.2);
	backdrop-filter: blur(20px);
}

.cinematic-brand {
	display: inline-flex;
	align-items: center;
	gap: 0.7rem;
	min-width: 15.5rem;
	flex: 0 0 auto;
	color: var(--color-contrast);
	text-decoration: none;
}

.cinematic-brand > svg {
	width: 2.8rem;
	height: 2.8rem;
	color: var(--color-brand);
	filter: drop-shadow(0 0 14px rgba(168, 85, 247, 0.18));
}

.cinematic-brand-copy {
	display: flex;
	flex-direction: column;
	line-height: 1;
	letter-spacing: 0.08em;
}

.cinematic-brand-copy strong {
	font-size: 1.22rem;
	font-weight: 850;
}

.cinematic-brand-copy small {
	margin-top: 0.28rem;
	font-size: 0.56rem;
	color: var(--color-secondary);
	letter-spacing: 0.24em;
}

.cinematic-nav {
	display: flex;
	min-width: 0;
	flex: 1 1 auto;
	align-items: center;
	gap: 0.45rem;
	height: 100%;
}

.cinematic-nav-link {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	gap: 0.65rem;
	min-width: 7.35rem;
	flex: 0 1 auto;
	height: 2.75rem;
	padding: 0 1rem;
	border: 1px solid transparent;
	border-radius: 0.65rem;
	background: transparent;
	box-shadow: none;
	color: var(--color-base);
	font: inherit;
	text-decoration: none;
	cursor: pointer;
	transition:
		transform 180ms cubic-bezier(0.4, 0, 0.2, 1),
		color 180ms cubic-bezier(0.4, 0, 0.2, 1),
		background-color 180ms cubic-bezier(0.4, 0, 0.2, 1),
		border-color 180ms cubic-bezier(0.4, 0, 0.2, 1);
}

.cinematic-nav-link svg {
	width: 1.25rem;
	height: 1.25rem;
}

.cinematic-nav-link:hover {
	transform: translateY(-1px);
	color: var(--color-contrast);
	background: rgba(255, 255, 255, 0.05);
}

.cinematic-nav-link.router-link-active {
	color: #e9d5ff;
	background: rgba(116, 44, 220, 0.13);
	border-color: rgba(168, 85, 247, 0.36);
	box-shadow: 0 0 24px rgba(126, 34, 206, 0.13);
}

.cinematic-topbar-actions {
	display: flex;
	align-items: center;
	gap: 0.7rem;
	min-width: max-content;
	flex: 0 0 auto;
	margin-left: auto;
}

.cinematic-account {
	width: 13.5rem;
	max-width: 13.5rem;
	margin-top: 0 !important;
}

.cinematic-window-controls {
	flex: 0 0 auto;
	height: var(--top-bar-height);
}

@media (max-width: 1400px) {
	.cinematic-brand {
		min-width: auto;
		margin-right: 0.5rem;
	}

	.cinematic-brand-copy,
	.cinematic-nav-link span {
		display: none;
	}

	.cinematic-nav-link {
		min-width: 2.75rem;
		padding: 0;
	}
}

@media (max-width: 1024px) {
	.cinematic-account {
		width: 3.5rem;
		max-width: 3.5rem;
	}
}

@media (max-width: 800px) {
	.cinematic-topbar {
		padding-left: 0.5rem;
	}

	.cinematic-brand,
	.cinematic-nav {
		display: none;
	}

	.cinematic-topbar-actions {
		width: 100%;
		min-width: 0;
		gap: 0.4rem;
	}

	.cinematic-account {
		flex: 1 1 auto;
		min-width: 0;
	}

	.cinematic-topbar-actions :deep(.account-trigger) {
		width: min(270px, calc(100vw - 17rem));
		min-width: 0;
	}
}

@media (max-width: 600px) {
	.cinematic-topbar {
		padding-left: 0.25rem;
	}

	.cinematic-topbar-actions {
		gap: 0.25rem;
	}
}

.app-contents.cinematic-shell-contents {
	left: 0;
	top: var(--top-bar-height);
	height: calc(100vh - var(--top-bar-height));
	border-radius: 0;
	grid-template-columns: 1fr;
	background: #050912;
}

.app-contents.cinematic-shell-contents::before {
	display: none;
}

.app-contents.cinematic-shell-contents .app-viewport {
	overflow-y: auto;
	overflow-x: hidden;
}

.app-grid-layout {
	display: grid;
	grid-template: 'status status' 'nav dummy';
	grid-template-columns: auto 1fr;
	grid-template-rows: auto 1fr;
	position: relative;
	//z-index: 0;
	background-color: var(--color-raised-bg);
	height: 100vh;
}

.app-grid-navbar {
	grid-area: nav;
}

.app-grid-statusbar {
	grid-area: status;
}

[data-tauri-drag-region-exclude] {
	-webkit-app-region: no-drag;
}

.app-contents {
	position: absolute;
	z-index: 1;
	left: var(--left-bar-width);
	top: var(--top-bar-height);
	right: 0;
	bottom: 0;
	height: calc(100vh - var(--top-bar-height));
	background-color: var(--color-bg);
	border-top-left-radius: var(--radius-xl);

	display: grid;
	grid-template-columns: 1fr 0px;
	// transition: grid-template-columns 0.4s ease-in-out;

	&.sidebar-enabled {
		grid-template-columns: 1fr 300px;
	}
}

.loading-indicator-container {
	border-top-left-radius: var(--radius-xl);
	overflow: hidden;
}

.app-sidebar {
	overflow: visible;
	width: 300px;
	position: relative;
	height: calc(100vh - var(--top-bar-height));
	background: var(--brand-gradient-bg);

	--color-button-bg: var(--brand-gradient-button);
	--color-button-bg-hover: var(--brand-gradient-border);
	--color-divider: var(--brand-gradient-border);
	--color-divider-dark: var(--brand-gradient-border);
}

.app-sidebar::before {
	content: '';
	box-shadow: -15px 0 15px -15px rgba(0, 0, 0, 0.1) inset;
	top: 0;
	bottom: 0;
	left: -2rem;
	width: 2rem;
	position: absolute;
	pointer-events: none;
}

.app-viewport {
	flex-grow: 1;
	height: 100%;
	overflow: auto;
	overflow-x: hidden;
}

.app-contents::before {
	z-index: 1;
	content: '';
	position: fixed;
	left: var(--left-bar-width);
	top: var(--top-bar-height);
	right: calc(-1 * var(--left-bar-width));
	bottom: calc(-1 * var(--left-bar-width));
	border-radius: var(--radius-xl);
	box-shadow: 1px 1px 15px rgba(0, 0, 0, 0.1) inset;
	border-color: var(--surface-5);
	border-width: 1px;
	border-style: solid;
	pointer-events: none;
}

.sidebar-teleport-content {
	display: contents;
}

.sidebar-default-content {
	display: none;
}

.sidebar-teleport-content:empty + .sidebar-default-content.sidebar-enabled {
	display: contents;
}

.popup-survey-enter-active {
	transition:
		opacity 0.25s ease,
		transform 0.25s cubic-bezier(0.51, 1.08, 0.35, 1.15);
	transform-origin: top center;
}

.popup-survey-leave-active {
	transition:
		opacity 0.25s ease,
		transform 0.25s cubic-bezier(0.68, -0.17, 0.23, 0.11);
	transform-origin: top center;
}

.popup-survey-enter-from,
.popup-survey-leave-to {
	opacity: 0;
	transform: translateY(10rem) scale(0.8) scaleY(1.6);
}

.toast-enter-active {
	transition: opacity 0.25s linear;
}

.toast-enter-from,
.toast-leave-to {
	opacity: 0;
}

@media (prefers-reduced-motion: no-preference) {
	.toast-enter-active,
	.nav-button-animated-enter-active {
		transition: all 0.5s cubic-bezier(0.15, 1.4, 0.64, 0.96);
	}

	.toast-leave-active,
	.nav-button-animated-leave-active {
		transition: all 0.25s ease;
	}

	.toast-enter-from {
		scale: 0.5;
		translate: 0 -10rem;
		opacity: 0;
	}

	.toast-leave-to {
		scale: 0.96;
		translate: 20rem 0;
		opacity: 0;
	}

	.nav-button-animated-enter-active {
		position: relative;
	}

	.nav-button-animated-enter-active::before {
		content: '';
		inset: 0;
		border-radius: 100vw;
		background-color: var(--color-brand-highlight);
		position: absolute;
		animation: pop 0.5s ease-in forwards;
		opacity: 0;
	}

	@keyframes pop {
		0% {
			scale: 0.5;
		}
		50% {
			opacity: 0.5;
		}
		100% {
			scale: 1.5;
		}
	}

	.nav-button-animated-enter-from {
		scale: 0.5;
		translate: -2rem 0;
		opacity: 0;
	}

	.nav-button-animated-leave-to {
		scale: 0.75;
		opacity: 0;
	}

	.fade-enter-active {
		transition: 0.25s ease-in-out;
	}

	.fade-enter-from {
		opacity: 0;
	}
}
</style>
<style>
.mac {
	.app-grid-statusbar {
		padding-left: 5rem;
	}
}

.windows {
	.fake-appbar {
		height: 2.5rem !important;
	}

	.window-controls {
		display: flex !important;
	}

	.info-card {
		right: 8rem;
	}

	.profile-card {
		right: 8rem;
	}
}
</style>
<style src="vue-multiselect/dist/vue-multiselect.css"></style>
