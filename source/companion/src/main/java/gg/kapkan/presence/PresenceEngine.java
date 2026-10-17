package gg.kapkan.presence;

import com.google.gson.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;

/** Game-independent bounded presence transport. No Minecraft or rendering dependencies. */
public final class PresenceEngine implements AutoCloseable {
    public record Player(UUID uuid,String name){}
    public record Snapshot(String key,String server,Player self,List<Player> players){
        public Snapshot{players=List.copyOf(players.subList(0,Math.min(256,players.size())));}
    }
    public record Config(boolean enabled,boolean showOwn,String endpoint){}
    private record Seen(long expires,String cape,String material){}
    public record RemoteCape(CapeData data,String material){}
    public record RelayAppearance(CapeData data,String material){}
    public record OwnAppearance(CapeData data,String material){}
    private volatile Map<UUID,RelayAppearance> relay=Map.of();
    private volatile boolean relayConnected;
    private record Appearance(CapeData data,String material){}
    private record Session(String key,String endpoint,String token){}
    final Path configPath;
    final boolean associated;
    final LongSupplier clock;
    final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService monitor=Executors.newSingleThreadScheduledExecutor(daemon("kapkan-presence-settings"));
    private final ExecutorService network=Executors.newSingleThreadExecutor(daemon("kapkan-presence-network"));
    private final AtomicBoolean pending=new AtomicBoolean();
    private volatile Snapshot snapshot;
    private volatile Map<UUID,Seen> seen=Map.of();
    private volatile Map<String,CapeData> capeImages=Map.of();
    private volatile Appearance appearance;
    private volatile Config config=new Config(false,false,"");
    private volatile String cacheKey="",status="Service not configured";
    private Session session;
    private volatile long nextRequest;
    private int failures;
    private volatile boolean closed;
    public PresenceEngine(Path path,boolean associated){this(path,associated,System::currentTimeMillis);}
    PresenceEngine(Path path,boolean associated,LongSupplier clock){this.configPath=path;this.associated=associated;this.clock=clock;loadConfig();}
    static ThreadFactory daemon(String name){return run->{Thread t=new Thread(run,name);t.setDaemon(true);return t;};}
    public void start(){monitor.scheduleWithFixedDelay(this::poll,0,1,TimeUnit.SECONDS);}
    public void snapshot(Snapshot s){
        Snapshot before=snapshot;snapshot=s;
        if(before!=null&&(s==null||!before.key().equals(s.key()))){seen=Map.of();capeImages=Map.of();cacheKey="";relay=Map.of();relayConnected=false;}
    }
    public boolean visible(UUID id,boolean self){
        Snapshot s=snapshot;Config c=config;if(closed||!associated||!c.enabled||s==null)return false;
        // Our own identity is already known locally. It never needs a service response.
        if(id.equals(s.self.uuid()))return c.showOwn;
        if(self)return false;
        if(relayConnected)return relay.containsKey(id);
        if(s.server.isBlank()||!s.key.equals(cacheKey))return false;
        Seen hit=seen.get(id);return hit!=null&&hit.expires>clock.getAsLong();
    }
    public boolean showOwn(){return !closed&&associated&&config.enabled&&config.showOwn;}
    public String status(){return relayConnected?"Server cosmetic relay connected":status;}
    public boolean sharingEnabled(){return !closed&&config.enabled;}
    public OwnAppearance ownAppearance(){Appearance own=appearance;return own==null?new OwnAppearance(null,"Flow"):new OwnAppearance(own.data,own.material);}
    public void relay(boolean connected,Map<UUID,RelayAppearance> appearances){relay=Map.copyOf(appearances);relayConnected=connected;}
    public synchronized void setSharingEnabled(boolean enabled)throws java.io.IOException {
        Properties p=new Properties();try(var in=Files.newInputStream(configPath)){p.load(in);}catch(java.io.IOException ignored){}
        p.setProperty("enabled",Boolean.toString(enabled));Path directory=configPath.toAbsolutePath().getParent();Files.createDirectories(directory);Path temporary=Files.createTempFile(directory,"presence-",".tmp");
        try{try(var out=Files.newOutputStream(temporary)){p.store(out,"KapKan cosmetic sharing");}try{Files.move(temporary,configPath,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temporary,configPath,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temporary);}
        loadConfig();
    }
    public void publishCape(CapeData data,String material){
        if(!Set.of("Flow","Silk","Heavy").contains(material))material="Flow";
        Appearance next=data==null?null:new Appearance(data,material),before=appearance;
        if(before==null?next==null:next!=null&&before.data.hash().equals(next.data.hash())&&before.material.equals(next.material))return;
        appearance=next;nextRequest=0;
    }
    public RemoteCape cape(UUID id){
        Snapshot s=snapshot;if(s==null||s.self.uuid.equals(id)||!visible(id,false))return null;
        if(relayConnected){RelayAppearance remote=relay.get(id);return remote==null||remote.data==null?null:new RemoteCape(remote.data,remote.material);}
        Seen hit=seen.get(id);CapeData data=hit==null?null:capeImages.get(hit.cape);
        return data==null?null:new RemoteCape(data,hit.material);
    }
    private long nextConfig;
    private void poll(){
        if(closed)return;
        long now=clock.getAsLong();if(now>=nextConfig){nextConfig=now+5000;loadConfig();}
        Snapshot s=snapshot;Config c=config;
        if(relayConnected||!associated||!c.enabled||c.endpoint.isBlank()||s==null||s.server.isBlank()){
            seen=Map.of();cacheKey="";status=!associated?"Start Minecraft through KapKan":!c.enabled?"Badges are off":s==null?"Waiting for a world":s.server.isBlank()?"Single-player badge ready":"Multiplayer service not configured";
            if(pending.compareAndSet(false,true))network.execute(()->{try{leave();nextRequest=0;}finally{pending.set(false);}});
            return;
        }
        if(now<nextRequest)return;
        if(pending.compareAndSet(false,true))network.execute(()->{try{exchange(s,c);}finally{pending.set(false);}});
    }
    void loadConfig(){
        Properties p=new Properties();try(var in=Files.newInputStream(configPath)){p.load(in);}catch(Exception ignored){}
        String url=p.getProperty("endpoint","").trim().replaceAll("/+$","");
        try{if(!url.isEmpty()){URI u=URI.create(url);boolean local=Set.of("localhost","127.0.0.1","[::1]").contains(Objects.toString(u.getHost(),""));
            if(u.getHost()==null||u.getUserInfo()!=null||u.getFragment()!=null||u.getQuery()!=null||!("https".equals(u.getScheme())||local&&"http".equals(u.getScheme())))url="";
        }}catch(Exception e){url="";}
        Config next=new Config(Boolean.parseBoolean(p.getProperty("enabled","true")),Boolean.parseBoolean(p.getProperty("showOwn","true")),url);
        if(!next.equals(config)){config=next;seen=Map.of();capeImages=Map.of();cacheKey="";nextRequest=0;}
    }
    private boolean current(Snapshot s,Config c){Snapshot now=snapshot;return !closed&&associated&&c.equals(config)&&c.enabled&&!c.endpoint.isEmpty()&&!s.server.isBlank()&&now!=null&&s.key.equals(now.key);}
    void exchange(Snapshot s,Config c){
        Appearance own=appearance;
        try{
            if(!current(s,c))return;
            if(session==null||!session.key.equals(s.key)||!session.endpoint.equals(c.endpoint)){
                leave();byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);session=new Session(s.key,c.endpoint,Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            }
            if(!current(s,c))return;
            JsonObject hb=new JsonObject();hb.addProperty("session",session.token);hb.addProperty("server",s.server);hb.add("player",player(s.self));
            if(own!=null){JsonObject cape=new JsonObject();cape.addProperty("hash",own.data.hash());cape.addProperty("material",own.material);hb.add("cape",cape);}
            String api="/v2";JsonObject heartbeat;
            try{heartbeat=post(c.endpoint+api+"/heartbeat",hb);}catch(HttpFailure e){if(e.status!=404)throw e;api="/v1";heartbeat=post(c.endpoint+api+"/heartbeat",hb);}
            if(api.equals("/v2")&&own!=null&&heartbeat.has("needCape")&&heartbeat.get("needCape").getAsBoolean()){
                if(!current(s,c))return;JsonObject upload=sessionBody(s);upload.addProperty("hash",own.data.hash());upload.addProperty("png",own.data.base64());post(c.endpoint+"/v2/cape/upload",upload);
            }
            Map<UUID,Seen> result=new HashMap<>();
            for(int offset=0;offset<s.players.size();offset+=64){
                if(!current(s,c))return;
                JsonObject q=sessionBody(s);JsonArray batch=new JsonArray();Set<UUID> requested=new HashSet<>();
                for(Player p:s.players.subList(offset,Math.min(offset+64,s.players.size()))){batch.add(player(p));requested.add(p.uuid);}
                q.add("players",batch);JsonArray found=post(c.endpoint+api+"/query",q).getAsJsonArray("present");
                if(found==null||found.size()>64)throw new IllegalArgumentException("Invalid presence response");
                for(JsonElement e:found){JsonObject item=e.getAsJsonObject();UUID id=UUID.fromString(item.get("uuid").getAsString());int ttl=Math.max(0,Math.min(60,item.get("ttl").getAsInt()));
                    String hash="",material="Flow";
                    if(api.equals("/v2")&&item.has("cape"))try{JsonObject cape=item.getAsJsonObject("cape");String value=cape.get("hash").getAsString();if(value.matches("[a-f0-9]{64}"))hash=value;String weight=cape.get("material").getAsString();if(Set.of("Flow","Silk","Heavy").contains(weight))material=weight;}catch(RuntimeException ignored){}
                    if(requested.contains(id)&&ttl>0)result.put(id,new Seen(clock.getAsLong()+ttl*1000L,hash,material));
                }
            }
            Map<String,CapeData> images=new LinkedHashMap<>();int downloads=0;boolean more=false;
            // Nearer, loaded players are first in the snapshot. Bound both memory and downloads per cycle.
            for(Player player:s.players){Seen hit=result.get(player.uuid);if(player.uuid.equals(s.self.uuid)||hit==null||hit.cape.isEmpty()||images.containsKey(hit.cape))continue;
                if(images.size()>=32)break;CapeData data=capeImages.get(hit.cape);
                if(data==null){if(downloads++>=8){more=true;continue;}if(!current(s,c))return;
                    JsonObject request=sessionBody(s);request.addProperty("hash",hit.cape);
                    try{JsonObject value=post(c.endpoint+"/v2/cape/read",request,CapeData.MAX_BYTES*4/3+2048);String encoded=value.get("png").getAsString();if(encoded.length()>CapeData.MAX_BYTES*4/3+4)throw new java.io.IOException("Cape limit");data=CapeData.fromPng(Base64.getDecoder().decode(encoded),hit.cape);}catch(Exception ignored){continue;}
                }
                images.put(hit.cape,data);
            }
            if(current(s,c)){seen=Map.copyOf(result);capeImages=Map.copyOf(images);cacheKey=s.key;status=api.equals("/v2")?"Capes and badges connected":"Badge service needs a cape-sharing update";failures=0;nextRequest=appearance!=own?0:clock.getAsLong()+(more?5000:15000);}
        }catch(Exception e){status="Presence temporarily unavailable";failures=Math.min(5,failures+1);nextRequest=clock.getAsLong()+Math.min(120000,5000L<<failures)+ThreadLocalRandom.current().nextLong(2500);}
    }
    private JsonObject sessionBody(Snapshot s){JsonObject j=new JsonObject();j.addProperty("session",session.token);j.addProperty("server",s.server);return j;}
    private void leave(){Session old=session;session=null;if(old!=null)try{JsonObject b=new JsonObject();b.addProperty("session",old.token);post(old.endpoint+"/v1/leave",b);}catch(Exception ignored){} }
    static JsonObject player(Player p){JsonObject j=new JsonObject();j.addProperty("uuid",p.uuid.toString());j.addProperty("name",p.name);return j;}
    JsonObject post(String url,JsonObject body)throws Exception{return post(url,body,65536);}
    static final class HttpFailure extends java.io.IOException{final int status;HttpFailure(int status){super("Presence HTTP "+status);this.status=status;}}
    JsonObject post(String url,JsonObject body,int limit)throws Exception{
        HttpRequest request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).header("Content-Type","application/json").header("User-Agent","KapKan-Presence/1.0.0")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build();
        var future=http.sendAsync(request,info->new LimitedBody(limit));
        try{HttpResponse<byte[]> r=future.get(6,TimeUnit.SECONDS);if(r.statusCode()!=200)throw new HttpFailure(r.statusCode());return JsonParser.parseString(new String(r.body(),StandardCharsets.UTF_8)).getAsJsonObject();}
        finally{if(!future.isDone())future.cancel(true);}
    }
    static class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        final int limit;LimitedBody(){this(65536);}LimitedBody(int limit){this.limit=limit;}
        final CompletableFuture<byte[]> result=new CompletableFuture<>();final java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();Flow.Subscription subscription;
        public CompletionStage<byte[]> getBody(){return result;}
        public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        public void onNext(List<ByteBuffer> blocks){for(ByteBuffer b:blocks){if(bytes.size()+b.remaining()>limit){subscription.cancel();result.completeExceptionally(new java.io.IOException("Response limit"));return;}byte[] part=new byte[b.remaining()];b.get(part);bytes.writeBytes(part);}subscription.request(1);}
        public void onError(Throwable t){result.completeExceptionally(t);}public void onComplete(){result.complete(bytes.toByteArray());}
    }
    public static String context(String host,int port){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("kapkan-presence-v1|java|"+host.toLowerCase(Locale.ROOT).replaceAll("\\.$","")+":"+port).getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    @Override public synchronized void close(){if(closed)return;closed=true;seen=Map.of();capeImages=Map.of();relay=Map.of();relayConnected=false;monitor.shutdownNow();network.execute(this::leave);network.shutdown();}
}
