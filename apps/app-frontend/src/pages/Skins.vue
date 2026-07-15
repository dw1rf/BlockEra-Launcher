<script setup lang="ts">
import {
	EditIcon,
	ExcitedRinthbot,
	LogInIcon,
	PlusIcon,
	SpinnerIcon,
	TrashIcon,
	UpdatedIcon,
} from '@modrinth/assets'
import {
	Button,
	ButtonStyled,
	ConfirmModal,
	injectNotificationManager,
	SkinButton,
	SkinLikeTextButton,
	SkinPreviewRenderer,
} from '@modrinth/ui'
import { arrayBufferToBase64 } from '@modrinth/utils'
import { computedAsync } from '@vueuse/core'
import type { Ref } from 'vue'
import { computed, inject, onMounted, onUnmounted, ref, useTemplateRef, watch } from 'vue'

import steveSkin from '@/assets/skins/steve.png'
import type AccountsCard from '@/components/ui/AccountsCard.vue'
import EditSkinModal from '@/components/ui/skin/EditSkinModal.vue'
import SelectCapeModal from '@/components/ui/skin/SelectCapeModal.vue'
import UploadSkinModal from '@/components/ui/skin/UploadSkinModal.vue'
import { trackEvent } from '@/helpers/analytics'
import { get_default_user, login as login_flow, users } from '@/helpers/auth'
import type { RenderResult } from '@/helpers/rendering/batch-skin-renderer.ts'
import { generateSkinPreviews, skinBlobUrlMap } from '@/helpers/rendering/batch-skin-renderer.ts'
import { get as getSettings } from '@/helpers/settings.ts'
import type { Cape, Skin, SkinTextureUrl } from '@/helpers/skins.ts'
import {
	equip_skin,
	filterDefaultSkins,
	filterSavedSkins,
	get_available_capes,
	get_available_skins,
	get_normalized_skin_texture,
	normalize_skin_texture,
	remove_custom_skin,
	set_default_cape,
} from '@/helpers/skins.ts'
import { handleSevereError } from '@/store/error'
const editSkinModal = useTemplateRef('editSkinModal')
const selectCapeModal = useTemplateRef('selectCapeModal')
const uploadSkinModal = useTemplateRef('uploadSkinModal')

const notifications = injectNotificationManager()
const { handleError } = notifications

const settings = ref(await getSettings())
const skins = ref<Skin[]>([])
const capes = ref<Cape[]>([])

const accountsCard = inject('accountsCard') as Ref<typeof AccountsCard>
const currentUser = ref(undefined)
const currentUserId = ref<string | undefined>(undefined)

const username = computed(() => currentUser.value?.profile?.name ?? undefined)
const offlineAccount = computed(() => currentUser.value?.account_type === 'pirate')
const selectedSkin = ref<Skin | null>(null)
const defaultCape = ref<Cape>()

const originalSelectedSkin = ref<Skin | null>(null)
const originalDefaultCape = ref<Cape>()

const savedSkins = computed(() => {
	try {
		return filterSavedSkins(skins.value)
	} catch (error) {
		handleError(error as Error)
		return []
	}
})
const defaultSkins = computed(() => filterDefaultSkins(skins.value))

const currentCape = computed(() => {
	if (selectedSkin.value?.cape_id) {
		const overrideCape = capes.value.find((c) => c.id === selectedSkin.value?.cape_id)
		if (overrideCape) {
			return overrideCape
		}
	}
	return defaultCape.value
})

const skinTexture = computedAsync(async () => {
	if (selectedSkin.value?.texture) {
		return await get_normalized_skin_texture(selectedSkin.value)
	} else {
		return ''
	}
})
const capeTexture = computed(() => currentCape.value?.texture)
const skinVariant = computed(() => selectedSkin.value?.variant)
const skinNametag = computed(() =>
	settings.value.hide_nametag_skins_page ? undefined : username.value,
)

let userCheckInterval: number | null = null

const deleteSkinModal = ref()
const skinToDelete = ref<Skin | null>(null)

function confirmDeleteSkin(skin: Skin) {
	skinToDelete.value = skin
	deleteSkinModal.value?.show()
}

