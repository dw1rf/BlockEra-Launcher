package space.blockera.core.tools;

import java.util.LinkedHashMap;
import java.util.Map;

/** Local-only tool state. No credentials or remote addon data are accepted. */
public final class LocalToolConfig {
	public static final int SCHEMA_VERSION = 1;
	private int schemaVersion = SCHEMA_VERSION;
	private String autoTextTemplate = "";
	private Map<String, Marker> markers = new LinkedHashMap<>();
	public String autoTextTemplate() { return autoTextTemplate; }
	public void setAutoTextTemplate(String value) { autoTextTemplate = value == null ? "" : value.trim(); }
	public Map<String, Marker> markers() { return markers; }
	public void putMarker(String scope, Marker marker) { markers.put(scope, marker); }
	public Marker marker(String scope) { return markers.get(scope); }
	public void validate() {
		if (schemaVersion != SCHEMA_VERSION) throw new IllegalArgumentException("Unsupported tools schema");
		if (autoTextTemplate == null || autoTextTemplate.length() > 256) autoTextTemplate = "";
		if (markers == null) markers = new LinkedHashMap<>();
		markers.entrySet().removeIf(entry -> entry.getKey() == null || entry.getKey().length() > 256 || entry.getValue() == null);
	}
	public static final class Marker {
		private String dimension;
		private int x;
		private int y;
		private int z;
		public Marker() { }
		public Marker(String dimension, int x, int y, int z) { this.dimension = dimension; this.x = x; this.y = y; this.z = z; }
		public String dimension() { return dimension; }
		public int x() { return x; }
		public int y() { return y; }
		public int z() { return z; }
	}
}
