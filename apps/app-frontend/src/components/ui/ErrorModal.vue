<script setup>
import {
	CheckIcon,
	CopyIcon,
	DropdownIcon,
	HammerIcon,
	LogInIcon,
	UpdatedIcon,
	XIcon,
} from '@modrinth/assets'
import { ButtonStyled, Collapsible, injectNotificationManager } from '@modrinth/ui'
import { computed, ref } from 'vue'

import { ChatIcon } from '@/assets/icons'
import ModalWrapper from '@/components/ui/modal/ModalWrapper.vue'
import { trackEvent } from '@/helpers/analytics'
import { login as login_flow, set_default_user } from '@/helpers/auth.js'
import { launcherText } from '@/helpers/launcher-language'
import { install } from '@/helpers/profile.js'
import { cancel_directory_change } from '@/helpers/settings.ts'
import { applyMigrationFix, restartApp } from '@/helpers/utils.js'
import { handleSevereError } from '@/store/error.js'

const { handleError } = injectNotificationManager()

const errorModal = ref()
const error = ref()
const closable = ref(true)
const errorCollapsed = ref(false)
const migrationFixSuccess = ref(null) // null | true | false
const migrationFixCallbackModel = ref()

const title = ref(launcherText('An error occurred'))
const errorType = ref('unknown')
const supportLink = ref('https://support.modrinth.com')
const metadata = ref({})

defineExpose({
	async show(errorVal, context, canClose = true, source = null) {
		closable.value = canClose

		if (errorVal.message && errorVal.message.includes('Minecraft authentication error:')) {
			title.value = launcherText('Unable to sign in to Minecraft')
			errorType.value = 'minecraft_auth'
			supportLink.value =
				'https://support.modrinth.com/en/articles/9038231-minecraft-sign-in-issues'

			if (
				errorVal.message.includes('existing connection was forcibly closed') ||
				errorVal.message.includes('error sending request for url')
			) {
				metadata.value.network = true
			}
			if (errorVal.message.includes('because the target machine actively refused it')) {
				metadata.value.hostsFile = true
			}
		} else if (errorVal.message && errorVal.message.includes('User is not logged in')) {
			title.value = launcherText('Sign in to Minecraft')
			errorType.value = 'minecraft_sign_in'
			supportLink.value = 'https://support.modrinth.com'
		} else if (errorVal.message && errorVal.message.includes('Move directory error:')) {
			title.value = launcherText('Could not change app directory')
			errorType.value = 'directory_move'
			supportLink.value = 'https://support.modrinth.com'

			if (errorVal.message.includes('directory is not writeable')) {
				metadata.value.readOnly = true
			}

			if (errorVal.message.includes('Not enough space')) {
				metadata.value.notEnoughSpace = true
			}
		} else if (errorVal.message && errorVal.message.includes('No loader version selected for')) {
			title.value = launcherText('No loader selected')
			errorType.value = 'no_loader_version'
			supportLink.value = 'https://support.modrinth.com'
			metadata.value.profilePath = context.profilePath
		} else if (source === 'state_init') {
			title.value = launcherText('Error initializing BlockEra Launcher')
			errorType.value = 'state_init'
			supportLink.value = 'https://support.modrinth.com'
		} else {
			title.value = launcherText('An error occurred')
			errorType.value = 'unknown'
			supportLink.value = 'https://support.modrinth.com'
			metadata.value = {}
		}

		error.value = errorVal
		errorModal.value.show()
	},
})

const loadingMinecraft = ref(false)
async function loginMinecraft() {
	try {
		loadingMinecraft.value = true
		const loggedIn = await login_flow()

		if (loggedIn) {
			await set_default_user(loggedIn.profile.id).catch(handleError)
		}

		await trackEvent('AccountLogIn', { source: 'ErrorModal' })
		loadingMinecraft.value = false
		errorModal.value.hide()
	} catch (err) {
		loadingMinecraft.value = false
		handleSevereError(err)
	}
}

async function cancelDirectoryChange() {
	try {
		await cancel_directory_change()
		window.location.reload()
	} catch (err) {
		handleError(err)
	}
}

function retryDirectoryChange() {
	window.location.reload()
}

