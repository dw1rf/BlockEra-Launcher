<template>
	<button
		v-if="mode !== 'isolated'"
		ref="button"
		type="button"
		class="account-trigger"
		:class="{ expanded: mode === 'expanded' }"
		@click="toggleMenu"
	>
		<Avatar
			size="36px"
			:src="
				selectedAccount ? avatarUrl : 'https://launcher-files.modrinth.com/assets/steve_head.png'
			"
		/>
		<div class="account-trigger-copy">
			<strong>
				<component
					:is="getAccountType(selectedAccount)"
					v-if="selectedAccount"
					class="vector-icon"
				/>
				{{ selectedAccount ? selectedAccount.profile.name : 'Выбрать аккаунт' }}
			</strong>
			<span>{{ selectedAccount ? 'Готов к запуску' : 'Требуется вход' }}</span>
		</div>
		<DropdownIcon class="account-trigger-chevron" :class="{ open: showCard }" />
	</button>
	<Teleport to="body">
		<transition name="account-popover">
			<div
				v-if="showCard && mode !== 'isolated'"
				ref="card"
				class="account-popover"
				:style="popoverStyle"
				role="menu"
			>
				<div class="account-popover-heading">
					<div><span>АККАУНТ ДЛЯ ИГРЫ</span><strong>Быстрое переключение</strong></div>
					<span class="account-count">{{ accounts.length }}</span>
				</div>
				<button
					v-for="account in accounts"
					:key="account.profile.id"
					type="button"
					class="account-choice"
					:class="{ active: account.profile.id === selectedAccount?.profile.id }"
					@click="selectAndClose(account)"
				>
					<Avatar :src="getAccountAvatarUrl(account)" size="36px" />
					<span><strong>{{ account.profile.name }}</strong><small>Аккаунт Minecraft</small></span>
					<span class="account-check">{{ account.profile.id === selectedAccount?.profile.id ? '✓' : '' }}</span>
				</button>
				<div v-if="accounts.length === 0" class="account-empty">
					<strong>Аккаунты не добавлены</strong>
					<span>Добавьте Microsoft, Ely.by или офлайн-профиль.</span>
				</div>
				<button type="button" class="manage-accounts" @click.stop="showAccountManager">
					Управление аккаунтами
					<DropdownIcon />
				</button>
			</div>
		</transition>
	</Teleport>
	<Teleport to="body">
	<ModalWrapper ref="accountManagerModal" class="account-manager-modal" header="Аккаунты BlockEra">
		<div class="account-manager-body">
			<div class="account-manager-intro">
				<span>ПРОФИЛИ MINECRAFT</span>
				<h2>Выберите способ входа</h2>
				<p>Активный аккаунт будет использоваться при следующем запуске игры.</p>
			</div>
			<div class="account-provider-grid">
				<button type="button" :disabled="microsoftLoginDisabled" @click="login()">
					<MicrosoftIcon v-if="!microsoftLoginDisabled" /><SpinnerIcon v-else class="animate-spin" />
					<span><strong>Microsoft</strong><small>Лицензионный аккаунт</small></span>
				</button>
				<button type="button" @click="showOfflineLoginModal()">
					<OfflineIcon /><span><strong>Офлайн</strong><small>Локальное имя игрока</small></span>
				</button>
				<button type="button" :disabled="elyByLoginDisabled" @click="showElyByLoginModal()">
					<ElyByIcon v-if="!elyByLoginDisabled" /><SpinnerIcon v-else class="animate-spin" />
					<span><strong>Ely.by</strong><small>Аккаунт Ely.by</small></span>
				</button>
			</div>
			<div v-if="accounts.length" class="managed-account-list">
				<div v-for="account in accounts" :key="account.profile.id" class="managed-account-row">
					<button type="button" class="managed-account-select" @click="setAccount(account)">
						<Avatar :src="getAccountAvatarUrl(account)" size="40px" />
						<span><strong>{{ account.profile.name }}</strong><small>{{ account.profile.id === selectedAccount?.profile.id ? 'Используется сейчас' : 'Выбрать аккаунт' }}</small></span>
					</button>
					<button type="button" class="managed-account-delete" aria-label="Удалить аккаунт" @click="logout(account.profile.id)"><TrashIcon /></button>
				</div>
			</div>
		</div>
	</ModalWrapper>
	<ModalWrapper ref="addElyByModal" class="modal" header="Вход через Ely.by">
		<ModalWrapper
			ref="requestElyByTwoFactorCodeModal"
			class="modal"
			header="Двухфакторная аутентификация Ely.by"
		>
			<div class="flex flex-col gap-4 px-6 py-5">
				<label class="label">Введите код подтверждения</label>
				<input
					v-model="elyByTwoFactorCode"
					type="text"
					placeholder="Код 2FA"
					class="input"
				/>
				<div class="mt-6 ml-auto">
					<Button
						:disabled="elyByLoginDisabled"
						icon-only
						color="primary"
						class="continue-button"
						@click="addElyByProfile()"
					>
						Продолжить
					</Button>
				</div>
			</div>
		</ModalWrapper>
		<div class="flex flex-col gap-4 px-6 py-5">
			<label class="label">Имя игрока или email</label>
			<input
				v-model="elyByLogin"
				type="text"
				placeholder="Имя или email"
				class="input"
			/>
			<label class="label">Пароль</label>
			<input
				v-model="elyByPassword"
				type="password"
				placeholder="Пароль Ely.by"
				class="input"
			/>
			<div class="mt-6 ml-auto">
				<Button
					:disabled="elyByLoginDisabled"
					icon-only
					color="primary"
					class="continue-button"
					@click="addElyByProfile()"
				>
					Войти
				</Button>
			</div>
		</div>
	</ModalWrapper>
	<ModalWrapper ref="addOfflineModal" class="modal" header="Добавить офлайн-аккаунт">
		<div class="flex flex-col gap-4 px-6 py-5">
			<label class="label">Имя игрока</label>
			<input
				v-model="offlinePlayerName"
				type="text"
				placeholder="Например, Steve"
				class="input"
			/>
			<div class="mt-6 ml-auto">
				<Button icon-only color="primary" class="continue-button" @click="addOfflineProfile()">
					Добавить
				</Button>
			</div>
		</div>
	</ModalWrapper>
	<ModalWrapper
		ref="authenticationElyByErrorModal"
		class="modal"
		header="Error while proceeding authentication event with Ely.by"
	>
		<div class="flex flex-col gap-4 px-6 py-5">
			<label class="text-base font-medium text-red-700">
				An error occurred while logging in.
			</label>

			<div class="mt-6 ml-auto">
				<Button color="primary" class="retry-button" @click="retryAddElyByProfile">
					Try again
				</Button>
			</div>
		</div>
	</ModalWrapper>
	<ModalWrapper
		ref="inputElyByErrorModal"
		class="modal"
		header="Error while proceeding input event with Ely.by"
	>
		<div class="flex flex-col gap-4 px-6 py-5">
			<label class="text-base font-medium text-red-700">
				An error occurred while adding the Ely.by account. Please follow the instructions below.
			</label>

			<ul class="list-disc list-inside text-sm space-y-1">
				<li>Check that you have entered the correct player name or email.</li>
				<li>Check that you have entered the correct password.</li>
			</ul>

			<div class="mt-6 ml-auto">
				<Button color="primary" class="retry-button" @click="retryAddElyByProfile">
					Try again
				</Button>
			</div>
		</div>
	</ModalWrapper>
	<ModalWrapper
		ref="inputOfflineErrorModal"
		class="modal"
		header="Error while proceeding input event with offline account"
	>
		<div class="flex flex-col gap-4 px-6 py-5">
			<label class="text-base font-medium text-red-700">
				An error occurred while adding the offline account. Please follow the instructions below.
			</label>

			<ul class="list-disc list-inside text-sm space-y-1">
				<li>Check that you have entered the correct player name.</li>
				<li>
					Player name must be at least {{ minOfflinePlayerNameLength }} characters long and no more
					than {{ maxOfflinePlayerNameLength }} characters.
				</li>
				<li>Make sure your name meets the format requirement `{{ nameExp }}`</li>
			</ul>

			<div class="mt-6 ml-auto">
				<Button color="primary" class="retry-button" @click="retryAddOfflineProfile">
					Try again
				</Button>
			</div>
		</div>
	</ModalWrapper>
	<ModalWrapper ref="unexpectedErrorModal" class="modal" header="Unexpected error occurred">
		<div class="modal-body">
			<label class="label">An unexpected error has occurred. Please try again later.</label>
		</div>
	</ModalWrapper>
	</Teleport>
