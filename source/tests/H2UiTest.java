package gg.kapkan.v23;
import java.awt.*;
import java.nio.file.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Actual running Swing UI on the host display; no Minecraft/native-device claim. */
public final class H2UiTest {
    static int checks;
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);checks++;}
    static void screenshot(String name)throws Exception{
        SwingUtilities.invokeAndWait(()->KapKanH2Launcher.instance.a.frame.validate());Thread.sleep(300);
        Rectangle[] bounds={null};SwingUtilities.invokeAndWait(()->{
            var a=KapKanH2Launcher.instance.a;bounds[0]=a.frame.getBounds();
            for(var box:new JComboBox<?>[]{a.versionBox,a.loaderBox,a.accountBox})if(box.isPopupVisible()){
                var popup=(javax.swing.plaf.basic.BasicComboPopup)box.getUI().getAccessibleChild(box,0);
                bounds[0]=bounds[0].union(new Rectangle(popup.getLocationOnScreen(),popup.getSize()));
            }
        });
        ImageIO.write(new Robot().createScreenCapture(bounds[0]),"png",Path.of(System.getProperty("kapkan.qa.out"),name+".png").toFile());
    }
    public static void main(String[] args)throws Exception{
        KapKanH2Launcher.main(args);Thread.sleep(2000);
        SwingUtilities.invokeAndWait(()->{
            var ui=KapKanH2Launcher.instance;check(ui!=null,"UI created");
            ui.a.versionBox.setModel(new DefaultComboBoxModel<>(new String[]{"26.1.2","26.1.1","26.1","1.21.11","1.21.10","1.21.9","1.21.8","1.21.7","1.21.6","1.21.5","1.21.4"}));
            ui.a.loaderBox.setSelectedItem("Fabric");
            check(ui.nav.size()==5,"Five pages");check(!ui.nav.containsKey("Servers"),"No Servers page");
            check(ui.a.accountBox.getAccessibleContext().getAccessibleName().equals("Minecraft account"),"Accessible account label");
            check(ui.a.playButton.getActionListeners().length==1,"Single Play handler");
            check(ui.a.playButton.isEnabled(),"Ready Play enabled");
        });
        screenshot("KapKan-H2-Play");
        SwingUtilities.invokeAndWait(()->KapKanH2Launcher.instance.a.versionBox.showPopup());screenshot("KapKan-H2-Version-Menu");
        SwingUtilities.invokeAndWait(()->{var box=KapKanH2Launcher.instance.a.versionBox;var pop=(javax.swing.plaf.basic.BasicComboPopup)box.getUI().getAccessibleChild(box,0);int selected=box.getSelectedIndex();check(selected>=pop.getList().getFirstVisibleIndex()&&selected<=pop.getList().getLastVisibleIndex(),"Selected version visible in open menu");});
        SwingUtilities.invokeAndWait(()->{var ui=KapKanH2Launcher.instance;ui.a.versionBox.hidePopup();ui.a.frame.setSize(820,560);ui.a.frame.setLocation(20,20);});
        Thread.sleep(150);
        SwingUtilities.invokeAndWait(()->{
            var ui=KapKanH2Launcher.instance;var f=ui.a.frame;
            for(Component c:new Component[]{ui.a.versionBox,ui.a.loaderBox,ui.a.accountBox,ui.a.playButton,ui.profile}){
                Rectangle b=SwingUtilities.convertRectangle(c.getParent(),c.getBounds(),f.getContentPane());
                check(b.x>=0&&b.y>=0&&b.x+b.width<=f.getContentPane().getWidth()&&b.y+b.height<=f.getContentPane().getHeight(),"Visible at 820x560: "+c.getClass().getName());
                check(c.getHeight()>=38,"Usable control height");
            }
            ui.select("Settings");check(ui.badgeState.getText().equals("Service not configured"),"Honest no-service state");
        });screenshot("KapKan-H2-Settings-Small");
        SwingUtilities.invokeAndWait(()->KapKanH2Launcher.instance.select("Play"));screenshot("KapKan-H2-Play-Small");
        SwingUtilities.invokeAndWait(()->{var ui=KapKanH2Launcher.instance;ui.a.frame.setSize(1180,760);ui.a.frame.setLocationRelativeTo(null);ui.select("Accounts");});screenshot("KapKan-H2-Accounts");
        System.out.println("Real Swing H2 UI: "+checks+" checks passed. Linux virtual display; no Mac/Windows gameplay claim.");System.exit(0);
    }
}
