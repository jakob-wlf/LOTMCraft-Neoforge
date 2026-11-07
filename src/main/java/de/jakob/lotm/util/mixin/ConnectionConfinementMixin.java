package de.jakob.lotm.util.mixin;

import de.jakob.lotm.beyonders.abilities.justiciar.ConfinementAbility;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ConnectionConfinementMixin {
    @Shadow public ServerPlayer player;
    @Inject(method="teleport(DDDFFLjava/util/Set;)V",at=@At("HEAD"),cancellable=true)
    private void lotm$confinementConnection(double x,double y,double z,float yaw,float pitch,Set<RelativeMovement> relative,CallbackInfo ci) {
        if (!ConfinementAbility.canTeleport(player,player.serverLevel(),new Vec3(x,y,z))) ci.cancel();
    }
}
