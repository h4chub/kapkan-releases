package gg.kapkan.presence.cosmetics;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** Rasterizes at framebuffer resolution, never at Minecraft's coarse GUI resolution. */
public final class MenuRaster {
    public enum Icon {UP,DOWN}
    public static final float SIZE=11;
    private static final Font FONT=load();
    private static final FontRenderContext FRC=new FontRenderContext(null,true,true);
    private static Font load(){try(var in=MenuRaster.class.getResourceAsStream("/assets/kapkan/font/menu.ttf")){return Font.createFont(Font.TRUETYPE_FONT,in).deriveFont(SIZE);}catch(Exception e){return new Font(Font.SANS_SERIF,Font.PLAIN,11);}}
    public static float width(String text){return (float)FONT.getStringBounds(text,FRC).getWidth();}
    public static BufferedImage paintIcon(Icon icon,int pw,int ph){
        BufferedImage image=new BufferedImage(pw,ph,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.scale(pw/12.0,ph/12.0);g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
        var path=new java.awt.geom.Path2D.Float();boolean up=icon==Icon.UP;
        path.moveTo(2.5,up?8:4);path.lineTo(6,up?4:8);path.lineTo(9.5,up?8:4);g.draw(path);g.dispose();return image;
    }
    public static BufferedImage paint(String text,int w,int h,float radius,int pw,int ph){
        BufferedImage image=new BufferedImage(pw,ph,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.scale(pw/(double)w,ph/(double)h);g.setColor(Color.WHITE);
        if(text!=null){g.setFont(FONT);g.drawString(text,0,FONT.getLineMetrics(text.isEmpty()?" ":text,FRC).getAscent());}
        else g.fill(new RoundRectangle2D.Float(0,0,w,h,radius*2,radius*2));
        g.dispose();return image;
    }
}
