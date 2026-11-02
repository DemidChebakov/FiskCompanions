package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityFlying;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.passive.EntityBat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.Hero;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;

/**
 * Flight for heroes whose suit can fly. Fisk's flight modifiers only steer players, so the mob drives its own motion,
 * as Fisk's {@code EntityIronMan} does. The flying state lives here: {@code Vars.FLYING} is only set for the pose and
 * sound, and Fisk resets it for heroes without controlled flight.
 */
public class FlightController {

    private static final double GRAVITY = 0.08D;
    private static final double MIN_SPEED_FACTOR = 0.4D;
    private static final double MAX_SPEED_FACTOR = 4.0D;
    /** Minimum ticks in the air before landing, and on the ground before taking off again. */
    private static final int MIN_AIR_TICKS = 60;
    private static final int MIN_GROUND_TICKS = 20;
    /** Ticks pressed against a wall before giving up on flying there and walking (path-finding) instead. */
    private static final int BLOCKED_TICKS = 15;
    /** After landing because of a wall, stay on the ground this long unless the goal is in the air. */
    private static final int OBSTRUCTED_GROUND_TICKS = 100;
    /** The reason to fly must last this long before taking off: a hop or a knock-back is not a reason. */
    private static final int TAKEOFF_DELAY = 10;
    /** Something counts as "up in the air" only this far above the ground (a jump peaks at 1.25 blocks). */
    private static final double AIRBORNE_HEIGHT = 2.5D;
    /** While flying, stay this high above the ground: touching it makes Fisk reset the flying state (pose flicker). */
    private static final double MIN_HOVER = 1.2D;
    /** Boost pose (sprinting) on beyond this distance and off below the other, so it doesn't flip at one threshold. */
    private static final double BOOST_ON = 8.0D, BOOST_OFF = 5.0D;
    /** Turn rates while flying, degrees per tick: a body that snaps to a new heading looks like a glitch. */
    private static final float MAX_YAW_STEP = 15.0F, MAX_PITCH_STEP = 10.0F;

    private final EntityFiskHero hero;
    private boolean flying;
    private int stateTicks;
    private int blockedTicks;
    private int obstructedUntil = -1;
    private int lastPathCheck = -100;
    private boolean lastPathOk;
    private int wantTicks;
    private boolean boosting;

    public FlightController(EntityFiskHero hero) {
        this.hero = hero;
    }

    public boolean canFly() {
        Hero h = FiskBridge.hero(hero);
        return h != null && (h.getModifiers()
            .contains(Modifier.FLIGHT)
            || h.getModifiers()
                .contains(Modifier.CONTROLLED_FLIGHT)
            || h.getModifiers()
                .contains(Modifier.PROPELLED_FLIGHT));
    }

    public boolean isFlying() {
        return flying;
    }

    /** True for a while after flight was stopped by walls (e.g. a Nether tunnel): the follow AI may teleport then. */
    public boolean wasObstructed() {
        return hero.ticksExisted < obstructedUntil + OBSTRUCTED_GROUND_TICKS * 2;
    }

    /** Room to rise: the hitbox, lifted two blocks, is free. Tunnels and low caves give none. */
    private boolean hasHeadroom() {
        return hero.worldObj.func_147461_a(
            hero.boundingBox.getOffsetBoundingBox(0.0D, 2.0D, 0.0D)
                .contract(0.05D, 0.05D, 0.05D))
            .isEmpty();
    }

    /** A walking path reaches the goal (checked once a second; path-finding is not free). */
    private boolean canWalkTo(Entity goal) {
        if (hero.ticksExisted - lastPathCheck >= 20) {
            lastPathCheck = hero.ticksExisted;
            net.minecraft.pathfinding.PathEntity path = hero.getNavigator()
                .getPathToEntityLiving(goal);
            lastPathOk = false;
            if (path != null) {
                net.minecraft.pathfinding.PathPoint end = path.getFinalPathPoint();
                double ex = end.xCoord + 0.5D - goal.posX, ez = end.zCoord + 0.5D - goal.posZ;
                lastPathOk = ex * ex + ez * ez < 9.0D && Math.abs(end.yCoord - goal.boundingBox.minY) < 3.0D;
            }
        }
        return lastPathOk;
    }

