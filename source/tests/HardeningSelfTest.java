package gg.kapkan.release;

import com.sun.net.httpserver.HttpServer;
import gg.kapkan.launcher.launch.*;
import java.io.*;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import java.util.jar.*;
import java.lang.reflect.*;
import javax.tools.ToolProvider;

/** Production JAR methods are invoked; all servers/game files/installers are explicitly test fixtures. */
public final class HardeningSelfTest {
 static int assertions;
 static void check(boolean value,String why){assertions++;if(!value)throw new AssertionError(why);}
 static String hash(byte[] b)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(b));}
 static Path write(Path p,byte[] b)throws Exception{Files.createDirectories(p.getParent());Files.write(p,b);return p;}
 static Path write(Path p,String s)throws Exception{return write(p,s.getBytes(StandardCharsets.UTF_8));}
 static String desc(String path,byte[] data)throws Exception{return "{\"path\":\""+path+"\",\"sha1\":\""+hash(data)+"\",\"size\":"+data.length+"}";}
 static String library(String ver){return "{\"name\":\"org.example:shared:"+ver+"\"}";}
 static byte[] jar(Map<String,byte[]> entries,String main)throws Exception {
  var data=new ByteArrayOutputStream();var mf=new Manifest();mf.getMainAttributes().putValue("Manifest-Version","1.0");if(main!=null)mf.getMainAttributes().putValue("Main-Class",main);
  try(var out=new JarOutputStream(data,mf)){for(var e:entries.entrySet()){out.putNextEntry(new JarEntry(e.getKey()));out.write(e.getValue());out.closeEntry();}}
  return data.toByteArray();
 }
 static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception {
  Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);
  try{return m.invoke(target,args);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception x)throw x;throw e;}
 }
 static void readiness(Path root)throws Exception {
  var inst=new MinecraftInstallation(root.resolve("readiness"));String id="26.1.2";
  byte[] client=jar(Map.of("fixture.txt","NOT MINECRAFT".getBytes()),null),lib=jar(Map.of("entry.txt","LIBRARY FIXTURE".getBytes()),null),asset="ASSET FIXTURE".getBytes();
  String lh="org/example/shared/2/shared-2.jar",ah=hash(asset);
  byte[] index=("{\"objects\":{\"fixture\":{\"hash\":\""+ah+"\",\"size\":"+asset.length+"}}}").getBytes();
  String metadata="{\"id\":\""+id+"\",\"mainClass\":\"fixture.Main\",\"downloads\":{\"client\":"+desc("unused",client)+"},\"libraries\":[{\"name\":\"org.example:shared:2\",\"downloads\":{\"artifact\":"+desc(lh,lib)+"}}],\"assetIndex\":{\"id\":\"fixture\",\"sha1\":\""+hash(index)+"\",\"size\":"+index.length+"}}";
  write(inst.versionJson(id),metadata);write(inst.versionJar(id),client);
  Path sentinel=write(inst.gameDir().resolve("mods/keep.jar"),"KEEP MOD");Path options=write(inst.gameDir().resolve("options.txt"),"KEEP KEYS");
  check(!FreshInstallSupport.isPrepared(inst,id),"Missing libraries cannot count as ready without a pending marker");
  Path lp=write(inst.librariesDir().resolve(lh),lib);
  check(!FreshInstallSupport.isPrepared(inst,id),"Missing asset index cannot count as ready");
  write(inst.assetsDir().resolve("indexes/fixture.json"),index);
  check(!FreshInstallSupport.isPrepared(inst,id),"Missing asset object cannot count as ready");
  Path ap=write(inst.assetsDir().resolve("objects/"+ah.substring(0,2)+"/"+ah),asset);
  check(FreshInstallSupport.isPrepared(inst,id),"Complete base accepted offline");
  write(ap,"x");check(!FreshInstallSupport.isPrepared(inst,id),"Truncated asset rejected");write(ap,asset);
  byte[] corrupt=lib.clone();corrupt[corrupt.length-1]^=1;write(lp,corrupt);
  check(!FreshInstallSupport.isPrepared(inst,id),"Same-size library hash mismatch rejected");write(lp,lib);
  check(FreshInstallSupport.isPrepared(inst,id),"Repaired library accepted");
  Path pending=write(inst.gameDir().resolve(".kapkan-setup/base-"+id+".pending"),"pending");
  check(!FreshInstallSupport.isPrepared(inst,id),"Pending flag still respected");Files.delete(pending);
  write(inst.versionJson(id),"{");check(!FreshInstallSupport.isPrepared(inst,id),"Corrupt JSON rejected");write(inst.versionJson(id),metadata);
  check(Files.readString(sentinel).equals("KEEP MOD"),"Mod sentinel unchanged");check(Files.readString(options).equals("KEEP KEYS"),"Controls unchanged");
  for(String invalid:List.of("../options.txt","C:/outside","/absolute","x/../../outside")) {
   boolean rejected=false;try{InstallIntegrity.safe(inst.librariesDir(),invalid);}catch(IOException e){rejected=true;}check(rejected,"Unsafe managed path rejected: "+invalid);
  }
  System.out.println("PASS incomplete install readiness, hashes, assets, controls/mod preservation");
 }
 static void profiles(Path root)throws Exception {
  var inst=new MinecraftInstallation(root.resolve("profiles"));String base="1.21.11",id="1.21.11-forge-fixture";
  write(inst.versionJson(base),"{\"id\":\""+base+"\",\"mainClass\":\"fixture.Main\",\"libraries\":["+library("1")+"]}");
  write(inst.versionJar(base),jar(Map.of("fixture.txt",new byte[]{1}),null));
  write(inst.versionJson(id),"{\"id\":\""+id+"\",\"inheritsFrom\":\""+base+"\",\"mainClass\":\"fixture.ForgeMain\",\"libraries\":["+library("2")+"]}");
  var p=new VersionProfileLoader(inst).load(id);
  check(p.libraries().size()==1,"Child override removes parent's matching older dependency");
  check(MiniJson.string(MiniJson.object(p.libraries().getFirst()).get("name")).endsWith(":2"),"Child dependency selected");
  var service=new ModLoaderInstallerService();Class<?>[] sig={MinecraftInstallation.class,String.class,String.class};
  check(!(boolean)invoke(service,"profileTargetsBase",sig,inst,id,"1.21.1"),"1.21.11 must not match 1.21.1");
  check(!(boolean)invoke(service,"profileTargetsBase",sig,inst,id,base),"Missing inherited profile dependency is not ready");
  write(inst.librariesDir().resolve("org/example/shared/2/shared-2.jar"),jar(Map.of("fixture.txt",new byte[]{2}),null));
  check((boolean)invoke(service,"profileTargetsBase",sig,inst,id,base),"Exact complete profile accepted");
  check(service.ensureInstalled(inst,base,"Forge",null).equals(id),"Null optional logger doesn't break cached Forge");
  check(service.ensureInstalled(inst,base,"Vanilla",null).equals(base),"Vanilla still selects original base");
  check(!ProfileHardening.targetsBase(inst,"../outside",base),"Invalid inherited profile path rejected");
  List<Object> variants=new ArrayList<>();
  variants.add(Map.of("name","org.example:shared:1:natives-windows"));variants.add(Map.of("name","org.example:shared:1:natives-macos"));
  variants.add(Map.of("name","org.example:shared:1","rules",List.of(Map.of("os",Map.of("name","windows")))));
  variants.add(Map.of("name","org.example:shared:1","rules",List.of(Map.of("os",Map.of("name","osx")))));
  VersionProfile q=new VersionProfile("x","x","fixture.Main","x","release",21,"",List.of(),List.of(),variants,Map.of());
  check(ProfileHardening.normalize(q).libraries().size()==4,"Native classifiers and OS variants preserved");
  check(p.mainClass().equals("fixture.ForgeMain")&&p.jarVersion().equals(base),"Inherited entrypoint/base unchanged");
  System.out.println("PASS exact Forge version targeting, inheritance override, selectors, null logger");
 }
 static void privacy()throws Exception {
  List<String> cmd=List.of("java","Main","--accessToken","DUMMY_ACCESS","--refresh_token=DUMMY_REFRESH","--username","Player","--uuid","test-uuid","--userProperties","DUMMY_PROPERTIES");
  Method m=MinecraftProcessLauncher.class.getDeclaredMethod("shellJoin",List.class);m.setAccessible(true);String out=(String)m.invoke(null,cmd);
  check(!out.contains("DUMMY_ACCESS"),"Packaged command logger redacts access token");check(!out.contains("DUMMY_REFRESH"),"Packaged command logger redacts inline refresh token");check(!out.contains("DUMMY_PROPERTIES"),"User properties redacted");
  check(out.contains("Player")&&out.contains("test-uuid"),"Nonsecret diagnostic arguments preserved");
  check(cmd.get(3).equals("DUMMY_ACCESS"),"Actual login arguments unchanged");
  System.out.println("PASS packaged logger privacy; dummy values only, launch arguments unchanged");
 }
 static void network(Path root)throws Exception {
  AtomicInteger md=new AtomicInteger(),forgeMd=new AtomicInteger(),archive=new AtomicInteger(),bad=new AtomicInteger();
  byte[] valid=jar(Map.of("payload.txt","FIXTURE".getBytes()),"FixtureInstaller");
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String origin="http://127.0.0.1:"+server.getAddress().getPort();
  server.createContext("/",e->{byte[] b;int status=200;
   switch(e.getRequestURI().getPath()){
    case "/fabric-meta":if(md.incrementAndGet()==1){status=503;b="busy".getBytes();}else b="{}".getBytes();break;
    case "/forge-meta":if(forgeMd.incrementAndGet()==1){status=503;b="busy".getBytes();}else b="{}".getBytes();break;
    case "/archive":b=archive.incrementAndGet()==1?"HTTP 200 but not a JAR".getBytes():valid;break;
    default:bad.incrementAndGet();b="corrupt".getBytes();
   }
   e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);e.close();
  });server.start();
  try {
   var fabric=new FabricInstallerService();var forge=new ModLoaderInstallerService();
   check(invoke(fabric,"getText",new Class[]{String.class},origin+"/fabric-meta").equals("{}")&&md.get()==2,"Actual Fabric metadata method retries 503");
   check(invoke(forge,"getText",new Class[]{String.class},origin+"/forge-meta").equals("{}")&&forgeMd.get()==2,"Actual Forge/Neo metadata method retries 503");
   Path target=root.resolve("network/fixture.jar");
   invoke(fabric,"download",new Class[]{String.class,Path.class},origin+"/archive",target);
   check(archive.get()==2&&Arrays.equals(Files.readAllBytes(target),valid),"Corrupt JAR response retried before publication");
   invoke(fabric,"download",new Class[]{String.class,Path.class},origin+"/archive",target);check(archive.get()==2,"Valid JAR cache reused offline without request");
   Path badDest=write(root.resolve("network/invalid.jar"),"OLD INVALID BUT PRESERVED ON FAILURE");byte[] old=Files.readAllBytes(badDest);
   boolean failed=false;try{invoke(fabric,"download",new Class[]{String.class,Path.class},origin+"/bad",badDest);}catch(IOException e){failed=true;}
   check(failed&&bad.get()==3,"Repeated invalid JAR response stops after three attempts");check(Arrays.equals(old,Files.readAllBytes(badDest)),"Existing artifact not destroyed on failed repair");
   try(var files=Files.list(target.getParent())){check(files.noneMatch(p->p.getFileName().toString().endsWith(".part")),"Failed staging downloads cleaned");}
   Method dl=ModLoaderInstallerService.class.getDeclaredMethod("download",String.class,Path.class,Consumer.class,String.class);dl.setAccessible(true);
   Path fi=root.resolve("network/forge-installer.jar");dl.invoke(forge,origin+"/archive",fi,null,"Forge");
   check(Arrays.equals(Files.readAllBytes(fi),valid),"Production Forge/Neo installer download publishes validated JAR");
  }finally{server.stop(0);}
  System.out.println("PASS actual loader transport routing, transient failures, JAR integrity, cache and failed-repair preservation");
 }
 static Path compileInstaller(Path root)throws Exception {
  Path src=write(root.resolve("FixtureInstaller.java"),"import java.nio.file.*; public class FixtureInstaller { public static void main(String[] a)throws Exception {if(a.length!=2)throw new Exception(\"Arguments lost\"); Files.writeString(Path.of(a[1]).resolve(\"fixture-installer-ran\"),a[0]);System.out.println(\"Fixture installer ran\");} }");
  check(ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","21","-d",root.toString(),src.toString())==0,"Synthetic installer compiled");
  return write(root.resolve("installer.jar"),jar(Map.of("FixtureInstaller.class",Files.readAllBytes(root.resolve("FixtureInstaller.class"))),"FixtureInstaller"));
 }
 static void installer(Path root)throws Exception {
  var inst=new MinecraftInstallation(root.resolve("installer game with spaces"));String base="26.1.2";
  write(inst.versionJson(base),"{\"id\":\"26.1.2\",\"mainClass\":\"fixture.Main\",\"javaVersion\":{\"majorVersion\":25},\"libraries\":[]}");
  write(inst.versionJar(base),new byte[]{1});Path profiles=write(inst.gameDir().resolve("launcher_profiles.json"),"USER PROFILES SENTINEL");
  Path jar=compileInstaller(root.resolve("installer-source"));Path helper=root.resolve("prepare-java-fixture");
  String java=Path.of(System.getProperty("java.home"),"bin","java").toString();Path requested=root.resolve("requested-java-major");
  write(helper,"#!/bin/sh\nwhile [ $# -gt 0 ]; do\ncase \"$1\" in\n--major) shift; printf '%s' \"$1\" > '"+requested+"';;\n--result) shift; result=\"$1\";;\nesac\nshift\ndone\nprintf '%s' '{\"java\":\""+java+"\"}' > \"$result\"\n");helper.toFile().setExecutable(true);
  String before=System.getProperty("kapkan.nativeBootstrap");System.setProperty("kapkan.nativeBootstrap",helper.toString());
  try {
   var service=new ModLoaderInstallerService();Class<?>[] sig={MinecraftInstallation.class,String.class,Path.class,String.class,String.class,Consumer.class};
   invoke(service,"runInstaller",sig,inst,base,jar,"--installClient","Forge",(Consumer<String>)s->{});
   check(Files.readString(requested).equals("25"),"Forge asks managed bootstrap for profile's required Java 25 (fixture returns test JVM)");
   check(Files.readString(inst.gameDir().resolve("fixture-installer-ran")).equals("--installClient"),"Forge flags and spaced game path preserved");
   invoke(service,"runInstaller",sig,inst,base,jar,"--install-client","NeoForge",null);
   check(Files.readString(inst.gameDir().resolve("fixture-installer-ran")).equals("--install-client"),"NeoForge flag preserved");
   check(Files.readString(profiles).equals("USER PROFILES SENTINEL"),"Installer launch never overwrites existing profiles");
  }finally{if(before==null)System.clearProperty("kapkan.nativeBootstrap");else System.setProperty("kapkan.nativeBootstrap",before);}
  LoaderInstallSupport.runCommand(List.of(java,"-version"),root,"Fixture",null,Duration.ofSeconds(10));check(true,"Successful real Java subprocess handled");
  boolean nonzero=false;try{LoaderInstallSupport.runCommand(List.of(java,"-NotAnActualVMOption"),root,"Fixture",null,Duration.ofSeconds(10));}catch(IOException e){nonzero=e.getMessage().contains("exited with code");}check(nonzero,"Nonzero installer exit surfaces context");
  Path sleeper=write(root.resolve("sleep-fixture.sh"),"#!/bin/sh\necho $$ > '"+root.resolve("sleep-pid")+"'\nexec sleep 60\n");sleeper.toFile().setExecutable(true);
  long start=System.nanoTime();boolean timeout=false;
  try{LoaderInstallSupport.runCommand(List.of(sleeper.toString()),root,"Fixture",null,Duration.ofMillis(200));}catch(IOException e){timeout=e.getMessage().contains("timed out");}
  check(timeout&&System.nanoTime()-start<TimeUnit.SECONDS.toNanos(5),"Hung installer has a working timeout");
  long pid=Long.parseLong(Files.readString(root.resolve("sleep-pid")).trim());for(int i=0;i<50&&ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);i++)Thread.sleep(20);
  check(!ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false),"Timed-out fixture process stopped");
  System.out.println("PASS actual Forge/Neo installer method, managed-Java request, correct flags, bounded subprocess, profile preservation");
 }
 public static void main(String[] args)throws Exception {
  Path root=Files.createTempDirectory("kapkan-hardening-fixtures-");String home=System.getProperty("user.home");System.setProperty("user.home",root.resolve("home").toString());
  try{readiness(root);profiles(root);privacy();network(root);installer(root);System.out.println("HARDENING SELF-TEST: PASS ("+assertions+" assertions)");}
  finally{System.setProperty("user.home",home);try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