</template>

<script setup>
import {
	DropdownIcon,
	ElyByIcon,
	MicrosoftIcon,
	OfflineIcon,
	SpinnerIcon,
	TrashIcon,
} from '@modrinth/assets'
import { Avatar, Button, injectNotificationManager } from '@modrinth/ui'
import { computed, nextTick, onBeforeUnmount, onMounted, onUnmounted, ref } from 'vue'

import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import { trackEvent } from '@/helpers/analytics'
import {
	elyby_auth_authenticate,
	elyby_login,
	get_default_user,
	login as login_flow,
	offline_login,
	remove_user,
	set_default_user,
	users,
} from '@/helpers/auth'
import { process_listener } from '@/helpers/events'
import { getPlayerHeadUrl } from '@/helpers/rendering/batch-skin-renderer.ts'
import { get_available_skins } from '@/helpers/skins'
import { handleSevereError } from '@/store/error.js'

const { handleError } = injectNotificationManager()

defineProps({
	mode: {
		type: String,
		required: true,
		default: 'normal',
	},
})

const emit = defineEmits(['change'])

const accounts = ref({})
const microsoftLoginDisabled = ref(false)
const elyByLoginDisabled = ref(false)
const defaultUser = ref()

// This code is modified by AstralRinth
const clientToken = 'blockera-launcher'
const addOfflineModal = ref(null)
const addElyByModal = ref(null)
const requestElyByTwoFactorCodeModal = ref(null)
const authenticationElyByErrorModal = ref(null)
const inputElyByErrorModal = ref(null)
const inputOfflineErrorModal = ref(null)
const unexpectedErrorModal = ref(null)
const offlinePlayerName = ref('')
const elyByLogin = ref('')
const elyByPassword = ref('')
const elyByTwoFactorCode = ref('')
const minOfflinePlayerNameLength = 3
const maxOfflinePlayerNameLength = 20
const nameExp = 'a-zA-Z0-9_'
const nameRegex = new RegExp(`^[${nameExp}]+$`)

