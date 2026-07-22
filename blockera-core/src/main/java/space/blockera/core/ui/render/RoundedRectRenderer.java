package space.blockera.core.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.shaders.Uniform;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.math.Matrix4f;

/** GPU SDF rounded rectangle with fill, one-pixel border and framebuffer-aware smoothing. */
public final class RoundedRectRenderer {
	private RoundedRectRenderer() {
	}

	public static void draw(PoseStack poseStack, float left, float top, float right, float bottom,
			float radius, int fillArgb, float borderWidth, int borderArgb) {
		draw(poseStack, left, top, right, bottom, radius, fillArgb, borderWidth, borderArgb,
				UiScale.current().oneFramebufferPixel());
	}

	public static void draw(PoseStack poseStack, float left, float top, float right, float bottom,
			float radius, int fillArgb, float borderWidth, int borderArgb, float softness) {
		ShaderInstance shader = UiShaders.roundedRect();
		if (shader == null || right <= left || bottom <= top) return;
		set(shader, "Size", right - left, bottom - top);
		set(shader, "Radius", Math.min(radius, Math.min(right - left, bottom - top) * 0.5F));
		set(shader, "BorderWidth", Math.max(0.0F, borderWidth));
		set(shader, "Softness", Math.max(0.01F, softness));
		setColor(shader, "FillColor", fillArgb);
		setColor(shader, "BorderColor", borderArgb);
		RenderSystem.enableBlend();
		RenderSystem.defaultBlendFunc();
		RenderSystem.setShader(() -> shader);
		Matrix4f matrix = poseStack.last().pose();
		BufferBuilder buffer = Tesselator.getInstance().getBuilder();
		buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
		vertex(buffer, matrix, left, bottom, 0.0F, 1.0F);
		vertex(buffer, matrix, right, bottom, 1.0F, 1.0F);
		vertex(buffer, matrix, right, top, 1.0F, 0.0F);
		vertex(buffer, matrix, left, top, 0.0F, 0.0F);
		BufferUploader.drawWithShader(buffer.end());
	}

	private static void set(ShaderInstance shader, String name, float value) {
		Uniform uniform = shader.getUniform(name);
		if (uniform != null) uniform.set(value);
	}

	private static void set(ShaderInstance shader, String name, float x, float y) {
		Uniform uniform = shader.getUniform(name);
		if (uniform != null) uniform.set(x, y);
	}

	private static void setColor(ShaderInstance shader, String name, int argb) {
		Uniform uniform = shader.getUniform(name);
		if (uniform != null) uniform.set((argb >> 16 & 0xFF) / 255.0F, (argb >> 8 & 0xFF) / 255.0F,
				(argb & 0xFF) / 255.0F, (argb >>> 24) / 255.0F);
	}

	private static void vertex(BufferBuilder buffer, Matrix4f matrix, float x, float y, float u, float v) {
		buffer.vertex(matrix, x, y, 0.0F).uv(u, v).endVertex();
	}
}
