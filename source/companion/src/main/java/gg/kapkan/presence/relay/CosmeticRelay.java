package gg.kapkan.presence.relay;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.concurrent.*;
import static gg.kapkan.presence.relay.RelayProtocol.*;

/** Optional server support bundled with the client. Only compatible receivers receive cosmetic packets. */
public final class CosmeticRelay implements ModInitializer {
    private static final ExecutorService validation=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(16),run->{Thread t=new Thread(run,"kapkan-relay-validator");t.setDaemon(true);return t;},new ThreadPoolExecutor.AbortPolicy());
    private static final Map<MinecraftServer,ServerState> servers=new IdentityHashMap<>();
    private static final class ServerState {
        private static final class Queues {final ArrayDeque<RelayPayload> metadata=new ArrayDeque<>(),images=new ArrayDeque<>();int size(){return metadata.size()+images.size();}}
        final MinecraftServer server;final Map<UUID,Queues> outgoing=new LinkedHashMap<>();final RelayHub hub;int queued;
        ServerState(MinecraftServer server){this.server=server;hub=new RelayHub(validation,server::execute,this::queue);}
        void queue(UUID receiver,Message message){
            var player=server.getPlayerList().getPlayer(receiver);if(player==null||!ServerPlayNetworking.canSend(player,RelayPayload.TYPE))return;
            var queue=outgoing.computeIfAbsent(receiver,key->new Queues());
            if(queue.size()>=256||queued>=2048)return;RelayPayload payload=RelayPayload.of(message);
            if(message instanceof State||message instanceof Gone)queue.metadata.addLast(payload);else queue.images.addLast(payload);queued++;
        }
        void flush(){
            int budget=32;var iterator=outgoing.entrySet().iterator();
            while(iterator.hasNext()&&budget>0){var entry=iterator.next();var player=server.getPlayerList().getPlayer(entry.getKey());var queue=entry.getValue();
                if(player==null||!ServerPlayNetworking.canSend(player,RelayPayload.TYPE)){queued-=queue.size();iterator.remove();continue;}
                int perPlayer=4;while(queue.size()>0&&budget>0&&perPlayer-->0){ServerPlayNetworking.send(player,!queue.metadata.isEmpty()?queue.metadata.removeFirst():queue.images.removeFirst());queued--;budget--;}
                if(queue.size()==0)iterator.remove();
            }
        }
        void disconnect(UUID uuid){var queue=outgoing.remove(uuid);if(queue!=null)queued-=queue.size();hub.disconnect(uuid);}
    }
    @Override public void onInitialize(){
        PayloadTypeRegistry.serverboundPlay().register(RelayPayload.TYPE,RelayPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RelayPayload.TYPE,RelayPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RelayPayload.TYPE,(payload,context)->{
            Message message;try{message=RelayProtocol.decode(payload.bytes());}catch(java.io.IOException ignored){return;}
            if(!(message instanceof Offer||message instanceof Upload||message instanceof Request))return;
            // The connection supplies the identity even on offline-mode servers; client messages carry no UUID.
            UUID connected=context.player().getUUID();context.server().execute(()->{if(context.server().getPlayerList().getPlayer(connected)==context.player())servers.computeIfAbsent(context.server(),ServerState::new).hub.receive(connected,message);});
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{ServerState state=servers.get(server);if(state!=null)state.disconnect(handler.player.getUUID());});
        ServerTickEvents.END_SERVER_TICK.register(server->{ServerState state=servers.get(server);if(state!=null){state.hub.maintenance();state.flush();}});
        ServerLifecycleEvents.SERVER_STOPPED.register(server->{ServerState state=servers.remove(server);if(state!=null)state.hub.clear();});
    }
}