    public void update() {
        ++stateTicks;
        if (!canFly()) {
            if (flying) setFlying(false);
            return;
        }
        Entity goal = hero.getAttackTarget();
        double yOffset;
        double stopDist;
        if (goal != null && goal.isEntityAlive()) {
            yOffset = 0.0D; // feet level with the target's: aiming higher makes two fliers out-climb each other forever
            stopDist = 1.5D;
        } else {
            goal = hero.isSitting() ? null : hero.getOwner();
            yOffset = 2.0D; // hover above the owner's shoulder
            stopDist = 3.0D;
        }
        if (goal == null) {
            if (flying && stateTicks > MIN_AIR_TICKS) setFlying(false);
            return;
        }

        double dx = goal.posX - hero.posX;
        double dz = goal.posZ - hero.posZ;
        double horiz = MathHelper.sqrt_double(dx * dx + dz * dz);
        boolean goalAirborne = isAirborne(goal);
        // Climbing is a reason to fly only for a real height difference, not counting the hover offset above the owner
        // (with it, every jump of the owner launched the fliers).
        double climb = goal.boundingBox.minY - hero.boundingBox.minY;
        boolean wantFly = goalAirborne || climb > AIRBORNE_HEIGHT || horiz > 14.0D;
        wantTicks = wantFly ? wantTicks + 1 : 0;
        double goalY = goal.boundingBox.minY + yOffset;
        if (flying) {
            goalY = Math.max(goalY, groundBelow() + MIN_HOVER);
        }
        double dy = goalY - hero.posY;
        // Flight goes in a straight line. When the goal is out of sight round a corner (caves, Nether tunnels) and a
        // path leads there, or flight just hit a wall, walk like everyone else instead.
        boolean grounded = !goalAirborne && (hero.ticksExisted < obstructedUntil + OBSTRUCTED_GROUND_TICKS
            || !hero.canEntityBeSeen(goal) && canWalkTo(goal));

        if (!flying) {
            if (wantTicks >= TAKEOFF_DELAY && !grounded && stateTicks > MIN_GROUND_TICKS && hasHeadroom()) {
                setFlying(true);
                hero.motionY = 0.5D;
                hero.getPowers()
                    .record("flight");
            }
            return;
        }
        if (!wantFly && stateTicks > MIN_AIR_TICKS && horiz < stopDist + 2.0D) {
            setFlying(false); // drop the last block or two; fall damage is reduced in EntityFiskHero.fall
            return;
        }
        // Pressed against a wall (or a ceiling) without getting closer: land and let path-finding take over.
        double progress = Math.abs(hero.motionX) + Math.abs(hero.motionZ) + Math.abs(hero.motionY - GRAVITY);
        blockedTicks = (hero.isCollidedHorizontally || hero.isCollidedVertically && dy > 0.5D) && progress < 0.15D
            ? blockedTicks + 1
            : 0;
        if (blockedTicks >= BLOCKED_TICKS) {
            blockedTicks = 0;
            obstructedUntil = hero.ticksExisted;
            setFlying(false);
            hero.getPowers()
                .record("flight-blocked");
            return;
        }
        if (!Vars.FLYING.get(hero)) {
            Vars.FLYING.set(hero, true)
                .sync(); // re-assert at once if Fisk reset it: every tick off blends the flight pose out and back in
        }

        hero.getNavigator()
            .clearPathEntity();
        hero.fallDistance = 0.0F;
        double dist = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        if (boosting ? dist < BOOST_OFF : dist > BOOST_ON) {
            boosting = !boosting;
        }
        // Sprinting while flying is Fisk's "boost": suits with a boost pose fly horizontally, arm out.
        hero.setSprinting(boosting);
        if (dist > stopDist) {
            double speed = (hero.getAttackTarget() != null ? 0.55D : 0.45D) * speedFactor(boosting);
            hero.motionX = hero.motionX * 0.6D + dx / dist * speed * 0.4D;
            hero.motionY = hero.motionY * 0.6D + dy / dist * speed * 0.4D + GRAVITY;
            hero.motionZ = hero.motionZ * 0.6D + dz / dist * speed * 0.4D;
            float pitch = (float) -(Math.atan2(dy, horiz) * 180.0D / Math.PI);
            turnTowards(dx, dz, horiz, pitch);
        } else {
            hero.motionX *= 0.5D;
            hero.motionY = hero.motionY * 0.5D + GRAVITY * 0.5D + dy * 0.05D; // settle at the hover height
            hero.motionZ *= 0.5D;
            turnTowards(dx, dz, horiz, 0.0F); // level out, keep facing the goal
        }
    }

