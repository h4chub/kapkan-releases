package gg.kapkan.release;

import gg.kapkan.launcher.launch.LaunchPlan;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Version/loader mod collections. The original game directory is never moved. */
public final class ModProfiles {
    private final Path root, profiles, settings;
    private final String initialKey;
    private static final LinkOption[] NOFOLLOW={LinkOption.NOFOLLOW_LINKS};
    public record Selection(String key,String version,String loader,Path profile,Path mods,boolean custom) {
        // A new custom location gets its own stable launch directory. An already
        // running process never has its mods link repointed by a later launch.
        public Path game(){return profile.resolve("game-"+fingerprint(mods.toString()).substring(0,12));}
        public String label(){return version+" · "+loader;}
    }
    public ModProfiles(Path gameRoot,String initialVersion,String initialLoader) {
        root=gameRoot.toAbsolutePath().normalize(); profiles=root.resolve(".kapkan-profiles");
        settings=profiles.resolve("folders.properties"); initialKey=key(initialVersion,initialLoader);
    }
    public static String key(String version,String loader) {
        if(version==null||version.isBlank()||version.length()>180)throw new IllegalArgumentException("Choose a Minecraft version first.");
        if(loader==null||!Set.of("vanilla","fabric","quilt","forge","neoforge","optifine").contains(loader.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Choose a supported loader first.");
        try {
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((version+"\0"+loader.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8))).substring(0,12);
            String readable=version.replaceAll("[^A-Za-z0-9._-]","_");if(readable.length()>65)readable=readable.substring(0,65);
            return "mc-"+readable+"-"+loader.toLowerCase(Locale.ROOT)+"-"+hash;
        }catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}
    }
    private static String fingerprint(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new AssertionError(e);}}
    private void roots()throws IOException {realDirectory(root);realDirectory(profiles);}
    private static void realDirectory(Path dir)throws IOException {
        if(Files.exists(dir,NOFOLLOW)) {
            var a=Files.readAttributes(dir,BasicFileAttributes.class,NOFOLLOW);
            if(!a.isDirectory()||a.isSymbolicLink()||a.isOther())throw new IOException("A profile folder was replaced by a link or file: "+dir);
        }else Files.createDirectories(dir);
    }
    private Properties read()throws IOException {
        Properties p=new Properties();if(Files.exists(settings))try(var in=Files.newInputStream(settings)){p.load(in);}
        return p;
    }
    private static void save(Properties p,Path file)throws IOException {
        Path temp=Files.createTempFile(file.getParent(),"kapkan-settings-",".tmp");
        try {try(var out=Files.newOutputStream(temp)){p.store(out,"KapKan mod profiles");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    private Selection selection(Properties p,String version,String loader) {
        String key=key(version,loader),custom=p.getProperty("folder."+key,"");Path profile=profiles.resolve(key);
        return new Selection(key,version,loader,profile,custom.isBlank()?profile.resolve("mods"):Path.of(custom).toAbsolutePath().normalize(),!custom.isBlank());
    }
    public synchronized Selection resolve(String version,String loader)throws IOException {
        roots();
        try(var channel=FileChannel.open(profiles.resolve("settings.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            Properties p=read();if(!p.containsKey("legacy.owner")){p.setProperty("legacy.owner",initialKey);save(p,settings);}
            Selection s=selection(p,version,loader);realDirectory(s.profile());
            if(s.custom())validateCustom(s.mods(),s.key(),p);
            else {
                realDirectory(s.mods());
                if(s.key().equals(p.getProperty("legacy.owner"))&&!Boolean.parseBoolean(p.getProperty("legacy.copied","false"))) {
                    copyMissing(root.resolve("mods"),s.mods());p.setProperty("legacy.copied","true");save(p,settings);
                }
            }
            return s;
        }
    }
    public synchronized Selection choose(String version,String loader,Path folder)throws IOException {
        roots();
        try(var channel=FileChannel.open(profiles.resolve("settings.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            Properties p=read();p.putIfAbsent("legacy.owner",initialKey);String key=key(version,loader);
            if(folder==null)p.remove("folder."+key);
            else {Path canonical=folder.toRealPath();validateCustom(canonical,key,p);p.setProperty("folder."+key,canonical.toString());}
            save(p,settings);
        }
        return resolve(version,loader);
    }
    private void validateCustom(Path folder,String ownKey,Properties p)throws IOException {
        if(!Files.isDirectory(folder)||!Files.isReadable(folder))throw new IOException("The custom mods folder is unavailable. Reconnect it or choose another folder.");
        Path actual=folder.toRealPath();Path actualRoot=root.toRealPath();
        if(actualRoot.startsWith(actual)||actual.startsWith(profiles.toRealPath()))throw new IOException("Choose a separate mods folder outside KapKan's managed profiles.");
        for(String name:p.stringPropertyNames())if(name.startsWith("folder.")&&!name.equals("folder."+ownKey)) {
            Path other=Path.of(p.getProperty(name));
            if(Files.exists(other)&&Files.isSameFile(actual,other))throw new IOException("That folder belongs to another version or loader. Choose a separate folder.");
        }
    }
    /** Copies once, without overwriting or removing any existing file. Links are not followed. */
    static void copyMissing(Path from,Path to)throws IOException {
        if(!Files.isDirectory(from))return;
        from=from.toRealPath();
        try(var paths=Files.walk(from)) {
            for(Path source:paths.toList()) {
                if(Files.isSymbolicLink(source)&&Files.isDirectory(source))throw new IOException("Existing mods contain a linked folder. Choose the original mods folder as a custom folder to keep using it.");
                Path target=to.resolve(from.relativize(source));
                if(Files.isDirectory(source,NOFOLLOW))realDirectory(target);
                else if(Files.isRegularFile(source)) {
                    if(Files.exists(target,NOFOLLOW))continue;
                    Files.createDirectories(target.getParent());
                    Path temp=Files.createTempFile(target.getParent(),"kapkan-copy-",".tmp");
                    try{Files.copy(source,temp,StandardCopyOption.REPLACE_EXISTING);Files.move(temp,target);}
                    catch(FileAlreadyExistsException ignored){}finally{Files.deleteIfExists(temp);}
                }
            }
        }
    }
    public Path prepareGame(Selection s)throws IOException {
        realDirectory(s.profile());realDirectory(s.game());
        Properties links=new Properties();Path ledger=s.game().resolve(".kapkan-links.properties");
        if(Files.exists(ledger))try(var in=Files.newInputStream(ledger)){links.load(in);}
        link(s.game().resolve("mods"),s.mods(),links,ledger);
        // Keep worlds, packs, cosmetics and screenshots at their original locations.
        for(String name:List.of("saves","resourcepacks","shaderpacks","screenshots","config")) {
            Path shared=root.resolve(name);Files.createDirectories(shared);link(s.game().resolve(name),shared,links,ledger);
        }
        Path previous=root,last=s.profile().resolve("last-game.txt");
        if(Files.isRegularFile(last)){Path candidate=s.profile().resolve(Files.readString(last).trim()).normalize();if(candidate.getParent().equals(s.profile())&&candidate.getFileName().toString().startsWith("game-")&&Files.isDirectory(candidate,NOFOLLOW))previous=candidate;}
        for(String name:List.of("options.txt","servers.dat","servers.dat_old","hotbar.nbt")) {
            Path source=previous.resolve(name),target=s.game().resolve(name);if(!Files.isRegularFile(source))source=root.resolve(name);
            if(Files.isRegularFile(source)&&!Files.exists(target,NOFOLLOW))Files.copy(source,target);
        }
        Files.writeString(last,s.game().getFileName().toString());
        return s.game();
    }
    private static void link(Path path,Path target,Properties ledger,Path ledgerFile)throws IOException {
        if(!Files.isDirectory(target))throw new IOException("Mods folder is unavailable: "+target);
        String name=path.getFileName().toString(),previous=ledger.getProperty(name);Path canonical=target.toRealPath();
        if(Files.exists(path,NOFOLLOW)) {
            var a=Files.readAttributes(path,BasicFileAttributes.class,NOFOLLOW);
            if(!(a.isSymbolicLink()||a.isOther())||previous==null)throw new IOException("An existing folder is protected: "+path);
            if(Files.exists(path)&&Files.isSameFile(path,canonical)){ledger.setProperty(name,canonical.toString());save(ledger,ledgerFile);return;}
            Path old=Path.of(previous);
            boolean owned=Files.isSymbolicLink(path)?path.getParent().resolve(Files.readSymbolicLink(path)).normalize().equals(old):Files.exists(old)&&Files.isSameFile(path,old);
            if(!owned)throw new IOException("A managed folder link changed outside KapKan: "+path);
            Files.delete(path); // Deletes only the verified link, never its target or contents.
        }
        if(System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win"))windowsJunction(path,canonical);
        else Files.createSymbolicLink(path,canonical);
        if(!Files.isSameFile(path,canonical))throw new IOException("Couldn't verify the profile folder link.");
        ledger.setProperty(name,canonical.toString());save(ledger,ledgerFile);
    }
    static void windowsJunction(Path path,Path target)throws IOException {
        String systemRoot=System.getenv("SystemRoot");
        if(systemRoot==null)throw new IOException("Windows system folder is unavailable.");
        Path shell=Path.of(systemRoot,"System32","WindowsPowerShell","v1.0","powershell.exe");
        ProcessBuilder b=new ProcessBuilder(shell.toString(),"-NoProfile","-NonInteractive","-Command",
            "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path $env:KAPKAN_LINK_PATH -Value $env:KAPKAN_LINK_TARGET | Out-Null");
        b.environment().put("KAPKAN_LINK_PATH",path.toString());b.environment().put("KAPKAN_LINK_TARGET",target.toString());
        b.redirectErrorStream(true);b.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        try{Process process=b.start();if(!process.waitFor(20,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Creating the profile folder link timed out.");}
            if(process.exitValue()!=0)throw new IOException("Windows couldn't create the profile folder link. Check folder permissions.");
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Folder setup was interrupted.",e);}
    }
    public LaunchPlan apply(LaunchPlan p,Selection s)throws IOException {
        Path game=prepareGame(s);var command=new ArrayList<>(p.command());
        for(int i=1;i<command.size();i++) {
            if(command.get(i).equals("--gameDir")){if(i+1>=command.size())throw new IOException("Invalid Minecraft game directory argument.");command.set(++i,game.toString());}
            else if(command.get(i).startsWith("--gameDir="))command.set(i,"--gameDir="+game);
        }
        return new LaunchPlan(List.copyOf(command),game,p.launchVersion(),p.baseVersion(),p.loaderLabel(),p.javaExecutable(),p.javaMajor(),p.installationDirectory());
    }
    public Lease lock(Selection s)throws IOException {
        realDirectory(s.profile());FileChannel channel=FileChannel.open(s.profile().resolve("game.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        try{FileLock lock=channel.tryLock();if(lock==null)throw new IOException("Minecraft is already using this version and loader.");return new Lease(channel,lock);}
        catch(OverlappingFileLockException e){channel.close();throw new IOException("Minecraft is already using this version and loader.",e);}
        catch(IOException|RuntimeException e){channel.close();throw e;}
    }
    public static final class Lease implements AutoCloseable {
        private final FileChannel channel;private final FileLock lock;
        Lease(FileChannel channel,FileLock lock){this.channel=channel;this.lock=lock;}
        public synchronized void close(){try{if(lock.isValid())lock.release();channel.close();}catch(IOException ignored){}}
    }
}
