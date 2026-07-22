package space.blockera.core.hud;

import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;

/** Local-only mouse click counter; it never reads server data. */
public final class CpsMeter {
	private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
	private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();

	private CpsMeter() {
	}

	public static synchronized void record(int button, int action) {
		if (action != GLFW.GLFW_PRESS) return;
		long now = System.currentTimeMillis();
		if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) LEFT_CLICKS.addLast(now);
		else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) RIGHT_CLICKS.addLast(now);
		trim(now);
	}

	public static synchronized String value() {
		long now = System.currentTimeMillis();
		trim(now);
		return LEFT_CLICKS.size() + " | " + RIGHT_CLICKS.size();
	}

	private static void trim(long now) {
		trim(LEFT_CLICKS, now);
		trim(RIGHT_CLICKS, now);
	}

	private static void trim(Deque<Long> clicks, long now) {
		while (!clicks.isEmpty() && now - clicks.peekFirst() > 1000L) clicks.removeFirst();
	}
}
