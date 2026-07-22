package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.ModListScreen;
import space.blockera.core.BlockeraCore;
import space.blockera.core.config.ClientConfig;
import space.blockera.core.hud.BuiltinWidgetIds;
import space.blockera.core.hud.HudDataSnapshot;
import space.blockera.core.hud.HudLayoutStore;
import space.blockera.core.ui.components.ToggleSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Compact in-game control surface that leaves the world visible around it. */
public final class BlockeraInGameMenuScreen extends Screen {
	private record QuickSetting(String key, boolean available, BooleanSupplier enabled, Consumer<Boolean> setter,
			ToggleSwitch toggle) {
	}

	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private final UiAnimation opening = new UiAnimation(0.0F);
	private final List<QuickSetting> quickSettings = new ArrayList<>();
	private int panelLeft;
	private int panelTop;
	private int panelRight;
	private int panelBottom;
	private int leftRight;
	private int centerRight;
	private int rowsTop;
	private boolean compact;
	private final boolean pauseGame;

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
		compact = panelWidth < 560 || panelHeight < 240;
		int leftWidth = compact ? Math.min(108, Math.max(82, panelWidth / 3)) : 150;
		int rightWidth = compact ? 0 : 172;
		leftRight = panelLeft + leftWidth;
		centerRight = panelRight - rightWidth;
		int actionLeft = panelLeft + 10;
		int actionWidth = leftWidth - 20;
		int actionTop = panelTop + (compact ? 8 : 15);
		int actionHeight = compact ? Math.max(16, Math.min(20, (panelHeight - 30) / 6)) : (panelHeight < 280 ? 24 : 28);
		int actionGap = compact ? 3 : 5;
		addAction(actionLeft, actionTop, actionWidth, actionHeight, "blockera.ingame.resume", true,
				button -> onClose());
		addAction(actionLeft, actionTop + (actionHeight + actionGap), actionWidth, actionHeight,
				"blockera.ingame.settings", false, button -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
		addAction(actionLeft, actionTop + (actionHeight + actionGap) * 2, actionWidth, actionHeight,
				"blockera.ingame.hud_editor", false, button -> minecraft.setScreen(new HudEditorScreen(this)));
		addAction(actionLeft, actionTop + (actionHeight + actionGap) * 3, actionWidth, actionHeight,
				"blockera.ingame.mods", false, button -> minecraft.setScreen(new ModListScreen(this)));
		addAction(actionLeft, actionTop + (actionHeight + actionGap) * 4, actionWidth, actionHeight,
				"blockera.ingame.cosmetics", false,
				button -> minecraft.setScreen(new ComingSoonScreen(this, "blockera.ingame.cosmetics")));
		addAction(actionLeft, panelBottom - actionHeight - 12, actionWidth, actionHeight,
				"blockera.ingame.disconnect", false, button -> disconnect());

		buildQuickSettings();
		rowsTop = panelTop + (compact ? 25 : 38);
		int linksTop = panelBottom - 31;
		int linkLeft = leftRight + 12;
		int linkAvailable = centerRight - leftRight - 24;
		int linkWidth = Math.max(38, (linkAvailable - 8) / 3);
		if (!compact) {
			String[] links = {"website", "discord", "support"};
			for (int index = 0; index < links.length; index++) {
				String key = links[index];
				addRenderableWidget(new BlockeraButton(linkLeft + index * (linkWidth + 4), linksTop,
						linkWidth, 20, Component.translatable("blockera.main.link." + key),
						button -> minecraft.setScreen(new ComingSoonScreen(this, "blockera.main.link." + key))));
			}
		}
		opening.snap(0.0F);
	}

	private void addAction(int x, int y, int width, int height, String key, boolean accent, BlockeraButton.OnPress onPress) {
		addRenderableWidget(new BlockeraButton(x, y, width, height, Component.translatable(key), onPress, accent));
	}

	private void buildQuickSettings() {
		quickSettings.clear();
		addQuick("blockera.quick.full_bright", true,
				ClientConfig.FULL_BRIGHT::get,
				enabled -> { ClientConfig.FULL_BRIGHT.set(enabled); ClientConfig.FULL_BRIGHT.save(); });
		addQuick("blockera.quick.camera_bob", true, () -> minecraft.options.bobView().get(),
				enabled -> { minecraft.options.bobView().set(enabled); minecraft.options.save(); });
		addQuick("blockera.quick.cps", false, () -> false, enabled -> { });
		addWidgetQuick("blockera.quick.coordinates", BuiltinWidgetIds.COORDINATES);
		addWidgetQuick("blockera.quick.ping", BuiltinWidgetIds.PING);
		addQuick("blockera.quick.voice_chat", false, () -> false, enabled -> { });
		addQuick("blockera.quick.armor", false, () -> false, enabled -> { });
	}

	private void addWidgetQuick(String key, String widgetId) {
		addQuick(key, true, () -> HudLayoutStore.instance().settings(widgetId).enabled, enabled -> {
			HudLayoutStore.instance().settings(widgetId).enabled = enabled;
			HudLayoutStore.instance().save();
		});
	}

