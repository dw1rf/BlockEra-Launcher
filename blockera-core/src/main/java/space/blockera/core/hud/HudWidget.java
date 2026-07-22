package space.blockera.core.hud;

import com.mojang.blaze3d.vertex.PoseStack;
import space.blockera.core.api.Widget;
import java.util.List;

/** Render contract for built-in, data-only Blockera HUD widgets. */
public interface HudWidget extends Widget {
	default List<WidgetOptionSchema> options() { return List.of(); }
	int width(HudDataSnapshot data);

	int height(HudDataSnapshot data);

	void render(PoseStack poseStack, HudDataSnapshot data, int x, int y,
			HudWidgetSettings settings, boolean preview);
}
