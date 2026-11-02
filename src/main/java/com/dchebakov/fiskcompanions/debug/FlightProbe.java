package com.dchebakov.fiskcompanions.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MathHelper;
import net.minecraft.world.WorldServer;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.HeroIteration;
import com.fiskmods.heroes.common.hero.modifier.Modifier;

/**
 * Flight animation smoothness. Fisk animates a suit's flight from two timers: {@code FLIGHT_TIMER} ramps over 10 ticks
 * while {@code Vars.FLYING} is on, {@code FLIGHT_BOOST_TIMER} over 8 ticks while flying and sprinting (the boost pose).
 * Any flip of those flags, or a body snapped to a new heading, shows as a twitching pose. Owned fliers (one per flight
 * modifier kind) are watched tick by tick in four situations: the owner standing still, the owner hopping (the client
 * player really jumps, see {@link #jumpInterval}), a fight against a villain dummy on the ground (no reason to fly in
 * any of these), and the owner flying a circle overhead (where they should take off once and fly smoothly).
 */
final class FlightProbe {

    /** Client side: jump every this many ticks while on the ground, 0 = don't. */
    static volatile int jumpInterval;

    private static final String[] SITUATIONS = { "owner idle", "owner jumping", "fighting a ground target",
        "owner flying" };
    private static final int[] LENGTH = { 100, 300, 300, 300 };
    /** "owner flying": the owner circles this high and this wide, at a brisk flying speed. */
    private static final double CIRCLE_HEIGHT = 6.0D, CIRCLE_RADIUS = 7.0D, CIRCLE_SPEED = 0.25D;
    private static final int JUMP_EVERY = 20;
    /** A flight shorter than this (ticks) is a hop: the pose can't even finish blending in and out. */
    private static final int SHORT_FLIGHT = 60;

    private final WorldServer world;
    private final EntityPlayerMP player;
    private final String where;
    private final Consumer<String> report;
    private final double x, y, z;
    private final List<Stats> fliers = new ArrayList<>();
    private EntityFiskHero dummy;
    private int situation = -1;
    private int ticks;

    private static final class Stats {

        final String kind;
        final EntityFiskHero mob;
        boolean wasFlying, wasVar, wasSprint;
        float lastTimer, lastBoost, lastYaw, lastPitch;
        int timerSign, boostSign, flightStart;
        int takeoffs, shortFlights, varFlips, boostFlips, poseReversals, boostReversals, yawJumps, pitchJumps,
            flyingTicks;

        Stats(String kind, EntityFiskHero mob) {
            this.kind = kind;
            this.mob = mob;
        }

        void reset() {
            takeoffs = shortFlights = varFlips = boostFlips = poseReversals = boostReversals = yawJumps = pitchJumps = flyingTicks = 0;
            flightStart = mob.ticksExisted;
            // Start from the current state, so a flag already on isn't counted as a flip.
            wasFlying = mob.getFlight()
                .isFlying();
            wasVar = Vars.FLYING.get(mob);
            wasSprint = mob.isSprinting();
            lastTimer = Vars.FLIGHT_TIMER.get(mob);
            lastBoost = Vars.FLIGHT_BOOST_TIMER.get(mob);
            lastYaw = mob.renderYawOffset;
            lastPitch = mob.rotationPitch;
            timerSign = boostSign = 0;
        }
    }

    FlightProbe(WorldServer world, EntityPlayerMP player, List<HeroIteration> heroes, double x, double y, double z,
        String where, Consumer<String> report) {
        this.world = world;
        this.player = player;
        this.where = where;
        this.report = report;
        this.x = x;
        this.y = y;
        this.z = z;
        int i = 0;
        for (Modifier kind : new Modifier[] { Modifier.CONTROLLED_FLIGHT, Modifier.FLIGHT,
            Modifier.PROPELLED_FLIGHT }) {
            HeroIteration iter = pick(heroes, kind);
            if (iter == null) {
                continue;
            }
            EntityFiskHero mob = new EntityFiskHero(world);
            mob.setLocationAndAngles(x - 2.0D + i * 2.0D, y, z + 3.5D, 180.0F, 0.0F);
            mob.setAlignment(Alignment.HERO);
            mob.setHero(iter);
            mob.setOwnerId(player.getUniqueID());
            world.spawnEntityInWorld(mob);
            fliers.add(new Stats(kind.getName(), mob));
            ++i;
        }
    }

