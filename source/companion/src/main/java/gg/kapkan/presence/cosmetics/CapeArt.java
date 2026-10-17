package gg.kapkan.presence.cosmetics;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;

/** Original code-drawn textiles. Custom PNGs are normalized and kept locally. */
public final class CapeArt {
    public static final String[] DESIGNS={"black","ivory","burgundy"};
    public static final String[] NAMES={"Black","Ivory","Burgundy"};
    public static final int WIDTH=320,HEIGHT=512;
    public static BufferedImage make(String design,ClientSettings s) throws IOException {
        return make(design,s.base,s.accent,s.pattern,s.emblem,s.capes.resolve(s.customFile));
    }
    public static BufferedImage make(String design,int base,int accent,String pattern,boolean emblem,Path custom) throws IOException {
        if(design.equals("custom"))return read(custom);
        switch(design){case "black","crimson"->{base=0x000000;accent=0xFFFFFF;pattern="Solid";emblem=true;}case "ivory","glacier"->{base=0xF3F1EC;accent=0x111214;pattern="Solid";emblem=true;}case "burgundy","royal"->{base=0x4E1726;accent=0xFFFFFF;pattern="Solid";emblem=true;}}
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(base));g.fillRect(0,0,WIDTH,HEIGHT);g.setColor(new Color(accent));
        // Optional studio patterns remain available; the three stock capes are plain.
        if(pattern.equals("Border")){g.setStroke(new BasicStroke(3));g.drawRect(20,20,WIDTH-40,HEIGHT-40);}
        else if(pattern.equals("Chevron")){g.fillPolygon(new int[]{0,160,320,320,160,0},new int[]{356,436,356,372,452,372},6);}
        else if(pattern.equals("Diamond")){g.setStroke(new BasicStroke(3));g.drawPolygon(new int[]{160,286,160,34},new int[]{104,256,408,256},4);}
        if(emblem){try(var in=CapeArt.class.getResourceAsStream("/assets/kapkan/textures/font/double_k.png")){
            BufferedImage source=ImageIO.read(in),logo=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<logo.getHeight();y++)for(int x=0;x<logo.getWidth();x++)logo.setRGB(x,y,(source.getRGB(x,y)&0xFF000000)|accent);
            int w=116,h=Math.round(w*(float)logo.getHeight()/logo.getWidth());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(logo,(WIDTH-w)/2,(HEIGHT-h)/2,w,h,null);
        }}
        g.dispose();return image;
    }
    public static BufferedImage read(Path file) throws IOException {
        if(!Files.isRegularFile(file)||Files.size(file)>2*1024*1024)throw new IOException("Choose a PNG smaller than 2 MB.");
        try(ImageInputStream in=ImageIO.createImageInputStream(file.toFile())){
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);if(!readers.hasNext())throw new IOException("This file is not a readable PNG.");
            ImageReader reader=readers.next();try{
                if(!reader.getFormatName().equalsIgnoreCase("png"))throw new IOException("Choose a PNG image.");reader.setInput(in);int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<10||h<16||w>1024||h>1024||!((w*8==h*5)||(w==h*2&&w%64==0)))throw new IOException("Use a 5:8 PNG (80 x 128), or a 64 x 32 cape atlas.");
                BufferedImage source=reader.read(0);if(w==h*2){int unit=w/64;source=source.getSubimage(unit,unit,10*unit,16*unit);}
                BufferedImage result=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);Graphics2D g=result.createGraphics();g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);g.drawImage(source,0,0,WIDTH,HEIGHT,null);g.dispose();return result;
            }finally{reader.dispose();}
        }
    }
    public static Path saveNew(BufferedImage image,Path folder,String label) throws IOException {
        Files.createDirectories(folder);String clean=label.replaceAll("[^A-Za-z0-9_-]","-").replaceAll("-+","-");if(clean.isBlank())clean="Custom";clean=clean.substring(0,Math.min(45,clean.length()));
        Path target=folder.resolve(clean+"-"+UUID.randomUUID().toString().substring(0,8)+".png");Path temp=Files.createTempFile(folder,"import-",".tmp");
        try{if(!ImageIO.write(image,"PNG",temp.toFile()))throw new IOException("Could not write PNG.");Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE);return target;}finally{Files.deleteIfExists(temp);}
    }
    public static java.util.List<Path> library(Path folder){try(var files=Files.list(folder)){return files.filter(p->p.getFileName().toString().matches("[A-Za-z0-9_-]{1,90}\\.png")).sorted().limit(128).toList();}catch(IOException e){return java.util.List.of();}}
    public static byte[] png(BufferedImage image) throws IOException {var bytes=new ByteArrayOutputStream();ImageIO.write(image,"PNG",bytes);return bytes.toByteArray();}
}
