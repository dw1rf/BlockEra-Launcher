package space.blockera.core.hud;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import space.blockera.core.BlockeraCore;

/** Session-local combat streak state; event hooks update it without server integration. */
@Mod.EventBusSubscriber(modid = BlockeraCore.MOD_ID, value = Dist.CLIENT)
public final class ComboTracker {
	private static int combo;
	private static long lastHit;
	private ComboTracker() { }
	public static void hit() { long now = System.currentTimeMillis(); combo = now - lastHit <= 2_000L ? combo + 1 : 1; lastHit = now; }
	public static void reset() { combo = 0; }
	public static String value() { if (System.currentTimeMillis() - lastHit > 2_000L) combo = 0; return Integer.toString(combo); }
	@SubscribeEvent public static void onAttack(AttackEntityEvent event) {
		if (event.getEntity() == Minecraft.getInstance().player && event.getTarget().isAlive()) hit();
	}
}
