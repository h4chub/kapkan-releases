package gg.kapkan.hudqa;

import gg.kapkan.presence.cosmetics.ClientFeatures;
import gg.kapkan.presence.cosmetics.HudRenderer;
import gg.kapkan.presence.cosmetics.IntroductionScreen;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** QA ONLY: one fresh private world, real equipment and real HUD on bright/dark walls. */
@Mixin(Minecraft.class)
public abstract class EquipmentHudProbe {
    private static boolean creating,done,setupRequested;
    private static volatile boolean equipped;
    private static int ticks,stage,seconds,checks;
    private static volatile int standY;
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND};
    private static final Item[] ITEMS={Items.DIAMOND_HELMET,Items.DIAMOND_CHESTPLATE,Items.DIAMOND_LEGGINGS,Items.DIAMOND_BOOTS,Items.MACE,Items.SHIELD};
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
    private static ItemStack item(Item item,int mode){
        ItemStack result=new ItemStack(item);int max=result.getMaxDamage();
        result.setDamageValue(switch(mode){case 0->0;case 1->max/2;case 2->max-1;case 3->max*3/4;case 4->2;default->max/2;});
        return result;
    }
    @Inject(method="tick",at=@At("TAIL"))
    private void hudQa$tick(CallbackInfo ci){
        if(done||++ticks%20!=0)return;
        Minecraft mc=(Minecraft)(Object)this;
        try{
            if(mc.screen instanceof IntroductionScreen){mc.screen.onClose();return;}
            if(mc.player==null){
                if(!creating&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){
                    creating=true;
                    mc.createWorldOpenFlows().createFreshLevel("KapKan private equipment HUD QA",new LevelSettings("KapKan private equipment HUD QA",GameType.CREATIVE,
                        new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1234L,false,false),WorldPresets::createFlatWorldDimensions,mc.screen);
                }
                return;
            }
            if(mc.level==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
            if(!setupRequested){
                setupRequested=true;mc.setScreen(null);mc.mouseHandler.releaseMouse();
                var server=mc.getSingleplayerServer();
                server.execute(()->{
                    try{
                        var level=server.overworld();level.getChunk(0,0);
                        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0)+4;standY=y;
                        var source=server.createCommandSourceStack().withSuppressedOutput();
                        var commands=List.of("time set day","weather clear",
                            "fill -20 "+(y-1)+" -10 20 "+(y+12)+" 10 minecraft:air",
                            "fill -20 "+(y-1)+" -10 20 "+(y-1)+" 10 minecraft:stone",
                            "fill -20 "+y+" -8 20 "+(y+12)+" -8 minecraft:white_concrete",
                            "fill -20 "+y+" 8 20 "+(y+12)+" 8 minecraft:black_concrete",
                            "tp QA_Bench 0.5 "+y+" 0.5 180 0");
                        for(String command:commands)server.getCommands().performPrefixedCommand(source,command);
                        var player=server.getPlayerList().getPlayers().getFirst();
                        for(int i=0;i<SLOTS.length;i++)player.setItemSlot(SLOTS[i],item(ITEMS[i],i));
                        player.inventoryMenu.broadcastChanges();equipped=true;
                    }catch(Throwable t){mc.execute(()->fail(mc,t));}
                });
                return;
            }
            if(!equipped||mc.screen!=null)return;
            mc.mouseHandler.releaseMouse();
            if(++seconds<4)return;
            checkEquipment(mc);
            if(stage==0){
                pose(mc,-180.0f);stage=1;seconds=0;return;
            }
            if(stage==1){
                saveState(mc,"bright");Screenshot.grab(mc.gameDirectory,"KapKan-equipment-bright.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));
                var server=mc.getSingleplayerServer();server.execute(()->server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),"tp QA_Bench 0.5 "+standY+" 0.5 0 0"));
                pose(mc,0);stage=2;seconds=0;return;
            }
            if(stage==2){pose(mc,0);stage=3;seconds=0;return;}
            if(stage==3){
                saveState(mc,"dark");Screenshot.grab(mc.gameDirectory,"KapKan-equipment-dark.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));
                stage=4;seconds=0;return;
            }
            if(stage==4){
                Path screenshots=mc.gameDirectory.toPath().resolve("screenshots");
                require(Files.size(screenshots.resolve("KapKan-equipment-bright.png"))>1000,"Bright screenshot completed");
                require(Files.size(screenshots.resolve("KapKan-equipment-dark.png"))>1000,"Dark screenshot completed");
                Files.writeString(mc.gameDirectory.toPath().resolve("hud-qa-pass.json"),"{\"status\":\"PASS\",\"checks\":"+checks+",\"minecraft\":\""+System.getProperty("kapkan.qa.version")+"\",\"scope\":\"actual private-world equipment and HUD, bright/dark backgrounds; visual review required\",\"mainHand\":\"mace498/500\",\"screenshots\":[\"KapKan-equipment-bright.png\",\"KapKan-equipment-dark.png\"]}\n");
                done=true;mc.stop();
            }
        }catch(Throwable t){fail(mc,t);}
    }
    private static void pose(Minecraft mc,float yaw){mc.player.setYRot(yaw);mc.player.yRotO=yaw;mc.player.setXRot(0);mc.player.xRotO=0;}
    private static void checkEquipment(Minecraft mc){
        require(ClientFeatures.settings().armor,"Existing armor setting enabled in private profile");
        for(int i=0;i<SLOTS.length;i++){
            ItemStack stack=mc.player.getItemBySlot(SLOTS[i]);require(stack.is(ITEMS[i]),"Synced inventory slot "+SLOTS[i]);
            require(stack.getMaxDamage()>0&&stack.getDamageValue()<stack.getMaxDamage(),"Valid damage bounds "+SLOTS[i]);
        }
        ItemStack mace=mc.player.getMainHandItem();require(mace.getMaxDamage()==500&&mace.getMaxDamage()-mace.getDamageValue()==498,"Actual main-hand mace498/500");
        var widget=HudRenderer.widgets().stream().filter(w->w.id().equals("armor")).findFirst().orElseThrow();
        require(widget.items().size()==6,"Actual HUD includes four armor rows then both damageable hands");
        require(widget.lines().size()==6,"Actual HUD has six numeric durability rows");
        for(int i=0;i<ITEMS.length;i++){
            require(widget.items().get(i).is(ITEMS[i]),"HUD slot order "+SLOTS[i]);
            ItemStack stack=mc.player.getItemBySlot(SLOTS[i]);String expected=(stack.getMaxDamage()-stack.getDamageValue())+"/"+stack.getMaxDamage();
            require(widget.lines().get(i).replace(" ","").equals(expected),"Live HUD remaining/max "+SLOTS[i]);
        }
        require(widget.lines().stream().noneMatch(s->s.contains("Head")||s.contains("Chest")||s.contains("Legs")||s.contains("Feet")||s.contains("empty")),"Gameplay labels omit slot names and empty hints");
    }
    private static void saveState(Minecraft mc,String name)throws Exception{
        var lines=new ArrayList<String>();
        for(int i=0;i<SLOTS.length;i++){ItemStack item=mc.player.getItemBySlot(SLOTS[i]);lines.add(SLOTS[i]+" "+item.getItem()+" "+(item.getMaxDamage()-item.getDamageValue())+"/"+item.getMaxDamage());}
        var widget=HudRenderer.widgets().stream().filter(w->w.id().equals("armor")).findFirst().orElseThrow();
        lines.add("HUD="+widget.lines());lines.add("widget="+widget.width()+"x"+widget.height());
        lines.add("camera="+mc.player.getX()+","+mc.player.getY()+","+mc.player.getZ()+","+mc.player.getYRot()+","+mc.player.getXRot());
        Files.write(mc.gameDirectory.toPath().resolve(name+"-equipment.txt"),lines);
    }
    private static void fail(Minecraft mc,Throwable t){done=true;t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("hud-qa-failed.txt"),t.toString());}catch(Exception ignored){}mc.stop();}
}
