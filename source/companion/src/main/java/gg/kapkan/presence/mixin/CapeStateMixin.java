package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.CapeFrame;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.*;
@Mixin(AvatarRenderState.class)
public class CapeStateMixin implements CapeFrame.Holder {
    @Unique private CapeFrame kapkan$frame;
    public CapeFrame kapkan$cape(){return kapkan$frame;}
    public void kapkan$cape(CapeFrame frame){kapkan$frame=frame;}
}
