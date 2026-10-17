package gg.kapkan.presence.mixin;

import gg.kapkan.presence.BadgeText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.feature.NameTagFeatureRenderer;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Brighten only the visible badge. Vanilla occluded text and player-name styling are retained. */
@Mixin(NameTagFeatureRenderer.class)
public class BadgeLightMixin {
    @Redirect(method="renderTranslucent",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",ordinal=1))
    private void kapkan$whiteBadge(Font font,Component text,float x,float y,int color,boolean shadow,
            Matrix4fc pose,MultiBufferSource buffers,Font.DisplayMode mode,int background,int light){
        font.drawInBatch(text,x,y,color,shadow,pose,buffers,mode,background,light);
        if(mode==Font.DisplayMode.NORMAL&&BadgeText.hasBadge(text))
            font.drawInBatch(BadgeText.prefix(),x,y,0xFFFFFFFF,false,pose,buffers,Font.DisplayMode.NORMAL,0,0xF000F0);
    }
}
