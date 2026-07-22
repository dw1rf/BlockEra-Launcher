package space.blockera.core;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import space.blockera.core.api.WidgetRegistry;
import space.blockera.core.config.ClientConfig;
import space.blockera.core.chat.BlockeraChatRuntime;
import space.blockera.core.chat.ChatConfigStore;
import space.blockera.core.hud.BuiltinHudWidgets;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.enhancement.HitboxConfigStore;
import space.blockera.core.tools.LocalToolConfigStore;

@Mod(BlockeraCore.MOD_ID)
public final class BlockeraCore {
	public static final String MOD_ID = "blockera_core";
	public static final String VERSION = "0.4.0";

	private final WidgetRegistry widgetRegistry = BuiltinHudWidgets.registry();

	@SuppressWarnings("removal") // Forge 43 exposes configuration registration through this 1.19.2 API.
	public BlockeraCore() {
		ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC, "blockera-core-client.toml");
		HudLayoutStore.instance().load();
		ChatConfigStore.instance().load();
		HitboxConfigStore.instance().load();
		LocalToolConfigStore.instance().load();
		BlockeraChatRuntime.instance().reload();
	}

	public WidgetRegistry widgetRegistry() {
		return widgetRegistry;
	}
}
