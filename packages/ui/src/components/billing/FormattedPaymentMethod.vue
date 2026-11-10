<script setup lang="ts">
import type Stripe from 'stripe'

import { useVIntl } from '../../composables/i18n'
import { commonMessages, getPaymentMethodIcon, paymentMethodMessages } from '../../utils'

const { formatMessage } = useVIntl()
defineProps<{
	method: Stripe.PaymentMethod
}>()
function paymentLabel(key: string): string {
	return formatMessage(
		Object.hasOwn(paymentMethodMessages, key)
			? paymentMethodMessages[key as keyof typeof paymentMethodMessages]
			: paymentMethodMessages.unknown,
	)
}
</script>

<template>
	<template v-if="'type' in method">
		<component :is="getPaymentMethodIcon(method.type)" class="size-[1.5em]" />
		<span v-if="method.type === 'card' && 'card' in method && method.card">
			{{
				formatMessage(commonMessages.paymentMethodCardDisplay, {
					card_brand: paymentLabel(method.card.brand),
					last_four: method.card.last4,
				})
			}}
		</span>
		<template v-else>
			{{ paymentLabel(method.type) }}
		</template>

		<span v-if="method.type === 'cashapp' && 'cashapp' in method && method.cashapp">
			({{ method.cashapp.cashtag }})
		</span>
		<span v-else-if="method.type === 'paypal' && 'paypal' in method && method.paypal">
			({{ method.paypal.payer_email }})
		</span>
	</template>
</template>
