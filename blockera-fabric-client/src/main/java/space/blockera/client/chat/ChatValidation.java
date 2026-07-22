package space.blockera.client.chat;

import java.util.regex.Pattern;

final class ChatValidation {
	private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9_-]{1,32}");

	private ChatValidation() {
	}

	static void requireIdentifier(String value, String kind) {
		if (value == null || !IDENTIFIER.matcher(value).matches()) {
			throw new IllegalArgumentException("Invalid chat " + kind + " identifier");
		}
	}

	static void requireName(String value, String kind) {
		if (value == null || value.isBlank() || value.length() > 48) {
			throw new IllegalArgumentException("Chat " + kind + " name must contain 1-48 characters");
		}
	}
}