    /**
     * Turns body, head and pitch toward a heading at a limited rate. Near the goal (under half a block horizontally)
     * the heading is meaningless and would spin, so only the pitch eases.
     */
    private void turnTowards(double dx, double dz, double horiz, float pitch) {
        if (horiz > 0.5D) {
            float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
            float step = MathHelper.wrapAngleTo180_float(yaw - hero.renderYawOffset);
            step = Math.max(-MAX_YAW_STEP, Math.min(MAX_YAW_STEP, step));
            hero.rotationYaw = hero.renderYawOffset = hero.rotationYawHead = hero.renderYawOffset + step;
        }
        float pstep = Math.max(-MAX_PITCH_STEP, Math.min(MAX_PITCH_STEP, pitch - hero.rotationPitch));
        hero.rotationPitch += pstep;
    }

    /**
     * Up in the air for real: flying (a player in flight mode, a flying character, ghasts, bats, bosses) or at least
     * {@link #AIRBORNE_HEIGHT} above the ground. A jump, a step or a knock-back doesn't count.
     */
    private boolean isAirborne(Entity goal) {
        if (goal.onGround || goal.isInWater()
            || goal instanceof EntityLivingBase && ((EntityLivingBase) goal).isOnLadder()) {
            return false;
        }
        if (goal instanceof EntityPlayer && ((EntityPlayer) goal).capabilities.isFlying) {
            return true;
        }
        if ((goal instanceof EntityPlayer || goal instanceof EntityFiskHero) && Vars.FLYING.get(goal)) {
            return true;
        }
        if (goal instanceof EntityFlying || goal instanceof EntityBat || goal instanceof IBossDisplayData) {
            return true;
        }
        AxisAlignedBB below = goal.boundingBox.copy();
        below.minY -= AIRBORNE_HEIGHT;
        return hero.worldObj.func_147461_a(below)
            .isEmpty();
    }

    /** Top of the highest block under the hero, up to 6 blocks down (or 6 below if there is none). */
    private double groundBelow() {
        AxisAlignedBB column = hero.boundingBox.copy();
        column.maxY = column.minY + 0.01D;
        column.minY -= 6.0D;
        double top = hero.boundingBox.minY - 6.0D;
        for (Object o : hero.worldObj.func_147461_a(column)) {
            top = Math.max(top, ((AxisAlignedBB) o).maxY);
        }
        return top;
    }

    /**
     * The suit's own flight speed relative to Fisk's defaults: {@code speed} (default 0.1) while cruising and, for
     * controlled flight, {@code boostSpeed} (default 0.2) when boosting; plain flight boosts by 1.2x like a sprinting
     * player. So fast suits fly faster and slow ones slower.
     */
    public double speedFactor(boolean boosting) {
        ModifierEntry entry = FiskBridge.enabled(hero, Modifier.CONTROLLED_FLIGHT);
        if (entry == null) entry = FiskBridge.enabled(hero, Modifier.FLIGHT);
        if (entry == null) entry = FiskBridge.enabled(hero, Modifier.PROPELLED_FLIGHT);
        if (entry == null) {
            // Not enabled by the hero's script right now (e.g. Anti-Monitor): use the suit's declared flight settings.
            entry = FiskBridge.declared(hero, Modifier.CONTROLLED_FLIGHT, Modifier.FLIGHT, Modifier.PROPELLED_FLIGHT);
        }
        if (entry == null) {
            return 1.0D;
        }
        float speed = FiskBridge.prop(entry, PowerProperty.SPEED, 0.1F);
        double factor;
        if (!boosting) {
            factor = speed / 0.1D;
        } else if (entry.modifier == Modifier.CONTROLLED_FLIGHT) {
            factor = FiskBridge.prop(entry, PowerProperty.BOOST_SPEED, speed * 2.0F) / 0.2D;
        } else {
            factor = speed / 0.1D * 1.2D;
        }
        return Math.max(MIN_SPEED_FACTOR, Math.min(MAX_SPEED_FACTOR, factor));
    }

    private void setFlying(boolean value) {
        flying = value;
        stateTicks = 0;
        wantTicks = 0;
        boosting = false;
        if (!value) {
            hero.setSprinting(false);
        }
        Vars.FLYING.set(hero, value)
            .sync();
    }
}
