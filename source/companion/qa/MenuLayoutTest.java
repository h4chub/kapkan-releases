import gg.kapkan.presence.cosmetics.MenuLayout;

/** Geometry and input mapping only. Actual Minecraft screenshots verify the renderer separately. */
public final class MenuLayoutTest {
    private static int assertions;
    private static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        int[][] displays={{854,480},{1280,720},{1366,768},{1920,1080},{2560,1440},{3840,2160},{3440,1440},{1080,1920}};
        for(int[] display:displays)for(double density:new double[]{1,2,3,4,5,6}){
            int w=(int)Math.ceil(display[0]/density),h=(int)Math.ceil(display[1]/density);
            MenuLayout m=MenuLayout.fit(w,h,density);
            double panelW=MenuLayout.WIDTH*m.scale(),panelH=MenuLayout.HEIGHT*m.scale();
            String context=display[0]+"x"+display[1]+" GUI "+density;
            check(m.scale()>0&&Double.isFinite(m.scale()),"Finite scale: "+context);
            check(panelW<=w*0.860001&&panelH<=h*0.860001,"Visible game margins: "+context);
            check(panelW*density<=992.001&&panelH*density<=604.501,"Physical size cap: "+context);
            check(Math.abs(m.x()-(w-panelW)/2)<0.00001&&Math.abs(m.y()-(h-panelH)/2)<0.00001,"Centered: "+context);
            for(double[] point:new double[][]{{12,83},{276,200},{374,20},{144,342},{616,370}}){
                double x=m.x()+point[0]*m.scale(),y=m.y()+point[1]*m.scale();
                check(Math.abs(m.localX(x)-point[0])<0.00001&&Math.abs(m.localY(y)-point[1])<0.00001,"Mouse coordinate inverse: "+context);
            }
        }
        System.out.println("MenuLayoutTest: "+assertions+" assertions passed across 48 display / GUI-scale combinations");
    }
}
