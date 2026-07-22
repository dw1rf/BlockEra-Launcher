package space.blockera.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Bounded local substring rule. Regex and executable expressions are deliberately unsupported. */
public final class ChatFilterRule {
	public static final int MAX_PHRASES = 16;
	public static final int MAX_PHRASE_LENGTH = 64;

	private String id = "filter";
	private String name = "Filter";
	private List<String> include = new ArrayList<>();
	private List<String> exclude = new ArrayList<>();
	private int color = 0xFF8D68FF;
	private boolean enabled = true;

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

	public void setName(String name) { this.name = Objects.requireNonNull(name, "name"); }
	public void setInclude(List<String> include) {
		this.include = new ArrayList<>(Objects.requireNonNull(include, "include"));
	}
	public void setExclude(List<String> exclude) {
		this.exclude = new ArrayList<>(Objects.requireNonNull(exclude, "exclude"));
	}
	public void setColor(int color) { this.color = 0xFF000000 | color; }
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

	/** Include is an OR-list. Any matching exclude phrase vetoes the rule. */
	public boolean matches(String message) {
		if (!enabled || message == null || include == null || exclude == null || include.isEmpty()) {
			return false;
		}
		String searchable = message.toLowerCase(Locale.ROOT);
		for (String phrase : exclude) {
			if (phrase != null && searchable.contains(phrase.toLowerCase(Locale.ROOT))) {
				return false;
			}
		}
		for (String phrase : include) {
			if (phrase != null && searchable.contains(phrase.toLowerCase(Locale.ROOT))) {
				return true;
			}
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
			throw new IllegalArgumentException("Too many chat " + kind + " phrases");
		}
		for (String phrase : phrases) {
			if (phrase == null || phrase.isBlank() || phrase.length() > MAX_PHRASE_LENGTH) {
				throw new IllegalArgumentException("Chat filter phrases must contain 1-"
					+ MAX_PHRASE_LENGTH + " characters");
			}
		}
	}
}