	private void addQuick(String key, boolean available, BooleanSupplier enabled, Consumer<Boolean> setter) {
		quickSettings.add(new QuickSetting(key, available, enabled, setter, new ToggleSwitch(enabled.getAsBoolean())));
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		float progress = opening.update(1.0F, 190);
		GuiComponent.fill(poseStack, 0, 0, width, height, Math.round(progress * 96.0F) << 24 | 0x070A10);
		BlockeraDraw.glassPanel(poseStack, panelLeft, panelTop, panelRight, panelBottom,
				THEME.panelRadius(), THEME.glassPanelArgb(), THEME.borderArgb());
		BlockeraDraw.roundedRect(poseStack, leftRight, panelTop + 10, leftRight + 1, panelBottom - 10, 0, THEME.borderArgb());
		if (!compact) BlockeraDraw.roundedRect(poseStack, centerRight, panelTop + 10, centerRight + 1, panelBottom - 10, 0, THEME.borderArgb());
		renderQuickSettings(poseStack, mouseX, mouseY);
		if (!compact) renderServerSummary(poseStack);
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private void renderQuickSettings(PoseStack poseStack, int mouseX, int mouseY) {
		int left = leftRight + 12;
		int right = centerRight - 12;
		UiFont.drawSemibold(poseStack, Component.translatable("blockera.ingame.quick_settings"), left,
				panelTop + 15, THEME.textPrimaryArgb());
		int availableHeight = panelBottom - (compact ? 6 : 40) - rowsTop;
		int rowHeight = Math.max(compact ? 14 : 22, Math.min(compact ? 19 : 30, availableHeight / quickSettings.size()));
		for (int index = 0; index < quickSettings.size(); index++) {
			QuickSetting setting = quickSettings.get(index);
			int top = rowsTop + index * rowHeight;
			boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < top + rowHeight - 3;
			BlockeraDraw.roundedRect(poseStack, left, top, right, top + rowHeight - 3, THEME.smallRadius(),
					hovered && setting.available ? THEME.cardHoverArgb() : THEME.glassCardArgb());
			Component label = UiFont.ellipsize(Component.translatable(setting.key), Math.max(24, right - left - 52), false);
			UiFont.draw(poseStack, label, left + 9,
					top + (rowHeight - 3 - UiFont.REGULAR_SIZE) * 0.5F,
					setting.available ? THEME.textPrimaryArgb() : THEME.textDisabledArgb());
			if (!compact && !setting.available) UiFont.drawSmall(poseStack, Component.translatable("blockera.state.coming_soon"),
					right - 70, top + (rowHeight - 3 - UiFont.SMALL_SIZE) * 0.5F, THEME.textDisabledArgb());
			setting.toggle.render(poseStack, right - 36, top + (rowHeight - 17) / 2,
					setting.enabled.getAsBoolean(), setting.available, THEME);
		}
	}

	private void renderServerSummary(PoseStack poseStack) {
		int left = centerRight + 12;
		int right = panelRight - 12;
		UiFont.drawSemibold(poseStack, Component.translatable("blockera.ingame.current_server"), left,
				panelTop + 15, THEME.textPrimaryArgb());
		BlockeraDraw.roundedRect(poseStack, left, panelTop + 37, right, panelTop + 88,
				THEME.cardRadius(), THEME.glassCardArgb());
		String server = minecraft.getCurrentServer() == null
				? Component.translatable("blockera.ingame.singleplayer").getString() : minecraft.getCurrentServer().name;
		UiFont.drawSemibold(poseStack, UiFont.ellipsize(Component.literal(server), right - left - 18, true),
				left + 9, panelTop + 47, THEME.textPrimaryArgb());
		HudDataSnapshot data = HudDataSnapshot.capture(minecraft);
		UiFont.drawSmall(poseStack, Component.translatable("blockera.ingame.online_ping", data.playerCount(), data.ping()),
				left + 9, panelTop + 66, THEME.textSecondaryArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.ingame.rank"), left, panelTop + 104,
				THEME.textSecondaryArgb());
		UiFont.draw(poseStack, "—", left, panelTop + 120, THEME.textDisabledArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.ingame.updates"), left, panelTop + 151,
				THEME.textSecondaryArgb());
		UiFont.draw(poseStack, Component.translatable("blockera.ingame.up_to_date"), left, panelTop + 167,
				THEME.successArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.ingame.version", BlockeraCore.VERSION), left,
				panelBottom - 24, THEME.textDisabledArgb());
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		int left = leftRight + 12;
		int right = centerRight - 12;
		int availableHeight = panelBottom - (compact ? 6 : 40) - rowsTop;
		int rowHeight = Math.max(compact ? 14 : 22, Math.min(compact ? 19 : 30, availableHeight / quickSettings.size()));
		if (button == 0 && mouseX >= left && mouseX < right && mouseY >= rowsTop
				&& mouseY < rowsTop + rowHeight * quickSettings.size()) {
			int index = Math.min(quickSettings.size() - 1, ((int) mouseY - rowsTop) / rowHeight);
			QuickSetting setting = quickSettings.get(index);
			if (setting.available) setting.setter.accept(!setting.enabled.getAsBoolean());
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}

	private void disconnect() {
		if (minecraft.level != null) minecraft.level.disconnect();
		minecraft.clearLevel(new TitleScreen());
	}

	@Override public void onClose() { minecraft.setScreen(null); }
	@Override public boolean isPauseScreen() { return pauseGame; }
}
