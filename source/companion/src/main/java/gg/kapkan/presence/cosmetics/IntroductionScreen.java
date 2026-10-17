package gg.kapkan.presence.cosmetics;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** One first-start introduction, also explicitly reachable from Help. */
public final class IntroductionScreen extends Screen {
    private final Screen parent;
    private int x,y,w,h;
    public IntroductionScreen(Screen parent){super(Component.literal("Welcome to KapKan"));this.parent=parent;}
    @Override protected void init(){w=Math.min(352,width-20);h=Math.min(174,height-20);x=(width-w)/2;y=(height-h)/2;
        addRenderableWidget(Button.builder(Component.literal("Got it"),button->onClose()).bounds(x+16,y+h-36,w-32,22).build());
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){if(minecraft.level==null)extractPanorama(g,delta);minecraft.gui.extractDeferredSubtitles();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xA007080A);MenuStyle.rounded(g,x,y,w,h,10,MenuStyle.BG);MenuStyle.text(g,"Welcome to KapKan",x+16,y+16,MenuStyle.TEXT);
        String shortcut=ClientFeatures.menuKey.isUnbound()?"Pause > KapKan":ClientFeatures.menuKey.getTranslatedKeyMessage().getString();
        g.textWithWordWrap(font,Component.literal("Press "+shortcut+" to open your menu. Rebind it in Controls."),x+16,y+42,w-32,MenuStyle.TEXT);
        if(h>=150)g.textWithWordWrap(font,Component.literal("Arrange your HUD, choose cosmetics and adjust features. Replay this introduction from Help."),x+16,y+76,w-32,MenuStyle.MUTED);
        super.extractRenderState(g,mx,my,delta);
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
