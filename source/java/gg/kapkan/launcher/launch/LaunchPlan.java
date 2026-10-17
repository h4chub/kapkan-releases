package gg.kapkan.launcher.launch;

import java.nio.file.Path;
import java.util.List;

/** The installation cache and the per-profile game directory have separate lifetimes. */
public record LaunchPlan(List<String> command, Path workingDirectory, String launchVersion,
                         String baseVersion, String loaderLabel, Path javaExecutable,
                         int javaMajor, Path installationDirectory) {
    /** Retains binary compatibility with the existing engine and its callers. */
    public LaunchPlan(List<String> command, Path workingDirectory, String launchVersion,
                      String baseVersion, String loaderLabel, Path javaExecutable, int javaMajor) {
        this(command, workingDirectory, launchVersion, baseVersion, loaderLabel,
             javaExecutable, javaMajor, workingDirectory);
    }
}
