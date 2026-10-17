package gg.kapkan.presence;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import java.nio.file.Path;
import java.util.*;

public final class BadgeClient implements ClientModInitializer {
    public static PresenceEngine engine;
    private static Object connection;
    private static String context;
    private static String connectionKey;
    private static int ticks;
    @Override public void onInitializeClient(){
        Path config=Path.of(System.getProperty("kapkan.presence.config",Path.of(System.getProperty("user.home"),".kapkan","presence.properties").toString()));
        engine=new PresenceEngine(config,true);engine.start();
        gg.kapkan.presence.relay.RelayClient.initialize();
        Runtime.getRuntime().addShutdownHook(new Thread(()->engine.close(),"kapkan-presence-shutdown"));
    }
    public static void tick(Minecraft mc){
        if(engine==null)return;
        var current=mc.getConnection();
        if(mc.player==null||mc.level==null||current==null){connection=null;engine.snapshot(null);gg.kapkan.presence.relay.RelayClient.reset();return;}
        if(current!=connection){
            connection=current;context="";
            // An integrated single-player server has no multiplayer ServerData.
            if(mc.getCurrentServer()!=null){ServerAddress address=ServerAddress.parseString(mc.getCurrentServer().ip);context=PresenceEngine.context(address.getHost(),address.getPort());}
            connectionKey=UUID.randomUUID().toString();ticks=0;
        }
        gg.kapkan.presence.relay.RelayClient.tick(mc);
        if(ticks++%20!=0)return;
        var self=new PresenceEngine.Player(mc.player.getUUID(),mc.player.getGameProfile().name());
        var players=new ArrayList<PresenceEngine.Player>();players.add(self);
        var added=new HashSet<UUID>();added.add(self.uuid());
        mc.level.players().stream().filter(p->p!=mc.player).sorted(Comparator.comparingDouble(p->p.distanceToSqr(mc.player))).limit(64).forEach(p->{players.add(new PresenceEngine.Player(p.getUUID(),p.getGameProfile().name()));added.add(p.getUUID());});
        for(var info:current.getOnlinePlayers()){
            if(players.size()>=256)break;var p=info.getProfile();if(added.add(p.id()))players.add(new PresenceEngine.Player(p.id(),p.name()));
        }
        engine.snapshot(new PresenceEngine.Snapshot(connectionKey+":"+self.uuid(),context,self,players));
    }
    public static Component decorate(Component original,UUID uuid){
        if(original==null||engine==null)return original;Minecraft mc=Minecraft.getInstance();boolean self=mc.player!=null&&mc.player.getUUID().equals(uuid);
        return engine.visible(uuid,self)?BadgeText.prepend(original):original;
    }
    public static boolean selfNameAllowed(Entity entity,double distanceSq){
        Minecraft mc=Minecraft.getInstance();
        if(engine==null||entity!=mc.player||mc.player==null)return false;
        Team team=entity.getTeam();
        boolean teamAllows=team==null||team.getNameTagVisibility()==Team.Visibility.ALWAYS||team.getNameTagVisibility()==Team.Visibility.HIDE_FOR_OTHER_TEAMS;
        return BadgeVisibility.self(engine.visible(entity.getUUID(),true),mc.options.getCameraType().isFirstPerson(),Minecraft.renderNames(),mc.options.hideGui,entity.isInvisible(),entity.isSpectator(),entity.isVehicle(),entity.isDiscrete(),distanceSq,teamAllows);
    }
}
