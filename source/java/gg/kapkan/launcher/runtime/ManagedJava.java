package gg.kapkan.launcher.runtime;

import gg.kapkan.launcher.launch.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Prepare the game's required runtime lazily, after resolving its version profile. */
public final class ManagedJava {
    private ManagedJava() {}
    public static JavaLocator.JavaRuntime find(MinecraftInstallation installation, int required, VersionProfile profile) {
        String helper = System.getProperty("kapkan.nativeBootstrap", "");
        if (helper.isBlank()) throw new IllegalStateException("Open KapKan.app or KapKan.exe, not the internal launcher JAR.");
        String arch = targetArch(System.getProperty("os.name", ""), System.getProperty("os.arch", ""), profile);
        Path result = null;
        try {
            Path tempDir = Path.of(System.getProperty("user.home"), ".kapkan", "runtime-requests");
            Files.createDirectories(tempDir);
            result = Files.createTempFile(tempDir, "java-", ".json");
            Path nativeLog = Path.of(System.getProperty("user.home"), ".kapkan", "logs", "runtime-prepare.log");
            Files.createDirectories(nativeLog.getParent());
            System.out.println("KapKan: preparing Java " + required + " / " + arch + " for " + profile.id());
            Process p = new ProcessBuilder(helper, "--prepare-java", "--major", Integer.toString(required),
                "--arch", arch, "--result", result.toString())
                .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(nativeLog.toFile())).start();
            if (!p.waitFor(20, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                throw new IOException("Java setup timed out. See " + nativeLog);
            }
            if (p.exitValue() != 0) throw new IOException("Java " + required + " setup failed. See " + nativeLog);
            Map<String,Object> map = MiniJson.object(MiniJson.parse(Files.readString(result, StandardCharsets.UTF_8)));
            Object value = map.get("java");
            if (!(value instanceof String text) || text.isBlank()) throw new IOException("Java setup returned no runtime path");
            Path java = Path.of(text);
            if (!Files.isRegularFile(java)) throw new IOException("Prepared Java executable is missing");
            return new JavaLocator.JavaRuntime(java, required, "KapKan managed Java (" + arch + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Java setup was interrupted", e);
        } catch (IOException e) {
            throw new IllegalStateException(e.getMessage(), e);
        } finally {
            if (result != null) try { Files.deleteIfExists(result); } catch (IOException ignored) {}
        }
    }
    public static String targetArch(String os, String architecture, VersionProfile profile) {
        String a = normalizeArch(architecture);
        // Pre-Apple-Silicon LWJGL versions need an x64 VM/Rosetta on ARM Macs.
        // Newer Minecraft versions and MacBook Neo use native ARM64; no M-series name check.
        if (os.toLowerCase(Locale.ROOT).contains("mac") && a.equals("aarch64")) {
            boolean intel = false, arm = false;
            for (Object obj : profile.libraries()) {
                Map<String,Object> lib = MiniJson.object(obj);
                String name = Objects.toString(lib.get("name"), "");
                if (!name.startsWith("org.lwjgl:")) continue;
                String metadata = lib.toString().toLowerCase(Locale.ROOT);
                if (metadata.contains("natives-macos-arm64") || metadata.contains("natives-osx-arm64")) arm = true;
                if (metadata.contains("natives-macos") || metadata.contains("natives-osx")) intel = true;
            }
            if (intel && !arm) return "x64";
        }
        return a;
    }
    public static String normalizeArch(String a) {
        return switch (a.toLowerCase(Locale.ROOT)) {
            case "aarch64", "arm64" -> "aarch64";
            case "amd64", "x86_64", "x64" -> "x64";
            default -> throw new IllegalArgumentException("Unsupported 64-bit architecture: " + a);
        };
    }
}
