package gg.kapkan.release;

import com.sun.net.httpserver.HttpServer;
import gg.kapkan.launcher.install.BaseGameInstallerService;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Real loopback HTTP transfers, bounded parallelism, recovery and production ASM integration. */
public final class AssetDownloadTest {
    static int checks;
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static String hash(byte[] value) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value)); }
    static void remove(Path path) throws Exception { try(var walk=Files.walk(path)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);} }
    static final class Fixture implements AutoCloseable {
        final Map<String,byte[]> data = new LinkedHashMap<>();
        final Map<String,AtomicInteger> requests = new ConcurrentHashMap<>();
        final AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger();
        final ExecutorService threads = Executors.newCachedThreadPool();
        final HttpServer server;
        final URI root;
        final byte[] index;
        final Map<String,Object> version;
        volatile int delay = 75;
        volatile String corruptOnce = "", missing = "";
        Fixture(int count) throws Exception {
            for(int i=0;i<count;i++){byte[] b=new byte[16384];new Random(i).nextBytes(b);data.put(hash(b),b);}
            StringJoiner entries=new StringJoiner(",");int i=0;
            for(var entry:data.entrySet())entries.add("\"fixture/"+(i++)+"\":{\"hash\":\""+entry.getKey()+"\",\"size\":"+entry.getValue().length+"}");
            String first=data.keySet().iterator().next();
            entries.add("\"duplicate\":{\"hash\":\""+first+"\",\"size\":"+data.get(first).length+"}");
            index=("{\"objects\":{"+entries+"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.setExecutor(threads);
            root=URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/");
            version=Map.of("assetIndex",Map.of("id","fixture","url",root.resolve("index").toString(),"sha1",hash(index),"size",index.length));
            server.createContext("/", exchange -> {
                String path=exchange.getRequestURI().getPath(), key=path.substring(path.lastIndexOf('/')+1);
                int number=requests.computeIfAbsent(key,k->new AtomicInteger()).incrementAndGet();
                boolean asset=!key.equals("index");
                if(asset)peak.accumulateAndGet(active.incrementAndGet(),Math::max);
                try {
                    if(asset)Thread.sleep(delay);
                    byte[] bytes=asset?data.get(key):index;int status=200;
                    if(bytes==null||key.equals(missing)){status=404;bytes=new byte[0];}
                    else if(key.equals(corruptOnce)&&number==1){bytes=bytes.clone();bytes[0]^=1;}
                    exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
                } catch(InterruptedException e){Thread.currentThread().interrupt();} catch(IOException ignored){}
                finally{exchange.close();if(asset)active.decrementAndGet();}
            });server.start();
        }
        void reset(){requests.clear();peak.set(0);}
        int count(String key){return requests.getOrDefault(key,new AtomicInteger()).get();}
        int requestCount(){return requests.values().stream().mapToInt(AtomicInteger::get).sum();}
        Path asset(Path game,String hash){return game.resolve("assets/objects/"+hash.substring(0,2)+"/"+hash);}
        public void close(){server.stop(0);threads.shutdownNow();}
    }
    /** Only test routing changes; the production method still receives the official HTTPS URI. */
    static final class RoutedClient extends HttpClient {
        final HttpClient delegate=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        final URI root;
        RoutedClient(URI root){this.root=root;}
        public <T> HttpResponse<T> send(HttpRequest request,HttpResponse.BodyHandler<T> handler)throws IOException,InterruptedException{
            if(!request.uri().getHost().equals("resources.download.minecraft.net"))return delegate.send(request,handler);
            var builder=HttpRequest.newBuilder(root.resolve(request.uri().getPath())).GET().timeout(Duration.ofSeconds(5));
            request.headers().map().forEach((k,v)->v.forEach(s->builder.header(k,s)));
            var response=delegate.send(builder.build(),handler);
            return new HttpResponse<T>(){
                public int statusCode(){return response.statusCode();}public HttpRequest request(){return request;}
                public Optional<HttpResponse<T>> previousResponse(){return Optional.empty();}public HttpHeaders headers(){return response.headers();}
                public T body(){return response.body();}public Optional<SSLSession> sslSession(){return response.sslSession();}
                public URI uri(){return request.uri();}public Version version(){return response.version();}
            };
        }
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){throw new UnsupportedOperationException();}
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){throw new UnsupportedOperationException();}
        public Optional<CookieHandler> cookieHandler(){return delegate.cookieHandler();}public Optional<Duration> connectTimeout(){return delegate.connectTimeout();}
        public Redirect followRedirects(){return delegate.followRedirects();}public Optional<ProxySelector> proxy(){return delegate.proxy();}
        public SSLContext sslContext(){return delegate.sslContext();}public SSLParameters sslParameters(){return delegate.sslParameters();}
        public Optional<Authenticator> authenticator(){return delegate.authenticator();}public Version version(){return delegate.version();}
        public Optional<Executor> executor(){return delegate.executor();}
    }
    static long install(Fixture f,Path game,int workers,List<LaunchProgress.Snapshot> events)throws Exception{
        long start=System.nanoTime();
        try(var listener=LaunchProgress.listenDetailed(events::add);var client=HttpClient.newHttpClient()){
            AssetInstaller.install(client,game,f.version,s->{},f.root,workers);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
    }
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("kapkan-assets-test-");
        try(Fixture f=new Fixture(96)){
            List<LaunchProgress.Snapshot> serial=new ArrayList<>(),parallel=new ArrayList<>();
            long before=install(f,root.resolve("serial"),1,serial);
            check(f.peak.get()==1,"single-worker benchmark must be sequential");f.reset();
            Path game=root.resolve("parallel");long after=install(f,game,6,parallel);
            check(f.peak.get()>1&&f.peak.get()<=6,"bounded concurrent transfers");
            check(after*2<before,"parallel download must remove per-file latency bottleneck");
            check(f.data.keySet().stream().allMatch(k->f.count(k)==1),"duplicate content downloaded only once");
            var end=parallel.getLast();check(end.filesDone()==96&&end.total()==96L*16384&&end.done()==end.total(),"aggregate totals reflect bytes and unique files");
            check(parallel.stream().anyMatch(s->s.stage().equals("Minecraft assets")&&s.remainingSeconds()>0&&s.bytesPerSecond()>0),"live speed and ETA reach listener");
            check(parallel.stream().filter(s->s.stage().equals("Minecraft assets")).count()<after/150+6,"progress is throttled across all workers");
            f.reset();install(f,game,6,new ArrayList<>());check(f.requestCount()==0,"fully verified asset cache uses no network");
            String first=f.data.keySet().iterator().next();Files.write(f.asset(game,first),new byte[16384]);f.reset();f.corruptOnce=first;
            install(f,game,6,new ArrayList<>());check(f.count(first)==2&&f.requestCount()==2,"only corrupt file retried and repaired");
            check(RetryDownloader.matches(f.asset(game,first),first,16384),"repaired bytes verified");f.corruptOnce="";
            Path production=root.resolve("production");f.delay=5;f.reset();
            var ctor=BaseGameInstallerService.class.getDeclaredConstructor(HttpClient.class,URI.class);ctor.setAccessible(true);
            var service=ctor.newInstance(new RoutedClient(f.root),f.root.resolve("manifest"));
            var method=BaseGameInstallerService.class.getDeclaredMethod("installAssets",Path.class,Map.class,java.util.function.Consumer.class);method.setAccessible(true);
            method.invoke(service,production,f.version,(java.util.function.Consumer<String>)s->{});
            check(f.peak.get()>1&&f.peak.get()<=6,"real patched installer uses concurrent asset path");
            Path failed=root.resolve("failed");Files.createDirectories(failed.resolve("mods"));Files.writeString(failed.resolve("mods/keep.jar"),"KEEP");
            f.missing=first;f.reset();boolean rejected=false;
            try{install(f,failed,6,new ArrayList<>());}catch(IOException expected){rejected=true;}
            check(rejected&&!Files.exists(f.asset(failed,first)),"failed file never becomes installed");
            check(Files.readString(failed.resolve("mods/keep.jar")).equals("KEEP"),"mods preserved");
            try(var files=Files.walk(failed)){check(files.noneMatch(p->p.toString().endsWith(".part")),"failure cleans partial downloads");}
            f.missing="";install(f,failed,6,new ArrayList<>());check(RetryDownloader.matches(f.asset(failed,first),first,16384),"failed install is repairable");
            f.delay=1500;f.reset();AtomicReference<Throwable> error=new AtomicReference<>();AtomicBoolean flag=new AtomicBoolean();
            Thread cancel=Thread.ofPlatform().start(()->{try{install(f,root.resolve("cancel"),6,new ArrayList<>());}catch(Throwable e){error.set(e);flag.set(Thread.currentThread().isInterrupted());}});
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(f.active.get()==0&&System.nanoTime()<until)Thread.sleep(10);
            cancel.interrupt();cancel.join(4000);check(!cancel.isAlive()&&error.get() instanceof InterruptedException&&flag.get(),"cancellation joins workers and restores interrupt flag");
            try(var files=Files.walk(root.resolve("cancel"))){check(files.noneMatch(p->p.toString().endsWith(".part")),"cancellation cleans partial downloads");}
            AtomicLong clock=new AtomicLong();DownloadRate rate=new DownloadRate(clock::get);rate.received(1000);
            check(rate.bytesPerSecond()==0,"no made-up initial ETA");clock.set(2_000_000_000L);rate.received(1000);check(Math.abs(rate.bytesPerSecond()-1000)<1,"rate measures bytes over elapsed time");
            clock.set(6_000_000_000L);check(rate.bytesPerSecond()==0,"stall clears stale rate");
            System.out.printf(Locale.ROOT,"PASS %d checks; same 96-file delayed HTTP fixture: sequential %.2fs, parallel %.2fs, %.2fx throughput; maximum 6 workers. Not a real-internet guarantee.%n",checks,before/1000.0,after/1000.0,(double)before/after);
        }finally{remove(root);}
    }
}
