package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.boss.IBossDisplayData;

/**
 * Bosses (the Ender Dragon, the Wither) circle far and fast. With the normal 32-block follow range a character drops a
 * boss as soon as it swoops away and, if owned, only finds it again when it comes within the guard radius. Every target
 * task keeps a boss target up to {@link #RANGE} blocks away.
 */
final class BossRange {

    static final double RANGE = 96.0D;

    private BossRange() {}

    static boolean isBoss(Entity entity) {
        return entity instanceof IBossDisplayData;
    }

    /** Target distance for the task's current target: {@link #RANGE} for a boss, else {@code normal}. */
    static double of(EntityCreature owner, double normal) {
        return isBoss(owner.getAttackTarget()) ? Math.max(normal, RANGE) : normal;
    }
}
