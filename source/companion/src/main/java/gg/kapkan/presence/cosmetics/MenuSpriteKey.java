package gg.kapkan.presence.cosmetics;

import java.util.Objects;

/** A reusable lookup key is never inserted into the texture cache. Stored keys cannot change. */
final class MenuSpriteKey {
    private final boolean probe;
    private String text;
    private Object icon;
    private int w,h,radius,pw,ph,hash;
    private MenuSpriteKey(boolean probe){this.probe=probe;}
    static MenuSpriteKey probe(){return new MenuSpriteKey(true);}
    MenuSpriteKey set(String text,Object icon,int w,int h,int radius,int pw,int ph){
        if(!probe)throw new IllegalStateException("Stored sprite keys are immutable");
        assign(text,icon,w,h,radius,pw,ph);return this;
    }
    private void assign(String text,Object icon,int w,int h,int radius,int pw,int ph){
        this.text=text;this.icon=icon;this.w=w;this.h=h;this.radius=radius;this.pw=pw;this.ph=ph;
        int value=Objects.hashCode(text);value=31*value+Objects.hashCode(icon);value=31*value+w;
        value=31*value+h;value=31*value+radius;value=31*value+pw;hash=31*value+ph;
    }
    MenuSpriteKey stored(){MenuSpriteKey key=new MenuSpriteKey(false);key.assign(text,icon,w,h,radius,pw,ph);return key;}
    @Override public int hashCode(){return hash;}
    @Override public boolean equals(Object other){
        return other instanceof MenuSpriteKey key&&w==key.w&&h==key.h&&radius==key.radius&&pw==key.pw&&ph==key.ph&&icon==key.icon&&Objects.equals(text,key.text);
    }
}
