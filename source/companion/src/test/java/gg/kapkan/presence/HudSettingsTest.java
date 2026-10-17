package gg.kapkan.presence;
import gg.kapkan.presence.cosmetics.*;
import java.nio.file.*;
import java.util.*;
public final class HudSettingsTest {
    static int assertions;
    static void check(boolean value,String detail){assertions++;if(!value)throw new AssertionError(detail);}
    public static void main(String[]args)throws Exception{
        Path root=Files.createTempDirectory("kapkan-hud-settings"),file=root.resolve("client.properties");
        Files.writeString(file,"cape=false\nfps=false\ncoordinates=true\ndesign=royal\nbase=FE1201\nhudCorner=1\nfuture.option=preserve me\nzoomPower=4\ncustomFile=my-old-cape.png\n");
        ClientSettings s=new ClientSettings(file);check(!s.cape&&!s.fps&&s.coordinates,"existing toggles preserved");check(s.design.equals("burgundy")&&s.base==0xFE1201,"legacy design migration preserved");check(s.hud.get("fps").x==1,"legacyHUDcorner migrated");check(s.zoomPower==4&&s.customFile.equals("my-old-cape.png"),"existingcustom cape andzoom retained");
        check(s.claimIntroduction(),"first installation introduction claimed");check(!s.claimIntroduction(),"sameprocess introonlyonce");check(!new ClientSettings(file).claimIntroduction(),"intro stays dismissed afterrestart");
        Properties written=new Properties();try(var in=Files.newInputStream(file)){written.load(in);}check(written.getProperty("future.option").equals("preserve me"),"unknownproperties retained");
        s.hud.get("keys").move(.67,.34);s.hud.get("keys").resize(1.65);s.keystrokes=true;s.clicks=true;s.saveProfile("PvP");s.hud.reset();s.keystrokes=false;s.clicks=false;s.save();s.loadProfile("PvP");check(s.keystrokes&&s.clicks,"profile includes enabledwidgets");check(Math.abs(s.hud.get("keys").scale-1.65)<.0001,"profile restoration scaling");check(Math.abs(s.hud.get("keys").x-.67)<.0001,"profile restoration positions");
        s=new ClientSettings(file);check(s.activeProfile.equals("PvP")&&s.keystrokes,"restart retains profile andtoggles");check(s.profileNames().containsAll(List.of("Default","PvP")),"saved profileenumeration");s.deleteProfile("PvP");check(!s.profileNames().contains("PvP"),"profiledeletion");
        try{s.saveProfile("../escape");throw new AssertionError("unsafeprofilename accepted");}catch(IllegalArgumentException expected){assertions++;}
        for(int width:new int[]{90,320,854,1920})for(int height:new int[]{60,180,480,1080})for(String id:HudLayout.IDS)for(double scale:new double[]{.5,1,2}){
            s.hud.get(id).resize(scale);s.hud.get(id).move(1,1);var b=s.hud.bounds(id,width,height,120,28);check(b.x()>=0&&b.y()>=0&&b.x()+b.width()<=width&&b.y()+b.height()<=height,"widgetclampedto GUI safearea");s.hud.drag(id,-200,-200,width,height,b.width(),b.height());check(s.hud.get(id).x==0&&s.hud.get(id).y==0,"dragnegativeclamped");
        }
        Properties malformed=new Properties();malformed.setProperty("hud.keys","NaN,0.5,Infinity");check(HudLayout.read(malformed,"hud.",0).get("keys").scale==1,"nonfinitegeometry rejected");
        Files.writeString(root.resolve("broken.properties"),"bad=\\uXX\n");ClientSettings broken=new ClientSettings(root.resolve("broken.properties"));try{broken.save();throw new AssertionError("damagedfileoverwritten");}catch(java.io.IOException expected){check(Files.readString(broken.file).contains("uXX"),"malformed config preserved");}
        check(Files.list(root).noneMatch(p->p.getFileName().toString().endsWith(".tmp")),"atomicwriteleavesnotempfiles");System.out.println("HudSettingsTest: "+assertions+" assertions passed");
    }
}
