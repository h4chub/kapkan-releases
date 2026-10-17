package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AvatarRenderer.class)
public class AvatarCapeMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",at=@At("TAIL"))
    private void kapkan$cape(Avatar entity,AvatarRenderState state,float partial,CallbackInfo ci){
        CapeFrame frame=null;var s=ClientFeatures.settings();
        if(entity==Minecraft.getInstance().player&&s.cape){
            String design=ClientFeatures.extractingPreview&&Minecraft.getInstance().screen instanceof KapkanScreen menu?menu.previewDesign():s.design;
            var texture=CosmeticTextures.cape(design);if(texture!=null)frame=new CapeFrame(texture,(ClientFeatures.extractingPreview?ClientFeatures.preview:ClientFeatures.motion).frame(partial,s.reducedMotion));
        }else if(entity!=Minecraft.getInstance().player)frame=SharedCapes.frame(entity,partial);
        ((CapeFrame.Holder)state).kapkan$cape(frame);
    }
}
