package gg.kapkan.qa105;
import gg.kapkan.presence.cosmetics.*;
import gg.kapkan.presence.BadgeClient;
import java.nio.file.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** TEST ONLY: real per-target game, world, menu, HUD and relay startup smoke. */
@Mixin(Minecraft.class)
public abstract class SmokeProbe {
 private static int stage,ticks,wait,checks;private static boolean failed;
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("KAPKAN105_TARGET_SMOKE_PASS "+label);}
 @Inject(method="tick",at=@At("TAIL")) private void qa105$smoke(CallbackInfo ci){
  if(failed||stage>=99||++ticks%10!=0)return;Minecraft mc=(Minecraft)(Object)this;
  try{
   if(stage==0){if(mc.screen instanceof IntroductionScreen){check(ClientFeatures.settings().onboardingShown,"first-launch introduction state saved on exact target");mc.screen.onClose();stage=1;wait=0;}return;}
   if(stage==1){if(++wait<4)return;check(!(mc.screen instanceof IntroductionScreen),"closed introduction does not repeat");if(mc.screen instanceof TitleScreen&&mc.getOverlay()==null){var data=new ServerData("KapKan exact-target localhost QA","127.0.0.1:"+Integer.getInteger("kapkan.qa.serverPort",25578),ServerData.Type.OTHER);ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(data.ip),data,false,null);stage=2;wait=0;}return;}
   if(mc.player==null||mc.level==null)return;
   if(stage==2){if(++wait<12)return;check(mc.player!=null&&mc.level!=null,"actual matching-version world entered");check(BadgeClient.engine.status().contains("relay"),"matching-version server relay connected");check(ClientFeatures.menuKey.saveString().equals("key.keyboard.j"),"default menu key is J");var s=ClientFeatures.settings();s.coordinates=s.armor=s.effects=s.keystrokes=s.clicks=true;s.save();Files.writeString(mc.gameDirectory.toPath().resolve("joined-ready.txt"),"Actual exact-target world entered\n");stage=3;wait=0;return;}
   if(stage==3){if(++wait<8)return;Screenshot.grab(mc.gameDirectory,"KapKan-1.0.5-"+System.getProperty("kapkan.qa.version")+"-actual-world-HUD.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(ClientFeatures.menuKey.saveString()));stage=4;wait=0;return;}
   if(stage==4){if(++wait<8)return;check(mc.screen instanceof KapkanScreen,"J mapping opens the actual compact menu");check(!mc.screen.children().isEmpty(),"compact menu has interactive controls in exact-target runtime");Screenshot.grab(mc.gameDirectory,"KapKan-1.0.5-"+System.getProperty("kapkan.qa.version")+"-actual-menu.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));check(mc.screen.keyPressed(new KeyEvent(256,0,0)),"Escape closes menu");check(mc.screen==null,"menu returns to actual gameplay");stage=5;wait=0;return;}
   if(stage==5){if(++wait<5)return;check(!(mc.screen instanceof IntroductionScreen),"world and menu events do not repeat welcome");Files.writeString(mc.gameDirectory.toPath().resolve("smoke-passed.txt"),"Actual exact-target Minecraft "+System.getProperty("kapkan.qa.version")+": "+checks+" assertions passed\n");stage=99;}
  }catch(Throwable t){failed=true;t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("smoke-failed.txt"),t.toString());}catch(Exception ignored){}}
 }
}
