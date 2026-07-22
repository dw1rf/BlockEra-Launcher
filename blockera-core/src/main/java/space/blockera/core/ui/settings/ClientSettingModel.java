package space.blockera.core.ui.settings;

import space.blockera.core.ui.BlockeraIcon;
import space.blockera.core.ui.BlockeraMenuScreen;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Data model for a card; the screen never hard-codes individual module coordinates. */
public final class ClientSettingModel {
	private final String id;
	private final String titleKey;
	private final String descriptionKey;
	private final ClientSettingCategory category;
	private final BlockeraIcon icon;
	private final ClientSettingType type;
	private final BooleanSupplier enabled;
	private final Consumer<Boolean> toggle;
	private final Consumer<BlockeraMenuScreen> action;
	private final Consumer<BlockeraMenuScreen> settingsAction;

	private ClientSettingModel(String id, String titleKey, String descriptionKey,
			ClientSettingCategory category, BlockeraIcon icon, ClientSettingType type,
			BooleanSupplier enabled, Consumer<Boolean> toggle, Consumer<BlockeraMenuScreen> action,
			Consumer<BlockeraMenuScreen> settingsAction) {
		this.id = Objects.requireNonNull(id, "id");
		this.titleKey = Objects.requireNonNull(titleKey, "titleKey");
		this.descriptionKey = descriptionKey;
		this.category = Objects.requireNonNull(category, "category");
		this.icon = Objects.requireNonNull(icon, "icon");
		this.type = Objects.requireNonNull(type, "type");
		this.enabled = enabled;
		this.toggle = toggle;
		this.action = action;
		this.settingsAction = settingsAction;
	}

	public static ClientSettingModel toggle(String id, String titleKey, String descriptionKey,
			ClientSettingCategory category, BlockeraIcon icon, BooleanSupplier enabled, Consumer<Boolean> toggle) {
		return new ClientSettingModel(id, titleKey, descriptionKey, category, icon, ClientSettingType.TOGGLE,
				Objects.requireNonNull(enabled, "enabled"), Objects.requireNonNull(toggle, "toggle"), null, null);
	}

	public static ClientSettingModel module(String id, String titleKey, String descriptionKey,
			ClientSettingCategory category, BlockeraIcon icon, BooleanSupplier enabled, Consumer<Boolean> toggle,
			Consumer<BlockeraMenuScreen> settingsAction) {
		return new ClientSettingModel(id, titleKey, descriptionKey, category, icon, ClientSettingType.TOGGLE,
				Objects.requireNonNull(enabled, "enabled"), Objects.requireNonNull(toggle, "toggle"), null,
				Objects.requireNonNull(settingsAction, "settingsAction"));
	}

	public static ClientSettingModel action(String id, String titleKey, String descriptionKey,
			ClientSettingCategory category, BlockeraIcon icon, Consumer<BlockeraMenuScreen> action) {
		return new ClientSettingModel(id, titleKey, descriptionKey, category, icon, ClientSettingType.ACTION,
				() -> false, null, Objects.requireNonNull(action, "action"), null);
	}

	public static ClientSettingModel unavailable(String id, String titleKey, String descriptionKey,
			ClientSettingCategory category, BlockeraIcon icon) {
		return new ClientSettingModel(id, titleKey, descriptionKey, category, icon, ClientSettingType.UNAVAILABLE,
				() -> false, null, null, null);
	}

	public String id() { return id; }
	public String titleKey() { return titleKey; }
	public String descriptionKey() { return descriptionKey; }
	public ClientSettingCategory category() { return category; }
	public BlockeraIcon icon() { return icon; }
	public ClientSettingType type() { return type; }
	public boolean enabled() { return type == ClientSettingType.TOGGLE && enabled.getAsBoolean(); }
	public boolean available() { return type != ClientSettingType.UNAVAILABLE; }
	public boolean hasSettings() { return settingsAction != null; }
	public void toggleValue() { if (type == ClientSettingType.TOGGLE) toggle.accept(!enabled()); }

	public void activate(BlockeraMenuScreen screen) {
		if (settingsAction != null) settingsAction.accept(screen);
		else if (type == ClientSettingType.TOGGLE) toggleValue();
		else if (type == ClientSettingType.ACTION) action.accept(screen);
	}
}
