package gg.kapkan.release;

import gg.kapkan.v23.KapKanH2Launcher;
import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import javax.swing.JOptionPane;

/** Native packaging entry point. H2 Swing UI backed by the preserved H1 launch engine. */
public final class ReleaseMain {
    private ReleaseMain() {}
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            System.err.println("KapKan: unhandled error on " + thread.getName());
            error.printStackTrace(System.err);
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(null,
                    "KapKan encountered a startup error.\nDetails: " +
                    Path.of(System.getProperty("user.home"), ".kapkan", "logs", "launcher.log"),
                    "KapKan", JOptionPane.ERROR_MESSAGE);
            }
        });
        System.out.println("KapKan " + System.getProperty("kapkan.release.version", "1.0.7") +
            " | " + System.getProperty("os.name") + " | " + System.getProperty("os.arch") +
            " | Java " + Runtime.version());
        KapKanH2Launcher.main(args);
    }
}
