package space.blockera.core.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import com.mojang.math.Matrix4f;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import space.blockera.core.BlockeraCore;
import space.blockera.core.ui.BlockeraChatStyleScope;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Shared STB TrueType renderer. It never delegates normal drawing or metrics to Minecraft Font. */
public final class UiFontRenderer implements AutoCloseable {
	public enum Weight { REGULAR, SEMIBOLD }
	private static final UiFontRenderer INSTANCE = new UiFontRenderer();
	private static final Logger LOGGER = LogUtils.getLogger();
	private GlyphAtlas regular;
	private GlyphAtlas semibold;
	private boolean initializationAttempted;
	private record TextSegment(String text, int argb, Weight weight) {}

	private UiFontRenderer() {
	}

	public static UiFontRenderer instance() { return INSTANCE; }

	public void draw(PoseStack poseStack, Component component, float x, float y, float size, int argb, Weight weight) {
		draw(poseStack, component.getVisualOrderText(), x, y, size, argb, weight);
	}

	public void draw(PoseStack poseStack, FormattedCharSequence sequence, float x, float y, float size,
			int argb, Weight weight) {
		float cursor = x;
		for (TextSegment segment : segments(sequence, argb, weight)) {
			draw(poseStack, segment.text(), cursor, y, size, segment.argb(), segment.weight());
			cursor += width(segment.text(), size, segment.weight());
		}
	}

	public void draw(PoseStack poseStack, String text, float x, float y, float size, int argb, Weight weight) {
		GlyphAtlas atlas = atlas(weight);
		if (atlas == null || text.isEmpty()) return;
		float cursor = x;
		StringBuilder run = new StringBuilder();
		Boolean atlasRun = null;
		for (int offset = 0; offset < text.length();) {
			int codePoint = text.codePointAt(offset);
			offset += Character.charCount(codePoint);
			boolean supported = atlas.hasGlyph(codePoint);
			if (atlasRun != null && atlasRun != supported) {
				String value = run.toString();
				cursor += atlasRun ? drawAtlas(poseStack, value, cursor, y, size, argb, atlas)
						: drawVanilla(poseStack, value, cursor, y, size, argb);
				run.setLength(0);
			}
			atlasRun = supported;
			run.appendCodePoint(codePoint);
		}
		if (!run.isEmpty()) {
			String value = run.toString();
			if (Boolean.TRUE.equals(atlasRun)) drawAtlas(poseStack, value, cursor, y, size, argb, atlas);
			else drawVanilla(poseStack, value, cursor, y, size, argb);
		}
	}

	private float drawAtlas(PoseStack poseStack, String text, float x, float y, float size, int argb,
			GlyphAtlas atlas) {
		float factor = size / atlas.sourcePixelHeight();
		float baseline = y + atlas.ascent() * factor;
		float cursor = x;
		int previous = -1;
		Matrix4f matrix = poseStack.last().pose();
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
		int alpha = argb >>> 24;
		int red = argb >> 16 & 0xFF;
		int green = argb >> 8 & 0xFF;
		int blue = argb & 0xFF;
		for (int offset = 0; offset < text.length();) {
			int codePoint = text.codePointAt(offset);
			offset += Character.charCount(codePoint);
			cursor += atlas.kerning(previous, codePoint) * factor;
			GlyphAtlas.Glyph glyph = atlas.glyph(codePoint);
			if (glyph != null && glyph.width() > 0 && glyph.height() > 0) {
				float left = cursor + glyph.xOffset() * factor;
				float top = baseline + glyph.yOffset() * factor;
				float right = left + glyph.width() * factor;
				float bottom = top + glyph.height() * factor;
				vertex(buffer, matrix, left, bottom, glyph.u0(), glyph.v1(), red, green, blue, alpha);
				vertex(buffer, matrix, right, bottom, glyph.u1(), glyph.v1(), red, green, blue, alpha);
				vertex(buffer, matrix, right, top, glyph.u1(), glyph.v0(), red, green, blue, alpha);
				vertex(buffer, matrix, left, top, glyph.u0(), glyph.v0(), red, green, blue, alpha);
			}
			cursor += glyph == null ? 0.0F : glyph.advance() * factor;
			previous = codePoint;
		}
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
		RenderSystem.setShaderTexture(0, atlas.textureId());
		BufferUploader.drawWithShader(buffer.end());
		return cursor - x;
	}

	private static float drawVanilla(PoseStack poseStack, String text, float x, float y, float size, int argb) {
		var font = net.minecraft.client.Minecraft.getInstance().font;
		float scale = size / 9.5F;
		poseStack.pushPose();
		poseStack.translate(x, y, 0.0D);
		poseStack.scale(scale, scale, 1.0F);
		BlockeraChatStyleScope.withVanillaFallback(() -> font.draw(poseStack, text, 0.0F, 0.0F, argb));
		poseStack.popPose();
		return BlockeraChatStyleScope.withVanillaFallback(() -> font.width(text)) * scale;
	}

