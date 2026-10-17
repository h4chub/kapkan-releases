package gg.kapkan.release;

import gg.kapkan.launcher.launch.*;
import gg.kapkan.launcher.runtime.ManagedJava;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Keep vendor installer flags, but prepare the required Java and bound subprocess lifetime. */
public final class LoaderInstallSupport {
 private LoaderInstallSupport() {}
 public static void run(MinecraftInstallation inst,String base,Path jar,String flag,String label,Consumer<String> log)throws Exception {
  FreshInstallSupport.validateBaseId(base);LoaderNetwork.validateJar(jar,true,"");
  Path root=inst.gameDir().toAbsolutePath().normalize();Files.createDirectories(root);
  Path stub=root.resolve("launcher_profiles.json");
  if(!Files.exists(stub)&&!Files.exists(root.resolve("launcher_profiles_microsoft_store.json"))) {
   try{Files.writeString(stub,"{\"profiles\":{},\"settings\":{},\"version\":3}",StandardOpenOption.CREATE_NEW);}catch(FileAlreadyExistsException ignored){}
  }
  VersionProfile profile=new VersionProfileLoader(inst).load(base);
  var runtime=ManagedJava.find(inst,profile.javaMajor(),profile);
  if(!flag.equals("--installClient")&&!flag.equals("--install-client"))throw new IOException("Unrecognized installer mode");
  var command=List.of(runtime.executable().toString(),"-jar",jar.toAbsolutePath().toString(),flag,root.toString());
  LaunchLogPrivacy.logger(log).accept(label+": starting official installer with Java "+profile.javaMajor());
  runCommand(command,root,label,log,Duration.ofMinutes(20));
 }
 static void runCommand(List<String> command,Path root,String label,Consumer<String> log,Duration timeout)throws Exception {
  Consumer<String> sink=LaunchLogPrivacy.logger(log);
  Process p=new ProcessBuilder(command).directory(root.toFile()).redirectErrorStream(true).start();
  // Close unused stdin so installers cannot wait forever for terminal input.
  p.getOutputStream().close();
  Deque<String> tail=new ArrayDeque<>();
  Thread reader=Thread.ofPlatform().daemon().name("KapKan-installer-output").start(()->{
   try(var br=new BufferedReader(new InputStreamReader(p.getInputStream(),StandardCharsets.UTF_8))) {
    String line;while((line=br.readLine())!=null){if(line.length()>4096)line=line.substring(0,4096);synchronized(tail){if(tail.size()>=8)tail.removeFirst();tail.addLast(line);}try{sink.accept(label+": "+line);}catch(RuntimeException ignored){}}
   }catch(IOException ignored){}
  });
  try {
   if(!p.waitFor(timeout.toMillis(),TimeUnit.MILLISECONDS))throw new IOException(label+" installer timed out. Completed game files and your mods were kept; retry Play.");
   reader.join(2000);
   if(p.exitValue()!=0){String last;synchronized(tail){last=String.join("\n",tail);}throw new IOException(label+" installer exited with code "+p.exitValue()+".\n"+last);}
  }catch(InterruptedException e){Thread.currentThread().interrupt();throw e;}
  finally {
   if(p.isAlive()){p.toHandle().descendants().forEach(ProcessHandle::destroy);p.destroy();if(p.isAlive()){p.toHandle().descendants().forEach(ProcessHandle::destroyForcibly);p.destroyForcibly();}}
   try{p.getInputStream().close();}catch(IOException ignored){}
  }
 }
}
