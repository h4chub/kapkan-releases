package gg.kapkan.presence.cosmetics;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
/** Retains native clipboard, selection, keyboard and narration; renders at display resolution. */
public final class MenuInput extends EditBox {
    private final int left,top,outerWidth;private int start;private String hint="";
    public MenuInput(Font font,int x,int y,int width,Component label){super(font,x+10,y+6,width-20,14,label);left=x;top=y;outerWidth=width;setBordered(false);}
    public MenuInput hint(String value){hint=value;return this;}
    @Override public boolean isMouseOver(double x,double y){return x>=left&&x<left+outerWidth&&y>=top&&y<top+28;}
    private int position(double x){String v=getValue();for(int i=start;i<v.length();i++)if(x<left+10+MenuRaster.width(v.substring(start,i))+MenuRaster.width(v.substring(i,i+1))/2)return i;return v.length();}
    @Override public void onClick(MouseButtonEvent e,boolean twice){moveCursorTo(position(e.x()),e.hasShiftDown());}
    @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){moveCursorTo(position(e.x()),true);}
    @Override public void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        MenuStyle.rounded(g,left,top,outerWidth,28,6,isFocused()?MenuStyle.ACCENT:MenuStyle.EDGE);
        MenuStyle.rounded(g,left+1,top+1,outerWidth-2,26,5,MenuStyle.PANEL);
        String value=getValue();int cursor=getCursorPosition();start=Math.min(start,Math.min(cursor,value.length()));
        while(start<cursor&&MenuRaster.width(value.substring(start,cursor))>outerWidth-24)start++;
        int end=start;while(end<value.length()&&MenuRaster.width(value.substring(start,end+1))<=outerWidth-20)end++;
        // Native selection is contiguous and always touches the caret.
        String selected=getHighlighted();if(!selected.isEmpty()){
            int highlight=((gg.kapkan.presence.mixin.EditBoxAccessor)(Object)this).kapkan$highlightPos();
            int a=Math.min(cursor,highlight),b=Math.max(cursor,highlight);
            a=Math.max(start,a);b=Math.min(end,b);
            if(b>a)MenuStyle.rounded(g,left+10+MenuStyle.width(value.substring(start,a)),top+6,MenuStyle.width(value.substring(a,b)),15,1,0xFF555761);
        }
        MenuStyle.text(g,value.isEmpty()&&!isFocused()?MenuStyle.fit(hint,outerWidth-20):value.substring(start,end),left+10,top+6,value.isEmpty()?MenuStyle.MUTED:MenuStyle.TEXT);
        var preedit=((gg.kapkan.presence.mixin.EditBoxAccessor)(Object)this).kapkan$preeditOverlay();
        if(isFocused()&&preedit!=null){preedit.updateInputPosition(left+10+MenuStyle.width(value.substring(start,cursor)),top+6);preedit.extractRenderState(g,mx,my,partial);}
        if(isFocused()&&preedit==null&&(System.currentTimeMillis()/500)%2==0){int cx=left+10+MenuStyle.width(value.substring(start,cursor));MenuStyle.rounded(g,cx,top+6,1,15,0,MenuStyle.TEXT);}
    }
}
