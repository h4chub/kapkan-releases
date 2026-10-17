package gg.kapkan.release;

import gg.kapkan.launcher.launch.*;
import java.nio.file.*;
import java.util.*;

/** Read-only preflight using real, independently downloaded Fabric metadata/dependencies. No game is launched. */
public final class OfficialFabricCacheTest {
    public static void main(String[] args)throws Exception {
        Path cache=Path.of(args[0]).toAbsolutePath();int checks=0;
        for(int i=1;i<args.length;i++) {
            String base=args[i],id="fabric-loader-0.19.5-"+base;
            Path cwd=cache.resolve("unused game profile");Files.createDirectories(cwd);
            var command=new ArrayList<>(List.of("java","-cp","fixture-client-not-minecraft.jar","net.fabricmc.loader.impl.launch.knot.KnotClient"));
            var plan=new LaunchPlan(command,cwd,id,base,"Fabric",Path.of("java"),21,cache);
            FabricLaunchSupport.verify(plan,command,System.out::println);
            Map<String,Object> profile=MiniJson.object(MiniJson.parse(Files.readString(cache.resolve("versions").resolve(id).resolve(id+".json"))));
            for(Object item:MiniJson.array(profile.get("libraries"))) {
                Map<String,Object> lib=MiniJson.object(item);if(!RuleEvaluator.allowed(lib))continue;
                if(!command.get(2).contains(cache.resolve("libraries").resolve(InstallIntegrity.libraryPath(lib)).toString()))throw new AssertionError("Missing real dependency: "+lib.get("name"));
                checks++;
            }
            System.out.println("REAL FABRIC CACHE PREFLIGHT "+base+": PASS. No Minecraft/gameplay test.");
        }
        System.out.println("OFFICIAL FABRIC CACHE TEST: PASS ("+checks+" library entries)");
    }
}
