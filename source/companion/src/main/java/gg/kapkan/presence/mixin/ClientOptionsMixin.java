package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.ClientFeatures;
import net.minecraft.client.*;
import java.util.Arrays;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Options.class)
public abstract class ClientOptionsMixin {
    @Shadow @Final @Mutable public KeyMapping[] keyMappings;
    @Inject(method="load",at=@At("HEAD")) private void kapkan$keys(CallbackInfo ci){
        ClientFeatures.keys();if(Arrays.asList(keyMappings).contains(ClientFeatures.menuKey))return;
        keyMappings=Arrays.copyOf(keyMappings,keyMappings.length+3);keyMappings[keyMappings.length-3]=ClientFeatures.menuKey;keyMappings[keyMappings.length-2]=ClientFeatures.zoomKey;keyMappings[keyMappings.length-1]=ClientFeatures.toggleSprintKey;
    }
}
