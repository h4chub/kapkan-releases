package gg.kapkan.presence.cosmetics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.*;
import java.awt.image.BufferedImage;

public final class MenuStyle {
    public static final int BG=0xFF111318,PANEL=0xFF191C23,CONTROL=0xFF262B34,TEXT=0xFFF2F3F6,MUTED=0xFFA8B0BD,ACCENT=0xFFE295A6,EDGE=0xFF353D49;
    public static final Style FONT=Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("kapkan","ui")));
    private static final class Sprite {final Identifier id;final int bytes;long used;Sprite(Identifier id,int bytes,long frame){this.id=id;this.bytes=bytes;used=frame;}}
    private static final Map<MenuSpriteKey,Sprite> CACHE=new LinkedHashMap<>(64,.75f,true);
    // Extraction is confined to the client thread. Only copies enter CACHE.
    private static final MenuSpriteKey LOOKUP=MenuSpriteKey.probe();
    private static long frame,serial,rasterizations;private static long bytes;private static Object extractor;
    public static long rasterizations(){return rasterizations;}
    public static int cachedSprites(){return CACHE.size();}
    public static Component label(String text){return Component.literal(text).withStyle(FONT);}
    public static int width(String text){return (int)Math.ceil(MenuRaster.width(text));}
    public static String fit(String value,int maxWidth){if(width(value)<=maxWidth)return value;int n=value.length();while(n>0&&width(value.substring(0,n)+"…")>maxWidth)n--;return value.substring(0,n)+"…";}
    public static int mix(int from,int to,float amount){float t=Math.clamp(amount,0,1);int c=0;for(int shift=0;shift<=24;shift+=8)c|=Math.round(((from>>>shift)&255)*(1-t)+((to>>>shift)&255)*t)<<shift;return c;}
    public static void text(GuiGraphicsExtractor g,String text,int x,int y,int color){if(!text.isEmpty())sprite(g,text,x,y,width(text)+2,14,0,color);}
    public static void scaled(GuiGraphicsExtractor g,String text,int x,int y,float scale,int color){g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale,scale);text(g,text,0,0,color);g.pose().popMatrix();}
    public static void rounded(GuiGraphicsExtractor g,int x,int y,int w,int h,int radius,int color){sprite(g,null,x,y,w,h,radius,color);}
    public static void chevron(GuiGraphicsExtractor g,int x,int y,boolean up,int color){sprite(g,null,up?MenuRaster.Icon.UP:MenuRaster.Icon.DOWN,x,y,12,12,0,color);}
    public static void centered(GuiGraphicsExtractor g,String value,int x,int y,int w,int color){text(g,value,x+(w-width(value))/2,y,color);}
    private static final class SmoothTexture extends DynamicTexture {
        SmoothTexture(NativeImage pixels){super(()->"KapKan smooth UI",pixels);sampler=RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);}
    }
    private static void sprite(GuiGraphicsExtractor g,String text,int x,int y,int w,int h,int radius,int color){
        sprite(g,text,null,x,y,w,h,radius,color);
    }
    private static void sprite(GuiGraphicsExtractor g,String text,MenuRaster.Icon icon,int x,int y,int w,int h,int radius,int color){
        if(w<=0||h<=0||(color>>>24)==0)return;
        if(extractor!=g){extractor=g;frame++;trim();}
        double density=Minecraft.getInstance().getWindow().getGuiScale()*Math.hypot(g.pose().m00(),g.pose().m01());
        int pw=Math.max(1,(int)Math.ceil(w*density)),ph=Math.max(1,(int)Math.ceil(h*density));
        MenuSpriteKey lookup=LOOKUP.set(text,icon,w,h,radius,pw,ph);Sprite sprite=CACHE.get(lookup);
        if(sprite==null){
            MenuSpriteKey key=lookup.stored();
            BufferedImage image=icon==null?MenuRaster.paint(text,w,h,radius,pw,ph):MenuRaster.paintIcon(icon,pw,ph);NativeImage pixels=new NativeImage(pw,ph,false);
            int[] argb=image.getRGB(0,0,pw,ph,null,0,pw);var buffer=org.lwjgl.system.MemoryUtil.memIntBuffer(pixels.getPointer(),argb.length);
            for(int i=0;i<argb.length;i++){int c=argb[i];buffer.put(i,(c&0xFF00FF00)|((c&255)<<16)|((c>>>16)&255));}
            Identifier id=Identifier.fromNamespaceAndPath("kapkan","dynamic/ui/"+(serial++));
            Minecraft.getInstance().getTextureManager().register(id,new SmoothTexture(pixels));
            sprite=new Sprite(id,pw*ph*4,frame);CACHE.put(key,sprite);bytes+=sprite.bytes;rasterizations++;
        }
        sprite.used=frame;
        g.blit(RenderPipelines.GUI_TEXTURED,sprite.id,x,y,0,0,w,h,pw,ph,pw,ph,color);
    }
    private static void trim(){
        var it=CACHE.entrySet().iterator();
        while(it.hasNext()&&(CACHE.size()>384||bytes>16*1024*1024)){
            Sprite value=it.next().getValue();
            // Keep textures referenced by the current and immediately preceding extracted frames.
            if(frame-value.used<3)continue;
            Minecraft.getInstance().getTextureManager().release(value.id);bytes-=value.bytes;it.remove();
        }
    }
}
