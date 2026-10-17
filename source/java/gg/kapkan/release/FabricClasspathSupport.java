package gg.kapkan.release;

/** Cross-platform checks for Fabric entries in a fully assembled Java classpath. */
public final class FabricClasspathSupport {
    private FabricClasspathSupport() {}

    public static boolean matchesLoader(String path, String loaderVersion, String expectedJarName) {
        if (path == null || path.isBlank()) return false;
        String normalized = path.replace('\\', '/');
        if (!normalized.contains("/net/fabricmc/fabric-loader/")) return false;
        if (loaderVersion == null || loaderVersion.isBlank()) return normalized.endsWith(".jar");
        String expected = (expectedJarName == null || expectedJarName.isBlank())
            ? "fabric-loader-" + loaderVersion + ".jar"
            : expectedJarName;
        return normalized.endsWith("/" + expected) || normalized.endsWith(expected);
    }
}
