package space.blockera.core.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PanoramaRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import space.blockera.core.BlockeraCore;

/** Vanilla-shaped title menu with Blockera branding and rendering. */
@SuppressWarnings("removal")
public final class BlockeraTitleScreen extends Screen {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final ResourceLocation LOGO = new ResourceLocation(BlockeraCore.MOD_ID,
			"textures/gui/title/blockera_logo.png");
	private static final int LOGO_TEXTURE_WIDTH = 1280;
	private static final int LOGO_TEXTURE_HEIGHT = 239;
	private final PanoramaRenderer panorama = new PanoramaRenderer(TitleScreen.CUBE_MAP);
	private final UiAnimation opening = new UiAnimation(0.0F);
	private float panoramaFade;

	public BlockeraTitleScreen() {
		super(Component.translatable("blockera.title.name"));
	}

	@Override
	protected void init() {
		int buttonWidth = Math.min(310, Math.max(220, width / 4));
		int buttonHeight = 24;
		int left = width / 2 - buttonWidth / 2;
		int top = Math.max(118, height / 2 - 20);
		int gap = 32;

		addRenderableWidget(new BlockeraButton(left, top, buttonWidth, buttonHeight,
				Component.translatable("blockera.title.play"),
				button -> minecraft.setScreen(new SelectWorldScreen(this)), true));
		addRenderableWidget(new BlockeraButton(left, top + gap, buttonWidth, buttonHeight,
				Component.translatable("blockera.title.servers"),
				button -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
		addRenderableWidget(new BlockeraButton(left, top + gap * 2, buttonWidth / 2 - 4, buttonHeight,
				Component.translatable("blockera.title.mods"),
				button -> minecraft.setScreen(new net.minecraftforge.client.gui.ModListScreen(this))));
		addRenderableWidget(new BlockeraButton(left + buttonWidth / 2 + 4, top + gap * 2, buttonWidth / 2 - 4, buttonHeight,
				Component.translatable("blockera.title.hud"),
				button -> minecraft.setScreen(new BlockeraMenuScreen(this))));
		addRenderableWidget(new BlockeraButton(left, top + gap * 3, 98, buttonHeight,
				Component.translatable("menu.options"),
				button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
		addRenderableWidget(new BlockeraButton(left + buttonWidth - 98, top + gap * 3, 98, buttonHeight,
				Component.translatable("menu.quit"), button -> minecraft.stop()));
		addRenderableWidget(new BlockeraButton(width / 2 - 82, top + gap * 4, 164, 24,
				Component.translatable("blockera.control.title"),
				button -> minecraft.setScreen(new BlockeraMenuScreen(this)), true));
		opening.snap(0.0F);
	}

	@Override
	public void tick() {
		panoramaFade = Math.min(1.0F, panoramaFade + 0.025F);
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		panorama.render(partialTick, panoramaFade);
		float progress = opening.update(1.0F, 180);
		GuiComponent.fill(poseStack, 0, 0, width, height, Math.round(progress * 112.0F) << 24 | 0x05070B);
		renderLogo(poseStack);
		super.render(poseStack, mouseX, mouseY, partialTick);
		UiFont.drawSmall(poseStack, Component.translatable("blockera.ingame.version", BlockeraCore.VERSION),
				12, height - 26, THEME.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.literal("Minecraft 1.19.2"), 12, height - 13, THEME.textSecondaryArgb());
	}

	private void renderLogo(PoseStack poseStack) {
		int maximumWidth = Math.max(220, Math.round(width * 0.48F));
		int maximumHeight = Math.min(94, Math.max(48, height / 4));
		float scale = Math.min(maximumWidth / (float) LOGO_TEXTURE_WIDTH,
				maximumHeight / (float) LOGO_TEXTURE_HEIGHT);
		int logoWidth = Math.round(LOGO_TEXTURE_WIDTH * scale);
		int logoHeight = Math.round(LOGO_TEXTURE_HEIGHT * scale);
		int x = (width - logoWidth) / 2;
		int y = Math.max(26, height / 5 - logoHeight / 2 + 16);
		RenderSystem.setShader(GameRenderer::getPositionTexShader);
		RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
		RenderSystem.setShaderTexture(0, LOGO);
		GuiComponent.blit(poseStack, x, y, logoWidth, logoHeight, 0.0F, 0.0F,
				LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT, LOGO_TEXTURE_WIDTH, LOGO_TEXTURE_HEIGHT);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
