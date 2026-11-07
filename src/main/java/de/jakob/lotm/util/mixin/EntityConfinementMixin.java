package de.jakob.lotm.util.mixin;

import de.jakob.lotm.beyonders.abilities.justiciar.ConfinementAbility;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.Set;

@Mixin(Entity.class)
public abstract class EntityConfinementMixin {
    @Inject(method="teleportTo(DDD)V",at=@At("HEAD"),cancellable=true)
    private void lotm$confinementLocal(double x,double y,double z,CallbackInfo ci) {
        if ((Object)this instanceof LivingEntity entity && entity.level() instanceof ServerLevel level
                && !ConfinementAbility.canTeleport(entity,level,new Vec3(x,y,z))) ci.cancel();
    }
    @Inject(method="teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FF)Z",at=@At("HEAD"),cancellable=true)
    private void lotm$confinementWorld(ServerLevel level,double x,double y,double z,Set<RelativeMovement> relative,float yaw,float pitch,CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof LivingEntity entity && !ConfinementAbility.canTeleport(entity,level,new Vec3(x,y,z))) cir.setReturnValue(false);
    }
    @Inject(method="changeDimension(Lnet/minecraft/world/level/portal/DimensionTransition;)Lnet/minecraft/world/entity/Entity;",at=@At("HEAD"),cancellable=true)
    private void lotm$confinementDimension(DimensionTransition transition,CallbackInfoReturnable<Entity> cir) {
        if ((Object)this instanceof LivingEntity entity && !ConfinementAbility.canTeleport(entity,transition.newLevel(),transition.pos())) cir.setReturnValue(null);
    }
}
