package gg.kapkan.release;

import gg.kapkan.launcher.launch.LaunchPlan;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;

/** Filesystem/argument fixtures with the verified official binaries; this does not execute Minecraft. */
public final class PerformanceIntegrationTest {
    private static int checks;
    private static final String PREFIX = "-Dfabric.addMods=";
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); checks++; }
    public static void main(String[] args) throws Exception {
        Path bundle = Path.of(args.length == 0 ? "performance" : args[0]).toAbsolutePath(), temp = Files.createTempDirectory("kapkan-performance-fixtures-");
        String oldConfig = System.getProperty("kapkan.performance.config"), oldDirectory = System.getProperty("kapkan.performanceDirectory"), oldDisabled = System.getProperty("kapkan.performance.disabled");
        try {
            System.setProperty("kapkan.performanceDirectory", bundle.toString());
            System.setProperty("kapkan.performance.config", temp.resolve("preferences/performance.properties").toString());
            System.clearProperty("kapkan.performance.disabled");
            Path game = temp.resolve("clean"); Files.createDirectories(game.resolve("mods"));
            Path user = game.resolve("options.txt"); Files.writeString(user, "renderDistance:19\nfov:91\n"); byte[] settings = Files.readAllBytes(user);
            for (String target : List.of("26.1", "26.1.1", "26.1.2")) {
                LaunchPlan p = plan(game, target, "Fabric", List.of()); var selected = PerformanceIntegration.prepare(p, s -> {});
                check(added(selected).size() == 1, "One pinned mod for exact target " + target);
                check(p.command().size() == 3, "Input command is immutable in use");
                check(selected.command().stream().filter(x -> x.startsWith(PREFIX)).count() == 1, "One merged addMods argument");
                check(PerformanceIntegration.prepare(selected, s -> {}).command().equals(selected.command()), "Repeated preparation is idempotent");
            }
            check(Arrays.equals(settings, Files.readAllBytes(user)), "Game graphics settings preserved byte for byte");
            check(Files.list(game.resolve("mods")).findAny().isEmpty(), "No bundled files installed into mods folder");
            for (String loader : List.of("Vanilla", "Forge", "NeoForge", "Quilt")) unchanged(plan(game, "26.1.2", loader, List.of()), "Unsupported loader " + loader);
            for (String version : List.of("1.21.11", "26.2", "26.1-snapshot")) unchanged(plan(game, version, "Fabric", List.of()), "Unsupported version " + version);
            var outdated = new LaunchPlan(List.of("java", "-cp", "game"), game, "fabric-loader-0.18.5-26.1.2", "26.1.2", "Fabric", Path.of("java"), 25);
            unchanged(outdated, "Older Fabric retained unchanged");
            var oldJava = new LaunchPlan(List.of("java", "-cp", "game"), game, "fabric-loader-0.19.5-26.1.2", "26.1.2", "Fabric", Path.of("java"), 21);
            unchanged(oldJava, "Old Java retained unchanged");
            PerformanceIntegration.setEnabled(false); check(!PerformanceIntegration.enabled(), "Saved opt-out applies immediately");
            unchanged(plan(game, "26.1.2", "Fabric", List.of()), "Saved opt-out prevents injection");
            PerformanceIntegration.setEnabled(true); check(PerformanceIntegration.enabled(), "Saved enable preference reloads");
            System.setProperty("kapkan.performance.disabled", "true"); check(!PerformanceIntegration.enabled(), "System opt-out overrides saved enable");
            unchanged(plan(game, "26.1.2", "Fabric", List.of()), "System opt-out respected"); System.clearProperty("kapkan.performance.disabled");
            unchanged(plan(game, "26.1.2", "Fabric", List.of("-Dkapkan.performance.disabled=true")), "Per-process opt-out respected");

            Path companion = fixture(temp.resolve("companion.jar"), "kapkan-presence", "", null);
            var injected = PerformanceIntegration.prepare(plan(game, "26.1.2", "Fabric", List.of(PREFIX + companion)), s -> {});
            check(added(injected).size() == 2 && added(injected).getFirst().equals(companion.toString()), "Existing companion injection preserved and appended");
            Path external = fixture(temp.resolve("user-immediatelyfast.jar"), "immediatelyfast", "", null); byte[] original = Files.readAllBytes(external);
            var merged = PerformanceIntegration.prepare(plan(game, "26.1.2", "Fabric", List.of(PREFIX + companion, PREFIX + external)), s -> {});
            check(added(merged).size() == 2 && added(merged).containsAll(List.of(companion.toString(), external.toString())), "Multiple existing properties combined without losing paths");
            check(added(merged).stream().noneMatch(x -> x.contains("ImmediatelyFast-Fabric")), "Existing injected same ID is not duplicated");
            check(Arrays.equals(original, Files.readAllBytes(external)), "Existing injected mod untouched");
            unchanged(plan(game, "26.1.2", "Fabric", List.of(PREFIX + "@mods-list.txt")), "Uninspectable Fabric list retained without additions");

            for (String id : List.of("immediatelyfast", "lithium", "ferritecore")) {
                Path dir = temp.resolve("manual-" + id); Path existing = fixture(dir.resolve("mods/renamed.jar"), id, "", null); byte[] existingBytes = Files.readAllBytes(existing);
                var selected = PerformanceIntegration.prepare(plan(dir, "26.1.2", "Fabric", List.of()), s -> {});
                check(added(selected).size() == (id.equals("immediatelyfast") ? 0 : 1), "Renamed existing " + id + " preserved without duplicate");
                check(Arrays.equals(existingBytes, Files.readAllBytes(existing)), "Existing " + id + " file untouched");
            }
            Path provided = temp.resolve("provided"); fixture(provided.resolve("mods/alternate.jar"), "alternate", ",\"provides\":[\"immediatelyfast\"]", null);
            check(added(PerformanceIntegration.prepare(plan(provided, "26.1.2", "Fabric", List.of()), s -> {})).size() == 0, "Provided mod ID prevents duplicate");
            for (String key : List.of("breaks", "conflicts")) {
                Path conflict = temp.resolve(key); fixture(conflict.resolve("mods/another.jar"), "another", ",\"" + key + "\":{\"immediatelyfast\":\"*\"}", null);
                check(added(PerformanceIntegration.prepare(plan(conflict, "26.1.2", "Fabric", List.of()), s -> {})).size() == 0, "Reverse " + key + " prevents injection");
            }
            for (String renderer : List.of("optifabric", "canvas", "vulkanmod")) {
                Path conflict = temp.resolve("renderer-" + renderer); fixture(conflict.resolve("mods/renderer.jar"), renderer, "", null);
                check(added(PerformanceIntegration.prepare(plan(conflict, "26.1.2", "Fabric", List.of()), s -> {})).isEmpty(), "Known renderer conflict skips ImmediatelyFast: " + renderer);
            }
            Path hydrogen = temp.resolve("hydrogen"); fixture(hydrogen.resolve("mods/memory.jar"), "hydrogen", "", null);
            check(added(PerformanceIntegration.prepare(plan(hydrogen, "26.1.2", "Fabric", List.of()), s -> {})).size() == 1, "Unrelated Hydrogen mod does not suppress ImmediatelyFast");
            Path nested = temp.resolve("nested"), child = fixture(temp.resolve("nested-immediatelyfast.jar"), "immediatelyfast", "", null);
            fixture(nested.resolve("mods/container.jar"), "container", ",\"jars\":[{\"file\":\"META-INF/jars/child.jar\"}]", Files.readAllBytes(child));
            check(added(PerformanceIntegration.prepare(plan(nested, "26.1.2", "Fabric", List.of()), s -> {})).size() == 0, "Nested same-ID mod is not duplicated");
            Path broken = temp.resolve("broken"); Files.createDirectories(broken.resolve("mods")); Files.writeString(broken.resolve("mods/user.jar"), "USER DATA");
            unchanged(plan(broken, "26.1.2", "Fabric", List.of()), "Unreadable active JAR fails safe");
            check(Files.readString(broken.resolve("mods/user.jar")).equals("USER DATA"), "Unreadable mod remains untouched");
            Path linked = temp.resolve("linked"); Files.createDirectories(linked); Files.createSymbolicLink(linked.resolve("mods"), provided.resolve("mods"));
            check(added(PerformanceIntegration.prepare(plan(linked, "26.1.2", "Fabric", List.of()), s -> {})).size() == 0, "Linked custom mods folder inspected");
            check(Files.isSymbolicLink(linked.resolve("mods")), "Custom folder link retained");

            Path corrupt = temp.resolve("bundle"); Files.createDirectories(corrupt.resolve("mods"));
            try (var files = Files.list(bundle.resolve("mods"))) { for (Path file : files.toList()) Files.copy(file, corrupt.resolve("mods").resolve(file.getFileName())); }
            Path bad = corrupt.resolve("mods/ImmediatelyFast-Fabric-1.15.3+26.1.jar"); byte[] bytes = Files.readAllBytes(bad); bytes[100] ^= 1; Files.write(bad, bytes);
            System.setProperty("kapkan.performanceDirectory", corrupt.toString());
            check(added(PerformanceIntegration.prepare(plan(game, "26.1.2", "Fabric", List.of()), s -> {})).size() == 0, "Same-size tampered binary fails SHA512 and is skipped");
            System.setProperty("kapkan.performanceDirectory", temp.resolve("missing").toString());
            unchanged(plan(game, "26.1.2", "Fabric", List.of()), "Missing bundle does not prevent launch");
            System.setProperty("kapkan.performanceDirectory", bundle.toString());
            Path resources = temp.resolve("packaged Resources"); Files.createDirectories(resources.resolve("performance/mods"));
            try (var files = Files.list(bundle.resolve("mods"))) { for (Path file : files.toList()) Files.copy(file, resources.resolve("performance/mods").resolve(file.getFileName())); }
            Path probe = resources.resolve("KapKan-Launcher.jar");
            try (var jar = new JarOutputStream(Files.newOutputStream(probe))) {
                for (String name : List.of("gg/kapkan/release/PerformanceIntegration.class", "gg/kapkan/release/PerformanceIntegration$Asset.class", "gg/kapkan/release/PerformanceIntegration$Inventory.class", "gg/kapkan/release/PerformanceIntegrationTest$LocationProbe.class", "gg/kapkan/launcher/launch/LaunchPlan.class")) {
                    jar.putNextEntry(new JarEntry(name)); try (var in = PerformanceIntegrationTest.class.getClassLoader().getResourceAsStream(name)) { jar.write(in.readAllBytes()); } jar.closeEntry();
                }
            }
            String baseline = Path.of(gg.kapkan.launcher.launch.MiniJson.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
            var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java").toString(), "-Dkapkan.performance.config=" + temp.resolve("probe.properties"), "-cp", probe + File.pathSeparator + baseline, "gg.kapkan.release.PerformanceIntegrationTest$LocationProbe", game.toString()).redirectErrorStream(true).start();
            String probeOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            check(process.waitFor() == 0 && probeOutput.contains("PACKAGED LOCATION PASS"), "Default Resources/performance location works from packaged launcher JAR: " + probeOutput);
            Path config = Path.of(System.getProperty("kapkan.performance.config")); Files.writeString(config, "enabled=true\nuserPreference=keep\n");
            PerformanceIntegration.setEnabled(false); check(Files.readString(config).contains("userPreference=keep"), "Unknown preference keys preserved");
            System.out.println("Performance integration: " + checks + " checks passed; filesystem/argument fixtures only, no Minecraft benchmark.");
        } finally {
            restore("kapkan.performance.config", oldConfig); restore("kapkan.performanceDirectory", oldDirectory); restore("kapkan.performance.disabled", oldDisabled);
            try (var paths = Files.walk(temp)) { for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
        }
    }
    private static void restore(String key, String value) { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }
    public static final class LocationProbe {
        public static void main(String[] args) {
            var plan = new LaunchPlan(List.of("java", "-cp", "game"), Path.of(args[0]), "fabric-loader-0.19.5-26.1.2", "26.1.2", "Fabric", Path.of("java"), 25);
            var prepared = PerformanceIntegration.prepare(plan, System.out::println);
            String command = String.join(" ", prepared.command());
            long injected = prepared.command().stream().filter(arg -> arg.startsWith("-Dfabric.addMods=")).mapToLong(arg -> arg.substring("-Dfabric.addMods=".length()).split(java.util.regex.Pattern.quote(File.pathSeparator)).length).sum();
            if (!command.contains("ImmediatelyFast-Fabric") || command.contains("lithium-fabric") || command.contains("ferritecore-") || injected != 1) throw new AssertionError(command);
            System.out.println("PACKAGED LOCATION PASS");
        }
    }
    private static void unchanged(LaunchPlan plan, String reason) { check(PerformanceIntegration.prepare(plan, s -> {}).command().equals(plan.command()), reason); }
    private static LaunchPlan plan(Path game, String version, String loader, List<String> extra) {
        var command = new ArrayList<>(List.of("java")); command.addAll(extra); command.addAll(List.of("-cp", "game"));
        return new LaunchPlan(List.copyOf(command), game, "fabric-loader-0.19.5-" + version, version, loader, Path.of("java"), 25);
    }
    private static List<String> added(LaunchPlan plan) {
        return plan.command().stream().filter(x -> x.startsWith(PREFIX)).flatMap(x -> Arrays.stream(x.substring(PREFIX.length()).split(java.util.regex.Pattern.quote(File.pathSeparator)))).toList();
    }
    private static Path fixture(Path file, String id, String extra, byte[] nested) throws IOException {
        Files.createDirectories(file.getParent());
        try (var jar = new JarOutputStream(Files.newOutputStream(file))) {
            jar.putNextEntry(new JarEntry("fabric.mod.json")); jar.write(("{\"schemaVersion\":1,\"id\":\"" + id + "\",\"version\":\"test\"" + extra + "}").getBytes(StandardCharsets.UTF_8)); jar.closeEntry();
            if (nested != null) { jar.putNextEntry(new JarEntry("META-INF/jars/child.jar")); jar.write(nested); jar.closeEntry(); }
        }
        return file;
    }
}
