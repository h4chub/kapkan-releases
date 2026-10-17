package gg.kapkan.v23;

import static gg.kapkan.v23.KapKanH2Launcher.*;
import gg.kapkan.release.UpdateChecker;
import gg.kapkan.release.UpdatePreferences;
import java.awt.*;
import java.net.URI;
import java.util.concurrent.Callable;
import javax.swing.*;
import javax.swing.border.EmptyBorder;

/** A dismissible notice, not a modal dialog or an automatic installer. All UI state is EDT-owned. */
final class LauncherUpdates {
    final KapKanH2Launcher ui;
    final UpdatePreferences preferences;
    final JPanel banner = panel(new BorderLayout());
    final JLabel bannerDetail = label("", Font.PLAIN, 12, MUTED);
    final JLabel status = label("KapKan " + UpdateChecker.VERSION + " · Public Beta", Font.PLAIN, 12, MUTED);
    final JButton checkButton = button("Check for updates", false, () -> checkNow(true));
    final JButton downloadButton = button("Download update", true, this::download);
    final JButton noticeDownload = button("Download update", true, this::download);
    final JButton laterButton = button("Later", false, this::dismiss);
    Callable<UpdateChecker.Result> source = new UpdateChecker()::check;
    BrowserOpener opener = uri -> {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
            throw new IllegalStateException("No browser available");
        Desktop.getDesktop().browse(uri);
    };
    UpdateChecker.Release available;
    boolean checking, dismissed, opening;
    interface BrowserOpener { void open(URI uri) throws Exception; }

    LauncherUpdates(KapKanH2Launcher ui) {
        this.ui = ui; preferences = new UpdatePreferences();
        JPanel surface = new LauncherPolish.Surface(new BorderLayout(14, 8));
        surface.setBorder(new EmptyBorder(14, 16, 14, 16));
        JPanel words = column(); words.add(label("Update available", Font.BOLD, 14, TEXT));
        words.add(Box.createVerticalStrut(4)); words.add(bannerDetail); surface.add(words);
        JPanel buttons = panel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(noticeDownload); buttons.add(laterButton); surface.add(buttons, BorderLayout.EAST);
        banner.setBorder(new EmptyBorder(16, 0, 0, 0)); banner.add(surface); banner.setVisible(false);
        banner.getAccessibleContext().setAccessibleName("KapKan update available");
        downloadButton.setVisible(false);
    }
    JPanel settings() {
        JPanel card = LauncherPages.card("Launcher updates", "Get a notice when a new KapKan version is ready.");
        JCheckBox automatic = KapKanH2Launcher.check("Check for updates when KapKan opens", preferences.automatic());
        automatic.addActionListener(e -> {
            try { preferences.automatic(automatic.isSelected()); }
            catch (Exception error) { automatic.setSelected(preferences.automatic()); status.setText("Couldn't save update preference. Try again."); }
        });
        card.add(automatic); card.add(Box.createVerticalStrut(6)); card.add(status);
        card.add(Box.createVerticalStrut(14)); card.add(LauncherPages.actions(checkButton, downloadButton));
        card.add(note("Downloads open in your browser. Close KapKan and Minecraft before installing. Your game folder stays in place."));
        return card;
    }
    void start() { if (preferences.automatic()) checkNow(false); }
    void checkNow(boolean manual) {
        if (checking) return;
        checking = true; checkButton.setEnabled(false); status.setText("Checking for updates…");
        new SwingWorker<UpdateChecker.Result, Void>() {
            protected UpdateChecker.Result doInBackground() throws Exception { return source.call(); }
            protected void done() {
                checking = false; checkButton.setEnabled(true);
                if (!ui.a.frame.isDisplayable()) return;
                try { accept(get(), manual); }
                catch (Exception error) {
                    status.setText(available == null ? "Couldn't check for updates. Try again when you're online."
                        : "New check failed. The previously found update is still available.");
                    // An offline update service must never open the launch log or stop Minecraft.
                }
            }
        }.execute();
    }
    void accept(UpdateChecker.Result result, boolean manual) {
        available = result.update().orElse(null);
        downloadButton.setVisible(available != null);
        if (available == null) {
            status.setText(result.platform() == UpdateChecker.Platform.UNSUPPORTED ? "No installer is published for this platform."
                : "You're up to date · KapKan " + UpdateChecker.VERSION);
            banner.setVisible(false);
        } else {
            if (manual) dismissed = false;
            String version = "KapKan " + available.version() + (available.beta() ? " · Public Beta" : "");
            status.setText(version + " is available."); bannerDetail.setText(version);
            noticeDownload.getAccessibleContext().setAccessibleDescription("Download " + available.filename() + " from the official KapKan release.");
            showPending();
        }
        ui.root.revalidate(); ui.root.repaint();
    }
    void showPending() {
        banner.setVisible(available != null && !dismissed && !ui.preparing && !ui.a.running);
        ui.root.revalidate();
    }
    void dismiss() { dismissed = true; banner.setVisible(false); ui.root.revalidate(); ui.root.repaint(); }
    void download() {
        if (available == null || opening) return;
        UpdateChecker.Release release = available;
        opening = true; downloadButton.setEnabled(false); noticeDownload.setEnabled(false);
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { opener.open(release.download()); return null; }
            protected void done() {
                opening = false; downloadButton.setEnabled(true); noticeDownload.setEnabled(true);
                try {
                    get(); status.setText("Download opened. Close KapKan and Minecraft before installing.");
                    bannerDetail.setText("Close KapKan and Minecraft before installing.");
                } catch (Exception error) {
                    status.setText("Couldn't open your browser. Open the official release below.");
                    JTextField link = new JTextField(release.page().toString()); textField(link); link.setEditable(false);
                    link.getAccessibleContext().setAccessibleName("Official KapKan update page");
                    JPanel help = column(); help.add(note("Copy this link into your browser to download the update.")); help.add(link);
                    JOptionPane.showMessageDialog(ui.a.frame, help, "Download KapKan", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        }.execute();
    }
}
