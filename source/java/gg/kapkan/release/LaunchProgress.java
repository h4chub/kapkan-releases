package gg.kapkan.release;

import java.util.*;
import java.util.function.Consumer;

/** Measured, launch-scoped progress shared explicitly with a bounded download group. */
public final class LaunchProgress {
    private LaunchProgress() {}
    @FunctionalInterface public interface Listener { void update(String stage, long done, long total); }
    public record Snapshot(String stage, long done, long total, double bytesPerSecond,
                           long remainingSeconds, int filesDone, int filesTotal, boolean downloading) {
        public int percent() {
            if (total > 0) return (int)Math.min(100, done * 100.0 / total);
            if (filesTotal > 0) return Math.min(100, filesDone * 100 / filesTotal);
            return -1;
        }
        public String description() {
            StringBuilder text = new StringBuilder(stage);
            if (filesTotal > 0) text.append(String.format(Locale.ROOT, " · %,d / %,d files", filesDone, filesTotal));
            else if (total > 0) text.append(" · ").append(size(done)).append(" / ").append(size(total));
            if (downloading) {
                if (bytesPerSecond > 0) text.append(" · ").append(size((long)bytesPerSecond)).append("/s");
                if (remainingSeconds > 0) text.append(" · ~").append(duration(remainingSeconds)).append(" left");
                else if (total >= 0 && done >= total) text.append(" · Finishing");
                else text.append(" · Estimating time…");
            }
            return text.toString();
        }
        public String details() {
            String description = description(), prefix = stage + " · ";
            return description.startsWith(prefix) ? description.substring(prefix.length()) : description;
        }
    }
    static String size(long bytes) {
        return bytes >= 1_000_000 ? String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
            : String.format(Locale.ROOT, "%.0f KB", bytes / 1_000.0);
    }
    static String duration(long seconds) {
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds + 59) / 60 + "m";
        long minutes = (seconds + 59) / 60;
        return minutes / 60 + "h " + minutes % 60 + "m";
    }
    private static final ThreadLocal<Context> ACTIVE = new ThreadLocal<>();
    private record Context(Session session, Batch batch, Transfer transfer) {}
    private static final class Session {
        final Consumer<Snapshot> listener;
        String stage = "Downloading";
        DownloadRate rate = new DownloadRate();
        long previous, next;
        boolean closed;
        Session(Consumer<Snapshot> listener) { this.listener = listener; }
        synchronized void emit(Snapshot snapshot, boolean force) {
            if (closed) return;
            long now = System.nanoTime();
            if (!force && now < next) return;
            next = now + 200_000_000L;
            try { listener.accept(snapshot); } catch (RuntimeException ignored) {}
        }
    }
    public static AutoCloseable listen(Listener listener) {
        return listenDetailed(s -> listener.update(s.stage(), s.done(), s.total()));
    }
    public static AutoCloseable listenDetailed(Consumer<Snapshot> listener) {
        Context previous = ACTIVE.get();
        Session session = new Session(Objects.requireNonNull(listener));
        ACTIVE.set(new Context(session, null, null));
        return () -> { synchronized (session) { session.closed = true; } restore(previous); };
    }
    private static void restore(Context context) { if (context == null) ACTIVE.remove(); else ACTIVE.set(context); }
    static void checking(int checked, int total) {
        Context c = ACTIVE.get();
        if (c != null) c.session.emit(new Snapshot("Checking asset files", 0, -1, 0, -1, checked, total, false), checked == 0);
    }
    static void file(String name) {
        Context c = ACTIVE.get();
        if (c == null || c.batch != null) return;
        Session s = c.session;
        s.stage = "Downloading " + (name.length() > 38 ? name.substring(0, 35) + "…" : name);
        s.previous = 0; s.rate = new DownloadRate();
    }
    static void attempt() {
        Context c = ACTIVE.get();
        if (c == null) return;
        if (c.batch != null) c.batch.reset(c.transfer);
        else c.session.previous = 0;
    }
    static void bytes(long done, long total) {
        Context c = ACTIVE.get();
        if (c == null) return;
        if (c.batch != null) { c.batch.bytes(c.transfer, done); return; }
        Session s = c.session;
        s.rate.received(Math.max(0, done - s.previous)); s.previous = done;
        double rate = s.rate.bytesPerSecond();
        s.emit(new Snapshot(s.stage, done, total, rate, eta(total, done, rate), -1, -1, true), false);
    }
    private static long eta(long total, long done, double rate) {
        return total < 0 || rate <= 0 ? -1 : Math.max(0, (long)Math.ceil((total - done) / rate));
    }
    static Batch batch(long total, int files) { return new Batch(ACTIVE.get(), total, files); }
    static final class Transfer { long done; }
    static final class Batch {
        private final Session session;
        private final long total;
        private final int files;
        private final DownloadRate rate = new DownloadRate();
        private final Set<Transfer> active = new HashSet<>();
        private long verified;
        private int finished;
        Batch(Context context, long total, int files) {
            session = context == null ? null : context.session; this.total = total; this.files = files; emit(true);
        }
        synchronized Transfer transfer() { Transfer t = new Transfer(); active.add(t); return t; }
        AutoCloseable bind(Transfer transfer) {
            Context old = ACTIVE.get();
            if (session != null) ACTIVE.set(new Context(session, this, transfer));
            return () -> restore(old);
        }
        synchronized void reset(Transfer t) { t.done = 0; emit(false); }
        synchronized void bytes(Transfer t, long bytes) {
            rate.received(Math.max(0, bytes - t.done)); t.done = bytes; emit(false);
        }
        synchronized void complete(Transfer t, long size) {
            active.remove(t); verified += size; finished++; emit(finished == files);
        }
        synchronized void tick() { emit(false); }
        private void emit(boolean force) {
            if (session == null) return;
            long done = verified;
            for (Transfer t : active) done += t.done;
            double speed = rate.bytesPerSecond();
            session.emit(new Snapshot("Minecraft assets", done, total, speed, eta(total, done, speed), finished, files, true), force);
        }
    }
}
