package gg.kapkan.presence.cosmetics;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import java.util.function.*;

/** Native focus, narration and input with small, interruptible visual transitions. */
public class MenuButton extends AbstractWidget {
    private final Supplier<String> label;private final Runnable action;
    public BooleanSupplier selected=()->false;
    public String cape,narrationLabel,description="";
    public boolean quiet,primary,tab,toggle,alignLeft,noCape;
    public int chevron;
    private float hoverAmount;private long lastFrame,pressedUntil;
    public MenuButton(int x,int y,int w,int h,Supplier<String> label,Runnable action){super(x,y,w,h,MenuStyle.label(label.get()));this.label=label;this.action=action;}
    public MenuButton(int x,int y,int w,int h,String label,Runnable action){this(x,y,w,h,()->label,action);}
    protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        boolean on=selected.getAsBoolean(),hover=active&&isHoveredOrFocused();int x=getX(),y=getY();long now=System.nanoTime();
        float step=lastFrame==0?1:Math.min(1,(now-lastFrame)/90_000_000f);lastFrame=now;
        if(ClientFeatures.settings().reducedMotion)hoverAmount=hover?1:0;else hoverAmount+=((hover?1:0)-hoverAmount)*step;
        setMessage(MenuStyle.label((narrationLabel==null?label.get():narrationLabel)+(toggle?on?", on":", off":on?", selected":"")+(description.isBlank()?"":", "+description)));
        if(isFocused())MenuStyle.rounded(g,x-2,y-2,width+4,height+4,8,MenuStyle.ACCENT);
        int base=tab&&on?0xFF2C2630:(quiet||tab)?MenuStyle.BG:MenuStyle.PANEL;
        int background=MenuStyle.mix(base,MenuStyle.CONTROL,hoverAmount);
        if(now<pressedUntil)background=MenuStyle.EDGE;
        if(cape!=null||noCape){
            MenuStyle.rounded(g,x,y,width,height,8,on?0xFF382B35:background);
            if(cape!=null){var texture=CosmeticTextures.cape(cape);if(texture!=null)KapkanScreen.image(g,texture,x+(width-34)/2,y+10,34,55);}
            else{MenuStyle.rounded(g,x+width/2-13,y+19,26,29,5,MenuStyle.CONTROL);MenuStyle.centered(g,"/",x,y+26,width,MenuStyle.MUTED);}
            MenuStyle.centered(g,label.get(),x,y+height-27,width,on?MenuStyle.TEXT:MenuStyle.MUTED);
            if(on)MenuStyle.rounded(g,x+width/2-10,y+height-9,20,2,1,MenuStyle.ACCENT);
        }else if(toggle){
            MenuStyle.rounded(g,x,y,width,height,7,background);
            int ly=description.isBlank()?y+(height-14)/2:y+7;
            int reserve=width>=220?105:55;
            MenuStyle.text(g,MenuStyle.fit(label.get(),width-reserve),x+12,ly,active?MenuStyle.TEXT:MenuStyle.MUTED);
            if(!description.isBlank())MenuStyle.text(g,MenuStyle.fit(description,width-reserve),x+12,y+25,MenuStyle.MUTED);
            if(width>=220)MenuStyle.text(g,on?"ON":"OFF",x+width-77,y+(height-14)/2,on?MenuStyle.ACCENT:MenuStyle.MUTED);
            int tx=x+width-43;MenuStyle.rounded(g,tx,y+(height-16)/2,30,16,8,on?MenuStyle.ACCENT:0xFF4D5664);
            MenuStyle.rounded(g,tx+(on?17:3),y+(height-10)/2,10,10,5,on?MenuStyle.BG:MenuStyle.TEXT);
        }else{
            if(primary)background=MenuStyle.mix(MenuStyle.ACCENT,0xFFF2B9C6,hoverAmount);
            MenuStyle.rounded(g,x,y,width,height,6,background);
            int color=primary?MenuStyle.BG:!active?MenuStyle.MUTED:tab&&!on&&!hover?MenuStyle.MUTED:MenuStyle.TEXT;
            String value=MenuStyle.fit(label.get(),width-20);
            if(chevron!=0)MenuStyle.chevron(g,x+(width-12)/2,y+(height-12)/2,chevron<0,color);
            else if(alignLeft)MenuStyle.text(g,value,x+12,y+(height-14)/2,color);else MenuStyle.centered(g,value,x,y+(height-14)/2,width,color);
            if(tab&&on)MenuStyle.rounded(g,x+2,y+8,2,height-16,1,MenuStyle.ACCENT);
        }
    }
    public void onClick(MouseButtonEvent event,boolean doubleClick){if(active){pressedUntil=System.nanoTime()+90_000_000L;action.run();}}
    public boolean keyPressed(KeyEvent event){if(active&&(event.key()==257||event.key()==335||event.key()==32)){playDownSound(net.minecraft.client.Minecraft.getInstance().getSoundManager());pressedUntil=System.nanoTime()+90_000_000L;action.run();return true;}return false;}
    protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
}
