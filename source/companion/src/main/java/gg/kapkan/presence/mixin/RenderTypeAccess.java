package gg.kapkan.presence.mixin;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(RenderType.class)
public interface RenderTypeAccess {
    @Invoker("create") static RenderType kapkan$create(String name,RenderSetup setup){throw new AssertionError();}
}
