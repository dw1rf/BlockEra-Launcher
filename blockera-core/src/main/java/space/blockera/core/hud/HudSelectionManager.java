package space.blockera.core.hud;

import java.util.LinkedHashSet;
import java.util.Set;

/** Stable multi-selection state used only by HUD Editor. */
public final class HudSelectionManager {
	private final LinkedHashSet<String> selected = new LinkedHashSet<>();

	public void selectOnly(String id) { selected.clear(); selected.add(id); }
	public void toggle(String id) { if (!selected.remove(id)) selected.add(id); }
	public void clear() { selected.clear(); }
	public boolean contains(String id) { return selected.contains(id); }
	public boolean isEmpty() { return selected.isEmpty(); }
	public int size() { return selected.size(); }
	public String primary() { return selected.stream().findFirst().orElse(null); }
	public Set<String> all() { return Set.copyOf(selected); }
}
