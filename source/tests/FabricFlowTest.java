package gg.kapkan.release;

import com.sun.net.httpserver.HttpServer;
import gg.kapkan.launcher.launch.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.io.*;
import javax.tools.ToolProvider;

/** Full packaged launch chain, but SYNTHETIC loader/client/dependency files, not Minecraft. */
public final class FabricFlowTest {
 static int assertions;
 static void check(boolean b,String msg){assertions++;if(!b)throw new AssertionError(msg);}
 static String json(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
 static Path write(Path p,byte[] b)throws IOException{Files.createDirectories(p.getParent());return Files.write(p,b);}
 static Path write(Path p,String s)throws IOException{return write(p,s.getBytes(StandardCharsets.UTF_8));}
 static String library(String name,String path,String origin,byte[] data)throws Exception{return "{\"name\":"+json(name)+",\"downloads\":{\"artifact\":{\"path\":"+json(path)+",\"url\":"+json(origin+"/repo/"+path)+",\"sha1\":"+json(HardeningSelfTest.hash(data))+",\"size\":"+data.length+"}}}";}
 static byte[] knot(Path source)throws Exception {
  Path java=write(source.resolve("net/fabricmc/loader/impl/launch/knot/KnotClient.java"),"package net.fabricmc.loader.impl.launch.knot; public final class KnotClient {public static void main(String[] args){System.out.println(\"KAPKAN_TEST_FIXTURE_ONLY: synthetic entrypoint reached, NOT MINECRAFT\");}}");
  check(ToolProvider.getSystemJavaCompiler().run(null,null,null,"--release","21","-d",source.toString(),java.toString())==0,"Fixture entrypoint compiles");
  return HardeningSelfTest.jar(Map.of("net/fabricmc/loader/impl/launch/knot/KnotClient.class",Files.readAllBytes(source.resolve("net/fabricmc/loader/impl/launch/knot/KnotClient.class"))),null);
 }
 static void run(Path home,String base,boolean intermediary,byte[] loader,byte[] dependency)throws Exception {
  System.setProperty("user.home",home.toString());
  var inst=new MinecraftInstallation(home.resolve(".kapkan/game"));String profile="fabric-loader-0.19.5-"+base;
  String loaderPath="net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar";
  String mixinVersion="0.17.4+mixin.0.8.7";
  String mixinPath="net/fabricmc/sponge-mixin/"+mixinVersion+"/sponge-mixin-"+mixinVersion+".jar";
  String intermediaryPath="net/fabricmc/intermediary/"+base+"/intermediary-"+base+".jar";
  var libraryRequests=new ConcurrentHashMap<String,AtomicInteger>();AtomicInteger metadata=new AtomicInteger();
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);String origin="http://127.0.0.1:"+server.getAddress().getPort();
  Map<String,byte[]> paths=new HashMap<>();paths.put(loaderPath,loader);paths.put(mixinPath,dependency);if(intermediary)paths.put(intermediaryPath,dependency);
  String libs=library("net.fabricmc:fabric-loader:0.19.5",loaderPath,origin,loader)+","+library("net.fabricmc:sponge-mixin:"+mixinVersion,mixinPath,origin,dependency)+(intermediary?","+library("net.fabricmc:intermediary:"+base,intermediaryPath,origin,dependency):"");
  for(String artifact:List.of("asm","asm-analysis","asm-commons","asm-tree","asm-util")) {
   String path="org/ow2/asm/"+artifact+"/9.10.1/"+artifact+"-9.10.1.jar";
   paths.put(path,dependency);libs+=","+library("org.ow2.asm:"+artifact+":9.10.1",path,origin,dependency);
  }
  String metadataJson="{\"id\":"+json(profile)+",\"inheritsFrom\":"+json(base)+",\"mainClass\":\"net.fabricmc.loader.impl.launch.knot.KnotClient\",\"libraries\":["+libs+"]}";
  server.createContext("/",e->{byte[] out;int status=200;String path=e.getRequestURI().getPath();
   if(path.startsWith("/repo/")){String rel=path.substring(6);libraryRequests.computeIfAbsent(rel,k->new AtomicInteger()).incrementAndGet();out=paths.get(rel);if(out==null){status=404;out="not a fixture path".getBytes();}}
   else if(path.endsWith("/profile/json")){metadata.incrementAndGet();out=metadataJson.getBytes();}
   else if(path.equals("/v2/versions/loader/"+base)){metadata.incrementAndGet();out="[{\"loader\":{\"version\":\"0.19.5\",\"stable\":true}}]".getBytes();}
   else{status=404;out="unexpected path".getBytes();}
   e.sendResponseHeaders(status,out.length);e.getResponseBody().write(out);e.close();
  });server.start();
  System.setProperty("kapkan.fabricMetaBase",origin);
  Path helper=home.resolve("prepare-java-fixture");String java=Path.of(System.getProperty("java.home"),"bin/java").toString();
  write(helper,"#!/bin/sh\nwhile [ $# -gt 0 ]; do\nif [ \"$1\" = --result ]; then shift; result=\"$1\"; fi\nshift\ndone\nprintf '%s' '{\"java\":\""+java+"\"}' > \"$result\"\n");helper.toFile().setExecutable(true);System.setProperty("kapkan.nativeBootstrap",helper.toString());
  // Deliberately no official launcher, no prior Fabric or game libraries, no shared cache.
  write(inst.versionJson(base),"{\"id\":"+json(base)+",\"mainClass\":\"fixture.Vanilla\",\"type\":\"release\",\"javaVersion\":{\"majorVersion\":21},\"libraries\":[],\"arguments\":{\"jvm\":[\"-cp\",\"${classpath}\"],\"game\":[\"--username\",\"${auth_player_name}\",\"--accessToken\",\"DUMMY_LOGIN_VALUE\"]}}");
  write(inst.versionJar(base),HardeningSelfTest.jar(Map.of("fixture.txt","NOT MINECRAFT".getBytes()),null));
  Map<Path,byte[]> sentinels=new LinkedHashMap<>();
  for(String p:List.of("mods/user-mod.jar","config/user.cfg","saves/world/level.dat","options.txt","servers.dat","resourcepacks/keep.zip","shaderpacks/keep.zip")){Path target=inst.gameDir().resolve(p);byte[] value=("KEEP EXACT "+p).getBytes();write(target,value);sentinels.put(target,value);}
  try {
   var service=new ModLoaderInstallerService();String selected=service.ensureInstalled(inst,base,"Fabric",System.out::println);
   check(selected.equals(profile),"Fresh Fabric returns inherited profile for "+base);
   check(!Files.exists(inst.versionJar(profile)),"No fake Fabric client jar required");
   check(libraryRequests.get(loaderPath).get()==1,"Loader downloaded exactly once");
   check(Files.exists(inst.librariesDir().resolve(intermediaryPath))==intermediary,"Intermediary only when supplied by "+base+" profile");
   var builder=new LaunchPlanBuilder(inst);LaunchPlan plan=builder.build(selected,"Fabric","FixturePlayer",1024);
   check(plan.baseVersion().equals(base),"Resolved base stays exact");
   check(plan.command().contains("net.fabricmc.loader.impl.launch.knot.KnotClient"),"Knot entrypoint chosen");
   String cp=plan.command().get(plan.command().indexOf("-cp")+1);
   check(cp.contains(loaderPath)&&cp.contains(inst.versionJar(base).toString()),"Final production classpath contains loader and actual base path");
   check(cp.contains(intermediaryPath)==intermediary,"No invented intermediary in classpath");
   var profiles=new gg.kapkan.release.ModProfiles(inst.gameDir(),base,"Fabric");
   var modSelection=profiles.resolve(base,"Fabric");
   if(Boolean.getBoolean("kapkan.test.restoredJson"))write(modSelection.game().resolve("versions").resolve(profile).resolve(profile+".json"),metadataJson);
   if(Boolean.getBoolean("kapkan.test.linkedCache")) {
    Files.createDirectories(modSelection.game());
    Files.createSymbolicLink(modSelection.game().resolve("versions"),inst.gameDir().resolve("versions"));
    Files.createSymbolicLink(modSelection.game().resolve("libraries"),inst.librariesDir());
   }
   plan=profiles.apply(plan,modSelection);
   check(plan.installationDirectory().equals(inst.gameDir()),"Installation root retained separately from profile cwd");
   plan=CompanionIntegration.prepare(plan,System.out::println);
   check(plan.installationDirectory().equals(inst.gameDir()),"Companion keeps installation root");
   check(paths.size()==(intermediary?8:7),"All seven reported Fabric libraries covered");
   check(plan.workingDirectory().equals(modSelection.game()),"Production launcher uses the isolated mod profile");
   // Damage AFTER installation and plan creation. Final launch must repair without selecting a newer loader.
   int metaBeforeLaunch=metadata.get();
   Files.delete(inst.librariesDir().resolve(loaderPath));
   write(inst.librariesDir().resolve(mixinPath),HardeningSelfTest.jar(Map.of("fixture.txt",new byte[]{2}),null));
   String asmTree="org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar";
   write(inst.librariesDir().resolve(asmTree),"broken final library");
   var damagedCommand=new ArrayList<>(plan.command());
   int classpathIndex=damagedCommand.indexOf("-cp")+1;
   String stale=modSelection.game().resolve("libraries").resolve(loaderPath).toString();
   damagedCommand.set(classpathIndex,stale+File.pathSeparator+damagedCommand.get(classpathIndex));
   plan=new LaunchPlan(damagedCommand,plan.workingDirectory(),plan.launchVersion(),plan.baseVersion(),plan.loaderLabel(),plan.javaExecutable(),plan.javaMajor(),plan.installationDirectory());
   List<String> output=new CopyOnWriteArrayList<>();CountDownLatch ended=new CountDownLatch(1);AtomicInteger code=new AtomicInteger(-999);
   Process p=new MinecraftProcessLauncher().launch(plan,output::add,c->{code.set(c);ended.countDown();});
   try{check(ended.await(15,TimeUnit.SECONDS)&&code.get()==0,"Synthetic entrypoint executes through real ProcessBuilder");}
   finally{if(p.isAlive())p.destroyForcibly();}
   check(output.stream().anyMatch(s->s.contains("KAPKAN_TEST_FIXTURE_ONLY")),"Reached explicitly synthetic entrypoint");
   check(output.stream().noneMatch(s->s.contains("DUMMY_LOGIN_VALUE")),"Launch logging did not expose dummy token");
   check(output.stream().noneMatch(line->line.startsWith("Java classpath:")&&line.contains(".kapkan-profiles")),"Final classpath uses installation paths without duplicate profile cache entries");
   check(metadata.get()==metaBeforeLaunch,"Final repair reuses exact cached profile without metadata selection");
   for(String relative:List.of(loaderPath,mixinPath,asmTree))check(Arrays.equals(paths.get(relative),Files.readAllBytes(inst.librariesDir().resolve(relative))),"Final launch restored exact bytes: "+relative);
   check(output.stream().anyMatch(line->line.contains("repaired 3 libraries")),"Final launch reports completed dependency repair");
   if(Boolean.getBoolean("kapkan.test.restoredJson"))check(Files.readString(modSelection.game().resolve("versions").resolve(profile).resolve(profile+".json")).equals(metadataJson),"Manual JSON is left intact");
   int loaderCount=libraryRequests.get(loaderPath).get();int mixinCount=libraryRequests.get(mixinPath).get();
   // A nonempty corrupt dependency used to pass cache validation in the baseline.
   write(inst.librariesDir().resolve(mixinPath),"NOT A JAR BUT NONEMPTY");
   check(FabricDetector.findInstalled(inst,base).isBlank(),"Corrupt cached dependency no longer passes detection");
   service.ensureInstalled(inst,base,"Fabric",System.out::println);
   check(libraryRequests.get(mixinPath).get()==mixinCount+1,"Only corrupt dependency re-downloaded");
   check(libraryRequests.get(loaderPath).get()==loaderCount,"Valid loader kept during dependency repair");
   write(inst.versionJson(profile),"{broken");
   check(service.ensureInstalled(inst,base,"Fabric",System.out::println).equals(profile),"Corrupt profile repaired");
   check(libraryRequests.get(loaderPath).get()==loaderCount,"Profile-only repair reuses cached loader");
   Files.delete(inst.librariesDir().resolve(loaderPath));
   service.ensureInstalled(inst,base,"Fabric",System.out::println);
   check(libraryRequests.get(loaderPath).get()==loaderCount+1,"Missing loader repaired automatically");
   // Losing every Fabric library repairs the shared installation, not the profile directory.
   for(String relative:paths.keySet())Files.delete(inst.librariesDir().resolve(relative));
   service.ensureInstalled(inst,base,"Fabric",System.out::println);
   for(String relative:paths.keySet())check(LoaderNetwork.jarUsable(inst.librariesDir().resolve(relative)),"Repaired missing library "+relative);
   int requests=metadata.get();server.stop(0);
   check(service.ensureInstalled(inst,base,"Fabric",null).equals(profile),"Cached second Fabric check succeeds with metadata server shut down");
   Path custom=home.resolve("custom mods & quote '");Files.createDirectories(custom);write(custom.resolve("custom-fixture.txt"),"custom mods retained");
   var customSelection=profiles.choose(base,"Fabric",custom);
   LaunchPlan again=CompanionIntegration.prepare(profiles.apply(builder.build(profile,"Fabric","FixturePlayer",1024),customSelection),System.out::println);
   check(again.installationDirectory().equals(inst.gameDir())&&again.workingDirectory().equals(customSelection.game()),"Custom-folder plan keeps separate roots");
   List<String> replay=new CopyOnWriteArrayList<>();CountDownLatch replayEnded=new CountDownLatch(1);AtomicInteger replayCode=new AtomicInteger(-999);
   Process replayProcess=new MinecraftProcessLauncher().launch(again,replay::add,c->{replayCode.set(c);replayEnded.countDown();});
   try{check(replayEnded.await(15,TimeUnit.SECONDS)&&replayCode.get()==0,"Repaired installation launches from custom mods while metadata server is offline");}
   finally{if(replayProcess.isAlive())replayProcess.destroyForcibly();}
   check(Files.readString(customSelection.game().resolve("mods/custom-fixture.txt")).equals("custom mods retained"),"Custom mods remain reachable");
   check(again.command().contains("net.fabricmc.loader.impl.launch.knot.KnotClient")&&metadata.get()==requests,"Offline cached launch planning does not reinstall");
   for(var e:sentinels.entrySet())check(Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey())),"Untouched "+e.getKey().getFileName());
   System.out.println("FABRIC FLOW FIXTURE "+base+": PASS; intermediary="+intermediary+". NOT a Minecraft gameplay test.");
  }finally{server.stop(0);}
 }
 public static void main(String[] args)throws Exception {
  if(System.getenv("KAPKAN_MINECRAFT_DIR")!=null)throw new IllegalStateException("Run this isolated test without KAPKAN_MINECRAFT_DIR set");
  Path root=Files.createTempDirectory("kapkan-fabric-flow-with-spaces-");String home=System.getProperty("user.home"),meta=System.getProperty("kapkan.fabricMetaBase"),helper=System.getProperty("kapkan.nativeBootstrap");
  try{byte[] loader=knot(root.resolve("fixture-source")),lib=HardeningSelfTest.jar(Map.of("fixture.txt",new byte[]{1}),null);
   List<String> versions=List.of("26.2","26.1.2","26.1.1","26.1","1.21.11","1.21.10","1.21.9","1.21.8","1.21.7","1.21.6","1.21.5","1.21.4","1.21.3","1.21.2","1.21.1","1.21","1.20.6","1.20.5","1.20.4","1.20.3","1.20.2","1.20.1","1.20","1.19.4","1.19.3","1.19.2","1.19.1","1.19","1.18.2","1.18.1","1.18","26.1-snapshot-5","25w14a","21w40a");
   for(String base:versions)run(root.resolve(base),base,!base.startsWith("26."),loader,lib);
   System.out.println("FABRIC FLOW TEST: PASS ("+assertions+" assertions; "+versions.size()+" version-ID fixtures)");}
  finally{System.setProperty("user.home",home);if(meta==null)System.clearProperty("kapkan.fabricMetaBase");else System.setProperty("kapkan.fabricMetaBase",meta);if(helper==null)System.clearProperty("kapkan.nativeBootstrap");else System.setProperty("kapkan.nativeBootstrap",helper);try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
 }
}