// This code is modified by AstralRinth
function getAccountType(account) {
	switch (account.account_type) {
		case 'microsoft':
			return MicrosoftIcon
		case 'pirate':
			return OfflineIcon
		case 'elyby':
			return ElyByIcon
	}
}

// This code is modified by AstralRinth
function showOfflineLoginModal() {
	addOfflineModal.value?.show()
}

// This code is modified by AstralRinth
function showElyByLoginModal() {
	addElyByModal.value?.show()
}

// This code is modified by AstralRinth
function retryAddOfflineProfile() {
	inputOfflineErrorModal.value?.hide()
	clearOfflineFields()
	showOfflineLoginModal()
}

// This code is modified by AstralRinth
function retryAddElyByProfile() {
	authenticationElyByErrorModal.value?.hide()
	inputElyByErrorModal.value?.hide()
	elyByLoginDisabled.value = false
	clearElyByFields()
	showElyByLoginModal()
}

// This code is modified by AstralRinth
function clearElyByFields() {
	elyByLogin.value = ''
	elyByPassword.value = ''
	elyByTwoFactorCode.value = ''
}

// This code is modified by AstralRinth
function clearOfflineFields() {
	offlinePlayerName.value = ''
}

// This code is modified by AstralRinth
async function addOfflineProfile() {
	const name = offlinePlayerName.value.trim()
	const isValidName =
		nameRegex.test(name) &&
		name.length >= minOfflinePlayerNameLength &&
		name.length <= maxOfflinePlayerNameLength

	if (!isValidName) {
		addOfflineModal.value?.hide()
		inputOfflineErrorModal.value?.show()
		clearOfflineFields()
		return
	}

	try {
		const result = await offline_login(name)

		addOfflineModal.value?.hide()

		if (result) {
			await setAccount(result)
			await refreshValues()
		} else {
			unexpectedErrorModal.value?.show()
		}
	} catch (error) {
		handleError(error)
		unexpectedErrorModal.value?.show()
	} finally {
		clearOfflineFields()
	}
}

