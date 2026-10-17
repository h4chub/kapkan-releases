package gg.kapkan.launcher.system;

import gg.kapkan.launcher.launch.MiniJson;
import java.awt.*;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Small, bounded OS probes. Never mistake the launcher's heap for physical RAM. */
public final class HardwareProbe {
    private HardwareProbe() {}
    public static SystemInfo probe() {
        String os = System.getProperty("os.name", "Unknown");
        String arch = System.getProperty("os.arch", "Unknown");
        boolean mac = os.toLowerCase(Locale.ROOT).contains("mac");
        boolean win = os.toLowerCase(Locale.ROOT).startsWith("windows");
        boolean arm = arch.equalsIgnoreCase("aarch64") || arch.equalsIgnoreCase("arm64");
        long memory = 0;
        var bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean physical) memory = physical.getTotalMemorySize();
        String gpu = "Unknown GPU";
        boolean battery = false;
        int percent = -1;
        if (mac) {
            long detected = parseLong(command("/usr/sbin/sysctl", "-n", "hw.memsize").trim());
            if (detected > 0) memory = detected;
            String cpu = command("/usr/sbin/sysctl", "-n", "machdep.cpu.brand_string").trim();
            String display = command("/usr/sbin/system_profiler", "SPDisplaysDataType");
            var match = java.util.regex.Pattern.compile("(?m)^\\s*Chipset Model:\\s*(.+)$").matcher(display);
            if (match.find()) gpu = match.group(1).trim();
            else if (!cpu.isBlank()) gpu = cpu;
            else if (arm) gpu = "Apple Silicon";
            String b = command("/usr/bin/pmset", "-g", "batt");
            battery = b.contains("InternalBattery");
            var p = java.util.regex.Pattern.compile("(\\d+)%").matcher(b);
            if (p.find()) percent = (int) parseLong(p.group(1));
        } else if (win) {
            String script = "$c=Get-CimInstance Win32_ComputerSystem; $g=@(Get-CimInstance Win32_VideoController | ForEach-Object {$_.Name}); $b=@(Get-CimInstance Win32_Battery); @{ram=$c.TotalPhysicalMemory;gpu=($g -join ' / ');battery=($b.Count -gt 0);percent=$(if($b.Count){$b[0].EstimatedChargeRemaining}else{-1})}|ConvertTo-Json -Compress";
            String text = command("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script).trim();
            try {
                Map<String,Object> m = MiniJson.object(MiniJson.parse(text));
                long ram = number(m.get("ram")); if (ram > 0) memory = ram;
                String detected = Objects.toString(m.get("gpu"), ""); if (!detected.isBlank()) gpu = detected;
                battery = Boolean.TRUE.equals(m.get("battery"));
                percent = (int) number(m.get("percent"));
            } catch (RuntimeException ignored) { /* conservative unknown GPU, real MXBean RAM */ }
        }
        int width=0,height=0,refresh=60;
        try {
            var dm = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDisplayMode();
            width=dm.getWidth(); height=dm.getHeight(); if(dm.getRefreshRate()>0) refresh=dm.getRefreshRate();
        } catch (HeadlessException ignored) {}
        return new SystemInfo(os,System.getProperty("os.version",""),arch,Math.max(0,memory),
            Math.max(1,Runtime.getRuntime().availableProcessors()),gpu,width,height,refresh,battery,percent,mac&&arm);
    }
    private static long number(Object o){if(o instanceof Number n)return n.longValue();return parseLong(Objects.toString(o,"0"));}
    private static long parseLong(String s){try{return Long.parseLong(s);}catch(NumberFormatException e){return 0;}}
    private static String command(String... args) {
        Process p=null;
        try {
            p=new ProcessBuilder(args).redirectErrorStream(true).start();
            final Process process=p;
            var output=new ByteArrayOutputStream();
            Thread reader=Thread.ofPlatform().daemon().start(()->{
                try(var in=process.getInputStream()){in.transferTo(output);}catch(IOException ignored){}
            });
            if(!p.waitFor(4,TimeUnit.SECONDS)){p.destroyForcibly();return "";}
            reader.join(500);
            return p.exitValue()==0 ? output.toString(StandardCharsets.UTF_8) : "";
        } catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();return "";}
        finally {if(p!=null&&p.isAlive())p.destroyForcibly();}
    }
}
