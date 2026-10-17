package gg.kapkan.presence;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
/** Original component is appended intact under an empty parent, so its style cannot inherit badge formatting. */
public final class BadgeText {
    private static final FontDescription FONT=new FontDescription.Resource(Identifier.fromNamespaceAndPath("kapkan","badge"));
    private static final Component PREFIX=Component.literal("\ue000\ue001").withStyle(Style.EMPTY.withFont(FONT).withColor(0xFFFFFF).withBold(false).withItalic(false).withObfuscated(false).withUnderlined(false).withStrikethrough(false).withoutShadow());
    public static Component prepend(Component original){return Component.empty().append(PREFIX).append(original);}
    public static boolean hasBadge(Component text){return !text.getSiblings().isEmpty()&&text.getSiblings().getFirst().equals(PREFIX);}
    public static Component prefix(){return PREFIX;}
}
