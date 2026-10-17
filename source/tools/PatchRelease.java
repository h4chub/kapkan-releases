import org.objectweb.asm.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.io.*;

/** Consolidates the known production classpath; redirects only named integration points. */
public class PatchRelease {
 static final Map<String,Integer> hooks=new TreeMap<>();
 static void hit(String k){hooks.merge(k,1,Integer::sum);}
 public static void main(String[] args)throws Exception {
  Path base=Path.of(args[0]), classes=Path.of(args[1]), out=Path.of(args[2]);
  var entries=new TreeMap<String,byte[]>();
  try(var jar=new JarFile(base.toFile())){
   var en=jar.entries();while(en.hasMoreElements()){
    var e=en.nextElement();if(e.isDirectory()||e.getName().equalsIgnoreCase("META-INF/MANIFEST.MF"))continue;
    byte[] b=jar.getInputStream(e).readAllBytes();
    if(e.getName().endsWith(".class"))b=patch(b);
    entries.put(e.getName(),b);
   }
  }
  for(String key:List.of("assetDownloads","fabricText","fabricDownload","loaderText","loaderDownload","installerRun","profileMatch","profileNormalize","logPrivacy","fabricLibraryCheck","loaderLogDefault","fabricDetectorJarCheck","launchPlanIdentity")) {
   if(!hooks.containsKey(key)||hooks.get(key)<1)throw new IllegalStateException("Required production hook not found: "+key);
  }
  System.out.println("Verified production hooks: "+hooks);
  if(hooks.getOrDefault("fabricLaunchPreflight",0)!=1)throw new IllegalStateException("Expected final Fabric launch preflight hook");
  try(var walk=Files.walk(classes)){
   for(Path f:walk.filter(Files::isRegularFile).toList())entries.put(classes.relativize(f).toString().replace(File.separatorChar,'/'),Files.readAllBytes(f));
  }
  Manifest manifest=new Manifest();manifest.getMainAttributes().putValue("Manifest-Version","1.0");
  manifest.getMainAttributes().putValue("Main-Class","gg.kapkan.release.ReleaseMain");
  manifest.getMainAttributes().putValue("Implementation-Version","1.0.5");
  manifest.getMainAttributes().putValue("KapKan-Build","client-release-1.0.5");
  manifest.getMainAttributes().putValue("KapKan-Hotfix","fabric-dependency-recovery-3");
  Path temporary=Files.createTempFile(out.toAbsolutePath().getParent(),"launcher-",".jar.tmp");
  try(var jar=new JarOutputStream(Files.newOutputStream(temporary),manifest)){
   for(var e:entries.entrySet()){var ze=new JarEntry(e.getKey());ze.setTime(0);jar.putNextEntry(ze);jar.write(e.getValue());jar.closeEntry();}
  }
  try{Files.move(temporary,out,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
  catch(AtomicMoveNotSupportedException e){Files.move(temporary,out,StandardCopyOption.REPLACE_EXISTING);}
  System.out.println("Release jar: "+out+" ("+entries.size()+" entries)");
 }
 static byte[] patch(byte[] b){
  var reader=new ClassReader(b);var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);boolean[] changed={false};
  reader.accept(new ClassVisitor(Opcodes.ASM9,writer){
   String owner;
   public void visit(int v,int a,String n,String s,String sup,String[] i){owner=n;super.visit(v,a,n,s,sup,i);}
   public MethodVisitor visitMethod(int a,String n,String d,String s,String[] e){
    if(owner.equals("gg/kapkan/launcher/launch/MinecraftProcessLauncher")&&n.equals("verifyAndRepairFabricCommand")&&
       d.equals("(Lgg/kapkan/launcher/launch/LaunchPlan;Ljava/util/List;Ljava/util/function/Consumer;)V")) {
      changed[0]=true;hit("fabricLaunchPreflight");var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();
      for(int j=0;j<3;j++)mv.visitVarInsn(Opcodes.ALOAD,j);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/FabricLaunchSupport","verify",d,false);
      mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
    }
    if(owner.equals("gg/kapkan/launcher/launch/MinecraftProcessLauncher")&&n.equals("shellJoin")&&d.equals("(Ljava/util/List;)Ljava/lang/String;")) {
      changed[0]=true;hit("logPrivacy");var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/LaunchLogPrivacy","command",d,false);
      mv.visitInsn(Opcodes.ARETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
    }
    if(owner.equals("gg/kapkan/launcher/launch/FabricInstallerService")||owner.equals("gg/kapkan/launcher/launch/ModLoaderInstallerService")) {
      boolean fabric=owner.endsWith("/FabricInstallerService");
      if(n.equals("getText")&&d.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
        changed[0]=true;hit(fabric?"fabricText":"loaderText");var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitFieldInsn(Opcodes.GETFIELD,owner,"http","Ljava/net/http/HttpClient;");mv.visitVarInsn(Opcodes.ALOAD,1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/LoaderNetwork","text","(Ljava/net/http/HttpClient;Ljava/lang/String;)Ljava/lang/String;",false);
        mv.visitInsn(Opcodes.ARETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
      }
      if(fabric&&n.equals("download")&&d.equals("(Ljava/lang/String;Ljava/nio/file/Path;)V")) {
        changed[0]=true;hit("fabricDownload");var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitFieldInsn(Opcodes.GETFIELD,owner,"http","Ljava/net/http/HttpClient;");mv.visitVarInsn(Opcodes.ALOAD,1);mv.visitVarInsn(Opcodes.ALOAD,2);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/LoaderNetwork","jar","(Ljava/net/http/HttpClient;Ljava/lang/String;Ljava/nio/file/Path;)V",false);
        mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
      }
      if(!fabric&&n.equals("download")&&d.equals("(Ljava/lang/String;Ljava/nio/file/Path;Ljava/util/function/Consumer;Ljava/lang/String;)V")) {
        changed[0]=true;hit("loaderDownload");var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitFieldInsn(Opcodes.GETFIELD,owner,"http","Ljava/net/http/HttpClient;");for(int j=1;j<=4;j++)mv.visitVarInsn(Opcodes.ALOAD,j);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/LoaderNetwork","installer","(Ljava/net/http/HttpClient;Ljava/lang/String;Ljava/nio/file/Path;Ljava/util/function/Consumer;Ljava/lang/String;)V",false);
        mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
      }
      if(!fabric&&n.equals("runInstaller")&&d.equals("(Lgg/kapkan/launcher/launch/MinecraftInstallation;Ljava/lang/String;Ljava/nio/file/Path;Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Consumer;)V")) {
        changed[0]=true;hit("installerRun");var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();for(int j=1;j<=6;j++)mv.visitVarInsn(Opcodes.ALOAD,j);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/LoaderInstallSupport","run",d,false);
        mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
      }
      if(!fabric&&n.equals("profileTargetsBase")&&d.equals("(Lgg/kapkan/launcher/launch/MinecraftInstallation;Ljava/lang/String;Ljava/lang/String;)Z")) {
        changed[0]=true;hit("profileMatch");var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();for(int j=1;j<=3;j++)mv.visitVarInsn(Opcodes.ALOAD,j);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/ProfileHardening","targetsBase",d,false);
        mv.visitInsn(Opcodes.IRETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
      }
    }

    // V14's final Fabric verifier used a Unix-only path substring. On Windows the
    // command classpath contains backslashes, so an installed loader could be falsely rejected.
    if(owner.equals("gg/kapkan/launcher/launch/MinecraftProcessLauncher") &&
       n.equals("lambda$verifyAndRepairFabricCommand$4") &&
       d.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z")) {
      changed[0]=true;
      var mv=super.visitMethod(a,n,d,s,e); mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD,2); // classpath entry
      mv.visitVarInsn(Opcodes.ALOAD,0); // loader version
      mv.visitVarInsn(Opcodes.ALOAD,1); // expected jar name
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/FabricClasspathSupport","matchesLoader","(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z",false);
      mv.visitInsn(Opcodes.IRETURN); mv.visitMaxs(0,0); mv.visitEnd(); return null;
    }
    if(owner.equals("gg/kapkan/launcher/install/BaseGameInstallerService")) {
     changed[0]=true;
     // Preserve version/library selection; replace the serial asset loop with a bounded group.
     if(n.equals("installAssets") && d.equals("(Ljava/nio/file/Path;Ljava/util/Map;Ljava/util/function/Consumer;)V")) {
      hit("assetDownloads");var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitFieldInsn(Opcodes.GETFIELD,owner,"http","Ljava/net/http/HttpClient;");
      for(int i=1;i<=3;i++)mv.visitVarInsn(Opcodes.ALOAD,i);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/AssetInstaller","install","(Ljava/net/http/HttpClient;Ljava/nio/file/Path;Ljava/util/Map;Ljava/util/function/Consumer;)V",false);
      mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
     }
     if(n.equals("downloadRaw") && d.equals("(Ljava/net/URI;Ljava/nio/file/Path;Ljava/lang/String;J)V")) {
      var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitFieldInsn(Opcodes.GETFIELD,owner,"http","Ljava/net/http/HttpClient;");
      mv.visitVarInsn(Opcodes.ALOAD,1);mv.visitVarInsn(Opcodes.ALOAD,2);mv.visitVarInsn(Opcodes.ALOAD,3);mv.visitVarInsn(Opcodes.LLOAD,4);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/RetryDownloader","download","(Ljava/net/http/HttpClient;Ljava/net/URI;Ljava/nio/file/Path;Ljava/lang/String;J)V",false);
      mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
     }
     if(n.equals("getText") && d.equals("(Ljava/net/URI;)Ljava/lang/String;")) {
      var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();
      mv.visitVarInsn(Opcodes.ALOAD,0);mv.visitFieldInsn(Opcodes.GETFIELD,owner,"http","Ljava/net/http/HttpClient;");mv.visitVarInsn(Opcodes.ALOAD,1);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/RetryDownloader","text","(Ljava/net/http/HttpClient;Ljava/net/URI;)Ljava/lang/String;",false);
      mv.visitInsn(Opcodes.ARETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
     }
     if(n.equals("message") && d.equals("(Ljava/lang/Throwable;)Ljava/lang/String;")) {
      var mv=super.visitMethod(a,n,d,s,e);mv.visitCode();mv.visitVarInsn(Opcodes.ALOAD,0);
      mv.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/RetryDownloader","installError",d,false);
      mv.visitInsn(Opcodes.ARETURN);mv.visitMaxs(0,0);mv.visitEnd();return null;
     }
    }
    return new MethodVisitor(Opcodes.ASM9,super.visitMethod(a,n,d,s,e)){
     boolean baseGuardReplaced=false;
     public void visitCode(){
      super.visitCode();
      if(owner.equals("gg/kapkan/launcher/launch/ModLoaderInstallerService")&&n.equals("ensureInstalled")&&d.equals("(Lgg/kapkan/launcher/launch/MinecraftInstallation;Ljava/lang/String;Ljava/lang/String;Ljava/util/function/Consumer;)Ljava/lang/String;")){
       hit("loaderLogDefault");changed[0]=true;super.visitVarInsn(Opcodes.ALOAD,4);
       super.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/LaunchLogPrivacy","logger","(Ljava/util/function/Consumer;)Ljava/util/function/Consumer;",false);super.visitVarInsn(Opcodes.ASTORE,4);
      }
     }
     public void visitInsn(int opcode){
      if(opcode==Opcodes.ARETURN&&owner.equals("gg/kapkan/launcher/launch/LaunchPlanBuilder")&&n.equals("build")&&d.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;I)Lgg/kapkan/launcher/launch/LaunchPlan;")) {
       changed[0]=true;hit("launchPlanIdentity");super.visitVarInsn(Opcodes.ALOAD,7);super.visitVarInsn(Opcodes.ALOAD,2);
       super.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/ProfileHardening","normalizePlan","(Lgg/kapkan/launcher/launch/LaunchPlan;Lgg/kapkan/launcher/launch/VersionProfile;Ljava/lang/String;)Lgg/kapkan/launcher/launch/LaunchPlan;",false);
      }
      if(opcode==Opcodes.ARETURN&&owner.equals("gg/kapkan/launcher/launch/VersionProfileLoader")&&n.equals("load")&&d.equals("(Ljava/lang/String;I)Lgg/kapkan/launcher/launch/VersionProfile;")) {
       changed[0]=true;hit("profileNormalize");super.visitMethodInsn(Opcodes.INVOKESTATIC,"gg/kapkan/release/ProfileHardening","normalize","(Lgg/kapkan/launcher/launch/VersionProfile;)Lgg/kapkan/launcher/launch/VersionProfile;",false);
      }
      super.visitInsn(opcode);
     }
     public void visitMethodInsn(int op,String o,String name,String desc,boolean itf){
      if(o.equals("java/nio/file/Files")&&name.equals("isRegularFile")&&desc.equals("(Ljava/nio/file/Path;[Ljava/nio/file/LinkOption;)Z")&&
        (owner.equals("gg/kapkan/launcher/launch/FabricDetector")||(owner.equals("gg/kapkan/launcher/launch/FabricInstallerService")&&n.equals("ensureInstalled")))) {
        o="gg/kapkan/release/LoaderNetwork";name="jarUsable";hit(owner.endsWith("/FabricDetector")?"fabricDetectorJarCheck":"fabricLibraryCheck");changed[0]=true;
      }
      // Keep interrupted first installs repairable even after the client JAR was downloaded.
      if(o.equals("gg/kapkan/launcher/launch/MinecraftInstallation")&&name.equals("versionInstalled")&&desc.equals("(Ljava/lang/String;)Z")&&
        (owner.equals("gg/kapkan/v23/KapKanV23Launcher$App$4") || (owner.equals("gg/kapkan/launcher/install/BaseGameInstallerService")&&n.equals("ensureInstalled")&&!baseGuardReplaced))){
          o="gg/kapkan/release/FreshInstallSupport";name="isPrepared";desc="(Lgg/kapkan/launcher/launch/MinecraftInstallation;Ljava/lang/String;)Z";op=Opcodes.INVOKESTATIC;itf=false;baseGuardReplaced=true;changed[0]=true;
      }
      if(o.equals("gg/kapkan/launcher/runtime/MacJavaFix")&&name.equals("find")&&desc.equals("(Lgg/kapkan/launcher/launch/MinecraftInstallation;ILgg/kapkan/launcher/launch/VersionProfile;)Lgg/kapkan/launcher/launch/JavaLocator$JavaRuntime;")){o="gg/kapkan/launcher/runtime/ManagedJava";changed[0]=true;}
      if(owner.equals("gg/kapkan/v23/KapKanV23Launcher$App$4")&&o.equals("gg/kapkan/launcher/importer/LocalVersionImporter")&&name.equals("ensureFullyImported")&&desc.equals("(Lgg/kapkan/launcher/launch/MinecraftInstallation;Ljava/lang/String;)Z")){o="gg/kapkan/release/FreshInstallSupport";changed[0]=true;}
      if(o.equals("gg/kapkan/v23/KapKanV23Launcher$Keychain")&&(name.equals("save")||name.equals("load"))){o="gg/kapkan/release/PlatformCredentials";changed[0]=true;}
      super.visitMethodInsn(op,o,name,desc,itf);
     }
     public void visitLdcInsn(Object value){
      // A small initial allocation avoids reserving 2 GiB for a low-memory computer.
      // The existing plan still obtains Xmx from the hardware-aware policy.
      if(owner.equals("gg/kapkan/launcher/launch/LaunchPlanBuilder")&&value.equals("-Xms2G")){value="-Xms512M";changed[0]=true;}
      super.visitLdcInsn(value);
     }
    };
   }
  },0);
  return changed[0]?writer.toByteArray():b;
 }
}
