package space.blockera.client.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

/** Compact translucent Escape/Right-Shift menu ported from the Forge client. */
public final class BlockeraInGameMenuScreen extends Screen {
    private final boolean pauseGame;
    private int panelLeft;
    private int panelTop;
    private int panelRight;
    private int panelBottom;

    public BlockeraInGameMenuScreen(boolean pauseGame) {
        super(Component.translatable("blockera.ingame.title"));
        this.pauseGame = pauseGame;
    }

    @Override
    protected void init() {
        int panelWidth = Math.min(720, width - 24);
        int panelHeight = Math.min(330, height - 24);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        panelRight = panelLeft + panelWidth;
        panelBottom = panelTop + panelHeight;

        int leftWidth = Math.min(160, Math.max(118, panelWidth / 4));
        int buttonLeft = panelLeft + 14;
        int buttonWidth = leftWidth - 28;
        int buttonHeight = 27;
        int top = panelTop + 18;
        int gap = 34;
        addRenderableWidget(new BlockeraButton(buttonLeft, top, buttonWidth, buttonHeight,
            Component.translatable("blockera.ingame.resume"), button -> onClose(), true));
        addRenderableWidget(new BlockeraButton(buttonLeft, top + gap, buttonWidth, buttonHeight,
            Component.translatable("blockera.control.title"),
            button -> minecraft.setScreen(new BlockeraMenuScreen(this))));
        addRenderableWidget(new BlockeraButton(buttonLeft, top + gap * 2, buttonWidth, buttonHeight,
            Component.translatable("blockera.ingame.settings"),
            button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options))));
        addRenderableWidget(new BlockeraButton(buttonLeft, top + gap * 3, buttonWidth, buttonHeight,
            Component.translatable("blockera.ingame.target"),
            button -> minecraft.setScreen(new BlockeraMenuScreen(this))));
        addRenderableWidget(new BlockeraButton(buttonLeft, panelBottom - buttonHeight - 15, buttonWidth, buttonHeight,
            Component.translatable("blockera.ingame.disconnect"),
            button -> minecraft.disconnect(new BlockeraTitleScreen(), false)));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, ThemeTokens.BACKDROP);
        BlockeraDraw.panel(graphics, panelLeft, panelTop, panelRight, panelBottom);
        int divider = panelLeft + Math.min(160, Math.max(118, (panelRight - panelLeft) / 4));
        graphics.fill(divider, panelTop + 12, divider + 1, panelBottom - 12, ThemeTokens.BORDER);

        int contentLeft = divider + 22;
        UiText.drawSemibold(graphics, Component.translatable("blockera.ingame.quick_settings"), contentLeft,
            panelTop + 21, ThemeTokens.TEXT);
        drawStatusCard(graphics, contentLeft, panelTop + 48, panelRight - 22,
            Component.translatable("blockera.ingame.local_target"),
            Component.translatable("blockera.ingame.target_help"), ThemeTokens.SUCCESS);
        drawStatusCard(graphics, contentLeft, panelTop + 112, panelRight - 22,
            Component.translatable("blockera.ingame.safety"),
            Component.translatable("blockera.ingame.safety_help"), ThemeTokens.WARNING);
        drawStatusCard(graphics, contentLeft, panelTop + 176, panelRight - 22,
            Component.translatable("blockera.ingame.current_server"), serverName(), ThemeTokens.MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void drawStatusCard(
        GuiGraphics graphics,
        int left,
        int top,
        int right,
        Component title,
        Component value,
        int valueColor
    ) {
        BlockeraDraw.card(graphics, left, top, right, top + 49, false);
        UiText.drawSemibold(graphics, title, left + 10, top + 9, ThemeTokens.TEXT);
        UiText.draw(graphics, value, left + 10, top + 28, valueColor);
    }

    private Component serverName() {
        if (minecraft.isSingleplayer()) {
            return Component.translatable("blockera.ingame.singleplayer");
        }
        var server = minecraft.getCurrentServer();
        return Component.literal(server == null ? "—" : server.name);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return pauseGame;
    }
}
