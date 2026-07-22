package space.blockera.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Fabric port of the Forge 1.19.2 main menu, retaining its layout and Blockera logo. */
public final class BlockeraTitleScreen extends Screen {
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath(
        "blockera_client", "textures/gui/title/blockera_logo.png"
    );
    private static final int LOGO_WIDTH = 1280;
    private static final int LOGO_HEIGHT = 239;

    public BlockeraTitleScreen() {
        super(Component.translatable("blockera.title.name"));
    }

    @Override
    protected void init() {
        int buttonWidth = Math.min(230, Math.max(200, width / 4));
        int buttonHeight = ThemeTokens.CONTROL_HEIGHT;
        int left = width / 2 - buttonWidth / 2;
        int top = Math.max(104, height / 2 - 22);
        int gap = 25;

        addRenderableWidget(new BlockeraButton(left, top, buttonWidth, buttonHeight,
            Component.translatable("blockera.title.play"),
            button -> minecraft.setScreen(new SelectWorldScreen(this))));
        addRenderableWidget(new BlockeraButton(left, top + gap, buttonWidth, buttonHeight,
            Component.translatable("blockera.title.servers"),
            button -> minecraft.setScreen(new JoinMultiplayerScreen(this))));
        addRenderableWidget(new BlockeraButton(left, top + gap * 2, buttonWidth / 2 - 4, buttonHeight,
            Component.translatable("blockera.title.modules"),
            button -> minecraft.setScreen(new BlockeraMenuScreen(this))));
        addRenderableWidget(new BlockeraButton(left + buttonWidth / 2 + 4, top + gap * 2,
            buttonWidth / 2 - 4, buttonHeight, Component.translatable("blockera.control.hud"),
            button -> minecraft.setScreen(new BlockeraMenuScreen(this, BlockeraMenuScreen.Tab.HUD))));
        addRenderableWidget(new BlockeraButton(left, top + gap * 3, buttonWidth / 2 - 4, buttonHeight,
            Component.translatable("menu.options"),
            button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new BlockeraButton(left + buttonWidth / 2 + 4, top + gap * 3,
            buttonWidth / 2 - 4, buttonHeight, Component.translatable("menu.quit"), button -> minecraft.stop()));
        addRenderableWidget(new BlockeraButton(left, top + gap * 4, buttonWidth / 2 - 4, buttonHeight,
            Component.translatable("blockera.accounts.title"),
            button -> minecraft.setScreen(new BlockeraAccountScreen(this))));
        addRenderableWidget(new BlockeraButton(left + buttonWidth / 2 + 4, top + gap * 4,
            buttonWidth / 2 - 4, buttonHeight, Component.translatable("blockera.control.title"),
            button -> minecraft.setScreen(new BlockeraMenuScreen(this))));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderPanorama(graphics, partialTick);
        graphics.fill(0, 0, width, height, ThemeTokens.BACKDROP);
        renderLogo(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        UiText.draw(graphics, Component.translatable("blockera.client.version"), 12, height - 25,
            ThemeTokens.TEXT);
        UiText.draw(graphics, Component.literal("Fabric · Minecraft 1.21.11"), 12, height - 13,
            ThemeTokens.MUTED);
    }

    private void renderLogo(GuiGraphics graphics) {
        int maximumWidth = Math.max(220, Math.round(width * 0.48F));
        int maximumHeight = Math.min(94, Math.max(48, height / 4));
        float scale = Math.min(maximumWidth / (float) LOGO_WIDTH, maximumHeight / (float) LOGO_HEIGHT);
        int logoWidth = Math.round(LOGO_WIDTH * scale);
        int logoHeight = Math.round(LOGO_HEIGHT * scale);
        int x = (width - logoWidth) / 2;
        int y = Math.max(25, height / 5 - logoHeight / 2 + 14);
        graphics.blit(RenderPipelines.GUI_TEXTURED, LOGO, x, y, 0.0F, 0.0F,
            logoWidth, logoHeight, LOGO_WIDTH, LOGO_HEIGHT, LOGO_WIDTH, LOGO_HEIGHT);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
