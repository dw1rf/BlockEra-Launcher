package space.blockera.core.chat;

final class ChatValidation {
	private static final int MAX_IDENTIFIER_LENGTH = 32;
	private static final int MAX_NAME_LENGTH = 64;

	private ChatValidation() {
	}

	static void requireIdentifier(String value, String kind) {
		if (!isIdentifier(value)) {
			throw new IllegalArgumentException("Invalid chat " + kind + " identifier");
		}
	}

	private static boolean isIdentifier(String value) {
		if (value == null || value.isEmpty() || value.length() > MAX_IDENTIFIER_LENGTH) return false;
		for (int index = 0; index < value.length(); index++) {
			char character = value.charAt(index);
			boolean alphaNumeric = character >= 'a' && character <= 'z' || character >= '0' && character <= '9';
			if (!alphaNumeric && (index == 0 || character != '_' && character != '-')) return false;
		}
		return true;
	}

	static void requireName(String value, String kind) {
		if (value == null || value.isBlank() || value.length() > MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("Chat " + kind + " names must contain 1-" + MAX_NAME_LENGTH + " characters");
		}
	}
}