// This code is modified by AstralRinth
async function addElyByProfile() {
	elyByLoginDisabled.value = true
	if (!elyByLogin.value || !elyByPassword.value) {
		addElyByModal.value?.hide()
		inputElyByErrorModal.value?.show()
		clearElyByFields()
		return
	}

	// Parse ely.by credential fields
	const login = elyByLogin.value.trim()
	let password = elyByPassword.value.trim()
	const twoFactorCode = elyByTwoFactorCode.value.trim()
	if (password && twoFactorCode) {
		password = `${password}:${twoFactorCode}`
	}

	try {
		const raw_result = await elyby_auth_authenticate(login, password, clientToken)

		const json_data = JSON.parse(raw_result)

		console.log(json_data?.error)
		console.log(json_data?.errorMessage)

		if (!json_data.accessToken) {
			if (
				json_data.error === 'ForbiddenOperationException' &&
				json_data.errorMessage?.includes('two factor')
			) {
				requestElyByTwoFactorCodeModal.value?.show()
				return
			}

			addElyByModal.value?.hide()
			requestElyByTwoFactorCodeModal.value?.hide()
			authenticationElyByErrorModal.value?.show()
			return
		}

		const accessToken = json_data.accessToken
		const selectedProfileId = convertRawStringToUUIDv4(json_data.selectedProfile.id)
		const selectedProfileName = json_data.selectedProfile.name

		const result = await elyby_login(selectedProfileId, selectedProfileName, accessToken)

		addElyByModal.value?.hide()
		requestElyByTwoFactorCodeModal.value?.hide()

		clearElyByFields()

		await setAccount(result)
		await refreshValues()
	} catch (err) {
		handleError(err)
		unexpectedErrorModal.value?.show()
	} finally {
		elyByLoginDisabled.value = false
	}
}

// This code is modified by AstralRinth
function convertRawStringToUUIDv4(rawId) {
	if (rawId.length !== 32) {
		console.warn('Invalid UUID string:', rawId)
		return rawId
	}
	return `${rawId.slice(0, 8)}-${rawId.slice(8, 12)}-${rawId.slice(12, 16)}-${rawId.slice(16, 20)}-${rawId.slice(20)}`
}

const equippedSkin = ref(null)
const headUrlCache = ref(new Map())

async function refreshValues() {
	defaultUser.value = await get_default_user().catch(handleError)
	accounts.value = await users().catch(handleError)

	try {
		const skins = await get_available_skins()
		equippedSkin.value = skins.find((skin) => skin.is_equipped)

		if (equippedSkin.value) {
			try {
				const headUrl = await getPlayerHeadUrl(equippedSkin.value)
				headUrlCache.value.set(equippedSkin.value.texture_key, headUrl)
			} catch (error) {
				console.warn('Failed to get head render for equipped skin:', error)
			}
		}
	} catch {
		equippedSkin.value = null
	}
}

function setLoginDisabled(value) {
	microsoftLoginDisabled.value = value
}

defineExpose({
	refreshValues,
	setLoginDisabled,
	microsoftLoginDisabled,
})
await refreshValues()

const avatarUrl = computed(() => {
	if (equippedSkin.value?.texture_key) {
		const cachedUrl = headUrlCache.value.get(equippedSkin.value.texture_key)
		if (cachedUrl) {
			return cachedUrl
		}
		return `https://mc-heads.net/avatar/${equippedSkin.value.texture_key}/128`
	}
	if (selectedAccount.value?.profile?.id) {
		return `https://mc-heads.net/avatar/${selectedAccount.value.profile.id}/128`
	}
	return 'https://launcher-files.modrinth.com/assets/steve_head.png'
})

function getAccountAvatarUrl(account) {
	if (
		account.profile.id === selectedAccount.value?.profile?.id &&
		equippedSkin.value?.texture_key
	) {
		const cachedUrl = headUrlCache.value.get(equippedSkin.value.texture_key)
		if (cachedUrl) {
			return cachedUrl
		}
	}
	return `https://mc-heads.net/avatar/${account.profile.id}/128`
}

const selectedAccount = computed(() =>
	accounts.value.find((account) => account.profile.id === defaultUser.value),
)

async function setAccount(account) {
	defaultUser.value = account.profile.id
	await set_default_user(account.profile.id).catch(handleError)
	emit('change')
}

async function login() {
	microsoftLoginDisabled.value = true
	const loggedIn = await login_flow().catch(handleSevereError)

	if (loggedIn) {
		await setAccount(loggedIn)
		await refreshValues()
	}

	trackEvent('AccountLogIn')
	microsoftLoginDisabled.value = false
}

