package gg.kapkan.presence;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
public final class RenderingPolicyTest {
    static int checks;static void check(boolean b,String s){if(!b)throw new AssertionError(s);checks++;}
    public static void main(String[] args){
        var original=Component.literal("[ADMIN] ").withStyle(ChatFormatting.GOLD).append(Component.literal("A_very_long_display_name_0123456789").withStyle(ChatFormatting.RED,ChatFormatting.BOLD)).append(Component.literal(" [guild]").withStyle(ChatFormatting.ITALIC));
        var before=original.copy();var result=BadgeText.prepend(original);
        check(result.getString().equals("\ue000\ue001"+original.getString()),"Exact prefix and suffix preservation");
        check(result.getSiblings().get(1)==original,"Server-provided component retained intact");check(original.equals(before),"Original unmodified");
        var style=result.getSiblings().getFirst().getStyle();check(style.getColor().getValue()==0xFFFFFF,"White badge");check(!style.isBold()&&!style.isItalic()&&!style.isObfuscated(),"Logo styling isolated");check(result.getStyle().equals(Style.EMPTY),"No inherited colour/font on original name");
        check(BadgeVisibility.self(true,false,true,false,false,false,false,false,4,true),"Third-person permitted");
        check(!BadgeVisibility.self(true,true,true,false,false,false,false,false,4,true),"First person hidden");
        check(!BadgeVisibility.self(false,false,true,false,false,false,false,false,4,true),"Opt-out or unavailable hidden");
        check(!BadgeVisibility.self(true,false,false,false,false,false,false,false,4,true),"Game render-name rule");
        check(!BadgeVisibility.self(true,false,true,true,false,false,false,false,4,true),"F1 hides self");
        check(!BadgeVisibility.self(true,false,true,false,true,false,false,false,4,true),"Invisibility respected");
        check(!BadgeVisibility.self(true,false,true,false,false,true,false,false,4,true),"Spectator hidden");
        check(!BadgeVisibility.self(true,false,true,false,false,false,true,false,4,true),"Vehicle rule");
        check(!BadgeVisibility.self(true,false,true,false,false,false,false,false,4096,true),"64 block limit");
        check(!BadgeVisibility.self(true,false,true,false,false,false,false,true,1024,true),"Sneak range limit");
        check(!BadgeVisibility.self(true,false,true,false,false,false,false,false,4,false),"Team hides self");
        System.out.println("Text components and visibility policy: "+checks+" assertions passed using real Minecraft classes. Not rendered gameplay.");
    }
}
