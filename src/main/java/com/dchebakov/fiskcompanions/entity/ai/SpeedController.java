package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.speedster.SpeedsterHelper;

/**
 * Super speed for speedster suits. A player turns it on with a key and picks a speed level with the scroll wheel; Fisk
 * then raises the entity's movement speed from {@code Vars.SPEEDING}/{@code Vars.SPEED} for any entity (top speed =
 * level x 50). The mob runs at the suit's top level towards a far target or owner, slows down at medium range and
 * switches it off for close combat.
 */
public class SpeedController {

    private static final double FULL_SPEED_DIST = 12.0D;
    private static final double SPEED_DIST = 4.0D;

    private final EntityFiskHero hero;

    public SpeedController(EntityFiskHero hero) {
        this.hero = hero;
    }

    public boolean isSpeeding() {
        return Vars.SPEEDING.get(hero);
    }

    /** Highest speed level the suit allows (0 if it has no super speed). */
    public int maxLevel() {
        return FiskBridge.enabled(hero, Modifier.SUPER_SPEED) != null ? SpeedsterHelper.getMaxSpeedSetting(hero) : 0;
    }

    public void update() {
        int max = maxLevel();
        Entity goal = goal();
        if (max <= 0 || goal == null
            || hero.getFlight()
                .isFlying()) {
            set(false, 0);
            return;
        }
        double dist = hero.getDistanceToEntity(goal);
        if (dist > FULL_SPEED_DIST) {
            set(true, max);
        } else if (dist > SPEED_DIST) {
            set(true, Math.max(1, max / 2));
        } else {
            set(false, 0);
        }
    }

    private Entity goal() {
        EntityLivingBase target = hero.getAttackTarget();
        if (target != null && target.isEntityAlive()) {
            return target;
        }
        return hero.isSitting() ? null : hero.getOwner();
    }

    private void set(boolean speeding, int level) {
        if (speeding && Vars.SPEED.get(hero) != level) {
            Vars.SPEED.set(hero, (byte) level)
                .sync();
        }
        // Sprinting while speeding gives Fisk's speedster sprint pose (the flight controller manages it while flying).
        if (!hero.getFlight()
            .isFlying()) {
            hero.setSprinting(speeding && level > 1);
        }
        if (Vars.SPEEDING.get(hero) != speeding) {
            Vars.SPEEDING.set(hero, speeding)
                .sync();
            if (speeding) {
                hero.getPowers()
                    .record("super_speed");
            }
        }
    }
}
