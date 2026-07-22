package space.blockera.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Applies the packaged Blockera fonts while preserving translations and component styling. */
public final class UiText {
    private static final FontDescription REGULAR = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath("blockera_client", "ui_regular")
    );
    private static final FontDescription SEMIBOLD = new FontDescription.Resource(
        Identifier.fromNamespaceAndPath("blockera_client", "ui_semibold")
    );

    private UiText() {
    }

    public static Component regular(Component value) {
        return value.copy().withStyle(style -> style.withFont(REGULAR));
    }

    public static Component semibold(Component value) {
        return value.copy().withStyle(style -> style.withFont(SEMIBOLD).withBold(false));
    }

    public static void draw(GuiGraphics graphics, Component value, int x, int y, int color) {
        graphics.drawString(Minecraft.getInstance().font, regular(value), x, y, color, false);
    }

    public static void drawSemibold(GuiGraphics graphics, Component value, int x, int y, int color) {
        graphics.drawString(Minecraft.getInstance().font, semibold(value), x, y, color, false);
    }

	public static void drawCentered(GuiGraphics graphics, Component value, int centerX, int y, int color) {
		Component styled = regular(value);
		int x = centerX - Minecraft.getInstance().font.width(styled) / 2;
		graphics.drawString(Minecraft.getInstance().font, styled, x, y, color, false);
	}
}
