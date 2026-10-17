package gg.kapkan.qa105;

import com.mojang.blaze3d.platform.InputConstants;
import gg.kapkan.presence.cosmetics.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.input.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TEST ONLY. Drives shipped native widgets in an actual Minecraft process; never packaged for users. */
@Mixin(Minecraft.class)
public abstract class UiInteractionProbe {
    private static int stage,wait,checks;
    private static boolean failed;
    private static Path marker(Minecraft mc,String name){return mc.gameDirectory.toPath().resolve(name);}
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("KAPKAN105_UI_PASS "+label);}
    private static void next(){stage++;wait=0;}
    private static KapkanScreen menu(Minecraft mc){check(mc.screen instanceof KapkanScreen,"actual KapKan screen is active");return (KapkanScreen)mc.screen;}
    private static List<MenuButton> rowButtons(KapkanScreen menu){return menu.children().stream().filter(c->c instanceof MenuButton).map(c->(MenuButton)c).filter(b->b.getX()==144&&b.getWidth()==472&&b.getY()>=121&&b.getY()<325).toList();}
    private static String firstRow(KapkanScreen menu){return rowButtons(menu).getFirst().getMessage().getString();}
    private static EditBox search(KapkanScreen menu){return menu.children().stream().filter(c->c instanceof EditBox).map(c->(EditBox)c).findFirst().orElseThrow();}
    private static MenuButton at(KapkanScreen menu,int x,int y){return menu.children().stream().filter(c->c instanceof MenuButton).map(c->(MenuButton)c).filter(b->b.getX()==x&&b.getY()==y).findFirst().orElseThrow();}
    private static MenuButton named(KapkanScreen menu,String label){return menu.children().stream().filter(c->c instanceof MenuButton).map(c->(MenuButton)c).filter(b->b.getMessage().getString().equals(label)||b.getMessage().getString().startsWith(label+",")).findFirst().orElseThrow();}
    private static MouseButtonEvent mouse(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));}
    private static void click(Minecraft mc,MenuButton b){KapkanScreen menu=menu(mc);MenuLayout layout=MenuLayout.fit(menu.width,menu.height,mc.getWindow().getGuiScale());double x=layout.x()+(b.getX()+b.getWidth()/2.0)*layout.scale(),y=layout.y()+(b.getY()+b.getHeight()/2.0)*layout.scale();check(menu.mouseClicked(mouse(x,y),false),"native mouse click reaches "+b.getMessage().getString());menu.mouseReleased(mouse(x,y));}
    private static void screenshot(Minecraft mc,String label){Screenshot.grab(mc.gameDirectory,"KapKan-1.0.5-ui-"+label+"-actual.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));}

    @Inject(method="tick",at=@At("TAIL"))
    private void qa105$ui(CallbackInfo ci){
        if(failed||stage>=99)return;Minecraft mc=(Minecraft)(Object)this;wait++;
        try{
            if(Boolean.getBoolean("kapkan.qa.restart")){
                if(mc.getOverlay()!=null||wait<80)return;
                check(ClientFeatures.settings().onboardingShown,"restart retains dismissed onboarding state");
                check(!(mc.screen instanceof IntroductionScreen),"restart does not automatically repeat introduction");
                check(ClientFeatures.menuKey.saveString().equals("key.keyboard.k"),"native options loader restores rebinding to K after process restart");
                boolean expected=Boolean.parseBoolean(Files.readString(marker(mc,"ui-expected-fps.txt")).trim());
                check(ClientFeatures.settings().fps==expected,"native settings load retains UI toggle after process restart");
                ClientFeatures.menuKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(74));KeyMapping.resetMapping();mc.options.save();
                check(ClientFeatures.menuKey.saveString().equals("key.keyboard.j"),"QA restores default J after proving persisted K");
                Files.writeString(marker(mc,"ui-restart-passed.txt"),"Actual UI process-restart checks: "+checks+" assertions passed\n");stage=99;return;
            }
            if(stage==0){
                if(mc.screen instanceof IntroductionScreen){check(ClientFeatures.settings().onboardingShown,"first introduction already persisted dismissal");mc.screen.onClose();return;}
                if(mc.player==null&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null&&wait>40){ServerData data=new ServerData("KapKan UI QA","127.0.0.1:"+Integer.getInteger("kapkan.qa.serverPort",25578),ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(data.ip),data,false,null);next();}return;
            }
            if(mc.player==null||mc.level==null)return;
            if(stage==1){if(wait<80)return;check(ClientFeatures.menuKey.saveString().equals("key.keyboard.j"),"native menu mapping starts at J");Files.writeString(marker(mc,"joined-ready.txt"),"Actual UI client joined loopback server\n");mc.setScreen(null);KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(74));next();return;}
            if(stage==2){
                if(wait<10)return;KapkanScreen menu=menu(mc);check(rowButtons(menu).size()==4&&firstRow(menu).startsWith("FPS"),"actual feature list renders its first four rows");
                boolean previous=ClientFeatures.settings().fps;click(mc,rowButtons(menu).getFirst());check(ClientFeatures.settings().fps!=previous,"actual FPS toggle changes state");check(new ClientSettings(ClientFeatures.settings().file).fps==!previous,"actual toggle saves immediately to disk");Files.writeString(marker(mc,"ui-expected-fps.txt"),Boolean.toString(!previous));
                EditBox search=search(menu);menu.setFocused(search);search.setFocused(true);for(int cp:"armor".codePoints().toArray())check(menu.charTyped(new CharacterEvent(cp)),"native text input accepts search character "+(char)cp);next();return;
            }
            if(stage==3){
                if(wait<10)return;KapkanScreen menu=menu(mc);check(search(menu).getValue().equals("armor"),"search retains complete typed query through widget refresh");check(rowButtons(menu).size()==1&&firstRow(menu).startsWith("Armor durability"),"typed search filters actual feature widgets");screenshot(mc,"search-armor");
                check(menu.keyPressed(new KeyEvent(65,0,2)),"native Ctrl+A selects search text");check(menu.keyPressed(new KeyEvent(259,0,0)),"native Backspace clears selected search text");next();return;
            }
            if(stage==4){
                if(wait<10)return;KapkanScreen menu=menu(mc);check(search(menu).getValue().isEmpty(),"native search editing clears query");click(mc,named(menu,"Movement"));check(rowButtons(menu).size()==2&&firstRow(menu).startsWith("Smooth zoom"),"Movement category filters to actual movement features");click(mc,named(menu,"HUD"));check(rowButtons(menu).size()==4&&firstRow(menu).startsWith("FPS"),"HUD category restores HUD rows");click(mc,named(menu,"All"));
                MenuLayout layout=MenuLayout.fit(menu.width,menu.height,mc.getWindow().getGuiScale());check(menu.mouseScrolled(layout.x()+300*layout.scale(),layout.y()+240*layout.scale(),0,-1),"actual screen consumes wheel scrolling inside feature list");check(firstRow(menu).startsWith("Coordinates"),"mouse wheel advances first visible row");
                click(mc,at(menu,554,80));check(firstRow(menu).startsWith("FPS"),"geometric up-arrow scrolls list upward");click(mc,at(menu,588,80));check(firstRow(menu).startsWith("Coordinates"),"geometric down-arrow scrolls list downward");
                check(menu.keyPressed(new KeyEvent(267,0,0)),"Page Down navigates scrollable list");check(firstRow(menu).startsWith("Active effects"),"Page Down reaches lower feature rows");click(mc,at(menu,12,200));next();return;
            }
            if(stage==5){
                if(wait<10)return;KapkanScreen menu=menu(mc);click(mc,at(menu,426,128));check(menu.keyPressed(new KeyEvent(75,0,0)),"actual Controls key capture consumes K");check(ClientFeatures.menuKey.saveString().equals("key.keyboard.k"),"actual Controls callback rebinds menu from J to K");
                boolean persisted=Files.readAllLines(marker(mc,"options.txt")).stream().anyMatch(line->line.contains("key.kapkan.menu")&&line.contains("key.keyboard.k"));check(persisted,"native Minecraft options file saves new K mapping");next();return;
            }
            if(stage==6){if(wait<10)return;screenshot(mc,"controls-rebound-k");check(mc.screen.keyPressed(new KeyEvent(256,0,0)),"native Escape closes rebound controls menu");check(mc.screen==null,"closing controls returns to live gameplay");KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(74));next();return;}
            if(stage==7){if(wait<10)return;check(mc.screen==null,"old J mapping no longer opens menu after rebinding");check(!(mc.screen instanceof IntroductionScreen),"menu closure does not repeat introduction");KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(75));next();return;}
            if(stage==8){if(wait<10)return;KapkanScreen menu=menu(mc);click(mc,at(menu,12,239));click(mc,at(menu,144,115));check(mc.screen instanceof IntroductionScreen,"actual Help button explicitly replays onboarding");check(mc.screen.keyPressed(new KeyEvent(256,0,0)),"native Escape dismisses replayed introduction");check(mc.screen instanceof KapkanScreen,"Help onboarding returns to parent menu");check(mc.screen.keyPressed(new KeyEvent(256,0,0)),"native Escape closes parent menu");next();return;}
            if(stage==9){if(wait<40)return;check(mc.screen==null,"gameplay remains clear after menu and Help closures");check(ClientFeatures.settings().onboardingShown,"Help replay leaves persistent dismissed state intact");check(ClientFeatures.menuKey.saveString().equals("key.keyboard.k"),"K binding remains set for independent restart verification");Files.writeString(marker(mc,"ui-passed.txt"),"Actual native UI interactions: "+checks+" assertions passed\n");stage=99;}
        }catch(Throwable t){failed=true;t.printStackTrace();try{Files.writeString(marker(mc,"ui-failed.txt"),t.toString()+"\n");}catch(Exception ignored){}}
    }
}