    /** One hero per flight kind; big Iron Man suits first (the Hulkbuster is the reported case). */
    private static HeroIteration pick(List<HeroIteration> heroes, Modifier kind) {
        String[] prefs = { "mark48", "iron_man_mk85", "superman", "vision", "martian", "falcon", "" };
        for (String pref : prefs) {
            for (HeroIteration iter : heroes) {
                if (FiskBridge.keyOf(iter)
                    .contains(pref)
                    && iter.hero.getModifiers()
                        .contains(kind)) {
                    return iter;
                }
            }
        }
        return null;
    }

    boolean isEmpty() {
        return fliers.isEmpty();
    }

    /** Returns true when all situations are done (and everything it spawned is gone). */
    boolean tick() {
        if (situation < 0 || ticks >= LENGTH[situation]) {
            if (situation >= 0) {
                finishSituation();
            }
            if (++situation >= SITUATIONS.length) {
                cleanUp();
                return true;
            }
            startSituation();
        }
        ++ticks;
        if (situation == 3) {
            double a = ticks * CIRCLE_SPEED / CIRCLE_RADIUS;
            player.playerNetServerHandler.setPlayerLocation(
                x + 0.5D + Math.cos(a) * CIRCLE_RADIUS,
                y + CIRCLE_HEIGHT,
                z + 0.5D + Math.sin(a) * CIRCLE_RADIUS,
                (float) Math.toDegrees(a),
                0.0F);
        }
        if (dummy != null) {
            dummy.setPositionAndRotation(x + 10.5D, y, z + 0.5D, 90.0F, 0.0F);
            dummy.motionX = dummy.motionY = dummy.motionZ = 0.0D;
            dummy.setHealth(dummy.getMaxHealth());
            dummy.setAttackTarget(null);
        }
        for (Stats s : fliers) {
            sample(s);
        }
        return false;
    }

    private void startSituation() {
        ticks = 0;
        jumpInterval = situation == 1 ? JUMP_EVERY : 0;
        player.capabilities.isFlying = situation == 3;
        player.sendPlayerAbilities();
        player.playerNetServerHandler.setPlayerLocation(x + 0.5D, y, z + 0.5D, 0.0F, 10.0F);
        if (situation == 2) {
            dummy = new EntityFiskHero(world);
            dummy.setLocationAndAngles(x + 10.5D, y, z + 0.5D, 90.0F, 0.0F);
            dummy.setAlignment(Alignment.VILLAIN);
            dummy.setSitting(true);
            dummy.setHero(
                fliers.get(0).mob.getWornSuit() != null ? fliers.get(0).mob.getWornSuit()
                    : FiskBridge.lookup(fliers.get(0).mob.getHeroKey()));
            world.spawnEntityInWorld(dummy);
        }
        for (Stats s : fliers) {
            s.reset();
        }
    }

    private static int sign(float d) {
        return d > 1.0E-4F ? 1 : d < -1.0E-4F ? -1 : 0;
    }

