package gg.kapkan.presence;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
public final class PresenceEngineTest {
    static int checks;
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);checks++;}
    public static void main(String[] args)throws Exception {
        AtomicLong time=new AtomicLong(100000);AtomicInteger queries=new AtomicInteger(),beats=new AtomicInteger(),leaves=new AtomicInteger();AtomicBoolean fail=new AtomicBoolean();
        UUID alpha=UUID.randomUUID(),beta=UUID.randomUUID(),plain=UUID.randomUUID();
        var server=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath();var body=JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();String response="{}";int status=fail.get()?503:200;
            if(path.endsWith("heartbeat")){beats.incrementAndGet();check(!body.has("accessToken")&&!body.has("chat"),"Minimum presence fields");response="{\"ttl\":75}";}
            if(path.endsWith("query")){queries.incrementAndGet();response="{\"present\":[{\"uuid\":\""+alpha+"\",\"ttl\":60},{\"uuid\":\""+beta+"\",\"ttl\":60}]}";}
            if(path.endsWith("leave"))leaves.incrementAndGet();byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        Path path=Files.createTempFile("kapkan-presence-test-",".properties");String url="http://127.0.0.1:"+server.getAddress().getPort();
        var engine=new PresenceEngine(path,true,time::get);var self=new PresenceEngine.Player(alpha,"Alpha");var players=List.of(self,new PresenceEngine.Player(beta,"Beta"),new PresenceEngine.Player(plain,"PlainPlayer"));
        var config=new PresenceEngine.Config(true,true,url);var snap=new PresenceEngine.Snapshot("connection-a",PresenceEngine.context("server.example",25565),self,players);
        try{
            Files.writeString(path,"enabled=true\nshowOwn=true\nendpoint="+url+"\n");engine.loadConfig();engine.snapshot(snap);engine.exchange(snap,config);
            check(engine.visible(alpha,true),"Own Tab permitted");check(engine.visible(beta,false),"Remote participant visible");check(!engine.visible(plain,false),"Nonparticipant absent");
            check(queries.get()==1&&beats.get()==1,"Batch query once per exchange");
            time.addAndGet(61000);check(!engine.visible(beta,false),"Cached result expires without a query");
            engine.exchange(snap,config);check(engine.visible(beta,false),"Reconnect refresh");
            var changed=new PresenceEngine.Snapshot("connection-b",PresenceEngine.context("other.example",25565),self,players);engine.snapshot(changed);check(!engine.visible(beta,false),"Server switch clears immediately");engine.exchange(changed,config);check(leaves.get()==1,"Prior session leaves");
            fail.set(true);engine.exchange(changed,config);check(engine.status().contains("unavailable"),"Outage state");time.addAndGet(61000);check(!engine.visible(beta,false),"Outage expires gracefully");
            fail.set(false);Files.writeString(path,"enabled=false\nshowOwn=true\nendpoint="+url+"\n");engine.loadConfig();int before=queries.get();engine.exchange(changed,config);check(queries.get()==before&&!engine.visible(beta,false),"Disabled stops lookups and badges");
            Files.writeString(path,"enabled=true\nshowOwn=false\nendpoint="+url+"\n");engine.loadConfig();var noSelf=new PresenceEngine.Config(true,false,url);engine.exchange(changed,noSelf);check(!engine.visible(alpha,true)&&engine.visible(beta,false),"Self opt-out preserves others");
            check(PresenceEngine.context("SERVER.EXAMPLE.",25565).equals(PresenceEngine.context("server.example",25565)),"Context canonicalization");check(!PresenceEngine.context("server.example",25566).equals(snap.server()),"Ports separate contexts");
            var body=new PresenceEngine.LimitedBody();body.onSubscribe(new java.util.concurrent.Flow.Subscription(){public void request(long n){}public void cancel(){}});body.onNext(List.of(java.nio.ByteBuffer.allocate(65537)));check(body.result.isCompletedExceptionally(),"Oversized response bounded");
            Files.writeString(path,"");engine.loadConfig();
            var local=new PresenceEngine.Snapshot("single-player","",self,List.of(self));engine.snapshot(local);
            check(engine.showOwn()&&engine.visible(alpha,true),"Fresh default shows own badge in single player without service");
            check(!engine.visible(beta,false)&&!engine.visible(beta,true),"Local badge cannot mark another player");
            engine.snapshot(snap);check(engine.visible(alpha,true),"Own badge also works in multiplayer without a service");
            engine.snapshot(null);check(!engine.visible(alpha,true),"Leaving a world clears local badge state");
            engine.snapshot(local);Files.writeString(path,"enabled=true\nshowOwn=false\n");engine.loadConfig();
            check(!engine.visible(alpha,true),"An explicitly saved self opt-out is preserved");
            Files.writeString(path,"enabled=false\nshowOwn=true\n");engine.loadConfig();check(!engine.visible(alpha,true),"Master switch hides the offline badge too");
            Files.writeString(path,"enabled=true\nendpoint="+url+"\n");engine.loadConfig();int oldBeats=beats.get(),oldQueries=queries.get();
            engine.exchange(local,config);check(beats.get()==oldBeats&&queries.get()==oldQueries,"Single player never sends presence requests even with an endpoint");
            try(var unassociated=new PresenceEngine(path,false,time::get)){unassociated.snapshot(local);check(!unassociated.visible(alpha,true),"Opening Minecraft outside KapKan does not announce a launcher badge");}
            Files.writeString(path,"enabled=true\nshowOwn=true\nendpoint="+url+"\nlegacyPreference=keep-me\n");engine.loadConfig();engine.snapshot(snap);
            engine.relay(true,Map.of(beta,new PresenceEngine.RelayAppearance(null,"Flow")));
            check(engine.visible(beta,false)&&engine.cape(beta)==null,"Relay No Cape preserves participant badge and masks HTTP artwork");
            check(engine.status().equals("Server cosmetic relay connected"),"Relay connection status is explicit");
            engine.setSharingEnabled(false);check(!engine.visible(beta,false),"Saved sharing opt-out hides relay appearances immediately");
            var retained=new Properties();try(var in=Files.newInputStream(path)){retained.load(in);}
            check(retained.getProperty("endpoint").equals(url)&&retained.getProperty("legacyPreference").equals("keep-me"),"Changing sharing retains endpoint and existing preferences");
            engine.setSharingEnabled(true);engine.relay(true,Map.of(beta,new PresenceEngine.RelayAppearance(null,"Flow")));engine.snapshot(null);
            check(!engine.visible(beta,false)&&engine.cape(beta)==null,"Disconnect clears relay view immediately");
            engine.close();check(!engine.visible(alpha,true),"Shutdown clears local visibility");
            System.out.println("Presence engine: "+checks+" checks passed. Local HTTP fixtures, no gameplay claim.");
        }finally{engine.close();Thread.sleep(150);server.stop(0);Files.deleteIfExists(path);}
    }
}
