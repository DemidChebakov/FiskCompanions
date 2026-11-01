package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.player.EntityPlayer;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

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
        return target != null && isSuitableTarget(target, false);
    }

    @Override
    public void startExecuting() {
        taskOwner.setAttackTarget(target);
        super.startExecuting();
    }
}
