package gg.kapkan.release;
import gg.kapkan.launcher.launch.LaunchPlan;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.*;
public final class CompanionIntegrationTest {
    static int checks;
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("kapkan-preserve-");
        try{
            if(args.length==0){
                Path resources=dir.resolve("synthetic-resources");Files.createDirectories(resources);
                for(var target:CompanionIntegration.FILES.entrySet())fixture(resources.resolve(target.getValue()),metadata("kapkan-presence","1.0.6","="+target.getKey()),true);
                Path legacy=fixture(resources.resolve("legacy-core.jar"),metadata("kapkan-client-core","0.9","1.21.11"),false);
                Path previous=fixture(resources.resolve("old-presence.jar"),metadata("kapkan-presence","1.0.4","26.1.2"),true);
                args=new String[]{resources.resolve(CompanionIntegration.FILE).toString(),legacy.toString(),previous.toString(),previous.toString(),previous.toString()};
                System.out.println("Using synthetic JAR structure fixtures; these are not executable Minecraft mods.");
            }
            Path game=dir.resolve("game"),mods=game.resolve("mods");Files.createDirectories(mods);
            for(String n:List.of("user-mod.jar","KapKan-ClientCore-user-custom.jar","KapKan-ClientCore-1.21.11-v0.9.jar"))Files.writeString(mods.resolve(n),"USER "+n);
            for(String n:List.of("options.txt","servers.dat","saves/world/level.dat","resourcepacks/user.zip","shaderpacks/user.zip","config/user.json")){Path p=game.resolve(n);Files.createDirectories(p.getParent());Files.writeString(p,"USER DATA "+n);}
            Map<Path,byte[]> before=new HashMap<>();try(var walk=Files.walk(game)){for(Path p:walk.filter(Files::isRegularFile).toList())before.put(p,Files.readAllBytes(p));}
            System.setProperty("kapkan.presenceJar",Path.of(args[0]).toAbsolutePath().toString());
            System.setProperty("kapkan.companionDirectory",Path.of(args[0]).toAbsolutePath().getParent().toString());
            System.setProperty("kapkan.clientCoreJar",Path.of(args[1]).toAbsolutePath().toString());
            LaunchPlan p=plan(game,"26.1.2","Fabric");var first=CompanionIntegration.prepare(p,System.out::println);var second=CompanionIntegration.prepare(p,System.out::println);
            check(first.command().stream().anyMatch(s->s.startsWith("-Dfabric.addMods=")),"Per-process injection");
            check(!first.command().equals(second.command()),"Independent game-session associations");
            check(!CompanionIntegration.supportsLoader("fabric-loader-0.18.4-26.1.2"),"Older loader not forced into incompatible companion");
            check(CompanionIntegration.supportsLoader("fabric-loader-0.19.5-26.1.2"),"Tested loader supported");
            for(var target:CompanionIntegration.FILES.entrySet()){
                var selected=CompanionIntegration.prepare(plan(game,target.getKey(),"Fabric"),System.out::println);
                check(selected.command().stream().anyMatch(s->s.startsWith("-Dfabric.addMods=")&&s.endsWith(target.getValue())),"Exact build selected for "+target.getKey());
                check(selected.command().stream().anyMatch(s->s.startsWith("-Dkapkan.presence.config=")),"Saved presence preferences used for "+target.getKey());
            }
            boolean rejected=false;try{CompanionIntegration.validate(Path.of(args[0]),"26.1");}catch(java.io.IOException expected){rejected=true;}
            check(rejected,"Wrong Minecraft target is rejected before changing user files");
            for(String loader:List.of("Vanilla","Forge","NeoForge"))check(CompanionIntegration.prepare(plan(game,"26.1.2",loader),System.out::println).command().size()==3,"Unsupported loader unchanged");
            check(CompanionIntegration.prepare(plan(game,"26.2","Fabric"),System.out::println).command().size()==3,"Unsupported version unchanged");
            for(var e:before.entrySet())check(Arrays.equals(e.getValue(),Files.readAllBytes(e.getKey())),"Preserved "+e.getKey());
            Files.copy(Path.of(args[1]),mods.resolve("KapKan-ClientCore-1.21.11-Beta1.jar"));CompanionIntegration.prepare(p,System.out::println);
            check(Files.exists(game.resolve(".kapkan-managed/retired/KapKan-ClientCore-1.21.11-Beta1.jar")),"Exact H1 core backed up");
            check(!Files.exists(mods.resolve("KapKan-ClientCore-1.21.11-Beta1.jar")),"Old incompatible known core retired");
            check(Files.readString(mods.resolve("KapKan-ClientCore-1.21.11-v0.9.jar")).startsWith("USER"),"User-modified legacy file untouched");
            if(args.length>2){
                Path previous=mods.resolve("my-renamed-old-badge.jar");Files.copy(Path.of(args[2]),previous);
                Path broken=dir.resolve("broken-replacement.jar");Files.writeString(broken,"incomplete download");
                System.setProperty("kapkan.presenceJar",broken.toString());
                check(CompanionIntegration.prepare(p,System.out::println).command().equals(p.command())&&Files.exists(previous),"Invalid replacement cannot retire the older companion");
                System.setProperty("kapkan.presenceJar",Path.of(args[0]).toAbsolutePath().toString());
                var upgraded=CompanionIntegration.prepare(p,System.out::println);
                check(!Files.exists(previous)&&Files.exists(game.resolve(".kapkan-managed/retired/my-renamed-old-badge.jar")),"Exact older official badge backed up even if renamed");
                check(upgraded.command().stream().anyMatch(s->s.startsWith("-Dfabric.addMods=")),"Fixed companion injected after known-old badge retirement");
                check(Arrays.equals(Files.readAllBytes(Path.of(args[2])),Files.readAllBytes(game.resolve(".kapkan-managed/retired/my-renamed-old-badge.jar"))),"Retired companion backup is byte-identical");
            }
            if(args.length>3){Path old=mods.resolve("previous-build-six.jar");Files.copy(Path.of(args[3]),old);var updated=CompanionIntegration.prepare(p,System.out::println);check(!Files.exists(old)&&Files.exists(game.resolve(".kapkan-managed/retired/previous-build-six.jar")),"Build 6 companion retained as a backup during update");check(updated.command().stream().anyMatch(s->s.startsWith("-Dfabric.addMods=")),"In-game companion injected after Build 6 retirement");}
            if(args.length>4){Path old=mods.resolve("renamed-1.0.4.jar");Files.copy(Path.of(args[4]),old);var updated=CompanionIntegration.prepare(p,System.out::println);check(!Files.exists(old)&&Files.exists(game.resolve(".kapkan-managed/retired/renamed-1.0.4.jar")),"Known 1.0.4 companion backed up during update");check(updated.command().stream().anyMatch(s->s.startsWith("-Dfabric.addMods=")),"1.0.6 injected after 1.0.4 retirement");}
            migrationFixtures(dir.resolve("migration"),Path.of(args[0]),Path.of(args[1]),args.length>4?Path.of(args[4]):null);
            if(args.length>5)real105Migration(dir.resolve("real-1.0.5-upgrade"),Path.of(args[5]),Path.of(args[0]).toAbsolutePath().getParent());
            System.out.println("Companion integration: "+checks+" checks passed (synthetic game-folder fixtures).");
        }finally{try(var walk=Files.walk(dir)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
    static void migrationFixtures(Path root,Path replacement,Path legacy,Path previousPresence)throws Exception{
        System.setProperty("kapkan.presenceJar",replacement.toAbsolutePath().toString());
        Path game=root.resolve("manual-current"),mods=game.resolve("mods");Files.createDirectories(mods);
        Path current=fixture(mods.resolve("manually-installed.jar"),metadata("kapkan-presence","1.0.6","=26.1.2"),true);
        byte[] currentBytes=Files.readAllBytes(current);
        Path malformed=fixture(mods.resolve("malformed-json.jar"),"{\"id\":\"kapkan-presence\",",true);
        Path unrelated=fixture(mods.resolve("user-mod.jar"),"{\"id\":\"user-mod\",\"description\":\"kapkan-presence\"}",false);
        Path broken=mods.resolve("not-a-jar.jar");Files.writeString(broken,"USER incomplete unrelated jar");
        Path oversized=fixture(mods.resolve("oversized-json.jar"),metadata("kapkan-presence","1.0.4","26.1.2")+" ".repeat(65536),true);
        Map<Path,byte[]> preserved=new HashMap<>();for(Path f:List.of(malformed,unrelated,broken,oversized))preserved.put(f,Files.readAllBytes(f));
        for(String name:List.of("options.txt","servers.dat","saves/world/level.dat","config/kapkan-client.json","resourcepacks/user.zip","mods/user-selected.txt")){
            Path file=game.resolve(name);Files.createDirectories(file.getParent());Files.writeString(file,"USER "+name);preserved.put(file,Files.readAllBytes(file));
        }
        var retained=CompanionIntegration.prepare(plan(game,"26.1.2","Fabric"),System.out::println);
        check(!injected(retained),"Matching 1.0.6 manual companion retained without duplicate injection");
        check(Arrays.equals(currentBytes,Files.readAllBytes(current)),"Manual current companion bytes preserved");
        check(retained.command().stream().anyMatch(s->s.startsWith("-Dkapkan.launchSession=")),"Matching manual companion keeps launcher association");
        Path duplicate=mods.resolve("z-duplicate-current.jar");Files.copy(current,duplicate);
        check(!injected(CompanionIntegration.prepare(plan(game,"26.1.2","Fabric"),System.out::println)),"Duplicate manual current builds do not cause additional injection");
        check(Files.exists(current)&&!Files.exists(duplicate)&&Arrays.equals(currentBytes,Files.readAllBytes(game.resolve(".kapkan-managed/retired/z-duplicate-current.jar"))),"Duplicate same-ID current companion safely backed up");
        for(var saved:preserved.entrySet())check(Arrays.equals(saved.getValue(),Files.readAllBytes(saved.getKey())),"Unrelated or malformed user file preserved: "+saved.getKey().getFileName());

        int i=0;
        for(String[] spec:List.of(new String[]{"1.0.4","26.1.2","true"},new String[]{"1.0.6","26.1","true"},new String[]{"1.0.6",">=26.1","true"},new String[]{"1.0.6","26.1.2","false"})){
            Path target=root.resolve("replace-"+(i++)),old=target.resolve("mods/my-renamed-companion.jar");
            fixture(old,metadata("kapkan-presence",spec[0],spec[1]),Boolean.parseBoolean(spec[2]));byte[] before=Files.readAllBytes(old);
            var upgraded=CompanionIntegration.prepare(plan(target,"26.1.2","Fabric"),System.out::println);
            check(injected(upgraded),"Old, wrong-target, ranged or incomplete same-ID companion replaced");
            check(!Files.exists(old)&&Arrays.equals(before,Files.readAllBytes(target.resolve(".kapkan-managed/retired/my-renamed-companion.jar"))),"Replaced same-ID companion retained byte for byte");
        }

        Path collision=root.resolve("backup-collision"),old=fixture(collision.resolve("mods/old.jar"),metadata("kapkan-presence","1.0.4","26.1.2"),true);
        byte[] original=Files.readAllBytes(old);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original));
        Path retired=collision.resolve(".kapkan-managed/retired");Files.createDirectories(retired);
        Files.writeString(retired.resolve("old.jar"),"EARLIER BACKUP");Files.writeString(retired.resolve("old.jar."+hash+".jar"),"OTHER EXISTING FILE");
        check(injected(CompanionIntegration.prepare(plan(collision,"26.1.2","Fabric"),System.out::println)),"Colliding backup names do not retain outdated active companion");
        check(!Files.exists(old)&&Arrays.equals(original,Files.readAllBytes(retired.resolve("old.jar."+hash+".2.jar"))),"Collision backup uses the full SHA256 with a unique suffix");
        check(Files.readString(retired.resolve("old.jar")).equals("EARLIER BACKUP")&&Files.readString(retired.resolve("old.jar."+hash+".jar")).equals("OTHER EXISTING FILE"),"Every existing backup survives unchanged");
        Files.write(old,original);
        check(injected(CompanionIntegration.prepare(plan(collision,"26.1.2","Fabric"),System.out::println))&&!Files.exists(old),"Existing identical backup safely removes the duplicate active outdated copy");
        check(Arrays.equals(original,Files.readAllBytes(retired.resolve("old.jar."+hash+".2.jar"))),"Identical backup bytes remain intact on repeated upgrade");

        Path legacyGame=root.resolve("legacy-collision"),legacyOld=legacyGame.resolve("mods/KapKan-ClientCore-1.21.11-Beta1.jar");Files.createDirectories(legacyOld.getParent());Files.copy(legacy,legacyOld);
        byte[] legacyBytes=Files.readAllBytes(legacy);String legacyHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(legacyBytes));
        Path legacyBackup=legacyGame.resolve(".kapkan-managed/retired");Files.createDirectories(legacyBackup);Files.writeString(legacyBackup.resolve(legacyOld.getFileName()),"OLDER DIFFERENT BACKUP");
        check(injected(CompanionIntegration.prepare(plan(legacyGame,"26.1.2","Fabric"),System.out::println))&&!Files.exists(legacyOld),"Known legacy hash migration survives backup name collision");
        check(Arrays.equals(legacyBytes,Files.readAllBytes(legacyBackup.resolve(legacyOld.getFileName()+"."+legacyHash+".jar"))),"Legacy hash backup preserves exact bytes with full SHA256");

