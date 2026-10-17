import gg.kapkan.launcher.launch.*;
import gg.kapkan.release.FreshInstallSupport;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;

public class ReproduceBaseline {
 public static void main(String[] args) throws Exception {
  Path root=Files.createTempDirectory("kapkan-baseline-repro-");
  var inst=new MinecraftInstallation(root);
  Path v=root.resolve("versions/1.21.11"); Files.createDirectories(v);
  Files.write(v.resolve("1.21.11.jar"),new byte[]{1,2,3});
  Files.writeString(v.resolve("1.21.11.json"),"{\"id\":\"1.21.11\",\"mainClass\":\"fixture.Main\",\"libraries\":[{\"name\":\"example:missing:1\",\"downloads\":{\"artifact\":{\"path\":\"example/missing/1/missing-1.jar\",\"size\":5}}}]}");
  System.out.println("BUG partial base with missing library reported prepared = "+FreshInstallSupport.isPrepared(inst,"1.21.11"));
  String id="1.21.11-forge-test";Path p=root.resolve("versions").resolve(id);Files.createDirectories(p);
  Files.writeString(p.resolve(id+".json"),"{\"inheritsFrom\":\"1.21.11\",\"mainClass\":\"fixture.ForgeMain\"}");
  Method m=ModLoaderInstallerService.class.getDeclaredMethod("profileTargetsBase",MinecraftInstallation.class,String.class,String.class);m.setAccessible(true);
  System.out.println("BUG 1.21.11 Forge profile wrongly accepted for 1.21.1 = "+m.invoke(new ModLoaderInstallerService(),inst,id,"1.21.1"));
  Method sj=MinecraftProcessLauncher.class.getDeclaredMethod("shellJoin",List.class);sj.setAccessible(true);
  System.out.println("BUG command log exposes dummy session token = "+((String)sj.invoke(null,List.of("java","Main","--accessToken","TEST_ONLY_NOT_A_REAL_TOKEN"))).contains("TEST_ONLY_NOT_A_REAL_TOKEN"));
  Files.writeString(v.resolve("1.21.11.json"),"{\"id\":\"1.21.11\",\"mainClass\":\"fixture.Main\",\"libraries\":[{\"name\":\"org.example:shared:1\"}]}");
  Files.writeString(p.resolve(id+".json"),"{\"inheritsFrom\":\"1.21.11\",\"mainClass\":\"fixture.ForgeMain\",\"libraries\":[{\"name\":\"org.example:shared:2\"}]}");
  var profile=new VersionProfileLoader(inst).load(id);
  System.out.println("BUG inherited libraries retain old version ahead of override = "+profile.libraries());
  try(var walk=Files.walk(root)){for(Path f:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(f);}
 }
}
