package space.blockera.core.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.blockera.core.ui.BlockeraChatStyleScope;
import space.blockera.core.ui.UiFont;
import space.blockera.core.ui.render.UiFontRenderer;

@Mixin(Font.class)
public abstract class FontMixin {
	@Inject(method = "drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I",
			at = @At("HEAD"), cancellable = true)
	private void blockera$drawStringShadow(PoseStack poseStack, String text, float x, float y, int color,
			CallbackInfoReturnable<Integer> callback) {
		if (!BlockeraChatStyleScope.active()) return;
		draw(poseStack, text, x, y, color);
		callback.setReturnValue(endX(x, UiFontRenderer.instance().width(text, UiFont.REGULAR_SIZE,
				UiFontRenderer.Weight.REGULAR)));
	}

	@Inject(method = "drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFIZ)I",
			at = @At("HEAD"), cancellable = true)
	private void blockera$drawStringShadowBidirectional(PoseStack poseStack, String text, float x, float y, int color,
			boolean bidirectional, CallbackInfoReturnable<Integer> callback) {
		blockera$drawStringShadow(poseStack, text, x, y, color, callback);
	}

	@Inject(method = "draw(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I",
			at = @At("HEAD"), cancellable = true)
	private void blockera$drawString(PoseStack poseStack, String text, float x, float y, int color,
			CallbackInfoReturnable<Integer> callback) {
		blockera$drawStringShadow(poseStack, text, x, y, color, callback);
	}

	@Inject(method = "drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;FFI)I",
			at = @At("HEAD"), cancellable = true)
	private void blockera$drawComponentShadow(PoseStack poseStack, Component text, float x, float y, int color,
			CallbackInfoReturnable<Integer> callback) {
		if (!BlockeraChatStyleScope.active()) return;
		draw(poseStack, text, x, y, color);
		callback.setReturnValue(endX(x, UiFontRenderer.instance().width(text, UiFont.REGULAR_SIZE,
				UiFontRenderer.Weight.REGULAR)));
	}

	@Inject(method = "draw(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;FFI)I",
			at = @At("HEAD"), cancellable = true)
	private void blockera$drawComponent(PoseStack poseStack, Component text, float x, float y, int color,
			CallbackInfoReturnable<Integer> callback) {
		blockera$drawComponentShadow(poseStack, text, x, y, color, callback);
	}
	@Inject(method = "drawShadow(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/util/FormattedCharSequence;FFI)I",
			at = @At("HEAD"), cancellable = true)
	private void blockera$drawChatShadow(PoseStack poseStack, FormattedCharSequence text, float x, float y, int color,
			CallbackInfoReturnable<Integer> callback) {
		if (!BlockeraChatStyleScope.active()) return;
		draw(poseStack, text, x, y, color);
		callback.setReturnValue(Math.round(x + UiFontRenderer.instance().width(text, UiFont.REGULAR_SIZE,
				UiFontRenderer.Weight.REGULAR)));
	}

	@Inject(method = "draw(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/util/FormattedCharSequence;FFI)I",
			at = @At("HEAD"), cancellable = true)
	private void blockera$drawChat(PoseStack poseStack, FormattedCharSequence text, float x, float y, int color,
			CallbackInfoReturnable<Integer> callback) {
		if (!BlockeraChatStyleScope.active()) return;
		draw(poseStack, text, x, y, color);
		callback.setReturnValue(Math.round(x + UiFontRenderer.instance().width(text, UiFont.REGULAR_SIZE,
				UiFontRenderer.Weight.REGULAR)));
	}

	private static void draw(PoseStack poseStack, FormattedCharSequence text, float x, float y, int color) {
		int argb = (color >>> 24) == 0 ? color | 0xFF000000 : color;
		UiFontRenderer.instance().draw(poseStack, text, x, y, UiFont.REGULAR_SIZE, argb,
				UiFontRenderer.Weight.REGULAR);
	}

	private static void draw(PoseStack poseStack, String text, float x, float y, int color) {
		int argb = (color >>> 24) == 0 ? color | 0xFF000000 : color;
		UiFontRenderer.instance().draw(poseStack, text, x, y, UiFont.REGULAR_SIZE, argb,
				UiFontRenderer.Weight.REGULAR);
	}

	private static void draw(PoseStack poseStack, Component text, float x, float y, int color) {
		int argb = (color >>> 24) == 0 ? color | 0xFF000000 : color;
		UiFontRenderer.instance().draw(poseStack, text, x, y, UiFont.REGULAR_SIZE, argb,
				UiFontRenderer.Weight.REGULAR);
	}

	@Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
	private void blockera$stringWidth(String text, CallbackInfoReturnable<Integer> callback) {
		if (BlockeraChatStyleScope.active()) callback.setReturnValue(Math.round(UiFontRenderer.instance().width(
				text, UiFont.REGULAR_SIZE, UiFontRenderer.Weight.REGULAR)));
	}

	@Inject(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I", at = @At("HEAD"), cancellable = true)
	private void blockera$sequenceWidth(FormattedCharSequence text, CallbackInfoReturnable<Integer> callback) {
		if (BlockeraChatStyleScope.active()) callback.setReturnValue(Math.round(UiFontRenderer.instance().width(
				text, UiFont.REGULAR_SIZE, UiFontRenderer.Weight.REGULAR)));
	}

	@Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
	private void blockera$formattedWidth(FormattedText text, CallbackInfoReturnable<Integer> callback) {
		if (!BlockeraChatStyleScope.active()) return;
		float width = text instanceof Component component
				? UiFontRenderer.instance().width(component, UiFont.REGULAR_SIZE, UiFontRenderer.Weight.REGULAR)
				: UiFontRenderer.instance().width(text.getString(), UiFont.REGULAR_SIZE, UiFontRenderer.Weight.REGULAR);
		callback.setReturnValue(Math.round(width));
	}

	private static int endX(float x, float width) { return Math.round(x + width); }
}
