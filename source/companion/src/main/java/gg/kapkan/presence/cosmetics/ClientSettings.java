package gg.kapkan.presence.cosmetics;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Versioned preferences. Writes are atomic and retain keys owned by older/newer components. */
public final class ClientSettings {
    public final Path file,capes;
    public boolean cape=true,fps=true,coordinates=false,zoom=true,emblem=true,reducedMotion=false;
    public boolean keystrokes=false,clicks=false,armor=false,effects=false,toggleSprint=false,sprintIndicator=true;
    public boolean onboardingShown=false;
    public String design="black",pattern="Solid",material="Flow",customFile="",activeProfile="Default";
    public boolean skin=false,slim=false;
    public String skinFile="";
    public int base=0x000000,accent=0xFFFFFF,hudCorner=0,zoomPower=3;
    public HudLayout hud=new HudLayout();
    private final Properties retained=new Properties();
    private String loadError="";
    public ClientSettings(Path file){this.file=file;this.capes=file.resolveSibling("capes");load();}
    public static ClientSettings standard(){return new ClientSettings(Path.of(System.getProperty("kapkan.client.config",Path.of(System.getProperty("user.home"),".kapkan","client.properties").toString())));}
    private void load(){
        if(!Files.exists(file))return;
        try(var in=Files.newInputStream(file)){retained.load(in);}catch(IOException|IllegalArgumentException e){loadError="Preferences could not load: "+file;return;}
        Properties p=retained;
        cape=bool(p,"cape",true);fps=bool(p,"fps",true);coordinates=bool(p,"coordinates",false);zoom=bool(p,"zoom",true);
        emblem=bool(p,"emblem",true);reducedMotion=bool(p,"reducedMotion",false);onboardingShown=bool(p,"onboardingShown",false);
        keystrokes=bool(p,"keystrokes",false);clicks=bool(p,"clicks",false);armor=bool(p,"armor",false);effects=bool(p,"effects",false);
        toggleSprint=bool(p,"toggleSprint",false);sprintIndicator=bool(p,"sprintIndicator",true);
        String old=p.getProperty("design","black");design=switch(old){case "crimson"->"black";case "glacier"->"ivory";case "royal"->"burgundy";default->choice(old,"black","ivory","burgundy","studio","custom");};
        pattern=choice(p.getProperty("pattern"),"Solid","Border","Chevron","Diamond");material=choice(p.getProperty("material"),"Flow","Silk","Heavy");
        base=color(p.getProperty("base"),base);accent=color(p.getProperty("accent"),accent);hudCorner=number(p.getProperty("hudCorner"),0,0,2);zoomPower=number(p.getProperty("zoomPower"),3,2,5);
        String name=p.getProperty("customFile","");if(name.matches("[A-Za-z0-9_-]{1,90}\\.png"))customFile=name;
        skin=bool(p,"skin",false);slim=bool(p,"slim",false);String skinName=p.getProperty("skinFile","");if(skinName.matches("[A-Za-z0-9_-]{1,90}\\.png"))skinFile=skinName;
        hud=HudLayout.read(p,"hud.",hudCorner);try{activeProfile=profileName(p.getProperty("activeProfile","Default"));}catch(IllegalArgumentException ignored){activeProfile="Default";}
    }
    public String loadError(){return loadError;}
    private static boolean bool(Properties p,String key,boolean fallback){String value=p.getProperty(key);if(value==null)return fallback;if(value.equalsIgnoreCase("true"))return true;if(value.equalsIgnoreCase("false"))return false;return fallback;}
    private static String choice(String value,String...allowed){for(String s:allowed)if(s.equals(value))return s;return allowed[0];}
    public static int color(String value,int fallback){try{String v=value.replace("#","");return v.matches("[0-9a-fA-F]{6}")?Integer.parseInt(v,16):fallback;}catch(Exception e){return fallback;}}
    private static int number(String v,int fallback,int lo,int hi){try{return Math.clamp(Integer.parseInt(v),lo,hi);}catch(Exception e){return fallback;}}
    private static String profileName(String name){String value=name==null?"":name.trim();if(!value.matches("[A-Za-z0-9 _-]{1,32}"))throw new IllegalArgumentException("Use 1–32 letters, numbers, spaces, _ or -");return value;}
    private void writeHud(Properties p,String prefix){hud.write(p,prefix);p.setProperty(prefix+"enabled",fps+","+coordinates+","+keystrokes+","+clicks+","+armor+","+effects+","+sprintIndicator);}
    public List<String> profileNames(){TreeSet<String> names=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);names.add("Default");for(String key:retained.stringPropertyNames())if(key.startsWith("profile.")&&key.endsWith(".enabled"))names.add(key.substring(8,key.length()-8));return List.copyOf(names);}
    public void saveProfile(String name)throws IOException{String valid=profileName(name);Properties before=new Properties();before.putAll(retained);String previous=activeProfile;writeHud(retained,"profile."+valid+".");activeProfile=valid;try{save();}catch(IOException e){retained.clear();retained.putAll(before);activeProfile=previous;throw e;}}
    public void loadProfile(String name)throws IOException{String valid=profileName(name);String prefix="profile."+valid+".";if(!retained.containsKey(prefix+"enabled")&&!valid.equals("Default"))throw new IllegalArgumentException("HUD profile not found");
        hud=HudLayout.read(retained,prefix,0);String enabled=retained.getProperty(prefix+"enabled","true,false,false,false,false,false,true");String[] values=enabled.split(",");if(values.length==7){fps=Boolean.parseBoolean(values[0]);coordinates=Boolean.parseBoolean(values[1]);keystrokes=Boolean.parseBoolean(values[2]);clicks=Boolean.parseBoolean(values[3]);armor=Boolean.parseBoolean(values[4]);effects=Boolean.parseBoolean(values[5]);sprintIndicator=Boolean.parseBoolean(values[6]);}activeProfile=valid;save();}
    public void deleteProfile(String name)throws IOException{String valid=profileName(name);if(valid.equals("Default"))throw new IllegalArgumentException("Keep the Default profile");retained.keySet().removeIf(k->k.toString().startsWith("profile."+valid+"."));if(activeProfile.equals(valid))activeProfile="Default";save();}
    /** Commit before displaying. Neither joining a world nor closing a menu calls this. */
    public boolean claimIntroduction()throws IOException{if(onboardingShown)return false;onboardingShown=true;try{save();}catch(IOException e){onboardingShown=false;throw e;}return true;}
    public synchronized void save()throws IOException{
        if(!loadError.isEmpty())throw new IOException(loadError+". Repair or restore this file before saving.");
        Properties p=new Properties();p.putAll(retained);
        p.setProperty("cape",""+cape);p.setProperty("fps",""+fps);p.setProperty("coordinates",""+coordinates);p.setProperty("zoom",""+zoom);p.setProperty("emblem",""+emblem);p.setProperty("reducedMotion",""+reducedMotion);p.setProperty("onboardingShown",""+onboardingShown);
        p.setProperty("keystrokes",""+keystrokes);p.setProperty("clicks",""+clicks);p.setProperty("armor",""+armor);p.setProperty("effects",""+effects);p.setProperty("toggleSprint",""+toggleSprint);p.setProperty("sprintIndicator",""+sprintIndicator);
        p.setProperty("design",design);p.setProperty("pattern",pattern);p.setProperty("material",material);p.setProperty("base",String.format("%06X",base));p.setProperty("accent",String.format("%06X",accent));p.setProperty("hudCorner",""+hudCorner);p.setProperty("zoomPower",""+zoomPower);p.setProperty("customFile",customFile);p.setProperty("skin",""+skin);p.setProperty("slim",""+slim);p.setProperty("skinFile",skinFile);p.setProperty("activeProfile",activeProfile);p.setProperty("settingsVersion","1.0.5");writeHud(p,"hud.");
        Path absolute=file.toAbsolutePath();Files.createDirectories(absolute.getParent());Path temp=Files.createTempFile(absolute.getParent(),"client-",".tmp");
        try{try(var out=Files.newOutputStream(temp)){p.store(out,"KapKan 1.0.5 in-game preferences");}try{Files.move(temp,absolute,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,absolute,StandardCopyOption.REPLACE_EXISTING);}retained.clear();retained.putAll(p);}finally{Files.deleteIfExists(temp);}
    }
}
