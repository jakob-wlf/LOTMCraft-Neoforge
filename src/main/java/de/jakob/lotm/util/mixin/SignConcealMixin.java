package de.jakob.lotm.util.mixin;

import de.jakob.lotm.beyonders.abilities.darkness.ConcealmentAuthorityAbility;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(SignBlockEntity.class)
public class SignConcealMixin {
    @ModifyVariable(method = "setText", at = @At("HEAD"), argsOnly = true)
    private SignText lotm$concealName(SignText text) {
        return ConcealmentAuthorityAbility.censorSign((SignBlockEntity) (Object) this, text);
    }
}
