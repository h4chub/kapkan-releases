package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.*;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin extends RenderLayer<AvatarRenderState,PlayerModel> {
    protected CapeLayerMixin(RenderLayerParent<AvatarRenderState,PlayerModel> parent){super(parent);}
    @Shadow private boolean hasLayer(ItemStack stack,EquipmentClientInfo.LayerType type){throw new AssertionError();}
    @Inject(method="submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",at=@At("HEAD"),cancellable=true)
    private void kapkan$cloth(PoseStack pose,SubmitNodeCollector collector,int light,AvatarRenderState state,float yaw,float pitch,CallbackInfo ci){
        CapeFrame frame=((CapeFrame.Holder)state).kapkan$cape();if(frame==null)return;ci.cancel();
        if(!state.showCape||state.isInvisible||state.isSpectator||state.isFallFlying||state.isVisuallySwimming||hasLayer(state.chestEquipment,EquipmentClientInfo.LayerType.WINGS))return;
        pose.pushPose();getParentModel().body.translateAndRotate(pose);
        if(hasLayer(state.chestEquipment,EquipmentClientInfo.LayerType.HUMANOID))pose.translate(0,-0.035f,0.075f);
        CapeRenderer.submit(pose,collector,light,frame);pose.popPose();
    }
}
