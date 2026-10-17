package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.ClientFeatures;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Synthesizes only vanilla's sprint input; vanilla hunger, collision, sneak and water rules remain active. */
@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @Inject(method="tick",at=@At("TAIL")) private void kapkan$sprint(CallbackInfo ci){
        if(!ClientFeatures.sprintRequested())return;ClientInput self=(ClientInput)(Object)this;Input input=self.keyPresses;
        // Modern vanilla can retain its sprint flag while crouching. KapKan suspends its own latch during sneak.
        if(input.shift()){var player=Minecraft.getInstance().player;if(player!=null)player.setSprinting(false);return;}
        self.keyPresses=new Input(input.forward(),input.backward(),input.left(),input.right(),input.jump(),input.shift(),true);
    }
}
