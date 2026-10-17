package gg.kapkan.launcher.launch;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Read-only checks. Never repairs/deletes mods, saves, controls, or other user data. */
public final class InstallIntegrity {
 private InstallIntegrity() {}
 public static String libraryPath(Map<String,Object> lib) {
  String path=MiniJson.string(MiniJson.object(MiniJson.object(lib.get("downloads")).get("artifact")).get("path"));
  return path.isBlank()?FabricDetector.mavenPath(MiniJson.string(lib.get("name"))):path;
 }
 public static boolean baseComplete(MinecraftInstallation inst,String id) {
  try {
   if(inst==null||!inst.versionInstalled(id))return false;
   Map<String,Object> raw=MiniJson.object(MiniJson.parse(Files.readString(inst.versionJson(id))));
   if(raw.isEmpty()||!id.equals(MiniJson.string(raw.get("id")))||MiniJson.string(raw.get("mainClass")).isBlank())return false;
   if(!file(inst.versionJar(id),MiniJson.object(MiniJson.object(raw.get("downloads")).get("client")),true))return false;
   if(!librariesComplete(inst,MiniJson.array(raw.get("libraries"))))return false;
   Map<String,Object> ai=MiniJson.object(raw.get("assetIndex"));
   String assetId=MiniJson.string(ai.get("id"));
   if(!assetId.isBlank()) {
    Path index=safe(inst.assetsDir().resolve("indexes"),assetId+".json");
    if(!file(index,ai,true))return false;
    Map<String,Object> data=MiniJson.object(MiniJson.parse(Files.readString(index)));
    if(!(data.get("objects") instanceof Map))return false;
    for(Object obj:MiniJson.object(data.get("objects")).values()) {
     Map<String,Object> d=MiniJson.object(obj);String hash=MiniJson.string(d.get("hash"));
     if(!hash.matches("[0-9a-fA-F]{40}"))return false;
     // Asset filename is hash-addressed. Check existence/size here to avoid hashing
     // the entire sound/texture cache on every Play; transfer verification still hashes it.
     if(!file(safe(inst.assetsDir().resolve("objects"),hash.substring(0,2)+"/"+hash),d,false))return false;
    }
   }
   Map<String,Object> log=MiniJson.object(MiniJson.object(raw.get("logging")).get("client"));
   Map<String,Object> lf=MiniJson.object(log.get("file"));String lid=MiniJson.string(lf.get("id"));
   if(!lid.isBlank()&&!file(safe(inst.assetsDir().resolve("log_configs"),lid),lf,true))return false;
   return true;
  }catch(Exception e){return false;}
 }
 public static boolean librariesComplete(MinecraftInstallation inst,List<Object> libs) {
  try {
   for(Object obj:libs) {
    Map<String,Object> lib=MiniJson.object(obj);if(lib.isEmpty()||!RuleEvaluator.allowed(lib))continue;
    Map<String,Object> downloads=MiniJson.object(lib.get("downloads"));
    Map<String,Object> artifact=MiniJson.object(downloads.get("artifact"));
    String rel=MiniJson.string(artifact.get("path"));
    // Legacy native-only library entries do not necessarily have a normal artifact.
    if(rel.isBlank()&&!lib.containsKey("natives"))rel=FabricDetector.mavenPath(MiniJson.string(lib.get("name")));
    if(rel!=null&&!rel.isBlank()&&!file(safe(inst.librariesDir(),rel),artifact,true))return false;
    Map<String,Object> natives=MiniJson.object(lib.get("natives"));
    String classifier=MiniJson.string(natives.get(RuleEvaluator.currentOsName()));
    if(!classifier.isBlank()) {
     String arch=System.getProperty("os.arch","").toLowerCase(Locale.ROOT);
     classifier=classifier.replace("${arch}", (arch.contains("aarch64")||arch.contains("arm64"))?"arm64":arch.contains("64")?"64":"32");
     Map<String,Object> nat=MiniJson.object(MiniJson.object(downloads.get("classifiers")).get(classifier));
     String np=MiniJson.string(nat.get("path"));
     if(np.isBlank()||!file(safe(inst.librariesDir(),np),nat,true))return false;
    }
   }
   return true;
  }catch(Exception e){return false;}
 }
 public static Path safe(Path root,String name)throws IOException {
  if(name==null||name.isBlank()||name.indexOf('\0')>=0||name.indexOf(':')>=0)throw new IOException("Invalid managed file path");
  name=name.replace('\\','/');
  if(name.startsWith("/")||Arrays.asList(name.split("/",-1)).contains(".."))throw new IOException("Managed path escapes its folder");
  Path base=root.toAbsolutePath().normalize(),p=base.resolve(name).normalize();
  if(!p.startsWith(base)||p.equals(base))throw new IOException("Managed path escapes its folder");
  return p;
 }
 private static boolean file(Path p,Map<String,Object> descriptor,boolean hash)throws IOException {
  if(!Files.isRegularFile(p)||Files.size(p)==0)return false;
  Object sz=descriptor.get("size");if(sz instanceof Number n && n.longValue()>=0 && Files.size(p)!=n.longValue())return false;
  String expected=MiniJson.string(descriptor.get("sha1"));
  if(hash&&!expected.isBlank()) {
   if(!expected.matches("[0-9a-fA-F]{40}"))return false;
   try {
    MessageDigest d=MessageDigest.getInstance("SHA-1");
    try(InputStream in=Files.newInputStream(p)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)d.update(b,0,n);}
    if(!HexFormat.of().formatHex(d.digest()).equalsIgnoreCase(expected))return false;
   }catch(NoSuchAlgorithmException e){throw new AssertionError(e);}
  }
  return true;
 }
}