async function deleteSkin() {
	if (!skinToDelete.value) return
	await remove_custom_skin(skinToDelete.value).catch(handleError)
	await loadSkins()
	skinToDelete.value = null
}

async function loadCapes() {
	try {
		capes.value = (await get_available_capes()) ?? []
		defaultCape.value = capes.value.find((c) => c.is_equipped)
		originalDefaultCape.value = defaultCape.value
	} catch (error) {
		if (currentUser.value && error instanceof Error) {
			handleError(error)
		}
	}
}

async function loadSkins() {
	try {
		skins.value = (await get_available_skins()) ?? []
		generateSkinPreviews(skins.value, capes.value)
		selectedSkin.value = skins.value.find((s) => s.is_equipped) ?? null
		originalSelectedSkin.value = selectedSkin.value
	} catch (error) {
		if (currentUser.value && error instanceof Error) {
			handleError(error)
		}
	}
}

async function changeSkin(newSkin: Skin) {
	const previousSkin = selectedSkin.value
	const previousSkinsList = [...skins.value]

	skins.value = skins.value.map((skin) => {
		return {
			...skin,
			is_equipped: skin.texture_key === newSkin.texture_key,
		}
	})

	selectedSkin.value = skins.value.find((s) => s.texture_key === newSkin.texture_key) || null

	try {
		await equip_skin(newSkin)
		if (accountsCard.value) {
			await accountsCard.value.refreshValues()
		}
	} catch (error) {
		selectedSkin.value = previousSkin
		skins.value = previousSkinsList

		if ((error as { message?: string })?.message?.includes('429 Too Many Requests')) {
			notifications.addNotification({
				type: 'error',
				title: 'Slow down!',
				text: "You're changing your skin too frequently. Mojang's servers have temporarily blocked further requests. Please wait a moment before trying again.",
			})
		} else {
			handleError(error as Error)
		}
	}
}

async function handleCapeSelected(cape: Cape | undefined) {
	const previousDefaultCape = defaultCape.value
	const previousCapesList = [...capes.value]

	capes.value = capes.value.map((c) => ({
		...c,
		is_equipped: cape ? c.id === cape.id : false,
	}))

	defaultCape.value = cape ? capes.value.find((c) => c.id === cape.id) : undefined

	try {
		await set_default_cape(cape)
	} catch (error) {
		defaultCape.value = previousDefaultCape
		capes.value = previousCapesList

		if ((error as { message?: string })?.message?.includes('429 Too Many Requests')) {
			notifications.addNotification({
				type: 'error',
				title: 'Slow down!',
				text: "You're changing your cape too frequently. Mojang's servers have temporarily blocked further requests. Please wait a moment before trying again.",
			})
		} else {
			handleError(error as Error)
		}
	}
}

async function onSkinSaved() {
	await Promise.all([loadCapes(), loadSkins()])
}

async function loadCurrentUser() {
	try {
		const defaultId = await get_default_user()
		currentUserId.value = defaultId

		const allAccounts = await users()
		currentUser.value = allAccounts.find((acc) => acc.profile.id === defaultId)
	} catch (e) {
		handleError(e as Error)
		currentUser.value = undefined
		currentUserId.value = undefined
	}
}

function getBakedSkinTextures(skin: Skin): RenderResult | undefined {
	const key = `${skin.texture_key}+${skin.variant}+${skin.cape_id ?? 'no-cape'}`
	return skinBlobUrlMap.get(key)
}

async function login() {
	accountsCard.value.setLoginDisabled(true)
	const loggedIn = await login_flow().catch(handleSevereError)

	if (loggedIn && accountsCard) {
		await accountsCard.value.refreshValues()
	}

	trackEvent('AccountLogIn')
	accountsCard.value.setLoginDisabled(false)
}

function openUploadSkinModal(e: MouseEvent) {
	uploadSkinModal.value?.show(e)
}

