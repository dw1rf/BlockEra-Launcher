package space.blockera.core.ui.components;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import space.blockera.core.ui.BlockeraDraw;
import space.blockera.core.ui.ThemeTokens;
import space.blockera.core.ui.UiFont;

/** Lightweight Unicode search input rendered with the Blockera UI font. */
public final class SearchField extends AbstractWidget {
	private final ThemeTokens theme;
	private String value = "";
	private int cursor;

	public SearchField(int x, int y, int width, int height, ThemeTokens theme) {
		super(x, y, width, height, Component.translatable("blockera.search.placeholder"));
		this.theme = theme;
	}

	public String value() { return value; }
	public void setValue(String value) {
		this.value = value == null ? "" : value.substring(0, Math.min(64, value.length()));
		cursor = this.value.length();
	}

	public void setBounds(int x, int y, int width, int height) {
		this.x = x; this.y = y; setWidth(width); setHeight(height);
	}

	@Override
	public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		int border = isFocused() ? theme.accentArgb() : theme.borderArgb();
		BlockeraDraw.roundedRect(poseStack, x, y, x + width, y + height, 6, border);
		BlockeraDraw.roundedRect(poseStack, x + 1, y + 1, x + width - 1, y + height - 1, 5, theme.cardArgb());
		box(poseStack, x + 8, y + (height - 7) / 2, 6, theme.textMutedArgb());
		GuiComponent.fill(poseStack, x + 13, y + height / 2 + 2, x + 16, y + height / 2 + 3, theme.textMutedArgb());
		Component text = value.isEmpty() && !isFocused() ? getMessage() : Component.literal(value);
		int color = value.isEmpty() && !isFocused() ? theme.textMutedArgb() : theme.textPrimaryArgb();
		UiFont.draw(poseStack, UiFont.ellipsize(text, width - 28, false), x + 20, y + (height - 9) / 2.0F, color);
		if (isFocused() && (Util.getMillis() / 500L) % 2L == 0L) {
			int caret = x + 20 + UiFont.width(Component.literal(value.substring(0, cursor)));
			GuiComponent.fill(poseStack, Math.min(x + width - 8, caret), y + 5,
					Math.min(x + width - 7, caret + 1), y + height - 5, theme.accentHoverArgb());
		}
	}

	@Override
	public void onClick(double mouseX, double mouseY) {
		setFocused(true);
		cursor = value.length();
	}

	@Override
	public boolean charTyped(char codePoint, int modifiers) {
		if (!isFocused() || !SharedConstants.isAllowedChatCharacter(codePoint) || value.length() >= 64) return false;
		value = value.substring(0, cursor) + codePoint + value.substring(cursor);
		cursor++;
		return true;
	}

	@Override
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!isFocused()) return false;
		if (Screen.isSelectAll(keyCode)) {
			value = "";
			cursor = 0;
			return true;
		}
		if (Screen.isPaste(keyCode)) {
			String clipboard = SharedConstants.filterText(Minecraft.getInstance().keyboardHandler.getClipboard());
			String accepted = clipboard.substring(0, Math.min(64 - value.length(), clipboard.length()));
			value = value.substring(0, cursor) + accepted + value.substring(cursor);
			cursor += accepted.length();
			return true;
		}
		switch (keyCode) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (cursor > 0) { value = value.substring(0, cursor - 1) + value.substring(cursor); cursor--; }
				return true;
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (cursor < value.length()) value = value.substring(0, cursor) + value.substring(cursor + 1);
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> { cursor = Math.max(0, cursor - 1); return true; }
			case GLFW.GLFW_KEY_RIGHT -> { cursor = Math.min(value.length(), cursor + 1); return true; }
			case GLFW.GLFW_KEY_HOME -> { cursor = 0; return true; }
			case GLFW.GLFW_KEY_END -> { cursor = value.length(); return true; }
			default -> { return false; }
		}
	}

	@Override
	public void updateNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	private static void box(PoseStack poseStack, int x, int y, int size, int color) {
		GuiComponent.fill(poseStack, x, y, x + size, y + 1, color);
		GuiComponent.fill(poseStack, x, y + size - 1, x + size, y + size, color);
		GuiComponent.fill(poseStack, x, y, x + 1, y + size, color);
		GuiComponent.fill(poseStack, x + size - 1, y, x + size, y + size, color);
	}
}
