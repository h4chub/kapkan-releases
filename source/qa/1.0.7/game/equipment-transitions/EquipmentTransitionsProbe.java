package gg.kapkan.hudqa;

import gg.kapkan.presence.cosmetics.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
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

/** QA ONLY: real inventory/cache transitions and empty gameplay/editor behavior. */
@Mixin(Minecraft.class)
public abstract class EquipmentTransitionsProbe {
    private static boolean creating,requested,done;
    private static volatile int applied=-1;
    private static int ticks,stage,seconds,checks;
    private static HudRenderer.Widget retainedMace;
    private static final List<String> results=new ArrayList<>();
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND};
    private static void require(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    private static ItemStack mace(){ItemStack stack=new ItemStack(Items.MACE);stack.setDamageValue(2);return stack;}
    private static ItemStack shield(){ItemStack stack=new ItemStack(Items.SHIELD);stack.setDamageValue(stack.getMaxDamage()/2);return stack;}
    private static HudRenderer.Widget widget(){return HudRenderer.widgets().stream().filter(w->w.id().equals("armor")).findFirst().orElseThrow();}
    private static void apply(Minecraft mc,int phase){
        requested=true;seconds=0;
        var server=mc.getSingleplayerServer();
        server.execute(()->{
            try{
                var player=server.getPlayerList().getPlayers().getFirst();
                for(var slot:SLOTS)player.setItemSlot(slot,ItemStack.EMPTY);
                if(phase==0){
                    var level=server.overworld();level.getChunk(0,0);int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,0,0)+4;
                    var source=server.createCommandSourceStack().withSuppressedOutput();
                    for(String command:List.of("time set day","weather clear","fill -20 "+(y-1)+" -10 20 "+(y+12)+" 10 minecraft:air","fill -20 "+(y-1)+" -10 20 "+(y-1)+" 10 minecraft:stone","fill -20 "+y+" -8 20 "+(y+12)+" -8 minecraft:black_concrete","tp QA_Bench 0.5 "+y+" 0.5 180 0"))server.getCommands().performPrefixedCommand(source,command);
                    player.setItemSlot(EquipmentSlot.MAINHAND,mace());
                }else if(phase==1){
                    player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE));player.setItemSlot(EquipmentSlot.OFFHAND,shield());
                }else if(phase==2){
                    player.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.CARVED_PUMPKIN));
                    ItemStack chest=new ItemStack(Items.DIAMOND_CHESTPLATE);chest.set(DataComponents.UNBREAKABLE,Unit.INSTANCE);player.setItemSlot(EquipmentSlot.CHEST,chest);
                    player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.STONE));player.setItemSlot(EquipmentSlot.OFFHAND,shield());
                }
                player.inventoryMenu.broadcastChanges();applied=phase;
            }catch(Throwable t){mc.execute(()->fail(mc,t));}
        });
    }
    @Inject(method="tick",at=@At("TAIL"))
    private void hudQa$transition(CallbackInfo ci){
        if(done||++ticks%20!=0)return;Minecraft mc=(Minecraft)(Object)this;
        try{
            if(mc.screen instanceof IntroductionScreen){mc.screen.onClose();return;}
            if(mc.player==null){
                if(!creating&&mc.screen instanceof TitleScreen&&mc.getOverlay()==null){creating=true;mc.createWorldOpenFlows().createFreshLevel("KapKan private HUD transition QA",new LevelSettings("KapKan private HUD transition QA",GameType.CREATIVE,new LevelSettings.DifficultySettings(Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(1234L,false,false),WorldPresets::createFlatWorldDimensions,mc.screen);}
                return;
            }
            if(mc.level==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
            if(!requested){mc.setScreen(null);mc.mouseHandler.releaseMouse();apply(mc,0);return;}
            if(stage<4&&applied!=stage)return;
            mc.mouseHandler.releaseMouse();if(++seconds<4)return;
            var w=widget();
            if(stage==0){
                for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})require(mc.player.getItemBySlot(slot).isEmpty(),"Armor slot really empty "+slot);
                require(mc.player.getOffhandItem().isEmpty(),"Offhand empty for mace-only case");
                require(w.items().size()==1&&w.items().getFirst().is(Items.MACE),"Armor-empty widget has only held mace");
                require(w.lines().equals(List.of("498/500")),"Mace-only exact live remaining/max");
                retainedMace=w;record(mc,"mace-only",w);stage=1;apply(mc,1);return;
            }
            if(stage==1){
                require(mc.player.getMainHandItem().is(Items.STONE),"Actual held block swap reached client");
                require(w.items().size()==1&&w.items().getFirst().is(Items.SHIELD),"Nondurable main-hand block filtered, shield remains");
                require(w.lines().equals(List.of("168/336")),"Offhand shield durability survives held swap");
                require(retainedMace.items().getFirst().is(Items.MACE)&&retainedMace.items().getFirst().getDamageValue()==2,"Previous live widget snapshot remains immutable after inventory swap");
                record(mc,"held-block-filtered-shield-remains",w);stage=2;apply(mc,2);return;
            }
            if(stage==2){
                require(w.items().size()==3&&w.items().get(0).is(Items.CARVED_PUMPKIN)&&w.items().get(1).is(Items.DIAMOND_CHESTPLATE)&&w.items().get(2).is(Items.SHIELD),"Nondurable armor and unbreakable armor retain occupied order");
                require(w.items().get(0).getMaxDamage()==0&&w.lines().get(0).isEmpty(),"Max-zero pumpkin is icon-only");
                require(!w.items().get(1).isDamageableItem()&&w.items().get(1).getMaxDamage()>0&&w.lines().get(1).equals("∞"),"Unbreakable max-positive armor shows infinity");
                require(w.lines().get(2).equals("168/336"),"Shield remains with special armor states");
                record(mc,"pumpkin-and-unbreakable",w);stage=3;apply(mc,3);return;
            }
            if(stage==3){
                for(var slot:SLOTS)require(mc.player.getItemBySlot(slot).isEmpty(),"All-empty actual inventory "+slot);
                require(w.items().isEmpty()&&w.lines().isEmpty(),"All-empty live widget has no item rows or numeric/hint lines");
                record(mc,"all-empty-gameplay",w);Screenshot.grab(mc.gameDirectory,"KapKan-equipment-all-empty.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));
                stage=4;seconds=0;return;
            }
            if(stage==4){
                require(w.items().isEmpty()&&w.lines().isEmpty(),"All-empty gameplay remains empty after cache refresh");
                mc.setScreen(new HudEditorScreen(null));stage=5;seconds=0;return;
            }
            if(stage==5){
                require(mc.screen instanceof HudEditorScreen,"Actual private HUD editor is open");
                require(w.items().isEmpty()&&w.lines().isEmpty(),"Editor hint is not injected into shared gameplay data");
                Screenshot.grab(mc.gameDirectory,"KapKan-equipment-editor-empty-hint.png",mc.getMainRenderTarget(),1,c->System.out.println(c.getString()));
                stage=6;seconds=0;return;
            }
            if(stage==6){
                Path folder=mc.gameDirectory.toPath().resolve("screenshots");
                require(Files.size(folder.resolve("KapKan-equipment-all-empty.png"))>1000,"All-empty screenshot saved");
                require(Files.size(folder.resolve("KapKan-equipment-editor-empty-hint.png"))>1000,"Editor hint screenshot saved");
                Files.writeString(mc.gameDirectory.toPath().resolve("hud-qa-pass.json"),"{\"status\":\"PASS\",\"checks\":"+checks+",\"scope\":\"actual equipment removal, swaps, cache snapshots, max-zero and unbreakable armor, empty gameplay/editor; visual review required\",\"performanceMeasured\":false}\n");
                done=true;mc.stop();
            }
        }catch(Throwable t){fail(mc,t);}
    }
    private static void record(Minecraft mc,String label,HudRenderer.Widget w)throws Exception{
        String value=label+" | items="+w.items().stream().map(s->s.getItem().toString()).toList()+" | lines="+w.lines();results.add(value);
        Files.write(mc.gameDirectory.toPath().resolve("transition-results.txt"),results);System.out.println("HUD_TRANSITION_PASS "+value);
    }
    private static void fail(Minecraft mc,Throwable t){done=true;t.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("hud-qa-failed.txt"),t.toString());}catch(Exception ignored){}mc.stop();}
}