function onSkinFileUploaded(buffer: ArrayBuffer) {
	const fakeEvent = new MouseEvent('click')
	const originalSkinTexUrl = `data:image/png;base64,` + arrayBufferToBase64(buffer)
	normalize_skin_texture(originalSkinTexUrl).then((skinTextureNormalized: Uint8Array) => {
		const skinTexUrl: SkinTextureUrl = {
			original: originalSkinTexUrl,
			normalized: `data:image/png;base64,` + arrayBufferToBase64(skinTextureNormalized),
		}
		if (editSkinModal.value && editSkinModal.value.shouldRestoreModal) {
			editSkinModal.value.restoreWithNewTexture(skinTexUrl)
		} else {
			editSkinModal.value?.showNew(fakeEvent, skinTexUrl)
		}
	})
}

function onUploadCanceled() {
	editSkinModal.value?.restoreModal()
}

watch(
	() => selectedSkin.value?.cape_id,
	() => {},
)

onMounted(() => {
	userCheckInterval = window.setInterval(checkUserChanges, 250)
})

onUnmounted(() => {
	if (userCheckInterval !== null) {
		window.clearInterval(userCheckInterval)
	}
})

async function checkUserChanges() {
	try {
		const defaultId = await get_default_user()
		if (defaultId !== currentUserId.value) {
			await loadCurrentUser()
			await loadCapes()
			await loadSkins()
		}
	} catch (error) {
		if (currentUser.value && error instanceof Error) {
			handleError(error)
		}
	}
}

await Promise.all([loadCapes(), loadSkins(), loadCurrentUser()])
</script>

