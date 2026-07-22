package space.blockera.core.hud;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Two-second cached sampler. Sensor failures never reach the render thread. */
public final class SystemMetricsSampler {
	private static final String EMPTY = "—";
	private static volatile Snapshot snapshot = new Snapshot(EMPTY, EMPTY, EMPTY, EMPTY);
	private static volatile boolean started;

	private SystemMetricsSampler() { }
	public static Snapshot snapshot() { start(); return snapshot; }

	public static synchronized void start() {
		if (started) return;
		started = true;
		ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
			Thread thread = new Thread(runnable, "blockera-system-metrics"); thread.setDaemon(true); return thread;
		});
		executor.scheduleWithFixedDelay(SystemMetricsSampler::sample, 0, 2, TimeUnit.SECONDS);
	}

	private static void sample() {
		String cpu = EMPTY;
		String memory = EMPTY;
		String battery = EMPTY;
		String temperature = EMPTY;
		try {
			var bean = ManagementFactory.getOperatingSystemMXBean();
			if (bean instanceof com.sun.management.OperatingSystemMXBean os) {
				double load = os.getCpuLoad();
				if (load >= 0.0D) cpu = Math.round(load * 100.0D) + "%";
				long total = os.getTotalMemorySize();
				long used = total - os.getFreeMemorySize();
				memory = used / 1_048_576L + " / " + total / 1_048_576L + " MiB";
			}
		} catch (RuntimeException ignored) { }
		try {
			Class<?> infoType = Class.forName("oshi.SystemInfo");
			Object hardware = infoType.getMethod("getHardware").invoke(infoType.getConstructor().newInstance());
			Object sensors = hardware.getClass().getMethod("getSensors").invoke(hardware);
			double value = ((Number) sensors.getClass().getMethod("getCpuTemperature").invoke(sensors)).doubleValue();
			if (Double.isFinite(value) && value > 0.0D) temperature = Math.round(value) + " °C";
			Object sources = hardware.getClass().getMethod("getPowerSources").invoke(hardware);
			int count = sources instanceof List<?> list ? list.size() : sources.getClass().isArray() ? Array.getLength(sources) : 0;
			if (count > 0) {
				Object source = sources instanceof List<?> list ? list.get(0) : Array.get(sources, 0);
				Method capacity = source.getClass().getMethod("getRemainingCapacityPercent");
				double percent = ((Number) capacity.invoke(source)).doubleValue();
				if (Double.isFinite(percent) && percent >= 0.0D) battery = Math.round(percent * 100.0D) + "%";
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) { }
		snapshot = new Snapshot(cpu, memory, battery, temperature);
	}

	public record Snapshot(String cpu, String memory, String battery, String temperature) { }
}
