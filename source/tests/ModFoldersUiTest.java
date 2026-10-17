package gg.kapkan.v23;
import java.awt.*;
import java.nio.file.*;
import javax.swing.*;
import javax.imageio.ImageIO;

/** Runs the actual Swing launcher against an isolated temporary user home. */
public final class ModFoldersUiTest {
    static int checks;static KapKanH2Launcher ui;static Path out;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    interface Condition{boolean ready();}
    static void await(Condition condition)throws Exception{long end=System.nanoTime()+8_000_000_000L;while(System.nanoTime()<end){boolean[] ok={false};SwingUtilities.invokeAndWait(()->ok[0]=condition.ready());if(ok[0])return;Thread.sleep(40);}throw new AssertionError("UI did not reach the expected state");}
    static void ed(Runnable task)throws Exception{SwingUtilities.invokeAndWait(task);}
    static JFileChooser chooser(Container c){for(Component item:c.getComponents()){if(item instanceof JFileChooser f)return f;if(item instanceof Container child){var f=chooser(child);if(f!=null)return f;}}return null;}
    static void screenshot(String filename)throws Exception{ed(()->ui.a.frame.validate());Thread.sleep(100);ImageIO.write(new Robot().createScreenCapture(ui.a.frame.getBounds()),"png",out.resolve(filename).toFile());}
    static void select(String version,String loader)throws Exception{ed(()->{ui.a.versionBox.setSelectedItem(version);ui.a.loaderBox.setSelectedItem(loader);ui.select("Mods");});}
    public static void main(String[] args)throws Exception{
        try{run(args);}catch(Throwable e){e.printStackTrace();System.exit(1);}
    }
    static void run(String[] args)throws Exception{
        out=Path.of(System.getProperty("kapkan.qa.out"));Files.createDirectories(out);
        KapKanH2Launcher.main(new String[0]);await(()->KapKanH2Launcher.instance!=null&&KapKanH2Launcher.instance.a.frame.isVisible());ui=KapKanH2Launcher.instance;
        ed(()->{ui.a.versionBox.setModel(new DefaultComboBoxModel<>(new String[]{"26.1.2","1.21.11"}));ui.a.loaderBox.setSelectedItem("Fabric");});
        var current=ui.content.modProfiles.resolve("26.1.2","Fabric");Files.writeString(current.mods().resolve("Fabric-current.jar"),"fixture");
        var older=ui.content.modProfiles.resolve("1.21.11","Fabric");Files.writeString(older.mods().resolve("Fabric-older.jar"),"fixture");
        select("26.1.2","Fabric");await(()->ui.mods.contains("Fabric-current.jar"));
        ed(()->{check(!ui.mods.contains("Fabric-older.jar"),"Mods page isolates versions");check(ui.content.modProfile.getText().equals("26.1.2 · Fabric"),"Selected profile is visible");check(ui.content.modFolder.getToolTipText().equals(current.mods().toString()),"Exact folder is available in the tooltip");check(!ui.content.defaultFolder.isVisible(),"Reset is hidden for default folders");});
        select("1.21.11","Fabric");await(()->ui.mods.contains("Fabric-older.jar"));ed(()->check(!ui.mods.contains("Fabric-current.jar"),"Switching versions refreshes the actual list"));
        select("26.1.2","Forge");await(()->ui.content.modSelection!=null&&ui.content.modSelection.loader().equals("Forge"));ed(()->check(ui.mods.isEmpty(),"Same version with another loader has its own collection"));
        select("26.1.2","Fabric");await(()->ui.mods.contains("Fabric-current.jar"));
        Path custom=Files.createTempDirectory("kapkan-custom-ui-");Files.writeString(custom.resolve("My-custom-mod.jar"),"custom");
        SwingUtilities.invokeLater(()->ui.content.chooseFolder.doClick());
        await(()->{for(Window w:Window.getWindows())if(w.isShowing()&&chooser(w)!=null)return true;return false;});
        ed(()->{JFileChooser selected=null;for(Window w:Window.getWindows())if(w.isShowing()&&chooser(w)!=null)selected=chooser(w);check(selected!=null,"Choose folder opens a real chooser");check(selected.getFileSelectionMode()==JFileChooser.DIRECTORIES_ONLY,"Chooser accepts folders only");selected.setSelectedFile(custom.toFile());selected.approveSelection();});
        await(()->ui.mods.contains("My-custom-mod.jar"));
        ed(()->{check(!ui.mods.contains("Fabric-current.jar"),"Custom choice updates the visible collection");check(ui.content.defaultFolder.isVisible(),"Use default becomes available");check(ui.content.modSelection.custom(),"Custom mapping saved through the real chooser");});
        screenshot("KapKan-1.0.4-Custom-Mods.png");
        ed(()->ui.content.defaultFolder.doClick());await(()->ui.mods.contains("Fabric-current.jar"));
        ed(()->{check(Files.exists(custom.resolve("My-custom-mod.jar")),"Reset preserves custom files");check(!ui.content.modSelection.custom(),"Reset restores default mapping");ui.preparing=true;ui.busy(true);check(!ui.content.chooseFolder.isEnabled(),"Folder changes disabled while preparing");check(!ui.content.defaultFolder.isEnabled(),"Reset disabled while preparing");ui.preparing=false;ui.a.running=true;ui.busy(true);check(!ui.content.chooseFolder.isEnabled(),"Folder changes disabled during game");ui.a.running=false;ui.busy(false);check(ui.content.chooseFolder.isEnabled(),"Folder controls restored after game closes");});
        ed(()->ui.content.modSearch.setText("does-not-exist"));await(()->ui.mods.isEmpty());ed(()->check(ui.content.emptyTitle.getText().equals("No matching mods"),"Search gives clear empty feedback"));ed(()->ui.content.modSearch.setText(""));
        await(()->ui.mods.contains("Fabric-current.jar"));
        for(int width:new int[]{820,1180}){
            ed(()->{ui.a.frame.setSize(width,width==820?560:760);ui.a.frame.validate();});
            screenshot("KapKan-1.0.4-Mods-"+width+".png");
            ed(()->{var b=ui.content.chooseFolder;check(b.isShowing()&&b.getWidth()>=b.getPreferredSize().width,"Folder button fits at "+width);var label=ui.content.modProfile;check(label.getWidth()>=label.getPreferredSize().width,"Full profile label fits at "+width);});
        }
        ed(()->{ui.select("Play");check(ui.a.playButton.isEnabled(),"Play remains available");ui.select("Settings");check(ui.currentPage.equals("Settings"),"Settings remain accessible");ui.a.frame.dispose();});
        System.out.println("Mod folders UI: PASS ("+checks+" checks, scale "+System.getProperty("sun.java2d.uiScale")+")");System.exit(0);
    }
}