<template>
	<EditSkinModal
		ref="editSkinModal"
		:capes="capes"
		:default-cape="defaultCape"
		:offline="offlineAccount"
		@saved="onSkinSaved"
		@deleted="() => loadSkins()"
		@open-upload-modal="openUploadSkinModal"
	/>
	<SelectCapeModal ref="selectCapeModal" :capes="capes" @select="handleCapeSelected" />
	<UploadSkinModal
		ref="uploadSkinModal"
		@uploaded="onSkinFileUploaded"
		@canceled="onUploadCanceled"
	/>
	<ConfirmModal
		ref="deleteSkinModal"
		title="Удалить этот скин?"
		description="Скин будет удалён без возможности восстановления."
		proceed-label="Удалить"
		@proceed="deleteSkin"
	/>

	<main class="skin-studio">
		<header class="studio-hero">
			<div>
				<p class="studio-eyebrow">ПЕРСОНАЛИЗАЦИЯ</p>
				<h1>Гардероб</h1>
				<p>
					Меняйте скин и плащ, сохраняйте любимые образы и переключайтесь между ними в один клик.
				</p>
			</div>
			<button v-if="currentUser" class="upload-action" @click="openUploadSkinModal">
				<PlusIcon /> Добавить скин
			</button>
		</header>
		<div v-if="currentUser && offlineAccount" class="offline-skin-notice">
			<UpdatedIcon />
			<div>
				<strong>Сетевые скины работают через Ely.by</strong>
				<span
					>В мультиплеере BlockEra загружает скин Ely.by по вашему offline-нику. Локальные PNG из
					гардероба остаются доступны для выбора и предпросмотра.</span
				>
			</div>
		</div>

		<div v-if="currentUser" class="skin-layout">
			<div class="preview-panel">
				<div class="panel-heading">
					<div>
						<p>ТЕКУЩИЙ ОБРАЗ</p>
						<h2>{{ username || 'Игрок' }}</h2>
					</div>
					<span class="equipped-pill"><UpdatedIcon /> Активен</span>
				</div>
				<div class="preview-container">
					<SkinPreviewRenderer
						:cape-src="capeTexture"
						:texture-src="skinTexture || steveSkin"
						:variant="skinVariant || 'CLASSIC'"
						:nametag="skinNametag"
						:initial-rotation="Math.PI / 8"
						:scale="1.08"
						:fov="36"
						hint="Потяните, чтобы повернуть"
					>
						<template v-if="!offlineAccount" #subtitle>
							<ButtonStyled :disabled="!!selectedSkin?.cape_id">
								<button
									v-tooltip="
										selectedSkin?.cape_id ? 'Для этого скина уже выбран отдельный плащ.' : undefined
									"
									:disabled="!!selectedSkin?.cape_id"
									@click="
										(e: MouseEvent) =>
											selectCapeModal?.show(
												e,
												selectedSkin?.texture_key,
												currentCape,
												skinTexture,
												skinVariant,
											)
									"
								>
									<UpdatedIcon />
									Настроить плащ
								</button>
							</ButtonStyled>
						</template>
					</SkinPreviewRenderer>
				</div>
				<div class="preview-footer">
					<div>
						<span>Модель</span
						><strong>{{ skinVariant === 'SLIM' ? 'Тонкие руки' : 'Классическая' }}</strong>
					</div>
					<div>
						<span>{{ offlineAccount ? 'Аккаунт' : 'Плащ' }}</span
						><strong>{{ offlineAccount ? 'Offline' : currentCape?.name || 'Не выбран' }}</strong>
					</div>
				</div>
			</div>

			<div class="skins-container">
				<section class="skin-section">
					<div class="library-heading">
						<div>
							<p>ВАША КОЛЛЕКЦИЯ</p>
							<h2>Сохранённые скины</h2>
						</div>
						<span>{{ savedSkins.length }} сохранено</span>
					</div>
					<div class="skin-card-grid">
						<SkinLikeTextButton class="skin-card" @click="openUploadSkinModal">
							<template #icon>
								<PlusIcon class="size-8" />
							</template>
							<span>Новый скин</span>
						</SkinLikeTextButton>

						<SkinButton
							v-for="skin in savedSkins"
							:key="`saved-skin-${skin.texture_key}`"
							class="skin-card"
							:forward-image-src="getBakedSkinTextures(skin)?.forwards"
							:backward-image-src="getBakedSkinTextures(skin)?.backwards"
							:selected="selectedSkin === skin"
							@select="changeSkin(skin)"
						>
							<template #overlay-buttons>
								<Button
									color="green"
									aria-label="Изменить скин"
									class="pointer-events-auto"
									@click.stop="(e: MouseEvent) => editSkinModal?.show(e, skin)"
								>
									<EditIcon /> Изменить
								</Button>
								<Button
									v-show="!skin.is_equipped"
									v-tooltip="'Удалить скин'"
									aria-label="Удалить скин"
									color="red"
									class="!rounded-[100%] pointer-events-auto"
									icon-only
									@click.stop="() => confirmDeleteSkin(skin)"
								>
									<TrashIcon />
								</Button>
							</template>
						</SkinButton>
					</div>
				</section>

				<section class="skin-section defaults-section">
					<div class="library-heading">
						<div>
							<p>БАЗОВЫЕ ОБРАЗЫ</p>
							<h2>Стандартные скины</h2>
						</div>
						<span>{{ defaultSkins.length }} доступно</span>
					</div>
					<div class="skin-card-grid">
						<SkinButton
							v-for="skin in defaultSkins"
							:key="`default-skin-${skin.texture_key}`"
							class="skin-card"
							:forward-image-src="getBakedSkinTextures(skin)?.forwards"
							:backward-image-src="getBakedSkinTextures(skin)?.backwards"
							:selected="selectedSkin === skin"
							:tooltip="skin.name"
							@select="changeSkin(skin)"
						/>
					</div>
				</section>
			</div>
		</div>

		<div v-else class="signin-panel">
			<div
				class="bg-bg-raised card-shadow rounded-lg p-7 flex flex-col gap-5 shadow-md relative max-w-xl w-full mx-auto"
			>
				<img
					:src="ExcitedRinthbot"
					alt=""
					class="absolute -top-28 right-8 md:right-20 h-28 w-auto"
				/>
				<div
					class="absolute top-0 left-0 w-full h-[1px] opacity-40 bg-gradient-to-r from-transparent via-brand to-transparent"
					style="
						background: linear-gradient(
							to right,
							transparent 2rem,
							var(--color-brand) calc(100% - 13rem),
							var(--color-brand) calc(100% - 5rem),
							transparent calc(100% - 2rem)
						);
					"
				></div>

				<div class="flex flex-col gap-5">
					<h1 class="text-3xl font-extrabold m-0">Войдите в Minecraft</h1>
					<p class="text-lg m-0">
						После входа здесь появятся ваши скины, плащи и стандартная коллекция Minecraft.
					</p>
					<ButtonStyled v-show="accountsCard" color="brand" :disabled="accountsCard.loginDisabled">
						<button :disabled="accountsCard.loginDisabled" @click="login">
							<LogInIcon v-if="!accountsCard.loginDisabled" />
							<SpinnerIcon v-else class="animate-spin" />
							Войти в аккаунт
						</button>
					</ButtonStyled>
				</div>
			</div>
		</div>
	</main>
