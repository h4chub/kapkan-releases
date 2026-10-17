package gg.kapkan.release;
import java.nio.file.*;
/** Runs unchanged on the previous shipped JAR to verify the upgrade path. */
public final class ReleaseDiscoveryTest {
    public static void main(String[] args)throws Exception{
        if(args.length!=3)throw new IllegalArgumentException("feed.json current-tag expected-tag-or-none");
        String json=Files.readString(Path.of(args[0]));int checks=0;
        for(var platform:new UpdateChecker.Platform[]{UpdateChecker.Platform.MACOS,UpdateChecker.Platform.WINDOWS}){
            var result=UpdateChecker.select(json,args[1],platform);
            if(args[2].equals("none")){if(result.update().isPresent())throw new AssertionError("Unexpected update: "+result);}
            else{
                var update=result.update().orElseThrow(()->new AssertionError("No update found for "+platform));
                if(!update.tag().equals(args[2]))throw new AssertionError(update);
                String core=args[2].replaceFirst("^v", "").split("-",2)[0];
                String wanted=platform==UpdateChecker.Platform.MACOS?"KapKan-macOS-"+core+".zip":"KapKan-"+core+"-Setup.exe";
                if(!update.filename().equals(wanted)||!update.download().toString().endsWith("/"+args[2]+"/"+wanted))throw new AssertionError(update);
            }
            checks++;
        }
        System.out.println("Release discovery: PASS ("+checks+" platforms; "+args[1]+" -> "+args[2]+")");
    }
}
