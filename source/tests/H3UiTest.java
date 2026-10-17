package gg.kapkan.v23;
import java.awt.*;
import java.awt.event.*;
import javax.accessibility.AccessibleState;
import java.nio.file.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Actual running Swing UI on the host display; no Minecraft/native-device claim. */
public final class H3UiTest {
    static int checks;
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);checks++;}
    static void screenshot(String name)throws Exception{
        SwingUtilities.invokeAndWait(()->KapKanH2Launcher.instance.a.frame.validate());Thread.sleep(300);
        Rectangle[] bounds={null};SwingUtilities.invokeAndWait(()->{
            var ui=KapKanH2Launcher.instance;var a=ui.a;bounds[0]=a.frame.getBounds();
            if(ui.accountMenu!=null&&ui.accountMenu.isShowing())bounds[0]=bounds[0].union(new Rectangle(ui.accountMenu.getLocationOnScreen(),ui.accountMenu.getSize()));
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
        screenshot("KapKan-H3-Play");
        Robot keyboard=new Robot();keyboard.setAutoDelay(60);
        SwingUtilities.invokeAndWait(()->{
            var ui=KapKanH2Launcher.instance;
            var original=ui.a.state.active;ui.a.state.addGuest("SecondPlayer");ui.useAccount(original);
            ui.profile.requestFocusInWindow();
        });Thread.sleep(150);
        keyboard.keyPress(KeyEvent.VK_DOWN);keyboard.keyRelease(KeyEvent.VK_DOWN);Thread.sleep(200);
        SwingUtilities.invokeAndWait(()->{
            var ui=KapKanH2Launcher.instance;
            check(ui.accountMenu.isShowing(),"Down opens account menu");
            check(ui.profile.getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.EXPANDED),"Expanded state exposed");
            check(LauncherPolish.keyboardFocus,"Keyboard focus is visible");
        });screenshot("KapKan-H3-Account-Menu");
        keyboard.keyPress(KeyEvent.VK_ESCAPE);keyboard.keyRelease(KeyEvent.VK_ESCAPE);Thread.sleep(150);
        SwingUtilities.invokeAndWait(()->{
            var ui=KapKanH2Launcher.instance;
            check(!ui.accountMenu.isVisible(),"Escape closes account menu");
            check(ui.profile.getAccessibleContext().getAccessibleStateSet().contains(AccessibleState.COLLAPSED),"Collapsed state exposed");
            check(ui.a.state.active.name().equals("Player"),"Escape preserves current account");
            ui.showAccountMenu();
        });
        keyboard.keyPress(KeyEvent.VK_DOWN);keyboard.keyRelease(KeyEvent.VK_DOWN);
        keyboard.keyPress(KeyEvent.VK_ENTER);keyboard.keyRelease(KeyEvent.VK_ENTER);Thread.sleep(200);
        SwingUtilities.invokeAndWait(()->{
            var ui=KapKanH2Launcher.instance;
            check(ui.a.state.active.name().equals("SecondPlayer"),"Keyboard selection switches actual active profile");
            check(ui.profile.getText().equals("SecondPlayer"),"Header name follows selection");
            check(((KapKanV23Launcher.AccountProfile)ui.a.accountBox.getSelectedItem()).name().equals("SecondPlayer"),"Play dropdown follows selection");
            check(ui.detail.getText().endsWith("SecondPlayer"),"Launch summary follows selection");
            var reloaded=new KapKanV23Launcher.State();
            check(reloaded.active.name().equals("SecondPlayer"),"Account choice persists");
            ui.busy(true);ui.showAccountMenu();check(!ui.profile.isEnabled()&&!ui.accountMenu.isShowing(),"Account switcher disabled during launch");ui.busy(false);
            ui.useAccount(ui.a.state.profiles.stream().filter(a->a.name().equals("Player")).findFirst().orElseThrow());
            ui.showAccountMenu();
            ((JMenuItem)ui.accountMenu.getComponent(ui.accountMenu.getComponentCount()-1)).doClick();ui.accountMenu.setVisible(false);
            check(ui.currentPage.equals("Accounts"),"Manage accounts opens retained page");ui.select("Play");
            LauncherPolish.keyboardFocus=false;
        });
        SwingUtilities.invokeAndWait(()->KapKanH2Launcher.instance.a.versionBox.showPopup());screenshot("KapKan-H3-Version-Menu");
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
            var play=ui.a.playButton;
            check(play.getWidth()>=126,"Play label retains space at minimum window width");
            ui.a.state.addGuest("SixteenLetterTag");ui.refreshAccounts();
            ui.a.frame.validate();
            Rectangle header=SwingUtilities.convertRectangle(ui.profile.getParent(),ui.profile.getBounds(),f.getContentPane());
            check(header.x+header.width<=f.getContentPane().getWidth(),"Long account name stays inside small window");
            ui.showAccountMenu();Rectangle menu=new Rectangle(ui.accountMenu.getLocationOnScreen(),ui.accountMenu.getSize());
            check(ui.a.frame.getGraphicsConfiguration().getBounds().contains(menu),"Account menu remains on screen");ui.accountMenu.setVisible(false);
            ui.useAccount(ui.a.state.profiles.stream().filter(a->a.name().equals("Player")).findFirst().orElseThrow());
            ui.select("Settings");check(ui.badgeState.getText().equals("Service not configured"),"Honest no-service state");
        });screenshot("KapKan-H3-Settings-Small");
        SwingUtilities.invokeAndWait(()->KapKanH2Launcher.instance.select("Play"));screenshot("KapKan-H3-Play-Small");
        SwingUtilities.invokeAndWait(()->{var ui=KapKanH2Launcher.instance;ui.a.frame.setSize(1180,760);ui.a.frame.setLocationRelativeTo(null);ui.select("Accounts");});screenshot("KapKan-H3-Accounts");
        SwingUtilities.invokeAndWait(()->{
            var ui=KapKanH2Launcher.instance;ui.select("Play");
            for(int i=0;i<10;i++)ui.a.state.addGuest("Profile"+i);ui.refreshAccounts();
            ui.showAccountMenu();long rows=java.util.Arrays.stream(ui.accountMenu.getComponents()).filter(c->c instanceof LauncherPolish.AccountItem).count();
            check(rows==6,"Account menu bounded to six profiles with remaining profiles in Manage accounts");ui.accountMenu.setVisible(false);
        });
        System.out.println("Real Swing H3 UI: "+checks+" checks passed. Linux virtual display; no Mac/Windows gameplay claim.");System.exit(0);
    }
}
