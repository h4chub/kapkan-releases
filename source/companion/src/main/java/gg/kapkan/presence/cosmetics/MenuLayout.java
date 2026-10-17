package gg.kapkan.presence.cosmetics;

/** A bounded, framebuffer-aware menu. GUI scaling never turns it into a full-screen overlay. */
public record MenuLayout(double x,double y,double scale) {
    public static final int WIDTH=640,HEIGHT=390;
    public static MenuLayout fit(int guiWidth,int guiHeight,double guiScale) {
        double density=Math.max(0.25,guiScale);
        double scale=Math.min(1.55/density,Math.min(guiWidth*0.86/WIDTH,guiHeight*0.86/HEIGHT));
        scale=Math.max(0.01,scale);
        return new MenuLayout((guiWidth-WIDTH*scale)/2,(guiHeight-HEIGHT*scale)/2,scale);
    }
    public double localX(double x){return (x-this.x)/scale;}
    public double localY(double y){return (y-this.y)/scale;}
}
