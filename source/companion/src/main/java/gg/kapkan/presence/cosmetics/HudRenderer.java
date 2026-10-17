package gg.kapkan.presence.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** The editor and gameplay share exactly the same widget geometry and live values. */
public final class HudRenderer {
    public record Widget(String id,int width,int height,List<String> lines,List<ItemStack> items){}
    public static String name(String id){return switch(id){case "fps"->"FPS";case "coordinates"->"Coordinates";case "keys"->"Keystrokes";case "clicks"->"Mouse clicks";case "armor"->"Armor durability";case "effects"->"Active effects";case "sprint"->"Sprint status";default->id;};}
    public static boolean enabled(ClientSettings s,String id){return switch(id){case "fps"->s.fps;case "coordinates"->s.coordinates;case "keys"->s.keystrokes;case "clicks"->s.clicks;case "armor"->s.armor;case "effects"->s.effects;case "sprint"->s.sprintIndicator&&s.toggleSprint;default->false;};}
    public static void toggle(ClientSettings s,String id){switch(id){case "fps"->s.fps=!s.fps;case "coordinates"->s.coordinates=!s.coordinates;case "keys"->s.keystrokes=!s.keystrokes;case "clicks"->s.clicks=!s.clicks;case "armor"->s.armor=!s.armor;case "effects"->s.effects=!s.effects;case "sprint"->s.sprintIndicator=!s.sprintIndicator;}}
    private static final int ALL_WIDGETS=(1<<7)-1;
    private static final HudRefreshCache<List<Widget>> CACHE=new HudRefreshCache<>();
    private static final Widget KEYS=new Widget("keys",76,72,List.of(),List.of()),CLICKS=new Widget("clicks",96,25,List.of(),List.of());
    private static final EquipmentSlot[] ARMOR_SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private static final String[] ARMOR_NAMES={"Head","Chest","Legs","Feet"};
    private record EffectName(MobEffectInstance effect,String label){}
    private static final Comparator<EffectName> EFFECT_NAMES=Comparator.comparing(EffectName::label);
    static int enabledMask(ClientSettings s){
        return (s.fps?1:0)|(s.coordinates?2:0)|(s.keystrokes?4:0)|(s.clicks?8:0)|(s.armor?16:0)|(s.effects?32:0)|(s.sprintIndicator&&s.toggleSprint?64:0);
    }
    public static List<Widget> widgets(){
        return widgets(ALL_WIDGETS);
    }
    private static List<Widget> widgets(int mask){
        Minecraft mc=Minecraft.getInstance();return CACHE.get(mc.player,mask,System.nanoTime(),HudRenderer::refresh);
    }
    private static List<Widget> refresh(int mask){
        if(mask==0)return List.of();
        Minecraft mc=Minecraft.getInstance();var result=new ArrayList<Widget>(Integer.bitCount(mask));
        if((mask&1)!=0)result.add(text("fps",mc.getFps()+" FPS"));
        if((mask&2)!=0)result.add(text("coordinates",mc.player==null?"Coordinates: no world":mc.player.getBlockX()+"  "+mc.player.getBlockY()+"  "+mc.player.getBlockZ()));
        if((mask&4)!=0)result.add(KEYS);if((mask&8)!=0)result.add(CLICKS);
        if((mask&16)!=0){
            var armorLines=new ArrayList<String>(4);var armorItems=new ArrayList<ItemStack>(4);
            for(int i=0;i<ARMOR_SLOTS.length;i++){ItemStack item=mc.player==null?ItemStack.EMPTY:mc.player.getItemBySlot(ARMOR_SLOTS[i]);armorItems.add(item);
                armorLines.add(item.isEmpty()?ARMOR_NAMES[i]+" · empty":item.isDamageableItem()?Math.max(0,item.getMaxDamage()-item.getDamageValue())+" / "+item.getMaxDamage():"Unbreakable");
            }
            result.add(new Widget("armor",width(armorLines)+25,88,List.copyOf(armorLines),List.copyOf(armorItems)));
        }
        if((mask&32)!=0){
            var effects=new ArrayList<String>();
            if(mc.player!=null){
                var sorted=new ArrayList<EffectName>(mc.player.getActiveEffects().size());
                for(MobEffectInstance effect:mc.player.getActiveEffects())sorted.add(new EffectName(effect,Component.translatable(effect.getDescriptionId()).getString()));
                sorted.sort(EFFECT_NAMES);
                for(int i=0;i<Math.min(8,sorted.size());i++){
                    EffectName named=sorted.get(i);MobEffectInstance effect=named.effect();String label=named.label();if(effect.getAmplifier()>0)label+=" "+(effect.getAmplifier()+1);
                    effects.add(label+" · "+duration(effect.getDuration(),effect.isInfiniteDuration()));
                }if(sorted.size()>8)effects.add("+"+(sorted.size()-8)+" more effects");
            }if(effects.isEmpty())effects.add("No active effects");
            result.add(new Widget("effects",Math.min(236,width(effects)+16),effects.size()*19+10,List.copyOf(effects),List.of()));
        }
        if((mask&64)!=0){String sprint=ClientFeatures.sprintLatched()?(mc.player!=null&&mc.player.isSprinting()?"Sprint · active":"Sprint · armed"):"Sprint · off";result.add(text("sprint",sprint));}
        return List.copyOf(result);
    }
    public static String duration(int ticks,boolean infinite){if(infinite)return "∞";long seconds=Math.max(0,((long)ticks+19)/20),remainder=seconds%60;return seconds/60+":"+(remainder<10?"0":"")+remainder;}
    private static Widget text(String id,String value){return new Widget(id,MenuStyle.width(value)+18,23,List.of(value),List.of());}
    private static int width(List<String> lines){int result=lines.isEmpty()?32:0;for(String line:lines)result=Math.max(result,MenuStyle.width(line));return result;}
    /** Gameplay never consumes editor hit-test bounds. Keep that map out of its frame loop. */
    public static void renderGameplay(GuiGraphicsExtractor g,ClientSettings s){render(g,s,false,null,false);}
    public static Map<String,HudLayout.Bounds> render(GuiGraphicsExtractor g,ClientSettings s,boolean editor,String selected){
        return render(g,s,editor,selected,true);
    }
    private static Map<String,HudLayout.Bounds> render(GuiGraphicsExtractor g,ClientSettings s,boolean editor,String selected,boolean collectBounds){
        Map<String,HudLayout.Bounds> bounds=collectBounds?new LinkedHashMap<>():Map.of();Minecraft mc=Minecraft.getInstance();
        for(Widget widget:widgets(editor?ALL_WIDGETS:enabledMask(s))){
            boolean active=enabled(s,widget.id());if(!active&&!editor)continue;
            HudLayout.Bounds b=s.hud.bounds(widget.id(),g.guiWidth(),g.guiHeight(),widget.width(),widget.height());if(collectBounds)bounds.put(widget.id(),b);
            double safeScale=Math.min(s.hud.get(widget.id()).scale,Math.min((double)b.width()/widget.width(),(double)b.height()/widget.height()));
            if(editor){g.outline(b.x()-1,b.y()-1,b.width()+2,b.height()+2,widget.id().equals(selected)?0xFFE3A67D:active?0xFF6D727D:0xFF343840);}
            g.pose().pushMatrix();g.pose().translate(b.x(),b.y());g.pose().scale((float)safeScale,(float)safeScale);
            MenuStyle.rounded(g,0,0,widget.width(),widget.height(),4,active?0xDA111419:0xA0111419);
            int text=active?0xFFF0F2F5:0xFF858992;
            if(widget.id().equals("keys")){
                key(g,mc.options.keyUp.getTranslatedKeyMessage().getString(),26,3,24,21,mc.options.keyUp.isDown(),text);
                key(g,mc.options.keyLeft.getTranslatedKeyMessage().getString(),1,27,24,21,mc.options.keyLeft.isDown(),text);
                key(g,mc.options.keyDown.getTranslatedKeyMessage().getString(),26,27,24,21,mc.options.keyDown.isDown(),text);
                key(g,mc.options.keyRight.getTranslatedKeyMessage().getString(),51,27,24,21,mc.options.keyRight.isDown(),text);
                key(g,"Jump",1,51,74,18,mc.options.keyJump.isDown(),text);
            }else if(widget.id().equals("clicks")){
                key(g,"LMB",3,3,43,19,mc.mouseHandler.isLeftPressed(),text);key(g,"RMB",50,3,43,19,mc.mouseHandler.isRightPressed(),text);
            }else if(widget.id().equals("armor")){
                for(int i=0;i<widget.lines().size();i++){ItemStack item=widget.items().get(i);if(!item.isEmpty())g.item(item,5,5+i*20);MenuStyle.text(g,widget.lines().get(i),26,7+i*20,text);}
            }else{for(int i=0;i<widget.lines().size();i++)MenuStyle.text(g,widget.lines().get(i),8,5+i*19,text);}
            g.pose().popMatrix();
        }return bounds;
    }
    private static void key(GuiGraphicsExtractor g,String label,int x,int y,int width,int height,boolean held,int text){
        MenuStyle.rounded(g,x,y,width,height,3,held?0xFFF2F1ED:0xFF282D35);
        if(MenuStyle.width(label)>width-4)label=label.length()>3?label.substring(0,3):label;
        MenuStyle.centered(g,label,x,y+(height-14)/2,width,held?0xFF15171B:text);
    }
}
