package gg.kapkan.qa;

import gg.kapkan.presence.*;
import gg.kapkan.presence.cosmetics.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.*;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import java.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Real render and transport test with a disposable RemotePlayer fixture, not two game processes. */
@Mixin(Minecraft.class)
public abstract class CapeWorldProbe {
    private static int ticks,seconds,stage,checks;private static boolean creating;
    private static RemotePlayer peer;private static PresenceEngine sender;private static CapeData custom;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("CAPE_WORLD_PASS "+label);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"KapKan-"+name+".png",mc.getMainRenderTarget(),1,c->System.out.println("CAPTURE "+name));}
    private static void click(Minecraft mc,String starts){for(var child:mc.screen.children())if(child instanceof AbstractWidget b&&b.getMessage().getString().startsWith(starts)){
        double scale=Math.min(1.15,Math.min((mc.screen.width-24.0)/600,(mc.screen.height-24.0)/366)),ox=(mc.screen.width-600*scale)/2,oy=(mc.screen.height-366*scale)/2;
        MouseButtonEvent e=new MouseButtonEvent(ox+(b.getX()+b.getWidth()/2.0)*scale,oy+(b.getY()+b.getHeight()/2.0)*scale,new MouseButtonInfo(0,0));check(mc.screen.mouseClicked(e,false),"Click "+starts);mc.screen.mouseReleased(e);return;
    }throw new AssertionError("Missing "+starts);}
    @Inject(method="tick",at=@At("TAIL")) private void qa$world(CallbackInfo ci){
        Minecraft mc=(Minecraft)(Object)this;if(stage==99||++ticks%20!=0)return;
        try{
            if(mc.player==null){if(!creating&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){creating=true;mc.createWorldOpenFlows().createFreshLevel("KapKan shared cape QA",new LevelSettings("KapKan shared cape QA",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(1234L,false,false),WorldPresets::createFlatWorldDimensions,mc.screen);}return;}
            seconds++;
            if(stage==0&&seconds>=5){mc.setScreen(new KapkanScreen(null));stage=1;seconds=0;}
            else if(stage==1&&seconds>=3){shot(mc,"White-Logo-Black");click(mc,"Burgundy");stage=2;seconds=0;}
            else if(stage==2&&seconds>=3){shot(mc,"White-Logo-Burgundy");click(mc,"Ivory");stage=3;seconds=0;}
            else if(stage==3&&seconds>=3){shot(mc,"Ivory-Colour");click(mc,"Import PNG");stage=4;seconds=0;}
            else if(stage==4&&seconds>=3){
                check(mc.screen.children().stream().anyMatch(c->c instanceof AbstractWidget b&&b.getMessage().getString().startsWith("Browse capes")),"Cape site action exists in the importer");
                check(KapkanScreen.CAPE_WEBSITE.equals("https://mc-capes.com/"),"Browser action uses the verified HTTPS catalogue");shot(mc,"Cape-Importer");mc.screen.onClose();
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);mc.player.setXRot(0);stage=5;seconds=0;
            }else if(stage==5&&seconds>=3){
                check(BadgeText.hasBadge(mc.getEntityRenderDispatcher().extractEntity(mc.player,0).nameTag),"Own visible name tag retains the KK prefix");shot(mc,"White-Name-Badge");
                mc.options.setCameraType(CameraType.FIRST_PERSON);mc.player.setYRot(0);mc.player.setXRot(0);
                UUID id=UUID.fromString("fabe96d5-bcfe-44d3-b0e5-e622c9873210");peer=new RemotePlayer(mc.level,new com.mojang.authlib.GameProfile(id,"CapeFriend"));peer.setId(987654);peer.setPos(mc.player.getX(),mc.player.getY(),mc.player.getZ()+3);peer.setYRot(0);peer.yBodyRot=0;
                var parts=Avatar.class.getDeclaredField("DATA_PLAYER_MODE_CUSTOMISATION");parts.setAccessible(true);peer.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<Byte>)parts.get(null),(byte)127);mc.level.addEntity(peer);
                String context=PresenceEngine.context("render-fixture.example",25565);var field=BadgeClient.class.getDeclaredField("context");field.setAccessible(true);field.set(null,context);
                Path cfg=mc.gameDirectory.toPath().resolve("peer-presence.properties");Files.writeString(cfg,"enabled=true\nendpoint="+System.getProperty("kapkan.qa.serviceUrl")+"\n");sender=new PresenceEngine(cfg,true);
                var self=new PresenceEngine.Player(id,"CapeFriend");var us=new PresenceEngine.Player(mc.player.getUUID(),mc.player.getGameProfile().name());sender.snapshot(new PresenceEngine.Snapshot("fixture",context,self,List.of(self,us)));
                custom=CapeData.fromPng(CapeArt.png(CapeArt.make("studio",0x193F67,0xFFFFFF,"Border",true,cfg)));sender.publishCape(custom,"Silk");sender.start();stage=6;seconds=0;
            }else if(stage==6&&seconds>=22){
                var cape=BadgeClient.engine.cape(peer.getUUID());check(cape!=null&&cape.data().hash().equals(custom.hash()),"Real game receives custom PNG from independent service client");
                var state=(AvatarRenderState)mc.getEntityRenderDispatcher().extractEntity(peer,0);CapeFrame frame=((CapeFrame.Holder)state).kapkan$cape();
                check(frame!=null&&frame.texture().getPath().endsWith(custom.hash()),"Received custom cape reaches the real RemotePlayer renderer");check(state.showCape,"Remote cape model part is visible");check(state.nameTag!=null&&BadgeText.hasBadge(state.nameTag),"Remote participant receives name badge");
                shot(mc,"Shared-Custom-Cape");peer.setInvisible(true);var hidden=(AvatarRenderState)mc.getEntityRenderDispatcher().extractEntity(peer,0);check(hidden.isInvisible,"Invisible remote remains hidden under native render policy");peer.setInvisible(false);
                sender.publishCape(CapeData.fromPng(CapeArt.png(CapeArt.make("burgundy",ClientFeatures.settings()))),"Heavy");stage=7;seconds=0;
            }else if(stage==7&&seconds>=22){
                var cape=BadgeClient.engine.cape(peer.getUUID());check(cape!=null&&!cape.data().hash().equals(custom.hash())&&cape.material().equals("Heavy"),"Changed remote selection refreshes artwork and physics");shot(mc,"Shared-Premade-Cape");sender.publishCape(null,"Flow");stage=8;seconds=0;
            }else if(stage==8&&seconds>=22){
                check(BadgeClient.engine.cape(peer.getUUID())==null,"Remote unequip clears received cape");check(((CapeFrame.Holder)mc.getEntityRenderDispatcher().extractEntity(peer,0)).kapkan$cape()==null,"Renderer falls back to vanilla after unequip");check(SharedCapes.trackedPlayers()==0,"Unused remote cloth state is released");
                sender.close();Files.writeString(mc.gameDirectory.toPath().resolve("qa-passed.txt"),"Actual Minecraft 26.1.2: "+checks+" cape/render checks; independent HTTP sender + RemotePlayer fixture, not two Minecraft clients.\n");stage=99;mc.stop();
            }
        }catch(Throwable t){t.printStackTrace();if(sender!=null)sender.close();try{Files.writeString(mc.gameDirectory.toPath().resolve("qa-failed.txt"),t.toString());}catch(Exception ignored){}stage=99;mc.stop();}
    }
}
