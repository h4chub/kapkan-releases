package gg.kapkan.presence;
import gg.kapkan.presence.cosmetics.*;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public final class CosmeticsTest {
    private static int checks;
    private static void check(boolean result,String label){if(!result)throw new AssertionError(label);checks++;}
    public static void main(String[] args) throws Exception {
        for(String type:new String[]{"Flow","Silk","Heavy"}){
            CapeMotion motion=new CapeMotion();for(int i=0;i<200;i++)motion.tick(0,0,type,false);check(motion.tipTrail()<0.001,"No perpetual idle wave: "+type);
            for(int i=0;i<150;i++)motion.tick(0.28,0,type,false);double height=motion.tipHeight(),trail=motion.tipTrail();System.out.println(type+" running tip "+height+", "+trail);check(trail>0.30&&height<0.88,"Running lifts and trails: "+type);
            float[] frame=motion.frame(0.5f,false);for(float p:frame)check(Float.isFinite(p),"Finite vertex");
            for(int i=0;i<300;i++)motion.tick(0,0,type,false);System.out.println(type+" settled tip "+motion.tipHeight()+", "+motion.tipTrail());check(motion.tipTrail()<0.03&&motion.tipHeight()>0.95,"Stops settle under gravity: "+type);
            float[] idle=motion.frame(1,false);for(int i=0;i<20;i++)motion.tick(0,0,type,false);float[] still=motion.frame(1,false);check(Math.abs(idle[60]-still[60])<0.003&&Math.abs(idle[61]-still[61])<0.003,"No continuous motion when stopped");
        }
        Path dir=Files.createTempDirectory("kapkan-cosmetics-test-");try{
            ClientSettings s=new ClientSettings(dir.resolve("client.properties"));check(s.cape&&s.fps&&!s.coordinates,"Defaults");s.base=0xAABBCC;s.skin=true;s.skinFile="test.png";s.material="Heavy";s.save();ClientSettings again=new ClientSettings(s.file);check(again.base==s.base&&again.skin&&again.skinFile.equals("test.png")&&again.material.equals("Heavy"),"Preferences survive reload");
            for(String name:CapeArt.DESIGNS){var image=CapeArt.make(name,s);check(image.getWidth()==320&&image.getHeight()==512,"Premade dimensions");}
            check(s.design.equals("black"),"Default cape is black");
            var black=CapeArt.make("black",s);check(black.getRGB(0,0)==0xFF000000&&black.getRGB(80,100)==0xFF000000,"Black cape has plain black fabric");
            int minX=320,maxX=0,minY=512,maxY=0;boolean white=false;
            for(int y=0;y<512;y++)for(int x=0;x<320;x++)if((black.getRGB(x,y)&0xFFFFFF)!=0){minX=Math.min(x,minX);maxX=Math.max(x,maxX);minY=Math.min(y,minY);maxY=Math.max(y,maxY);if(black.getRGB(x,y)==0xFFFFFFFF)white=true;}
            check(white&&Math.abs(minX+maxX-319)<=2&&Math.abs(minY+maxY-511)<=2,"White original logo is centred in both axes");
            Files.writeString(s.file,"design=royal\ncustomFile=keep.png\n");ClientSettings migrated=new ClientSettings(s.file);check(migrated.design.equals("burgundy")&&migrated.customFile.equals("keep.png"),"Old premade migrates without discarding imported cape reference");
            var corner=MenuRaster.paint(null,40,30,10,80,60);boolean coverage=false;for(int y=0;y<20;y++)for(int x=0;x<20;x++){int a=corner.getRGB(x,y)>>>24;if(a>0&&a<255)coverage=true;}check(coverage,"Rounded edge has subpixel alpha coverage at display density");
            var letters=MenuRaster.paint("KapKan",60,14,0,120,28);coverage=false;for(int y=0;y<28;y++)for(int x=0;x<120;x++){int a=letters.getRGB(x,y)>>>24;if(a>0&&a<255)coverage=true;}check(coverage,"Bundled UI font is antialiased at display density");
            Path original=dir.resolve("original.png");ImageIO.write(CapeArt.make("ivory",s),"PNG",original.toFile());byte[] before=Files.readAllBytes(original);var image=CapeArt.read(original);Path saved=CapeArt.saveNew(image,s.capes,"../../design name");check(saved.getParent().equals(s.capes)&&Files.exists(saved),"Imported filename stays within cape directory");check(java.util.Arrays.equals(before,Files.readAllBytes(original)),"Original PNG unchanged");
            check(CapeArt.library(s.capes).size()==1,"Saved cape discoverable");
            Path atlas=dir.resolve("atlas.png");ImageIO.write(new BufferedImage(64,32,BufferedImage.TYPE_INT_ARGB),"PNG",atlas.toFile());check(CapeArt.read(atlas).getHeight()==512,"Traditional atlas normalized");
            Path wrong=dir.resolve("wrong.png");ImageIO.write(new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB),"PNG",wrong.toFile());try{CapeArt.read(wrong);throw new AssertionError("Accepted wrong cape shape");}catch(java.io.IOException expected){checks++;}
            Path text=dir.resolve("not.png");Files.writeString(text,"not a png");try{CapeArt.read(text);throw new AssertionError("Accepted invalid PNG");}catch(java.io.IOException expected){checks++;}
            check(CosmeticTextures.readSkin(wrong).getWidth()==64,"Skin dimensions accepted");try{CosmeticTextures.readSkin(original);throw new AssertionError("Accepted cape as skin");}catch(java.io.IOException expected){checks++;}
            Files.writeString(s.file,"customFile=../../escape.png\nzoomPower=500\nbase=invalid\n");again=new ClientSettings(s.file);check(again.customFile.isBlank()&&again.zoomPower==5&&again.base==0x000000,"Malformed preferences bounded");
            check(ClientFeatures.welcomeAlpha(0)==0&&ClientFeatures.welcomeAlpha(2)==1&&ClientFeatures.welcomeAlpha(7)>0&&ClientFeatures.welcomeAlpha(9)==0,"Welcome fades in and out");
        }finally{try(var paths=Files.walk(dir)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
        System.out.println("Cosmetics: "+checks+" checks passed");
    }
}