const loadingRepair = ref(false)
async function repairInstance() {
	loadingRepair.value = true
	try {
		await install(metadata.value.profilePath, false)
		errorModal.value.hide()
	} catch (err) {
		handleSevereError(err)
	}
	loadingRepair.value = false
}

const hasDebugInfo = computed(
	() =>
		errorType.value === 'directory_move' ||
		errorType.value === 'minecraft_auth' ||
		errorType.value === 'state_init' ||
		errorType.value === 'no_loader_version',
)

const debugInfo = computed(() => error.value.message ?? error.value ?? 'No error message.')

const copied = ref(false)

async function copyToClipboard(text) {
	await navigator.clipboard.writeText(text)
	copied.value = true
	setTimeout(() => {
		copied.value = false
	}, 3000)
}

async function onApplyMigrationFix(eol) {
	console.log(`[AR] • Attempting to apply migration ${eol.toUpperCase()} fix`)
	try {
		const result = await applyMigrationFix(eol)
		migrationFixSuccess.value = result === true
		console.log(`[AR] • Successfully applied migration ${eol.toUpperCase()} fix`, result)
	} catch (err) {
		console.error(`[AR] • Failed to apply migration fix:`, err)
		migrationFixSuccess.value = false
	} finally {
		migrationFixCallbackModel.value?.show?.()
		if (migrationFixSuccess.value === true) {
			setTimeout(async () => {
				await restartApp()
			}, 3000)
		}
	}
}
</script>

