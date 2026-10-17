package gg.kapkan.release;

import java.awt.Desktop;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.ZipFile;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;

/** Retires only positively identified older Mac distributions, using the system Trash. */
public final class LauncherMaintenance {
    public static final int BUILD_NUMBER=10013;
    private static final Set<String> APP_FILES=Set.of(
        "Contents/Info.plist","Contents/PkgInfo","Contents/MacOS/KapKan",
        "Contents/Resources/BUILD-INFO.json","Contents/Resources/RELEASE-GATE.json",
        "Contents/Resources/KapKan-Launcher.jar","Contents/Resources/KapKan-AppIcon.png",
        "Contents/Resources/KapKan.icns","Contents/Resources/KapKan-ClientCore-1.21.11-Beta1.jar",
        "Contents/Resources/KapKan-Presence-26.1.2-H2.jar","Contents/Resources/KapKan-Presence-26.1.2.jar",
        "Contents/Resources/licenses/Go.txt");
    private static final Set<String> EXTRA_FILES=Set.of("Make-DMG.command","READ-ME.md","QA-REPORT.md",".DS_Store");
    private final Path file=Path.of(System.getProperty("user.home"),".kapkan","maintenance.properties");
    private final Properties preferences=new Properties();
    public LauncherMaintenance(){try(var in=Files.newInputStream(file)){preferences.load(in);}catch(IOException ignored){}}
    public boolean automatic(){return Boolean.parseBoolean(preferences.getProperty("automatic","true"));}
    public String status(){return preferences.getProperty("lastResult","Old verified launcher copies are moved to Trash after startup.");}
    public synchronized void automatic(boolean enabled)throws IOException{preferences.setProperty("automatic",Boolean.toString(enabled));save();}
    private void save()throws IOException{
        Files.createDirectories(file.getParent());Path temp=Files.createTempFile(file.getParent(),"maintenance-",".tmp");
        try{try(var out=Files.newOutputStream(temp)){preferences.store(out,"KapKan launcher maintenance");}
            try{Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
    }
    public synchronized String clean(Path game,boolean manual)throws IOException{
        if(!System.getProperty("os.name","").startsWith("Mac"))return "Automatic package cleanup is available on macOS.";
        if(!manual&&(!automatic()||preferences.getProperty("completedBuild","").equals(""+BUILD_NUMBER)))return status();
        String bootstrap=System.getProperty("kapkan.nativeBootstrap","");
        if(bootstrap.isBlank())return "Open the KapKan app to clean old downloads.";
        Path exe=Path.of(bootstrap).toRealPath(),current=exe.getParent().getParent().getParent();
        if(!current.getFileName().toString().endsWith(".app"))return "Current application could not be identified; nothing was moved.";
        Path home=Path.of(System.getProperty("user.home")).toRealPath();
        List<Path> protectedPaths=List.of(current,game,home.resolve(".kapkan"),home.resolve("Library/Application Support/minecraft"));
        List<Path> roots=List.of(home.resolve("Downloads"),home.resolve("Desktop"),home.resolve("Applications"),Path.of("/Applications"));
        if(!Desktop.isDesktopSupported()||!Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH))return "Trash is unavailable; old copies were kept.";
        Result result=retire(scan(roots,protectedPaths),protectedPaths,p->Desktop.getDesktop().moveToTrash(p.toFile()),LauncherMaintenance::inUse);
        String message=result.moved()==0?(result.kept()>0?"Old copies in use or changed were kept. Try again after closing them.":"No old copies found in accessible folders."):
            "Moved "+result.moved()+" old "+(result.moved()==1?"copy":"copies")+" to Trash."+(result.kept()>0?" Kept "+result.kept()+" unavailable copies.":"");
        preferences.setProperty("completedBuild",""+BUILD_NUMBER);preferences.setProperty("lastResult",message);save();return message;
    }
    static List<Path> scan(List<Path> roots,List<Path> protectedPaths){
        Set<Path> found=new LinkedHashSet<>();
        for(Path root:roots){
            if(Files.isSymbolicLink(root)||!Files.isDirectory(root))continue;
            try(var entries=Files.list(root)){
                for(Path p:entries.limit(4096).toList())if(safe(p,protectedPaths)&&oldPackage(p))found.add(p.toAbsolutePath().normalize());
            }catch(IOException|SecurityException ignored){}
        }
        return List.copyOf(found);
    }
    record Result(int moved,int kept){}
    static Result retire(List<Path> candidates,List<Path> protectedPaths,Predicate<Path> trash,Predicate<Path> busy){
        int moved=0,kept=0;
        for(Path p:candidates){
            // Recheck immediately before moving; a newly changed folder is never trusted from the scan.
            try{if(safe(p,protectedPaths)&&oldPackage(p)&&!busy.test(p)&&trash.test(p))moved++;else kept++;}
            catch(RuntimeException e){kept++;}
        }
        return new Result(moved,kept);
    }
    private static boolean inUse(Path candidate){
        String prefix=candidate.toAbsolutePath().normalize().toString()+File.separator;
        try(var processes=ProcessHandle.allProcesses()){
            return processes.anyMatch(p->{var info=p.info();return info.command().orElse("").startsWith(prefix)||
                Arrays.stream(info.arguments().orElse(new String[0])).anyMatch(arg->arg.contains(prefix));});
        }catch(RuntimeException e){return true;}
    }
    private static Path resolved(Path path)throws IOException{return Files.exists(path)?path.toRealPath():path.toAbsolutePath().normalize();}
    private static boolean safe(Path p,List<Path> protectedPaths){
        try{
            if(Files.isSymbolicLink(p))return false;Path real=resolved(p);
            for(Path protect:protectedPaths){Path q=resolved(protect);if(real.startsWith(q)||q.startsWith(real))return false;}
            return true;
        }catch(IOException|SecurityException e){return false;}
    }
    private static boolean allowedFile(String name,boolean app){
        if(app)return APP_FILES.contains(name)||name.endsWith("/.DS_Store");
        return EXTRA_FILES.contains(name)||name.startsWith("KapKan.app/")&&allowedFile(name.substring(11),true);
    }
    private static boolean allowedDirectory(String name,boolean app){
        String prefix=name.endsWith("/")?name:name+"/";
        return app?APP_FILES.stream().anyMatch(f->f.startsWith(prefix)):
            name.equals("KapKan.app")||name.startsWith("KapKan.app/")&&allowedDirectory(name.substring(11),true);
    }
    static boolean oldPackage(Path p){
        String name=p.getFileName().toString();if(!name.startsWith("KapKan")||Files.isSymbolicLink(p))return false;
        try{
            if(Files.isRegularFile(p)&&name.toLowerCase(Locale.ROOT).endsWith(".zip")){
                try(ZipFile z=new ZipFile(p.toFile())){
                    if(z.size()>128)return false;
                    Set<String> names=new HashSet<>();
                    for(var entry:Collections.list(z.entries())){
                        String n=entry.getName();if(!names.add(n)||n.contains("\\")||n.startsWith("/")||Arrays.asList(n.split("/")).contains(".."))return false;
                        if(entry.isDirectory()){if(!allowedDirectory(n.substring(0,n.length()-1),false))return false;}
                        else if(!allowedFile(n,false))return false;
                    }
                    var info=z.getEntry("KapKan.app/Contents/Info.plist");
                    if(info==null||z.getEntry("KapKan.app/Contents/MacOS/KapKan")==null||z.getEntry("KapKan.app/Contents/Resources/KapKan-Launcher.jar")==null)return false;
                    try(var in=z.getInputStream(info)){return oldInfo(in);}
                }
            }
            if(!Files.isDirectory(p))return false;
            boolean app=name.endsWith(".app");Path bundle=app?p:p.resolve("KapKan.app");
            if(!Files.isRegularFile(bundle.resolve("Contents/MacOS/KapKan"))||!Files.isRegularFile(bundle.resolve("Contents/Resources/KapKan-Launcher.jar")))return false;
            try(var walk=Files.walk(p)){
                var entries=walk.limit(129).toList();if(entries.size()>128)return false;
                for(Path entry:entries){if(entry.equals(p))continue;if(Files.isSymbolicLink(entry))return false;
                    String relative=p.relativize(entry).toString().replace(File.separatorChar,'/');
                    if(Files.isDirectory(entry)){if(!allowedDirectory(relative,app))return false;}
                    else if(!Files.isRegularFile(entry)||!allowedFile(relative,app))return false;
                }
            }
            try(var in=Files.newInputStream(bundle.resolve("Contents/Info.plist"))){return oldInfo(in);}
        }catch(Exception e){return false;}
    }
    private static boolean oldInfo(InputStream in)throws Exception{
        byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)return false;
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING,true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        var builder=factory.newDocumentBuilder();builder.setEntityResolver((publicId,systemId)->new InputSource(new StringReader("")));
        var document=builder.parse(new ByteArrayInputStream(bytes));var dict=document.getElementsByTagName("dict").item(0);if(dict==null)return false;
        Map<String,String> values=new HashMap<>();String key=null;
        for(var node=dict.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element e){
            if(e.getTagName().equals("key"))key=e.getTextContent();else if(key!=null){if(values.put(key,e.getTextContent())!=null)return false;key=null;}
        }
        if(!"gg.kapkan.launcher".equals(values.get("CFBundleIdentifier"))||!Set.of("1.0.0","1.0.1","1.0.2").contains(values.getOrDefault("CFBundleShortVersionString","")))return false;
        int build=Integer.parseInt(values.getOrDefault("CFBundleVersion","0"));return build>=10001&&build<BUILD_NUMBER;
    }
}
