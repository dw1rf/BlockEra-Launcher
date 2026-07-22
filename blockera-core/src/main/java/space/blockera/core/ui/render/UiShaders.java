package space.blockera.core.ui.render;

import net.minecraft.client.renderer.ShaderInstance;

public final class UiShaders {
	private static ShaderInstance roundedRect;

	private UiShaders() {
	}

	public static ShaderInstance roundedRect() { return roundedRect; }
	public static void setRoundedRect(ShaderInstance shader) { roundedRect = shader; }
}
