package gg.kapkan.presence.mixin;
import gg.kapkan.presence.cosmetics.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PauseScreen.class)
public abstract class PauseMenuMixin extends Screen {
    protected PauseMenuMixin(Component title){super(title);}
    @Inject(method="init",at=@At("TAIL")) private void kapkan$button(CallbackInfo ci){addRenderableWidget(new MenuButton(width-94,8,86,22,"KapKan",()->minecraft.setScreen(new KapkanScreen(this))));}
}
