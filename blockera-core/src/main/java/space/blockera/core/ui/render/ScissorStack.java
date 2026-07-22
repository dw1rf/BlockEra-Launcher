package space.blockera.core.ui.render;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.util.ArrayDeque;
import java.util.Deque;

/** Nested framebuffer-aware clipping stack. */
public final class ScissorStack {
	private record Bounds(int left, int top, int right, int bottom) {
		Bounds intersect(Bounds other) {
			return new Bounds(Math.max(left, other.left), Math.max(top, other.top),
					Math.min(right, other.right), Math.min(bottom, other.bottom));
		}
	}

	private static final Deque<Bounds> STACK = new ArrayDeque<>();

	private ScissorStack() {
	}

	public static void push(int left, int top, int right, int bottom) {
		Bounds next = new Bounds(left, top, right, bottom);
		if (!STACK.isEmpty()) next = STACK.peek().intersect(next);
		STACK.push(next);
		apply(next);
	}

	public static void pop() {
		if (STACK.isEmpty()) return;
		STACK.pop();
		if (STACK.isEmpty()) RenderSystem.disableScissor(); else apply(STACK.peek());
	}

	public static void clear() {
		STACK.clear();
		RenderSystem.disableScissor();
	}

	private static void apply(Bounds bounds) {
		var window = Minecraft.getInstance().getWindow();
		double scale = window.getGuiScale();
		int x = (int) Math.floor(bounds.left * scale);
		int y = (int) Math.floor(window.getHeight() - bounds.bottom * scale);
		int width = Math.max(0, (int) Math.ceil((bounds.right - bounds.left) * scale));
		int height = Math.max(0, (int) Math.ceil((bounds.bottom - bounds.top) * scale));
		RenderSystem.enableScissor(x, y, width, height);
	}
}
