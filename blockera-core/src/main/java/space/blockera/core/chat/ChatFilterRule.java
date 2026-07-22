package space.blockera.core.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** A safe substring-only rule used to route and highlight chat messages. */
public final class ChatFilterRule {
	public static final int MAX_PHRASES = 16;
	public static final int MAX_PHRASE_LENGTH = 64;

	private String id = "filter";
	private String name = "Filter";
	private List<String> include = new ArrayList<>();
	private List<String> exclude = new ArrayList<>();
	private int color = 0xFF9B7BFF;
	private boolean enabled = true;
	private boolean mention;
	private boolean sound;

	public ChatFilterRule() {
	}

	public ChatFilterRule(String id, String name, List<String> include, List<String> exclude) {
		this.id = Objects.requireNonNull(id, "id");
		this.name = Objects.requireNonNull(name, "name");
		this.include = new ArrayList<>(Objects.requireNonNull(include, "include"));
		this.exclude = new ArrayList<>(Objects.requireNonNull(exclude, "exclude"));
	}

	public String id() { return id; }
	public String name() { return name; }
	public List<String> include() { return Collections.unmodifiableList(include); }
	public List<String> exclude() { return Collections.unmodifiableList(exclude); }
	public int color() { return color; }
	public boolean enabled() { return enabled; }
	public boolean mention() { return mention; }
	public boolean sound() { return sound; }

	public void setId(String id) { this.id = Objects.requireNonNull(id, "id"); }
	public void setName(String name) { this.name = Objects.requireNonNull(name, "name"); }
	public void setInclude(List<String> include) {
		this.include = new ArrayList<>(Objects.requireNonNull(include, "include"));
	}
	public void setExclude(List<String> exclude) {
		this.exclude = new ArrayList<>(Objects.requireNonNull(exclude, "exclude"));
	}
	public void setColor(int color) { this.color = color; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }
	public void setMention(boolean mention) { this.mention = mention; }
	public void setSound(boolean sound) { this.sound = sound; }

	/** Include is an OR-list, while any exclude phrase vetoes the match. */
	public boolean matches(String message) {
		if (!enabled || message == null || include == null || exclude == null || include.isEmpty()) return false;
		String searchable = message.toLowerCase(Locale.ROOT);
		for (String phrase : exclude) {
			if (phrase != null && searchable.contains(phrase.toLowerCase(Locale.ROOT))) return false;
		}
		for (String phrase : include) {
			if (phrase != null && searchable.contains(phrase.toLowerCase(Locale.ROOT))) return true;
		}
		return false;
	}

	void validate() {
		ChatValidation.requireIdentifier(id, "filter");
		ChatValidation.requireName(name, "filter");
		validatePhrases(include, "include");
		validatePhrases(exclude, "exclude");
	}

	private static void validatePhrases(List<String> phrases, String kind) {
		if (phrases == null || phrases.size() > MAX_PHRASES) {
			throw new IllegalArgumentException("A chat filter may contain at most " + MAX_PHRASES + " " + kind + " phrases");
		}
		for (String phrase : phrases) {
			if (phrase == null || phrase.isBlank() || phrase.length() > MAX_PHRASE_LENGTH) {
				throw new IllegalArgumentException("Chat filter phrases must contain 1-" + MAX_PHRASE_LENGTH + " characters");
			}
		}
	}
}
