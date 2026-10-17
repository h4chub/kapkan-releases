package gg.kapkan.release;

import gg.kapkan.launcher.launch.LaunchPlan;
import gg.kapkan.launcher.launch.MiniJson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.zip.ZipInputStream;

/** Adds verified, unmodified optimization mods to one process without installing into user folders. */
public final class PerformanceIntegration {
    private static final String ADD_MODS = "-Dfabric.addMods=";
    private static final Set<String> TARGETS = Set.of("26.1", "26.1.1", "26.1.2");
    private static final int MAX_METADATA = 1024 * 1024, MAX_NESTED = 16 * 1024 * 1024;
    private record Asset(String id, String version, String file, long size, String sha512, Set<String> conflicts) {}
    private static final List<Asset> ASSETS = List.of(
        new Asset("immediatelyfast", "1.15.3+26.1", "ImmediatelyFast-Fabric-1.15.3+26.1.jar", 310057,
            "b665037aee8e76e330f068f640b340b97ce8c0c0e940dfccda8a5b00f772dfa9b2d20594bb950e4f2595b359ad3c27190a4d998da28d4507a9747ab507980d11",
            Set.of("optifabric", "canvas", "vulkanmod")),
        new Asset("lithium", "0.24.7+mc26.1.2", "lithium-fabric-0.24.7+mc26.1.2.jar", 912821,
            "37240bdbf93c19fdc6aa632d89223639f0ef9d923ee1793b33d624946b0482cc972834049ed07d71db3494e16c9de68e755937aac7c4ac5d7291bd38c1425899",
            Set.of("optifabric")),
        new Asset("ferritecore", "9.0.0", "ferritecore-9.0.0-fabric.jar", 72677,
            "d81fa97e11784c19d42f89c2f433831d007603dd7193cee45fa177e4a6a9c52b384b198586e04a0f7f63cd996fed713322578bde9a8db57e1188854ae5cbe584",
            Set.of("hydrogen")));
    private PerformanceIntegration() {}

    private static Path settings() {
        String configured = System.getProperty("kapkan.performance.config", "");
        return configured.isBlank() ? Path.of(System.getProperty("user.home"), ".kapkan", "performance.properties") : Path.of(configured);
    }

