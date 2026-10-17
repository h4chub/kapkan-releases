package gg.kapkan.release;

import gg.kapkan.launcher.importer.LocalVersionImporter;
import gg.kapkan.launcher.install.BaseGameInstallerService;
import gg.kapkan.launcher.launch.*;
import java.io.IOException;
import java.nio.file.*;

/** The existing official installer is the fallback when no local Minecraft installation is available. */
public final class FreshInstallSupport {
    private FreshInstallSupport() {}
    private static Path marker(MinecraftInstallation installation,String id){
        return installation.gameDir().resolve(".kapkan-setup").resolve("base-"+id+".pending");
    }
    public static boolean isPrepared(MinecraftInstallation installation,String id){
        try{validateBaseId(id);return installation!=null&&InstallIntegrity.baseComplete(installation,id)&&!Files.exists(marker(installation,id));}
        catch(LaunchException e){return false;}
    }
    public static boolean ensureFullyImported(MinecraftInstallation installation,String baseVersion)throws LaunchException{
        return ensureFullyImported(installation,baseVersion,System.out::println);
    }
    public static boolean ensureFullyImported(MinecraftInstallation installation,String baseVersion,java.util.function.Consumer<String> report)throws LaunchException{
        validateBaseId(baseVersion);
        if(installation==null)throw new LaunchException("KapKan's game folder is unavailable");
        if(!Files.exists(marker(installation,baseVersion))) {
            if(isPrepared(installation,baseVersion))return true;
            if(LocalVersionImporter.ensureFullyImported(installation,baseVersion)&&InstallIntegrity.baseComplete(installation,baseVersion))return true;
        }
        return install(installation,baseVersion,new BaseGameInstallerService(),report);
    }
    // Package-visible service injection is for local fixture tests only. Production uses the default official manifest.
    static boolean install(MinecraftInstallation installation,String id,BaseGameInstallerService service)throws LaunchException{
        return install(installation,id,service,System.out::println);
    }
    static boolean install(MinecraftInstallation installation,String id,BaseGameInstallerService service,java.util.function.Consumer<String> report)throws LaunchException{
        validateBaseId(id);
        Path pending=marker(installation,id);
        try{
            Files.createDirectories(pending.getParent());
            Files.writeString(pending,"This selected base-game download has not finished. Retry Play to resume it.\n");
            report.accept("KapKan: preparing official Minecraft files for "+id);
            service.ensureInstalled(installation,id,report);
            if(!InstallIntegrity.baseComplete(installation,id))throw new LaunchException("Minecraft files are still incomplete: "+id);
            Files.deleteIfExists(pending);
            return true;
        }catch(IOException e){throw new LaunchException("Cannot update Minecraft setup state: "+e.getMessage(),e);}
    }
    public static void validateBaseId(String id)throws LaunchException{
        if(id==null||!id.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,119}")||id.contains("..")||id.startsWith("fabric-loader-"))
            throw new LaunchException("Expected a base Minecraft version, not a path or Fabric launch profile: "+id);
    }
}
