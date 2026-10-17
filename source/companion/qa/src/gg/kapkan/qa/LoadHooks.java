package gg.kapkan.qaloader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
/** Test-only load check: validates Mixin application in Fabric's real class loader. */
public final class LoadHooks implements PreLaunchEntrypoint {
 public void onPreLaunch(){
  if(!Boolean.getBoolean("kapkan.qa.hooksOnly"))return;
  try{
   for(String target:new String[]{"net.minecraft.client.Minecraft","net.minecraft.server.packs.repository.PackRepository","net.minecraft.client.gui.components.PlayerTabOverlay","net.minecraft.client.renderer.entity.EntityRenderer","net.minecraft.client.renderer.entity.LivingEntityRenderer","net.minecraft.client.renderer.entity.player.AvatarRenderer","net.minecraft.client.renderer.entity.state.AvatarRenderState","net.minecraft.client.renderer.entity.layers.CapeLayer","net.minecraft.client.Options","net.minecraft.client.gui.Gui","net.minecraft.client.Camera","net.minecraft.client.player.AbstractClientPlayer","net.minecraft.client.gui.screens.PauseScreen","net.minecraft.client.gui.components.EditBox"}){
    Class.forName(target,false,Thread.currentThread().getContextClassLoader());System.out.println("REAL_GAME_MIXIN_LOAD_PASS "+target);
   }
   System.out.println("HOOK CHECK COMPLETE. This is class loading only, not gameplay.");System.exit(0);
  }catch(Throwable t){t.printStackTrace();System.exit(2);}
 }
}
