package gg.kapkan.presence.cosmetics;

import net.minecraft.client.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import com.mojang.blaze3d.platform.InputConstants;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import static gg.kapkan.presence.cosmetics.MenuStyle.*;

/** A compact in-game utility: native widgets/input, bounded layout, no blur pass. */
public final class KapkanScreen extends Screen {
    public static final String CAPE_WEBSITE="https://mc-capes.com/";
    private static final int W=MenuLayout.WIDTH,H=MenuLayout.HEIGHT,LEFT=144,CONTENT=472,ROW_Y=121,ROW_H=51,VISIBLE_ROWS=4;
    private final Screen parent;
    private final ClientSettings s=ClientFeatures.settings();
    private int tab=3,group,scroll,customIndex=-1;
    private double originX,originY,scale;
    private String status="",name="My cape",path="",query="",profileName="My layout";
    private long statusUntil;
    private boolean statusError,importing,front,choosingFile,profiles,refreshQueued;
    private KeyMapping binding;
    private EditBox searchBox;
    private java.util.List<Path> custom;
    private java.util.List<Row> rows=java.util.List.of();
    private record Row(String label,String description,int group,BooleanSupplier enabled,Runnable toggle,Supplier<String> value,Runnable action){}
    public KapkanScreen(Screen parent){super(MenuStyle.label("KapKan"));this.parent=parent;}
    @Override protected void init(){
        MenuLayout layout=MenuLayout.fit(width,height,minecraft.getWindow().getGuiScale());scale=layout.scale();originX=layout.x();originY=layout.y();
        clearWidgets();searchBox=null;
        if(custom==null)custom=CapeArt.library(s.capes);
        if(s.design.equals("custom"))for(int i=0;i<custom.size();i++)if(custom.get(i).getFileName().toString().equals(s.customFile)){customIndex=i;break;}
        String[] names={"Features","Capes","Skins","Controls","Help"};int[] targets={3,0,2,5,6};
        for(int i=0;i<names.length;i++){int target=targets[i];MenuButton b=button(12,83+i*39,108,32,names[i],()->navigate(target));b.tab=true;b.alignLeft=true;b.selected=()->tab==target||(target==0&&tab==1);}
        MenuButton close=button(588,20,28,28,"×",this::onClose);close.quiet=true;close.narrationLabel="Close KapKan";
        if(importing){importControls();return;}
        switch(tab){case 0->capes();case 1->studio();case 2->skins();case 3->{if(profiles)profileControls();else featureControls();}case 5->controlSettings();case 6->help();}
        if(tab<=2){button(410,318,98,27,()->front?"View back":"View front",()->front=!front).quiet=true;button(516,318,100,27,()->ClientFeatures.previewRunning?"Stop walk":"Preview walk",()->ClientFeatures.previewRunning=!ClientFeatures.previewRunning).quiet=true;}
    }
    private void navigate(int target){tab=target;profiles=false;importing=false;binding=null;scroll=0;status="";rebuildWidgets();}
    private MenuButton button(int x,int y,int w,int h,String text,Runnable action){return button(x,y,w,h,()->text,action);}
    private MenuButton button(int x,int y,int w,int h,Supplier<String> text,Runnable action){return addRenderableWidget(new MenuButton(x,y,w,h,text,action));}
    private MenuButton toggle(int x,int y,int w,int h,String label,String description,BooleanSupplier value,Runnable action){
        MenuButton b=button(x,y,w,h,label,()->{boolean before=value.getAsBoolean();action.run();if(before!=value.getAsBoolean())save(label+" "+(value.getAsBoolean()?"on":"off"));});b.toggle=true;b.selected=value;b.description=description;return b;
    }
    private EditBox field(int x,int y,int w,String label,String value,int max,Consumer<String> change){
        MenuInput box=new MenuInput(font,x,y,w,MenuStyle.label(label));box.hint(label);box.setMaxLength(max);box.setTextColor(TEXT);box.setTextShadow(false);box.addFormatter((text,index)->FormattedCharSequence.forward(text,FONT));box.setValue(value);box.setResponder(change);addRenderableWidget(box);return box;
    }
    private void capes(){
        for(int i=0;i<3;i++){String id=CapeArt.DESIGNS[i];MenuButton b=button(LEFT+(i%2)*132,92+(i/2)*108,122,100,CapeArt.NAMES[i],()->{s.design=id;s.cape=true;save(displayName()+" equipped");});b.cape=id;b.selected=()->s.cape&&s.design.equals(id);}
        MenuButton none=button(276,200,122,100,"No Cape",()->{s.cape=false;save("KapKan cape removed");});none.noCape=true;none.selected=()->!s.cape;none.narrationLabel="No KapKan cape. Existing Minecraft cape unchanged";
        button(LEFT,310,122,28,"Customize",()->{tab=1;rebuildWidgets();}).primary=true;
        button(276,310,122,28,"Import PNG",()->{importing=true;rebuildWidgets();}).quiet=true;
        if(!custom.isEmpty())button(LEFT,342,254,23,this::customLabel,()->{customIndex=(customIndex+1)%custom.size();equip(custom.get(customIndex));}).quiet=true;
    }
    private String customLabel(){if(customIndex<0)return "Saved capes ("+custom.size()+")  ›";String file=custom.get(customIndex%custom.size()).getFileName().toString();return "Saved · "+shorten(file.replaceFirst("-[a-f0-9]{8}\\.png$",""),24)+"  ›";}
    private void equip(Path file){s.customFile=file.getFileName().toString();s.design="custom";s.cape=true;CosmeticTextures.invalidate("custom");save("Custom cape equipped");}
    private void studio(){
        button(LEFT,103,254,28,()->"Style · "+(s.pattern.equals("Solid")?"Plain":s.pattern)+"  ›",()->{s.pattern=cycle(s.pattern,"Solid","Border","Chevron","Diamond");studioChanged();});
        field(LEFT,161,120,"Base colour",String.format("#%06X",s.base),7,v->{if(validColor(v)){s.base=ClientSettings.color(v,s.base);studioChanged();}else error("Use six hex digits, like #111111");});
        field(278,161,120,"Logo colour",String.format("#%06X",s.accent),7,v->{if(validColor(v)){s.accent=ClientSettings.color(v,s.accent);studioChanged();}else error("Use six hex digits, like #FFFFFF");});
        toggle(LEFT,202,143,30,"KK logo","",()->s.emblem,()->{s.emblem=!s.emblem;studioChanged();});
        button(297,202,101,30,"Swap colours",()->{int before=s.base;s.base=s.accent;s.accent=before;studioChanged();rebuildWidgets();}).quiet=true;
        field(LEFT,271,148,"Cape name",name,32,v->name=v);
        button(300,271,98,28,"Save cape",()->{try{Path saved=CapeArt.saveNew(CapeArt.make("studio",s),s.capes,name);equip(saved);custom=CapeArt.library(s.capes);save("Cape saved");}catch(Exception e){error(e.getMessage());}}).primary=true;
        button(LEFT,317,104,27,"Back to capes",()->{tab=0;rebuildWidgets();}).quiet=true;
        button(288,317,110,27,"Open folder",()->folder(s.capes)).quiet=true;
    }
    private void studioChanged(){s.design="studio";s.cape=true;CosmeticTextures.invalidate("studio");save("Design updated");}
    private static boolean validColor(String value){return value.matches("#?[a-fA-F0-9]{6}");}
    private void skins(){
        button(LEFT,139,142,30,"Import skin PNG",()->{importing=true;rebuildWidgets();}).primary=true;
        button(294,139,104,30,"Open folder",()->folder(s.file.resolveSibling("skins"))).quiet=true;
        button(LEFT,211,254,30,()->"Arm model · "+(s.slim?"Slim":"Classic")+"  ›",()->{s.slim=!s.slim;save("Arm model updated");});
        toggle(LEFT,262,168,31,"Custom skin","",()->s.skin,()->{if(s.skinFile.isBlank()){error("Import a skin PNG first");return;}s.skin=!s.skin;});
        button(320,262,78,31,"Reset",()->{s.skin=false;save("Account skin restored");}).quiet=true;
    }
    private void featureControls(){
        searchBox=field(374,20,204,"Search features",query,60,v->{query=v;scroll=0;refreshQueued=true;});
        String[] categories={"All","HUD","Movement"};int[] ws={46,50,94};int x=LEFT;
        for(int i=0;i<categories.length;i++){int target=i;MenuButton b=button(x,80,ws[i],28,categories[i],()->{group=target;scroll=0;rebuildWidgets();});b.tab=true;b.selected=()->group==target;x+=ws[i]+6;}
        rows=java.util.List.of(
            feature("FPS","Current rendered frames per second.",1,()->s.fps,()->s.fps=!s.fps),
            feature("Coordinates","Your current block position: X, Y and Z.",1,()->s.coordinates,()->s.coordinates=!s.coordinates),
            feature("Keystrokes","Movement and jump keys as you press them.",1,()->s.keystrokes,()->s.keystrokes=!s.keystrokes),
            feature("Mouse clicks","See when left and right mouse buttons are held.",1,()->s.clicks,()->s.clicks=!s.clicks),
            feature("Equipment durability","Armor and held tools with colored durability.",1,()->s.armor,()->s.armor=!s.armor),
            feature("Active effects","Effect names, level and time remaining.",1,()->s.effects,()->s.effects=!s.effects),
            feature("Smooth zoom","Hold your zoom key to look closer.",2,()->s.zoom,()->s.zoom=!s.zoom),
            feature("Toggle sprint","Use the toggle-sprint key to latch sprint.",2,()->s.toggleSprint,()->s.toggleSprint=!s.toggleSprint),
            feature("Sprint status","Show when toggle sprint is active.",1,()->s.sprintIndicator,()->s.sprintIndicator=!s.sprintIndicator)
        ).stream().filter(r->(group==0||r.group==group)&&(query.isBlank()||(r.label+" "+r.description).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT).trim()))).toList();
        listRows();
        button(LEFT,342,138,28,"Edit HUD",()->minecraft.setScreen(new HudEditorScreen(this))).primary=true;
        button(292,342,128,28,"Saved layouts",()->{profiles=true;scroll=0;rebuildWidgets();});
        MenuButton up=button(554,80,28,28,"Scroll up",()->moveScroll(-1));up.chevron=-1;up.quiet=true;up.narrationLabel="Scroll features up";up.active=scroll>0;
        MenuButton down=button(588,80,28,28,"Scroll down",()->moveScroll(1));down.chevron=1;down.quiet=true;down.narrationLabel="Scroll features down";down.active=scroll+VISIBLE_ROWS<rows.size();
    }
    private static Row feature(String label,String description,int group,BooleanSupplier enabled,Runnable action){return new Row(label,description,group,enabled,action,null,null);}
    private static Row setting(String label,String description,Supplier<String> value,Runnable action){return new Row(label,description,0,null,null,value,action);}
    private void listRows(){
        scroll=Math.clamp(scroll,0,Math.max(0,rows.size()-VISIBLE_ROWS));
        for(int i=scroll;i<Math.min(rows.size(),scroll+VISIBLE_ROWS);i++){
            Row row=rows.get(i);int y=ROW_Y+(i-scroll)*ROW_H;
            if(row.enabled!=null)toggle(LEFT,y,CONTENT,45,row.label,row.description,row.enabled,row.toggle);
            else{MenuButton b=button(LEFT+282,y+7,190,30,row.value,row.action);b.narrationLabel=row.label+", "+row.value.get()+", "+row.description;}
        }
    }
    private void controlSettings(){
        rows=java.util.List.of(
            setting("Open KapKan","Default J. Also available from the pause menu.",()->binding==ClientFeatures.menuKey?"Press a key…":ClientFeatures.menuKey.getTranslatedKeyMessage().getString(),()->binding=ClientFeatures.menuKey),
            setting("Zoom key","Hold to zoom while playing.",()->binding==ClientFeatures.zoomKey?"Press a key…":ClientFeatures.zoomKey.getTranslatedKeyMessage().getString(),()->binding=ClientFeatures.zoomKey),
            setting("Toggle sprint key","Separate from Minecraft's hold-to-sprint key.",()->binding==ClientFeatures.toggleSprintKey?"Press a key…":ClientFeatures.toggleSprintKey.getTranslatedKeyMessage().getString(),()->binding=ClientFeatures.toggleSprintKey),
            setting("Zoom strength","Adjust magnification while zoom is held.",()->s.zoomPower+"×  ›",()->{s.zoomPower=s.zoomPower==5?2:s.zoomPower+1;save("Zoom strength updated");}),
            setting("Cape motion","Change the fabric weight of KapKan capes.",()->s.material+"  ›",()->{s.material=cycle(s.material,"Flow","Silk","Heavy");save("Cape motion updated");}),
            feature("Reduced motion","Gentler capes and instant UI hover changes.",0,()->s.reducedMotion,()->s.reducedMotion=!s.reducedMotion),
            feature("Share cosmetics","Share with compatible clients on this server.",0,()->gg.kapkan.presence.BadgeClient.engine!=null&&gg.kapkan.presence.BadgeClient.engine.sharingEnabled(),()->{var e=gg.kapkan.presence.BadgeClient.engine;if(e==null){error("Cosmetic service is not ready");return;}try{e.setSharingEnabled(!e.sharingEnabled());}catch(java.io.IOException failure){error("Could not save sharing preference");}})
        );
        listRows();
        MenuButton up=button(554,80,28,28,"Scroll up",()->moveScroll(-1));up.chevron=-1;up.quiet=true;up.narrationLabel="Scroll controls up";up.active=scroll>0;
        MenuButton down=button(588,80,28,28,"Scroll down",()->moveScroll(1));down.chevron=1;down.quiet=true;down.narrationLabel="Scroll controls down";down.active=scroll+VISIBLE_ROWS<rows.size();
    }
    private void profileControls(){
        field(LEFT,108,286,"Layout name",profileName,32,v->profileName=v);
        button(440,108,176,28,"Save current layout",()->{try{s.saveProfile(profileName);save("Layout saved");rebuildWidgets();}catch(Exception e){error(e.getMessage());}}).primary=true;
        java.util.List<String> names=s.profileNames();scroll=Math.clamp(scroll,0,Math.max(0,names.size()-4));
        for(int i=scroll;i<Math.min(names.size(),scroll+4);i++){String saved=names.get(i);int y=154+(i-scroll)*44;
            MenuButton load=button(LEFT,y,370,36,saved,()->{try{s.loadProfile(saved);profileName=saved;save("Layout loaded");rebuildWidgets();}catch(Exception e){error(e.getMessage());}});load.alignLeft=true;load.selected=()->s.activeProfile.equals(saved);
            button(524,y,92,36,"Delete",()->{try{s.deleteProfile(saved);save("Layout deleted");rebuildWidgets();}catch(Exception e){error(e.getMessage());}}).quiet=true;
        }
        button(LEFT,342,132,28,"Back to features",()->{profiles=false;scroll=0;rebuildWidgets();}).quiet=true;
        button(478,342,138,28,"Edit HUD",()->minecraft.setScreen(new HudEditorScreen(this))).primary=true;
    }
    private void help(){
        button(LEFT,115,194,30,"Show introduction",ClientFeatures::showIntroduction).primary=true;
        button(350,115,194,30,"Minecraft controls",()->minecraft.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options)));
        button(LEFT,303,194,29,"Open settings folder",()->folder(s.file.toAbsolutePath().getParent())).quiet=true;
    }
    private void importControls(){
        field(LEFT,178,CONTENT,"PNG file path",path,2048,v->path=v);
        button(LEFT,224,160,30,"Import and equip",()->importPath(path)).primary=true;
        button(316,224,144,30,()->choosingFile?"Choosing…":"Choose file…",this::chooseFile);
        button(530,224,86,30,"Back",()->{importing=false;rebuildWidgets();}).quiet=true;
        if(tab!=2)button(LEFT,278,258,28,"Browse mc-capes.com  ↗",()->Util.getPlatform().openUri(CAPE_WEBSITE)).quiet=true;
    }
    private void chooseFile(){
        if(choosingFile)return;choosingFile=true;int importTab=tab;
        Thread.ofPlatform().daemon().name("kapkan-png-picker").start(()->{
            String chosen=null,failure=null;
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()){chosen=org.lwjgl.util.tinyfd.TinyFileDialogs.tinyfd_openFileDialog("Choose a KapKan PNG",System.getProperty("user.home")+java.io.File.separator,stack.pointers(stack.UTF8("*.png")),"PNG image",false);}catch(Throwable e){failure="Drop a PNG here or paste its path.";}
            String result=chosen,message=failure;minecraft.execute(()->{choosingFile=false;if(minecraft.screen!=this)return;if(message!=null){error(message);return;}if(result!=null){tab=importTab;path=result;importPath(result);}});
        });
    }
    private void importPath(String raw){try{String value=raw.trim();if(value.startsWith("\"")&&value.endsWith("\""))value=value.substring(1,value.length()-1);Path source=Path.of(value);if(tab==2){Path saved=CapeArt.saveNew(CosmeticTextures.readSkin(source),s.file.resolveSibling("skins"),source.getFileName().toString().replaceFirst("(?i)\\.png$",""));s.skinFile=saved.getFileName().toString();s.skin=true;CosmeticTextures.invalidateSkin();save("Skin imported locally");}else{Path saved=CapeArt.saveNew(CapeArt.read(source),s.capes,source.getFileName().toString().replaceFirst("(?i)\\.png$",""));equip(saved);custom=CapeArt.library(s.capes);}importing=false;rebuildWidgets();}catch(Exception e){error(e.getMessage()==null?"Could not import this PNG":e.getMessage());}}
    @Override public void onFilesDrop(java.util.List<Path> files){if(files.size()!=1){error("Drop one PNG at a time");return;}importPath(files.getFirst().toString());}
    private void folder(Path path){try{Files.createDirectories(path);Util.getPlatform().openPath(path);}catch(Exception e){error("Could not open folder");}}
    private void save(String message){try{s.save();ClientFeatures.refreshCosmetics();status=message;statusError=false;statusUntil=System.nanoTime()+2_500_000_000L;}catch(Exception e){error("Could not save. Check settings-folder permissions.");}}
    private void error(String message){status=message==null?"Could not complete this action":message;statusError=true;statusUntil=Long.MAX_VALUE;}
    public String displayName(){return switch(s.design){case "black"->"Black";case "ivory"->"Ivory";case "burgundy"->"Burgundy";case "studio"->"Custom design";default->"Custom cape";};}
    public String previewDesign(){return tab==1?"studio":s.design;}
    private static String cycle(String value,String...choices){for(int i=0;i<choices.length;i++)if(choices[i].equals(value))return choices[(i+1)%choices.length];return choices[0];}
    private static String shorten(String text,int count){return text.length()<=count?text:text.substring(0,count-1)+"…";}
    private String title(){if(importing)return tab==2?"Import skin":"Import cape";return switch(tab){case 0->"Capes";case 1->"Cape studio";case 2->"Skins";case 3->profiles?"Saved layouts":"Features";case 5->"Controls";default->"Help";};}
    private String subtitle(){if(importing)return "Choose a PNG, paste a path, or drop one here.";return switch(tab){case 0->"Make it yours.";case 1->"Your colours. Your cape.";case 2->"A local appearance override.";case 3->profiles?"Save and restore your HUD setup.":"Small tools for a better session.";case 5->"Keys, motion and sharing.";default->"A little help, whenever you need it.";};}
    @Override public void tick(){
        super.tick();if(refreshQueued){boolean focused=searchBox!=null&&searchBox.isFocused();int cursor=searchBox==null?0:searchBox.getCursorPosition();refreshQueued=false;rebuildWidgets();if(focused&&searchBox!=null){setFocused(searchBox);searchBox.setFocused(true);searchBox.moveCursorTo(Math.min(cursor,query.length()),false);}}
    }
    /** The menu supplies its own dim layer, so vanilla must not add a blur pass. */
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){if(minecraft.level==null)extractPanorama(g,delta);minecraft.gui.extractDeferredSubtitles();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0x6606090E);g.pose().pushMatrix();g.pose().translate((float)originX,(float)originY);g.pose().scale((float)scale,(float)scale);
        rounded(g,-3,5,W+6,H+1,13,0x25000000);rounded(g,0,0,W,H,12,BG);rounded(g,0,0,132,H,12,0xFF15181E);g.fill(131,18,132,H-18,0xFF272D37);
        scaled(g,"KapKan",20,24,1.5f,TEXT);rounded(g,20,57,20,2,1,ACCENT);text(g,"1.0.5",20,351,MUTED);text(g,"ESC to close",20,368,MUTED);
        scaled(g,title(),LEFT,23,1.6f,TEXT);text(g,subtitle(),LEFT,52,MUTED);
        if(importing){text(g,tab==2?"64 × 64 skin PNG · up to 2 MB":"5:8 artwork or 64 × 32 cape atlas · up to 2 MB",LEFT,119,TEXT);text(g,"PNG file path",LEFT,158,MUTED);}
        else switch(tab){
            case 0->{preview(g,delta);text(g,s.cape?"KapKan cape equipped":"No KapKan cape",410,352,TEXT);}
            case 1->{text(g,"Base colour",LEFT,143,MUTED);text(g,"Logo colour",278,143,MUTED);text(g,"Cape name",LEFT,252,MUTED);preview(g,delta);}
            case 2->{text(g,"Use your own PNG skin.",LEFT,100,TEXT);text(g,"Arm model",LEFT,190,MUTED);text(g,"Only visible on this device.",LEFT,313,MUTED);preview(g,delta);}
            case 3->{if(profiles){text(g,"Active · "+MenuStyle.fit(s.activeProfile,300),LEFT,81,MUTED);if(s.profileNames().isEmpty())text(g,"No saved layouts yet. Save your current HUD above.",LEFT,168,MUTED);}else{if(rows.isEmpty())text(g,"No matching features. Try another search.",LEFT,146,MUTED);listDecorations(g);}}
            case 5->{for(int i=scroll;i<Math.min(rows.size(),scroll+VISIBLE_ROWS);i++){Row r=rows.get(i);if(r.enabled==null){int y=ROW_Y+(i-scroll)*ROW_H;text(g,r.label,LEFT+2,y+7,TEXT);text(g,MenuStyle.fit(r.description,270),LEFT+2,y+25,MUTED);}}listDecorations(g);text(g,binding!=null?"Press a key. Escape cancels.":"Keybinds are also listed in Minecraft Controls.",LEFT,346,MUTED);}
            case 6->{text(g,"The introduction appears once and stays dismissed.",LEFT,87,TEXT);text(g,"Cosmetics in multiplayer",LEFT,177,TEXT);text(g,"Install matching KapKan on the Fabric server",LEFT,200,MUTED);text(g,"and both players' clients to share badges and capes.",LEFT,217,MUTED);text(g,"No Cape removes your KapKan cape only.",LEFT,250,TEXT);text(g,"Your existing Minecraft cape is left unchanged.",LEFT,269,MUTED);}
        }
        String feedback=CosmeticTextures.error.isBlank()?status:CosmeticTextures.error;
        if((System.nanoTime()<statusUntil||!CosmeticTextures.error.isBlank())&&!feedback.isBlank()){
            rounded(g,LEFT,372,CONTENT,16,4,statusError?0xFF44242F:0xFF242B35);text(g,MenuStyle.fit(feedback,CONTENT-16),LEFT+8,373,statusError||!CosmeticTextures.error.isBlank()?0xFFFFB6C3:TEXT);
        }
        super.extractRenderState(g,(int)((mx-originX)/scale),(int)((my-originY)/scale),delta);g.pose().popMatrix();
    }
    private void listDecorations(GuiGraphicsExtractor g){
        if(rows.size()>VISIBLE_ROWS){int track=ROW_H*VISIBLE_ROWS-6;rounded(g,625,ROW_Y,2,track,1,EDGE);int thumb=Math.max(18,track*VISIBLE_ROWS/rows.size());int y=ROW_Y+(track-thumb)*scroll/Math.max(1,rows.size()-VISIBLE_ROWS);rounded(g,625,y,2,thumb,1,ACCENT);}
    }
    private void preview(GuiGraphicsExtractor g,float delta){
        rounded(g,410,92,206,216,10,PANEL);
        if(minecraft.player!=null){ClientFeatures.extractingPreview=true;try{
            var state=minecraft.getEntityRenderDispatcher().extractEntity(minecraft.player,minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
            if(state instanceof AvatarRenderState avatar){avatar.bodyRot=front?155:-25;avatar.yRot=0;avatar.xRot=0;avatar.nameTag=null;avatar.isCrouching=false;avatar.isFallFlying=false;avatar.isVisuallySwimming=false;avatar.swimAmount=0;avatar.pose=net.minecraft.world.entity.Pose.STANDING;avatar.walkAnimationSpeed=ClientFeatures.previewRunning?0.8f:0;avatar.walkAnimationPos=(float)(System.nanoTime()/1e9*7);avatar.showCape=true;avatar.isInvisible=false;avatar.isSpectator=false;avatar.chestEquipment=net.minecraft.world.item.ItemStack.EMPTY;
                g.entity(avatar,(float)(88*scale),new Vector3f(0,avatar.boundingBoxHeight/2,0),new Quaternionf().rotateZ((float)Math.PI),null,(int)(originX+418*scale),(int)(originY+101*scale),(int)(originX+608*scale),(int)(originY+298*scale));
            }
        }finally{ClientFeatures.extractingPreview=false;}}
    }
    public static void image(GuiGraphicsExtractor g,Identifier texture,int x,int y,int w,int h){g.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,0,0,w,h,CapeArt.WIDTH,CapeArt.HEIGHT,CapeArt.WIDTH,CapeArt.HEIGHT);}
    @Override public boolean keyPressed(KeyEvent event){
        if(binding!=null){if(event.key()==256){binding=null;return true;}var key=InputConstants.getKey(event);if(ClientFeatures.conflict(key,binding)){error("That key is in use. Choose another.");return true;}binding.setKey(key);KeyMapping.resetMapping();minecraft.options.save();binding=null;status="Keybind saved";statusError=false;statusUntil=System.nanoTime()+2_000_000_000L;return true;}
        if((tab==3&&!profiles||tab==5)&&(searchBox==null||searchBox!=getFocused())&&(event.key()==266||event.key()==267)){moveScroll(event.key()==266?-VISIBLE_ROWS:VISIBLE_ROWS);return true;}
        return super.keyPressed(event);
    }
    private void moveScroll(int amount){int size=profiles?s.profileNames().size():rows.size();int next=Math.clamp(scroll+amount,0,Math.max(0,size-VISIBLE_ROWS));if(next!=scroll){scroll=next;rebuildWidgets();}}
    private MouseButtonEvent local(MouseButtonEvent e){return new MouseButtonEvent((e.x()-originX)/scale,(e.y()-originY)/scale,e.buttonInfo());}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean twice){return super.mouseClicked(local(e),twice);}
    @Override public boolean mouseReleased(MouseButtonEvent e){return super.mouseReleased(local(e));}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return super.mouseDragged(local(e),dx/scale,dy/scale);}
    @Override public void mouseMoved(double x,double y){super.mouseMoved((x-originX)/scale,(y-originY)/scale);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){double lx=(x-originX)/scale,ly=(y-originY)/scale;if((tab==3||tab==5)&&lx>=LEFT&&lx<=630&&ly>=75&&ly<=370&&vertical!=0){moveScroll(vertical>0?-1:1);return true;}return super.mouseScrolled(lx,ly,horizontal,vertical);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientFeatures.previewRunning=false;minecraft.setScreen(parent);}
}
