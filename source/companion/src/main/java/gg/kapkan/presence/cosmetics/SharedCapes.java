package gg.kapkan.presence.cosmetics;

import gg.kapkan.presence.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

/** Network images stay on the presence worker; GPU uploads and cloth stay on the client thread. */
public final class SharedCapes {
    private record Design(boolean enabled,String design,int base,int accent,String pattern,boolean emblem,Path file,String material){}
    private static final ExecutorService encoder=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),r->{Thread t=new Thread(r,"kapkan-cape-encoder");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardOldestPolicy());
    private static Design requested;
    private static final Map<UUID,Motion> players=new HashMap<>();
    private static final Map<String,Identifier> textures=new HashMap<>();
    private static Object world;
    private static int generation;
    private static final Map<String,Integer> decoding=new HashMap<>();
    private static final Map<String,Long> failed=new HashMap<>();
    private static final ExecutorService decoder=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(2),r->{Thread t=new Thread(r,"kapkan-cape-pixels");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final class Motion {
        final CapeMotion cloth=new CapeMotion();double x,y,z;String hash,material;
        Motion(Avatar player){x=player.getX();y=player.getY();z=player.getZ();}
        void tick(Avatar player,PresenceEngine.RemoteCape cape,boolean reduced){
            double dx=player.getX()-x,dy=player.getY()-y,dz=player.getZ()-z;
            if(Math.abs(dx)+Math.abs(dy)+Math.abs(dz)>3)cloth.reset();else cloth.tick(Math.hypot(dx,dz),dy,cape.material(),reduced);
            x=player.getX();y=player.getY();z=player.getZ();hash=cape.data().hash();material=cape.material();
        }
    }
    public static void tick(Minecraft mc){
        if(world!=mc.level){world=mc.level;generation++;decoding.clear();failed.clear();players.clear();textures.values().forEach(CosmeticTextures::release);textures.clear();}
        if(mc.player==null||mc.level==null||BadgeClient.engine==null)return;
        ClientSettings s=ClientFeatures.settings();Design next=new Design(s.cape,s.design,s.base,s.accent,s.pattern,s.emblem,s.capes.resolve(s.customFile),s.material);
        if(!next.equals(requested)){
            requested=next;
            // Hide a removed/changed cape immediately; only the latest completed encoding can publish.
            BadgeClient.engine.publishCape(null,next.material);
            if(next.enabled)encoder.execute(()->{
                try{CapeData data=CapeData.fromPng(CapeArt.png(CapeArt.make(next.design,next.base,next.accent,next.pattern,next.emblem,next.file)));
                    mc.execute(()->{if(next.equals(requested))BadgeClient.engine.publishCape(data,next.material);});
                }catch(Exception ignored){} // A missing local file never uploads a different file or a partial image.
            });
        }
        Set<UUID> live=new HashSet<>();Set<String> used=new HashSet<>();int uploads=0;
        for(var player:mc.level.players()){
            if(player==mc.player||live.size()>=64)continue;var cape=BadgeClient.engine.cape(player.getUUID());if(cape==null)continue;
            live.add(player.getUUID());String hash=cape.data().hash();used.add(hash);
            if(!textures.containsKey(hash)&&!decoding.containsKey(hash)&&uploads<2&&System.currentTimeMillis()>=failed.getOrDefault(hash,0L)){decode(mc,cape.data());uploads++;}
            if(!mc.isPaused())players.computeIfAbsent(player.getUUID(),id->new Motion(player)).tick(player,cape,s.reducedMotion);
        }
        players.keySet().retainAll(live);
        var iterator=textures.entrySet().iterator();while(iterator.hasNext()){var entry=iterator.next();if(!used.contains(entry.getKey())){CosmeticTextures.release(entry.getValue());iterator.remove();}}
    }
    private static void decode(Minecraft mc,CapeData data){
        String hash=data.hash();int expected=generation;decoding.put(hash,expected);
        try{decoder.execute(()->{
            NativeImage image=null;try{image=NativeImage.read(data.png());}catch(Exception ignored){}NativeImage result=image;
            mc.execute(()->{
                decoding.remove(hash,expected);
                if(expected!=generation||mc.level==null){if(result!=null)result.close();return;}
                boolean selected=mc.level.players().stream().anyMatch(player->{var cape=BadgeClient.engine.cape(player.getUUID());return cape!=null&&cape.data().hash().equals(hash);});
                if(result==null){failed.put(hash,System.currentTimeMillis()+15000);return;}
                if(!selected||textures.containsKey(hash)){result.close();return;}
                try{textures.put(hash,CosmeticTextures.shared(data,result));}catch(Exception ignored){result.close();failed.put(hash,System.currentTimeMillis()+15000);}
            });
        });}catch(RejectedExecutionException ignored){decoding.remove(hash,expected);}
    }
    public static CapeFrame frame(Avatar player,float partial){
        if(BadgeClient.engine==null||BadgeClient.engine.cape(player.getUUID())==null)return null;
        Motion motion=players.get(player.getUUID());if(motion==null)return null;Identifier texture=textures.get(motion.hash);
        return texture==null?null:new CapeFrame(texture,motion.cloth.frame(partial,ClientFeatures.settings().reducedMotion));
    }
    public static int trackedPlayers(){return players.size();}
}
