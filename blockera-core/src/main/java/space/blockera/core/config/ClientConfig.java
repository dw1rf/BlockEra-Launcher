package space.blockera.core.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** User-facing local HUD settings. This file never stores credentials or server tokens. */
public final class ClientConfig {
	public static final ForgeConfigSpec SPEC;
	public static final ForgeConfigSpec.BooleanValue CUSTOM_TITLE_SCREEN;
	public static final ForgeConfigSpec.BooleanValue CUSTOM_PAUSE_SCREEN;
	public static final ForgeConfigSpec.BooleanValue FANCY_THEME;
	public static final ForgeConfigSpec.BooleanValue HUD_ENABLED;
	public static final ForgeConfigSpec.BooleanValue HUD_EDITOR_HINT_SEEN;
	public static final ForgeConfigSpec.BooleanValue REDUCE_UI_MOTION;
	public static final ForgeConfigSpec.BooleanValue FULL_BRIGHT;
	public static final ForgeConfigSpec.BooleanValue HIDE_TOTEM_ANIMATION;
	public static final ForgeConfigSpec.BooleanValue BLOCK_HIGHLIGHT;
	public static final ForgeConfigSpec.BooleanValue HITBOX_PLAYERS;
	public static final ForgeConfigSpec.BooleanValue HITBOX_ANIMALS;
	public static final ForgeConfigSpec.BooleanValue HITBOX_ITEMS;

	static {
		ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
		builder.comment("Blockera Client Runtime global client settings").push("client");
		CUSTOM_TITLE_SCREEN = builder.comment("Replace the vanilla title screen with the Blockera title screen.")
				.define("customTitleScreen", true);
		CUSTOM_PAUSE_SCREEN = builder.comment("Apply Blockera styling and top navigation without replacing the vanilla pause layout.")
				.define("customPauseScreen", true);
		FANCY_THEME = builder.comment("Use the Blockera Fancy theme. Only Fancy is available in 0.4.0.")
				.define("fancyTheme", true);
		HUD_ENABLED = builder.comment("Render enabled Blockera HUD widgets.").define("hudEnabled", true);
		HUD_EDITOR_HINT_SEEN = builder.comment("The direct-manipulation HUD editor hint has been acknowledged.")
				.define("hudEditorHintSeen", false);
		REDUCE_UI_MOTION = builder.comment("Disable non-essential Blockera UI transitions.")
				.define("reduceUiMotion", false);
		builder.comment("Safe first-party game enhancement switches.").push("enhancements");
		FULL_BRIGHT = builder.comment("Replace the client light map with full brightness.")
				.define("fullBright", false);
		HIDE_TOTEM_ANIMATION = builder.comment("Suppress the local totem activation overlay animation.")
				.define("hideTotemAnimation", false);
		BLOCK_HIGHLIGHT = builder.comment("Render Minecraft's selected-block outline.")
				.define("blockHighlight", true);
		HITBOX_PLAYERS = builder.comment("Render depth-tested hitboxes for players within 128 blocks.")
				.define("hitboxPlayers", false);
		HITBOX_ANIMALS = builder.comment("Render depth-tested hitboxes for animals within 128 blocks.")
				.define("hitboxAnimals", false);
		HITBOX_ITEMS = builder.comment("Render depth-tested hitboxes for dropped items within 128 blocks.")
				.define("hitboxItems", false);
		builder.pop();
		builder.pop();
		SPEC = builder.build();
	}

	private ClientConfig() {
	}

}
