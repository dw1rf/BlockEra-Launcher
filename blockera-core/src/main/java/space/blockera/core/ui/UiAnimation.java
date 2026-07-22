package space.blockera.core.ui;

import net.minecraft.Util;
import space.blockera.core.config.ClientConfig;

/** Small frame-rate-independent ease-out animation used by hover, toggles and scroll. */
public final class UiAnimation {
	private float value;
	private long lastMillis = Util.getMillis();

	public UiAnimation(float initialValue) {
		value = initialValue;
	}

	public float update(float target, int durationMillis) {
		if (ClientConfig.REDUCE_UI_MOTION.get()) {
			snap(target);
			return value;
		}
		long now = Util.getMillis();
		float elapsed = Math.min(1.0F, Math.max(0.0F, (now - lastMillis) / (float) Math.max(1, durationMillis)));
		lastMillis = now;
		float eased = 1.0F - (float) Math.pow(1.0F - elapsed, 3.0D);
		value += (target - value) * eased;
		if (Math.abs(target - value) < 0.001F) value = target;
		return value;
	}

	public void snap(float target) {
		value = target;
		lastMillis = Util.getMillis();
	}

	public float value() { return value; }
}
