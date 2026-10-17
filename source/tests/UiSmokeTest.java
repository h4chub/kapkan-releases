import java.awt.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
/** Launches the REAL Swing app on Xvfb/Linux. It is not a macOS/Windows or Minecraft gameplay test. */
public class UiSmokeTest {
 public static void main(String[] args)throws Exception{
  gg.kapkan.release.ReleaseMain.main(new String[0]);
  Thread.sleep(3000);
  // Xvfb has no window manager/focus. Dismiss the existing intro through its own finish method.
  SwingUtilities.invokeAndWait(()->{
   for(Window w:Window.getWindows())if(w instanceof JFrame f){
    Component pane=f.getGlassPane();
    if(pane.getClass().getName().endsWith("$IntroOverlay"))try{var m=pane.getClass().getDeclaredMethod("finish");m.setAccessible(true);m.invoke(pane);}catch(Exception e){throw new RuntimeException(e);}
   }
  });
  Thread.sleep(6000);
  SwingUtilities.invokeAndWait(()->{
   int[] showing={0};
   for(Window w:Window.getWindows())if(w.isShowing()){
    showing[0]++;
    System.out.println("SHOWING "+w.getClass().getName()+" "+w.getWidth()+"x"+w.getHeight());
    if(w instanceof Frame f)System.out.println("TITLE "+f.getTitle());
   }
   for(Window w:Window.getWindows())if(w instanceof JFrame f&&f.isShowing()){
    if(f.getGlassPane().isVisible()&&f.getGlassPane().getClass().getName().endsWith("$IntroOverlay"))throw new AssertionError("intro still visible");
   }
   if(showing[0]==0)throw new AssertionError("The real launcher did not create a window");
  });
  var screen=new Robot().createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
  ImageIO.write(screen,"png",Path.of(args[0]).toFile());
  System.out.println("Linux real launcher UI smoke test: PASS (window created; no game launched)");
  System.exit(0);
 }
}
