package gg.kapkan.qa;
import gg.kapkan.presence.BadgeClient;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.*;
import net.minecraft.world.scores.Team;
import java.nio.file.*;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only driver. NEVER included in the shipped launcher or companion. */
@Mixin(Minecraft.class)
public abstract class GameProbe {
    private static int ticks,joined,stage;private static boolean connecting;private static int checks;
    private static void check(boolean result,String name){if(!result)throw new AssertionError(name);checks++;System.out.println("KAPKAN_LIVE_PASS "+name);}
    @Inject(method="tick",at=@At("TAIL"))
    private void qa$tick(CallbackInfo ci){
        Minecraft mc=(Minecraft)(Object)this;
        if(++ticks%20!=0)return;
        try {
            if(mc.player==null){
                if(!connecting&&mc.screen instanceof TitleScreen){connecting=true;ServerData d=new ServerData("KapKan local QA","127.0.0.1:25578",ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(d.ip),d,false,null);}
                return;
            }
            joined++;
            if(System.getProperty("kapkan.qa.role").equals("plain"))return;
            if(stage==0){
                if(mc.getConnection().getOnlinePlayers().size()<3||joined<25)return;
                for(var info:mc.getConnection().getOnlinePlayers()){
                    String name=info.getProfile().name();boolean expected=!name.equals("PlainPlayer");
                    if(expected&&!BadgeClient.engine.visible(info.getProfile().id(),info.getProfile().id().equals(mc.player.getUUID())))return;
                }
                for(var info:mc.getConnection().getOnlinePlayers()){
                    String name=info.getProfile().name();Component tab=mc.gui.getTabList().getNameForDisplay(info);
                    check(tab.getString().contains("\ue000")==!name.equals("PlainPlayer"),"tab participation "+name);
                    if(name.equals("QA_Beta"))check(tab.getString().contains("[VIP]")&&tab.getString().contains("QA_Beta"),"server prefix preserved");
                }
                for(var player:mc.level.players())if(player!=mc.player){var render=mc.getEntityRenderDispatcher().extractEntity(player,0);check(render.nameTag!=null,"remote nametag present "+player.getGameProfile().name());check(render.nameTag.getString().contains("\ue000")==!player.getGameProfile().name().equals("PlainPlayer"),"overhead participation "+player.getGameProfile().name());}
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                check(mc.getEntityRenderDispatcher().extractEntity(mc.player,0).nameTag==null,"no first-person self tag");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                var self=mc.getEntityRenderDispatcher().extractEntity(mc.player,0);check(self.nameTag!=null&&self.nameTag.getString().contains("\ue000"),"third-person self tag");
                mc.player.setInvisible(true);check(!BadgeClient.selfNameAllowed(mc.player,4),"invisible self hidden");mc.player.setInvisible(false);
                var team=mc.player.getTeam();if(team!=null){var old=team.getNameTagVisibility();team.setNameTagVisibility(Team.Visibility.NEVER);check(!BadgeClient.selfNameAllowed(mc.player,4),"team NEVER hides self");team.setNameTagVisibility(old);}
                stage=1;joined=0;mc.options.keyPlayerList.setDown(true);return;
            }
            if(stage==1&&joined>=3){Screenshot.grab(mc.gameDirectory,"KapKan-H2-live-"+System.getProperty("kapkan.qa.role")+".png",mc.getMainRenderTarget(),1,c->System.out.println("KAPKAN_SCREENSHOT "+c.getString()));stage=2;joined=0;return;}
            if(stage==2&&joined>=2){
                Path cfg=Path.of(System.getProperty("kapkan.presence.config"));Properties p=new Properties();try(var in=Files.newInputStream(cfg)){p.load(in);}p.setProperty("showOwn","false");try(var out=Files.newOutputStream(cfg)){p.store(out,"QA self off");}stage=3;joined=0;return;
            }
            if(stage==3&&joined>=9){
                check(!BadgeClient.engine.visible(mc.player.getUUID(),true),"self toggle disables own badge");
                check(mc.getEntityRenderDispatcher().extractEntity(mc.player,0).nameTag==null,"self toggle hides third-person tag");
                if(System.getProperty("kapkan.qa.role").equals("alpha")){Path cfg=Path.of(System.getProperty("kapkan.presence.config"));Properties p=new Properties();try(var in=Files.newInputStream(cfg)){p.load(in);}p.setProperty("enabled","false");try(var out=Files.newOutputStream(cfg)){p.store(out,"QA presence off");}stage=4;joined=0;}else{done(mc);stage=5;}return;
            }
            if(stage==4&&joined>=9){
                for(var info:mc.getConnection().getOnlinePlayers())check(!mc.gui.getTabList().getNameForDisplay(info).getString().contains("\ue000"),"opt-out clears "+info.getProfile().name());done(mc);stage=5;
            }
        }catch(Throwable t){System.err.println("KAPKAN_LIVE_FAIL "+t);t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("qa-failed.txt"),t.toString());}catch(Exception ignored){}stage=99;}
    }
    private static void done(Minecraft mc)throws Exception{Files.writeString(mc.gameDirectory.toPath().resolve("qa-passed.txt"),"Actual Minecraft 26.1.2 Fabric runtime: "+checks+" assertions passed. Linux software rendering.\n");System.out.println("KAPKAN_LIVE_DONE "+checks);}
}
