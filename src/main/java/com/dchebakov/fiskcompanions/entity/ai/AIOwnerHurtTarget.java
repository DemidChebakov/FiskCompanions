package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.player.EntityPlayer;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.entity.OwnerHits;

/** HERO with a suited owner: fights together by joining whatever the owner attacks. */
public class AIOwnerHurtTarget extends EntityAITarget {

    private final EntityFiskHero hero;
    private EntityLivingBase target;

    public AIOwnerHurtTarget(EntityFiskHero hero) {
        super(hero, false);
        this.hero = hero;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        EntityPlayer owner = hero.getOwner();
        if (owner == null || !FiskBridge.isSuited(owner)) {
            return false;
        }
        target = owner.getLastAttacker();
        if (target == null) {
            target = OwnerHits.lastHit(owner); // also the Ender Dragon when the owner hit one of its parts
        }
        return target != null && target != taskOwner.getAttackTarget() && isSuitableTarget(target, false);
    }

    @Override
    public void startExecuting() {
        taskOwner.setAttackTarget(target);
        super.startExecuting();
    }

    /** A boss is followed further than the normal follow range (see {@link BossRange}). */
    @Override
    protected double getTargetDistance() {
        return BossRange.of(taskOwner, super.getTargetDistance());
    }
}