<template>
	<ModalWrapper ref="errorModal" :header="title" :closable="closable">
		<div class="modal-body">
			<div class="markdown-body">
				<template v-if="errorType === 'minecraft_auth'">
					<template v-if="metadata.network">
						<h3>{{ $t('blockera.ui.2c560c673ce0') }}</h3>
						<p>
							{{ $t('blockera.ui.0951c9cfadf4') }}
							<a
								href="https://support.modrinth.com/en/articles/9038231-minecraft-sign-in-issues#h_e71a5f805f"
							>
								{{ $t('blockera.ui.3dee43ecd306') }}
							</a>
							{{ $t('blockera.ui.4f4e49d062f2') }}
						</p>
					</template>
					<template v-else-if="metadata.hostsFile">
						<h3>{{ $t('blockera.ui.2c560c673ce0') }}</h3>
						<p>
							{{ $t('blockera.ui.b6a5b59cb7ba') }}
							<a
								href="https://support.modrinth.com/en/articles/9038231-minecraft-sign-in-issues#h_d694a29256"
							>
								{{ $t('blockera.ui.3dee43ecd306') }}
							</a>
							{{ $t('blockera.ui.a0f20ab6097f') }}
						</p>
					</template>
					<template v-else>
						<h3>{{ $t('blockera.ui.a15f2216ac28') }}</h3>
						<p>
							{{ $t('blockera.ui.b7b62f61a300') }}
						</p>
						<div class="cta-button">
							<button class="btn btn-primary" :disabled="loadingMinecraft" @click="loginMinecraft">
								<LogInIcon /> {{ $t('blockera.ui.1c3eb22a2c17') }}
							</button>
						</div>
						<h3>{{ $t('blockera.ui.6f614eb6b772') }}</h3>
						<p>
							{{ $t('blockera.ui.0f0e301d0134') }}
							<a href="https://www.minecraft.net/en-us/download">{{
								$t('blockera.ui.a0eab8b25fc6')
							}}</a>
							{{ $t('blockera.ui.55db19d25943') }}
						</p>
					</template>
					<div class="cta-button">
						<button class="btn btn-primary" :disabled="loadingMinecraft" @click="loginMinecraft">
							<LogInIcon /> {{ $t('blockera.ui.c265097df2f4') }}
						</button>
					</div>
				</template>
				<template v-if="errorType === 'directory_move'">
					<template v-if="metadata.readOnly">
						<h3>{{ $t('blockera.ui.44e6d88dd13f') }}</h3>
						<p>
							{{ $t('blockera.ui.94f7df82f77c') }}
						</p>
					</template>
					<template v-else-if="metadata.notEnoughSpace">
						<h3>{{ $t('blockera.ui.4785c5c50f00') }}</h3>
						<p>
							{{ $t('blockera.ui.e8609c4f76c6') }}
						</p>
					</template>
					<template v-else>
						<p>
							{{ $t('blockera.ui.471d0f52b386') }}
						</p>
					</template>

					<div class="cta-button">
						<button class="btn" @click="retryDirectoryChange">
							<UpdatedIcon /> {{ $t('blockera.ui.2f95463cdb9d') }}
						</button>
						<button class="btn btn-danger" @click="cancelDirectoryChange">
							<XIcon /> {{ $t('blockera.ui.9d2ac8f9d7d8') }}
						</button>
					</div>
				</template>
				<div v-else-if="errorType === 'minecraft_sign_in'">
					<p>
						{{ $t('blockera.ui.81d29ff5cd35') }}
						<a href="https://www.minecraft.net/en-us/store/minecraft-java-bedrock-edition-pc">{{
							$t('blockera.ui.5a69a39cf505')
						}}</a
						>.
					</p>
					<div class="cta-button">
						<button class="btn btn-primary" :disabled="loadingMinecraft" @click="loginMinecraft">
							<LogInIcon /> {{ $t('blockera.ui.04b27c1aebd9') }}
						</button>
					</div>
				</div>
				<template v-else-if="errorType === 'state_init'">
					<p>
						{{ $t('blockera.ui.efb4051db259') }}
					</p>
					<p>{{ $t('blockera.ui.9854df32b631') }}</p>
					<ul>
						<li>{{ $t('blockera.ui.e74f49f3d1f5') }}</li>
						<li>{{ $t('blockera.ui.fea9ac65cca0') }}</li>
					</ul>
				</template>
				<template v-else-if="errorType === 'no_loader_version'">
					<p>{{ $t('blockera.ui.9545b2b662f9') }}</p>
					<p>{{ $t('blockera.ui.fbcbffec1854') }}</p>
					<div class="cta-button">
						<button class="btn btn-primary" :disabled="loadingRepair" @click="repairInstance">
							<HammerIcon /> {{ $t('blockera.ui.e6619b65c69f') }}
						</button>
					</div>
				</template>
				<template v-else>
					{{ debugInfo }}
				</template>
				<template v-if="hasDebugInfo">
					<hr />
					<p>
						{{ $t('blockera.ui.9ed2d5a98dab') }}
						<a :href="supportLink">{{ $t('blockera.ui.8f5b343647fd') }}</a>
						{{ $t('blockera.ui.3a75e1310eef') }}
					</p>
				</template>
			</div>
			<div class="flex items-center gap-2">
				<ButtonStyled>
					<a :href="supportLink" @click="errorModal.hide()"
						><ChatIcon /> {{ $t('blockera.ui.4541e7a202c6') }}</a
					>
				</ButtonStyled>
				<ButtonStyled v-if="closable">
					<button @click="errorModal.hide()"><XIcon /> {{ $t('blockera.ui.bbfa773e5a63') }}</button>
				</ButtonStyled>
				<ButtonStyled v-if="hasDebugInfo">
					<button :disabled="copied" @click="copyToClipboard(debugInfo)">
						<template v-if="copied">
							<CheckIcon class="text-green" /> {{ $t('blockera.ui.b7c3ca0ee379') }}
						</template>
						<template v-else> <CopyIcon /> {{ $t('blockera.ui.c3511494d913') }} </template>
					</button>
				</ButtonStyled>
			</div>
			<template v-if="hasDebugInfo">
				<div class="bg-button-bg rounded-xl mt-2 overflow-clip">
					<button
						class="flex items-center justify-between w-full bg-transparent border-0 px-4 py-3 cursor-pointer"
						@click="errorCollapsed = !errorCollapsed"
					>
						<span class="text-contrast font-extrabold m-0">{{
							$t('blockera.ui.4c806c855ac8')
						}}</span>
						<DropdownIcon
							class="h-5 w-5 text-secondary transition-transform"
							:class="{ 'rotate-180': !errorCollapsed }"
						/>
					</button>
					<Collapsible :collapsed="errorCollapsed">
						<pre
							class="m-0 px-4 py-3 bg-bg rounded-none whitespace-pre-wrap break-words overflow-x-auto max-w-full"
							>{{ debugInfo }}</pre
						>
					</Collapsible>
				</div>
				<template v-if="errorType === 'state_init'">
					<h2>{{ $t('blockera.ui.cf1ac793741a') }}</h2>
					<p>
						{{ $t('blockera.ui.b931cddd4a11') }}
					</p>
					<p>
						<strong>{{ $t('blockera.ui.617696c8133e') }}</strong>
						{{ $t('blockera.ui.206a57f9baad') }}
					</p>
					<p>
						<strong>{{ $t('blockera.ui.d77989c1e3c0') }}</strong>
						{{ $t('blockera.ui.cea1024f4105') }}
					</p>
					<p>
						<strong>{{ $t('blockera.ui.bca70bf7a9fa') }}</strong>
						{{ $t('blockera.ui.b0afbfcb925b') }}
					</p>
					<h3>{{ $t('blockera.ui.8a49d2b769ee') }}</h3>
					<div>
						<p class="notice__text">
							{{ $t('blockera.ui.590a977f879d') }}
						</p>
						<p>
							{{ $t('blockera.ui.af199f0f220c') }}
							<code>app.db</code> {{ $t('blockera.ui.34847c4c35c4') }}
							<code>%appdata%\Roaming\BlockEraLauncher</code> {{ $t('blockera.ui.31d066ed215e') }}
							<code>~/Library/Application Support/BlockEraLauncher</code>
							{{ $t('blockera.ui.7c829d26d870') }}
						</p>
					</div>
					<div class="flex justify-between">
						<ol class="flex flex-col gap-3">
							<li>
								<ButtonStyled class="neon-button neon">
									<button
										title="Convert all line endings in migration files to LF (Unix-style: \\n)"
										@click="onApplyMigrationFix('lf')"
									>
										{{ $t('blockera.ui.4a0880ea8254') }}
									</button>
								</ButtonStyled>
							</li>
							<li>
								<ButtonStyled class="neon-button neon">
									<button
										title="Convert all line endings in migration files to CRLF (Windows-style: \\r\\n)"
										@click="onApplyMigrationFix('crlf')"
									>
										{{ $t('blockera.ui.66d05094e407') }}
									</button>
								</ButtonStyled>
							</li>
						</ol>
					</div>
				</template>
			</template>
		</div>
	</ModalWrapper>
	<ModalWrapper
		ref="migrationFixCallbackModel"
		:header="$t('blockera.ui.e30d3bd53b9b')"
		:closable="closable"
	>
		<div class="modal-body">
			<h2 class="text-lg font-bold text-contrast space-y-2">
				<template v-if="migrationFixSuccess === true">
					<p class="flex items-center gap-2 neon-text">
						{{ $t('blockera.ui.964071606403') }}
					</p>
					<p class="mt-2 text-sm neon-text">{{ $t('blockera.ui.cf24c9e25b78') }}</p>
				</template>

				<template v-else-if="migrationFixSuccess === false">
					<p class="flex items-center gap-2 neon-text">
						{{ $t('blockera.ui.bab90054367c') }}
					</p>
					<p class="mt-2 text-sm neon-text">{{ $t('blockera.ui.cf24c9e25b78') }}</p>
				</template>
			</h2>
		</div>
	</ModalWrapper>
