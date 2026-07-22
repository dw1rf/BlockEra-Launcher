package space.blockera.core.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBTTFontinfo;
import org.lwjgl.stb.STBTruetype;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/** High-resolution STB TrueType atlas uploaded once with linear filtering. */
public final class GlyphAtlas implements AutoCloseable {
	public record Glyph(float u0, float v0, float u1, float v1, int width, int height,
			int xOffset, int yOffset, float advance) {
	}

	private static final int ATLAS_SIZE = 2048;
	private static final float SOURCE_PIXEL_HEIGHT = 64.0F;
	private static final int PADDING = 3;
	private final ByteBuffer fontData;
	private final STBTTFontinfo fontInfo;
	private final Map<Integer, Glyph> glyphs = new LinkedHashMap<>();
	private final float fontScale;
	private final float ascent;
	private final float descent;
	private final float lineHeight;
	private final int textureId;

	public GlyphAtlas(ResourceLocation fontResource) throws IOException {
		RenderSystem.assertOnRenderThreadOrInit();
		byte[] bytes;
		try (InputStream input = Minecraft.getInstance().getResourceManager().getResourceOrThrow(fontResource).open()) {
			bytes = input.readAllBytes();
		}
		fontData = BufferUtils.createByteBuffer(bytes.length);
		fontData.put(bytes).flip();
		fontInfo = STBTTFontinfo.create();
		if (!STBTruetype.stbtt_InitFont(fontInfo, fontData)) throw new IOException("Invalid TrueType font: " + fontResource);
		fontScale = STBTruetype.stbtt_ScaleForPixelHeight(fontInfo, SOURCE_PIXEL_HEIGHT);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer ascentBuffer = stack.mallocInt(1);
			IntBuffer descentBuffer = stack.mallocInt(1);
			IntBuffer gapBuffer = stack.mallocInt(1);
			STBTruetype.stbtt_GetFontVMetrics(fontInfo, ascentBuffer, descentBuffer, gapBuffer);
			ascent = ascentBuffer.get(0) * fontScale;
			descent = descentBuffer.get(0) * fontScale;
			lineHeight = (ascentBuffer.get(0) - descentBuffer.get(0) + gapBuffer.get(0)) * fontScale;
		}
		ByteBuffer pixels = buildAtlas();
		textureId = GL11.glGenTextures();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, ATLAS_SIZE, ATLAS_SIZE, 0,
				GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
	}

	public Glyph glyph(int codePoint) {
		return glyphs.get(codePoint);
	}

	public boolean hasGlyph(int codePoint) { return glyphs.containsKey(codePoint); }

	public float kerning(int leftCodePoint, int rightCodePoint) {
		if (leftCodePoint < 0 || rightCodePoint < 0) return 0.0F;
		return STBTruetype.stbtt_GetCodepointKernAdvance(fontInfo, leftCodePoint, rightCodePoint) * fontScale;
	}

	public int textureId() { return textureId; }
	public float sourcePixelHeight() { return SOURCE_PIXEL_HEIGHT; }
	public float ascent() { return ascent; }
	public float descent() { return descent; }
	public float lineHeight() { return lineHeight; }

	private ByteBuffer buildAtlas() throws IOException {
		ByteBuffer rgba = BufferUtils.createByteBuffer(ATLAS_SIZE * ATLAS_SIZE * 4);
		int cursorX = PADDING;
		int cursorY = PADDING;
		int rowHeight = 0;
		for (int codePoint : supportedCodePoints()) {
			// Index zero is the font's .notdef box. Do not bake it as if it were the
			// requested character: the renderer must delegate that code point to
			// Minecraft's font providers instead.
			if (STBTruetype.stbtt_FindGlyphIndex(fontInfo, codePoint) == 0) continue;
			try (MemoryStack stack = MemoryStack.stackPush()) {
				IntBuffer width = stack.mallocInt(1);
				IntBuffer height = stack.mallocInt(1);
				IntBuffer xOffset = stack.mallocInt(1);
				IntBuffer yOffset = stack.mallocInt(1);
				IntBuffer advance = stack.mallocInt(1);
				IntBuffer leftBearing = stack.mallocInt(1);
				ByteBuffer bitmap = STBTruetype.stbtt_GetCodepointBitmap(fontInfo, fontScale, fontScale,
						codePoint, width, height, xOffset, yOffset);
				STBTruetype.stbtt_GetCodepointHMetrics(fontInfo, codePoint, advance, leftBearing);
				int glyphWidth = width.get(0);
				int glyphHeight = height.get(0);
				if (cursorX + glyphWidth + PADDING > ATLAS_SIZE) {
					cursorX = PADDING;
					cursorY += rowHeight + PADDING;
					rowHeight = 0;
				}
				if (cursorY + glyphHeight + PADDING > ATLAS_SIZE) {
					if (bitmap != null) STBTruetype.stbtt_FreeBitmap(bitmap);
					throw new IOException("TrueType atlas overflow at U+" + Integer.toHexString(codePoint));
				}
				if (bitmap != null) {
					for (int y = 0; y < glyphHeight; y++) {
						for (int x = 0; x < glyphWidth; x++) {
							int alpha = bitmap.get(y * glyphWidth + x) & 0xFF;
							int target = ((cursorY + y) * ATLAS_SIZE + cursorX + x) * 4;
							rgba.put(target, (byte) 0xFF);
							rgba.put(target + 1, (byte) 0xFF);
							rgba.put(target + 2, (byte) 0xFF);
							rgba.put(target + 3, (byte) alpha);
						}
					}
					STBTruetype.stbtt_FreeBitmap(bitmap);
				}
				glyphs.put(codePoint, new Glyph(
						cursorX / (float) ATLAS_SIZE, cursorY / (float) ATLAS_SIZE,
						(cursorX + glyphWidth) / (float) ATLAS_SIZE,
						(cursorY + glyphHeight) / (float) ATLAS_SIZE,
						glyphWidth, glyphHeight, xOffset.get(0), yOffset.get(0), advance.get(0) * fontScale));
				cursorX += glyphWidth + PADDING;
				rowHeight = Math.max(rowHeight, glyphHeight);
			}
		}
		rgba.position(0);
		return rgba;
	}

	private static int[] supportedCodePoints() {
		int count = (0x00FF - 0x0020 + 1) + (0x052F - 0x0400 + 1) + 16;
		int[] result = new int[count];
		int index = 0;
		for (int cp = 0x0020; cp <= 0x00FF; cp++) result[index++] = cp;
		for (int cp = 0x0400; cp <= 0x052F; cp++) result[index++] = cp;
		int[] extras = {0x2013, 0x2014, 0x2018, 0x2019, 0x201C, 0x201D, 0x2022, 0x2026,
				0x20AC, 0x20BD, 0x2190, 0x2191, 0x2192, 0x2193, 0x2212, 0x25B6};
		for (int cp : extras) result[index++] = cp;
		return result;
	}

	@Override
	public void close() {
		RenderSystem.assertOnRenderThread();
		GL11.glDeleteTextures(textureId);
		fontInfo.free();
	}
}
