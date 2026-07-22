package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Honest placeholder for sections that require an unavailable first-party backend or URL. */
public final class ComingSoonScreen extends Screen {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private final Screen parent;
	private final String sectionKey;

	public ComingSoonScreen(Screen parent, String sectionKey) {
		super(Component.translatable("blockera.coming_soon.title"));
		this.parent = parent;
		this.sectionKey = sectionKey;
	}

	@Override
	protected void init() {
		addRenderableWidget(new BlockeraButton(width / 2 - 72, height / 2 + 48, 144, 22,
				Component.translatable("blockera.common.back"), button -> minecraft.setScreen(parent), true));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiComponent.fill(poseStack, 0, 0, width, height, THEME.menuBackdropArgb());
		BlockeraDraw.glassPanel(poseStack, width / 2 - 190, height / 2 - 86, width / 2 + 190, height / 2 + 86,
				12, THEME.glassPanelArgb(), THEME.borderArgb());
		UiFont.drawCentered(poseStack, Component.translatable(sectionKey), width / 2.0F, height / 2.0F - 54,
				THEME.accentArgb());
		UiFont.drawCentered(poseStack, Component.translatable("blockera.coming_soon.title"), width / 2.0F,
				height / 2.0F - 30, THEME.textPrimaryArgb());
		UiFont.drawCentered(poseStack, UiFont.ellipsize(Component.translatable("blockera.coming_soon.reason"),
				Math.max(120, width - 40), false), width / 2.0F, height / 2.0F - 7, THEME.textMutedArgb());
		UiFont.drawCentered(poseStack, UiFont.ellipsize(Component.translatable("blockera.coming_soon.no_fake_data"),
				Math.max(120, width - 40), false), width / 2.0F, height / 2.0F + 8, THEME.textMutedArgb());
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	@Override public void onClose() { minecraft.setScreen(parent); }
}
