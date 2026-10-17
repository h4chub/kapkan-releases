package gg.kapkan.release;

import java.util.ArrayDeque;
import java.util.function.LongSupplier;

/** Rolling measurement of bytes received, including connection latency and retries. */
final class DownloadRate {
    private record Sample(long at, long bytes) {}
    private final LongSupplier clock;
    private final ArrayDeque<Sample> samples = new ArrayDeque<>();
    private long received, lastByte;
    DownloadRate() { this(System::nanoTime); }
    DownloadRate(LongSupplier clock) {
        this.clock = clock;
        lastByte = clock.getAsLong(); samples.add(new Sample(lastByte, 0));
    }
    void received(long bytes) {
        if (bytes <= 0) return;
        received += bytes; lastByte = clock.getAsLong(); sample(lastByte);
    }
    private void sample(long now) {
        if (now - samples.getLast().at >= 200_000_000L) samples.add(new Sample(now, received));
        while (samples.size() > 2 && samples.getFirst().at < now - 8_000_000_000L) samples.removeFirst();
    }
    double bytesPerSecond() {
        long now = clock.getAsLong(); sample(now);
        Sample first = samples.getFirst();
        if (now - first.at < 1_500_000_000L || now - lastByte > 3_000_000_000L) return 0;
        return Math.max(0, (received - first.bytes) * 1_000_000_000.0 / (now - first.at));
    }
}
