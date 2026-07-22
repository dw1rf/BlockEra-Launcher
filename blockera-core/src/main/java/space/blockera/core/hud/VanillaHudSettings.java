package space.blockera.core.hud;

/** Serializable position, scale and visibility for an allow-listed vanilla HUD group. */
public final class VanillaHudSettings {
	public boolean enabled = true;
	public HudAnchor anchor = HudAnchor.TOP_LEFT;
	public int offsetX;
	public int offsetY;
	public float scale = 1.0F;
	public int width = -1;
	public int height = -1;
	public int zIndex;
	public boolean locked;

	public void validate() {
		if (anchor == null) anchor = HudAnchor.TOP_LEFT;
		scale = Math.max(0.5F, Math.min(2.0F, scale));
		width = Math.max(-1, width);
		height = Math.max(-1, height);
		zIndex = Math.max(-1000, Math.min(1000, zIndex));
	}

	public VanillaHudSettings copy() {
		VanillaHudSettings copy = new VanillaHudSettings();
		copy.enabled = enabled;
		copy.anchor = anchor;
		copy.offsetX = offsetX;
		copy.offsetY = offsetY;
		copy.scale = scale;
		copy.width = width;
		copy.height = height;
		copy.zIndex = zIndex;
		copy.locked = locked;
		return copy;
	}
}
