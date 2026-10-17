package gg.kapkan.presence;

import gg.kapkan.presence.cosmetics.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Two independent clients against the shipped Python/SQLite service, using real cape PNGs. */
public final class CapeSyncTest {
    static int checks;
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    public static void main(String[] args)throws Exception{
        Path temp=Files.createTempDirectory("kapkan-cape-sync-");
        String script="from server import Store, PresenceServer; import sys; s=PresenceServer(('127.0.0.1',0),Store(sys.argv[1])); print(s.server_port,flush=True); s.serve_forever()";
        Process service=new ProcessBuilder("python3","-u","-c",script,temp.resolve("state.db").toString()).directory(Path.of(System.getProperty("kapkan.test.service")).toFile()).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        AtomicLong clock=new AtomicLong(System.currentTimeMillis());PresenceEngine a=null,b=null;
        try(var reader=service.inputReader()){
            String url="http://127.0.0.1:"+Integer.parseInt(reader.readLine());
            Path ac=temp.resolve("alice.properties"),bc=temp.resolve("bob.properties");Files.writeString(ac,"enabled=true\nendpoint="+url+"\n");Files.writeString(bc,Files.readString(ac));
            a=new PresenceEngine(ac,true,clock::get);b=new PresenceEngine(bc,true,clock::get);
            var alice=new PresenceEngine.Player(UUID.randomUUID(),"Alice");var bob=new PresenceEngine.Player(UUID.randomUUID(),"Bob");var plain=new PresenceEngine.Player(UUID.randomUUID(),"Vanilla");var profiles=List.of(alice,bob,plain);
            var config=new PresenceEngine.Config(true,true,url);String context=PresenceEngine.context("private.example",25565);
            var as=new PresenceEngine.Snapshot("alice",context,alice,profiles);var bs=new PresenceEngine.Snapshot("bob",context,bob,profiles);a.snapshot(as);b.snapshot(bs);
            ClientSettings settings=new ClientSettings(temp.resolve("cosmetics.properties"));CapeData black=CapeData.fromPng(CapeArt.png(CapeArt.make("black",settings)));settings.base=0x246ABC;settings.pattern="Diamond";
            CapeData custom=CapeData.fromPng(CapeArt.png(CapeArt.make("studio",settings)));
            a.publishCape(black,"Flow");b.publishCape(custom,"Silk");a.exchange(as,config);b.exchange(bs,config);a.exchange(as,config);
            check(a.status().equals("Capes and badges connected")&&b.status().equals("Capes and badges connected"),"Both clients exchange with the actual service");
            check(a.cape(bob.uuid())!=null&&a.cape(bob.uuid()).data().hash().equals(custom.hash()),"Alice receives Bob's custom cape");
            check(b.cape(alice.uuid())!=null&&b.cape(alice.uuid()).data().hash().equals(black.hash()),"Bob receives Alice's premade cape");
            check(Arrays.equals(a.cape(bob.uuid()).data().png(),custom.png()),"Custom PNG bytes preserved end to end");
            check(a.cape(bob.uuid()).material().equals("Silk"),"Cloth material travels with the cape");
            check(a.cape(plain.uuid())==null&&!a.visible(plain.uuid(),false),"Vanilla player is untouched");
            check(a.cape(alice.uuid())==null,"Own cape stays local");
            byte[] damaged=custom.png();damaged[50]^=1;
            try{CapeData.fromPng(damaged,custom.hash());throw new AssertionError("Bad hash accepted");}catch(java.io.IOException expected){checks++;}
            try{CapeData.fromPng(damaged);throw new AssertionError("Bad PNG accepted");}catch(java.io.IOException expected){checks++;}
            byte[] metadata=Arrays.copyOf(custom.png(),custom.png().length+1);
            try{CapeData.fromPng(metadata);throw new AssertionError("Trailing data accepted");}catch(java.io.IOException expected){checks++;}
            b.publishCape(null,"Flow");b.exchange(bs,config);a.exchange(as,config);
            check(a.cape(bob.uuid())==null&&a.visible(bob.uuid(),false),"Unequipping removes cape while retaining badge");
            CapeData burgundy=CapeData.fromPng(CapeArt.png(CapeArt.make("burgundy",settings)));b.publishCape(burgundy,"Heavy");b.exchange(bs,config);a.exchange(as,config);
            check(a.cape(bob.uuid()).data().hash().equals(burgundy.hash()),"Selection change replaces cached artwork");
            var different=new PresenceEngine.Snapshot("bob-other",PresenceEngine.context("other.example",25565),bob,profiles);b.snapshot(different);
            check(b.cape(alice.uuid())==null,"Changing server clears cached appearances immediately");b.exchange(different,config);a.exchange(as,config);
            check(a.cape(bob.uuid())==null&&b.cape(alice.uuid())==null,"Capes do not cross server contexts");
            b.snapshot(bs);b.exchange(bs,config);a.exchange(as,config);check(a.cape(bob.uuid())!=null,"Reconnection restores cape");
            clock.addAndGet(61000);check(a.cape(bob.uuid())==null&&!a.visible(bob.uuid(),false),"Stale artwork expires during an outage");
            clock.addAndGet(-61000);a.exchange(as,config);
            Files.writeString(ac,"enabled=false\nendpoint="+url+"\n");a.loadConfig();check(a.cape(bob.uuid())==null,"Presence opt-out clears shared appearance immediately");
            a.snapshot(null);check(a.cape(bob.uuid())==null,"Leaving world clears appearance");
            System.out.println("Cape sync: "+checks+" checks passed against the shipped Python service with two independent clients.");
        }finally{if(a!=null)a.close();if(b!=null)b.close();Thread.sleep(100);service.destroy();service.waitFor();try(var files=Files.walk(temp)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
