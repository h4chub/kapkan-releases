package gg.kapkan.presence.cosmetics;
import net.minecraft.resources.Identifier;
public record CapeFrame(Identifier texture,float[] points) {
    public interface Holder {CapeFrame kapkan$cape();void kapkan$cape(CapeFrame frame);}
}
