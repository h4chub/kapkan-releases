package gg.kapkan.release;

import gg.kapkan.launcher.launch.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Metadata-driven final repair. The shared cache never follows the profile's game directory. */
public final class FabricLaunchSupport {
    private static final String KNOT = "net.fabricmc.loader.impl.launch.knot.KnotClient";
    private FabricLaunchSupport() {}

    public static void verify(LaunchPlan plan, List<String> command, Consumer<String> output) throws LaunchException {
        Consumer<String> log = LaunchLogPrivacy.logger(output);
        try {
            Path root = plan.installationDirectory().toAbsolutePath().normalize();
            Path metadata = InstallIntegrity.safe(root.resolve("versions"), plan.launchVersion() + "/" + plan.launchVersion() + ".json");
            Map<String,Object> profile = MiniJson.object(MiniJson.parse(Files.readString(metadata)));
            if (!plan.launchVersion().equals(MiniJson.string(profile.get("id")))
                    || !plan.baseVersion().equals(MiniJson.string(profile.get("inheritsFrom")))
                    || !KNOT.equals(MiniJson.string(profile.get("mainClass"))))
                throw new IOException("Fabric profile does not match the selected version and entry point.");
            int cp = -1;
            for (int i = 1; i < command.size(); i++) {
                if (Set.of("-cp", "-classpath", "--class-path").contains(command.get(i))) { cp = i; break; }
            }
            if (cp < 0 || cp + 1 >= command.size() || !command.contains(KNOT))
                throw new IOException("Fabric command is missing its classpath or KnotClient entry point.");
            LinkedHashSet<String> entries = new LinkedHashSet<>(Arrays.asList(command.get(cp + 1).split(Pattern.quote(java.io.File.pathSeparator), -1)));
            entries.remove("");
            boolean loader = false;
            int repaired = 0;
            // Validate every descriptor before any download. No version/ASM list is hardcoded.
            List<Dependency> dependencies = new ArrayList<>();
            for (Object item : MiniJson.array(profile.get("libraries"))) {
                Map<String,Object> lib = MiniJson.object(item);
                if (!RuleEvaluator.allowed(lib)) continue;
                String name = MiniJson.string(lib.get("name"));
                Map<String,Object> artifact = MiniJson.object(MiniJson.object(lib.get("downloads")).get("artifact"));
                String relative = InstallIntegrity.libraryPath(lib);
                Path file = InstallIntegrity.safe(root.resolve("libraries"), relative);
                boolean isLoader = name.startsWith("net.fabricmc:fabric-loader:");
                loader |= isLoader;
                String url = MiniJson.string(artifact.get("url"));
                if (url.isBlank()) {
                    String repository = MiniJson.string(lib.get("url"));
                    if (repository.isBlank()) repository = "https://maven.fabricmc.net/";
                    url = repository.replaceAll("/+$", "") + "/" + relative;
                }
                String hash = MiniJson.string(artifact.get("sha1"));
                if (hash.isBlank()) hash = MiniJson.string(lib.get("sha1"));
                Object suppliedSize = artifact.containsKey("size") ? artifact.get("size") : lib.get("size");
                long size = suppliedSize instanceof Number n ? n.longValue() : -1;
                if ((!hash.isBlank() && !hash.matches("(?i)[0-9a-f]{40}")) || size < -1)
                    throw new IOException("Invalid Fabric dependency integrity descriptor.");
                String normalized = relative.replace('\\', '/');
                int versionFolder = normalized.lastIndexOf('/', normalized.lastIndexOf('/') - 1);
                String component = versionFolder < 0 ? normalized : normalized.substring(0, versionFolder + 1);
                dependencies.add(new Dependency(file, URI.create(url), hash, size, isLoader, component));
            }
            if (!loader) throw new IOException("Fabric profile does not declare Fabric Loader.");
            try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                    .followRedirects(HttpClient.Redirect.NORMAL).build()) {
                for (Dependency dependency : dependencies) {
                    if (!dependency.usable()) {
                        log.accept("Fabric: repairing " + dependency.file().getFileName());
                        RetryDownloader.downloadValidated(http, dependency.url(), dependency.file(), dependency.hash(), dependency.size(),
                            p -> LoaderNetwork.validateJar(p, false, dependency.loader() ? KNOT.replace('.', '/') + ".class" : ""));
                        if (!dependency.usable()) throw new IOException("Fabric dependency remains incomplete after repair.");
                        repaired++;
                    }
                    // An old profile-cache copy or different library version must not win class loading.
                    entries.removeIf(entry -> entry.replace('\\', '/').contains("/libraries/" + dependency.component()));
                    entries.add(dependency.file().toString());
                }
            }
            command.set(cp + 1, String.join(java.io.File.pathSeparator, entries));
            log.accept("Fabric launch verified" + (repaired == 0 ? "" : ": repaired " + repaired + " libraries"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LaunchException("Fabric preparation cancelled. Verified files were kept.", e);
        } catch (Exception e) {
            throw new LaunchException("Fabric launch blocked: " + e.getMessage(), e);
        }
    }

    private record Dependency(Path file, URI url, String hash, long size, boolean loader, String component) {
        boolean usable() {
            try {
                if (!RetryDownloader.matches(file, hash, size)) return false;
                LoaderNetwork.validateJar(file, false, loader ? KNOT.replace('.', '/') + ".class" : "");
                return true;
            } catch (IOException | RuntimeException e) { return false; }
        }
    }
}
