package gg.kapkan.v23;

import gg.kapkan.launcher.system.SystemInfo;
import java.util.Locale;
import java.util.regex.Pattern;

/** Initial presets, not benchmark claims. Existing user keybindings are never edited. */
final class KapKanAutoTunePolicy {
    private KapKanAutoTunePolicy() {}
    static KapKanV23Launcher.App.AutoTune recommend(SystemInfo h) {
        String gpu=safe(h.gpuName()).toLowerCase(Locale.ROOT);
        double ram=h.totalMemoryGiB();
        boolean apple=h.appleSilicon();
        boolean neo=apple&&(gpu.contains("a18")||gpu.contains("macbook neo"));
        boolean m4=apple&&gpu.contains("m4");
        boolean discrete=!apple&&(gpu.contains("geforce")||gpu.contains("rtx")||gpu.contains("gtx")||gpu.contains("radeon rx"));
        boolean modest=neo||ram<=8.5||(!apple&&!discrete);
        String mode=System.getProperty("kapkan.autotune.mode","").trim().toLowerCase(Locale.ROOT);
        int render=modest?8:10,sim=modest?5:6,entities=modest?75:100;
        int fps=neo?90:modest?90:m4?180:apple?165:180;
        // Preserve the user's established M4 distances rather than forcing a new preset.
        if(m4){render=8;sim=6;}
        if(ram>0&&ram<6.5){render=6;sim=5;entities=60;fps=60;}
        if(mode.equals("quiet")||mode.equals("battery")){render=Math.min(render,6);sim=5;entities=60;fps=60;}
        else if(mode.equals("balanced")){fps=Math.min(fps,120);}
        else if(mode.equals("performance")){fps=modest?120:m4?180:Math.max(180,Math.min(240,h.refreshRate()));}
        else if(mode.equals("unlimited")){fps=260;}
        int[] px=physicalDisplayPixels(h);
        if((long)px[0]*px[1]>=6_000_000L){render=Math.min(render,8);sim=Math.min(sim,6);}
        return new KapKanV23Launcher.App.AutoTune(render,sim,entities,fps,memoryMiB(h));
    }
    static int memoryMiB(SystemInfo h) {
        double ram=h.totalMemoryGiB();
        if(ram<=0)return 2048;
        if(ram<4.5)return 1536;
        if(ram<6.5)return 2048;
        if(ram<=10.5)return 3072;
        if(ram<=20.5)return 4096;
        if(ram<=32.5)return 5120;
        return 6144;
    }
    static int[] physicalDisplayPixels(SystemInfo h){return new int[]{Math.max(0,h.displayWidth()),Math.max(0,h.displayHeight())};}
    static int[] parsePhysicalPixels(String text){
        if(text==null)return new int[]{0,0};
        var m=Pattern.compile("(?im)^\\s*(?:Resolution|UI Looks like):\\s*(\\d+)\\s*x\\s*(\\d+)").matcher(text);
        if(m.find())try{return new int[]{Integer.parseInt(m.group(1)),Integer.parseInt(m.group(2))};}catch(NumberFormatException ignored){}
        return new int[]{0,0};
    }
    private static String safe(String s){return s==null?"":s;}
}
