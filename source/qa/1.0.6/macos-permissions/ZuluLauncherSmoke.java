import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Native runtime acceptance only; paints our own window and never captures the desktop. */
public final class ZuluLauncherSmoke {
    static JFrame launcher;
    static JButton button(Container parent, String text) {
        for (Component component: parent.getComponents()) {
            if (component instanceof JButton found && text.equals(found.getText())) return found;
            if (component instanceof Container child) {
                JButton found=button(child,text); if (found!=null) return found;
            }
        }
        return null;
    }
    static JCheckBox checkbox(Container parent,String text) {
        for (Component component:parent.getComponents()) {
            if (component instanceof JCheckBox found && text.equals(found.getText())) return found;
            if (component instanceof Container child) {
                JCheckBox found=checkbox(child,text); if (found!=null) return found;
            }
        }
        return null;
    }
    static void paint(Path target) throws Exception {
        SwingUtilities.invokeAndWait(()->{
            try {
                BufferedImage image=new BufferedImage(launcher.getWidth(),launcher.getHeight(),BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics=image.createGraphics();launcher.paintAll(graphics);graphics.dispose();
                if (!ImageIO.write(image,"png",target.toFile())) throw new IllegalStateException("PNG writer missing");
            } catch(Exception error) { throw new RuntimeException(error); }
        });
    }
    public static void main(String[] arguments) {
        try {
            Path output=Path.of(arguments[0]);Files.createDirectories(output);
            gg.kapkan.release.ReleaseMain.main(new String[0]);
            long deadline=System.currentTimeMillis()+30000;
            while (launcher==null && System.currentTimeMillis()<deadline) {
                SwingUtilities.invokeAndWait(()->{
                    for (Window window:Window.getWindows()) if (window instanceof JFrame frame && frame.isShowing() && frame.getTitle().equals("KapKan 1.0.6")) launcher=frame;
                });
                if (launcher==null) Thread.sleep(150);
            }
            if (launcher==null) throw new AssertionError("Launcher frame was not created");
            Thread.sleep(1500);
            SwingUtilities.invokeAndWait(()->{
                if (button(launcher,"Play")==null || button(launcher,"Accounts")==null || button(launcher,"Settings")==null) throw new AssertionError("Expected launcher controls are absent");
            });
            paint(output.resolve("zulu21-launcher-play.png"));
            SwingUtilities.invokeAndWait(()->button(launcher,"Settings").doClick());
            Thread.sleep(250);
            SwingUtilities.invokeAndWait(()->{
                JCheckBox setting=checkbox(launcher,"Use rendering optimizations");
                if (setting==null) throw new AssertionError("Game performance setting is absent");
                setting.scrollRectToVisible(new Rectangle(0,0,setting.getWidth(),setting.getHeight()));
            });
            Thread.sleep(200);
            SwingUtilities.invokeAndWait(()->{
                JCheckBox setting=checkbox(launcher,"Use rendering optimizations");
                if (!setting.isShowing() || setting.getVisibleRect().isEmpty()) throw new AssertionError("Game performance setting is not visible after scrolling");
            });
            paint(output.resolve("zulu21-launcher-settings.png"));
            System.out.println("PASS: native Java "+Runtime.version()+" launcher Swing UI, Play/Accounts/Settings controls and Use rendering optimizations setting");
            SwingUtilities.invokeAndWait(()->{for(Window window:Window.getWindows())window.dispose();});
            System.exit(0);
        } catch(Throwable error) {
            error.printStackTrace();System.exit(1);
        }
    }
}
