package gg.kapkan.release;

import com.sun.net.httpserver.HttpServer;
import gg.kapkan.launcher.launch.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Isolated cached-profile/repair failures. No Minecraft or personal game data. */
public final class FabricPreflightTest {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static String q(String value){return FabricFlowTest.json(value);}
    static void write(Path file,byte[] bytes)throws Exception{Files.createDirectories(file.getParent());Files.write(file,bytes);}
    static void rejects(LaunchPlan plan,String expected)throws Exception{
        try{FabricLaunchSupport.verify(plan,new ArrayList<>(plan.command()),null);throw new AssertionError("Expected preflight failure: "+expected);}
        catch(LaunchException e){check(e.getMessage().contains(expected),"Useful failure: "+e.getMessage());}
    }
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("kapkan-final-repair-"),cache=root.resolve("shared installation"),game=root.resolve("custom mods game");
        Files.createDirectories(game);
        String base="26.1",id="fabric-loader-0.19.5-"+base;
        String relative="net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar";
        byte[] loader=FabricFlowTest.knot(root.resolve("fixture-source"));
        Path file=cache.resolve("libraries").resolve(relative),metadata=cache.resolve("versions").resolve(id).resolve(id+".json");
        AtomicInteger requests=new AtomicInteger(),status=new AtomicInteger(200);
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/repo/",e->{requests.incrementAndGet();e.sendResponseHeaders(status.get(),loader.length);e.getResponseBody().write(loader);e.close();});server.start();
        String repository="http://127.0.0.1:"+server.getAddress().getPort()+"/repo/";
        String lib="{\"name\":\"net.fabricmc:fabric-loader:0.19.5\",\"url\":"+q(repository)+",\"sha1\":"+q(HardeningSelfTest.hash(loader))+",\"size\":"+loader.length+"}";
        String skipped="{\"name\":\"example:never:1\",\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"never-an-os\"}}]}";
        String prefix="{\"id\":"+q(id)+",\"inheritsFrom\":"+q(base)+",\"mainClass\":\"net.fabricmc.loader.impl.launch.knot.KnotClient\",\"libraries\":[";
        String profile=prefix+lib+","+skipped+"]}";
        List<String> command=List.of("java","--class-path","retained-client.jar","net.fabricmc.loader.impl.launch.knot.KnotClient");
        var plan=new LaunchPlan(command,game,id,base,"Fabric",Path.of("java"),21,cache);
        Path sentinel=game.resolve("mods/user.jar");write(sentinel,"keep user data".getBytes());
        try {
            write(metadata,profile.getBytes());
            write(file,HardeningSelfTest.jar(Map.of("valid.zip.but.no.KnotClient",new byte[]{1}),null));
            var repaired=new ArrayList<>(command);FabricLaunchSupport.verify(plan,repaired,null);
            check(requests.get()==1,"A valid JAR missing KnotClient is repaired using cached Maven coordinates");
            check(Arrays.equals(loader,Files.readAllBytes(file)),"Exact pinned loader downloaded");
            check(repaired.get(2).contains("retained-client.jar")&&repaired.get(2).contains(file.toString()),"Classpath switch variants retained and required library added");
            check(!repaired.get(2).contains("never"),"OS-excluded library is ignored");
            FabricLaunchSupport.verify(plan,new ArrayList<>(command),null);
            check(requests.get()==1,"Cached valid launch performs zero downloads");
            String invalid="{\"name\":\"example:bad:1\",\"downloads\":{\"artifact\":{\"path\":\"../mods/user.jar\"}}}";
            Files.delete(file);write(metadata,(prefix+lib+","+invalid+"]}").getBytes());
            rejects(plan,"escapes its folder");check(requests.get()==1,"All paths checked before any repair starts");
            write(metadata,profile.replace("\"inheritsFrom\":\"26.1\"","\"inheritsFrom\":\"26.1.2\"").getBytes());
            rejects(plan,"selected version");check(requests.get()==1,"Wrong base blocked before downloading");
            write(metadata,(prefix+skipped+"]}").getBytes());rejects(plan,"does not declare Fabric Loader");
            write(metadata,profile.getBytes());
            var noEntry=new LaunchPlan(List.of("java","-cp","client.jar","wrong.Main"),game,id,base,"Fabric",Path.of("java"),21,cache);
            rejects(noEntry,"entry point");
            status.set(503);byte[] corrupt="retain until download verified".getBytes();write(file,corrupt);
            rejects(plan,"HTTP 503");check(requests.get()==4,"Failed download is retried within the existing bound");
            check(Arrays.equals(corrupt,Files.readAllBytes(file)),"Failed repair keeps the old file until a verified replacement exists");
            try(var files=Files.list(file.getParent())){check(files.noneMatch(p->p.getFileName().toString().endsWith(".part")),"No partial download left behind");}
            check(Files.readString(sentinel).equals("keep user data"),"Mods untouched after successful and failed repair");
            check(!Files.exists(game.resolve("libraries")),"No library copies or links created inside the profile");
            System.out.println("FABRIC PREFLIGHT TEST: PASS ("+checks+" assertions; synthetic fixtures)");
        } finally {
            server.stop(0);
            try(var walk=Files.walk(root)){for(Path p:walk.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
}
