package gg.kapkan.qa105;

import com.mojang.blaze3d.platform.InputConstants;
import gg.kapkan.presence.cosmetics.*;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.input.*;
import net.minecraft.world.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TEST ONLY: drives actual Minecraft input, vanilla sprint gates, and the rendered HUD editor. */
@Mixin(Minecraft.class)
public abstract class FeatureProbe {
    private static int stage,wait,checks;
    private static boolean failed;
    private static double originalX,originalScale,draggedX;
    private static Path marker(Minecraft mc,String name){return mc.gameDirectory.toPath().resolve(name);}
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("KAPKAN105_FEATURE_PASS "+label);}
    private static void next(){stage++;wait=0;}
    private static void click(KeyMapping key){KeyMapping.click(InputConstants.getKey(key.saveString()));}
    private static MouseButtonEvent mouse(double x,double y){return new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));}
    private static HudLayout.Bounds fpsBounds(Minecraft mc){var widget=HudRenderer.widgets().stream().filter(w->w.id().equals("fps")).findFirst().orElseThrow();return ClientFeatures.settings().hud.bounds("fps",mc.screen.width,mc.screen.height,widget.width(),widget.height());}
    @Inject(method="tick",at=@At("TAIL"))
    private void qa105$features(CallbackInfo ci){
        if(!Boolean.getBoolean("kapkan.qa.features")||failed||stage>=99)return;
        Minecraft mc=(Minecraft)(Object)this;wait++;
        try{
            if(stage==0){
                if(mc.screen instanceof IntroductionScreen){mc.screen.onClose();return;}
                if(mc.player==null&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null&&wait>40){
                    ServerData data=new ServerData("KapKan feature QA","127.0.0.1:"+Integer.getInteger("kapkan.qa.serverPort",25578),ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(data.ip),data,false,null);next();
                }return;
            }
            if(mc.player==null||mc.level==null)return;
            var s=ClientFeatures.settings();
            if(stage==1){
                if(wait<80)return;
                s.fps=s.coordinates=s.keystrokes=s.clicks=s.armor=s.effects=s.toggleSprint=s.sprintIndicator=s.zoom=true;s.zoomPower=3;s.reducedMotion=false;s.save();
                mc.player.getAbilities().flying=false;mc.player.getAbilities().mayfly=false;mc.player.getAbilities().instabuild=false;mc.player.getFoodData().setFoodLevel(20);mc.setScreen(null);
                Files.writeString(marker(mc,"joined-ready.txt"),"Actual feature client ready for server armor/effect setup\n");next();return;
            }
            if(stage==2){
                if(wait<100)return;
                var widgets=HudRenderer.widgets();
                check(widgets.size()==7,"seven real HUD widgets created");
                String xyz=mc.player.getBlockX()+"  "+mc.player.getBlockY()+"  "+mc.player.getBlockZ();
                check(widgets.stream().filter(w->w.id().equals("coordinates")).findFirst().orElseThrow().lines().getFirst().equals(xyz),"coordinate HUD matches actual player block position");
                check(widgets.stream().filter(w->w.id().equals("fps")).findFirst().orElseThrow().lines().getFirst().endsWith(" FPS"),"FPS HUD receives actual Minecraft frame counter");
                var helmet=mc.player.getItemBySlot(EquipmentSlot.HEAD);check(!helmet.isEmpty(),"actual server equipped armor for durability verification");
                String durability=(helmet.getMaxDamage()-helmet.getDamageValue())+" / "+helmet.getMaxDamage();
                check(widgets.stream().filter(w->w.id().equals("armor")).findFirst().orElseThrow().lines().getFirst().equals(durability),"armor HUD matches actual stack remaining and maximum durability");
                check(!mc.player.getActiveEffects().isEmpty(),"actual server supplied effect for timer verification");
                check(!widgets.stream().filter(w->w.id().equals("effects")).findFirst().orElseThrow().lines().getFirst().equals("No active effects"),"active effect HUD reads actual player effects");
                mc.options.keyUp.setDown(true);click(ClientFeatures.toggleSprintKey);next();return;
            }
            if(stage==3){if(wait<10)return;check(ClientFeatures.sprintLatched(),"native R click engages toggle sprint latch");check(mc.player.isSprinting(),"vanilla player starts sprinting with forward input");mc.options.keyShift.setDown(true);next();return;}
            if(stage==4){if(wait<6)return;check(mc.options.keyShift.isDown(),"actual sneak key mapping is held");check(mc.player.input.keyPresses.shift(),"actual player input contains sneak flag");check(mc.player.isShiftKeyDown(),"actual player is sneaking");check(!mc.player.isSprinting(),"sneaking stops KapKan toggle sprint");mc.options.keyShift.setDown(false);mc.player.getFoodData().setFoodLevel(0);next();return;}
            if(stage==5){mc.player.getFoodData().setFoodLevel(0);if(wait<8)return;check(!mc.player.isSprinting(),"low food prevents toggle sprint through vanilla rules");mc.player.getFoodData().setFoodLevel(20);next();return;}
            if(stage==6){mc.player.getFoodData().setFoodLevel(20);if(wait<10)return;check(mc.player.isSprinting(),"sprint resumes when normal eligibility returns");click(ClientFeatures.toggleSprintKey);next();return;}
            if(stage==7){if(wait<3)return;check(!ClientFeatures.sprintLatched()&&!mc.player.isSprinting(),"toggle off clears actual sprint state");mc.options.keyUp.setDown(false);ClientFeatures.zoomKey.setDown(true);next();return;}
            if(stage==8){if(wait<8)return;float fov=ClientFeatures.fov(90);check(fov<31&&fov>=29.9f,"held zoom reaches configured three times FOV");ClientFeatures.zoomKey.setDown(false);next();return;}
            if(stage==9){if(wait<10)return;check(Math.abs(ClientFeatures.fov(90)-90)<.1,"zoom release restores normal camera FOV");mc.setScreen(new KapkanScreen(null));ClientFeatures.zoomKey.setDown(true);check(ClientFeatures.fov(90)==90,"open menu prevents held zoom from changing camera");ClientFeatures.zoomKey.setDown(false);mc.screen.onClose();check(!(mc.screen instanceof IntroductionScreen),"closing menu does not reopen welcome");ClientFeatures.showIntroduction();check(mc.screen instanceof IntroductionScreen,"Help can explicitly replay introduction");mc.screen.onClose();mc.setScreen(new HudEditorScreen(null));next();return;}
            if(stage==10){
                if(wait<10)return;var b=fpsBounds(mc);originalX=s.hud.get("fps").x;originalScale=s.hud.get("fps").scale;
                check(mc.screen.mouseClicked(mouse(b.x()+3,b.y()+3),false),"editor selects actual rendered widget");
                check(mc.screen.mouseDragged(mouse(b.x()+73,b.y()+34),70,31),"editor consumes drag input");mc.screen.mouseReleased(mouse(b.x()+73,b.y()+34));draggedX=s.hud.get("fps").x;
                check(draggedX>originalX,"widget drag updates normalized layout");next();return;
            }
            if(stage==11){if(wait<5)return;var b=fpsBounds(mc);check(mc.screen.mouseScrolled(b.x()+3,b.y()+3,0,1),"mouse wheel scales selected live HUD widget");check(s.hud.get("fps").scale>originalScale,"mouse wheel changes real widget scale");double x=s.hud.get("fps").x;check(mc.screen.keyPressed(new KeyEvent(262,0,0)),"arrow key moves selected widget");check(s.hud.get("fps").x>x,"keyboard alternative updates widget position");s.saveProfile("Gameplay QA");double saved=s.hud.get("fps").x;s.hud.reset();s.loadProfile("Gameplay QA");check(Math.abs(saved-s.hud.get("fps").x)<.0001,"saved profile restores position inside actual game");check(new ClientSettings(s.file).hud.get("fps").scale==s.hud.get("fps").scale,"editor scaling persisted immediately to preferences");Screenshot.grab(mc.gameDirectory,"KapKan-1.0.5-actual-feature-HUD-editor.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));next();return;}
            if(stage==12){if(wait<5)return;mc.screen.onClose();check(mc.screen==null,"Escape-equivalent editor close returns to gameplay");check(!(mc.screen instanceof IntroductionScreen),"editor closure keeps welcome dismissed");Files.writeString(marker(mc,"mouse-input-ready.txt"),"Ready for native X11 mouse and keyboard input\n");next();return;}
            if(stage==13){if(wait<10||!mc.mouseHandler.isLeftPressed()||!mc.mouseHandler.isRightPressed()||!mc.options.keyUp.isDown())return;check(mc.mouseHandler.isLeftPressed(),"native left mouse event reaches click indicator input");check(mc.mouseHandler.isRightPressed(),"native right mouse event reaches click indicator input");check(mc.options.keyUp.isDown(),"native W event reaches keystroke indicator input");Screenshot.grab(mc.gameDirectory,"KapKan-1.0.5-actual-live-feature-HUD.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));Files.writeString(marker(mc,"feature-passed.txt"),"Actual Minecraft feature checks: "+checks+" assertions passed\n");stage=99;}
        }catch(Throwable t){failed=true;t.printStackTrace();try{Files.writeString(marker(mc,"feature-failed.txt"),t.toString()+"\n");}catch(Exception ignored){}}
    }
}
