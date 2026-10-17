package gg.kapkan.release;

import gg.kapkan.launcher.install.BaseGameInstallerService;
import gg.kapkan.launcher.launch.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import javax.net.ssl.*;

/** Actual local TCP transfers through the production HTTP client and patched installer.
 * Payloads are test bytes, never Minecraft executables or genuine CDN responses. */
public class DownloadRecoveryTest {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(b));}
 static final RetryDownloader.Policy FAST=new RetryDownloader.Policy(3,10,1000,350,3000);
 record Response(int status,byte[] body,int advertised,long pause,String extra){}
 interface Responder {Response respond(String path,int count)throws Exception;}
 static final class Server implements AutoCloseable {
  final ServerSocket socket;final ConcurrentHashMap<String,AtomicInteger> counts=new ConcurrentHashMap<>(); final List<Socket> clients=new CopyOnWriteArrayList<>();
  Server(Responder responder)throws Exception{
   socket=new ServerSocket(0,50,InetAddress.getByName("127.0.0.1"));
   Thread.ofVirtual().start(()->{while(!socket.isClosed())try{
    Socket c=socket.accept();clients.add(c);Thread.ofVirtual().start(()->{try(c){
     c.setSoTimeout(5000);var reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.US_ASCII));String first=reader.readLine();if(first==null)return;
     String line;while((line=reader.readLine())!=null&&!line.isEmpty()){}
     String path=first.split(" ")[1];int count=counts.computeIfAbsent(path,x->new AtomicInteger()).incrementAndGet();Response r=responder.respond(path,count);
     var out=c.getOutputStream();out.write(("HTTP/1.1 "+r.status()+" test\r\nContent-Length: "+r.advertised()+"\r\nConnection: close\r\n"+r.extra()+"\r\n").getBytes(StandardCharsets.US_ASCII));out.write(r.body());out.flush();if(r.pause()>0)Thread.sleep(r.pause());c.shutdownOutput();
    }catch(Exception ignored){}finally{clients.remove(c);}});
   }catch(IOException e){if(!socket.isClosed())throw new RuntimeException(e);}});
  }
  URI uri(String path){return URI.create("http://127.0.0.1:"+socket.getLocalPort()+path);}
  int count(String path){return counts.getOrDefault(path,new AtomicInteger()).get();}
  public void close()throws Exception{socket.close();for(Socket c:clients)c.close();}
 }
 static Response ok(byte[] b){return new Response(200,b,b.length,0,"");}
 static Response text(String s){return ok(s.getBytes(StandardCharsets.UTF_8));}
 static Response cut(byte[] b){return new Response(200,Arrays.copyOf(b,Math.max(1,b.length/2)),b.length,0,"");}
 static final class RecordingClient extends HttpClient {
  final HttpClient delegate=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).followRedirects(Redirect.NORMAL).build();
  final List<Optional<Version>> requested=new CopyOnWriteArrayList<>();IOException sendError;
  public <T> HttpResponse<T> send(HttpRequest r,HttpResponse.BodyHandler<T> h)throws IOException,InterruptedException{requested.add(r.version());if(sendError!=null)throw sendError;return delegate.send(r,h);}
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h){return delegate.sendAsync(r,h);}
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r,HttpResponse.BodyHandler<T> h,HttpResponse.PushPromiseHandler<T> p){return delegate.sendAsync(r,h,p);}
  public Optional<CookieHandler> cookieHandler(){return delegate.cookieHandler();}public Optional<Duration> connectTimeout(){return delegate.connectTimeout();}public Redirect followRedirects(){return delegate.followRedirects();}public Optional<ProxySelector> proxy(){return delegate.proxy();}public SSLContext sslContext(){return delegate.sslContext();}public SSLParameters sslParameters(){return delegate.sslParameters();}public Optional<Authenticator> authenticator(){return delegate.authenticator();}public Version version(){return delegate.version();}public Optional<Executor> executor(){return delegate.executor();}
 }
 static void noParts(Path dir)throws Exception{try(var walk=Files.walk(dir)){check(walk.noneMatch(p->p.toString().endsWith(".part")),"no leftover temporary files");}}
 static void testFileRecovery(Path root)throws Exception{
  byte[] data=new byte[8192];new Random(8).nextBytes(data);var client=new RecordingClient();
  try(Server server=new Server((p,n)->n==1?cut(data):ok(data))){
   Path file=root.resolve("recover.jar");RetryDownloader.download(client,server.uri("/client"),file,hash(data),data.length,FAST);
   check(Arrays.equals(data,Files.readAllBytes(file)),"exact file after retry");check(server.count("/client")==2,"one cut transfer, one retry");
   check(client.requested.get(1).equals(Optional.of(HttpClient.Version.HTTP_1_1)),"HTTP/1.1 requested after stream failure");
   RetryDownloader.download(client,server.uri("/client"),file,hash(data),data.length,FAST);check(server.count("/client")==2,"verified cache reused without network");
  }noParts(root);
  System.out.println("PASS real truncated TCP body recovered + explicit HTTP/1.1 retry + cached file reuse");
 }
 static void testMetadata(Path root)throws Exception{
  byte[] json="{\"versions\":[{\"id\":\"26.1.2\"}]}".getBytes();
  try(Server s=new Server((p,n)->n==1?cut(json):ok(json))){check(RetryDownloader.text(new RecordingClient(),s.uri("/manifest"),FAST).equals(new String(json)),"metadata recovered");check(s.count("/manifest")==2,"metadata retried");}
  System.out.println("PASS metadata truncated-body recovery");
 }
 static void testHttp(Path root)throws Exception{
  byte[] data="TEST-CONTENT".getBytes();
  try(Server s=new Server((p,n)->p.equals("/busy")&&n<3?new Response(503,new byte[0],0,0,"Retry-After: 0\r\n"):p.equals("/missing")?new Response(404,new byte[0],0,0,""):ok(data))){
   RetryDownloader.download(new RecordingClient(),s.uri("/busy"),root.resolve("busy.jar"),hash(data),data.length,FAST);check(s.count("/busy")==3,"503 retried twice");
   try{RetryDownloader.download(new RecordingClient(),s.uri("/missing"),root.resolve("missing.jar"),hash(data),data.length,FAST);throw new AssertionError("404 accepted");}catch(RetryDownloader.DownloadFailure e){check(e.getMessage().contains("HTTP 404"),"HTTP error visible");check(e.getCause()!=null,"cause retained");check(e.getMessage().contains("downloads.log"),"log path visible");}
   check(s.count("/missing")==1,"404 not pointlessly retried");
  }noParts(root);System.out.println("PASS 503 retries + permanent HTTP rejection + actionable cause/log");
 }
 static void testIntegrityAndLocal(Path root)throws Exception{
  byte[] wanted="GOOD".getBytes(),wrong="BAD!".getBytes();Path target=root.resolve("existing.jar");Files.writeString(target,"EXISTING");
  try(Server s=new Server((p,n)->ok(wrong))){
   try{RetryDownloader.download(new RecordingClient(),s.uri("/wrong-hash"),target,hash(wanted),wanted.length,FAST);throw new AssertionError("bad hash accepted");}catch(RetryDownloader.DownloadFailure e){check(e.getMessage().contains("SHA-1"),"checksum reason visible");}
   check(s.count("/wrong-hash")==3,"bounded checksum retries");check(Files.readString(target).equals("EXISTING"),"old file never replaced by corrupt download");
   Path directory=root.resolve("is-directory");Files.createDirectory(directory);
   try{RetryDownloader.download(new RecordingClient(),s.uri("/local"),directory,hash(wanted),wanted.length,FAST);throw new AssertionError("directory accepted");}catch(RetryDownloader.DownloadFailure e){check(e.getMessage().contains("destination"),"local error distinguished");}
   check(s.count("/local")==0,"local errors do not trigger network retries");
  }noParts(root);System.out.println("PASS corrupt download rejected + original file preserved + local error classified");
 }
 static void testIdleAndCancel(Path root)throws Exception{
  byte[] data="XX".getBytes();
  try(Server s=new Server((p,n)->new Response(200,data,1000,4000,""))){
   long start=System.nanoTime();
   try{RetryDownloader.download(new RecordingClient(),s.uri("/idle"),root.resolve("idle.jar"),null,1000,new RetryDownloader.Policy(2,0,1000,200,700));throw new AssertionError("idle accepted");}catch(RetryDownloader.DownloadFailure e){check(e.getMessage().contains("time limit"),"idle timeout explained");}
   check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<2500,"idle transfer bounded");check(s.count("/idle")==2,"bounded idle retries");
   AtomicReference<Throwable> error=new AtomicReference<>();AtomicBoolean restored=new AtomicBoolean();
   Thread worker=Thread.ofPlatform().start(()->{try{RetryDownloader.download(new RecordingClient(),s.uri("/cancel"),root.resolve("cancel.jar"),null,1000,FAST);}catch(Throwable e){error.set(e);restored.set(Thread.currentThread().isInterrupted());}});
   long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);while(s.count("/cancel")==0&&System.nanoTime()<deadline)Thread.sleep(10);
   worker.interrupt();worker.join(1500);check(!worker.isAlive(),"cancel stops transfer");check(error.get() instanceof InterruptedException,"cancellation is not a network retry");check(restored.get(),"interrupt retained");check(s.count("/cancel")==1,"no retry after cancellation");
  }noParts(root);System.out.println("PASS idle stream timeout + cancellation stops retries");
 }
 static void testTlsAndValidation(Path root)throws Exception{
  var client=new RecordingClient();client.sendError=new SSLHandshakeException("certificate validation failed");
  try{RetryDownloader.download(client,URI.create("https://example.invalid/secure"),root.resolve("tls.jar"),null,-1,FAST);throw new AssertionError("TLS accepted");}catch(RetryDownloader.DownloadFailure e){check(e.getMessage().contains("TLS"),"TLS error visible");}
  check(client.requested.size()==1,"TLS verification errors not bypassed or repeatedly retried");
  try{RetryDownloader.text(new RecordingClient(),URI.create("http://example.invalid/plain"),FAST);throw new AssertionError("insecure external URL accepted");}catch(IOException e){check(e.getMessage().contains("HTTPS"),"external HTTP rejected");}
  noParts(root);System.out.println("PASS injected TLS validation failure not bypassed + HTTPS requirement");
 }
 static String desc(String url,byte[] b)throws Exception{return "{\"url\":\""+url+"\",\"sha1\":\""+hash(b)+"\",\"size\":"+b.length+"}";}
 static void testProductionInstaller(Path root)throws Exception{
  byte[] data="NOT-MINECRAFT: PRODUCTION TRANSPORT FIXTURE".getBytes(),lib="LIBRARY FIXTURE".getBytes(),assets="{\"objects\":{}}".getBytes();
  AtomicBoolean refuseLibrary=new AtomicBoolean(true);AtomicReference<Server> ref=new AtomicReference<>();
  try(Server s=new Server((p,n)->{
   String base=ref.get().uri("").toString();
   return switch(p){
    case "/manifest"->text("{\"versions\":[{\"id\":\"26.1.2\",\"url\":\""+base+"/version\"}]}");
    case "/version"->text("{\"assetIndex\":"+desc(base+"/assets",assets).replaceFirst("\\{","{\"id\":\"fixture\",")+",\"id\":\"26.1.2\",\"type\":\"release\",\"mainClass\":\"fixture.NoGame\",\"downloads\":{\"client\":"+desc(base+"/client",data)+"},\"libraries\":[{\"name\":\"example:fixture:1\",\"downloads\":{\"artifact\":"+desc(base+"/lib",lib).replaceFirst("\\{","{\"path\":\"example/fixture/1/lib.jar\",")+"}}]}");
    case "/assets"->ok(assets);
    case "/client"->n==1?cut(data):ok(data);
    case "/lib"->refuseLibrary.get()?new Response(503,new byte[0],0,0,""):ok(lib);
    default->new Response(404,new byte[0],0,0,"");
   };
  })){
   ref.set(s);var ctor=BaseGameInstallerService.class.getDeclaredConstructor(HttpClient.class,URI.class);ctor.setAccessible(true);
   var service=ctor.newInstance(new RecordingClient(),s.uri("/manifest"));var mc=new MinecraftInstallation(root.resolve("game space (1)"));
   for(String f:List.of("mods/keep.jar","saves/world/sentinel","config/keep.json","options.txt","servers.dat")){Path x=mc.gameDir().resolve(f);Files.createDirectories(x.getParent());Files.writeString(x,"KEEP:"+f);}
   try{FreshInstallSupport.install(mc,"26.1.2",service);throw new AssertionError("503 library allowed");}catch(LaunchException e){check(e.getMessage().contains("503"),"production installer surfaces actual HTTP failure");check(e.getMessage().contains("lib.jar"),"production installer reports exact failed file");}
   check(s.count("/client")==2,"production installer recovered first truncated file");check(!FreshInstallSupport.isPrepared(mc,"26.1.2"),"pending marker still blocks incomplete installation");
   refuseLibrary.set(false);check(FreshInstallSupport.install(mc,"26.1.2",service),"repair completed");check(s.count("/client")==2,"verified client reused during library retry");check(FreshInstallSupport.isPrepared(mc,"26.1.2"),"completed install prepared");
   for(String f:List.of("mods/keep.jar","saves/world/sentinel","config/keep.json","options.txt","servers.dat"))check(Files.readString(mc.gameDir().resolve(f)).equals("KEEP:"+f),"preserved "+f);
   int manifests=s.count("/manifest");service.ensureInstalled(mc,"26.1.2",x->{});check(s.count("/manifest")==manifests,"already prepared second launch makes no download requests");
  }noParts(root);System.out.println("PASS real patched installer: cut connection recovery, partial setup repair, cache, data preservation, second launch");
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("kapkan-download-tests-");String old=System.getProperty("user.home");System.setProperty("user.home",root.toString());
  try{
   testFileRecovery(root);testMetadata(root);testHttp(root);testIntegrityAndLocal(root);testIdleAndCancel(root);testTlsAndValidation(root);testProductionInstaller(root);
   String log=Files.readString(RetryDownloader.logPath());check(log.contains("caused by")||log.contains("Caused by"),"underlying connection cause in local log");
   System.out.println("DOWNLOAD RECOVERY TESTS: PASS ("+checks+" assertions)");
  }finally{System.setProperty("user.home",old);try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
