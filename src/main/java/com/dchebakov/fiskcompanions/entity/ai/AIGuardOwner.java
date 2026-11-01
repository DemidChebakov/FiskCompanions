package com.dchebakov.fiskcompanions.entity.ai;

import java.util.List;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;

import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

/** HERO: picks off hostile mobs that come near the owner, before they attack. */
public class AIGuardOwner extends EntityAITarget {

    private final EntityFiskHero hero;
    private final double radius;
    private EntityLiving threat;

    public AIGuardOwner(EntityFiskHero hero, double radius) {
        super(hero, true);
        this.hero = hero;
        this.radius = radius;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        EntityPlayer owner = hero.getOwner();
        if (owner == null || hero.getRNG()
            .nextInt(10) != 0) {
            return false;
        }
        threat = null;
        double best = Double.MAX_VALUE;
        List<?> list = hero.worldObj
            .getEntitiesWithinAABB(EntityLiving.class, owner.boundingBox.expand(radius, 4.0D, radius));
        for (Object o : list) {
            EntityLiving e = (EntityLiving) o;
            if (e instanceof IMob && isSuitableTarget(e, false)) {
                double d = e.getDistanceSqToEntity(owner);
                if (d < best) {
                    best = d;
                    threat = e;
                }
            }
        }
        return threat != null;
    }

    @Override
    public void startExecuting() {
        taskOwner.setAttackTarget(threat);
        super.startExecuting();
    }
}
