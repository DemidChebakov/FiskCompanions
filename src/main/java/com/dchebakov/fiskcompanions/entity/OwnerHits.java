package com.dchebakov.fiskcompanions.entity;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.player.AttackEntityEvent;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/**
 * What each player last hit, so owned heroes can join in. {@code EntityPlayer.getLastAttacker} only records living
 * targets, so a hit on one of the Ender Dragon's body parts (plain entities) was invisible to the heroes; parts are
 * resolved to the dragon here.
 */
public final class OwnerHits {

    private static final int MEMORY_TICKS = 100;
    private static final Map<EntityPlayer, Hit> HITS = new WeakHashMap<>();

    private static final class Hit {

        final EntityLivingBase target;
        final int tick;

        Hit(EntityLivingBase target, int tick) {
            this.target = target;
            this.tick = tick;
        }
    }

    @SubscribeEvent
    public void onAttack(AttackEntityEvent event) {
        EntityPlayer player = event.entityPlayer;
        if (player.worldObj.isRemote) {
            return;
        }
        Entity target = event.target;
        EntityLivingBase living = FiskBridge.dragonOf(target);
        if (living == null && target instanceof EntityLivingBase) {
            living = (EntityLivingBase) target;
        }
        if (living != null) {
            synchronized (HITS) {
                HITS.put(player, new Hit(living, player.ticksExisted));
            }
        }
    }

    /** The living entity {@code player} hit in the last 5 seconds, or null. */
    public static EntityLivingBase lastHit(EntityPlayer player) {
        Hit hit;
        synchronized (HITS) {
            hit = HITS.get(player);
        }
        if (hit == null || player.ticksExisted - hit.tick > MEMORY_TICKS || !hit.target.isEntityAlive()) {
            return null;
        }
        return hit.target;
    }
}
