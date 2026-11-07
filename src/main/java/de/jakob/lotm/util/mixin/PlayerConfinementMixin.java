package de.jakob.lotm.util.mixin;

import de.jakob.lotm.beyonders.abilities.justiciar.ConfinementAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class PlayerConfinementMixin {
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",at=@At("HEAD"),cancellable=true)
    private void lotm$confinementTeleport(ServerLevel level,double x,double y,double z,Set<RelativeMovement> relative,float yaw,float pitch,CallbackInfoReturnable<Boolean> cir) {
        if (!ConfinementAbility.canTeleport((ServerPlayer)(Object)this,level,new Vec3(x,y,z))) cir.setReturnValue(false);
    }
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",at=@At("HEAD"),cancellable=true)
    private void lotm$confinementTeleportSimple(ServerLevel level,double x,double y,double z,float yaw,float pitch,CallbackInfo ci) {
        if (!ConfinementAbility.canTeleport((ServerPlayer)(Object)this,level,new Vec3(x,y,z))) ci.cancel();
    }
    @Inject(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),cancellable=true)
    private void lotm$confinementDimension(DimensionTransition transition,CallbackInfoReturnable<Entity> cir) {
        if (!ConfinementAbility.canTeleport((ServerPlayer)(Object)this,transition.newLevel(),transition.pos())) cir.setReturnValue(null);
    }
}
