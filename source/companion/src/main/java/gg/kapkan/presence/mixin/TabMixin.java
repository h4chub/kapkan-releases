package gg.kapkan.presence.mixin;
import gg.kapkan.presence.BadgeClient;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerTabOverlay.class)
public abstract class TabMixin {
    @Inject(method="getNameForDisplay",at=@At("RETURN"),cancellable=true)
    private void kapkan$tab(PlayerInfo player,CallbackInfoReturnable<Component> cir){cir.setReturnValue(BadgeClient.decorate(cir.getReturnValue(),player.getProfile().id()));}
}
