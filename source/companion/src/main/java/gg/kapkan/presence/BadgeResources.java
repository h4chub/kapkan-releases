package gg.kapkan.presence;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import java.io.*;
import java.util.*;

/** Two original badge assets, exposed as a built-in client pack without requiring Fabric API. */
public final class BadgeResources extends AbstractPackResources {
    private static final Set<String> FILES=Set.of("font/badge.json","textures/font/double_k_glyph.png","font/ui.json","font/ui.ttf","lang/en_us.json");
    public BadgeResources(){super(new PackLocationInfo("kapkan_badges",Component.literal("KapKan badges"),PackSource.BUILT_IN,Optional.empty()));}
    public IoSupplier<InputStream> getRootResource(String...elements){return null;}
    public Set<String> getNamespaces(PackType type){return type==PackType.CLIENT_RESOURCES?Set.of("kapkan"):Set.of();}
    public IoSupplier<InputStream> getResource(PackType type,Identifier id){
        if(type!=PackType.CLIENT_RESOURCES||!id.getNamespace().equals("kapkan")||!FILES.contains(id.getPath()))return null;
        return ()->{InputStream in=BadgeResources.class.getResourceAsStream("/assets/kapkan/"+id.getPath());if(in==null)throw new FileNotFoundException(id.toString());return in;};
    }
    public void listResources(PackType type,String namespace,String prefix,PackResources.ResourceOutput output){
        if(type!=PackType.CLIENT_RESOURCES||!namespace.equals("kapkan"))return;
        for(String name:FILES)if(prefix.isEmpty()||name.startsWith(prefix+"/")){
            Identifier id=Identifier.fromNamespaceAndPath("kapkan",name);output.accept(id,getResource(type,id));
        }
    }
    public void close(){}
}
