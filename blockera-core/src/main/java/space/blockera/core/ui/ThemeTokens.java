package space.blockera.core.ui;

/** Central Blockera theme. UI components do not embed theme-specific colors or radii. */
public record ThemeTokens(
		int backgroundArgb,
		int topBarArgb,
		int panelArgb,
		int panelElevatedArgb,
		int cardArgb,
		int cardHoverArgb,
		int cardPressedArgb,
		int accentArgb,
		int accentHoverArgb,
		int accentMutedArgb,
		int textPrimaryArgb,
		int textSecondaryArgb,
		int textDisabledArgb,
		int borderArgb,
		int borderHoverArgb,
		int successArgb,
		int warningArgb,
		int dangerArgb,
		int panelRadius,
		int cardRadius,
		int buttonRadius,
		int smallRadius,
		int spacingUnit) {

	public int surfaceArgb() { return panelArgb; }
	public int surfaceElevatedArgb() { return panelElevatedArgb; }
	public int surfaceHoverArgb() { return cardHoverArgb; }
	public int textMutedArgb() { return textSecondaryArgb; }
	public int accentSoftArgb() { return accentMutedArgb; }
	public int cornerRadius() { return panelRadius; }
	public int gameBackdropArgb() { return 0x66070A10; }
	public int menuBackdropArgb() { return 0x78070A10; }
	public int glassPanelArgb() { return 0xC80E111A; }
	public int glassCardArgb() { return 0xAA1A1E29; }

	public static ThemeTokens darkDefault() {
		return new ThemeTokens(
				0xE8070A10,
				0xE6070A10,
				0xC80E111A,
				0xD3121620,
				0xB81A1E29,
				0xD12A303D,
				0xE3343B4B,
				0xFF8D68FF,
				0xFFA184FF,
				0x298D68FF,
				0xFFF3F4F8,
				0xFFA4A7B3,
				0xFF626674,
				0x24AEA0DC,
				0x40AEA0DC,
				0xFF59D69A,
				0xFFF0C66B,
				0xFFE27D8D,
				12,
				8,
				8,
				6,
				4);
	}
}
