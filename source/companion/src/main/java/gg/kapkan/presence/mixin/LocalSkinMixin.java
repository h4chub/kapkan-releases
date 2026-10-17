package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.core.ClientAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class)
public class LocalSkinMixin {
    @Inject(method="getSkin",at=@At("RETURN"),cancellable=true) private void kapkan$skin(CallbackInfoReturnable<PlayerSkin> ci){
        if((Object)this!=Minecraft.getInstance().player)return;var s=ClientFeatures.settings();if(!s.skin)return;var texture=CosmeticTextures.skin();if(texture==null)return;
        PlayerSkin original=ci.getReturnValue();ci.setReturnValue(new PlayerSkin(new ClientAsset.ResourceTexture(texture,texture),original.cape(),original.elytra(),s.slim?PlayerModelType.SLIM:PlayerModelType.WIDE,false));
    }
}
