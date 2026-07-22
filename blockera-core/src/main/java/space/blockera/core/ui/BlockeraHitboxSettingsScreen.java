package space.blockera.core.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import space.blockera.core.config.ClientConfig;
import space.blockera.core.enhancement.EntityHitboxPolicy;
import space.blockera.core.enhancement.HitboxAppearanceConfig;
import space.blockera.core.enhancement.HitboxConfigStore;

/** Compact first-party editor for category hitbox colors and opacity. */
public final class BlockeraHitboxSettingsScreen extends Screen {
	private static final ThemeTokens THEME = ThemeTokens.darkDefault();
	private static final String[] COLORS = {"#9B7BFF", "#60D6A7", "#F0C66B", "#68B5FF", "#EE00F5", "#F8F7FF"};
	private final Screen parent;
	private int left;
	private int top;
	private int right;
	private int bottom;

	public BlockeraHitboxSettingsScreen(Screen parent) {
		super(Component.translatable("blockera.setting.hitboxes"));
		this.parent = parent;
	}

	@Override protected void init() {
		int panelWidth = Math.min(640, width - 24);
		int panelHeight = Math.min(390, height - 24);
		left = (width - panelWidth) / 2;
		top = (height - panelHeight) / 2;
		right = left + panelWidth;
		bottom = top + panelHeight;
		addRenderableWidget(new BlockeraButton(left + 14, top + 14, 80, 24,
				Component.translatable("blockera.common.back"), button -> onClose()));
		addRenderableWidget(new BlockeraButton(right - 112, top + 14, 98, 24,
				Component.translatable("blockera.common.done"), button -> onClose(), true));
		int y = top + 112;
		addCategoryButtons(y, EntityHitboxPolicy.Category.PLAYER); y += 54;
		addCategoryButtons(y, EntityHitboxPolicy.Category.ANIMAL); y += 54;
		addCategoryButtons(y, EntityHitboxPolicy.Category.ITEM);
		addRenderableWidget(new BlockeraButton(left + 220, bottom - 50, 36, 24, Component.literal("-"), button -> changeWidth(-0.25F)));
		addRenderableWidget(new BlockeraButton(left + 320, bottom - 50, 36, 24, Component.literal("+"), button -> changeWidth(0.25F)));
		addRenderableWidget(new BlockeraButton(right - 208, bottom - 50, 194, 24, eyeLabel(), button -> {
			config().setEyeDirection(!config().eyeDirection()); save(); rebuildWidgets();
		}));
	}

	private void addCategoryButtons(int y, EntityHitboxPolicy.Category category) {
		addRenderableWidget(new BlockeraButton(right - 306, y + 7, 102, 24, enabledLabel(category), button -> {
			ForgeConfigSpec.BooleanValue value = categoryValue(category);
			value.set(!value.get());
			value.save();
			rebuildWidgets();
		}));
		addRenderableWidget(new BlockeraButton(right - 194, y + 7, 82, 24, Component.translatable("blockera.hitbox.color"), button -> cycleColor(category)));
		addRenderableWidget(new BlockeraButton(right - 104, y + 7, 34, 24, Component.literal("-"), button -> opacity(category, -0.1F)));
		addRenderableWidget(new BlockeraButton(right - 62, y + 7, 34, 24, Component.literal("+"), button -> opacity(category, 0.1F)));
	}

	@Override public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		GuiComponent.fill(poseStack, 0, 0, width, height, THEME.gameBackdropArgb());
		BlockeraDraw.glassPanel(poseStack, left, top, right, bottom, 14, 0xD00E111A, THEME.borderHoverArgb());
		UiFont.drawSemibold(poseStack, title, left + 112, top + 21, THEME.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.translatable("blockera.hitbox.subtitle"), left + 112, top + 37, THEME.textMutedArgb());
		BlockeraDraw.glassPanel(poseStack, left + 14, top + 54, right - 14, top + 98, 8, THEME.glassCardArgb(), THEME.borderArgb());
		UiFont.drawCentered(poseStack, Component.translatable("blockera.hitbox.preview"), (left + right) / 2.0F, top + 71, THEME.textMutedArgb());
		int y = top + 112;
		renderCategory(poseStack, y, EntityHitboxPolicy.Category.PLAYER, "blockera.hitbox.players"); y += 54;
		renderCategory(poseStack, y, EntityHitboxPolicy.Category.ANIMAL, "blockera.hitbox.animals"); y += 54;
		renderCategory(poseStack, y, EntityHitboxPolicy.Category.ITEM, "blockera.hitbox.items");
		UiFont.drawSemibold(poseStack, Component.translatable("blockera.hitbox.line_width"), left + 18, bottom - 42, THEME.textPrimaryArgb());
		UiFont.draw(poseStack, Component.literal(String.format("%.2f", config().lineWidth())), left + 272, bottom - 42, THEME.textMutedArgb());
		super.render(poseStack, mouseX, mouseY, partialTick);
	}

	private void renderCategory(PoseStack poseStack, int y, EntityHitboxPolicy.Category category, String key) {
		HitboxAppearanceConfig.CategoryStyle style = config().style(category);
		BlockeraDraw.glassPanel(poseStack, left + 14, y, right - 14, y + 42, 8, THEME.glassCardArgb(), THEME.borderArgb());
		int rgb = 0xFF000000 | style.rgb();
		BlockeraDraw.roundedRect(poseStack, left + 26, y + 11, left + 46, y + 31, 5, rgb);
		UiFont.drawSemibold(poseStack, Component.translatable(key), left + 58, y + 14, THEME.textPrimaryArgb());
		UiFont.drawSmall(poseStack, Component.literal(Math.round(style.opacity() * 100) + "%"), right - 360, y + 16, THEME.textMutedArgb());
	}

	private void cycleColor(EntityHitboxPolicy.Category category) {
		HitboxAppearanceConfig.CategoryStyle style = config().style(category);
		int index = 0;
		while (index < COLORS.length && !COLORS[index].equals(style.color())) index++;
		style.setColor(COLORS[(index + 1) % COLORS.length]); save();
	}
	private void opacity(EntityHitboxPolicy.Category category, float delta) {
		var style = config().style(category);
		style.setOpacity(Math.max(0.05F, Math.min(1.0F, style.opacity() + delta))); save();
	}
	private void changeWidth(float delta) { config().setLineWidth(config().lineWidth() + delta); save(); }
	private ForgeConfigSpec.BooleanValue categoryValue(EntityHitboxPolicy.Category category) {
		return switch (category) {
			case PLAYER -> ClientConfig.HITBOX_PLAYERS;
			case ANIMAL -> ClientConfig.HITBOX_ANIMALS;
			case ITEM -> ClientConfig.HITBOX_ITEMS;
			case NONE -> throw new IllegalArgumentException("NONE has no toggle");
		};
	}
	private Component enabledLabel(EntityHitboxPolicy.Category category) {
		return Component.translatable(categoryValue(category).get() ? "blockera.common.enabled" : "blockera.common.disabled");
	}
	private Component eyeLabel() { return Component.translatable(config().eyeDirection() ? "blockera.hitbox.eye_on" : "blockera.hitbox.eye_off"); }
	private HitboxAppearanceConfig config() { return HitboxConfigStore.instance().config(); }
	private void save() { HitboxConfigStore.instance().save(); }
	@Override public void onClose() { save(); if (minecraft != null) minecraft.setScreen(parent); }
	@Override public boolean isPauseScreen() { return parent != null && parent.isPauseScreen(); }
}
