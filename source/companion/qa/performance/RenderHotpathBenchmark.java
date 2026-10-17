package gg.kapkan.presence.cosmetics;

import java.lang.management.ManagementFactory;
import java.util.*;

/** Before = exact former width implementation; after = current width lookup. No Minecraft FPS claim. */
public final class RenderHotpathBenchmark {
    private static volatile float sink;
    private static final String[] LABELS={"KapKan","Features","Capes","Skins","Controls","Help","Search features","FPS","Coordinates","Keystrokes","Mouse clicks","Armor durability","Active effects","Smooth zoom","Toggle sprint","Sprint status","Edit HUD","Saved layouts","120 FPS","-123  64  987","Sprint · active","Speed II · 1:09","No active effects","No Cape","Settings saved","Jump","LMB","RMB","W","A","S","D"};
    private record Sample(long cpu,long allocated){}
    private static Sample sample(boolean cache,int count,com.sun.management.ThreadMXBean bean){
        long id=Thread.currentThread().threadId(),allocated=bean.getThreadAllocatedBytes(id),cpu=bean.getCurrentThreadCpuTime();float total=0;
        for(int i=0;i<count;i++){String text=LABELS[i&(LABELS.length-1)];total+=cache?MenuRaster.width(text):MenuRaster.uncachedWidth(text);}
        cpu=bean.getCurrentThreadCpuTime()-cpu;allocated=bean.getThreadAllocatedBytes(id)-allocated;sink=total;return new Sample(cpu,allocated);
    }
    public static void main(String[] args){
        int count=args.length==0?1_000_000:Integer.parseInt(args[0]);var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);bean.setThreadCpuTimeEnabled(true);
        for(int i=0;i<6;i++){sample(false,100_000,bean);sample(true,100_000,bean);}
        long[] before=new long[9],after=new long[9],oldBytes=new long[9],newBytes=new long[9];
        for(int i=0;i<9;i++){
            Sample old,newer;if((i&1)==0){old=sample(false,count,bean);newer=sample(true,count,bean);}else{newer=sample(true,count,bean);old=sample(false,count,bean);}
            before[i]=old.cpu;after[i]=newer.cpu;oldBytes[i]=old.allocated;newBytes[i]=newer.allocated;
        }
        Arrays.sort(before);Arrays.sort(after);Arrays.sort(oldBytes);Arrays.sort(newBytes);
        System.out.printf(Locale.ROOT,"{\"operation\":\"logical UI text width, 32 warmed menu/HUD labels\",\"iterations_per_round\":%d,\"rounds\":9,\"before_cpu_ns_per_call\":%.3f,\"after_cpu_ns_per_call\":%.3f,\"before_allocated_bytes_per_call\":%.3f,\"after_allocated_bytes_per_call\":%.3f,\"scope\":\"Text metrics hotpath only; not a Minecraft FPS measurement\"}%n",count,before[4]/(double)count,after[4]/(double)count,oldBytes[4]/(double)count,newBytes[4]/(double)count);
    }
}
