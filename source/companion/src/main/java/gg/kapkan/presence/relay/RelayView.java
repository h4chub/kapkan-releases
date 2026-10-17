package gg.kapkan.presence.relay;

import gg.kapkan.presence.CapeData;
import gg.kapkan.presence.PresenceEngine;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static gg.kapkan.presence.relay.RelayProtocol.*;

/** Client-thread metadata with bounded off-thread image validation. No rendering or disk work here. */
public final class RelayView {
    private record Assembly(byte[] bytes,int position){}
    private final Map<UUID,State> players=new LinkedHashMap<>();
    private final LinkedHashMap<String,CapeData> images=new LinkedHashMap<>(32,.75f,true);
    private final Map<String,Assembly> pending=new HashMap<>();private final Map<String,Long> requests=new HashMap<>();
    private final Executor validator;private final Consumer<Runnable> main;private final LongSupplier clock;private int generation;private boolean connected;
    public RelayView(Executor validator,Consumer<Runnable> main){this(validator,main,System::currentTimeMillis);}
    public RelayView(Executor validator,Consumer<Runnable> main,LongSupplier clock){this.validator=validator;this.main=main;this.clock=clock;}
    public void receive(Message message){
        if(message instanceof State state){connected=true;if(players.size()<MAX_PLAYERS||players.containsKey(state.uuid()))players.put(state.uuid(),state);}
        else if(message instanceof Gone gone)players.remove(gone.uuid());
        else if(message instanceof Chunk chunk)chunk(chunk);
        // Clients ignore serverbound-only packet kinds.
    }
    public List<Request> requests(Collection<UUID> loaded){
        long now=clock.getAsLong();var result=new ArrayList<Request>();
        for(UUID id:loaded){State state=players.get(id);if(state==null||state.hash().isEmpty()||images.containsKey(state.hash())||pending.containsKey(state.hash()))continue;
            if(now-requests.getOrDefault(state.hash(),Long.MIN_VALUE/2)<15000||pending.size()>=4)continue;
            requests.put(state.hash(),now);pending.put(state.hash(),new Assembly(new byte[state.length()],0));result.add(new Request(state.hash()));if(result.size()>=2)break;
        }
        // Interrupted downloads may be retried. A missing asset never hides a player or blocks a frame.
        pending.keySet().removeIf(hash->now-requests.getOrDefault(hash,0L)>10000);
        requests.entrySet().removeIf(e->now-e.getValue()>30000);return result;
    }
    private void chunk(Chunk chunk){
        Assembly assembly=pending.get(chunk.hash());if(assembly==null)return;
        if(chunk.offset()!=assembly.position||chunk.bytes().length>assembly.bytes.length-assembly.position){pending.remove(chunk.hash());return;}
        System.arraycopy(chunk.bytes(),0,assembly.bytes,assembly.position,chunk.bytes().length);int next=assembly.position+chunk.bytes().length;
        if(next<assembly.bytes.length){pending.put(chunk.hash(),new Assembly(assembly.bytes,next));return;}
        pending.remove(chunk.hash());int expectedGeneration=generation;
        try{validator.execute(()->{CapeData value=null;try{value=CapeData.fromPng(assembly.bytes,chunk.hash());}catch(IOException ignored){}CapeData result=value;
            main.accept(()->{if(result==null||generation!=expectedGeneration)return;images.put(result.hash(),result);while(images.size()>32)images.remove(images.keySet().iterator().next());});});}catch(RejectedExecutionException ignored){}
    }
    public Map<UUID,PresenceEngine.RelayAppearance> appearances(){
        var result=new HashMap<UUID,PresenceEngine.RelayAppearance>();
        for(var state:players.values())if(state.badge())result.put(state.uuid(),new PresenceEngine.RelayAppearance(images.get(state.hash()),state.material()));
        return Map.copyOf(result);
    }
    public boolean connected(){return connected;}
    public void reset(){generation++;connected=false;players.clear();images.clear();pending.clear();requests.clear();}
    public int cachedImages(){return images.size();}public int pendingImages(){return pending.size();}
}
