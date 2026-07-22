package space.blockera.core.ui;

import space.blockera.core.ui.render.ScissorStack;

/** Backward-compatible facade for the nested Blockera clipping stack. */
public final class UiScissor {
	private UiScissor() {
	}

	public static void enable(int left, int top, int right, int bottom) { ScissorStack.push(left, top, right, bottom); }
	public static void disable() { ScissorStack.pop(); }
}
