package gg.kapkan.release;

import gg.kapkan.launcher.launch.LaunchPlan;
import java.nio.file.*;
import java.util.*;

public final class ModProfilesTest {
    static int checks;
    static void check(boolean result,String text){checks++;if(!result)throw new AssertionError(text);}
    interface Task{void run()throws Exception;}
    static void rejects(Task task,String text)throws Exception{boolean rejected=false;try{task.run();}catch(java.io.IOException|IllegalArgumentException e){rejected=true;}check(rejected,text);}
    public static void main(String[] args)throws Exception{
        Path root=Files.createTempDirectory("kapkan-mod-profile-tests-");Path original=root.resolve("mods");Files.createDirectories(original);
        Files.writeString(original.resolve("my-mod.jar"),"legacy");Files.writeString(original.resolve("disabled.jar.disabled"),"keep-disabled");
        Files.createDirectories(root.resolve("saves/My world"));Files.writeString(root.resolve("saves/My world/level.dat"),"world");
        Files.writeString(root.resolve("options.txt"),"key_key.jump:key.keyboard.space\nmaxFps:180\n");
        ModProfiles profiles=new ModProfiles(root,"26.1.2","Fabric");
        var other=profiles.resolve("1.21.11","Fabric");check(Files.list(other.mods()).findAny().isEmpty(),"Other versions start with no inherited mods");
        var current=profiles.resolve("26.1.2","Fabric");check(Files.readString(current.mods().resolve("my-mod.jar")).equals("legacy"),"Saved current profile receives legacy mods");
        check(Files.readString(original.resolve("my-mod.jar")).equals("legacy"),"Original is preserved");
        check(Files.exists(current.mods().resolve("disabled.jar.disabled")),"Disabled mods retained");
        Files.writeString(current.mods().resolve("my-mod.jar"),"edited");profiles.resolve("26.1.2","Fabric");check(Files.readString(current.mods().resolve("my-mod.jar")).equals("edited"),"Migration never overwrites later changes");
        var forge=profiles.resolve("26.1.2","Forge");var quilt=profiles.resolve("26.1.2","Quilt");var neo=profiles.resolve("26.1.2","NeoForge");var vanilla=profiles.resolve("26.1.2","Vanilla");
        check(Set.of(current.mods(),other.mods(),forge.mods(),quilt.mods(),neo.mods(),vanilla.mods()).size()==6,"Version and loader combinations have distinct folders");
        check(!profiles.resolve("26.1.2","fabric").mods().equals(forge.mods()),"Loader matching is case insensitive without mixing loaders");
        check(profiles.resolve("../../26?1","Fabric").mods().normalize().startsWith(root),"Version names cannot escape the game root");
        rejects(()->profiles.resolve("26.1.2","../escape"),"Invalid loader rejected");
        for(var s:List.of(current,other,forge,quilt,neo,vanilla)){
            var p=new LaunchPlan(List.of("java","-Xmx2G","-cp","/untouched/cache/classpath","GameMain","--gameDir",root.toString(),"--accessToken","secret"),root,"resolved",s.version(),s.loader(),Path.of("java"),21);
            var applied=profiles.apply(p,s);check(applied.workingDirectory().equals(s.game()),"Working directory follows "+s.label());
            check(applied.installationDirectory().equals(root),"Installation root stays shared "+s.label());
            check(applied.command().get(6).equals(s.game().toString()),"--gameDir follows "+s.label());
            check(applied.command().get(3).equals("/untouched/cache/classpath")&&applied.command().get(8).equals("secret"),"Classpath and auth untouched "+s.label());
            check(Files.isSameFile(s.game().resolve("mods"),s.mods()),"Loader-visible mods folder is the chosen folder "+s.label());
            check(Files.isSameFile(s.game().resolve("saves/My world/level.dat"),root.resolve("saves/My world/level.dat")),"Existing worlds stay shared "+s.label());
        }
        check(Files.readString(current.game().resolve("options.txt")).contains("key_key.jump"),"Existing keybindings copied");
        Files.writeString(current.game().resolve("options.txt"),"key_key.jump:key.keyboard.r\nmaxFps:144\n");profiles.prepareGame(current);check(Files.readString(current.game().resolve("options.txt")).contains("keyboard.r"),"Game settings aren't overwritten on relaunch");
        try(var lease=profiles.lock(current)){rejects(()->profiles.lock(current),"A second launch of the same profile is blocked");try(var different=profiles.lock(other)){check(true,"Different profiles have independent leases");}}
        try(var lease=profiles.lock(current)){check(true,"Lease is reusable after game exit");}
        Path custom=Files.createTempDirectory("custom mods & quote ' ");Files.writeString(custom.resolve("custom.jar"),"custom-content");
        var selected=profiles.choose("26.1.2","Fabric",custom);check(selected.custom()&&Files.isSameFile(selected.mods(),custom),"Custom folder selected");
        var reopened=new ModProfiles(root,"1.21.11","Forge");check(Files.isSameFile(reopened.resolve("26.1.2","Fabric").mods(),custom),"Custom selection persists across restart");
        rejects(()->profiles.choose("1.21.11","Fabric",custom),"Another profile cannot accidentally share a custom folder");
        rejects(()->profiles.choose("26.1.2","Fabric",root),"Game root cannot be its own mods folder");
        rejects(()->profiles.choose("26.1.2","Fabric",other.game()),"Cannot link a managed game directory as mods");
        rejects(()->profiles.choose("26.1.2","Fabric",custom.resolve("custom.jar")),"Files are rejected by folder selection");
        profiles.prepareGame(selected);check(Files.isSameFile(selected.game().resolve("mods"),custom),"Custom mods reach Minecraft's expected location");
        check(Files.isSameFile(current.game().resolve("mods"),current.mods()),"Changing folders never repoints an old game directory");
        check(Files.readString(selected.game().resolve("options.txt")).contains("keyboard.r"),"Keybindings follow a custom folder change");
        // An actual child JVM sees exactly the selected mods through its launch working directory.
        String javaBin=Path.of(System.getProperty("java.home"),"bin","java").toString();String classpath=Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator)).map(p->Path.of(p).toAbsolutePath().toString()).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        Process child=new ProcessBuilder(javaBin,"-cp",classpath,"gg.kapkan.release.ModProfilesTest$GameProbe",selected.game().toString()).directory(selected.game().toFile()).redirectErrorStream(true).start();
        String childOutput=new String(child.getInputStream().readAllBytes());check(child.waitFor()==0&&childOutput.contains("custom-content"),"Actual child JVM discovers custom mods in a path with spaces and quotes: "+childOutput);
        Path moved=custom.resolveSibling(custom.getFileName()+"-offline");Files.move(custom,moved);rejects(()->profiles.resolve("26.1.2","Fabric"),"Missing custom location fails clearly instead of falling back");
        var reset=profiles.choose("26.1.2","Fabric",null);check(!reset.custom()&&reset.mods().equals(current.mods()),"Use default restores the original collection");check(Files.readString(moved.resolve("custom.jar")).equals("custom-content"),"Custom contents are never removed");
        var protectedProfile=profiles.resolve("26.1.1","Fabric");Files.createDirectories(protectedProfile.game().resolve("mods"));Files.writeString(protectedProfile.game().resolve("mods/do-not-delete.txt"),"retained");
        rejects(()->profiles.prepareGame(protectedProfile),"Unowned real directory cannot be replaced");check(Files.readString(protectedProfile.game().resolve("mods/do-not-delete.txt")).equals("retained"),"Collision file remains intact");
        Path linkedSource=Files.createTempDirectory("kapkan-linked-mod-");Files.createSymbolicLink(linkedSource.resolve("linked.jar"),original.resolve("my-mod.jar"));Path copied=Files.createTempDirectory("kapkan-copied-mod-");ModProfiles.copyMissing(linkedSource,copied);check(Files.readString(copied.resolve("linked.jar")).equals("legacy"),"File symlink mods import safely as copied files");
        check(Files.readString(root.resolve("saves/My world/level.dat")).equals("world"),"World bytes unchanged");
        System.out.println("Mod profile tests: PASS ("+checks+" checks)");
    }
    public static final class GameProbe{public static void main(String[] args)throws Exception{if(!Path.of("").toRealPath().equals(Path.of(args[0]).toRealPath()))throw new AssertionError("Wrong working directory");System.out.println(Files.readString(Path.of("mods/custom.jar")));}}
}
