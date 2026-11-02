package com.dchebakov.fiskcompanions.entity.ai;

import java.util.List;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAITarget;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;

import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

/** HERO: picks off hostile mobs that come near the owner, before they attack. */
public class AIGuardOwner extends EntityAITarget {

    private static final double BOSS_RADIUS = 64.0D;

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
        // Bosses count from further out: the Ender Dragon attacks in wide swoops, rarely within the guard radius.
        double search = Math.max(radius, BOSS_RADIUS);
        List<?> list = hero.worldObj
            .getEntitiesWithinAABB(EntityLiving.class, owner.boundingBox.expand(search, search, search));
        for (Object o : list) {
            EntityLiving e = (EntityLiving) o;
            boolean near = BossRange.isBoss(e) || e.getDistanceSqToEntity(owner) <= radius * radius * 2.0D;
            if (near && e instanceof IMob && isSuitableTarget(e, false)) {
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

    /** A boss is followed further than the normal follow range (see {@link BossRange}). */
    @Override
    protected double getTargetDistance() {
        return BossRange.of(taskOwner, super.getTargetDistance());
    }
}
