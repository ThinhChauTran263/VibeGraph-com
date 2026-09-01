package com.vibegraph.infrastructure.service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.sun.management.OperatingSystemMXBean;
import com.vibegraph.auth.repository.ProjectUsageRepository;
import com.vibegraph.infrastructure.config.InfrastructureMonitorProperties;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot;
import com.vibegraph.graph.config.GraphPayloadProperties;
import com.vibegraph.mcp.config.McpLimitProperties;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.CapacityBoundary;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.CapacityMetrics;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.ContainerMetrics;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.DiskIoMetrics;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.DiskMetrics;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.HostMetrics;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.MemoryMetrics;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.NetworkMetrics;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.OperationEvidence;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.ResourceBreakdown;
import com.vibegraph.infrastructure.service.collector.CAdvisorContainerCollector;

import lombok.RequiredArgsConstructor;

/** Collects host metrics without Docker socket access or unbounded filesystem scans. */
@Service
@RequiredArgsConstructor
public class InfrastructureMetricsService {

    static final long MAX_IO_BYTES_PER_SECOND = 1L << 40;
    static final long MAX_DROPPED_PACKETS_PER_SAMPLE = 1_000_000_000L;

    private final InfrastructureMonitorProperties properties;
    private final ProjectUsageRepository projectUsageRepository;
    private final GraphPayloadProperties graphPayloadProperties;
    private final McpLimitProperties mcpLimitProperties;
    private final InfrastructureEventStream eventStream;
    private final ObjectProvider<OperationTelemetryRecorder> recorderProvider;
    private final Clock clock;
    private final CAdvisorContainerCollector containerCollector;
    private final AtomicReference<InfrastructureSnapshot> latest = new AtomicReference<>();
    private final Deque<InfrastructureSnapshot> liveSamples = new ArrayDeque<>();
    private final Deque<Double> cpuWindow = new ArrayDeque<>();
    private volatile ProcCounters previousProcCounters;
    private volatile long previousProcSampleNanos;

    @Scheduled(fixedDelayString = "${vibegraph.infrastructure.monitor.sample-interval-ms:1000}")
    public void sample() {
        if (!properties.isEnabled()) {
            return;
        }
        InfrastructureSnapshot snapshot = collect();
        OperationTelemetryRecorder recorder = recorderProvider.getIfAvailable(() -> null);
        if (recorder != null) {
            recorder.observe(snapshot);
        }
        synchronized (liveSamples) {
            liveSamples.addLast(snapshot);
            while (liveSamples.size() > properties.getLiveSampleCapacity()) {
                liveSamples.removeFirst();
            }
        }
        latest.set(snapshot);
        eventStream.publish(snapshot);
    }

    public InfrastructureSnapshot snapshot() {
        InfrastructureSnapshot value = latest.get();
        if (value != null) {
            return value;
        }
        InfrastructureSnapshot initial = collect();
        latest.compareAndSet(null, initial);
        return latest.get();
    }

    public List<InfrastructureSnapshot> liveSamples() {
        synchronized (liveSamples) {
            return List.copyOf(liveSamples);
        }
    }

    private InfrastructureSnapshot collect() {
        OperatingSystemMXBean os = operatingSystem();
        int processors = Runtime.getRuntime().availableProcessors();
        double cpu = percent(os == null ? -1 : os.getCpuLoad());
        CpuWindow cpuStats = cpuStats(cpu);
        List<ContainerMetrics> containers = properties.isCAdvisorEnabled()
                ? containerCollector.collect() : List.of();
        long totalMemory = totalMemory(os);
        long availableMemory = availableMemory(os);
        long usedMemory = Math.max(0, totalMemory - availableMemory);
        MemoryMetrics memory = memory(totalMemory, availableMemory, usedMemory, containers);
        DiskMetrics disk = disk();
        ProcMetrics proc = procMetrics();
        String status = status(cpu, memory.usedPercent(), disk.usedPercent());
        Double currentGHz = currentGHz();
        HostMetrics host = new HostMetrics(cpu, processors, currentGHz,
                cpuStats.average(), cpuStats.peak(), "OperatingSystemMXBean", currentGHz == null ? "MEASURED" : "MEASURED");
        return new InfrastructureSnapshot(
                Instant.now(clock), status, host, memory, disk,
                proc.network(), proc.diskIo(),
                containers, latestOperation(), capacity(), history(), recentIncidents());
    }

