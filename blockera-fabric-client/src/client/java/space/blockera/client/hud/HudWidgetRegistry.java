package space.blockera.client.hud;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HudWidgetRegistry {
    private final Map<String, ClientHudWidget> widgets = new LinkedHashMap<>();

    public void register(ClientHudWidget widget) {
        if (!widget.id().startsWith("blockera:")) {
            throw new IllegalArgumentException("Only first-party Blockera widgets are allowed");
        }
        if (widgets.putIfAbsent(widget.id(), widget) != null) {
            throw new IllegalArgumentException("Duplicate HUD widget: " + widget.id());
        }
    }

    public List<ClientHudWidget> widgets() {
        return List.copyOf(widgets.values());
    }
}
