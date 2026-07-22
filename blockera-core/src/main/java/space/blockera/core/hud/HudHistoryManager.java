package space.blockera.core.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded JSON-snapshot history; changes are recorded per interaction, never per render frame. */
public final class HudHistoryManager {
	private static final int LIMIT = 50;
	private final Deque<String> undo = new ArrayDeque<>();
	private final Deque<String> redo = new ArrayDeque<>();

	public void record(String before) {
		if (!undo.isEmpty() && undo.peek().equals(before)) return;
		undo.push(before);
		while (undo.size() > LIMIT) undo.removeLast();
		redo.clear();
	}

	public String undo(String current) {
		if (undo.isEmpty()) return null;
		redo.push(current);
		return undo.pop();
	}

	public String redo(String current) {
		if (redo.isEmpty()) return null;
		undo.push(current);
		return redo.pop();
	}

	public boolean canUndo() { return !undo.isEmpty(); }
	public boolean canRedo() { return !redo.isEmpty(); }
}