    private CpuWindow cpuStats(double cpu) {
        double value = Math.min(100d, Math.max(0d, cpu));
        synchronized (cpuWindow) {
            cpuWindow.addLast(value);
            while (cpuWindow.size() > properties.getLiveSampleCapacity()) cpuWindow.removeFirst();
            return rollingCpuStats(List.copyOf(cpuWindow), properties.getLiveSampleCapacity());
        }
    }

    static CpuWindow rollingCpuStats(List<Double> values, int capacity) {
        List<Double> bounded = values == null ? List.of() : values.stream()
                .filter(value -> value != null && Double.isFinite(value))
                .map(value -> Math.min(100d, Math.max(0d, value)))
                .toList();
        int from = Math.max(0, bounded.size() - Math.max(1, capacity));
        List<Double> window = bounded.subList(from, bounded.size());
        return new CpuWindow(window.stream().mapToDouble(Double::doubleValue).average().orElse(0d),
                window.stream().mapToDouble(Double::doubleValue).max().orElse(0d));
    }

    private Double currentGHz() {
        try {
            Path frequency = Path.of("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq");
            if (Files.isReadable(frequency)) {
                double khz = Double.parseDouble(Files.readString(frequency).trim());
                if (Double.isFinite(khz) && khz > 0) return Math.round(khz / 100_000d) / 10d;
            }
            for (String line : Files.readAllLines(Path.of("/proc/cpuinfo"))) {
                if (line.toLowerCase(Locale.ROOT).startsWith("cpu mhz")) {
                    double mhz = Double.parseDouble(line.substring(line.indexOf(':') + 1).trim());
                    if (Double.isFinite(mhz) && mhz > 0) return Math.round(mhz / 100d) / 10d;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Frequency is optional; host load remains measurable when sysfs is restricted.
        }
        return null;
    }

    record CpuWindow(double average, double peak) {
    }

    /**
     * Reads bounded Linux kernel counters and converts them to per-second deltas. The monitor
     * deliberately does not execute shell commands or require the Docker socket; on hosts where
     * procfs is unavailable (for example Windows development), the fields remain unavailable.
     */
    private ProcMetrics procMetrics() {
        ProcCounters current = readProcCounters();
        long now = System.nanoTime();
        if (current == null) {
            previousProcCounters = null;
            previousProcSampleNanos = 0;
            return ProcMetrics.unavailable();
        }
        ProcCounters previous = previousProcCounters;
        long previousNanos = previousProcSampleNanos;
        previousProcCounters = current;
        previousProcSampleNanos = now;
        if (previous == null || previousNanos <= 0 || now <= previousNanos) {
            return ProcMetrics.warmingUp();
        }
        double elapsedSeconds = Math.max((now - previousNanos) / 1_000_000_000d, 0.001d);
        return rates(previous, current, elapsedSeconds);
    }

    static ProcMetrics rates(ProcCounters previous, ProcCounters current, double elapsedSeconds) {
        long inBytes = delta(current.networkInBytes(), previous.networkInBytes());
        long outBytes = delta(current.networkOutBytes(), previous.networkOutBytes());
        long drops = Math.min(MAX_DROPPED_PACKETS_PER_SAMPLE,
                delta(current.networkDrops(), previous.networkDrops()));
        long readBytes = saturatedMultiply(delta(current.diskReadSectors(), previous.diskReadSectors()), 512L);
        long writeBytes = saturatedMultiply(delta(current.diskWriteSectors(), previous.diskWriteSectors()), 512L);
        long ioMillis = delta(current.diskIoMillis(), previous.diskIoMillis());
        double utilization = Math.min(100d, Math.max(0d, ioMillis / (elapsedSeconds * 1_000d) * 100d));
        return new ProcMetrics(
                new NetworkMetrics(rate(inBytes, elapsedSeconds), rate(outBytes, elapsedSeconds), drops,
                        "/proc/net/dev", "MEASURED"),
                new DiskIoMetrics(rate(readBytes, elapsedSeconds), rate(writeBytes, elapsedSeconds), utilization,
                        "/proc/diskstats", "MEASURED"));
    }

    private ProcCounters readProcCounters() {
        if (!Files.isReadable(Path.of("/proc/net/dev")) || !Files.isReadable(Path.of("/proc/diskstats"))) {
            return null;
        }
        try {
            return parseProcCounters(Files.readAllLines(Path.of("/proc/net/dev")),
                    Files.readAllLines(Path.of("/proc/diskstats")));
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    static ProcCounters parseProcCounters(List<String> networkLines, List<String> diskLines) {
        long networkIn = 0;
        long networkOut = 0;
        long networkDrops = 0;
        for (String line : networkLines) {
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String interfaceName = line.substring(0, colon).trim();
                if (isVirtualInterface(interfaceName)) continue;
                String[] fields = line.substring(colon + 1).trim().split("\\s+");
                if (fields.length < 12) continue;
                networkIn = saturatedAdd(networkIn, nonNegativeLong(fields[0]));
                networkDrops = saturatedAdd(networkDrops, nonNegativeLong(fields[3]));
                networkOut = saturatedAdd(networkOut, nonNegativeLong(fields[8]));
                networkDrops = saturatedAdd(networkDrops, nonNegativeLong(fields[11]));
        }

        long readSectors = 0;
        long writeSectors = 0;
        long ioMillis = 0;
        for (String line : diskLines) {
                String[] fields = line.trim().split("\\s+");
                // major minor device reads ... sectors-read ... writes ... sectors-written ... io-ms
                if (fields.length < 14 || isNonPhysicalDisk(fields[2])) continue;
                readSectors = saturatedAdd(readSectors, nonNegativeLong(fields[5]));
                writeSectors = saturatedAdd(writeSectors, nonNegativeLong(fields[9]));
                ioMillis = saturatedAdd(ioMillis, nonNegativeLong(fields[12]));
        }
        return new ProcCounters(networkIn, networkOut, networkDrops, readSectors, writeSectors, ioMillis);
    }

    private static boolean isPartition(String device) {
        // Avoid double counting partition counters when the whole-disk row is present.
        return device.matches("(?:sd[a-z]+|vd[a-z]+|xvd[a-z]+|mmcblk\\d+)\\d+$")
                || device.matches("mmcblk\\d+p\\d+")
                || device.matches("nvme\\d+n\\d+p\\d+");
    }

    private static boolean isVirtualInterface(String name) {
        return "lo".equals(name) || name.startsWith("docker") || name.startsWith("veth")
                || name.startsWith("br-") || name.startsWith("virbr") || name.startsWith("cni")
                || name.startsWith("flannel");
    }

    private static boolean isNonPhysicalDisk(String device) {
        return isPartition(device) || device.startsWith("loop") || device.startsWith("ram")
                || device.startsWith("dm-") || device.startsWith("md") || device.startsWith("zram")
                || device.startsWith("sr") || device.startsWith("fd");
    }

    private static long nonNegativeLong(String value) {
        try {
            return Math.max(0L, Long.parseLong(value));
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private static long delta(long current, long previous) {
        return current >= previous ? current - previous : 0L;
    }

    private static long rate(long bytes, double seconds) {
        if (!Double.isFinite(seconds) || seconds <= 0) return 0L;
        return Math.min(MAX_IO_BYTES_PER_SECOND, Math.max(0L, Math.round(bytes / seconds)));
    }

    private static long saturatedAdd(long left, long right) {
        if (right > Long.MAX_VALUE - left) return Long.MAX_VALUE;
        return left + right;
    }

    private static long saturatedMultiply(long value, long factor) {
        if (value > Long.MAX_VALUE / factor) return Long.MAX_VALUE;
        return value * factor;
    }

    record ProcCounters(long networkInBytes, long networkOutBytes, long networkDrops,
            long diskReadSectors, long diskWriteSectors, long diskIoMillis) {
    }

    record ProcMetrics(NetworkMetrics network, DiskIoMetrics diskIo) {
        private static ProcMetrics unavailable() {
            return new ProcMetrics(new NetworkMetrics(0, 0, 0, "/proc/net/dev", "UNAVAILABLE"),
                    new DiskIoMetrics(0, 0, null, "/proc/diskstats", "UNAVAILABLE"));
        }

        private static ProcMetrics warmingUp() {
            return new ProcMetrics(new NetworkMetrics(0, 0, 0, "/proc/net/dev", "WARMING_UP"),
                    new DiskIoMetrics(0, 0, null, "/proc/diskstats", "WARMING_UP"));
        }
    }

    private MemoryMetrics memory(long total, long available, long used, List<ContainerMetrics> containers) {
        double percent = ratio(used, total);
        long containerUsed = Math.min(used, containers.stream().mapToLong(ContainerMetrics::memoryUsedBytes).sum());
        List<ResourceBreakdown> rows = new ArrayList<>();
        containers.stream().filter(item -> item.memoryUsedBytes() > 0).forEach(item -> rows.add(
                new ResourceBreakdown("container-" + item.name(), item.name(), item.memoryUsedBytes(),
                        ratio(item.memoryUsedBytes(), total), "cAdvisor", "MEASURED")));
        long backend = Math.min(used - containerUsed, runtimeUsed());
        rows.add(new ResourceBreakdown("backend-jvm", "Backend JVM", backend, ratio(backend, total), "JVM", "MEASURED"));
        long other = Math.max(0, used - containerUsed - backend);
        rows.add(new ResourceBreakdown("host-other", "Host cache / other", other, ratio(other, total),
                containers.isEmpty() ? "DERIVED" : "cAdvisor + derived", containers.isEmpty() ? "ESTIMATED" : "ESTIMATED"));
        return new MemoryMetrics(total, used, available, percent, rows, "/proc/meminfo", "MEASURED");
    }

    private DiskMetrics disk() {
        try {
            Path root = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
            FileStore store = Files.getFileStore(root);
            long total = store.getTotalSpace();
            long free = store.getUsableSpace();
            long used = Math.max(0, total - free);
            long tracked = 0;
            String trackedStatus = "MEASURED";
            try {
                tracked = Math.max(0, projectUsageRepository.sumStorageBytes());
            } catch (RuntimeException ex) {
                // A failed usage query must never look like a measured zero. Keep the
                // filesystem total usable, but mark the project-specific row unavailable.
                trackedStatus = "UNAVAILABLE";
            }
            long other = Math.max(0, used - Math.min(used, tracked));
            List<ResourceBreakdown> rows = List.of(
                    new ResourceBreakdown("tracked-projects", "Tracked project storage", Math.min(used, tracked),
                            ratio(Math.min(used, tracked), total), "ProjectUsage", trackedStatus),
                    new ResourceBreakdown("filesystem-other", "Filesystem / other", other, ratio(other, total),
                            "FileStore", "ESTIMATED"));
            return new DiskMetrics(total, used, free, ratio(used, total), rows, "FileStore", "MEASURED");
        } catch (IOException | SecurityException ex) {
            return new DiskMetrics(0, 0, 0, 0, List.of(), "FileStore", "UNAVAILABLE");
        }
    }

    private OperationEvidence latestOperation() {
        OperationTelemetryRecorder recorder = recorderProvider.getIfAvailable(() -> null);
        return recorder == null ? null : recorder.recent(1, "ALL").stream().findFirst().orElse(null);
    }

    private CapacityMetrics capacity() {
        OperationTelemetryRecorder recorder = recorderProvider.getIfAvailable(() -> null);
        List<OperationEvidence> history = recorder == null ? List.of()
                : recorder.recent(properties.getOperationHistoryInSnapshot(), "ALL");
        Optional<OperationEvidence> analyze = history.stream()
                .filter(item -> "ANALYZE".equalsIgnoreCase(item.type()) && "SUCCESS".equals(item.status()))
                .max(java.util.Comparator.comparingInt(OperationEvidence::nodes));
        CapacityBoundary boundary = analyze.map(item -> new CapacityBoundary(item.nodes(), item.edges(),
                "OBSERVED", item.confidence(), item.id())).orElse(new CapacityBoundary(0, 0, "UNKNOWN", "LOW", null));
        return new CapacityMetrics("LEARNING", history.size(), history.isEmpty() ? "LOW" : "MEDIUM",
                null, new CapacityBoundary(mcpLimitProperties.getMaxNodes(), mcpLimitProperties.getMaxEdges(), "CONFIGURED", "UNKNOWN", null),
                new CapacityBoundary(graphPayloadProperties.getMaxNodeLimit(), graphPayloadProperties.getMaxEdgeLimit(), "CONFIGURED", "UNKNOWN", null), boundary,
                "1 Analyze or 1 full MCP");
    }

    private List<OperationEvidence> history() {
        OperationTelemetryRecorder recorder = recorderProvider.getIfAvailable(() -> null);
        return recorder == null ? List.of()
                : recorder.recent(properties.getOperationHistoryInSnapshot(), "ALL");
    }

    private List<InfrastructureSnapshot.Incident> recentIncidents() {
        OperationTelemetryRecorder recorder = recorderProvider.getIfAvailable(() -> null);
        if (recorder == null) return List.of();
        return recorder.recent(properties.getMaxIncidents(), "ALL").stream()
                .filter(item -> "STOPPED".equalsIgnoreCase(item.status()))
                .map(item -> new InfrastructureSnapshot.Incident(
                        "incident_" + item.id(), item.id(), "OPERATION", "CRITICAL",
                        item.stopReason() == null ? item.status() : item.stopReason(),
                        null, null, item.completedAt(), item.projectName(), item.type(), item.status()))
                .toList();
    }

    private String status(double cpu, double memory, double disk) {
        if (cpu >= properties.getCriticalCpuPercent() || memory >= properties.getCriticalMemoryPercent()
                || disk >= properties.getCriticalDiskPercent()) return "CRITICAL";
        if (cpu >= properties.getWarningCpuPercent() || memory >= properties.getWarningMemoryPercent()
                || disk >= properties.getWarningDiskPercent()) return "WATCH";
        return "HEALTHY";
    }

    private OperatingSystemMXBean operatingSystem() {
        return ManagementFactory.getOperatingSystemMXBean() instanceof OperatingSystemMXBean bean ? bean : null;
    }

    private long totalMemory(OperatingSystemMXBean os) {
        return os == null ? Runtime.getRuntime().maxMemory() : os.getTotalMemorySize();
    }

    private long availableMemory(OperatingSystemMXBean os) {
        try {
            long available = parseMemAvailableBytes(Files.readAllLines(Path.of("/proc/meminfo")));
            if (available > 0) return available;
        } catch (IOException | SecurityException ignored) {
            // Fall back to the JVM-provided physical free value when procfs is restricted.
        }
        return freeMemory(os);
    }

    static long parseMemAvailableBytes(List<String> lines) {
        if (lines == null) return 0L;
        for (String line : lines) {
            if (line == null || !line.startsWith("MemAvailable:")) continue;
            String[] fields = line.trim().split("\\s+");
            if (fields.length < 2) return 0L;
            try {
                long kib = Long.parseLong(fields[1]);
                return kib > 0 && kib <= Long.MAX_VALUE / 1024 ? kib * 1024 : 0L;
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    private long freeMemory(OperatingSystemMXBean os) {
        return os == null ? Runtime.getRuntime().freeMemory() : os.getFreeMemorySize();
    }

    private long runtimeUsed() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private double percent(double value) {
        return value < 0 ? 0 : Math.min(100, value * 100);
    }

    private double ratio(long value, long total) {
        return total <= 0 ? 0 : Math.round((value * 10000d / total)) / 100d;
    }
}
