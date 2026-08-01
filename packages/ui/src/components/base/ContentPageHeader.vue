<template>
	<div
		class="grid min-w-0 max-w-full grid-cols-1 gap-x-8 gap-y-6 border-0 border-b border-solid border-divider pb-4 lg:grid-cols-[minmax(0,1fr)_auto]"
	>
		<div class="flex min-w-0 gap-4">
			<slot name="icon" />
			<div class="flex min-w-0 flex-col gap-1">
				<div class="flex min-w-0 flex-wrap items-center gap-2">
					<h1 class="m-0 text-2xl font-extrabold leading-none text-contrast">
						<slot name="title" />
					</h1>
					<slot name="title-suffix" />
				</div>
				<p v-if="$slots.summary" class="m-0 line-clamp-2 max-w-[40rem] break-words empty:hidden">
					<slot name="summary" />
				</p>
				<div v-if="$slots.stats" class="mt-auto flex flex-wrap gap-4 empty:hidden">
					<slot name="stats" />
				</div>
			</div>
		</div>
		<div class="flex flex-wrap gap-2 items-center">
			<slot name="actions" />
		</div>
	</div>
</template>
