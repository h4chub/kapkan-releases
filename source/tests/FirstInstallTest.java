package gg.kapkan.release;

import com.sun.net.httpserver.HttpServer;
import gg.kapkan.launcher.install.BaseGameInstallerService;
import gg.kapkan.launcher.launch.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Uses real installer code and local HTTP fixture files, not a Minecraft executable. */
public class FirstInstallTest {
 static void check(boolean b,String text){if(!b)throw new AssertionError(text);}
 static String sha(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(b));}
 static String descriptor(String url,byte[] b)throws Exception{return "{\"url\":\""+url+"\",\"sha1\":\""+sha(b)+"\",\"size\":"+b.length+"}";}
 public static void main(String[] args)throws Exception{
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  String origin="http://127.0.0.1:"+server.getAddress().getPort();
  byte[] client="TEST FIXTURE CLIENT, NOT MINECRAFT".getBytes(StandardCharsets.UTF_8);
  byte[] library="TEST FIXTURE LIBRARY".getBytes(StandardCharsets.UTF_8);
  byte[] assets="{\"objects\":{}}".getBytes(StandardCharsets.UTF_8);
  var clientDownloads=new AtomicInteger();var refuseLibrary=new AtomicBoolean(true);
  String meta="{\"assetIndex\":"+descriptor(origin+"/assets",assets).replaceFirst("\\{","{\"id\":\"test\",")+",\"id\":\"26.1.2\",\"type\":\"release\",\"mainClass\":\"fixture.NotAMinecraftClass\",\"downloads\":{\"client\":"+descriptor(origin+"/client",client)+"},\"libraries\":[{\"name\":\"example:fixture:1\",\"downloads\":{\"artifact\":"+descriptor(origin+"/lib",library).replaceFirst("\\{","{\"path\":\"example/fixture/1/fixture-1.jar\",")+"}}]}";
  String manifest="{\"versions\":[{\"id\":\"26.1.2\",\"url\":\""+origin+"/version\"}]}";
  server.createContext("/",exchange->{
   byte[] b;int status=200;
   switch(exchange.getRequestURI().getPath()){
    case "/manifest":b=manifest.getBytes(StandardCharsets.UTF_8);break;
    case "/version":b=meta.getBytes(StandardCharsets.UTF_8);break;
    case "/assets":b=assets;break;
    case "/client":clientDownloads.incrementAndGet();b=client;break;
    case "/lib":if(refuseLibrary.get()){status=503;b="unavailable".getBytes();}else b=library;break;
    default:status=404;b=new byte[0];
   }
   exchange.sendResponseHeaders(status,b.length);exchange.getResponseBody().write(b);exchange.close();
  });server.start();
  Path dir=Files.createTempDirectory("kapkan-game-fixture-");
  try{
   var ctor=BaseGameInstallerService.class.getDeclaredConstructor(HttpClient.class,URI.class);ctor.setAccessible(true);
   var service=ctor.newInstance(HttpClient.newHttpClient(),URI.create(origin+"/manifest"));
   var mc=new MinecraftInstallation(dir.resolve("game"));
   Path sentinel=mc.gameDir().resolve("mods/keep.jar");Files.createDirectories(sentinel.getParent());Files.writeString(sentinel,"KEEP");
   boolean failed=false;try{FreshInstallSupport.install(mc,"26.1.2",service);}catch(LaunchException e){failed=true;}
   check(failed,"broken library download should fail");
   check(mc.versionInstalled("26.1.2"),"client was downloaded before library failure");
   check(!FreshInstallSupport.isPrepared(mc,"26.1.2"),"partial download must not count as ready");
   refuseLibrary.set(false);
   check(FreshInstallSupport.install(mc,"26.1.2",service),"repair should succeed");
   check(FreshInstallSupport.isPrepared(mc,"26.1.2"),"repaired install should count as ready");
   check(clientDownloads.get()==1,"valid client must be reused during repair");
   check(Files.readString(sentinel).equals("KEEP"),"mods survived");
   server.stop(0);
   service.ensureInstalled(mc,"26.1.2",System.out::println);
   check(clientDownloads.get()==1,"second already-ready launch must not use network");
   System.out.println("Official-installer local fixture: PASS (interrupted install, repair, SHA1 reuse, offline second check, mods preservation)");
  }finally{server.stop(0);try(var walk=Files.walk(dir)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
