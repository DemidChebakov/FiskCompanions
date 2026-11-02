package com.dchebakov.fiskcompanions.entity.ai;

import java.util.List;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;

/**
 * Nearest-target search like vanilla {@code EntityAINearestAttackableTarget}, but looking the whole follow range up and
 * down instead of 4 blocks, so flying heroes, villains, ghasts and the Ender Dragon are found.
 */
public class AINearestTarget extends EntityAITarget {

    private final Class<?> type;
    private final IEntitySelector selector;
    private EntityLivingBase target;

    public AINearestTarget(EntityCreature owner, Class<?> type, IEntitySelector selector) {
        super(owner, true);
        this.type = type;
        this.selector = selector;
        setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        double range = getTargetDistance();
        List<?> list = taskOwner.worldObj
            .selectEntitiesWithinAABB(type, taskOwner.boundingBox.expand(range, range, range), selector);
        target = null;
        double best = Double.MAX_VALUE;
        for (Object o : list) {
            if (!(o instanceof EntityLivingBase)) {
                continue;
            }
            EntityLivingBase e = (EntityLivingBase) o;
            double d = taskOwner.getDistanceSqToEntity(e);
            if (d < best && isSuitableTarget(e, false)) {
                best = d;
                target = e;
            }
        }
        return target != null;
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