const logout = async (id) => {
	const account = accounts.value.find((entry) => entry.profile.id === id)
	if (!window.confirm(`Удалить аккаунт ${account?.profile.name ?? ''} из BlockEra Launcher?`)) return
	await remove_user(id).catch(handleError)
	await refreshValues()
	if (!selectedAccount.value && accounts.value.length > 0) {
		await setAccount(accounts.value[0])
		await refreshValues()
	} else {
		emit('change')
	}
	trackEvent('AccountLogOut')
}

const showCard = ref(false)
const card = ref(null)
const button = ref(null)
const accountManagerModal = ref(null)
const popoverStyle = ref({})

function updatePopoverPosition() {
	if (!button.value) return
	const rect = button.value.getBoundingClientRect()
	const width = Math.min(340, window.innerWidth - 24)
	const left = Math.max(12, Math.min(rect.right - width, window.innerWidth - width - 12))
	const top = Math.min(rect.bottom + 10, window.innerHeight - 120)
	popoverStyle.value = {
		width: `${width}px`,
		left: `${left}px`,
		top: `${top}px`,
	}
}

async function selectAndClose(account) {
	await setAccount(account)
	showCard.value = false
}

async function showAccountManager() {
	showCard.value = false
	await nextTick()
	requestAnimationFrame(() => accountManagerModal.value?.show())
}

function handleKeydown(event) {
	if (event.key === 'Escape') showCard.value = false
}

const handleClickOutside = (event) => {
	const elements = document.elementsFromPoint(event.clientX, event.clientY)
	if (
		card.value &&
		card.value !== event.target &&
		!elements.includes(card.value) &&
		button.value &&
		!button.value.contains(event.target)
	) {
		toggleMenu(false)
	}
}

function toggleMenu(override = true) {
	if (showCard.value || !override) {
		showCard.value = false
	} else {
		updatePopoverPosition()
		showCard.value = true
	}
}

const unlisten = await process_listener(async (e) => {
	if (e.event === 'launched') {
		await refreshValues()
	}
})

onMounted(() => {
	window.addEventListener('click', handleClickOutside)
	window.addEventListener('keydown', handleKeydown)
	window.addEventListener('resize', updatePopoverPosition)
})

onBeforeUnmount(() => {
	window.removeEventListener('click', handleClickOutside)
	window.removeEventListener('keydown', handleKeydown)
	window.removeEventListener('resize', updatePopoverPosition)
})

onUnmounted(() => {
	unlisten()
})
</script>

<style scoped lang="scss">
.account-trigger {
	min-width: 220px;
	max-width: 270px;
	height: 58px;
	padding: 8px 12px;
	display: flex;
	align-items: center;
	gap: 10px;
	color: #f8f6ff;
	background: rgba(15, 18, 28, 0.82);
	border: 1px solid rgba(180, 120, 255, 0.18);
	border-radius: 15px;
	cursor: pointer;
	box-shadow: 0 12px 30px rgba(0, 0, 0, 0.24);
	transition: border-color 180ms ease, background 180ms ease, transform 180ms ease;

	&:hover {
		background: rgba(28, 23, 43, 0.96);
		border-color: rgba(174, 87, 255, 0.52);
	}

	&:active { transform: scale(0.985); }
}

