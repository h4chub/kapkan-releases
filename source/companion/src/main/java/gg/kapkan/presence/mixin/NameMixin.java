package gg.kapkan.presence.mixin;
import gg.kapkan.presence.BadgeClient;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderer.class)
public abstract class NameMixin {
    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",at=@At("TAIL"))
    private void kapkan$name(Entity player,EntityRenderState state,float delta,CallbackInfo ci){
        if(player instanceof Player&&state.nameTag!=null)state.nameTag=BadgeClient.decorate(state.nameTag,player.getUUID());
    }
}
