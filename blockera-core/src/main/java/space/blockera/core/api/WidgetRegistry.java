package space.blockera.core.api;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry intentionally limited to the Blockera namespace. There is no reflective
 * discovery or third-party addon loading in the MVP.
 */
public final class WidgetRegistry {
	private static final String FIRST_PARTY_PREFIX = "blockera:";

	private final Map<String, Widget> widgets = new LinkedHashMap<>();

	private WidgetRegistry() {}

	public static WidgetRegistry firstPartyOnly() {
		return new WidgetRegistry();
	}

	public synchronized void register(Widget widget) {
		Objects.requireNonNull(widget, "widget");
		if (!widget.id().startsWith(FIRST_PARTY_PREFIX)) {
			throw new IllegalArgumentException("Only first-party Blockera widgets are allowed");
		}
		if (widgets.putIfAbsent(widget.id(), widget) != null) {
			throw new IllegalArgumentException("Widget is already registered: " + widget.id());
		}
	}

	public synchronized Collection<Widget> all() {
		return Collections.unmodifiableList(widgets.values().stream().toList());
	}

	public synchronized Widget get(String id) {
		return widgets.get(id);
	}
}
