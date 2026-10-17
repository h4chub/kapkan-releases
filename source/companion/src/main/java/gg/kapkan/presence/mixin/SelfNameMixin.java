package gg.kapkan.presence.mixin;
import gg.kapkan.presence.BadgeClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntityRenderer.class)
public abstract class SelfNameMixin {
    @Inject(method="shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z",at=@At("RETURN"),cancellable=true)
    private void kapkan$self(LivingEntity player,double distance,CallbackInfoReturnable<Boolean> cir){
        if(player==Minecraft.getInstance().player&&BadgeClient.selfNameAllowed(player,distance))cir.setReturnValue(true);
    }
}