    private void sample(Stats s) {
        EntityFiskHero mob = s.mob;
        if (!mob.isEntityAlive()) {
            return;
        }
        boolean flying = mob.getFlight()
            .isFlying();
        boolean var = Vars.FLYING.get(mob);
        boolean sprint = mob.isSprinting();
        float timer = Vars.FLIGHT_TIMER.get(mob);
        float boost = Vars.FLIGHT_BOOST_TIMER.get(mob);
        if (flying && !s.wasFlying) {
            ++s.takeoffs;
            s.flightStart = mob.ticksExisted;
        } else if (!flying && s.wasFlying && mob.ticksExisted - s.flightStart < SHORT_FLIGHT) {
            ++s.shortFlights;
        }
        if (flying) ++s.flyingTicks;
        if (var != s.wasVar) ++s.varFlips;
        if (flying && sprint != s.wasSprint) ++s.boostFlips;
        int ts = sign(timer - s.lastTimer);
        if (ts != 0) {
            if (s.timerSign != 0 && ts != s.timerSign) ++s.poseReversals;
            s.timerSign = ts;
        }
        int bs = sign(boost - s.lastBoost);
        if (bs != 0) {
            if (s.boostSign != 0 && bs != s.boostSign) ++s.boostReversals;
            s.boostSign = bs;
        }
        if (flying) {
            if (Math.abs(MathHelper.wrapAngleTo180_float(mob.renderYawOffset - s.lastYaw)) > 30.0F) ++s.yawJumps;
            if (Math.abs(mob.rotationPitch - s.lastPitch) > 20.0F) ++s.pitchJumps;
        }
        s.wasFlying = flying;
        s.wasVar = var;
        s.wasSprint = sprint;
        s.lastTimer = timer;
        s.lastBoost = boost;
        s.lastYaw = mob.renderYawOffset;
        s.lastPitch = mob.rotationPitch;
    }

    private void finishSituation() {
        jumpInterval = 0;
        for (Stats s : fliers) {
            // A natural flight blends in once and out once: at most one pose reversal per flight, no hops, no
            // flickering flags, and the body turns rather than snaps.
            boolean pass;
            switch (situation) {
                case 0:
                    pass = s.takeoffs == 0 && s.varFlips == 0;
                    break;
                case 1:
                    pass = s.shortFlights == 0 && s.takeoffs <= 1
                        && s.poseReversals <= 1
                        && s.boostReversals <= 1
                        && s.yawJumps <= 2;
                    break;
                case 3:
                    // Flight is right here: be up most of the time (take off at most once), and blend the pose in once.
                    pass = s.flyingTicks >= LENGTH[3] * 0.7D && s.takeoffs <= 1
                        && s.shortFlights == 0
                        && s.poseReversals <= 1
                        && s.varFlips <= 2
                        && s.boostReversals <= 3
                        && s.yawJumps <= 2
                        && s.pitchJumps <= 2;
                    break;
                default:
                    pass = s.shortFlights == 0 && s.poseReversals <= 2
                        && s.boostReversals <= 3
                        && s.boostFlips <= 6
                        && s.yawJumps <= 4
                        && s.pitchJumps <= 4;
                    break;
            }
            String line = String.format(
                "flight probe [%s, %s, %s]: %s (%s flying=%d takeoffs=%d shortFlights=%d flyingVarFlips=%d boostFlips=%d poseReversals=%d boostReversals=%d yawJumps=%d pitchJumps=%d)",
                where,
                SITUATIONS[situation],
                s.kind,
                pass ? "PASS" : "FAIL",
                s.mob.getHeroKey(),
                s.flyingTicks,
                s.takeoffs,
                s.shortFlights,
                s.varFlips,
                s.boostFlips,
                s.poseReversals,
                s.boostReversals,
                s.yawJumps,
                s.pitchJumps);
            FiskCompanions.LOG.info("[gallery] {}", line);
            report.accept(line);
        }
        if (dummy != null) {
            dummy.setDead();
            dummy = null;
        }
    }

    private void cleanUp() {
        jumpInterval = 0;
        player.capabilities.isFlying = false;
        player.sendPlayerAbilities();
        player.playerNetServerHandler.setPlayerLocation(x + 0.5D, y, z + 0.5D, 0.0F, 10.0F);
        for (Stats s : fliers) {
            s.mob.setDead();
        }
        if (dummy != null) dummy.setDead();
    }
}
