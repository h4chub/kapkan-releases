package gg.kapkan.v23;

import gg.kapkan.release.LaunchProgress;
import java.awt.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Actual Swing rendering with an explicitly simulated progress sample, in an isolated profile. */
public final class DownloadProgressUiTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        try{
            KapKanH2Launcher.main(args);Thread.sleep(1200);
            SwingUtilities.invokeAndWait(()->{
                var ui=KapKanH2Launcher.instance;ui.a.versionBox.setModel(new DefaultComboBoxModel<>(new String[]{"26.1.2"}));
                ui.preparing=true;ui.busy(true);ui.a.playButton.setText("Preparing");
                ui.showDownloadProgress(new LaunchProgress.Snapshot("Minecraft assets",123_000_000,400_000_000,1_400_000,198,1250,4750,true));
                ui.a.frame.validate();
                check(ui.progress.isVisible()&&!ui.progress.isIndeterminate()&&ui.progress.getValue()==30,"byte-based aggregate progress displayed");
                check(ui.detail.getText().contains("1.4 MB/s")&&ui.detail.getText().contains("~4m left"),"measured speed and approximate ETA visible");
                check(ui.progress.getAccessibleContext().getAccessibleDescription().contains("Minecraft assets"),"accessible download description");
            });
            for(int width:new int[]{1100,820}){
                SwingUtilities.invokeAndWait(()->{var frame=KapKanH2Launcher.instance.a.frame;frame.setSize(width,width==820?560:740);frame.validate();});
                Thread.sleep(350);
                SwingUtilities.invokeAndWait(()->{
                    var ui=KapKanH2Launcher.instance;
                    check(ui.detail.getFontMetrics(ui.detail.getFont()).stringWidth(ui.detail.getText())<=ui.detail.getWidth(),"progress text fits "+width);
                    check(ui.state.getFontMetrics(ui.state.getFont()).stringWidth(ui.state.getText())<=ui.state.getWidth(),"stage fits "+width);
                });
                Rectangle[] bounds={null};SwingUtilities.invokeAndWait(()->bounds[0]=KapKanH2Launcher.instance.a.frame.getBounds());
                var images=new Robot().createMultiResolutionScreenCapture(bounds[0]).getResolutionVariants();
                ImageIO.write((java.awt.image.BufferedImage)images.getLast(),"png",Path.of(System.getProperty("kapkan.qa.out"),"KapKan-download-progress-"+width+".png").toFile());
            }
            SwingUtilities.invokeAndWait(()->{
                var ui=KapKanH2Launcher.instance;
                ui.showDownloadProgress(new LaunchProgress.Snapshot("Minecraft assets",0,400_000_000,0,-1,0,4750,true));
                check(ui.detail.getText().contains("Estimating time")&&!ui.detail.getText().contains("left"),"unknown ETA never shown as zero");
                ui.showDownloadProgress(new LaunchProgress.Snapshot("Downloading runtime",1024,-1,0,-1,-1,-1,true));
                check(ui.progress.isIndeterminate(),"unknown content length stays indeterminate");
                ui.preparing=false;String previous=ui.detail.getText();
                ui.showDownloadProgress(new LaunchProgress.Snapshot("late callback",0,100,0,-1,0,1,true));
                check(ui.detail.getText().equals(previous),"late callback cannot overwrite final launch status");
            });
            System.out.println("Download progress Swing UI: PASS ("+checks+" assertions; sample data, Linux rendering)");System.exit(0);
        }catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
}
