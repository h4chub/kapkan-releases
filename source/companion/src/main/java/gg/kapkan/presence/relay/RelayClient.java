package gg.kapkan.presence.relay;

import gg.kapkan.presence.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.concurrent.*;
import static gg.kapkan.presence.relay.RelayProtocol.*;

public final class RelayClient {
    private static final ExecutorService validation=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(8),run->{Thread t=new Thread(run,"kapkan-remote-cape-validator");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final RelayView view=new RelayView(validation,run->Minecraft.getInstance().execute(run));
    private static final ArrayDeque<RelayPayload> upload=new ArrayDeque<>();
    private static Object connection;private static String advertised="";private static int ticks,revision;private static boolean sharing;
    private RelayClient(){}
    public static void initialize(){
        ClientPlayNetworking.registerGlobalReceiver(RelayPayload.TYPE,(payload,context)->{
            Message message;try{message=RelayProtocol.decode(payload.bytes());}catch(java.io.IOException ignored){return;}
            Object expected=context.client().getConnection();context.client().execute(()->{if(connection==expected)view.receive(message);});
        });
    }
    public static void reset(){connection=null;view.reset();upload.clear();advertised="";ticks=0;if(BadgeClient.engine!=null)BadgeClient.engine.relay(false,Map.of());}
    public static void tick(Minecraft mc){
        if(mc.getConnection()!=connection){reset();connection=mc.getConnection();}
        if(connection==null||mc.player==null||mc.level==null||BadgeClient.engine==null)return;
        if(!ClientPlayNetworking.canSend(RelayPayload.TYPE)){BadgeClient.engine.relay(false,Map.of());return;}
        boolean enabled=BadgeClient.engine.sharingEnabled();var appearance=BadgeClient.engine.ownAppearance();
        CapeData cape=enabled?appearance.data():null;String hash=cape==null?"":cape.hash();String identity=enabled+"|"+hash+"|"+appearance.material();
        if(!identity.equals(advertised)||ticks%200==0){
            boolean changed=!identity.equals(advertised);advertised=identity;sharing=enabled;upload.clear();if(changed)revision++;
            ClientPlayNetworking.send(RelayPayload.of(new Offer(revision,enabled,hash,appearance.material(),cape==null?0:cape.size())));
            if(cape!=null){byte[] png=cape.png();for(int offset=0;offset<png.length;offset+=CHUNK)upload.addLast(RelayPayload.of(new Upload(revision,offset,Arrays.copyOfRange(png,offset,Math.min(png.length,offset+CHUNK)))));}
        }
        // Two <=24KiB pieces per tick bounds upload work. Menu changes clear the queue before replacements are sent.
        for(int n=0;n<2&&!upload.isEmpty();n++)ClientPlayNetworking.send(upload.removeFirst());
        if(enabled&&ticks%10==0){var loaded=new ArrayList<UUID>();for(var player:mc.level.players())if(player!=mc.player&&loaded.size()<64)loaded.add(player.getUUID());
            for(var request:view.requests(loaded))ClientPlayNetworking.send(RelayPayload.of(request));}
        BadgeClient.engine.relay(view.connected(),enabled?view.appearances():Map.of());ticks++;
    }
}
