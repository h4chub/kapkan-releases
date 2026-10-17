package gg.kapkan.presence.relay;

import gg.kapkan.presence.CapeData;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import static gg.kapkan.presence.relay.RelayProtocol.*;

/** Server-thread state. The network adapter supplies the connected player's UUID, never a client field. */
public final class RelayHub {
    private static final int MAX_PENDING=16*1024*1024, MAX_CACHE=24*1024*1024;
    private final Map<UUID,Member> members=new LinkedHashMap<>();
    private final LinkedHashMap<String,CapeData> images=new LinkedHashMap<>(32,.75f,true);
    private final Executor validator;private final Consumer<Runnable> main;private final BiConsumer<UUID,Message> send;private final LongSupplier clock;
    private int pendingBytes,cacheBytes;
    private static final class Member {
        int revision;boolean badge;String hash="",material="Flow";byte[] upload;int position;boolean validating;
        long changeWindow,uploadStarted;int changes;final Map<String,Long> requests=new HashMap<>();
    }
    public RelayHub(Executor validator,Consumer<Runnable> main,BiConsumer<UUID,Message> send){this(validator,main,send,System::currentTimeMillis);}
    public RelayHub(Executor validator,Consumer<Runnable> main,BiConsumer<UUID,Message> send,LongSupplier clock){this.validator=validator;this.main=main;this.send=send;this.clock=clock;}
    public void receive(UUID authenticatedPlayer,Message message){
        if(message instanceof Offer offer)offer(authenticatedPlayer,offer);
        else if(message instanceof Upload upload)upload(authenticatedPlayer,upload);
        else if(message instanceof Request request)request(authenticatedPlayer,request.hash());
        // A malicious client cannot send State/Gone messages for itself or anyone else.
    }
    private void offer(UUID uuid,Offer offer){
        Member member=members.get(uuid);boolean fresh=member==null;
        if(fresh){if(members.size()>=MAX_PLAYERS)return;member=new Member();members.put(uuid,member);}
        if(!fresh&&offer.revision()==member.revision){
            CapeData arrived=images.get(offer.hash());
            if(member.badge&&arrived!=null&&arrived.size()==offer.length()){
                boolean changed=member.upload!=null||!member.hash.equals(offer.hash());
                cancelUpload(member);member.hash=offer.hash();
                if(changed)broadcast(state(uuid,member));else send.accept(uuid,state(uuid,member));return;
            }
            if(member.upload==null&&member.badge&&!offer.hash().isEmpty()&&!images.containsKey(offer.hash())&&pendingBytes+offer.length()<=MAX_PENDING){member.upload=new byte[offer.length()];member.hash=offer.hash();member.uploadStarted=clock.getAsLong();member.position=0;pendingBytes+=offer.length();}
            send.accept(uuid,state(uuid,member));return;
        }
        long now=clock.getAsLong();if(now-member.changeWindow>=1000){member.changeWindow=now;member.changes=0;}
        if(++member.changes>8)return;
        cancelUpload(member);member.revision=offer.revision();member.badge=offer.badge();member.hash="";member.material=offer.material();
        if(fresh)for(var entry:members.entrySet())if(!entry.getKey().equals(uuid))send.accept(uuid,state(entry.getKey(),entry.getValue()));
        if(offer.badge()&&!offer.hash().isEmpty()){
            CapeData cached=images.get(offer.hash());
            if(cached!=null&&cached.size()==offer.length()){member.hash=cached.hash();broadcast(state(uuid,member));return;}
            if(pendingBytes+offer.length()<=MAX_PENDING){member.upload=new byte[offer.length()];member.uploadStarted=now;pendingBytes+=offer.length();member.hash=offer.hash();}
        }
        // A changed or removed cape disappears immediately, before a replacement PNG is validated.
        broadcast(new State(uuid,member.revision,member.badge,"",member.material,0));
    }
    private void upload(UUID uuid,Upload message){
        Member member=members.get(uuid);if(member==null||member.revision!=message.revision()||member.upload==null||member.validating)return;
        if(message.offset()!=member.position||message.bytes().length>member.upload.length-member.position){cancelUpload(member);member.hash="";return;}
        System.arraycopy(message.bytes(),0,member.upload,member.position,message.bytes().length);member.position+=message.bytes().length;
        if(member.position!=member.upload.length)return;
        byte[] complete=member.upload;String hash=member.hash;int revision=member.revision;member.validating=true;
        Member submitted=member;
        try{validator.execute(()->{CapeData image=null;try{image=CapeData.fromPng(complete,hash);}catch(Exception ignored){}CapeData result=image;
            main.accept(()->{Member current=members.get(uuid);if(current!=submitted||current.revision!=revision||current.upload!=complete)return;
                cancelUpload(current);if(result==null){current.hash="";return;}
                putImage(result);current.hash=result.hash();broadcast(state(uuid,current));});});
        }catch(RejectedExecutionException ignored){cancelUpload(member);member.hash="";}
    }
    private State state(UUID uuid,Member member){CapeData image=member.upload==null?images.get(member.hash):null;return new State(uuid,member.revision,member.badge,image==null?"":member.hash,member.material,image==null?0:image.size());}
    private void request(UUID uuid,String hash){
        Member member=members.get(uuid);if(member==null||!member.badge)return;
        // Only appearances currently selected by a connected participant are readable.
        if(members.values().stream().noneMatch(m->m.badge&&m.upload==null&&m.hash.equals(hash)))return;
        CapeData image=images.get(hash);if(image==null)return;long now=clock.getAsLong();
        member.requests.entrySet().removeIf(e->now-e.getValue()>30000);
        if(member.requests.size()>=64||now-member.requests.getOrDefault(hash,Long.MIN_VALUE/2)<10000)return;
        member.requests.put(hash,now);byte[] png=image.png();
        for(int offset=0;offset<png.length;offset+=CHUNK)send.accept(uuid,new Chunk(hash,offset,Arrays.copyOfRange(png,offset,Math.min(png.length,offset+CHUNK))));
    }
    private void putImage(CapeData data){
        if(images.containsKey(data.hash()))return;images.put(data.hash(),data);cacheBytes+=data.size();
        var iterator=images.entrySet().iterator();while(images.size()>32||cacheBytes>MAX_CACHE){var old=iterator.next();cacheBytes-=old.getValue().size();iterator.remove();}
    }
    private void cancelUpload(Member member){if(member.upload!=null)pendingBytes-=member.upload.length;member.upload=null;member.position=0;member.validating=false;}
    private void broadcast(Message message){for(UUID receiver:List.copyOf(members.keySet()))send.accept(receiver,message);}
    public void maintenance(){long now=clock.getAsLong();for(var member:members.values())if(member.upload!=null&&now-member.uploadStarted>15000)cancelUpload(member);}
    public void disconnect(UUID uuid){Member member=members.remove(uuid);if(member!=null){cancelUpload(member);broadcast(new Gone(uuid));}}
    public void clear(){for(var member:members.values())cancelUpload(member);members.clear();images.clear();cacheBytes=0;}
    public int participants(){return members.size();}public int pendingBytes(){return pendingBytes;}public int cachedImages(){return images.size();}
}
