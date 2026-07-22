package space.blockera.core.ui.settings;

import net.minecraft.client.Minecraft;
import net.minecraftforge.common.ForgeConfigSpec;
import space.blockera.core.config.ClientConfig;
import space.blockera.core.hud.BuiltinWidgetIds;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.ui.BlockeraIcon;

import java.util.List;

/** Allow-listed first-party menu catalog. Future modules are added here without rebuilding the Screen layout. */
public final class ClientSettingsCatalog {
	private static final List<ClientSettingModel> MODELS = List.of(
			unavailable("appearance", "blockera.setting.appearance", "blockera.setting.appearance.description", ClientSettingCategory.INTERFACE, BlockeraIcon.APPEARANCE),
			ClientSettingModel.action("ingame_chat", "blockera.setting.ingame_chat", null,
					ClientSettingCategory.INTERFACE, BlockeraIcon.CHAT, screen -> screen.openChatSettings()),
			unavailable("player_list", "blockera.setting.player_list", null, ClientSettingCategory.INTERFACE, BlockeraIcon.PLAYERS),
			unavailable("smart_disconnect", "blockera.setting.smart_disconnect", null, ClientSettingCategory.INTERFACE, BlockeraIcon.CONNECTION),
			unavailable("server_list", "blockera.setting.server_list", null, ClientSettingCategory.INTERFACE, BlockeraIcon.SERVER),
			unavailable("auto_reconnect", "blockera.setting.auto_reconnect", null, ClientSettingCategory.INTERFACE, BlockeraIcon.CONNECTION),
			unavailable("server_info", "blockera.setting.server_info", null, ClientSettingCategory.INTERFACE, BlockeraIcon.SERVER),
			ClientSettingModel.action("hud_editor", "blockera.setting.hud_widgets", "blockera.setting.hud_widgets.description",
					ClientSettingCategory.INTERFACE, BlockeraIcon.HUD, screen -> screen.openHudEditor()),

			configToggle("full_bright", "blockera.setting.full_bright", ClientSettingCategory.GAME_ENHANCEMENTS,
					BlockeraIcon.LIGHT, ClientConfig.FULL_BRIGHT),
			configToggle("hide_totem", "blockera.setting.hide_totem", ClientSettingCategory.GAME_ENHANCEMENTS,
					BlockeraIcon.EFFECTS, ClientConfig.HIDE_TOTEM_ANIMATION),
			ClientSettingModel.toggle("fov", "blockera.setting.fov", "blockera.setting.fov.description",
					ClientSettingCategory.GAME_ENHANCEMENTS, BlockeraIcon.CAMERA,
					() -> Minecraft.getInstance().options.fovEffectScale().get() > 0.0D,
					enabled -> setVanillaOption(() -> Minecraft.getInstance().options.fovEffectScale().set(enabled ? 1.0D : 0.0D))),
			unavailable("motion_blur", "blockera.setting.motion_blur", null, ClientSettingCategory.GAME_ENHANCEMENTS, BlockeraIcon.CAMERA),
			ClientSettingModel.toggle("camera_bob", "blockera.setting.camera_bob", "blockera.setting.camera_bob.description",
					ClientSettingCategory.GAME_ENHANCEMENTS, BlockeraIcon.CAMERA,
					() -> Minecraft.getInstance().options.bobView().get(),
					enabled -> setVanillaOption(() -> Minecraft.getInstance().options.bobView().set(enabled))),
			configToggle("block_highlight", "blockera.setting.block_highlight", ClientSettingCategory.GAME_ENHANCEMENTS,
					BlockeraIcon.BLOCKS, ClientConfig.BLOCK_HIGHLIGHT),
			ClientSettingModel.module("hitboxes", "blockera.setting.hitboxes", "blockera.setting.hitboxes.description",
					ClientSettingCategory.GAME_ENHANCEMENTS, BlockeraIcon.PLAYERS,
					() -> ClientConfig.HITBOX_PLAYERS.get() || ClientConfig.HITBOX_ANIMALS.get() || ClientConfig.HITBOX_ITEMS.get(),
					enabled -> {
						ClientConfig.HITBOX_PLAYERS.set(enabled); ClientConfig.HITBOX_ANIMALS.set(enabled); ClientConfig.HITBOX_ITEMS.set(enabled);
						ClientConfig.HITBOX_PLAYERS.save(); ClientConfig.HITBOX_ANIMALS.save(); ClientConfig.HITBOX_ITEMS.save();
					}, screen -> screen.openHitboxSettings()),

			unavailable("cps", "blockera.setting.cps", null, ClientSettingCategory.PVP, BlockeraIcon.COMBAT),
			unavailable("combo", "blockera.setting.combo", null, ClientSettingCategory.PVP, BlockeraIcon.COMBAT),
			unavailable("keystrokes", "blockera.setting.keystrokes", null, ClientSettingCategory.PVP, BlockeraIcon.COMBAT),
			unavailable("armor", "blockera.setting.armor", null, ClientSettingCategory.PVP, BlockeraIcon.COMBAT),
			unavailable("durability", "blockera.setting.durability", null, ClientSettingCategory.PVP, BlockeraIcon.TOOLS),
			unavailable("potion_effects", "blockera.setting.potion_effects", null, ClientSettingCategory.PVP, BlockeraIcon.EFFECTS),

			unavailable("modules", "blockera.setting.modules", "blockera.setting.modules.description", ClientSettingCategory.TOOLS, BlockeraIcon.TOOLS),

			widget(BuiltinWidgetIds.FPS, "blockera.hud.widget.fps", BlockeraIcon.PERFORMANCE),
			widget(BuiltinWidgetIds.COORDINATES, "blockera.hud.widget.coordinates", BlockeraIcon.WORLD),
			widget(BuiltinWidgetIds.DIRECTION, "blockera.hud.widget.direction", BlockeraIcon.WORLD),
			widget(BuiltinWidgetIds.BIOME, "blockera.hud.widget.biome", BlockeraIcon.WORLD),
			widget(BuiltinWidgetIds.SPEED, "blockera.hud.widget.speed", BlockeraIcon.PERFORMANCE),
			widget(BuiltinWidgetIds.MEMORY, "blockera.hud.widget.memory", BlockeraIcon.PERFORMANCE),
			widget(BuiltinWidgetIds.CLOCK, "blockera.hud.widget.clock", BlockeraIcon.CLOCK),
			widget(BuiltinWidgetIds.PING, "blockera.hud.widget.ping", BlockeraIcon.CONNECTION),
			widget(BuiltinWidgetIds.PLAYER_COUNT, "blockera.hud.widget.player_count", BlockeraIcon.PLAYERS),
			widget(BuiltinWidgetIds.EFFECTS, "blockera.hud.widget.effects", BlockeraIcon.EFFECTS),
			widget(BuiltinWidgetIds.DURABILITY, "blockera.hud.widget.durability", BlockeraIcon.TOOLS),
			widget(BuiltinWidgetIds.ARMOR, "blockera.hud.widget.armor", BlockeraIcon.COMBAT),
			widget(BuiltinWidgetIds.HEALTH, "blockera.hud.widget.health", BlockeraIcon.COMBAT),
			widget(BuiltinWidgetIds.FOOD, "blockera.hud.widget.food", BlockeraIcon.COMBAT),
			widget(BuiltinWidgetIds.SATURATION, "blockera.hud.widget.saturation", BlockeraIcon.COMBAT),
			widget(BuiltinWidgetIds.XP, "blockera.hud.widget.xp", BlockeraIcon.PERFORMANCE),
			widget(BuiltinWidgetIds.LIGHT, "blockera.hud.widget.light", BlockeraIcon.LIGHT),
			widget(BuiltinWidgetIds.TARGET_BLOCK, "blockera.hud.widget.target_block", BlockeraIcon.BLOCKS),
			widget(BuiltinWidgetIds.ENTITIES, "blockera.hud.widget.entities", BlockeraIcon.WORLD),
			widget(BuiltinWidgetIds.KEYSTROKES, "blockera.hud.widget.keystrokes", BlockeraIcon.COMBAT),
			widget(BuiltinWidgetIds.CPS, "blockera.hud.widget.cps", BlockeraIcon.COMBAT),

			ClientSettingModel.toggle("hud_enabled", "blockera.setting.hud_enabled", "blockera.setting.hud_enabled.description",
					ClientSettingCategory.SETTINGS, BlockeraIcon.HUD, ClientConfig.HUD_ENABLED::get, ClientConfig.HUD_ENABLED::set),
			ClientSettingModel.toggle("custom_title", "blockera.setting.custom_title", "blockera.setting.custom_title.description",
					ClientSettingCategory.SETTINGS, BlockeraIcon.APPEARANCE, ClientConfig.CUSTOM_TITLE_SCREEN::get, ClientConfig.CUSTOM_TITLE_SCREEN::set),
			ClientSettingModel.toggle("custom_pause", "blockera.setting.custom_pause", "blockera.setting.custom_pause.description",
					ClientSettingCategory.SETTINGS, BlockeraIcon.APPEARANCE, ClientConfig.CUSTOM_PAUSE_SCREEN::get, ClientConfig.CUSTOM_PAUSE_SCREEN::set),
			ClientSettingModel.toggle("fancy_theme", "blockera.setting.fancy_theme", "blockera.setting.fancy_theme.description",
					ClientSettingCategory.SETTINGS, BlockeraIcon.APPEARANCE, ClientConfig.FANCY_THEME::get, ClientConfig.FANCY_THEME::set),
			ClientSettingModel.toggle("reduce_motion", "blockera.setting.reduce_motion", "blockera.setting.reduce_motion.description",
					ClientSettingCategory.SETTINGS, BlockeraIcon.APPEARANCE, ClientConfig.REDUCE_UI_MOTION::get, ClientConfig.REDUCE_UI_MOTION::set));

