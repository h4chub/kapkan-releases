package gg.kapkan.release;

import gg.kapkan.launcher.launch.*;
import java.util.*;

/** Preserve profile semantics, while letting a child override the same library selection. */
public final class ProfileHardening {
 private ProfileHardening() {}
 public static VersionProfile normalize(VersionProfile p) {
  List<Object> in=p.libraries();Map<String,Object> selected=new LinkedHashMap<>();int anonymous=0;
  for(Object obj:in) {
   Map<String,Object> lib=MiniJson.object(obj);String name=MiniJson.string(lib.get("name"));
   String[] ext=name.split("@",2),parts=ext[0].split(":",-1);
   String key;
   if(parts.length<3)key="unknown:"+(anonymous++);
   else key=parts[0]+":"+parts[1]+":"+(parts.length>3?parts[3]:"")+"@"+(ext.length==2?ext[1]:"jar")
     +"|rules="+canonical(lib.get("rules"))+"|natives="+canonical(lib.get("natives"));
   // Different classifiers / OS selectors stay distinct. Matching child entries
   // replace parent versions instead of leaving both on the classpath, oldest first.
   selected.put(key,obj);
  }
  if(selected.size()==in.size())return p;
  return new VersionProfile(p.id(),p.displayVersion(),p.mainClass(),p.jarVersion(),p.type(),p.javaMajor(),p.assetIndexId(),p.gameArguments(),p.jvmArguments(),List.copyOf(selected.values()),p.raw());
 }
 private static String canonical(Object o) {
  if(o instanceof Map<?,?> m){Map<String,String> sorted=new TreeMap<>();m.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted.toString();}
  if(o instanceof List<?> l)return l.stream().map(ProfileHardening::canonical).toList().toString();
  return Objects.toString(o,"");
 }
 public static LaunchPlan normalizePlan(LaunchPlan plan,VersionProfile profile,String requestedLoader) {
  String label=plan.loaderLabel();
  if("Forge".equalsIgnoreCase(requestedLoader))label="Forge";
  else if("NeoForge".equalsIgnoreCase(requestedLoader))label="NeoForge";
  return new LaunchPlan(plan.command(),plan.workingDirectory(),plan.launchVersion(),profile.jarVersion(),label,plan.javaExecutable(),plan.javaMajor(),plan.installationDirectory());
 }
 public static boolean targetsBase(MinecraftInstallation inst,String profileId,String base) {
  try {
   FreshInstallSupport.validateBaseId(base);
   if(profileId==null||!profileId.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]{0,179}")||profileId.contains(".."))return false;
   VersionProfile p=new VersionProfileLoader(inst).load(profileId);
   return base.equals(p.jarVersion())&&!p.mainClass().isBlank()&&inst.versionInstalled(base)
     &&InstallIntegrity.librariesComplete(inst,p.libraries());
  }catch(Exception e){return false;}
 }
}
