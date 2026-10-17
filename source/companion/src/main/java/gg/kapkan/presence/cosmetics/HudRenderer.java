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
    private static List<Widget> cached=List.of();
    private static long cachedAt;
    private static Object cachedPlayer;
    public static List<Widget> widgets(){
        Minecraft mc=Minecraft.getInstance();long now=System.nanoTime();if(cachedPlayer==mc.player&&!cached.isEmpty()&&now-cachedAt<100_000_000L)return cached;cachedPlayer=mc.player;cachedAt=now;var result=new ArrayList<Widget>();
        result.add(text("fps",mc.getFps()+" FPS"));
        result.add(text("coordinates",mc.player==null?"Coordinates: no world":mc.player.getBlockX()+"  "+mc.player.getBlockY()+"  "+mc.player.getBlockZ()));
        result.add(new Widget("keys",76,72,List.of(),List.of()));result.add(new Widget("clicks",96,25,List.of(),List.of()));
        var armorLines=new ArrayList<String>();var armorItems=new ArrayList<ItemStack>();
        EquipmentSlot[] slots={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};String[] names={"Head","Chest","Legs","Feet"};
        for(int i=0;i<slots.length;i++){ItemStack item=mc.player==null?ItemStack.EMPTY:mc.player.getItemBySlot(slots[i]);armorItems.add(item);
            armorLines.add(item.isEmpty()?names[i]+" · empty":item.isDamageableItem()?Math.max(0,item.getMaxDamage()-item.getDamageValue())+" / "+item.getMaxDamage():"Unbreakable");
        }
        result.add(new Widget("armor",width(armorLines)+25,88,List.copyOf(armorLines),List.copyOf(armorItems)));
        var effects=new ArrayList<String>();
        if(mc.player!=null){List<MobEffectInstance> sorted=mc.player.getActiveEffects().stream().sorted(Comparator.comparing(e->Component.translatable(e.getDescriptionId()).getString())).toList();
            for(MobEffectInstance effect:sorted.stream().limit(8).toList()){
                String label=Component.translatable(effect.getDescriptionId()).getString();if(effect.getAmplifier()>0)label+=" "+(effect.getAmplifier()+1);
                effects.add(label+" · "+duration(effect.getDuration(),effect.isInfiniteDuration()));
            }if(sorted.size()>8)effects.add("+"+(sorted.size()-8)+" more effects");
        }if(effects.isEmpty())effects.add("No active effects");
        result.add(new Widget("effects",Math.min(236,width(effects)+16),effects.size()*19+10,List.copyOf(effects),List.of()));
        String sprint=ClientFeatures.sprintLatched()?(mc.player!=null&&mc.player.isSprinting()?"Sprint · active":"Sprint · armed"):"Sprint · off";result.add(text("sprint",sprint));cached=List.copyOf(result);return cached;
    }
    public static String duration(int ticks,boolean infinite){if(infinite)return "∞";int seconds=Math.max(0,(ticks+19)/20);return seconds/60+":"+String.format(Locale.ROOT,"%02d",seconds%60);}
    private static Widget text(String id,String value){return new Widget(id,MenuStyle.width(value)+18,23,List.of(value),List.of());}
    private static int width(List<String> lines){return lines.stream().mapToInt(MenuStyle::width).max().orElse(32);}
    public static Map<String,HudLayout.Bounds> render(GuiGraphicsExtractor g,ClientSettings s,boolean editor,String selected){
        Map<String,HudLayout.Bounds> bounds=new LinkedHashMap<>();Minecraft mc=Minecraft.getInstance();
        for(Widget widget:widgets()){
            boolean active=enabled(s,widget.id());if(!active&&!editor)continue;
            HudLayout.Bounds b=s.hud.bounds(widget.id(),g.guiWidth(),g.guiHeight(),widget.width(),widget.height());bounds.put(widget.id(),b);
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
