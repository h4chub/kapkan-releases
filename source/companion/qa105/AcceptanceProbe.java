package gg.kapkan.qa105;

import com.mojang.blaze3d.platform.InputConstants;
import gg.kapkan.presence.BadgeClient;
import gg.kapkan.presence.cosmetics.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TEST ONLY. Drives two actual isolated Minecraft clients. Never shipped. */
@Mixin(Minecraft.class)
public abstract class AcceptanceProbe {
    private static int ticks, stage, wait, checks;
    private static final String role=System.getProperty("kapkan.qa.role","alpha");
    private static final boolean restart=Boolean.getBoolean("kapkan.qa.restart");
    private static boolean connected, failed;
    private static Object beforeRespawn, beforeReconnect;
    private static String originalRemoteCape;
    private static Path file(Minecraft mc,String name){return mc.gameDirectory.toPath().resolve(name);}
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("KAPKAN105_ACTUAL_PASS "+role+" "+label);}
    private static void result(Minecraft mc,String name,String text)throws Exception{Files.writeString(file(mc,name),text+"\n");}
    private static void shot(Minecraft mc,String label){Screenshot.grab(mc.gameDirectory,"KapKan-1.0.5-"+role+"-"+label+".png",mc.getMainRenderTarget(),1,c->System.out.println("KAPKAN105_SCREENSHOT "+c.getString()));}
    private static boolean introduction(Screen s){return s!=null&&s.getClass().getSimpleName().equals("IntroductionScreen");}
    private static void openMenu(Minecraft mc){mc.setScreen(new KapkanScreen(null));}
    private static void click(Minecraft mc,int x,int y){var layout=MenuLayout.fit(mc.screen.width,mc.screen.height,mc.getWindow().getGuiScale());var event=new MouseButtonEvent(layout.x()+x*layout.scale(),layout.y()+y*layout.scale(),new MouseButtonInfo(0,0));mc.screen.mouseClicked(event,false);mc.screen.mouseReleased(event);}
    private static void join(Minecraft mc){ServerData d=new ServerData("KapKan isolated local QA","127.0.0.1:"+Integer.getInteger("kapkan.qa.serverPort",25578),ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(d.ip),d,false,null);connected=true;}
    @Inject(method="tick",at=@At("TAIL"))
    private void qa105$tick(CallbackInfo ci){
        Minecraft mc=(Minecraft)(Object)this;
        if(failed||++ticks%10!=0)return;
        try{
            if(stage==0){
                if(introduction(mc.screen)){
                    if(++wait<4)return;
                    check(!restart,"onboarding does not appear after restarting");
                    check(ClientFeatures.settings().onboardingShown,"introduction dismissal committed before first display");
                    shot(mc,"first-launch-introduction");mc.screen.onClose();stage=1;wait=0;return;
                }
                if(restart&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                    check(ClientFeatures.settings().onboardingShown,"onboarding state persisted through actual restart");
                    check(!ClientFeatures.settings().cape,"No Cape persisted through actual restart");
                    check(ClientFeatures.settings().coordinates&&ClientFeatures.settings().keystrokes,"feature preferences persisted through actual restart");
                    stage=1;wait=0;
                }
                return;
            }
            if(stage==1){
                if(++wait<6)return;
                check(!introduction(mc.screen),"closing introduction does not retrigger it");
                check(ClientFeatures.menuKey.saveString().equals("key.keyboard.j"),"menu defaults to J native rebindable key mapping");
                if(!connected&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null)join(mc);
                if(mc.player==null||mc.level==null)return;
                stage=2;wait=0;return;
            }
            if(stage==94){
                if(!connected&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){join(mc);wait=0;return;}
                if(mc.player==null||mc.level==null||mc.getConnection()==beforeReconnect)return;
                if(++wait<12)return;
                var peer=mc.getConnection().getOnlinePlayers().stream().filter(info->!info.getProfile().id().equals(mc.player.getUUID())).findFirst().orElse(null);
                if(peer==null||!BadgeClient.engine.visible(peer.getProfile().id(),false))return;
                check(!introduction(mc.screen),"actual reconnect does not repeat introduction");check(BadgeClient.engine.cape(peer.getProfile().id())==null,"No Cape resynchronizes after actual reconnect");result(mc,"reconnect-passed.txt","Actual client disconnect/reconnect verified");stage=95;return;
            }
            if(mc.player==null||mc.level==null)return;
            if(stage==90&&!restart&&Files.exists(file(mc,"dimension-request.txt"))){
                if(!mc.level.dimension().identifier().toString().equals("minecraft:the_nether"))return;
                if(++wait<10)return;var peer=mc.getConnection().getOnlinePlayers().stream().filter(info->!info.getProfile().id().equals(mc.player.getUUID())).findFirst().orElse(null);
                if(peer==null||!BadgeClient.engine.visible(peer.getProfile().id(),false))return;
                check(!introduction(mc.screen),"dimension change does not repeat introduction");check(BadgeClient.engine.cape(peer.getProfile().id())==null,"No Cape remains removed through dimension change");result(mc,"dimension-passed.txt","Actual Nether dimension transition verified");stage=91;wait=0;return;
            }
            if(stage==91&&mc.screen instanceof DeathScreen){beforeRespawn=mc.player;mc.player.respawn();mc.setScreen(null);stage=92;wait=0;return;}
            if(stage==92){
                if(mc.player==beforeRespawn||mc.player.getHealth()<=0||++wait<10)return;
                check(!introduction(mc.screen),"actual server death and respawn do not repeat introduction");result(mc,"respawn-passed.txt","Actual server death and respawn verified");stage=93;wait=0;return;
            }
            if(stage==93&&Files.exists(file(mc,"reconnect-request.txt"))){beforeReconnect=mc.getConnection();mc.disconnect(new TitleScreen(),false);connected=false;stage=94;wait=0;return;}
            if(stage==2){
                if(++wait<12||mc.getConnection().getOnlinePlayers().size()<2)return;
                check(!introduction(mc.screen),"joining multiplayer does not retrigger onboarding");
                var s=ClientFeatures.settings();
                if(!restart){s.cape=true;s.design=role.equals("alpha")?"black":"burgundy";s.fps=true;s.coordinates=true;s.keystrokes=true;s.clicks=true;s.armor=true;s.effects=true;s.toggleSprint=true;s.sprintIndicator=true;s.save();}
                result(mc,"joined-ready.txt","Joined actual dedicated Fabric server");
                stage=3;wait=0;return;
            }
            if(stage==3){
                if(++wait<12)return;
                var other=mc.level.players().stream().filter(p->p!=mc.player).findFirst().orElse(null);
                if(other==null||!BadgeClient.engine.visible(other.getUUID(),false))return;
                var cape=BadgeClient.engine.cape(other.getUUID());
                if(!restart&&cape==null)return;
                check(BadgeClient.engine.status().contains("relay"),"actual server relay connected");
                check(BadgeClient.engine.visible(other.getUUID(),false),"other independent client's badge received by server identity");
                check(mc.gui.getTabList().getNameForDisplay(mc.getConnection().getPlayerInfo(other.getUUID())).getString().contains("\ue000"),"other player's badge reaches actual Tab render text");
                if(!restart){
                    check(cape!=null,"other independent client's selected cape received");
                    
                    check(SharedCapes.frame(other,0)!=null,"remote player's cape uploaded for actual renderer");
                }else check(cape==null,"other client's No Cape survives restart and resynchronizes");
                mc.options.setCameraType(role.equals("beta")?CameraType.FIRST_PERSON:CameraType.THIRD_PERSON_BACK);stage=4;wait=0;return;
            }
            if(stage==4){
                if(++wait<6)return;shot(mc,restart?"restart-persisted-hud":"multiplayer-hud-and-cape");
                if(restart){result(mc,"restart-passed.txt","Actual restarted Minecraft "+checks+" checks passed");stage=90;wait=0;return;}
                if(role.equals("beta")){result(mc,"peer-ready.txt","Remote cape and badge verified");stage=20;wait=0;return;}
                mc.getWindow().setWindowed(1280,800);mc.options.guiScale().set(1);mc.resizeGui();openMenu(mc);stage=5;wait=0;return;
            }
            if(stage>=5&&stage<=10){
                if(++wait<5)return;wait=0;
                int index=stage-5;
                if(index<4){
                    int[] widths={1280,1280,1280,854};int[] heights={800,800,800,480};int[] scales={1,2,3,2};
                    
                    int children=mc.screen.children().size();check(children>0,"interactive menu widgets at viewport "+widths[index]+"x"+heights[index]+" GUI "+scales[index]);
                    shot(mc,"menu-"+widths[index]+"x"+heights[index]+"-gui"+scales[index]);
                    if(index<3){mc.getWindow().setWindowed(widths[index+1],heights[index+1]);mc.options.guiScale().set(scales[index+1]);mc.resizeGui();openMenu(mc);}
                    stage++;return;
                }
                if(index==4){
                    check(mc.screen.keyPressed(new KeyEvent(256,0,0)),"Escape input consumed by menu");
                    check(mc.screen==null,"Escape closes menu during gameplay");
                    mc.getWindow().setWindowed(1280,800);mc.options.guiScale().set(2);mc.resizeGui();
                    mc.setScreen(new HudEditorScreen(null));stage++;return;
                }
                shot(mc,"hud-editor");mc.screen.onClose();check(!introduction(mc.screen),"closing HUD editor does not retrigger onboarding");
                var s=ClientFeatures.settings();s.hud.get("fps").x=0.65;s.hud.get("fps").y=0.08;s.hud.get("fps").scale=1.2;s.saveProfile("QA Layout");
                s.hud.get("fps").x=0.1;s.loadProfile("QA Layout");check(Math.abs(s.hud.get("fps").x-0.65)<0.001,"saved HUD layout restores position in actual client");
                result(mc,"menu-passed.txt","Actual menu viewport, Escape and HUD profile checks passed");stage=11;wait=0;return;
            }
            if(stage==11){
                if(++wait<6)return;
                check(!introduction(mc.screen),"closing KapKan menu does not retrigger onboarding");
                openMenu(mc);click(mc,55,138);stage=12;wait=0;return;
            }
            if(stage==12){if(++wait<4)return;click(mc,330,248);check(!ClientFeatures.settings().cape,"No Cape UI selection removes local cape immediately");shot(mc,"no-cape-selection");mc.screen.onClose();result(mc,"no-cape-selected.txt","No Cape UI selected and saved in alpha client");stage=13;wait=0;return;}
            if(stage==13){
                if(++wait<10)return;
                check(!ClientFeatures.settings().cape,"local No Cape stays disabled");
                if(!Files.exists(Path.of(System.getProperty("kapkan.qa.peerDir")).resolve("remote-no-cape-passed.txt")))return;
                result(mc,"qa-passed.txt","Actual Minecraft two-client + menu checks: "+checks+" assertions passed");stage=90;wait=0;return;
            }
            if(stage==20){
                if(!Files.exists(Path.of(System.getProperty("kapkan.qa.peerDir")).resolve("no-cape-selected.txt")))return;
                var other=mc.level.players().stream().filter(p->p!=mc.player).findFirst().orElse(null);
                if(other==null||BadgeClient.engine.cape(other.getUUID())!=null)return;
                check(SharedCapes.frame(other,0)==null,"remote No Cape clears rendered cape without reconnect");
                shot(mc,"remote-no-cape");result(mc,"remote-no-cape-passed.txt","Other actual client's No Cape synchronized and renderer removed cape");
                ClientFeatures.settings().cape=false;ClientFeatures.settings().save();result(mc,"qa-passed.txt","Actual peer Minecraft process checks: "+checks+" assertions passed");stage=90;wait=0;
            }
        }catch(Throwable t){failed=true;t.printStackTrace();try{result(mc,"qa-failed.txt",t.toString());}catch(Exception ignored){}}
    }
}