</template>

<style>
.light-mode {
	--color-orange-bg: rgba(255, 163, 71, 0.2);
}

.dark-mode,
.oled-mode {
	--color-orange-bg: rgba(224, 131, 37, 0.2);
}
</style>

<style scoped lang="scss">
@import '../../../../../packages/assets/styles/neon-button.scss';
@import '../../../../../packages/assets/styles/neon-text.scss';

code {
	background: linear-gradient(90deg, #005eff, #00cfff);
	background-clip: text;
	-webkit-background-clip: text;
	color: transparent;
}

.cta-button {
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 0.5rem;
	gap: 0.5rem;
}

.warning-banner {
	display: flex;
	flex-direction: column;
	gap: 0.5rem;
	padding: var(--gap-lg);
	background-color: var(--color-orange-bg);
	border: 2px solid var(--color-orange);
	border-radius: var(--radius-md);
	margin-bottom: 1rem;
}

.warning-banner__title {
	display: flex;
	align-items: center;
	gap: 0.5rem;
	font-weight: 700;

	svg {
		color: var(--color-orange);
		height: 1.5rem;
		width: 1.5rem;
	}
}

.modal-body {
	display: flex;
	flex-direction: column;
	gap: var(--gap-md);
}

.markdown-body {
	overflow: auto;
}
</style>
