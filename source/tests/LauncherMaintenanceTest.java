package gg.kapkan.release;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class LauncherMaintenanceTest {
    static int checks;
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    static Path app(Path path,int build)throws Exception{
        Files.createDirectories(path.resolve("Contents/MacOS"));Files.createDirectories(path.resolve("Contents/Resources"));
        Files.writeString(path.resolve("Contents/Info.plist"),"<?xml version=\"1.0\"?><!DOCTYPE plist PUBLIC \"-//Apple//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\"><plist><dict><key>CFBundleIdentifier</key><string>gg.kapkan.launcher</string><key>CFBundleShortVersionString</key><string>1.0.0</string><key>CFBundleVersion</key><string>"+build+"</string></dict></plist>");
        Files.writeString(path.resolve("Contents/MacOS/KapKan"),"fixture");Files.writeString(path.resolve("Contents/Resources/KapKan-Launcher.jar"),"fixture");return path;
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0){
            for(String name:args){Path candidate=Path.of(name);System.out.println(candidate.getFileName()+" eligible="+LauncherMaintenance.oldPackage(candidate));}
            return; // Inspection only. Never move supplied paths.
        }
        Path root=Files.createTempDirectory("kapkan-cleanup-test-"),downloads=root.resolve("Downloads"),trash=root.resolve("Trash");Files.createDirectories(downloads);Files.createDirectories(trash);
        try{
            Path old=app(downloads.resolve("KapKan old.app"),10005),current=app(downloads.resolve("KapKan.app"),LauncherMaintenance.BUILD_NUMBER),future=app(downloads.resolve("KapKan next.app"),LauncherMaintenance.BUILD_NUMBER+1);
            Path folder=downloads.resolve("KapKan-macOS-1.0.0-H4");app(folder.resolve("KapKan.app"),10004);Files.writeString(folder.resolve("READ-ME.md"),"installer instructions");
            Path zip=downloads.resolve("KapKan-macOS-1.0.0-H3 (1).zip");
            try(var out=new ZipOutputStream(Files.newOutputStream(zip));var walk=Files.walk(folder)){
                for(Path f:walk.filter(Files::isRegularFile).toList()){out.putNextEntry(new ZipEntry(folder.relativize(f).toString()));out.write(Files.readAllBytes(f));out.closeEntry();}
            }
            check(LauncherMaintenance.oldPackage(old),"Old app identified from bundle metadata");check(LauncherMaintenance.oldPackage(folder),"Clean extracted distribution identified");check(LauncherMaintenance.oldPackage(zip),"Old duplicate ZIP verified by its contents");
            check(!LauncherMaintenance.oldPackage(current)&&!LauncherMaintenance.oldPackage(future),"Current and future builds retained");
            Path game=root.resolve(".kapkan/game");Files.createDirectories(game.resolve("mods"));Files.writeString(game.resolve("mods/user.jar"),"USER MOD");Files.createDirectories(game.resolve("saves/world"));Files.writeString(game.resolve("saves/world/level.dat"),"WORLD");
            Path withData=downloads.resolve("KapKan-custom");app(withData.resolve("KapKan.app"),10003);Files.createDirectories(withData.resolve("mods"));Files.writeString(withData.resolve("mods/my.jar"),"CUSTOM MOD");
            check(!LauncherMaintenance.oldPackage(withData),"Unzipped folder with user data retained");
            Path withEmptyData=downloads.resolve("KapKan-empty-mods");app(withEmptyData.resolve("KapKan.app"),10003);Files.createDirectories(withEmptyData.resolve("mods"));check(!LauncherMaintenance.oldPackage(withEmptyData),"Even unknown empty directories prevent cleanup");
            Path link=downloads.resolve("KapKan link.app");Files.createSymbolicLink(link,old);check(!LauncherMaintenance.oldPackage(link),"Symlink app retained");
            Path appLink=app(downloads.resolve("KapKan symbolic.app"),10003);Files.delete(appLink.resolve("Contents/Resources/KapKan-Launcher.jar"));Files.createSymbolicLink(appLink.resolve("Contents/Resources/KapKan-Launcher.jar"),game.resolve("mods/user.jar"));check(!LauncherMaintenance.oldPackage(appLink),"App with symlink resource retained");
            Path foreign=app(downloads.resolve("KapKan foreign.app"),10001);Path plist=foreign.resolve("Contents/Info.plist");Files.writeString(plist,Files.readString(plist).replace("gg.kapkan.launcher","example.other"));check(!LauncherMaintenance.oldPackage(foreign),"Name alone cannot identify a KapKan app");
            Path modZip=downloads.resolve("KapKan-my-mods.zip");try(var out=new ZipOutputStream(Files.newOutputStream(modZip))){out.putNextEntry(new ZipEntry("mods/a.jar"));out.write(1);out.closeEntry();}check(!LauncherMaintenance.oldPackage(modZip),"Mod archives retained despite KapKan filename");
            List<Path> protect=List.of(current,game,root.resolve(".kapkan"));var candidates=LauncherMaintenance.scan(List.of(downloads),protect);
            check(candidates.size()==3&&candidates.containsAll(List.of(old,folder,zip)),"Only three verified old distributions selected");
            check(LauncherMaintenance.scan(List.of(downloads),List.of(current,folder.resolve("custom-game"),game)).stream().noneMatch(folder::equals),"An ancestor of a configured game folder is protected");
            // Exercise macOS /var -> /private/var-style aliases on every filesystem.
            // The game folder has not been created, so resolving only existing paths is insufficient.
            Path protectedAlias=root.resolve("protected-downloads-alias");
            try{
                Files.createSymbolicLink(protectedAlias,downloads.toAbsolutePath());
                Path futureGame=protectedAlias.resolve(folder.getFileName()).resolve("future-game/deep-profile");
                check(!Files.exists(futureGame),"Aliased configured game folder is deliberately absent");
                check(LauncherMaintenance.scan(List.of(downloads),List.of(current,game,futureGame)).stream().noneMatch(folder::equals),"Existing symlink ancestor protects a nonexistent nested game folder");
            }catch(UnsupportedOperationException unsupported){System.out.println("SKIP symlink-ancestor fixture: symbolic links unsupported");}
            catch(FileSystemException unavailable){
                String reason=Objects.toString(unavailable.getReason(),"").toLowerCase(Locale.ROOT);
                if(reason.contains("not supported")||reason.contains("not implemented"))System.out.println("SKIP symlink-ancestor fixture: "+reason);
                else throw unavailable;
            }
            var busy=LauncherMaintenance.retire(List.of(old),protect,p->{throw new AssertionError("Busy copy must not reach Trash");},p->true);check(busy.kept()==1&&Files.exists(old),"Running old app retained");
            Path changed=app(downloads.resolve("KapKan changed.app"),10002);var snapshot=List.of(changed);Files.writeString(changed.resolve("world.txt"),"NEW DATA");
            check(LauncherMaintenance.retire(snapshot,protect,p->{throw new AssertionError("Changed copy must not reach Trash");},p->false).kept()==1,"Candidate rechecked after scan");
            var result=LauncherMaintenance.retire(candidates,protect,p->{try{Files.move(p,trash.resolve(p.getFileName()));return true;}catch(Exception e){return false;}},p->false);
            check(result.moved()==3&&result.kept()==0,"Verified old packages moved through Trash callback");
            check(Files.isDirectory(current)&&Files.isDirectory(future),"New apps survive cleanup");
            check(Files.readString(game.resolve("mods/user.jar")).equals("USER MOD")&&Files.readString(game.resolve("saves/world/level.dat")).equals("WORLD"),"Mods and worlds unchanged");
            check(Files.exists(withData.resolve("mods/my.jar"))&&Files.exists(modZip)&&Files.exists(changed.resolve("world.txt")),"User additions and archives unchanged");
            System.setProperty("user.home",root.toString());BadgeSettings settings=new BadgeSettings();check(settings.enabled()&&settings.own(),"Fresh own-badge default enabled");settings.save(true,false,"");check(!new BadgeSettings().own(),"Explicit self-badge opt-out preserved");
            System.out.println("Maintenance and defaults: "+checks+" checks passed with temporary files and simulated Trash; no personal files used.");
        }finally{try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
    }
}
