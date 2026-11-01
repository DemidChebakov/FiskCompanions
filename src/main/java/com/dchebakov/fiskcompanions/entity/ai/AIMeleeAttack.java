package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;

import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

/**
 * Melee chase and attack. Unlike vanilla {@code EntityAIAttackOnCollide} it keeps going without a path: there is never
 * a path to a flying hero, so a walker would otherwise stand still under a flier hovering within reach. Falls back to
 * steering straight at the target (jumping when it is above) and hits whenever it is in reach.
 */
public class AIMeleeAttack extends EntityAIBase {

    private static final int ATTACK_INTERVAL = 20;

    private final EntityFiskHero hero;
    private final double speed;
    private int repathTimer;
    private int attackTimer;

    public AIMeleeAttack(EntityFiskHero hero, double speed) {
        this.hero = hero;
        this.speed = speed;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase target = hero.getAttackTarget();
        return target != null && target.isEntityAlive();
    }

    @Override
    public void startExecuting() {
        repathTimer = 0;
    }

    @Override
    public void resetTask() {
        hero.getNavigator()
            .clearPathEntity();
    }

    @Override
    public void updateTask() {
        EntityLivingBase target = hero.getAttackTarget();
        if (target == null) {
            return;
        }
        hero.getLookHelper()
            .setLookPositionWithEntity(target, 30.0F, 30.0F);
        if (attackTimer > 0) {
            --attackTimer;
        }
        if (!hero.getFlight()
            .isFlying() && --repathTimer <= 0) {
            repathTimer = 4 + hero.getRNG()
                .nextInt(7);
            if (!hero.getNavigator()
                .tryMoveToEntityLiving(target, speed)) {
                hero.getMoveHelper()
                    .setMoveTo(target.posX, target.boundingBox.minY, target.posZ, speed);
            }
        }
        if (target.boundingBox.minY > hero.boundingBox.minY + 0.5D && hero.onGround) {
            hero.getJumpHelper()
                .setJumping();
        }
        double reach = hero.width * 2.0F * hero.width * 2.0F + target.width;
        double dist = hero.getDistanceSq(target.posX, target.boundingBox.minY, target.posZ);
        boolean inReach = dist <= reach + 1.0D || hero.boundingBox.expand(1.0D, 1.5D, 1.0D)
            .intersectsWith(target.boundingBox);
        if (inReach && attackTimer <= 0) {
            attackTimer = ATTACK_INTERVAL;
            hero.attackEntityAsMob(target);
        }
    }
}
