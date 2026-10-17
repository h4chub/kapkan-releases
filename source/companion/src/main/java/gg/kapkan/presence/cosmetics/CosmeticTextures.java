package gg.kapkan.presence.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.*;

public final class CosmeticTextures {
    private static final Map<String,Identifier> CAPES=new HashMap<>();
    private static Identifier skin;
    private static boolean failedSkin;
    private static final Set<String> failedCapes=new HashSet<>();
    public static String error="";
    public static Identifier cape(String design){
        if(failedCapes.contains(design))return null;
        return CAPES.computeIfAbsent(design,key->{try{return upload("cape/"+key,CapeArt.make(key,ClientFeatures.settings()));}catch(Exception e){failedCapes.add(key);error="Cape could not load. Re-import it in Studio.";return null;}});
    }
    public static void invalidate(String name){failedCapes.remove(name);error="";Identifier id=CAPES.remove(name);if(id!=null)release(id);}
    public static void release(Identifier id){CapeRenderer.forget(id);Minecraft.getInstance().getTextureManager().release(id);}
    public static Identifier shared(gg.kapkan.presence.CapeData data)throws IOException {
        Identifier id=Identifier.fromNamespaceAndPath("kapkan","dynamic/cape/shared/"+data.hash());
        return shared(data,NativeImage.read(data.png()));
    }
    /** Pixel decoding may occur on a worker; call this upload overload only on the client thread. */
    public static Identifier shared(gg.kapkan.presence.CapeData data,NativeImage pixels){
        Identifier id=Identifier.fromNamespaceAndPath("kapkan","dynamic/cape/shared/"+data.hash());
        Minecraft.getInstance().getTextureManager().register(id,new CapeTexture(pixels));return id;
    }
    public static Identifier upload(String name,BufferedImage image) throws IOException {
        Identifier id=Identifier.fromNamespaceAndPath("kapkan","dynamic/"+name);NativeImage pixels=NativeImage.read(CapeArt.png(image));
        Minecraft.getInstance().getTextureManager().register(id,name.startsWith("cape/")?new CapeTexture(pixels):new DynamicTexture(()->"KapKan "+name,pixels));return id;
    }
    private static final class CapeTexture extends DynamicTexture {
        CapeTexture(NativeImage pixels){super(()->"KapKan cape",pixels);sampler=com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getClampToEdge(com.mojang.blaze3d.textures.FilterMode.LINEAR);}
    }
    public static Identifier skin(){
        if(skin==null&&!failedSkin&&!ClientFeatures.settings().skinFile.isBlank())try{skin=upload("skin/local",readSkin(ClientFeatures.settings().file.resolveSibling("skins").resolve(ClientFeatures.settings().skinFile)));}catch(Exception e){failedSkin=true;error="Skin could not load. Re-import it in Skins.";}
        return skin;
    }
    public static void invalidateSkin(){if(skin!=null)Minecraft.getInstance().getTextureManager().release(skin);skin=null;failedSkin=false;error="";}
    public static BufferedImage readSkin(Path path) throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>2*1024*1024)throw new IOException("Choose a 64 x 64 skin PNG smaller than 2 MB.");
        try(var stream=ImageIO.createImageInputStream(path.toFile())){
            var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("Choose a PNG skin.");var reader=readers.next();
            try{reader.setInput(stream);if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)!=64||reader.getHeight(0)!=64)throw new IOException("Use a modern 64 x 64 Minecraft skin PNG.");return reader.read(0);}finally{reader.dispose();}
        }
    }
}
