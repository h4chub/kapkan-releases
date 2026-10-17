package gg.kapkan.qa;
import gg.kapkan.presence.BadgeClient;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Isolated real single-player test driver. Never shipped in a launcher or mod. */
@Mixin(Minecraft.class)
public abstract class SelfWorldProbe {
    private static int ticks,seconds,stage,checks;private static boolean creating;
    private static void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;System.out.println("SELF_WORLD_PASS "+label);}
    @Inject(method="tick",at=@At("TAIL"))
    private void qa$selfWorld(CallbackInfo ci){
        Minecraft mc=(Minecraft)(Object)this;if(stage==99||++ticks%20!=0)return;
        try{
            if(mc.player==null){
                if(!creating&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                    creating=true;
                    mc.createWorldOpenFlows().createFreshLevel("KapKan badge QA",new LevelSettings("KapKan badge QA",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(1234L,false,false),WorldPresets::createFlatWorldDimensions,mc.screen);
                }
                return;
            }
            seconds++;
            if(stage==0&&seconds>=6){
                check(mc.getResourceManager().getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath("kapkan","font/badge.json")).isPresent(),"Badge font resources loaded without Fabric API");
                var glyphMethod=net.minecraft.client.gui.Font.class.getDeclaredMethod("getGlyph",int.class,net.minecraft.network.chat.Style.class);glyphMethod.setAccessible(true);
                var style=net.minecraft.network.chat.Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("kapkan","badge")));
                check(glyphMethod.invoke(mc.font,0xe000,style)!=glyphMethod.invoke(mc.font,0xe002,style),"Logo bakes into the font atlas instead of a missing-glyph square");
                check(mc.getCurrentServer()==null,"Integrated world has no multiplayer ServerData");
                check(BadgeClient.engine.visible(mc.player.getUUID(),true),"Missing preferences default own badge on without endpoint");
                var info=mc.getConnection().getPlayerInfo(mc.player.getUUID());
                check(info!=null&&mc.gui.getTabList().getNameForDisplay(info).getString().contains("\ue000"),"Real Tab name contains the logo glyph");
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                check(mc.getEntityRenderDispatcher().extractEntity(mc.player,0).nameTag==null,"First-person own name stays hidden");
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);stage=1;seconds=0;
            }else if(stage==1&&seconds>=4){
                var tag=mc.getEntityRenderDispatcher().extractEntity(mc.player,0).nameTag;
                check(tag!=null&&tag.getString().contains("\ue000"),"Real third-person render state contains the logo");
                check(tag.getString().endsWith("BadgeCheck"),"Own username preserved");
                Screenshot.grab(mc.gameDirectory,"KapKan-Singleplayer-Badge.png",mc.getMainRenderTarget(),1,c->System.out.println("SELF_WORLD_SCREENSHOT "+c.getString()));
                Files.writeString(Path.of(System.getProperty("kapkan.presence.config")),"enabled=true\nshowOwn=false\n");stage=2;seconds=0;
            }else if(stage==2&&seconds>=8){
                check(!BadgeClient.engine.visible(mc.player.getUUID(),true),"Saved own-off applies while inside the world");
                check(mc.getEntityRenderDispatcher().extractEntity(mc.player,0).nameTag==null,"Saved own-off removes third-person nameplate");
                Files.writeString(Path.of(System.getProperty("kapkan.presence.config")),"enabled=true\nshowOwn=true\n");stage=3;seconds=0;
            }else if(stage==3&&seconds>=8){
                check(BadgeClient.engine.visible(mc.player.getUUID(),true),"Own badge can be re-enabled without reconnecting");
                mc.options.hideGui=true;check(!BadgeClient.selfNameAllowed(mc.player,4),"Hidden HUD hides own badge");mc.options.hideGui=false;
                mc.player.setInvisible(true);check(!BadgeClient.selfNameAllowed(mc.player,4),"Invisible self has no badge");mc.player.setInvisible(false);
                Files.writeString(mc.gameDirectory.toPath().resolve("qa-passed.txt"),"Actual Minecraft 26.1.2 / Fabric 0.19.5 single-player: "+checks+" checks passed. Linux Mesa software rendering.\n");stage=99;mc.stop();
            }
        }catch(Throwable t){t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("qa-failed.txt"),t.toString());}catch(Exception ignored){}stage=99;mc.stop();}
    }
}
