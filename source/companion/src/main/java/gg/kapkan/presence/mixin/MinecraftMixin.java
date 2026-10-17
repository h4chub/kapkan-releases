package gg.kapkan.presence.mixin;
import gg.kapkan.presence.BadgeClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method="tick",at=@At("TAIL")) private void kapkan$tick(CallbackInfo ci){BadgeClient.tick((Minecraft)(Object)this);gg.kapkan.presence.cosmetics.ClientFeatures.tick((Minecraft)(Object)this);}
    @Inject(method="disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V",at=@At("HEAD")) private void kapkan$leave(CallbackInfo ci){if(BadgeClient.engine!=null)BadgeClient.engine.snapshot(null);}
    @Inject(method="close",at=@At("HEAD")) private void kapkan$close(CallbackInfo ci){if(BadgeClient.engine!=null)BadgeClient.engine.close();}
}
