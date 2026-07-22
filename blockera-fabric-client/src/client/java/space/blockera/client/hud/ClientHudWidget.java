package space.blockera.client.hud;

import net.minecraft.client.gui.GuiGraphics;

public interface ClientHudWidget {
    String id();
    int width();
    int height();
    void render(GuiGraphics graphics, int x, int y, HudWidgetSettings settings);
}
