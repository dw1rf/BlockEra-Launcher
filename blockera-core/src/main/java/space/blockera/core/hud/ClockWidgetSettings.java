package space.blockera.core.hud;

/** Serializable first-party display options for the Blockera clock widget. */
public final class ClockWidgetSettings {
	public boolean showRealTime = true;
	public boolean showWorldTime = true;
	public boolean use24Hour = true;
	public boolean showSeconds;

	public void validate() {
		if (!showRealTime && !showWorldTime) showRealTime = true;
	}

	public ClockWidgetSettings copy() {
		ClockWidgetSettings copy = new ClockWidgetSettings();
		copy.showRealTime = showRealTime;
		copy.showWorldTime = showWorldTime;
		copy.use24Hour = use24Hour;
		copy.showSeconds = showSeconds;
		return copy;
	}
}
