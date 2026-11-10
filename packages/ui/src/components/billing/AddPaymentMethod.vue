<script setup lang="ts">
import { createStripeElements } from '@modrinth/utils'
import { loadStripe, type Stripe as StripsJs, type StripeElements } from '@stripe/stripe-js'
import type Stripe from 'stripe'
import { ref } from 'vue'

import ModalLoadingIndicator from '../modal/ModalLoadingIndicator.vue'

const emit = defineEmits<{
	(e: 'startLoading' | 'stopLoading'): void
}>()

export type SetupIntentResponse = {
	client_secret: string
}

export type AddPaymentMethodProps = {
	publishableKey: string
	createSetupIntent: () => Promise<SetupIntentResponse>
	returnUrl: string
	onError: (error: Error) => void
}

const props = defineProps<AddPaymentMethodProps>()

const elementsLoaded = ref<0 | 1 | 2>(0)
const stripe = ref<StripsJs | null>(null)
const elements = ref<StripeElements>()
const error = ref(false)

function handleError(cause: unknown) {
	props.onError(cause instanceof Error ? cause : new Error(String(cause)))
	error.value = true
}

async function reload(paymentMethods: Stripe.PaymentMethod[]) {
	try {
		elementsLoaded.value = 0
		error.value = false

		const result = await props.createSetupIntent()

		stripe.value = await loadStripe(props.publishableKey)
		if (!stripe.value) throw new Error('Stripe could not be loaded')
		const {
			elements: newElements,
			addressElement,
			paymentElement,
		} = createStripeElements(stripe.value, paymentMethods, {
			clientSecret: result.client_secret,
		})

		elements.value = newElements
		paymentElement.on('ready', () => {
			elementsLoaded.value += 1
		})
		addressElement.on('ready', () => {
			elementsLoaded.value += 1
		})
	} catch (err) {
		handleError(err)
	}
}

async function submit(): Promise<boolean> {
	if (!stripe.value || !elements.value) {
		handleError(new Error('Payment form is not ready'))
		return false
	}
	emit('startLoading')
	try {
		const result = await stripe.value.confirmSetup({
			elements: elements.value,
			confirmParams: { return_url: props.returnUrl },
		})
		if (result.error) {
			if (result.error.type !== 'validation_error')
				handleError(result.error.message ?? 'Payment setup failed')
			return false
		}
		return true
	} catch (cause) {
		handleError(cause)
		return false
	} finally {
		emit('stopLoading')
	}
}

defineExpose({
	reload,
	submit,
})
</script>

<template>
	<div class="min-h-[16rem] flex flex-col gap-2 justify-center items-center">
		<div v-show="elementsLoaded < 2">
			<ModalLoadingIndicator :error="error">
				Loading...
				<template #error> Error loading Stripe payment UI. </template>
			</ModalLoadingIndicator>
		</div>
		<div class="w-full">
			<div id="address-element"></div>
			<div id="payment-element" class="mt-4"></div>
		</div>
	</div>
</template>
