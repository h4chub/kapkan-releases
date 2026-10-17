package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.ClientFeatures;
import net.minecraft.client.gui.*;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public class ClientHudMixin {
    @Inject(method="extractRenderState",at=@At("TAIL")) private void kapkan$hud(GuiGraphicsExtractor g,DeltaTracker delta,CallbackInfo ci){ClientFeatures.hud(g);}
}
