package gg.kapkan.presence.cosmetics;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Direct manipulation editor. Widget values are the real game values, including while paused. */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private final ClientSettings settings=ClientFeatures.settings();
    private String selected="fps",dragging,status="",profileName="";
    private boolean error;
    private double grabX,grabY,toolbarX,toolbarY,toolbarScale;
    private int toolbarWidth=560;
    private EditBox profile;
    private Map<String,HudLayout.Bounds> bounds=Map.of();
    public HudEditorScreen(Screen parent){super(Component.literal("KapKan HUD editor"));this.parent=parent;}
    @Override protected void init(){
        toolbarScale=Math.min(1,Math.min((width-16.0)/toolbarWidth,(height-24.0)/108));toolbarX=(width-toolbarWidth*toolbarScale)/2;toolbarY=height-8-104*toolbarScale;
        clearWidgets();
        add(12,30,94,24,()->HudRenderer.enabled(settings,selected)?"Hide widget":"Show widget",()->{HudRenderer.toggle(settings,selected);persist();});
        add(112,30,32,24,"−",()->scale(-.1));add(149,30,32,24,"+",()->scale(.1));
        add(187,30,94,24,"Align left",()->align(0));add(287,30,94,24,"Center",()->align(.5));add(387,30,94,24,"Align right",()->align(1));
        add(487,30,60,24,"Reset",()->{settings.hud.reset();persist();});
        add(12,64,126,25,()->settings.activeProfile+"  ›",this::nextProfile);
        profile=new MenuInput(font,145,62,168,Component.literal("HUD profile name"));profile.setMaxLength(32);profile.setValue(profileName.isBlank()?settings.activeProfile:profileName);profile.setResponder(value->profileName=value);addRenderableWidget(profile);
        add(321,64,63,25,"Save as",()->{try{settings.saveProfile(profile.getValue());status="HUD profile saved";error=false;}catch(Exception e){fail(e);}});
        add(390,64,63,25,"Delete",()->{try{settings.deleteProfile(settings.activeProfile);profile.setValue(settings.activeProfile);status="Profile deleted";error=false;}catch(Exception e){fail(e);}});
        add(459,64,88,25,"Done",this::onClose);
    }
    private void add(int x,int y,int w,int h,String label,Runnable action){add(x,y,w,h,()->label,action);}
    private void add(int x,int y,int w,int h,java.util.function.Supplier<String> label,Runnable action){addRenderableWidget(new MenuButton(x,y,w,h,label,action));}
    private void persist(){if(ClientFeatures.saveSettings()){status="Saved";error=false;}else{status=ClientFeatures.settingsError();error=true;}}
    private void fail(Exception e){status=e.getMessage()==null?"Could not save HUD settings":e.getMessage();error=true;}
    private void scale(double amount){settings.hud.get(selected).resize(settings.hud.get(selected).scale+amount);persist();}
    private void align(double x){settings.hud.get(selected).x=x;persist();}
    private void nextProfile(){var names=settings.profileNames();int index=names.indexOf(settings.activeProfile);String name=names.get((index+1)%names.size());try{settings.loadProfile(name);profile.setValue(name);status="Profile loaded";error=false;}catch(Exception e){fail(e);}}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){if(minecraft.level==null)extractPanorama(g,delta);minecraft.gui.extractDeferredSubtitles();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float partial){
        g.fill(0,0,width,height,0x2507090C);bounds=HudRenderer.render(g,settings,true,selected);
        g.pose().pushMatrix();g.pose().translate((float)toolbarX,(float)toolbarY);g.pose().scale((float)toolbarScale,(float)toolbarScale);
        MenuStyle.rounded(g,0,0,toolbarWidth,104,8,0xF3111419);MenuStyle.text(g,"HUD editor",12,9,MenuStyle.TEXT);
        MenuStyle.text(g,HudRenderer.name(selected)+"  ·  "+Math.round(settings.hud.get(selected).scale*100)+"%",113,9,MenuStyle.ACCENT);
        MenuStyle.text(g,"Drag · scroll to scale · arrows to move · Esc to finish",12,91,MenuStyle.MUTED);
        if(!status.isBlank())MenuStyle.text(g,MenuStyle.fit(status,240),306,9,error?0xFFFFAAB6:MenuStyle.MUTED);
        super.extractRenderState(g,(int)((mx-toolbarX)/toolbarScale),(int)((my-toolbarY)/toolbarScale),partial);g.pose().popMatrix();
    }
    private boolean toolbar(double x,double y){return x>=toolbarX&&x<toolbarX+toolbarWidth*toolbarScale&&y>=toolbarY&&y<toolbarY+104*toolbarScale;}
    private MouseButtonEvent local(MouseButtonEvent event){return new MouseButtonEvent((event.x()-toolbarX)/toolbarScale,(event.y()-toolbarY)/toolbarScale,event.buttonInfo());}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){
        if(toolbar(event.x(),event.y()))return super.mouseClicked(local(event),twice);clearFocus();if(event.button()!=0)return false;
        var ids=new ArrayList<>(bounds.keySet());Collections.reverse(ids);for(String id:ids){var b=bounds.get(id);if(b.contains(event.x(),event.y())){selected=dragging=id;grabX=event.x()-b.x();grabY=event.y()-b.y();return true;}}return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(dragging!=null){HudLayout.Bounds b=bounds.get(dragging);if(b!=null)settings.hud.drag(dragging,event.x()-grabX,event.y()-grabY,width,height,b.width(),b.height());return true;}
        return super.mouseDragged(local(event),dx/toolbarScale,dy/toolbarScale);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){if(dragging!=null){dragging=null;persist();return true;}return super.mouseReleased(local(event));}
    @Override public void mouseMoved(double x,double y){super.mouseMoved((x-toolbarX)/toolbarScale,(y-toolbarY)/toolbarScale);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!toolbar(x,y)){for(var entry:bounds.entrySet())if(entry.getValue().contains(x,y)){selected=entry.getKey();scale(vertical>0?.05:-.05);return true;}}return super.mouseScrolled((x-toolbarX)/toolbarScale,(y-toolbarY)/toolbarScale,horizontal,vertical);}
    @Override public boolean keyPressed(KeyEvent event){
        if(profile!=null&&profile.isFocused())return super.keyPressed(event);
        var b=bounds.get(selected);int amount=event.hasShiftDown()?10:1;
        if(b!=null&&(event.key()==262||event.key()==263||event.key()==264||event.key()==265)){
            double x=b.x()+(event.key()==262?amount:event.key()==263?-amount:0),y=b.y()+(event.key()==264?amount:event.key()==265?-amount:0);settings.hud.drag(selected,x,y,width,height,b.width(),b.height());persist();return true;
        }
        if(event.key()==61||event.key()==334){scale(.05);return true;}if(event.key()==45||event.key()==333){scale(-.05);return true;}return super.keyPressed(event);
    }
    @Override public void onClose(){persist();minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return true;}
}
