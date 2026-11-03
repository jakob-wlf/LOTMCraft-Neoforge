package de.jakob.lotm.beyonders.abilities.red_priest;

import de.jakob.lotm.attachments.ModAttachments;
import de.jakob.lotm.beyonders.abilities.core.SelectableAbility;
import de.jakob.lotm.entity.custom.projectiles.FireballEntity;
import de.jakob.lotm.util.BeyonderData;
import de.jakob.lotm.util.data.PriestFlameVariants;
import de.jakob.lotm.util.helper.AbilityUtil;
import de.jakob.lotm.util.helper.VectorUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.ArrayUtils;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class CompressionAbility extends SelectableAbility {

    public static String CHARGES_TAG = "COMPRESSION_CHARGES";
    public static int[] LIMITS = new int[]{3,6,9,12};
    public CompressionAbility(String id) {
        super(id, 1.0f, "burning");
        hideHoldBar(0);

        baseDamage = 12f;
        hasDynamicSpirituality = true;
        dynamicSpirituality = new LinkedList<>(List.of(1600f, 600f, 500f, 200f, 200f, 120f, 70f, 60f));
        isHoldable(10000, 0);
    }

    @Override
    public void onHolding(ServerLevel level, LivingEntity entity, int subIndex, int ticks, int holdTime) {
        int Seq = BeyonderData.getSequence(entity);
        int interval = switch (Seq) {
            case 0,1,2 -> 10;
            case 3,4 -> 20;
            case 5 -> 30;
            case 7,6 -> 40;
            default -> 50;
        };

        if(ticks%interval==0){
            if(ticks >= holdTime) return;
            int MAX = (Seq <= 1) ? LIMITS[3] : (Seq <= 3) ? LIMITS[2] : (Seq <= 5) ? LIMITS[1] : LIMITS[0];
            if(getCharges(entity) >= MAX) return;
            compress(entity.level(), entity);
        }
    }

    @Override
    public void onRelease(ServerLevel level, LivingEntity entity, int subIndex, int ticks, int holdTime) {
        fireball(entity.level(), entity);
        if(ticks <= 20) {
            entity.getData(ModAttachments.COOLDOWN_COMPONENT.get()).setCooldown(this.getId(), 40);
        }
    }

    @Override
    protected String[] getAllAbilityNames() {
        return new String[]{
                "ability.lotmcraft.pyrokinesis.release",
        };
    }

    @Override
    protected void castSelectedAbility(Level level, LivingEntity entity, int selectedAbility) {
    }

    @Override
    public Map<String, Integer> getRequirements() {
        return new HashMap<>(Map.of(
                "red_priest", 7
        ));
    }

    @Override
    protected float getSpiritualityCost() {
        return 0;
    }

    @Override
    public void onHold(Level level, LivingEntity entity) {
        if(!(entity instanceof ServerPlayer player)) return;
        int CHARGES =  getCharges(player);
        int Seq = BeyonderData.getSequence(entity);
        int MAX = (Seq <= 1) ? LIMITS[3] : (Seq <= 3) ? LIMITS[2] : (Seq <= 5) ? LIMITS[1] : LIMITS[0];
        if(BeyonderData.getSequence(entity) <= 2 && CHARGES<3) CHARGES=setCharges(entity, 3);
        else if(BeyonderData.getSequence(entity) <= 6 && CHARGES<1) CHARGES=setCharges(entity, 1);
        MutableComponent text = Component.translatable("lotm.charges").append(": " + CHARGES).withStyle(ChatFormatting.DARK_RED);
        if( CHARGES >= MAX ) text.append(" - MAX - ").withStyle(ChatFormatting.DARK_RED);
        player.displayClientMessage(text, true);
    }

    public void compress(Level level, LivingEntity entity) {
        if(level.isClientSide) return;
        int Seq = BeyonderData.getSequence(entity);
        int MAX = (Seq <= 1) ? LIMITS[3] : (Seq <= 3) ? LIMITS[2] : (Seq <= 5) ? LIMITS[1] : LIMITS[0];
        Vec3 startPos = VectorUtil.getRelativePosition(entity.getEyePosition().add(entity.getLookAngle().normalize()), entity.getLookAngle().normalize(), 0, random.nextDouble(1, 2.85f), random.nextDouble(-.1, .6));

        level.playSound(null, startPos.x,startPos.y,startPos.z, SoundEvents.BLAZE_HURT, entity.getSoundSource(), 1.0f, 1.0f);
        int CHARGES = getCharges(entity);
        if(CHARGES >= MAX) {
            entity.sendSystemMessage(Component.literal("You have the maximum amount of charges").withStyle(ChatFormatting.RED));
            return;
        }
        CHARGES = setCharges(entity, CHARGES+1);
        if(CHARGES == 3) entity.sendSystemMessage(Component.literal("You have unlocked the giant fireball").withStyle(ChatFormatting.YELLOW));
        if(CHARGES == 6) entity.sendSystemMessage(Component.literal("Giant fireball have been enhanced").withStyle(ChatFormatting.GOLD));
        if(CHARGES == 9) entity.sendSystemMessage(Component.literal("Giant fireball is getting greater").withStyle(ChatFormatting.RED));
        if(CHARGES == 12) entity.sendSystemMessage(Component.literal("Giant fireball has reached its max potential").withStyle(ChatFormatting.DARK_RED));
        BeyonderData.reduceSpirituality(entity, dynamicSpirituality.get(Seq)*0.60f);

    }

    private void fireball(Level level, LivingEntity entity) {
        if(level.isClientSide)
            return;
        int Seq = BeyonderData.getSequence(entity);
        int CHARGES =  getCharges(entity);
        if( CHARGES == 0 ) {
            entity.sendSystemMessage(Component.literal("You have no charges").withStyle(ChatFormatting.RED));
            return;
        }
        Vec3 startPos = VectorUtil.getRelativePosition(entity.getEyePosition().add(entity.getLookAngle().normalize()), entity.getLookAngle().normalize(), 0, random.nextDouble(1, 2.85f), random.nextDouble(-.1, .6));
        Vec3 direction = AbilityUtil.getTargetLocation(entity, (int) (50 * multiplier(entity)), 1.4f).subtract(startPos).normalize();
        level.playSound(null, startPos.x, startPos.y, startPos.z, SoundEvents.BLAZE_SHOOT, entity.getSoundSource(), 1.0f, 1.0f);

        if(!ArrayUtils.contains(LIMITS, CHARGES)) {
            FireballEntity fireball = new FireballEntity(level, entity, baseDamage, BeyonderData.isGriefingEnabled(entity));
            fireball.setPos(startPos.x, startPos.y, startPos.z); // Set initial position
            fireball.setVariant(PriestFlameVariants.byId(BeyonderData.getSequence(entity)));
            fireball.shoot(direction.x, direction.y, direction.z, 1.85f*multiplier(entity), 0);
            level.addFreshEntity(fireball);
            setCharges(entity, CHARGES-1);
        } else {
            int Scale = ArrayUtils.indexOf(LIMITS, CHARGES) + 1;
            FireballEntity fireball = new FireballEntity(level, entity, baseDamage*Scale * 1.25f, BeyonderData.isGriefingEnabled(entity), Scale*5.5f);
            fireball.setPos(startPos.x, startPos.y, startPos.z); // Set initial position
            fireball.setVariant(PriestFlameVariants.byId(BeyonderData.getSequence(entity)));

            fireball.shoot(direction.x, direction.y, direction.z, 2.35f * multiplier(entity), 0);
            level.addFreshEntity(fireball);
            setCharges(entity, 0);
        }

        BeyonderData.reduceSpirituality(entity, dynamicSpirituality.get(Seq));

    }

    private static int getCharges(LivingEntity entity) {
        if(!entity.getPersistentData().contains(CHARGES_TAG)) {
            entity.getPersistentData().putInt(CHARGES_TAG, 0);
        }
        return entity.getPersistentData().getInt(CHARGES_TAG);
    }
    private static int setCharges(LivingEntity entity, int charges) {
        entity.getPersistentData().putInt(CHARGES_TAG, charges);
        return getCharges(entity);
    }
}
