package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.ClientFeatures;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Camera.class)
public class ZoomMixin {
    @Inject(method="calculateFov",at=@At("RETURN"),cancellable=true) private void kapkan$zoom(float partial,CallbackInfoReturnable<Float> ci){ci.setReturnValue(ClientFeatures.fov(ci.getReturnValue()));}
}
