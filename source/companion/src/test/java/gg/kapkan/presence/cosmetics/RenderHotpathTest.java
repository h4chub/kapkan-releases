package gg.kapkan.presence.cosmetics;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

public final class RenderHotpathTest {
    private static int checks;
    private static void check(boolean result,String message){if(!result)throw new AssertionError(message);checks++;}
    public static void main(String[] args){
        MenuRaster.clearWidths();
        for(String text:List.of("","KapKan","FPS","-123  64  987","Sprint · active","∞","Speed II · 1:09","Café","مرحبا","🎮")){
            float expected=MenuRaster.uncachedWidth(text);
            check(Float.floatToIntBits(MenuRaster.width(text))==Float.floatToIntBits(expected),"Exact uncached font metrics: "+text);
            check(MenuRaster.width(new String(text))==expected,"Equal text contents reuse exact metrics");
            for(int density:new int[]{1,2,3,4}){
                int logicalWidth=(int)Math.ceil(expected)+2;
                var oldImage=MenuRaster.paint(text,logicalWidth,14,0,logicalWidth*density,14*density);
                var cachedImage=MenuRaster.paint(text,(int)Math.ceil(MenuRaster.width(text))+2,14,0,logicalWidth*density,14*density);
                check(Arrays.equals(oldImage.getRGB(0,0,oldImage.getWidth(),oldImage.getHeight(),null,0,oldImage.getWidth()),cachedImage.getRGB(0,0,cachedImage.getWidth(),cachedImage.getHeight(),null,0,cachedImage.getWidth())),"Identical pixels at density "+density);
            }
        }
        for(int i=0;i<4096;i++)MenuRaster.width("coordinate "+i);
        check(MenuRaster.cachedWidths()<=1024,"Dynamic values cannot grow metrics cache indefinitely");
        check(MenuRaster.width("FPS")==MenuRaster.uncachedWidth("FPS"),"Evicted text is measured correctly again");
        int count=MenuRaster.cachedWidths();MenuRaster.width("x".repeat(2048));
        check(MenuRaster.cachedWidths()==count,"Long input is not retained in metrics cache");

        MenuSpriteKey probe=MenuSpriteKey.probe();Object icon=new Object();
        MenuSpriteKey stored=probe.set("FPS",icon,40,14,0,80,28).stored();
        Map<MenuSpriteKey,String> sprites=new LinkedHashMap<>();sprites.put(stored,"original");
        check("original".equals(sprites.get(probe)),"Reusable lookup finds immutable cache entry");
        check(stored.equals(probe)&&probe.equals(stored)&&stored.hashCode()==probe.hashCode(),"Symmetric key equality and hash");
        Object[][] changes={{"new",icon,40,14,0,80,28},{"FPS",null,40,14,0,80,28},{"FPS",icon,41,14,0,80,28},{"FPS",icon,40,15,0,80,28},{"FPS",icon,40,14,1,80,28},{"FPS",icon,40,14,0,120,28},{"FPS",icon,40,14,0,80,42}};
        for(Object[] change:changes){probe.set((String)change[0],change[1],(int)change[2],(int)change[3],(int)change[4],(int)change[5],(int)change[6]);check(sprites.get(probe)==null,"Changed text/shape/icon/density invalidates texture lookup");}
        check("original".equals(sprites.get(probe.set("FPS",icon,40,14,0,80,28))),"Probe mutation leaves stored entry intact");
        try{stored.set(null,null,0,0,0,0,0);throw new AssertionError("Stored key changed");}catch(IllegalStateException expected){checks++;}
        MenuSpriteKey collision=probe.set("Aa",null,10,14,0,10,14).stored();sprites.put(collision,"Aa");
        check(sprites.get(probe.set("BB",null,10,14,0,10,14))==null,"Equal string hash does not return wrong sprite");

        HudRefreshCache<List<Integer>> cache=new HudRefreshCache<>();AtomicInteger refreshes=new AtomicInteger(),armorReads=new AtomicInteger();int[] live={60,42};
        IntFunction<List<Integer>> source=mask->{refreshes.incrementAndGet();var values=new ArrayList<Integer>();if((mask&1)!=0)values.add(live[0]);if((mask&16)!=0){armorReads.incrementAndGet();values.add(live[1]);}return List.copyOf(values);};
        Object player=new Object();long now=1_000_000_000L;
        List<Integer> first=cache.get(player,1,now,source);live[0]=120;
        check(cache.get(player,1,now+1,source)==first,"Unchanged snapshot reused inside cadence");
        check(armorReads.get()==0,"Disabled armor/effect work is not requested");
        check(cache.get(player,1,now+HudRefreshCache.INTERVAL,source).equals(List.of(120)),"Live values refreshed at existing 100 ms cadence");
        check(cache.get(player,17,now+HudRefreshCache.INTERVAL+1,source).equals(List.of(120,42)),"Enabling a widget refreshes immediately");
        int reads=armorReads.get();check(cache.get(player,0,now+HudRefreshCache.INTERVAL+2,source).isEmpty(),"All disabled produces no widgets");
        cache.get(player,0,now+HudRefreshCache.INTERVAL*2,source);check(armorReads.get()==reads,"Disabled widgets stay unread on refresh");
        live[0]=144;check(cache.get(new Object(),1,now+HudRefreshCache.INTERVAL*2+1,source).equals(List.of(144)),"Player/world replacement invalidates snapshot");
        int before=refreshes.get();cache.get(player,127,now+HudRefreshCache.INTERVAL*2+2,source);check(refreshes.get()==before+1,"Editor's full-widget selection invalidates gameplay snapshot");
        System.out.println("Render hotpaths: "+checks+" checks passed");
    }
}
