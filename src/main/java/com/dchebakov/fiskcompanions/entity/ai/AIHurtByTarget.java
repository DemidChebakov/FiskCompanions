package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIHurtByTarget;

import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

/**
 * Vanilla revenge targeting, but never against someone on the same side. Vanilla's {@code isSuitableTarget} doesn't
 * check {@code isOnSameTeam} in 1.7.10, so without this a hero hit by a player would attack them even with "fight back
 * players" off. Wanted retaliation (the friendly-fire settings) goes through the grudge in
 * {@link EntityFiskHero#attackEntityFrom}, which makes {@code isOnSameTeam} false.
 */
public class AIHurtByTarget extends EntityAIHurtByTarget {

    private final EntityFiskHero hero;

    public AIHurtByTarget(EntityFiskHero hero) {
        super(hero, false);
        this.hero = hero;
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase attacker = hero.getAITarget();
        return attacker != null && !hero.isOnSameTeam(attacker) && super.shouldExecute();
    }

    /** A boss is followed further than the normal follow range (see {@link BossRange}). */
    @Override
    protected double getTargetDistance() {
        return BossRange.of(taskOwner, super.getTargetDistance());
    }
}