</template>

<style lang="scss" scoped>
.skin-studio {
	min-height: 100%;
	padding: 1.5rem 2rem 2rem;
	box-sizing: border-box;
	overflow-y: auto;
	background:
		radial-gradient(circle at 78% 0%, rgba(126, 34, 206, 0.17), transparent 31rem), #060a12;
	color: #f8fafc;
	--smooth: cubic-bezier(0.22, 1, 0.36, 1);
}

.studio-hero {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 2rem;
	min-height: 8rem;
	padding: 1.5rem 1.7rem;
	border: 1px solid rgba(168, 85, 247, 0.27);
	border-radius: 1.15rem;
	background:
		linear-gradient(110deg, rgba(19, 11, 35, 0.96), rgba(9, 13, 23, 0.91)),
		radial-gradient(circle at 80% 30%, rgba(147, 51, 234, 0.2), transparent 24rem);
	box-shadow: 0 22px 58px rgba(0, 0, 0, 0.24);
}

.studio-eyebrow,
.panel-heading p,
.library-heading p {
	margin: 0 0 0.45rem;
	color: #c084fc;
	font-size: 0.68rem;
	font-weight: 750;
	letter-spacing: 0.12em;
}

.studio-hero h1 {
	margin: 0;
	font-size: clamp(2.1rem, 3vw, 3rem);
	line-height: 1;
}

.studio-hero > div > p:last-child {
	max-width: 42rem;
	margin: 0.72rem 0 0;
	color: rgba(203, 213, 225, 0.72);
	font-size: 0.94rem;
}

