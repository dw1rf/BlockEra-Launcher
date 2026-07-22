package space.blockera.core.ui;

/** Thread-local guard for replacing only Minecraft chat text rendering. */
public final class BlockeraChatStyleScope {
	private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
	private static final ThreadLocal<Integer> VANILLA_BYPASS = ThreadLocal.withInitial(() -> 0);

	private BlockeraChatStyleScope() {
	}

	public static void enter() {
		DEPTH.set(DEPTH.get() + 1);
	}

	public static void exit() {
		DEPTH.set(Math.max(0, DEPTH.get() - 1));
	}

	public static boolean active() {
		return DEPTH.get() > 0 && VANILLA_BYPASS.get() == 0;
	}

	/** Prevents the Font mixin from recursively replacing an intentional vanilla fallback run. */
	public static <T> T withVanillaFallback(java.util.function.Supplier<T> action) {
		VANILLA_BYPASS.set(VANILLA_BYPASS.get() + 1);
		try {
			return action.get();
		} finally {
			VANILLA_BYPASS.set(Math.max(0, VANILLA_BYPASS.get() - 1));
		}
	}
}
