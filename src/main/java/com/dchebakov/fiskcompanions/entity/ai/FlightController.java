package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.Hero;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;

/**
 * Flight for heroes whose suit can fly. Fisk's flight modifiers only steer players, so the mob drives its own motion,
 * as Fisk's {@code EntityIronMan} does. The flying state lives here: {@code Vars.FLYING} is only set for the pose and
 * sound, and Fisk resets it for heroes without controlled flight.
 */
public class FlightController {

    private static final double GRAVITY = 0.08D;
    private static final double MIN_SPEED_FACTOR = 0.4D;
    private static final double MAX_SPEED_FACTOR = 4.0D;
    /** Minimum ticks in the air before landing, and on the ground before taking off again. */
    private static final int MIN_AIR_TICKS = 60;
    private static final int MIN_GROUND_TICKS = 20;

    private final EntityFiskHero hero;
    private boolean flying;
    private int stateTicks;

    public FlightController(EntityFiskHero hero) {
        this.hero = hero;
    }

    public boolean canFly() {
        Hero h = FiskBridge.hero(hero);
        return h != null && (h.getModifiers()
            .contains(Modifier.FLIGHT)
            || h.getModifiers()
                .contains(Modifier.CONTROLLED_FLIGHT)
            || h.getModifiers()
                .contains(Modifier.PROPELLED_FLIGHT));
    }

    public boolean isFlying() {
        return flying;
    }

    public void update() {
        ++stateTicks;
        if (!canFly()) {
            if (flying) setFlying(false);
            return;
        }
        Entity goal = hero.getAttackTarget();
        double yOffset;
        double stopDist;
        if (goal != null && goal.isEntityAlive()) {
            yOffset = 0.0D; // feet level with the target's: aiming higher makes two fliers out-climb each other forever
            stopDist = 1.5D;
        } else {
            goal = hero.isSitting() ? null : hero.getOwner();
            yOffset = 2.0D; // hover above the owner's shoulder
            stopDist = 3.0D;
        }
        if (goal == null) {
            if (flying && stateTicks > MIN_AIR_TICKS) setFlying(false);
            return;
        }

        double dx = goal.posX - hero.posX;
        double dy = goal.boundingBox.minY + yOffset - hero.posY;
        double dz = goal.posZ - hero.posZ;
        double horiz = MathHelper.sqrt_double(dx * dx + dz * dz);
        boolean goalAirborne = !goal.onGround && !goal.isInWater()
            && !(goal instanceof EntityLivingBase && ((EntityLivingBase) goal).isOnLadder());
        boolean wantFly = goalAirborne || dy > 2.5D || horiz > 14.0D;

        if (!flying) {
            if (wantFly && stateTicks > MIN_GROUND_TICKS) {
                setFlying(true);
                hero.motionY = 0.5D;
                hero.getPowers()
                    .record("flight");
            }
            return;
        }
        if (!wantFly && stateTicks > MIN_AIR_TICKS && horiz < stopDist + 2.0D) {
            setFlying(false); // drop the last block or two; fall damage is reduced in EntityFiskHero.fall
            return;
        }
        if (stateTicks % 20 == 0 && !Vars.FLYING.get(hero)) {
            Vars.FLYING.set(hero, true)
                .sync(); // re-assert the pose if Fisk reset it
        }

        hero.getNavigator()
            .clearPathEntity();
        hero.fallDistance = 0.0F;
        double dist = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        if (dist > stopDist) {
            boolean boosting = dist > 6.0D;
            double speed = (hero.getAttackTarget() != null ? 0.55D : 0.45D) * speedFactor(boosting);
            hero.motionX = hero.motionX * 0.6D + dx / dist * speed * 0.4D;
            hero.motionY = hero.motionY * 0.6D + dy / dist * speed * 0.4D + GRAVITY;
            hero.motionZ = hero.motionZ * 0.6D + dz / dist * speed * 0.4D;
            hero.rotationYaw = hero.renderYawOffset = hero.rotationYawHead = (float) (Math.atan2(dz, dx) * 180.0D
                / Math.PI) - 90.0F;
            hero.rotationPitch = (float) -(Math.atan2(dy, MathHelper.sqrt_double(dx * dx + dz * dz)) * 180.0D
                / Math.PI);
            // Sprinting while flying is Fisk's "boost": suits with a boost pose fly horizontally, arm out.
            hero.setSprinting(dist > 6.0D);
        } else {
            hero.setSprinting(false);
            hero.motionX *= 0.5D;
            hero.motionY = GRAVITY * 0.5D;
            hero.motionZ *= 0.5D;
        }
    }

    /**
     * The suit's own flight speed relative to Fisk's defaults: {@code speed} (default 0.1) while cruising and, for
     * controlled flight, {@code boostSpeed} (default 0.2) when boosting; plain flight boosts by 1.2x like a sprinting
     * player. So fast suits fly faster and slow ones slower.
     */
    public double speedFactor(boolean boosting) {
        ModifierEntry entry = FiskBridge.enabled(hero, Modifier.CONTROLLED_FLIGHT);
        if (entry == null) entry = FiskBridge.enabled(hero, Modifier.FLIGHT);
        if (entry == null) entry = FiskBridge.enabled(hero, Modifier.PROPELLED_FLIGHT);
        if (entry == null) {
            // Not enabled by the hero's script right now (e.g. Anti-Monitor): use the suit's declared flight settings.
            entry = FiskBridge.declared(hero, Modifier.CONTROLLED_FLIGHT, Modifier.FLIGHT, Modifier.PROPELLED_FLIGHT);
        }
        if (entry == null) {
            return 1.0D;
        }
        float speed = FiskBridge.prop(entry, PowerProperty.SPEED, 0.1F);
        double factor;
        if (!boosting) {
            factor = speed / 0.1D;
        } else if (entry.modifier == Modifier.CONTROLLED_FLIGHT) {
            factor = FiskBridge.prop(entry, PowerProperty.BOOST_SPEED, speed * 2.0F) / 0.2D;
        } else {
            factor = speed / 0.1D * 1.2D;
        }
        return Math.max(MIN_SPEED_FACTOR, Math.min(MAX_SPEED_FACTOR, factor));
    }

    private void setFlying(boolean value) {
        flying = value;
        stateTicks = 0;
        if (!value) {
            hero.setSprinting(false);
        }
        Vars.FLYING.set(hero, value)
            .sync();
    }
}
