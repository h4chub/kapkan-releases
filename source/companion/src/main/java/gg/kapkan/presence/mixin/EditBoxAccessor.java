package gg.kapkan.presence.mixin;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
/** Read native selection/composition state; editing behavior remains Minecraft's. */
@Mixin(EditBox.class)
public interface EditBoxAccessor {
    @Accessor("highlightPos") int kapkan$highlightPos();
    @Accessor("preeditOverlay") IMEPreeditOverlay kapkan$preeditOverlay();
}
