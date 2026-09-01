package com.vibegraph.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.DiskIoMetrics;
import com.vibegraph.infrastructure.dto.InfrastructureSnapshot.NetworkMetrics;

class InfrastructureProcMetricsTest {

    @Test
    void rollingCpuStatsKeepsOnlyConfiguredRecentSamples() {
        var stats = InfrastructureMetricsService.rollingCpuStats(List.of(10d, 20d, 30d, 40d), 2);

        assertThat(stats.average()).isEqualTo(35d);
        assertThat(stats.peak()).isEqualTo(40d);
    }

    @Test
    void parsesKernelAvailableMemoryInsteadOfFreeMemory() {
        long available = InfrastructureMetricsService.parseMemAvailableBytes(List.of(
                "MemTotal:        8143896 kB",
                "MemFree:         1766736 kB",
                "MemAvailable:    4715064 kB"));

        assertThat(available).isEqualTo(4_715_064L * 1024);
    }

    @Test
    void treatsMissingKernelAvailableMemoryAsUnavailable() {
        assertThat(InfrastructureMetricsService.parseMemAvailableBytes(List.of(
                "MemTotal: 8143896 kB", "MemFree: 1766736 kB"))).isZero();
    }

    @Test
    void parsesNetworkAndWholeDiskCountersWithoutLoopbackOrPartitions() {
        var counters = InfrastructureMetricsService.parseProcCounters(
                List.of(
                        "Inter-| Receive | Transmit",
                        " face |bytes packets errs drop fifo frame compressed multicast|bytes packets errs drop fifo colls carrier compressed",
                        "  eth0: 1000 10 0 2 0 0 0 0 2000 20 0 3 0 0 0 0",
                        "  docker0: 8000 80 0 4 0 0 0 0 8000 80 0 4 0 0 0 0",
                        "  lo: 9000 90 0 4 0 0 0 0 9000 90 0 4 0 0 0 0"),
                List.of(
                        "8 0 sda 10 0 100 1 20 0 200 2 0 3 99 0",
                        "8 1 sda1 99 0 999 9 99 0 999 9 0 0 99 0",
                        "253 0 dm-0 99 0 999 9 99 0 999 9 0 99 99 0"));

        assertThat(counters.networkInBytes()).isEqualTo(1_000);
        assertThat(counters.networkOutBytes()).isEqualTo(2_000);
        assertThat(counters.networkDrops()).isEqualTo(5);
        assertThat(counters.diskReadSectors()).isEqualTo(100);
        assertThat(counters.diskWriteSectors()).isEqualTo(200);
        assertThat(counters.diskIoMillis()).isEqualTo(3);
    }

    @Test
    void convertsCounterDeltaToBoundedPerSecondMetrics() {
        var previous = new InfrastructureMetricsService.ProcCounters(100, 200, 1, 10, 20, 100);
        var current = new InfrastructureMetricsService.ProcCounters(1_100, 2_200, 4, 110, 220, 150);

        var metrics = InfrastructureMetricsService.rates(previous, current, 2.0);
        NetworkMetrics network = metrics.network();
        DiskIoMetrics disk = metrics.diskIo();

        assertThat(network.inBytesPerSecond()).isEqualTo(500);
        assertThat(network.outBytesPerSecond()).isEqualTo(1_000);
        assertThat(network.droppedPackets()).isEqualTo(3);
        assertThat(disk.readBytesPerSecond()).isEqualTo(25_600);
        assertThat(disk.writeBytesPerSecond()).isEqualTo(51_200);
        assertThat(disk.utilizationPercent()).isEqualTo(2.5d);
    }

    @Test
    void counterResetDoesNotCreateNegativeTraffic() {
        var previous = new InfrastructureMetricsService.ProcCounters(2_000, 2_000, 100, 100, 100, 100);
        var current = new InfrastructureMetricsService.ProcCounters(10, 10, 2, 2, 2, 2);

        var metrics = InfrastructureMetricsService.rates(previous, current, 1.0);

        assertThat(metrics.network().inBytesPerSecond()).isZero();
        assertThat(metrics.network().outBytesPerSecond()).isZero();
        assertThat(metrics.network().droppedPackets()).isZero();
        assertThat(metrics.diskIo().readBytesPerSecond()).isZero();
        assertThat(metrics.diskIo().writeBytesPerSecond()).isZero();
    }
}
