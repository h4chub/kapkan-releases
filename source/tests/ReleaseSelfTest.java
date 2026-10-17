package gg.kapkan.v23;

import gg.kapkan.launcher.system.*;
import gg.kapkan.launcher.runtime.ManagedJava;
import gg.kapkan.launcher.launch.*;
import gg.kapkan.release.FreshInstallSupport;
import java.util.*;

public final class ReleaseSelfTest {
    static int assertions=0;
    static void check(boolean b,String message){assertions++;if(!b)throw new AssertionError(message);}
    static SystemInfo hw(String os,String arch,double gb,String gpu,boolean apple){return new SystemInfo(os,"test",arch,(long)(gb*1073741824L),8,gpu,2408,1506,60,true,80,apple);}
    static VersionProfile profile(List<Object> libs){return new VersionProfile("26.1.2","26.1.2","net.minecraft.client.main.Main","26.1.2","release",25,"test",List.of(),List.of(),libs,Map.of());}
    public static void main(String[] args)throws Exception{
        System.clearProperty("kapkan.autotune.mode");
        var neo=hw("Mac OS X","aarch64",8,"Apple A18 Pro",true);
        var air=hw("Mac OS X","aarch64",16,"Apple M4",true);
        var pro=hw("Mac OS X","aarch64",36,"Apple M4 Pro",true);
        var basic=hw("Windows 11","amd64",8,"Intel UHD Graphics",false);
        var gaming=hw("Windows 11","amd64",32,"NVIDIA GeForce RTX",false);
        check(KapKanAutoTunePolicy.memoryMiB(neo)==3072,"Neo uses a 3 GiB cap");
        check(KapKanAutoTunePolicy.memoryMiB(air)==4096,"M4 16GiB stays at 4GiB");
        check(KapKanAutoTunePolicy.memoryMiB(pro)==6144,"large RAM cap is bounded");
        check(KapKanAutoTunePolicy.memoryMiB(basic)==3072,"Windows 8GiB bounded");
        check(KapKanAutoTunePolicy.memoryMiB(hw("Windows 11","amd64",4,"Intel",false))==1536,"4GiB Windows bounded");
        check(KapKanAutoTunePolicy.memoryMiB(hw("Unknown","amd64",0,"",false))==2048,"unknown memory is conservative");
        check(KapKanAutoTunePolicy.recommend(neo).fps()==90,"Neo initial target");
        check(KapKanAutoTunePolicy.recommend(neo).simulationDistance()==5,"Neo sim cap");
        check(KapKanAutoTunePolicy.recommend(air).renderDistance()==8,"M4 prior distance");
        check(KapKanAutoTunePolicy.recommend(air).fps()==180,"M4 prior FPS target");
        check(KapKanAutoTunePolicy.recommend(gaming).fps()==180,"gaming target not a benchmark");
        System.setProperty("kapkan.autotune.mode","battery");
        check(KapKanAutoTunePolicy.recommend(neo).fps()==60,"battery target");
        System.clearProperty("kapkan.autotune.mode");
        for(String a:List.of("aarch64","arm64"))check(ManagedJava.normalizeArch(a).equals("aarch64"),"ARM alias");
        for(String a:List.of("amd64","x86_64","x64"))check(ManagedJava.normalizeArch(a).equals("x64"),"x64 alias");
        boolean threw=false;try{ManagedJava.normalizeArch("x86");}catch(IllegalArgumentException e){threw=true;}check(threw,"reject 32bit");
        var modern=profile(List.of(Map.of("name","org.lwjgl:lwjgl:3.4.1:natives-macos"),Map.of("name","org.lwjgl:lwjgl:3.4.1:natives-macos-arm64")));
        var old=profile(List.of(Map.of("name","org.lwjgl:lwjgl:3.2.1","natives",Map.of("osx","natives-macos"))));
        check(ManagedJava.targetArch("Mac OS X","aarch64",modern).equals("aarch64"),"modern Neo uses native ARM");
        check(ManagedJava.targetArch("Mac OS X","aarch64",old).equals("x64"),"Intel-only LWJGL uses x64");
        check(ManagedJava.targetArch("Windows 11","amd64",modern).equals("x64"),"Windows not affected by Mac classifiers");
        check(ManagedJava.targetArch("Mac OS X","x86_64",modern).equals("x64"),"Intel Mac selects x64");
        for(String id:List.of("26.1.2","1.21.11","1.18.2","26.3-pre1")){FreshInstallSupport.validateBaseId(id);assertions++;}
        for(String id:List.of("../bad","/tmp/x","fabric-loader-0.19.5-26.1.2","", "C:\\x")){
          threw=false;try{FreshInstallSupport.validateBaseId(id);}catch(LaunchException e){threw=true;}check(threw,"reject invalid ID "+id);
        }
        var probe=HardwareProbe.probe();
        check(probe.totalMemoryBytes()>0,"actual physical RAM probe succeeds");
        boolean actualApple=System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("mac")&&List.of("arm64","aarch64").contains(System.getProperty("os.arch","").toLowerCase(Locale.ROOT));
        check(probe.appleSilicon()==actualApple,"real operating system/architecture classified correctly");
        System.out.println("Release policy/architecture tests: PASS ("+assertions+" assertions)");
    }
}
