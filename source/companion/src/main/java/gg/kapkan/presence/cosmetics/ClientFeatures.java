package gg.kapkan.presence.cosmetics;

import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

public final class ClientFeatures {
    private static ClientSettings settings;
    private static KeyMapping.Category category;
    public static KeyMapping menuKey,zoomKey,toggleSprintKey;
    public static final CapeMotion motion=new CapeMotion(),preview=new CapeMotion();
    public static boolean extractingPreview,previewRunning;
    private static Object player;
    private static double x,y,z;
    private static boolean onboardingAttempted,sprintLatched;
    private static String settingsError="";
    private static final ZoomState zoomState=new ZoomState();
    public static ClientSettings settings(){if(settings==null){settings=ClientSettings.standard();settingsError=settings.loadError();}return settings;}
    /** Options.load HEAD registers these before Minecraft reads saved keybinds. */
    public static void keys(){if(menuKey!=null)return;category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("kapkan","client"));menuKey=new KeyMapping("key.kapkan.menu",GLFW.GLFW_KEY_J,category);zoomKey=new KeyMapping("key.kapkan.zoom",GLFW.GLFW_KEY_C,category);toggleSprintKey=new KeyMapping("key.kapkan.toggle_sprint",GLFW.GLFW_KEY_R,category);}
    public static boolean conflict(InputConstants.Key key,KeyMapping owner){
        for(var mapping:Minecraft.getInstance().options.keyMappings)if(mapping!=owner&&!mapping.isUnbound()&&mapping.saveString().equals(key.getName()))return true;
        return key.getValue()==GLFW.GLFW_KEY_ESCAPE||key.getValue()==GLFW.GLFW_KEY_F1||key.getValue()==GLFW.GLFW_KEY_F2||key.getValue()==GLFW.GLFW_KEY_F3||key.getValue()==GLFW.GLFW_KEY_F4||key.getValue()==GLFW.GLFW_KEY_F11;
    }
    /** Existing and intentionally unbound mappings are never reassigned at join time. */
    public static void chooseMenuKey(Minecraft mc){keys();}
    public static boolean saveSettings(){try{settings().save();settingsError="";settingsChanged();return true;}catch(Exception e){settingsError=e.getMessage()==null?"Preferences could not save":e.getMessage();return false;}}
    public static String settingsError(){return settingsError;}
    public static void settingsChanged(){if(!settings().toggleSprint)sprintLatched=false;}
    public static void refreshCosmetics(){settingsChanged();}
    public static void cosmeticsChanged(){refreshCosmetics();}
    public static void replayIntroduction(){showIntroduction();}
    public static void showIntroduction(){Minecraft mc=Minecraft.getInstance();keys();mc.setScreen(new IntroductionScreen(mc.screen));}
    public static void tick(Minecraft mc){
        keys();ClientSettings s=settings();SharedCapes.tick(mc);
        // Only the installation's first title-screen startup owns onboarding. World/player/menu events never do.
        if(!onboardingAttempted&&(mc.screen instanceof TitleScreen||mc.level!=null||mc.player!=null)){
            onboardingAttempted=true;boolean firstTitle=mc.level==null&&mc.player==null&&mc.screen instanceof TitleScreen;
            // QuickPlay can skip the title. Consume first-start onboarding silently, so disconnect cannot introduce it later.
            try{if(s.claimIntroduction()&&firstTitle)mc.setScreen(new IntroductionScreen(mc.screen));}catch(Exception e){settingsError="Introduction could not be saved. Check preferences folder permissions.";}
        }
        while(menuKey.consumeClick())if(mc.player!=null&&mc.screen==null)mc.setScreen(new KapkanScreen(null));
        while(toggleSprintKey.consumeClick())if(s.toggleSprint&&mc.player!=null&&mc.screen==null){sprintLatched=!sprintLatched;if(!sprintLatched)mc.player.setSprinting(false);}
        if(!s.toggleSprint)sprintLatched=false;
        if(mc.player==null||mc.level==null){player=null;motion.reset();zoomState.reset();sprintLatched=false;return;}
        if(player!=mc.player){player=mc.player;motion.reset();sprintLatched=false;x=mc.player.getX();y=mc.player.getY();z=mc.player.getZ();}
        if(!mc.isPaused()){
            double dx=mc.player.getX()-x,dz=mc.player.getZ()-z,dy=mc.player.getY()-y;
            if(Math.abs(dx)+Math.abs(dz)+Math.abs(dy)>3)motion.reset();else motion.tick(Math.hypot(dx,dz),dy,s.material,s.reducedMotion);
            x=mc.player.getX();y=mc.player.getY();z=mc.player.getZ();
        }
        if(mc.screen instanceof KapkanScreen)preview.tick(previewRunning?0.28:0,0,s.material,s.reducedMotion);
    }
    public static boolean sprintRequested(){Minecraft mc=Minecraft.getInstance();return settings().toggleSprint&&sprintLatched&&mc.player!=null&&mc.screen==null;}
    public static boolean sprintLatched(){return sprintLatched;}
    public static boolean zooming(){var mc=Minecraft.getInstance();return settings!=null&&settings.zoom&&zoomKey!=null&&zoomKey.isDown()&&mc.screen==null&&mc.player!=null;}
    public static float fov(float fov){ClientSettings s=settings();Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null){zoomState.reset();return fov;}return (float)(fov/zoomState.update(System.nanoTime(),zooming(),s.zoomPower,s.reducedMotion));}
    public static void hud(GuiGraphicsExtractor g){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.options.hideGui||mc.screen!=null||mc.gui.getDebugOverlay().showDebugScreen())return;
        HudRenderer.render(g,settings(),false,null);
        if(!settingsError.isBlank()){String message="KapKan: preferences could not save. Open Help.";MenuStyle.text(g,message,8,g.guiHeight()-16,0xFFFFAAB6);}
    }
    /** Retained for old external diagnostics; automatic welcome overlays were removed. */
    public static double welcomeAlpha(double seconds){return Math.clamp(Math.min(seconds/0.6,(8.0-seconds)/1.5),0,1);}
}
