package gg.kapkan.presence.cosmetics;

import java.util.*;

/** Resolution-independent HUD geometry. All positions are normalized inside the safe area. */
public final class HudLayout {
    public static final List<String> IDS=List.of("fps","coordinates","keys","clicks","armor","effects","sprint");
    public static final class Placement {
        public double x,y,scale;
        public Placement(double x,double y,double scale){this.x=unit(x);this.y=unit(y);this.scale=scale(scale);}
        public Placement copy(){return new Placement(x,y,scale);}
        public void move(double x,double y){this.x=unit(x);this.y=unit(y);}
        public void resize(double value){scale=scale(value);}
    }
    public record Bounds(int x,int y,int width,int height){public boolean contains(double px,double py){return px>=x&&px<x+width&&py>=y&&py<y+height;}}
    private final Map<String,Placement> widgets=new LinkedHashMap<>();
    public HudLayout(){reset();}
    public void reset(){
        widgets.clear();widgets.put("fps",new Placement(0,0,1));widgets.put("coordinates",new Placement(0,.09,1));
        widgets.put("keys",new Placement(0,.24,1));widgets.put("clicks",new Placement(0,.46,1));
        widgets.put("armor",new Placement(1,.12,1));widgets.put("effects",new Placement(1,.47,1));widgets.put("sprint",new Placement(.5,.92,1));
    }
    public Placement get(String id){Placement value=widgets.get(id);if(value==null)throw new IllegalArgumentException("Unknown HUD widget: "+id);return value;}
    public HudLayout copy(){HudLayout result=new HudLayout();for(String id:IDS)result.widgets.put(id,get(id).copy());return result;}
    public Bounds bounds(String id,int screenWidth,int screenHeight,int contentWidth,int contentHeight){
        Placement p=get(id);int margin=Math.min(8,Math.max(0,Math.min(screenWidth,screenHeight)/8));
        int width=Math.min(Math.max(1,screenWidth-margin*2),(int)Math.ceil(contentWidth*p.scale));
        int height=Math.min(Math.max(1,screenHeight-margin*2),(int)Math.ceil(contentHeight*p.scale));
        int x=margin+(int)Math.round(Math.max(0,screenWidth-margin*2-width)*p.x);
        int y=margin+(int)Math.round(Math.max(0,screenHeight-margin*2-height)*p.y);
        return new Bounds(x,y,width,height);
    }
    public void drag(String id,double x,double y,int screenWidth,int screenHeight,int widgetWidth,int widgetHeight){
        int margin=Math.min(8,Math.max(0,Math.min(screenWidth,screenHeight)/8));
        get(id).move((x-margin)/Math.max(1,screenWidth-margin*2-widgetWidth),(y-margin)/Math.max(1,screenHeight-margin*2-widgetHeight));
    }
    public void write(Properties target,String prefix){for(String id:IDS){Placement p=get(id);target.setProperty(prefix+id,Double.toString(p.x)+","+p.y+","+p.scale);}}
    public static HudLayout read(Properties source,String prefix,int legacyCorner){
        HudLayout result=new HudLayout();if(legacyCorner==1){result.get("fps").x=1;result.get("coordinates").x=1;}else if(legacyCorner==2){result.get("fps").y=.82;result.get("coordinates").y=.92;}
        for(String id:IDS){String raw=source.getProperty(prefix+id);if(raw==null)continue;String[] fields=raw.split(",");if(fields.length!=3)continue;
            try{double x=Double.parseDouble(fields[0]),y=Double.parseDouble(fields[1]),scale=Double.parseDouble(fields[2]);if(Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(scale))result.widgets.put(id,new Placement(x,y,scale));}catch(NumberFormatException ignored){}
        }return result;
    }
    private static double unit(double value){return Double.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    private static double scale(double value){return Double.isFinite(value)?Math.max(.5,Math.min(2,value)):1;}
}
