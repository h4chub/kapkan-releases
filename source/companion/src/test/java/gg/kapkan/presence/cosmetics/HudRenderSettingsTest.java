package gg.kapkan.presence.cosmetics;

import java.nio.file.*;

public final class HudRenderSettingsTest {
    private static int checks;
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        Path dir=Files.createTempDirectory("kapkan-hud-mask-");
        try{
            ClientSettings s=new ClientSettings(dir.resolve("client.properties"));
            check(HudRenderer.enabledMask(s)==1,"Default HUD only requests FPS; sprint status requires toggle sprint");
            s.fps=false;check(HudRenderer.enabledMask(s)==0,"All disabled requests no values");
            for(String id:HudLayout.IDS){
                HudRenderer.toggle(s,id);
                if(id.equals("sprint")){s.sprintIndicator=true;s.toggleSprint=true;}
                check(HudRenderer.enabledMask(s)==1<<HudLayout.IDS.indexOf(id),"Exactly selected widget requested: "+id);
                HudRenderer.toggle(s,id);if(id.equals("sprint"))s.toggleSprint=false;
            }
            for(int ticks:new int[]{-1,0,1,19,20,21,1180,1200,1380,Integer.MAX_VALUE}){
                long seconds=Math.max(0,((long)ticks+19)/20);String expected=seconds/60+":"+String.format(java.util.Locale.ROOT,"%02d",seconds%60);
                check(HudRenderer.duration(ticks,false).equals(expected),"Effect timer formatting: "+ticks);
            }
            check(HudRenderer.duration(0,true).equals("∞"),"Infinite effect timer unchanged");
        }finally{Files.deleteIfExists(dir.resolve("client.properties"));Files.delete(dir);}
        System.out.println("HUD render settings: "+checks+" checks passed");
    }
}
