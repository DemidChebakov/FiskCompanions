package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

/**
 * Keeps a HERO near its owner. An unsuited owner is guarded closely (bodyguard distance); a suited owner gets more room
 * to fight alongside. Teleports when left far behind, unless the hero can fly (then {@link FlightController} catches
 * up).
 */
public class AIFollowOwner extends EntityAIBase {

    private static final double GUARD_DIST = 3.0D;
    private static final double PARTNER_DIST = 6.0D;
    private static final double TELEPORT_DIST = 24.0D;

    private final EntityFiskHero hero;
    private final double speed;
    private EntityPlayer owner;
    private int repathTimer;

    public AIFollowOwner(EntityFiskHero hero, double speed) {
        this.hero = hero;
        this.speed = speed;
        setMutexBits(3);
    }

    private double minDist(EntityPlayer player) {
        return FiskBridge.isSuited(player) ? PARTNER_DIST : GUARD_DIST;
    }

    @Override
    public boolean shouldExecute() {
        EntityPlayer player = hero.getOwner();
        if (player == null || hero.isSitting() || hero.getAttackTarget() != null) {
            return false;
        }
        double min = minDist(player);
        if (hero.getDistanceSqToEntity(player) < min * min) {
            return false;
        }
        owner = player;
        return true;
    }

    @Override
    public boolean continueExecuting() {
        if (owner == null || !owner.isEntityAlive() || hero.isSitting() || hero.getAttackTarget() != null) {
            return false;
        }
        double min = minDist(owner) * 0.7D;
        return hero.getDistanceSqToEntity(owner) > min * min;
    }

    @Override
    public void startExecuting() {
        repathTimer = 0;
    }

    @Override
    public void resetTask() {
        owner = null;
        hero.getNavigator()
            .clearPathEntity();
    }

    @Override
    public void updateTask() {
        hero.getLookHelper()
            .setLookPositionWithEntity(owner, 10.0F, hero.getVerticalFaceSpeed());
        if (--repathTimer > 0) {
            return;
        }
        repathTimer = 10;
        if (hero.getFlight()
            .isFlying()) {
            return; // flight controller steers
        }
        if (!hero.getNavigator()
            .tryMoveToEntityLiving(owner, speed) && hero.getDistanceSqToEntity(owner) >= TELEPORT_DIST * TELEPORT_DIST
            && !hero.getFlight()
                .canFly()) {
            teleportNear(owner);
        }
    }

    private void teleportNear(EntityPlayer player) {
        int x = MathHelper.floor_double(player.posX) - 2;
        int z = MathHelper.floor_double(player.posZ) - 2;
        int y = MathHelper.floor_double(player.boundingBox.minY);
        for (int dx = 0; dx <= 4; ++dx) {
            for (int dz = 0; dz <= 4; ++dz) {
                if ((dx < 1 || dz < 1 || dx > 3 || dz > 3)
                    && World.doesBlockHaveSolidTopSurface(hero.worldObj, x + dx, y - 1, z + dz)
                    && !hero.worldObj.getBlock(x + dx, y, z + dz)
                        .isNormalCube()
                    && !hero.worldObj.getBlock(x + dx, y + 1, z + dz)
                        .isNormalCube()) {
                    hero.setLocationAndAngles(x + dx + 0.5D, y, z + dz + 0.5D, hero.rotationYaw, hero.rotationPitch);
                    hero.getNavigator()
                        .clearPathEntity();
                    return;
                }
            }
        }
    }
}
