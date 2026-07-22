package space.blockera.client.hud;

import java.lang.management.ManagementFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Samples host metrics away from the render loop and exposes safe fallbacks. */
final class SystemMetricsSampler {
	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
		Thread thread = new Thread(task, "Blockera system metrics");
		thread.setDaemon(true);
		return thread;
	});
	private volatile String cpu = "—";
	private volatile String memory = "—";

	SystemMetricsSampler() {
		executor.scheduleAtFixedRate(this::sample, 0L, 2L, TimeUnit.SECONDS);
	}

	String cpu() {
		return cpu;
	}

	String memory() {
		return memory;
	}

	private void sample() {
		try {
			if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
				double load = os.getCpuLoad();
				cpu = load < 0.0D ? "—" : Math.round(load * 100.0D) + "%";
				long total = os.getTotalMemorySize();
				long used = total - os.getFreeMemorySize();
				memory = used / 1_048_576L + " / " + total / 1_048_576L + " MB";
			}
		} catch (RuntimeException ignored) {
			cpu = "—";
			memory = "—";
		}
	}
}