    /** The saved preference applies to the next launch; the diagnostic property always takes precedence. */
    public static boolean enabled() {
        if (Boolean.getBoolean("kapkan.performance.disabled")) return false;
        Path file = settings();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return true;
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
            var values = new Properties(); values.load(in);
            return !"false".equalsIgnoreCase(values.getProperty("enabled", "true").strip());
        } catch (IOException | IllegalArgumentException unavailable) { return false; }
    }

    public static synchronized void setEnabled(boolean enabled) throws IOException {
        Path file = settings().toAbsolutePath().normalize(), parent = file.getParent();
        var values = new Properties();
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) throw new IOException("Performance preference is not a regular file");
            try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) { values.load(in); }
            catch (IllegalArgumentException malformed) { throw new IOException("Invalid performance preference", malformed); }
        }
        Files.createDirectories(parent);
        values.setProperty("enabled", Boolean.toString(enabled));
        Path temporary = Files.createTempFile(parent, "performance-", ".tmp");
        try {
            try (var out = Files.newOutputStream(temporary)) { values.store(out, "KapKan bundled optimization mods; applies next launch"); }
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }

    public static LaunchPlan prepare(LaunchPlan plan, Consumer<String> log) {
        if (!enabled() || disabledInCommand(plan.command()) || !TARGETS.contains(plan.baseVersion()) ||
                !"Fabric".equalsIgnoreCase(plan.loaderLabel()) || plan.javaMajor() < 25 || !supportsLoader(plan.launchVersion())) return plan;
        try {
            Path directory = directory();
            var existing = new Inventory();
            Path mods = plan.workingDirectory().resolve("mods");
            for (String argument : plan.command()) if (argument.startsWith("-Dfabric.modsFolder="))
                mods = resolve(plan.workingDirectory(), argument.substring("-Dfabric.modsFolder=".length()));
            scanFolder(mods, existing);
            var injected = new LinkedHashSet<String>();
            for (String argument : plan.command()) if (argument.startsWith(ADD_MODS)) {
                String value = argument.substring(ADD_MODS.length());
                for (String part : value.split(java.util.regex.Pattern.quote(File.pathSeparator), -1)) {
                    // Fabric @lists and wildcards have their own expansion rules. Preserve them untouched.
                    if (part.isBlank() || part.startsWith("@") || part.contains("*")) throw new IOException("Uninspectable injected mod path");
                    injected.add(part);
                    scanPath(resolve(plan.workingDirectory(), part), existing);
                }
            }
            boolean changed = false;
            for (Asset asset : ASSETS) {
                if (existing.ids.contains(asset.id())) { log.accept("KapKan: existing " + asset.id() + " retained."); continue; }
                if (!Collections.disjoint(existing.ids, asset.conflicts()) || existing.blocked.contains(asset.id())) {
                    log.accept("KapKan: bundled " + asset.id() + " skipped for mod compatibility."); continue;
                }
                Path jar = directory.resolve("mods").resolve(asset.file()).toAbsolutePath().normalize();
                if (jar.toString().contains(File.pathSeparator)) throw new IOException("Unsupported bundle path separator");
                try {
                    validate(jar, asset);
                    if (injected.add(jar.toString())) changed = true;
                    existing.ids.add(asset.id());
                } catch (IOException invalid) { log.accept("KapKan: bundled " + asset.id() + " unavailable or failed verification; normal launch retained."); }
            }
            if (!changed) return plan;
            var command = new ArrayList<>(plan.command());
            command.removeIf(arg -> arg.startsWith(ADD_MODS));
            command.add(1, ADD_MODS + String.join(File.pathSeparator, injected));
            log.accept("KapKan optimization mods prepared for " + plan.baseVersion() + ".");
            return new LaunchPlan(List.copyOf(command), plan.workingDirectory(), plan.launchVersion(), plan.baseVersion(),
                plan.loaderLabel(), plan.javaExecutable(), plan.javaMajor(), plan.installationDirectory());
        } catch (Exception unavailable) {
            log.accept("KapKan optimization bundle skipped: active mods could not be safely inspected. Normal launch retained.");
            return plan;
        }
    }

    private static boolean disabledInCommand(List<String> command) {
        String value = "false";
        for (String arg : command) if (arg.startsWith("-Dkapkan.performance.disabled=")) value = arg.substring("-Dkapkan.performance.disabled=".length());
        return Boolean.parseBoolean(value);
    }
    private static boolean supportsLoader(String version) {
        try {
            if (!version.startsWith("fabric-loader-")) return false;
            String[] parts = version.substring("fabric-loader-".length()).split("-", 2)[0].split("\\.");
            int major = Integer.parseInt(parts[0]), minor = Integer.parseInt(parts[1]), patch = Integer.parseInt(parts[2]);
            return major > 0 || minor > 19 || minor == 19 && patch >= 5;
        } catch (RuntimeException invalid) { return false; }
    }
    private static Path directory() throws Exception {
        String configured = System.getProperty("kapkan.performanceDirectory", "");
        if (!configured.isBlank()) return Path.of(configured);
        return Path.of(PerformanceIntegration.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent().resolve("performance");
    }
    private static Path resolve(Path game, String value) {
        Path path = Path.of(value); return path.isAbsolute() ? path : game.resolve(path);
    }
    private static void validate(Path file, Asset asset) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) != asset.size()) throw new IOException("Invalid bundled file");
        try {
            var digest = MessageDigest.getInstance("SHA-512");
            try (var in = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] buffer = new byte[16384]; for (int count; (count = in.read(buffer)) != -1;) digest.update(buffer, 0, count);
            }
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(asset.sha512()), digest.digest())) throw new IOException("Bundle checksum mismatch");
            try (var jar = new JarFile(file.toFile())) {
                var entry = jar.getJarEntry("fabric.mod.json"); if (entry == null) throw new IOException("Missing bundle metadata");
                Map<String, Object> meta;
                try (var in = jar.getInputStream(entry)) { meta = metadata(readBounded(in, MAX_METADATA)); }
                if (!asset.id().equals(MiniJson.string(meta.get("id"))) || !asset.version().equals(MiniJson.string(meta.get("version")))) throw new IOException("Wrong bundle metadata");
            }
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private static final class Inventory {
        final Set<String> ids = new HashSet<>(), blocked = new HashSet<>();
        final Set<Path> scanned = new HashSet<>(); int entries, zipEntries, nestedBudget = 64 * 1024 * 1024;
    }
    private static void scanFolder(Path folder, Inventory inventory) throws IOException {
        if (!Files.exists(folder, LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isDirectory(folder)) throw new IOException("Unavailable mod folder");
        try (var files = Files.list(folder)) {
            for (Path file : files.sorted().toList()) if (file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) scanPath(file, inventory);
        }
    }
    private static void scanPath(Path file, Inventory inventory) throws IOException {
        Path real = file.toRealPath(); if (!inventory.scanned.add(real)) return;
        if (Files.isDirectory(real)) {
            Path descriptor = real.resolve("fabric.mod.json");
            if (!Files.isRegularFile(descriptor)) throw new IOException("Uninspectable injected mod directory");
            Map<String,Object> meta;
            try (var in = Files.newInputStream(descriptor)) { meta = metadata(readBounded(in, MAX_METADATA)); }
            collect(meta, inventory);
            if (meta.get("jars") instanceof List<?> list && !list.isEmpty()) throw new IOException("Nested directory mods require normal loader resolution");
            return;
        }
        if (!Files.isRegularFile(real)) throw new IOException("Unavailable active mod");
        try (var jar = new JarFile(real.toFile())) {
            var entry = jar.getJarEntry("fabric.mod.json"); if (entry == null) return;
            Map<String,Object> meta;
            try (var in = jar.getInputStream(entry)) { meta = metadata(readBounded(in, MAX_METADATA)); }
            collect(meta, inventory);
            for (String nested : nested(meta)) {
                var child = jar.getJarEntry(nested); if (child == null) throw new IOException("Missing nested mod");
                try (var in = jar.getInputStream(child)) { scanNested(readNested(in, inventory), inventory, 1); }
            }
        }
    }
    private static void scanNested(byte[] bytes, Inventory inventory, int depth) throws IOException {
        if (depth > 4) throw new IOException("Nested mod depth exceeded");
        Map<String,byte[]> entries = new HashMap<>(); Map<String,Object> meta = null;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++inventory.zipEntries > 16384) throw new IOException("Nested archive entry count exceeded");
                String name = entry.getName();
                if (name.equals("fabric.mod.json")) {
                    byte[] descriptor = readBounded(zip, Math.min(MAX_METADATA, inventory.nestedBudget));
                    inventory.nestedBudget -= descriptor.length;
                    meta = metadata(descriptor);
                }
                else if (name.endsWith(".jar")) {
                    if (entries.size() >= 64) throw new IOException("Nested mod count exceeded");
                    entries.put(name, readNested(zip, inventory));
                } else readNested(zip, inventory); // Bound decompression even for entries we do not need.
            }
        }
        if (meta == null) return;
        collect(meta, inventory);
        for (String name : nested(meta)) {
            byte[] child = entries.get(name); if (child == null) throw new IOException("Missing nested mod");
            scanNested(child, inventory, depth + 1);
        }
    }
    private static List<String> nested(Map<String,Object> meta) throws IOException {
        var result = new ArrayList<String>();
        if (meta.get("jars") instanceof List<?> list) for (Object item : list) {
            String name = MiniJson.string(MiniJson.object(item).get("file"));
            if (name.isBlank()) throw new IOException("Invalid nested mod metadata"); result.add(name);
        }
        return result;
    }
    private static void collect(Map<String,Object> meta, Inventory inventory) throws IOException {
        if (++inventory.entries > 512) throw new IOException("Mod inspection count exceeded");
        String id = MiniJson.string(meta.get("id")); if (id.isBlank()) throw new IOException("Invalid active mod metadata");
        inventory.ids.add(id);
        if (meta.get("provides") instanceof List<?> list) for (Object provided : list) if (provided instanceof String value) inventory.ids.add(value);
        for (String key : List.of("breaks", "conflicts")) if (meta.get(key) instanceof Map<?,?> conflicts)
            for (Object other : conflicts.keySet()) if (other instanceof String value) inventory.blocked.add(value);
    }
    private static Map<String,Object> metadata(byte[] bytes) throws IOException {
        try { return MiniJson.object(MiniJson.parse(new String(bytes, StandardCharsets.UTF_8))); }
        catch (RuntimeException malformed) { throw new IOException("Invalid active mod metadata", malformed); }
    }
    private static byte[] readBounded(InputStream in, int limit) throws IOException {
        byte[] bytes = in.readNBytes(limit + 1); if (bytes.length > limit) throw new IOException("Mod inspection size exceeded"); return bytes;
    }
    private static byte[] readNested(InputStream in, Inventory inventory) throws IOException {
        byte[] bytes = readBounded(in, Math.min(MAX_NESTED, inventory.nestedBudget));
        inventory.nestedBudget -= bytes.length;
        return bytes;
    }
}
