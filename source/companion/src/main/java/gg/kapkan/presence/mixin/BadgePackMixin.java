package gg.kapkan.presence.mixin;
import gg.kapkan.presence.BadgeResources;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.*;
import java.util.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PackRepository.class)
public abstract class BadgePackMixin {
    @Shadow @Final private Set<RepositorySource> sources;
    @Inject(method="openAllSelected",at=@At("RETURN"),cancellable=true)
    private void kapkan$badgeResources(CallbackInfoReturnable<List<PackResources>> callback){
        if(sources.stream().noneMatch(source->source instanceof ClientPackSource))return;
        List<PackResources> packs=new ArrayList<>(callback.getReturnValue());
        if(packs.stream().noneMatch(pack->pack instanceof BadgeResources))packs.add(new BadgeResources());
        callback.setReturnValue(List.copyOf(packs));
    }
}