.account-trigger-copy {
	min-width: 0;
	flex: 1;
	display: flex;
	flex-direction: column;
	align-items: flex-start;

	strong, span {
		max-width: 100%;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	strong { font-size: 14px; }
	span { color: #a8adba; font-size: 11px; margin-top: 2px; }
}

.account-trigger-chevron {
	width: 18px;
	height: 18px;
	transition: transform 180ms ease;
	&.open { transform: rotate(180deg); }
}

.account-popover {
	position: fixed;
	z-index: 10000;
	box-sizing: border-box;
	display: flex;
	flex-direction: column;
	gap: 7px;
	max-height: min(520px, calc(100vh - 90px));
	overflow: auto;
	padding: 12px;
	color: #f8f6ff;
	background: rgba(12, 14, 23, 0.98);
	border: 1px solid rgba(176, 97, 255, 0.3);
	border-radius: 18px;
	box-shadow: 0 24px 70px rgba(0, 0, 0, 0.55), 0 0 36px rgba(122, 46, 230, 0.12);
	backdrop-filter: blur(24px);
}

.account-popover-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 5px 5px 9px;

	div { display: flex; flex-direction: column; }
	span { color: #bb78ff; font-size: 10px; font-weight: 800; letter-spacing: .12em; }
	strong { margin-top: 2px; font-size: 15px; }
	.account-count { min-width: 25px; padding: 5px 7px; text-align: center; background: rgba(139, 65, 229, .2); border-radius: 9px; }
}

.account-choice,
.manage-accounts,
.account-provider-grid button,
.managed-account-select,
.managed-account-delete {
	font: inherit;
	color: inherit;
	border: 0;
	cursor: pointer;
}

.account-choice {
	width: 100%;
	padding: 9px;
	display: flex;
	align-items: center;
	gap: 10px;
	text-align: left;
	background: rgba(255, 255, 255, .035);
	border: 1px solid transparent;
	border-radius: 12px;
	transition: background 160ms ease, border-color 160ms ease;

	&:hover { background: rgba(157, 82, 237, .12); }
	&.active { background: rgba(143, 64, 232, .16); border-color: rgba(184, 108, 255, .36); }
	> span:nth-child(2) { min-width: 0; flex: 1; display: flex; flex-direction: column; }
	strong, small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
	small { margin-top: 2px; color: #9da2b0; font-size: 11px; }
}

.account-check { color: #c77dff; font-size: 17px; font-weight: 900; }
.account-empty { padding: 15px 10px; display: flex; flex-direction: column; gap: 4px; color: #aeb2bf; font-size: 12px; }
.account-empty strong { color: #f8f6ff; font-size: 14px; }

.manage-accounts {
	margin-top: 4px;
	padding: 11px 12px;
	display: flex;
	align-items: center;
	justify-content: space-between;
	color: #d7afff;
	background: linear-gradient(135deg, rgba(114, 45, 211, .22), rgba(161, 72, 244, .12));
	border: 1px solid rgba(174, 94, 255, .28);
	border-radius: 12px;
	font-weight: 750;
	&:hover { background: linear-gradient(135deg, rgba(132, 52, 235, .34), rgba(178, 84, 255, .18)); }
	svg { width: 16px; transform: rotate(-90deg); }
}

.account-manager-body { min-width: min(680px, calc(100vw - 64px)); padding: 24px; color: #f8f6ff; }
.account-manager-intro span { color: #bd76ff; font-size: 11px; font-weight: 800; letter-spacing: .12em; }
.account-manager-intro h2 { margin: 5px 0; font-size: 26px; }
.account-manager-intro p { margin: 0; color: #aeb2bf; }
.account-provider-grid { margin: 20px 0; display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.account-provider-grid button {
	padding: 15px;
	display: flex;
	align-items: center;
	gap: 11px;
	text-align: left;
	background: rgba(255, 255, 255, .035);
	border: 1px solid rgba(255, 255, 255, .08);
	border-radius: 14px;
	transition: border-color 160ms ease, transform 160ms ease;
	&:hover { border-color: rgba(185, 104, 255, .45); transform: translateY(-1px); }
	&:disabled { opacity: .55; cursor: wait; }
	svg { width: 28px; height: 28px; }
	span { min-width: 0; display: flex; flex-direction: column; }
	small { margin-top: 3px; color: #969cab; font-size: 11px; }
}

.managed-account-list { display: flex; flex-direction: column; gap: 8px; }
.managed-account-row { display: flex; gap: 8px; }
.managed-account-select { flex: 1; padding: 10px; display: flex; align-items: center; gap: 11px; text-align: left; background: rgba(255,255,255,.035); border-radius: 13px; }
.managed-account-select span { display: flex; flex-direction: column; }
.managed-account-select small { margin-top: 2px; color: #9da2b0; }
.managed-account-delete { width: 44px; display: grid; place-items: center; color: #ff809f; background: rgba(255, 73, 116, .08); border-radius: 13px; }
.managed-account-delete svg { width: 18px; }

.account-popover-enter-active,
.account-popover-leave-active { transition: opacity 170ms ease, transform 170ms ease; }
.account-popover-enter-from,
.account-popover-leave-to { opacity: 0; transform: translateY(-8px) scale(.98); }

@media (max-width: 1050px) {
	.account-trigger { min-width: 56px; width: 56px; padding: 8px; }
	.account-trigger-copy, .account-trigger-chevron { display: none; }
	.account-provider-grid { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
	.account-trigger,
	.account-trigger-chevron,
	.account-popover-enter-active,
	.account-popover-leave-active,
	.account-provider-grid button { transition-duration: 1ms !important; }
}

.selected {
	background: var(--color-brand-highlight);
	border-radius: var(--radius-lg);
	color: var(--color-contrast);
	gap: 1rem;
}

.login-section {
	background: var(--color-bg);
	border-radius: var(--radius-lg);
	gap: 1rem;
}

.vector-icon {
	width: 12px;
	height: 12px;
}

.account {
	width: max-content;
	display: flex;
	align-items: center;
	text-align: left;
	padding: 0.5rem 1rem;

	h4,
	p {
		margin: 0;
	}
}

.account-card {
	position: fixed;
	display: flex;
	flex-direction: column;
	margin-top: 0.5rem;
	right: 2rem;
	z-index: 11;
	gap: 0.5rem;
	padding: 1rem;
	border: 1px solid var(--color-divider);
	width: max-content;
	user-select: none;
	-ms-user-select: none;
	-webkit-user-select: none;
	max-height: calc(100vh - 300px);
	overflow-y: auto;

	&::-webkit-scrollbar-track {
		border-top-right-radius: 1rem;
		border-bottom-right-radius: 1rem;
	}

	&::-webkit-scrollbar {
		border-top-right-radius: 1rem;
		border-bottom-right-radius: 1rem;
	}

	&.hidden {
		display: none;
	}

	&.expanded {
		left: 13.5rem;
	}

	&.isolated {
		position: relative;
		left: 0;
		top: 0;
	}
}

.accounts-title {
	font-size: 1.2rem;
	font-weight: bolder;
}

.centered {
	display: flex;
	gap: 1rem;
	margin: auto;
}

.account-group {
	width: 100%;
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
}

.option {
	width: calc(100% - 2.25rem);
	background: var(--color-raised-bg);
	color: var(--color-base);
	box-shadow: none;

	img {
		margin-right: 0.5rem;
	}
}

.icon {
	--size: 1.5rem !important;
}

.account-row {
	display: flex;
	flex-direction: row;
	gap: 0.5rem;
	vertical-align: center;
	justify-content: space-between;
	padding-right: 1rem;
}

.fade-enter-active,
.fade-leave-active {
	transition:
		opacity 0.25s ease,
		translate 0.25s ease,
		scale 0.25s ease;
}

.fade-enter-from,
.fade-leave-to {
	opacity: 0;
	translate: 0 -2rem;
	scale: 0.9;
}

.avatar-button {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	color: var(--color-base);
	background-color: var(--color-button-bg);
	border-radius: var(--radius-md);
	width: 100%;
	padding: 0.5rem 0.75rem;
	text-align: left;

	&.expanded {
		border: 1px solid var(--color-divider);
		padding: 1rem;
	}
}

.avatar-text {
	margin: auto 0 auto 0.25rem;
	display: flex;
	flex-direction: column;
}

.text {
	width: 6rem;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.accounts-text {
	display: flex;
	align-items: center;
	gap: 0.25rem;
	margin: 0;
}

.qr-code {
	background-color: white !important;
	border-radius: var(--radius-md);
}

.modal-body {
	display: flex;
	flex-direction: row;
	gap: var(--gap-lg);
	align-items: center;
	padding: var(--gap-xl);

	.modal-text {
		display: flex;
		flex-direction: column;
		gap: var(--gap-sm);
		width: 100%;

		h2,
		p {
			margin: 0;
		}

		.code-text {
			display: flex;
			flex-direction: row;
			gap: var(--gap-xs);
			align-items: center;

			.code {
				background-color: var(--color-bg);
				border-radius: var(--radius-md);
				border: solid 1px var(--color-button-bg);
				font-family: var(--mono-font);
				letter-spacing: var(--gap-md);
				color: var(--color-contrast);
				font-size: 2rem;
				font-weight: bold;
				padding: var(--gap-sm) 0 var(--gap-sm) var(--gap-md);
			}

			.btn {
				width: 2.5rem;
				height: 2.5rem;
			}
		}
	}
}

.button-row {
	display: flex;
	flex-direction: row;
}

.modal {
	position: absolute;
}

.code {
	color: var(--color-brand);
	padding: 0.05rem 0.1rem;
	// row not column
	display: flex;

	.card {
		background: var(--color-base);
		color: var(--color-contrast);
		padding: 0.5rem 1rem;
	}
}
</style>
