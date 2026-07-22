package space.blockera.core.config;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/** Public endpoints only. Authentication secrets are supplied at runtime and are never persisted here. */
public record RuntimeConfig(Optional<URI> apiBaseUrl, Optional<URI> websocketUrl) {
	public static final String API_URL_PROPERTY = "blockera.apiBaseUrl";
	public static final String WEBSOCKET_URL_PROPERTY = "blockera.websocketUrl";

	public RuntimeConfig {
		apiBaseUrl = Objects.requireNonNull(apiBaseUrl, "apiBaseUrl");
		websocketUrl = Objects.requireNonNull(websocketUrl, "websocketUrl");
		apiBaseUrl.ifPresent(RuntimeConfig::requireHttps);
		websocketUrl.ifPresent(RuntimeConfig::requireSecureWebSocket);
	}

	public static RuntimeConfig fromSystemProperties() {
		return new RuntimeConfig(readUri(API_URL_PROPERTY), readUri(WEBSOCKET_URL_PROPERTY));
	}

	private static Optional<URI> readUri(String property) {
		String value = System.getProperty(property);
		return value == null || value.isBlank() ? Optional.empty() : Optional.of(URI.create(value));
	}

	private static void requireHttps(URI uri) {
		if (!"https".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalArgumentException("Blockera API must use HTTPS");
		}
	}

	private static void requireSecureWebSocket(URI uri) {
		if (!"wss".equalsIgnoreCase(uri.getScheme())) {
			throw new IllegalArgumentException("Blockera WebSocket must use WSS");
		}
	}
}
