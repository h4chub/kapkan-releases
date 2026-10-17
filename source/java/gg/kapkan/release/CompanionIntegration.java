package gg.kapkan.release;

import gg.kapkan.launcher.launch.LaunchPlan;
import gg.kapkan.launcher.launch.MiniJson;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;

/** Injects the selected build per process and preserves incompatible older companions as backups. */
public final class CompanionIntegration {
    public static final Map<String,String> FILES=Map.of(
        "26.1","KapKan-1.0.6-mc26.1-fabric.jar",
        "26.1.1","KapKan-1.0.6-mc26.1.1-fabric.jar",
        "26.1.2","KapKan-1.0.6-mc26.1.2-fabric.jar");
    public static final String FILE=FILES.get("26.1.2");
    public static final String BUILD="client-1.0.6";
    private CompanionIntegration(){}
    public static LaunchPlan prepare(LaunchPlan plan,Consumer<String> log) {
        if(!"Fabric".equalsIgnoreCase(plan.loaderLabel()))return plan;
        try {
            Path source;
            if(FILES.containsKey(plan.baseVersion())) {
                if(!supportsLoader(plan.launchVersion())){log.accept("KapKan badges need Fabric Loader 0.19.5 or newer; retaining the current loader and launching without the companion.");return plan;}
                String configured="26.1.2".equals(plan.baseVersion())?System.getProperty("kapkan.presenceJar",""):"";
                String directory=System.getProperty("kapkan.companionDirectory","");
                Path resources=directory.isBlank()?Path.of(CompanionIntegration.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getParent():Path.of(directory);
                source=configured.isBlank()?resources.resolve(FILES.get(plan.baseVersion())):Path.of(configured);
                validate(source,plan.baseVersion());
                retireKnownLegacy(plan.workingDirectory());
                retireKnownPresence(plan.workingDirectory());
                if(migrateManualPresence(plan.workingDirectory(),plan.baseVersion(),log)) { log.accept("KapKan badges: matching manually installed companion retained; launcher association enabled.");return withArgs(plan,null); }
            } else if("1.21.11".equals(plan.baseVersion())) {
                String legacy=System.getProperty("kapkan.clientCoreJar","");
                if(legacy.isBlank())return plan;source=Path.of(legacy);
            } else {log.accept("KapKan badges: this version is not supported; continuing normal launch.");return plan;}
            if(!Files.isRegularFile(source)){log.accept("KapKan companion unavailable; continuing normal launch.");return plan;}
            log.accept(FILES.containsKey(plan.baseVersion())?"KapKan companion prepared for "+plan.baseVersion()+" ("+BUILD+")":"KapKan legacy companion retained for "+plan.baseVersion()+"; the 1.0.6 client features are not supported on this version.");return withArgs(plan,source);
        } catch(Exception e){log.accept("KapKan companion unavailable: "+e.getClass().getSimpleName()+". Continuing normal launch.");return plan;}
    }
    static boolean supportsLoader(String version){
        try{String[] n=version.substring("fabric-loader-".length()).split("-",2)[0].split("\\.");int major=Integer.parseInt(n[0]),minor=Integer.parseInt(n[1]),patch=Integer.parseInt(n[2]);return major>0||minor>19||minor==19&&patch>=5;}
        catch(Exception e){return false;}
    }
    private static LaunchPlan withArgs(LaunchPlan p,Path jar){
        var cmd=new ArrayList<>(p.command());
        if(jar!=null)cmd.add(1,"-Dfabric.addMods="+jar.toAbsolutePath());
        if(FILES.containsKey(p.baseVersion())){
            cmd.add(1,"-Dkapkan.launchSession="+UUID.randomUUID());
            cmd.add(1,"-Dkapkan.presence.config="+BadgeSettings.FILE.toAbsolutePath());
        }
        return new LaunchPlan(cmd,p.workingDirectory(),p.launchVersion(),p.baseVersion(),p.loaderLabel(),p.javaExecutable(),p.javaMajor(),p.installationDirectory());
    }
    static void validate(Path source,String version)throws Exception {
        try(var jar=new JarFile(source.toFile())){
            if(jar.getEntry("gg/kapkan/presence/BadgeClient.class")==null||jar.getEntry("gg/kapkan/presence/cosmetics/ClientFeatures.class")==null)throw new java.io.IOException("Incomplete companion");
            var entry=jar.getJarEntry("fabric.mod.json");if(entry==null)throw new java.io.IOException("Missing companion metadata");
            byte[] bytes;try(var in=jar.getInputStream(entry)){bytes=in.readNBytes(65537);}
            if(bytes.length>65536)throw new java.io.IOException("Invalid companion metadata");
            var meta=MiniJson.object(MiniJson.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
            String target=MiniJson.string(MiniJson.object(meta.get("depends")).get("minecraft")).strip();
            if(target.startsWith("="))target=target.substring(1).strip();
            if(!"kapkan-presence".equals(MiniJson.string(meta.get("id")))||!"1.0.6".equals(MiniJson.string(meta.get("version")))||!version.equals(target))
                throw new java.io.IOException("Companion does not match Minecraft "+version+" / KapKan 1.0.6");
        }
    }
    private static boolean migrateManualPresence(Path game,String version,Consumer<String> log)throws Exception {
        Path mods=game.resolve("mods");if(!Files.isDirectory(mods))return false;mods=mods.toRealPath();
        boolean retained=false;
        try(var paths=Files.list(mods)){for(Path file:paths.sorted().filter(p->p.getFileName().toString().endsWith(".jar")).toList()){
            if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||!presenceId(file))continue;
            boolean matching;
            try{validate(file,version);matching=true;}catch(Exception invalid){matching=false;}
            if(matching&&!retained){retained=true;continue;}
            backup(game,file);
            log.accept(matching?"Duplicate KapKan companion backed up; one matching copy retained.":"Older or mismatched KapKan companion backed up for the update.");
        }}return retained;
    }
    private static boolean presenceId(Path file) {
        try(var jar=new JarFile(file.toFile())){
            var entry=jar.getJarEntry("fabric.mod.json");if(entry==null)return false;
            byte[] bytes;try(var in=jar.getInputStream(entry)){bytes=in.readNBytes(65537);}
            if(bytes.length>65536)return false;
            var metadata=MiniJson.object(MiniJson.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)));
            return "kapkan-presence".equals(MiniJson.string(metadata.get("id")));
        }catch(Exception malformed){return false;}
    }
    static void retireKnownLegacy(Path game)throws Exception {
        String legacy=System.getProperty("kapkan.clientCoreJar","");if(legacy.isBlank()||!Files.isRegularFile(Path.of(legacy)))return;
        Path mods=game.resolve("mods");if(!Files.isDirectory(mods))return;mods=mods.toRealPath();
        byte[] expected=sha256(Path.of(legacy));
        for(String name:List.of("KapKan-ClientCore-1.21.11-Beta1.jar","KapKan-ClientCore-1.21.11-v0.9.jar")){
            Path old=mods.resolve(name);
            if(Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS)&&MessageDigest.isEqual(expected,sha256(old)))backup(game,old);
        }
    }
    static void retireKnownPresence(Path game)throws Exception {
        Path mods=game.resolve("mods");if(!Files.isDirectory(mods))return;mods=mods.toRealPath();
        Map<Long,String> previous=Map.of(435165L,"14fd51171053b9c8d576205ae9c317d4515788bd407ab304b5b1dde1c4e27c8c",446371L,"14732932bf61579bd3e560b0a76626a30d16f96269cbb5b4978faea4c764d591",1364895L,"0f3f428c54b883b1f5659abbeab0a9cf1665f0c27eece73f16378dcd73fb77ff");
        try(var files=Files.list(mods)){
            for(Path old:files.filter(p->p.getFileName().toString().endsWith(".jar")).toList()){
                if(!Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS))continue;String digest=previous.get(Files.size(old));if(digest==null)continue;byte[] known=HexFormat.of().parseHex(digest);
                if(MessageDigest.isEqual(known,sha256(old)))backup(game,old);
            }
        }
    }
    private static byte[] sha256(Path file)throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");
        try(var in=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)){byte[] buffer=new byte[16384];for(int count;(count=in.read(buffer))!=-1;)digest.update(buffer,0,count);}
        return digest.digest();
    }
    private static void backup(Path game,Path old)throws Exception {
        if(!Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS))throw new IOException("Companion is not a regular file");
        Path managed=game.resolve(".kapkan-managed"),retired=managed.resolve("retired");
        for(Path directory:List.of(managed,retired)){
            if(Files.exists(directory,LinkOption.NOFOLLOW_LINKS)&&!Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))throw new IOException("Companion backup folder is not a regular directory");
            Files.createDirectories(directory);
        }
        byte[] digest=sha256(old);String hex=HexFormat.of().formatHex(digest),name=old.getFileName().toString();
        for(int collision=0;;collision++){
            String candidate=collision==0?name:name+"."+hex+(collision==1?"":"."+collision)+".jar";
            Path target=retired.resolve(candidate);
            if(Files.exists(target,LinkOption.NOFOLLOW_LINKS)){
                if(Files.isRegularFile(target,LinkOption.NOFOLLOW_LINKS)&&Files.size(target)==Files.size(old)&&MessageDigest.isEqual(digest,sha256(target))){Files.delete(old);return;}
                continue;
            }
            try{Files.move(old,target);return;}catch(FileAlreadyExistsException raced){/* Keep every existing backup and try another name. */}
        }
    }
}