	private ClientSettingsCatalog() {
	}

	public static List<ClientSettingModel> all() { return MODELS; }

	private static ClientSettingModel unavailable(String id, String title, String description,
			ClientSettingCategory category, BlockeraIcon icon) {
		return ClientSettingModel.unavailable(id, title, description, category, icon);
	}

	private static ClientSettingModel configToggle(String id, String title, ClientSettingCategory category,
			BlockeraIcon icon, ForgeConfigSpec.BooleanValue value) {
		return ClientSettingModel.toggle(id, title, "blockera.setting.local_only.description", category, icon,
				value::get, enabled -> {
					value.set(enabled);
					value.save();
				});
	}

	private static void setVanillaOption(Runnable setter) {
		setter.run();
		Minecraft.getInstance().options.save();
	}

	private static ClientSettingModel widget(String id, String title, BlockeraIcon icon) {
		return ClientSettingModel.module("widget_" + id.substring(id.indexOf(':') + 1), title,
				"blockera.setting.widget.description", ClientSettingCategory.WIDGETS, icon,
				() -> HudLayoutStore.instance().settings(id).enabled,
				enabled -> {
					HudLayoutStore.instance().settings(id).enabled = enabled;
					HudLayoutStore.instance().save();
				}, screen -> screen.openWidgetSettings(id, title));
	}
}
