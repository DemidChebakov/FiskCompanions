package com.dchebakov.fiskcompanions.entity.ai;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.DamageSource;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.DataVar;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.Hero;
import com.fiskmods.heroes.common.hero.modifier.Modifier;

/**
 * Raises the character's shield against what is about to hit it: a projectile on its way in, the target swinging at it
 * in reach, or whoever just hurt it. Two kinds of shield:
 * <ul>
 * <li>Suit shields ({@code fiskheroes:shield}: Mk50/Mk85 nanite shields, Doctor Strange's shield, wide protective
 * fields). A player holds a key, which sets Fisk's {@code SHIELD} and {@code SHIELD_BLOCKING}. With those set, Fisk's
 * own attack handler blocks, damages and breaks the shield, and knocks melee attackers back, for any entity. The
 * coverage is relative to the look direction, so the character faces the threat.</li>
 * <li>Captain America's shield item. Fisk only lets players block with an item shield, so
 * {@link EntityFiskHero#attackEntityFrom} runs Fisk's block steps itself ({@code FiskBridge.blockWithItem}).</li>
 * </ul>
 * It blocks in bursts, so the character still gets to strike back. Server side.
 */
public class ShieldController {

    private static final int MAX_BLOCK_TICKS = 40;
    private static final int REST_TICKS = 25;
    private static final int RECENT_HIT_TICKS = 20;
    private static final double PROJECTILE_RANGE = 10.0D;
    private static final double SWING_RANGE = 4.5D;

    private final EntityFiskHero hero;
    private boolean blocking;
    private boolean suitShield;
    private int blockTicks;
    private int restTicks;
    private Entity lastAttacker;
    private int lastHitTick = -RECENT_HIT_TICKS;

    public ShieldController(EntityFiskHero hero) {
        this.hero = hero;
    }

    public boolean isBlocking() {
        return blocking;
    }

    /** True while blocking with an item shield (Captain America's), which this mod applies itself. */
    public boolean isBlockingWithItem() {
        return blocking && !suitShield;
    }

    /** Called for every attack on the character, blocked or not. */
    public void onAttacked(DamageSource source) {
        Entity attacker = source.getEntity();
        if (attacker != null && attacker != hero) {
            lastAttacker = attacker;
            lastHitTick = hero.ticksExisted;
        }
    }

    public void reset() {
        setBlocking(false);
        blockTicks = 0;
        restTicks = 0;
    }

    public void update() {
        Hero h = FiskBridge.hero(hero);
        suitShield = h != null && h.getModifiers()
            .contains(Modifier.SHIELD);
        boolean suitShieldReady = suitShield && h.getEnabledModifier(hero, Modifier.SHIELD) != null;
        boolean itemShield = !suitShield && hero.getWeapons()
            .holdsShield();
        // A suit shield only counts while the suit enables it (Mk50: nanites on; Mk85: not aiming or flying).
        boolean canBlock = (suitShieldReady || itemShield) && Vars.SHIELD_COOLDOWN.get(hero) <= 0;
        if (restTicks > 0) {
            --restTicks;
        }
        Entity threat = canBlock && restTicks <= 0 ? findThreat() : null;
        boolean want = threat != null;
        if (want && ++blockTicks > MAX_BLOCK_TICKS) {
            want = false;
            restTicks = REST_TICKS;
        }
        if (!want) {
            blockTicks = 0;
        }
        if (want) {
            hero.faceEntity(threat); // shield coverage is measured from the look direction
        }
        if (setBlocking(want) && want) {
            hero.getPowers()
                .record(suitShield ? "shield_block" : "shield_block:item");
        }
    }

    /** Returns true if the state changed. */
    private boolean setBlocking(boolean value) {
        boolean changed = blocking != value;
        blocking = value;
        hero.setBlockingFlag(value && !suitShield); // client: Captain America's blocking arm pose
        if (suitShield || !value) {
            setVar(Vars.SHIELD, value && suitShield);
            setVar(Vars.SHIELD_BLOCKING, value && suitShield);
        }
        return changed;
    }

    private void setVar(DataVar<Boolean> var, boolean value) {
        if (var.get(hero) != value) {
            var.set(hero, value)
                .sync();
        }
    }

    private Entity findThreat() {
        Entity projectile = incomingProjectile();
        if (projectile != null) {
            return projectile;
        }
        EntityLivingBase target = hero.getAttackTarget();
        if (target != null && target.isEntityAlive()
            && target.isSwingInProgress
            && hero.getDistanceToEntity(target) <= SWING_RANGE) {
            return target;
        }
        if (lastAttacker != null && lastAttacker.isEntityAlive()
            && hero.ticksExisted - lastHitTick < RECENT_HIT_TICKS
            && !(lastAttacker instanceof EntityLivingBase && hero.isOnSameTeam((EntityLivingBase) lastAttacker))) {
            return lastAttacker;
        }
        return null;
    }

    /** The nearest projectile flying at the character that wasn't fired by it or its side. */
    private Entity incomingProjectile() {
        List<?> list = hero.worldObj.getEntitiesWithinAABBExcludingEntity(
            hero,
            hero.boundingBox.expand(PROJECTILE_RANGE, PROJECTILE_RANGE, PROJECTILE_RANGE));
        Entity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Object o : list) {
            Entity e = (Entity) o;
            if (e instanceof EntityLivingBase || e instanceof EntityItem
                || e instanceof EntityXPOrb
                || !e.isEntityAlive()) {
                continue;
            }
            double speedSq = e.motionX * e.motionX + e.motionY * e.motionY + e.motionZ * e.motionZ;
            if (speedSq < 0.09D || !isProjectile(e)) {
                continue;
            }
            Entity owner = owner(e);
            if (owner == hero || owner instanceof EntityLivingBase && hero.isOnSameTeam((EntityLivingBase) owner)) {
                continue;
            }
            // Heading at us: closest approach of its straight path passes within 2 blocks of our middle.
            double dx = hero.posX - e.posX, dy = hero.posY + hero.height * 0.5D - e.posY, dz = hero.posZ - e.posZ;
            double along = (dx * e.motionX + dy * e.motionY + dz * e.motionZ) / Math.sqrt(speedSq);
            if (along <= 0.0D) {
                continue; // moving away
            }
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq - along * along > 4.0D) {
                continue;
            }
            if (distSq < bestDist) {
                bestDist = distSq;
                best = e;
            }
        }
        return best;
    }

    private static boolean isProjectile(Entity e) {
        return e instanceof EntityArrow || e instanceof EntityThrowable
            || e instanceof EntityFireball
            || e instanceof net.minecraft.entity.IProjectile;
    }

    private static Entity owner(Entity e) {
        if (e instanceof EntityArrow) {
            return ((EntityArrow) e).shootingEntity;
        }
        if (e instanceof EntityThrowable) {
            return ((EntityThrowable) e).getThrower();
        }
        if (e instanceof EntityFireball) {
            return ((EntityFireball) e).shootingEntity;
        }
        return null;
    }
}
