package gg.kapkan.release;

import gg.kapkan.launcher.launch.MiniJson;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Official content-addressed assets, downloaded with six shared-client workers. */
public final class AssetInstaller {
    static final int WORKERS = 6;
    private static final URI ASSET_ROOT = URI.create("https://resources.download.minecraft.net/");
    private AssetInstaller() {}
    private record Asset(String hash, long size, Path path) {}
    public static void install(HttpClient client, Path game, Map<String,Object> version, Consumer<String> log) throws Exception {
        install(client, game, version, log, ASSET_ROOT, WORKERS);
    }
    static void install(HttpClient client, Path game, Map<String,Object> version, Consumer<String> log, URI root, int workers) throws Exception {
        Map<String,Object> descriptor = object(version.get("assetIndex"));
        String id = MiniJson.string(descriptor.get("id")), url = MiniJson.string(descriptor.get("url"));
        if (id == null || url == null) return;
        if (!id.matches("[A-Za-z0-9._-]{1,128}") || id.equals(".") || id.equals("..")) throw new IOException("Invalid asset index ID");
        Consumer<String> report = log == null ? s -> {} : log;
        Path index = game.resolve("assets/indexes").resolve(id + ".json");
        report.accept("Minecraft: checking asset index...");
        RetryDownloader.download(client, URI.create(url), index, MiniJson.string(descriptor.get("sha1")), size(descriptor));
        Map<String,Object> objects = object(MiniJson.object(MiniJson.parse(Files.readString(index))).get("objects"));
        Map<String,Asset> unique = new LinkedHashMap<>();
        for (Object value : objects.values()) {
            Map<String,Object> entry = object(value);
            String hash = MiniJson.string(entry.get("hash"));
            if (hash == null || !hash.matches("[0-9a-fA-F]{40}")) throw new IOException("Invalid asset hash in index");
            hash = hash.toLowerCase(Locale.ROOT);
            Asset asset = new Asset(hash, size(entry), game.resolve("assets/objects").resolve(hash.substring(0, 2)).resolve(hash));
            Asset previous = unique.putIfAbsent(hash, asset);
            if (previous != null && previous.size != asset.size) throw new IOException("Conflicting asset sizes in index");
        }
        List<Asset> missing = new ArrayList<>();
        int checked = 0;
        long totalBytes = 0;
        boolean knownSize = true;
        LaunchProgress.checking(0, unique.size());
        for (Asset asset : unique.values()) {
            interrupted();
            if (!RetryDownloader.matches(asset.path, asset.hash, asset.size)) {
                missing.add(asset);
                if (asset.size < 0) knownSize = false;
                else totalBytes = Math.addExact(totalBytes, asset.size);
            }
            LaunchProgress.checking(++checked, unique.size());
        }
        int reused = unique.size() - missing.size();
        if (missing.isEmpty()) { report.accept("Minecraft: assets ready (" + reused + " verified and reused)."); return; }
        int count = Math.max(1, Math.min(WORKERS, Math.min(workers, missing.size())));
        report.accept("Minecraft: downloading " + missing.size() + " assets (" + reused + " reused, " + count + " at a time)...");
        LaunchProgress.Batch progress = LaunchProgress.batch(knownSize ? totalBytes : -1, missing.size());
        ExecutorService pool = Executors.newFixedThreadPool(count, r -> {
            Thread thread = new Thread(r, "KapKan-assets"); thread.setDaemon(true); return thread;
        });
        CompletionService<Asset> completions = new ExecutorCompletionService<>(pool);
        Set<Future<Asset>> running = new HashSet<>();
        int submitted = 0, completed = 0;
        try {
            while (completed < missing.size()) {
                interrupted();
                while (running.size() < count && submitted < missing.size()) {
                    Asset asset = missing.get(submitted++);
                    running.add(completions.submit(() -> {
                        LaunchProgress.Transfer transfer = progress.transfer();
                        try (var ignored = progress.bind(transfer)) {
                            RetryDownloader.download(client, root.resolve(asset.hash.substring(0, 2) + "/" + asset.hash), asset.path, asset.hash, asset.size);
                            progress.complete(transfer, Files.size(asset.path));
                        }
                        return asset;
                    }));
                }
                Future<Asset> done = completions.poll(200, TimeUnit.MILLISECONDS);
                progress.tick();
                if (done == null) continue;
                running.remove(done);
                try { done.get(); }
                catch (ExecutionException e) {
                    if (e.getCause() instanceof Exception cause) throw cause;
                    if (e.getCause() instanceof Error cause) throw cause;
                    throw new IOException("Asset download failed", e.getCause());
                }
                completed++;
                if (completed % 500 == 0) report.accept("Minecraft: assets " + completed + "/" + missing.size() + " downloaded...");
            }
            report.accept("Minecraft: assets ready (" + completed + " downloaded, " + reused + " reused).");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); throw e;
        } finally {
            for (Future<Asset> future : running) future.cancel(true);
            pool.shutdownNow();
            boolean cancelled = Thread.interrupted();
            try { pool.awaitTermination(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { cancelled = true; }
            finally { if (cancelled) Thread.currentThread().interrupt(); }
        }
    }
    private static Map<String,Object> object(Object value) { return value instanceof Map<?,?> ? MiniJson.object(value) : Map.of(); }
    private static long size(Map<String,Object> descriptor) throws IOException {
        Object value = descriptor.get("size");
        if (value == null) return -1;
        if (!(value instanceof Number number) || number.longValue() < 0 || number.longValue() > RetryDownloader.MAX_FILE)
            throw new IOException("Invalid asset size");
        return number.longValue();
    }
    private static void interrupted() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Asset download cancelled");
    }
}
