package gg.kapkan.qa;
import gg.kapkan.presence.cosmetics.*;
import gg.kapkan.presence.BadgeClient;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disposable client driver: no QA code is included in the shipped companion. */
@Mixin(Minecraft.class)
public abstract class CosmeticsWorldProbe {
    private static int ticks,seconds,stage,checks;private static boolean creating;private static double startZ,normalFov;private static java.util.concurrent.CompletableFuture<Void> reload;private static long idleRasterCount;
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("COSMETICS_WORLD_PASS "+label);}
    private static void shot(Minecraft mc,String name){Screenshot.grab(mc.gameDirectory,"KapKan-"+name+".png",mc.getMainRenderTarget(),1,c->System.out.println("CAPTURE "+name));}
    private static void click(Minecraft mc,String starts){
        for(var child:mc.screen.children())if(child instanceof AbstractWidget b&&b.getMessage().getString().startsWith(starts)){
            double scale=Math.min(1.15,Math.min((mc.screen.width-24.0)/600,(mc.screen.height-24.0)/366));double ox=(mc.screen.width-600*scale)/2,oy=(mc.screen.height-366*scale)/2;
            MouseButtonEvent event=new MouseButtonEvent(ox+(b.getX()+b.getWidth()/2.0)*scale,oy+(b.getY()+b.getHeight()/2.0)*scale,new MouseButtonInfo(0,0));
            check(mc.screen.mouseClicked(event,false),"Clickable control: "+starts);if(mc.screen!=null)mc.screen.mouseReleased(event);return;
        }throw new AssertionError("Missing control "+starts);
    }
    @Inject(method="tick",at=@At("TAIL")) private void qa$world(CallbackInfo ci){
        Minecraft mc=(Minecraft)(Object)this;if(stage==99||++ticks%20!=0)return;
        try{
            if(mc.player==null){if(!creating&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                creating=true;mc.options.keyInventory.setKey(InputConstants.Type.KEYSYM.getOrCreate(344));KeyMapping.resetMapping();
                mc.createWorldOpenFlows().createFreshLevel("KapKan cosmetics QA",new LevelSettings("KapKan cosmetics QA",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(1234L,false,false),WorldPresets::createFlatWorldDimensions,mc.screen);
            }return;}
            seconds++;
            if(stage==0&&seconds>=3){check(!ClientFeatures.menuKey.saveString().equals("key.keyboard.right.shift"),"Menu avoids an occupied Right Shift key");check(!ClientFeatures.menuKey.isUnbound(),"Menu automatically receives a free key");shot(mc,"Welcome");stage=1;seconds=0;}
            else if(stage==1&&seconds>=7){
                check(BadgeClient.engine.visible(mc.player.getUUID(),true),"Own badge still visible by default");check(mc.getResourceManager().getResource(net.minecraft.resources.Identifier.fromNamespaceAndPath("kapkan","font/ui.json")).isPresent(),"Custom UI font loaded");
                var state=mc.getEntityRenderDispatcher().extractEntity(mc.player,0);check(((CapeFrame.Holder)state).kapkan$cape()!=null,"Real avatar render state carries custom cape geometry");
                String assigned=ClientFeatures.menuKey.saveString();ClientFeatures.menuKey.setKey(InputConstants.UNKNOWN);mc.options.load();KeyMapping.resetMapping();check(ClientFeatures.menuKey.saveString().equals(assigned),"Automatically chosen key survives options reload");KeyMapping.click(InputConstants.getKey(ClientFeatures.menuKey.saveString()));stage=2;seconds=0;
            }else if(stage==2&&seconds>=2){if(seconds==2){idleRasterCount=MenuStyle.rasterizations();return;}if(seconds<4)return;check(MenuStyle.rasterizations()==idleRasterCount,"Idle menu reuses cached display-resolution text and shapes");check(mc.screen instanceof KapkanScreen,"Chosen key opens the in-game menu");shot(mc,"Capes-Menu");click(mc,"Ivory");check(ClientFeatures.settings().design.equals("ivory"),"Premade equips immediately");click(mc,"Preview walk");stage=3;seconds=0;}
            else if(stage==3&&seconds>=4){check(ClientFeatures.preview.tipTrail()>0.3,"Preview runs the actual cape solver");shot(mc,"Cape-Running-Preview");click(mc,"Stop walking");click(mc,"Customize");stage=4;seconds=0;}
            else if(stage==4&&seconds>=2){
                for(var child:mc.screen.children())if(child instanceof EditBox box&&box.getMessage().getString().contains("Base colour")){box.setFocused(true);box.keyPressed(new KeyEvent(65,0,2));check(box.getHighlighted().equals(box.getValue()),"Native select-all works in smooth text field");box.insertText("#314B64");box.setFocused(false);}
                check(ClientFeatures.settings().base==0x314B64,"Hex colour edit updates studio");shot(mc,"Cape-Studio");click(mc,"Save cape");check(!CapeArt.library(ClientFeatures.settings().capes).isEmpty(),"Save produces a reusable PNG");
                Path fixture=mc.gameDirectory.toPath().resolve("import-cape.png");javax.imageio.ImageIO.write(CapeArt.make("burgundy",ClientFeatures.settings()),"PNG",fixture.toFile());mc.screen.onFilesDrop(java.util.List.of(fixture));check(ClientFeatures.settings().design.equals("custom"),"Dropped cape imported and equipped");click(mc,"Skins");stage=5;seconds=0;
            }else if(stage==5&&seconds>=2){
                Path skin=mc.gameDirectory.toPath().resolve("skin-fixture.png");try(var in=Minecraft.class.getResourceAsStream("/assets/minecraft/textures/entity/player/wide/steve.png")){Files.copy(in,skin,StandardCopyOption.REPLACE_EXISTING);}mc.screen.onFilesDrop(java.util.List.of(skin));
                check(mc.player.getSkin().body().texturePath().getNamespace().equals("kapkan"),"Imported skin is used by the real player renderer");click(mc,"View front");stage=6;seconds=0;
            }else if(stage==6&&seconds>=2){shot(mc,"Skin-Importer");click(mc,"Settings");stage=7;seconds=0;}
            else if(stage==7&&seconds>=2){
                click(mc,"Coordinates");check(ClientFeatures.settings().coordinates,"Coordinate toggle changes the live setting");shot(mc,"Features");click(mc,"Premium");stage=8;seconds=0;
            }else if(stage==8&&seconds>=2){shot(mc,"Premium");mc.screen.onClose();mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);mc.player.setYRot(0);mc.player.setXRot(12);startZ=mc.player.getZ();mc.options.keyUp.setDown(true);mc.options.keySprint.setDown(true);mc.player.setSprinting(true);Files.writeString(mc.gameDirectory.toPath().resolve("record-start.txt"),"run");stage=9;seconds=0;}
            else if(stage==9&&seconds>=6){check(Math.abs(mc.player.getZ()-startZ)>1,"Player really runs in the integrated world");check(ClientFeatures.motion.tipTrail()>0.3,"World cape responds to player travel");shot(mc,"Cape-Running");mc.options.keyUp.setDown(false);mc.options.keySprint.setDown(false);mc.player.setSprinting(false);stage=10;seconds=0;}
            else if(stage==10&&seconds>=6){check(ClientFeatures.motion.tipTrail()<0.08,"World cape settles after player stops");shot(mc,"Cape-Settled");normalFov=mc.gameRenderer.getMainCamera().getFov();ClientFeatures.zoomKey.setDown(true);stage=11;seconds=0;}
            else if(stage==11&&seconds>=2){check(mc.gameRenderer.getMainCamera().getFov()<normalFov*0.6,"Hold zoom changes the real camera FOV");ClientFeatures.zoomKey.setDown(false);mc.options.hideGui=true;stage=12;seconds=0;}
            else if(stage==12&&seconds>=2){check(mc.gameRenderer.getMainCamera().getFov()>normalFov*0.9,"Releasing zoom restores the camera");mc.options.hideGui=false;mc.setScreen(new PauseScreen(true));check(mc.screen.children().stream().anyMatch(c->c instanceof AbstractWidget w&&w.getMessage().getString().equals("KapKan")),"Pause menu includes KapKan shortcut");
                ClientSettings disk=new ClientSettings(ClientFeatures.settings().file);check(disk.coordinates&&disk.skin&&disk.design.equals("custom"),"Capes, skin and HUD persist to disk");
                mc.setScreen(new KapkanScreen(null));click(mc,"Settings");click(mc,"Controls");click(mc,ClientFeatures.menuKey.getTranslatedKeyMessage().getString());String saved=ClientFeatures.menuKey.saveString();mc.screen.keyPressed(new KeyEvent(87,0,0));check(ClientFeatures.menuKey.saveString().equals(saved),"Rebinding refuses occupied movement key");mc.screen.keyPressed(new KeyEvent(256,0,0));
                reload=mc.reloadResourcePacks();stage=13;seconds=0;
            }else if(stage==13&&seconds>=3&&reload.isDone()&&mc.getOverlay()==null){
                check(!reload.isCompletedExceptionally(),"Minecraft resource reload succeeds with custom font and textures");
                click(mc,"Capes");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().handle(),840,560);stage=14;seconds=0;
            }else if(stage==14&&seconds>=3){
                shot(mc,"Menu-Small-Window");click(mc,"Ivory");check(ClientFeatures.settings().design.equals("ivory"),"Scaled menu remains clickable in 840 x 560 window");
                Files.writeString(mc.gameDirectory.toPath().resolve("qa-passed.txt"),"Actual Minecraft 26.1.2 / Fabric 0.19.5: "+checks+" cosmetic/menu checks passed. Linux software OpenGL.\n");stage=99;mc.stop();
            }
        }catch(Throwable e){e.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("qa-failed.txt"),e.toString());}catch(Exception ignored){}stage=99;mc.stop();}
    }
}
