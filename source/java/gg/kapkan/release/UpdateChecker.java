package gg.kapkan.release;

import gg.kapkan.launcher.launch.MiniJson;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.util.regex.Pattern;

/** Public release discovery only. No installer is downloaded or executed here. */
public final class UpdateChecker {
    public static final String VERSION = "1.0.5";
    public static final String TAG = "v1.0.5-beta.1";
    public static final URI RELEASES = URI.create("https://github.com/h4chub/kapkan-releases/releases");
    static final URI FEED = URI.create("https://api.github.com/repos/h4chub/kapkan-releases/releases?per_page=30");
    static final int MAX_BODY = 2 * 1024 * 1024;
    private static final String REPO = "https://github.com/h4chub/kapkan-releases/releases/";
    private static final Pattern VERSION_PATTERN = Pattern.compile("v?(0|[1-9][0-9]{0,4})\\.(0|[1-9][0-9]{0,4})\\.(0|[1-9][0-9]{0,4})(?:-(alpha|beta|rc)\\.(0|[1-9][0-9]{0,4}))?");

    public enum Platform {
        MACOS, WINDOWS, UNSUPPORTED;
        public static Platform current() {
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
            if (os.startsWith("mac") && Set.of("aarch64", "arm64", "x86_64", "amd64").contains(arch)) return MACOS;
            if (os.startsWith("windows") && Set.of("amd64", "x86_64", "x64").contains(arch)) return WINDOWS;
            return UNSUPPORTED;
        }
    }
    public record Release(String version, String tag, boolean beta, Platform platform, URI page, URI download, String filename) {}
    public record Result(Platform platform, Optional<Release> update) {}

    public Result check() throws Exception {
        Platform platform = Platform.current();
        if (platform == Platform.UNSUPPORTED) return new Result(platform, Optional.empty());
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            return fetch(client, FEED, TAG, platform, Duration.ofSeconds(10));
        }
    }

    static Result fetch(HttpClient client, URI endpoint, String current, Platform platform, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout)
            .header("User-Agent", "KapKan/" + VERSION)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28").GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> new LimitedBody());
        try {
            HttpResponse<byte[]> response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.statusCode() != 200) throw new IOException("Update service returned HTTP " + response.statusCode());
            return select(new String(response.body(), StandardCharsets.UTF_8), current, platform);
        } finally { if (!pending.isDone()) pending.cancel(true); }
    }

    /** Numeric version ordering, including beta releases; feed order is not trusted. */
    static Result select(String json, String currentTag, Platform platform) throws IOException {
        if (platform == Platform.UNSUPPORTED) return new Result(platform, Optional.empty());
        if (json.length() > MAX_BODY) throw new IOException("Update response too large");
        Version current = Version.parse(currentTag);
        if (current == null) throw new IOException("Invalid installed version");
        Object parsed;
        try { parsed = MiniJson.parse(json); } catch (RuntimeException e) { throw new IOException("Invalid update response", e); }
        if (!(parsed instanceof List<?> entries)) throw new IOException("Invalid release list");
        Release newest = null;
        Version newestVersion = current;
        for (Object entry : entries) {
            if (!(entry instanceof Map<?, ?> data) || !Boolean.FALSE.equals(data.get("draft"))
                    || !(data.get("prerelease") instanceof Boolean beta)) continue;
            String tag = string(data.get("tag_name"));
            Version version = Version.parse(tag);
            if (version == null || version.compareTo(newestVersion) <= 0) continue;
            if (current.stage == 3 && (beta || version.stage != 3)) continue;
            if (beta != (version.stage != 3)) continue;
            String page = REPO + "tag/" + tag;
            if (!page.equals(data.get("html_url"))) continue;
            String filename = platform == Platform.MACOS ? "KapKan-macOS-" + version.core() + ".zip"
                : "KapKan-" + version.core() + "-Setup.exe";
            String download = REPO + "download/" + tag + "/" + filename;
            if (!(data.get("assets") instanceof List<?> assets)) continue;
            boolean valid = false;
            for (Object item : assets) {
                if (!(item instanceof Map<?, ?> asset)) continue;
                Object size = asset.get("size");
                if (filename.equals(asset.get("name")) && download.equals(asset.get("browser_download_url"))
                        && "uploaded".equals(asset.get("state")) && size instanceof Number n && n.longValue() > 0
                        && n.longValue() <= 1024L * 1024 * 1024
                        && string(asset.get("digest")).matches("sha256:[0-9a-f]{64}")) valid = true;
            }
            if (!valid) continue;
            newestVersion = version;
            newest = new Release(version.core(), tag, beta, platform, URI.create(page), URI.create(download), filename);
        }
        return new Result(platform, Optional.ofNullable(newest));
    }
    private static String string(Object value) { return value instanceof String s ? s : ""; }

    private record Version(int major, int minor, int patch, int stage, int sequence) implements Comparable<Version> {
        static Version parse(String value) {
            if (value == null) return null;
            var m = VERSION_PATTERN.matcher(value);
            if (!m.matches()) return null;
            int stage = m.group(4) == null ? 3 : switch (m.group(4)) { case "alpha" -> 0; case "beta" -> 1; default -> 2; };
            return new Version(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)), stage,
                m.group(5) == null ? 0 : Integer.parseInt(m.group(5)));
        }
        String core() { return major + "." + minor + "." + patch; }
        public int compareTo(Version other) {
            int[] a = {major, minor, patch, stage, sequence}, b = {other.major, other.minor, other.patch, other.stage, other.sequence};
            for (int i = 0; i < a.length; i++) { int c = Integer.compare(a[i], b[i]); if (c != 0) return c; }
            return 0;
        }
    }
    /** Bound response memory before parsing, including chunked responses. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription next) { subscription = next; next.request(1); }
        public void onNext(List<ByteBuffer> chunks) {
            for (ByteBuffer chunk : chunks) {
                if (chunk.remaining() > MAX_BODY - bytes.size()) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Update response too large")); return;
                }
                byte[] part = new byte[chunk.remaining()]; chunk.get(part); bytes.writeBytes(part);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toByteArray()); }
    }
}