	public float width(Component component, float size, Weight weight) {
		return width(component.getVisualOrderText(), size, weight);
	}

	public float width(FormattedCharSequence sequence, float size, Weight weight) {
		float width = 0.0F;
		for (TextSegment segment : segments(sequence, 0xFFFFFFFF, weight)) {
			width += width(segment.text(), size, segment.weight());
		}
		return width;
	}

	public float width(String text, float size, Weight weight) {
		GlyphAtlas atlas = atlas(weight);
		if (atlas == null) return 0.0F;
		float factor = size / atlas.sourcePixelHeight();
		float vanillaScale = size / 9.5F;
		float width = 0.0F;
		int previous = -1;
		for (int offset = 0; offset < text.length();) {
			int codePoint = text.codePointAt(offset);
			offset += Character.charCount(codePoint);
			GlyphAtlas.Glyph glyph = atlas.glyph(codePoint);
			if (glyph == null) {
				String fallback = new String(Character.toChars(codePoint));
				int fallbackWidth = BlockeraChatStyleScope.withVanillaFallback(
						() -> net.minecraft.client.Minecraft.getInstance().font.width(fallback));
				width += fallbackWidth * vanillaScale;
				previous = -1;
			} else {
				width += atlas.kerning(previous, codePoint) * factor;
				width += glyph.advance() * factor;
				previous = codePoint;
			}
		}
		return width;
	}

	public float lineHeight(float size, Weight weight) {
		GlyphAtlas atlas = atlas(weight);
		return atlas == null ? size : atlas.lineHeight() * size / atlas.sourcePixelHeight();
	}

	public String ellipsize(String text, float maximumWidth, float size, Weight weight) {
		if (width(text, size, weight) <= maximumWidth) return text;
		String suffix = "\u2026";
		int[] points = text.codePoints().toArray();
		int low = 0;
		int high = points.length;
		while (low < high) {
			int middle = (low + high + 1) / 2;
			String candidate = new String(points, 0, middle) + suffix;
			if (width(candidate, size, weight) <= maximumWidth) low = middle; else high = middle - 1;
		}
		return new String(points, 0, low) + suffix;
	}

	public List<String> wrap(String text, float maximumWidth, float size, Weight weight) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split("\\s+")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && width(candidate, size, weight) > maximumWidth) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (!line.isEmpty()) line.append(' ');
			line.append(word);
		}
		if (!line.isEmpty()) lines.add(line.toString());
		return lines;
	}

	private static List<TextSegment> segments(FormattedCharSequence sequence, int fallbackArgb, Weight fallbackWeight) {
		List<TextSegment> result = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int[] currentColor = {fallbackArgb};
		Weight[] currentWeight = {fallbackWeight};
		boolean[] initialized = {false};
		sequence.accept((index, style, codePoint) -> {
			int color = styleColor(style, fallbackArgb);
			Weight weight = style.isBold() ? Weight.SEMIBOLD : fallbackWeight;
			if (!initialized[0]) {
				currentColor[0] = color;
				currentWeight[0] = weight;
				initialized[0] = true;
			}
			if (color != currentColor[0] || weight != currentWeight[0]) {
				if (!current.isEmpty()) result.add(new TextSegment(current.toString(), currentColor[0], currentWeight[0]));
				current.setLength(0);
				currentColor[0] = color;
				currentWeight[0] = weight;
			}
			current.appendCodePoint(codePoint);
			return true;
		});
		if (!current.isEmpty()) result.add(new TextSegment(current.toString(), currentColor[0], currentWeight[0]));
		return result;
	}

	private static int styleColor(Style style, int fallbackArgb) {
		if (style.getColor() == null) return fallbackArgb;
		return (fallbackArgb & 0xFF000000) | (style.getColor().getValue() & 0xFFFFFF);
	}

	private GlyphAtlas atlas(Weight weight) {
		ensureInitialized();
		return weight == Weight.SEMIBOLD ? semibold : regular;
	}

	private void ensureInitialized() {
		if (initializationAttempted) return;
		initializationAttempted = true;
		try {
			regular = new GlyphAtlas(resource("font/inter_regular.ttf"));
			semibold = new GlyphAtlas(resource("font/inter_semibold.ttf"));
		} catch (IOException | RuntimeException exception) {
			LOGGER.error("Blockera TrueType renderer initialization failed", exception);
			closeQuietly();
		}
	}

	@SuppressWarnings("removal")
	private static ResourceLocation resource(String path) { return new ResourceLocation(BlockeraCore.MOD_ID, path); }

	private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float u, float v,
			int red, int green, int blue, int alpha) {
		buffer.vertex(matrix, x, y, 0.0F).color(red, green, blue, alpha).uv(u, v).endVertex();
	}

	private void closeQuietly() {
		if (regular != null) regular.close();
		if (semibold != null) semibold.close();
		regular = null;
		semibold = null;
	}

	@Override
	public void close() { closeQuietly(); }
}
