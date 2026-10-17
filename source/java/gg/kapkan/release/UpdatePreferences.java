package gg.kapkan.release;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;

/** Separate from game settings and account state. */
public final class UpdatePreferences {
    private final Path file;
    private boolean automatic = true;
    public UpdatePreferences() { this(Path.of(System.getProperty("user.home"), ".kapkan", "updates.properties")); }
    public UpdatePreferences(Path file) {
        this.file = file;
        try (var input = Files.newInputStream(file)) {
            Properties p = new Properties(); p.load(input);
            automatic = !"false".equals(p.getProperty("automatic"));
        } catch (IOException | IllegalArgumentException ignored) {}
    }
    public boolean automatic() { return automatic; }
    public void automatic(boolean enabled) throws IOException {
        Files.createDirectories(file.getParent());
        Path temp = Files.createTempFile(file.getParent(), "updates-", ".tmp");
        try {
            Properties p = new Properties(); p.setProperty("automatic", Boolean.toString(enabled));
            try (var out = Files.newOutputStream(temp)) { p.store(out, "KapKan update notices"); }
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
            automatic = enabled;
        } finally { Files.deleteIfExists(temp); }
    }
}