.upload-action {
	display: inline-flex;
	align-items: center;
	gap: 0.55rem;
	min-height: 3rem;
	padding: 0 1.2rem;
	border: 1px solid rgba(216, 180, 254, 0.4);
	border-radius: 0.72rem;
	background: linear-gradient(135deg, #8b3dff, #5b21b6);
	box-shadow: 0 14px 30px rgba(91, 33, 182, 0.25);
	color: white;
	font: inherit;
	font-weight: 700;
	cursor: pointer;
	transition:
		transform 180ms var(--smooth),
		filter 180ms ease;
}

.upload-action:hover {
	transform: translateY(-2px);
	filter: brightness(1.08);
}
.upload-action svg {
	width: 1.15rem;
}

.offline-skin-notice {
	display: flex;
	align-items: flex-start;
	gap: 0.8rem;
	margin-top: 1rem;
	padding: 0.9rem 1rem;
	border: 1px solid rgba(192, 132, 252, 0.24);
	border-radius: 0.85rem;
	background: rgba(91, 33, 182, 0.14);
	color: #e9d5ff;
}
.offline-skin-notice > svg {
	width: 1.2rem;
	flex: 0 0 auto;
}
.offline-skin-notice div {
	display: flex;
	flex-direction: column;
	gap: 0.2rem;
}
.offline-skin-notice span {
	color: rgba(226, 232, 240, 0.68);
	font-size: 0.8rem;
	line-height: 1.45;
}

.skin-layout {
	display: grid;
	grid-template-columns: minmax(20rem, 0.86fr) minmax(32rem, 1.7fr);
	gap: 1rem;
	margin-top: 1rem;
}

.preview-panel,
.skins-container {
	border: 1px solid rgba(255, 255, 255, 0.09);
	border-radius: 1.05rem;
	box-shadow: 0 20px 48px rgba(0, 0, 0, 0.2);
}

.preview-panel {
	position: sticky;
	top: 1rem;
	align-self: start;
	height: calc(100vh - 8rem);
	min-height: 34rem;
	overflow: hidden;
	padding: 1.2rem;
	box-sizing: border-box;
	background:
		radial-gradient(circle at 50% 40%, rgba(147, 51, 234, 0.2), transparent 48%),
		linear-gradient(145deg, rgba(18, 24, 37, 0.96), rgba(8, 12, 21, 0.96));
}

.panel-heading,
.library-heading {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 1rem;
}

.panel-heading h2,
.library-heading h2 {
	margin: 0;
	font-size: 1.18rem;
}

.equipped-pill {
	display: inline-flex;
	align-items: center;
	gap: 0.35rem;
	padding: 0.42rem 0.65rem;
	border: 1px solid rgba(192, 132, 252, 0.24);
	border-radius: 99px;
	background: rgba(91, 33, 182, 0.24);
	color: #d8b4fe;
	font-size: 0.68rem;
	font-weight: 700;
}

.equipped-pill svg {
	width: 0.9rem;
}

.preview-container {
	height: calc(100% - 7.4rem);
	min-height: 26rem;
}

.preview-container :deep(button) {
	border-color: rgba(216, 180, 254, 0.25);
	background: rgba(49, 24, 82, 0.78);
	color: #e9d5ff;
}

.preview-footer {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 0.65rem;
}

.preview-footer div {
	display: flex;
	flex-direction: column;
	gap: 0.2rem;
	padding: 0.65rem 0.75rem;
	border: 1px solid rgba(255, 255, 255, 0.07);
	border-radius: 0.65rem;
	background: rgba(4, 8, 15, 0.48);
}

.preview-footer span {
	color: rgba(148, 163, 184, 0.72);
	font-size: 0.62rem;
}
.preview-footer strong {
	font-size: 0.74rem;
}

.skins-container {
	padding: 1.2rem;
	background: linear-gradient(145deg, rgba(18, 24, 37, 0.93), rgba(9, 14, 23, 0.93));
}

.library-heading > span {
	color: rgba(148, 163, 184, 0.72);
	font-size: 0.7rem;
}

.skin-section + .skin-section {
	margin-top: 1.35rem;
	padding-top: 1.15rem;
	border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.skin-card-grid {
	display: grid;
	grid-template-columns: repeat(auto-fill, minmax(9rem, 1fr));
	gap: 0.7rem;
	width: 100%;
	margin-top: 0.85rem;
}

.skin-card {
	aspect-ratio: 0.9;
	width: 100%;
	min-width: 0;
	box-sizing: border-box;
	border-radius: 0.78rem;
}

:deep(.skin-card) {
	overflow: hidden;
	border: 1px solid rgba(255, 255, 255, 0.08) !important;
	background: rgba(6, 10, 18, 0.72) !important;
	transition:
		transform 180ms var(--smooth),
		border-color 180ms ease !important;
}

:deep(.skin-card:hover) {
	transform: translateY(-2px);
	border-color: rgba(192, 132, 252, 0.45) !important;
}

.signin-panel {
	display: flex;
	min-height: 27rem;
	margin-top: 1rem;
	align-items: center;
	justify-content: center;
	padding: 2rem;
	border: 1px solid rgba(168, 85, 247, 0.24);
	border-radius: 1.05rem;
	background: rgba(12, 17, 27, 0.88);
}

.signin-panel > div {
	border: 1px solid rgba(192, 132, 252, 0.2);
	background: rgba(13, 18, 29, 0.95);
}

@media (max-width: 1050px) {
	.skin-layout {
		grid-template-columns: 1fr;
	}
	.preview-panel {
		position: relative;
		top: 0;
		height: 37rem;
	}
}

@media (prefers-reduced-motion: no-preference) {
	.studio-hero,
	.preview-panel,
	.skins-container {
		animation: studio-enter 350ms var(--smooth) both;
	}
	.preview-panel {
		animation-delay: 60ms;
	}
	.skins-container {
		animation-delay: 100ms;
	}
}

@keyframes studio-enter {
	from {
		opacity: 0;
		transform: translateY(8px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}
</style>
