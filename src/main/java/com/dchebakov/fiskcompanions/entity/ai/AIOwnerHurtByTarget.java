package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.player.EntityPlayer;

import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

/** HERO: attacks whatever is hurting the owner, suited or not. */
public class AIOwnerHurtByTarget extends EntityAITarget {

    private final EntityFiskHero hero;
    private EntityLivingBase attacker;

    public AIOwnerHurtByTarget(EntityFiskHero hero) {
        super(hero, false);
        this.hero = hero;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        EntityPlayer owner = hero.getOwner();
        if (owner == null) {
            return false;
        }
        attacker = owner.getAITarget();
        return attacker != null && isSuitableTarget(attacker, false);
    }

    @Override
    public void startExecuting() {
        taskOwner.setAttackTarget(attacker);
        super.startExecuting();
    }

    /** A boss is followed further than the normal follow range (see {@link BossRange}). */
    @Override
    protected double getTargetDistance() {
        return BossRange.of(taskOwner, super.getTargetDistance());
    }
}