        if(previousPresence!=null){
            Path knownGame=root.resolve("known-presence-collision"),knownOld=knownGame.resolve("mods/renamed-known-presence.jar");Files.createDirectories(knownOld.getParent());Files.copy(previousPresence,knownOld);
            byte[] knownBytes=Files.readAllBytes(previousPresence);String knownHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(knownBytes));
            Path knownBackups=knownGame.resolve(".kapkan-managed/retired");Files.createDirectories(knownBackups);Files.writeString(knownBackups.resolve(knownOld.getFileName()),"UNRELATED EXISTING BACKUP");
            check(injected(CompanionIntegration.prepare(plan(knownGame,"26.1.2","Fabric"),System.out::println))&&!Files.exists(knownOld),"Known official presence hash cannot remain active after backup collision");
            check(Arrays.equals(knownBytes,Files.readAllBytes(knownBackups.resolve(knownOld.getFileName()+"."+knownHash+".jar"))),"Known presence hash collision backup keeps exact bytes");
            check(Files.readString(knownBackups.resolve(knownOld.getFileName())).equals("UNRELATED EXISTING BACKUP"),"Known presence migration does not overwrite earlier backup");
        }

        Path invalidGame=root.resolve("invalid-replacement"),invalidOld=fixture(invalidGame.resolve("mods/old.jar"),metadata("kapkan-presence","1.0.4","26.1.2"),true);
        byte[] invalidBefore=Files.readAllBytes(invalidOld);
        Path wrongReplacement=fixture(root.resolve("wrong-replacement.jar"),metadata("kapkan-presence","1.0.6","=26.1"),true);
        System.setProperty("kapkan.presenceJar",wrongReplacement.toString());var unchanged=plan(invalidGame,"26.1.2","Fabric");
        check(CompanionIntegration.prepare(unchanged,System.out::println).command().equals(unchanged.command()),"Wrong-target replacement cannot change launch arguments");
        check(Arrays.equals(invalidBefore,Files.readAllBytes(invalidOld))&&!Files.exists(invalidGame.resolve(".kapkan-managed")),"Wrong-target replacement cannot move existing companion");
        System.setProperty("kapkan.presenceJar",replacement.toAbsolutePath().toString());

        Path customGame=root.resolve("custom-folder-game"),customMods=root.resolve("custom-mods-folder");Files.createDirectories(customMods);Files.createDirectories(customGame);
        Path customLink=customGame.resolve("mods");Files.createSymbolicLink(customLink,customMods.toAbsolutePath());
        Path customOld=fixture(customMods.resolve("manual-old.jar"),metadata("kapkan-presence","1.0.4","26.1.2"),true);byte[] customOldBytes=Files.readAllBytes(customOld);
        Path customLegacy=customMods.resolve("KapKan-ClientCore-1.21.11-Beta1.jar");Files.copy(legacy,customLegacy);
        Path customUser=fixture(customMods.resolve("unrelated-user.jar"),metadata("user-mod","2.0","26.1.2"),false);byte[] customUserBytes=Files.readAllBytes(customUser);
        Path customNotes=customMods.resolve("nested/user-notes.txt");Files.createDirectories(customNotes.getParent());Files.writeString(customNotes,"USER CUSTOM FOLDER DATA");
        Path customKnown=null;byte[] customKnownBytes=null;
        if(previousPresence!=null){customKnown=customMods.resolve("renamed-known-presence.jar");Files.copy(previousPresence,customKnown);customKnownBytes=Files.readAllBytes(customKnown);}
        check(injected(CompanionIntegration.prepare(plan(customGame,"26.1.2","Fabric"),System.out::println)),"Custom mods directory link permits migration and injects the matching new companion");
        check(Files.isSymbolicLink(customLink)&&customLink.toRealPath().equals(customMods.toRealPath()),"Custom mods directory link and selected target remain intact");
        Path customRetired=customGame.resolve(".kapkan-managed/retired");
        check(!Files.exists(customOld)&&Arrays.equals(customOldBytes,Files.readAllBytes(customRetired.resolve(customOld.getFileName()))),"Older manually identified KapKan in custom directory backed up to game directory");
        check(!Files.exists(customLegacy)&&Arrays.equals(legacyBytes,Files.readAllBytes(customRetired.resolve(customLegacy.getFileName()))),"Known legacy hash in custom directory backed up safely");
        if(customKnown!=null)check(!Files.exists(customKnown)&&Arrays.equals(customKnownBytes,Files.readAllBytes(customRetired.resolve(customKnown.getFileName()))),"Known presence hash in custom directory backed up safely");
        check(Arrays.equals(customUserBytes,Files.readAllBytes(customUser))&&Files.readString(customNotes).equals("USER CUSTOM FOLDER DATA"),"Unrelated custom mods and nested data remain unchanged");
        Path customCurrent=fixture(customMods.resolve("manual-current.jar"),metadata("kapkan-presence","1.0.6","=26.1.2"),true);byte[] customCurrentBytes=Files.readAllBytes(customCurrent);
        check(!injected(CompanionIntegration.prepare(plan(customGame,"26.1.2","Fabric"),System.out::println)),"Matching manual current companion in custom directory retained without duplicate injection");
        check(Arrays.equals(customCurrentBytes,Files.readAllBytes(customCurrent))&&Files.isSymbolicLink(customLink),"Custom current companion and directory link stay unchanged");

        Path linkGame=root.resolve("symlinks"),linkMods=linkGame.resolve("mods");Files.createDirectories(linkMods);
        Path outside=fixture(root.resolve("outside-old.jar"),metadata("kapkan-presence","1.0.4","26.1.2"),true);byte[] outsideBytes=Files.readAllBytes(outside);
        Path symlink=linkMods.resolve("linked-companion.jar");Files.createSymbolicLink(symlink,outside.toAbsolutePath());
        Path legacyLink=linkMods.resolve("KapKan-ClientCore-1.21.11-Beta1.jar");Files.createSymbolicLink(legacyLink,legacy.toAbsolutePath());
        check(injected(CompanionIntegration.prepare(plan(linkGame,"26.1.2","Fabric"),System.out::println)),"Symlinked files are not followed during migration");
        check(Files.isSymbolicLink(symlink)&&Files.isSymbolicLink(legacyLink)&&Arrays.equals(outsideBytes,Files.readAllBytes(outside)),"Symlink and external target remain unchanged");
        Path linkedBackupGame=root.resolve("linked-backup"),linkedBackupOld=fixture(linkedBackupGame.resolve("mods/old.jar"),metadata("kapkan-presence","1.0.4","26.1.2"),true);
        Path outsideFolder=root.resolve("outside-folder");Files.createDirectories(outsideFolder);Files.createDirectories(linkedBackupGame.resolve(".kapkan-managed"));
        Files.createSymbolicLink(linkedBackupGame.resolve(".kapkan-managed/retired"),outsideFolder.toAbsolutePath());
        var blocked=plan(linkedBackupGame,"26.1.2","Fabric");
        check(CompanionIntegration.prepare(blocked,System.out::println).command().equals(blocked.command())&&Files.exists(linkedBackupOld),"Symlinked backup directory prevents migration without changing active files");
        try(var files=Files.list(outsideFolder)){check(files.findAny().isEmpty(),"Symlinked backup directory receives no writes");}
    }
    /** Run the actual published 1.0.5 JAR bytes through the current launcher migration. */
    static void real105Migration(Path root,Path previousDirectory,Path currentDirectory)throws Exception{
        Map<String,String> hashes=Map.of(
            "26.1","38a34fbece91c1cb197a842f8543224cf4f2c736181431060ee73e60f7c5cdb8",
            "26.1.1","2666aa3a53b7063ec7fd2673893da1ef61018b1838ad6a8e4e3729409aac2b67",
            "26.1.2","4671b4ea573f7e4742ff03e30eaed562d6df3d828f4f1c33463682b987cdd2c9");
        for(String version:List.of("26.1","26.1.1","26.1.2")){
            Path previous=previousDirectory.resolve("KapKan-1.0.5-mc"+version+"-fabric.jar");byte[] original=Files.readAllBytes(previous);
            check(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(original)).equals(hashes.get(version)),"Real published 1.0.5 artifact hash: "+version);
            Path replacement=currentDirectory.resolve(CompanionIntegration.FILES.get(version));CompanionIntegration.validate(replacement,version);
            Path game=root.resolve(version),mods=game.resolve("mods");Files.createDirectories(mods);
            Path active=mods.resolve("user-renamed-kapkan.jar");Files.write(active,original);
            Path options=game.resolve("options.txt"),world=game.resolve("saves/world/level.dat");Files.writeString(options,"USER controls and graphics");Files.createDirectories(world.getParent());Files.writeString(world,"USER world");
            var upgraded=CompanionIntegration.prepare(plan(game,version,"Fabric"),System.out::println);
            check(upgraded.command().stream().anyMatch(arg->arg.equals("-Dfabric.addMods="+replacement.toAbsolutePath())),"Real 1.0.5 replaced by exact 1.0.6 target: "+version);
            Path backup=game.resolve(".kapkan-managed/retired/user-renamed-kapkan.jar");
            check(!Files.exists(active)&&Arrays.equals(original,Files.readAllBytes(backup)),"Real 1.0.5 retired byte for byte: "+version);
            check(Files.readString(options).equals("USER controls and graphics")&&Files.readString(world).equals("USER world"),"Real upgrade preserves controls/graphics/world: "+version);
            byte[] current=Files.readAllBytes(replacement);Path manual=mods.resolve("current-manually-installed.jar");Files.write(manual,current);
            check(!injected(CompanionIntegration.prepare(plan(game,version,"Fabric"),System.out::println)),"Real current manual companion is not duplicated: "+version);
            check(Arrays.equals(current,Files.readAllBytes(manual))&&Arrays.equals(original,Files.readAllBytes(backup)),"Real current companion and old backup remain unchanged: "+version);
            check(Arrays.equals(original,Files.readAllBytes(previous)),"Recovered 1.0.5 reference remains immutable: "+version);
            System.out.println("REAL 1.0.5 -> 1.0.6 MIGRATION: PASS Minecraft "+version+" (temporary game folder, original JAR bytes; no gameplay execution)");
        }
    }
    static boolean injected(LaunchPlan plan){return plan.command().stream().anyMatch(s->s.startsWith("-Dfabric.addMods="));}
    static String metadata(String id,String version,String target){return "{\"schemaVersion\":1,\"id\":\""+id+"\",\"version\":\""+version+"\",\"depends\":{\"minecraft\":\""+target+"\"}}";}
    static Path fixture(Path path,String metadata,boolean classes)throws Exception{
        Files.createDirectories(path.getParent());
        try(var jar=new JarOutputStream(Files.newOutputStream(path))){
            jar.putNextEntry(new JarEntry("fabric.mod.json"));jar.write(metadata.getBytes(StandardCharsets.UTF_8));jar.closeEntry();
            if(classes)for(String name:List.of("gg/kapkan/presence/BadgeClient.class","gg/kapkan/presence/cosmetics/ClientFeatures.class")){jar.putNextEntry(new JarEntry(name));jar.write(new byte[]{0});jar.closeEntry();}
        }return path;
    }
    static LaunchPlan plan(Path game,String version,String loader){return new LaunchPlan(List.of("java","-Xmx1G","main"),game,"fabric-loader-0.19.5-"+version,version,loader,Path.of("java"),25);}
}
