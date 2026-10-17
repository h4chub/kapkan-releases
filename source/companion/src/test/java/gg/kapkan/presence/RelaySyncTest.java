package gg.kapkan.presence;

import gg.kapkan.presence.relay.*;
import static gg.kapkan.presence.relay.RelayProtocol.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Two independent client protocol/cache instances through the actual bounded relay hub. No gameplay claim. */
public final class RelaySyncTest {
    private static int checks;
    private static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);checks++;}
    private static Message wire(Message value){try{return RelayProtocol.decode(RelayProtocol.encode(value));}catch(IOException error){throw new AssertionError(error);}}
    private static CapeData cape(int color)throws IOException {
        BufferedImage image=new BufferedImage(320,512,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<512;y++)for(int x=0;x<320;x++)image.setRGB(x,y,0xFF000000|color);
        var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return CapeData.fromPng(out.toByteArray());
    }
    private static CapeData noisyCape()throws IOException {
        var image=new BufferedImage(320,512,BufferedImage.TYPE_INT_ARGB);var random=new Random(1205);
        for(int y=0;y<512;y++)for(int x=0;x<320;x++)image.setRGB(x,y,0xFF000000|random.nextInt(0x1000000));
        var out=new ByteArrayOutputStream();ImageIO.write(image,"PNG",out);return CapeData.fromPng(out.toByteArray());
    }
    public static void main(String[] args)throws Exception {
        AtomicLong now=new AtomicLong(100000);UUID alice=UUID.randomUUID(),bob=UUID.randomUUID(),outsider=UUID.randomUUID();
        var workers=new ArrayDeque<Runnable>();var main=new ArrayDeque<Runnable>();
        RelayView a=new RelayView(workers::add,main::add,now::get),b=new RelayView(workers::add,main::add,now::get);
        var views=Map.of(alice,a,bob,b);var deliveries=new ArrayList<Message>();
        RelayHub hub=new RelayHub(workers::add,main::add,(receiver,message)->{deliveries.add(message);RelayView view=views.get(receiver);if(view!=null)view.receive(wire(message));},now::get);
        CapeData black=cape(0x121416),red=cape(0x7C2034);
        hub.receive(alice,wire(new Offer(1,true,black.hash(),"Flow",black.size())));hub.receive(bob,wire(new Offer(1,true,red.hash(),"Silk",red.size())));
        upload(hub,alice,1,black);upload(hub,bob,1,red);
        check(hub.pendingBytes()==black.size()+red.size(),"PNG validation remains queued outside server gameplay thread");
        check(a.appearances().get(bob).data()==null,"No unvalidated cape is exposed");drain(workers,main);
        request(hub,alice,a,List.of(bob));request(hub,bob,b,List.of(alice));
        check(a.appearances().get(bob).data()==null,"Remote client PNG validation also runs outside gameplay thread");drain(workers,main);
        check(a.connected()&&b.connected(),"Both clients receive relay acknowledgements");
        check(a.appearances().get(bob).data().hash().equals(red.hash()),"Alice receives Bob's selected cape");
        check(b.appearances().get(alice).data().hash().equals(black.hash()),"Bob receives Alice's selected cape");
        check(a.appearances().get(bob).material().equals("Silk"),"Material is synchronized");
        check(!a.appearances().containsKey(outsider),"Vanilla/nonparticipating player is unchanged");
        hub.receive(alice,wire(new State(bob,99,false,"","Flow",0)));
        check(a.appearances().containsKey(bob),"Client-authored state cannot spoof another player's badge");
        hub.receive(bob,wire(new Offer(2,true,"","Flow",0)));
        check(a.appearances().get(bob).data()==null&&a.appearances().containsKey(bob),"No Cape removes remote cape immediately and retains badge");
        hub.receive(bob,wire(new Offer(3,true,black.hash(),"Heavy",black.size())));
        request(hub,alice,a,List.of(bob));drain(workers,main);
        check(a.appearances().get(bob).data().hash().equals(black.hash()),"Cached selection changes propagate");
        check(a.appearances().get(bob).material().equals("Heavy"),"Cached cape material changes propagate");
        hub.receive(bob,wire(new Offer(4,false,"","Flow",0)));check(!a.appearances().containsKey(bob),"Sharing opt-out removes badge and cape");
        hub.disconnect(bob);check(!a.appearances().containsKey(bob)&&hub.participants()==1,"Disconnect removes participation without TTL delay");
        b.reset();hub.receive(bob,wire(new Offer(1,true,red.hash(),"Silk",red.size())));request(hub,bob,b,List.of(alice));drain(workers,main);
        check(b.appearances().get(alice).data()!=null,"A new connection receives current peers and assets");
        a.reset();check(!a.connected()&&a.appearances().isEmpty()&&a.cachedImages()==0,"Switching server clears metadata and image cache");
        CapeData blue=cape(0x123FC9);hub.receive(bob,wire(new Offer(2,true,blue.hash(),"Flow",blue.size())));upload(hub,bob,2,blue);
        hub.receive(bob,wire(new Offer(3,true,"","Flow",0)));drain(workers,main);
        check(b.appearances().get(bob).data()==null,"An in-flight PNG cannot resurrect a removed cape");
        hub.receive(bob,wire(new Offer(4,true,blue.hash(),"Flow",blue.size())));hub.receive(bob,wire(new Upload(4,2,new byte[]{1,2})));
        check(hub.pendingBytes()==0,"Out-of-order chunks discard bounded upload state");
        int before=deliveries.size();hub.receive(outsider,wire(new Request(black.hash())));check(deliveries.size()==before,"Nonparticipants cannot read server cape assets");
        invalid(new byte[RelayProtocol.MAX_PACKET+1]);invalid(new byte[]{99,1});
        byte[] valid=RelayProtocol.encode(new Gone(alice));invalid(Arrays.copyOf(valid,valid.length+1));
        try{RelayProtocol.encode(new Upload(1,0,new byte[CHUNK+1]));throw new AssertionError("Oversized chunk accepted");}catch(IOException expected){checks++;}
        // An actual multi-packet imported image follows the same protocol, without JSON/base64 inflation.
        now.addAndGet(2000);CapeData noisy=noisyCape();check(noisy.size()>CHUNK*4,"Fixture spans multiple network pieces");
        hub.receive(bob,wire(new Offer(10,true,noisy.hash(),"Silk",noisy.size())));upload(hub,bob,10,noisy);drain(workers,main);
        request(hub,bob,b,List.of(bob));drain(workers,main);
        check(Arrays.equals(b.appearances().get(bob).data().png(),noisy.png()),"Large imported image is reassembled byte-for-byte");
        for(var message:deliveries)check(RelayProtocol.encode(message).length<=MAX_PACKET,"Every network packet respects its cap");
        RelayHub bounded=new RelayHub(workers::add,main::add,(id,message)->{},now::get);
        for(int i=0;i<80;i++)bounded.receive(UUID.randomUUID(),wire(new Offer(1,true,black.hash(),"Flow",CapeData.MAX_BYTES)));
        check(bounded.pendingBytes()<=16*1024*1024,"Incomplete uploads share a global memory cap");
        now.addAndGet(16000);bounded.maintenance();check(bounded.pendingBytes()==0,"Interrupted uploads expire and release memory");
        for(int i=0;i<300;i++)bounded.receive(UUID.randomUUID(),wire(new Offer(1,true,"","Flow",0)));
        check(bounded.participants()==MAX_PLAYERS,"Participant metadata is bounded");bounded.clear();
        // If capacity recovers by another player uploading identical content, a same-revision retry adopts it.
        var recoveryMessages=new ArrayList<Message>();
        RelayHub recovery=new RelayHub(workers::add,main::add,(id,message)->{if(id.equals(alice))recoveryMessages.add(message);},now::get);
        var blockers=new ArrayList<UUID>();for(int i=0;i<21;i++){UUID id=UUID.randomUUID();blockers.add(id);recovery.receive(id,wire(new Offer(1,true,noisy.hash(),"Flow",CapeData.MAX_BYTES)));}
        int remaining=16*1024*1024-recovery.pendingBytes();
        recovery.receive(UUID.randomUUID(),wire(new Offer(1,true,noisy.hash(),"Flow",remaining)));
        recovery.receive(alice,wire(new Offer(1,true,red.hash(),"Silk",red.size())));
        check(recoveryMessages.stream().noneMatch(message->message instanceof State state&&state.uuid().equals(alice)&&!state.hash().isEmpty()),"Capacity-deferred cape is not announced as available");
        recovery.disconnect(blockers.getFirst());recovery.receive(bob,wire(new Offer(1,true,red.hash(),"Silk",red.size())));upload(recovery,bob,1,red);drain(workers,main);
        recoveryMessages.clear();recovery.receive(alice,wire(new Offer(1,true,red.hash(),"Silk",red.size())));
        check(recoveryMessages.stream().anyMatch(message->message instanceof State state&&state.uuid().equals(alice)&&state.hash().equals(red.hash())),"Same-revision retry adopts identical content uploaded by a peer");
        recoveryMessages.clear();recovery.receive(alice,wire(new Offer(2,true,"","Flow",0)));drain(workers,main);
        check(recoveryMessages.stream().anyMatch(message->message instanceof State state&&state.uuid().equals(alice)&&state.hash().isEmpty()),"No Cape still removes a recovered cached appearance");recovery.clear();
        hub.clear();check(hub.participants()==0&&hub.cachedImages()==0&&hub.pendingBytes()==0,"Server stop clears bounded state");
        System.out.println("Relay sync: "+checks+" checks passed with two independent protocol clients, authenticated adapter identities, and queued validation.");
    }
    private static void upload(RelayHub hub,UUID sender,int revision,CapeData data){byte[] bytes=data.png();for(int offset=0;offset<bytes.length;offset+=CHUNK)hub.receive(sender,wire(new Upload(revision,offset,Arrays.copyOfRange(bytes,offset,Math.min(bytes.length,offset+CHUNK)))));}
    private static void request(RelayHub hub,UUID id,RelayView view,List<UUID> loaded){for(Request request:view.requests(loaded))hub.receive(id,wire(request));}
    private static void drain(ArrayDeque<Runnable> workers,ArrayDeque<Runnable> main){while(!workers.isEmpty()||!main.isEmpty()){while(!workers.isEmpty())workers.remove().run();while(!main.isEmpty())main.remove().run();}}
    private static void invalid(byte[] bytes){try{RelayProtocol.decode(bytes);throw new AssertionError("Malformed packet accepted");}catch(IOException expected){checks++;}}
}
