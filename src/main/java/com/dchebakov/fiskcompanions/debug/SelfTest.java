package com.dchebakov.fiskcompanions.debug;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntitySpider;
import net.minecraft.entity.passive.EntityCow;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Headless behaviour test. Every loaded hero is spawned once as a VILLAIN next to a pig, facing a HERO mob wearing
 * another suit. After {@link #ROUND_TICKS} the arena is scored and cleared. Results go to the log and to
 * {@code fiskcompanions-selftest.txt} in the server directory.
 */
public final class SelfTest {

    private static final int PER_ROUND = 12;
    private static final int ROUND_TICKS = 300;
    private static final int SPACING = 28;

    private static SelfTest active;

    private final MinecraftServer server;
    private final WorldServer world;
    private final List<HeroIteration> heroes;
    private final boolean stopWhenDone;
    private final List<String> report = new ArrayList<>();
    private List<Cell> cells;
    /** Per round: a villain alone with passive mobs, and a hero bound to the player guarding against a spider. */
    private EntityFiskHero soloVillain, guard;
    private final Set<String> soloTargets = new TreeSet<>(), guardTargets = new TreeSet<>();
    private final List<Entity> extras = new ArrayList<>();
    private int round;
    private int ticks;
    private int problems;

    private SelfTest(MinecraftServer server, boolean stopWhenDone) {
        this.server = server;
        this.world = server.worldServerForDimension(0);
        this.heroes = FiskBridge.allIterations();
        this.stopWhenDone = stopWhenDone;
    }

    public static boolean start(MinecraftServer server, boolean stopWhenDone) {
        if (active != null) {
            return false;
        }
        active = new SelfTest(server, stopWhenDone);
        FMLCommonHandler.instance()
            .bus()
            .register(active);
        FiskCompanions.LOG.info("[selftest] starting: {} hero iterations", active.heroes.size());
        return true;
    }

    private static final class Cell {

        final HeroIteration villainHero, heroHero;
        final EntityFiskHero villain, hero;
        final EntityPig pig;
        final Set<String> villainTargets = new TreeSet<>(), heroTargets = new TreeSet<>();

        Cell(HeroIteration villainHero, HeroIteration heroHero, EntityFiskHero villain, EntityFiskHero hero,
            EntityPig pig) {
            this.villainHero = villainHero;
            this.heroHero = heroHero;
            this.villain = villain;
            this.hero = hero;
            this.pig = pig;
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (cells == null) {
            spawnRound();
            return;
        }
        if (++ticks % 5 == 0) {
            for (Cell c : cells) {
                sampleTarget(c.villain, c.villainTargets);
                sampleTarget(c.hero, c.heroTargets);
            }
            if (soloVillain != null) sampleTarget(soloVillain, soloTargets);
            if (guard != null) sampleTarget(guard, guardTargets);
        }
        if (ticks >= ROUND_TICKS) {
            finishRound();
        }
    }

    private static void sampleTarget(EntityFiskHero mob, Set<String> into) {
        EntityLivingBase target = mob.getAttackTarget();
        if (target != null) {
            into.add(
                target instanceof EntityFiskHero ? "FiskHero"
                    : target.getClass()
                        .getSimpleName());
        }
    }

    private void spawnRound() {
        ChunkCoordinates spawn = world.getSpawnPoint();
        cells = new ArrayList<>();
        ticks = 0;
        for (int k = 0; k < PER_ROUND; ++k) {
            int idx = round * PER_ROUND + k;
            if (idx >= heroes.size()) {
                break;
            }
            int x = spawn.posX - 42 + (k % 4) * SPACING;
            int z = spawn.posZ - 28 + (k / 4) * SPACING;
            int y = world.getTopSolidOrLiquidBlock(x, z);

            HeroIteration villainHero = heroes.get(idx);
            HeroIteration heroHero = heroes.get((idx + heroes.size() / 2 + 1) % heroes.size());
            EntityFiskHero villain = spawnMob(villainHero, Alignment.VILLAIN, x, y, z);
            EntityFiskHero hero = spawnMob(heroHero, Alignment.HERO, x - 7, y, z);
            EntityPig pig = new EntityPig(world);
            pig.setLocationAndAngles(x + 3.5D, y, z + 0.5D, 0.0F, 0.0F);
            world.spawnEntityInWorld(pig);
            cells.add(new Cell(villainHero, heroHero, villain, hero, pig));
        }
        spawnScenarios(spawn);
    }

    private void spawnScenarios(ChunkCoordinates spawn) {
        soloTargets.clear();
        guardTargets.clear();
        int x = spawn.posX + 90;
        int z = spawn.posZ + round * 20 - 100;
        int y = world.getTopSolidOrLiquidBlock(x, z);
        soloVillain = spawnMob(heroes.get(round * 7 % heroes.size()), Alignment.VILLAIN, x, y, z);
        extras.add(spawnAt(new EntityCow(world), x + 4, y, z));
        extras.add(spawnAt(new EntityVillager(world), x - 4, y, z));
        extras.add(spawnAt(new EntityIronGolem(world), x, y, z + 5));

        guard = null;
        if (!world.playerEntities.isEmpty()) {
            EntityPlayer player = (EntityPlayer) world.playerEntities.get(0);
            int px = (int) Math.floor(player.posX);
            int pz = (int) Math.floor(player.posZ);
            int py = world.getTopSolidOrLiquidBlock(px, pz);
            guard = spawnMob(heroes.get((round * 7 + 3) % heroes.size()), Alignment.HERO, px + 3, py, pz);
            guard.setOwnerId(player.getUniqueID());
            extras.add(spawnAt(new EntitySpider(world), px - 6, py, pz + 6));
        }
    }

    private Entity spawnAt(EntityLiving entity, int x, int y, int z) {
        entity.setLocationAndAngles(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
        world.spawnEntityInWorld(entity);
        return entity;
    }

    private EntityFiskHero spawnMob(HeroIteration iter, Alignment alignment, int x, int y, int z) {
        EntityFiskHero mob = new EntityFiskHero(world);
        mob.setLocationAndAngles(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
        mob.setAlignment(alignment);
        mob.setHero(iter);
        world.spawnEntityInWorld(mob);
        return mob;
    }

    private void finishRound() {
        for (Cell c : cells) {
            boolean villainSuit = c.villain.getWornSuit() != null;
            boolean heroSuit = c.hero.getWornSuit() != null;
            if (!villainSuit || !heroSuit) {
                ++problems;
            }
            String line = String.format(
                "%-40s suit=%s targets=%s uses=%s hp=%.0f | vs HERO %-34s suit=%s targets=%s uses=%s hp=%.0f | pig=%s",
                FiskBridge.keyOf(c.villainHero),
                villainSuit ? "ok" : "MISSING",
                c.villainTargets,
                c.villain.getPowers()
                    .getUsage(),
                c.villain.isEntityAlive() ? c.villain.getHealth() : 0.0F,
                FiskBridge.keyOf(c.heroHero),
                heroSuit ? "ok" : "MISSING",
                c.heroTargets,
                c.hero.getPowers()
                    .getUsage(),
                c.hero.isEntityAlive() ? c.hero.getHealth() : 0.0F,
                c.pig.isEntityAlive() ? "alive" : "dead");
            report.add(line);
            FiskCompanions.LOG.info("[selftest] {}", line);
            for (Entity e : new Entity[] { c.villain, c.hero, c.pig }) {
                e.setDead();
            }
        }
        String solo = String.format(
            "SOLO VILLAIN %-34s targets=%s uses=%s",
            soloVillain.getHeroKey(),
            soloTargets,
            soloVillain.getPowers()
                .getUsage());
        report.add(solo);
        FiskCompanions.LOG.info("[selftest] {}", solo);
        if (guard != null) {
            String g = String.format(
                "GUARD %-41s targets=%s uses=%s",
                guard.getHeroKey(),
                guardTargets,
                guard.getPowers()
                    .getUsage());
            report.add(g);
            FiskCompanions.LOG.info("[selftest] {}", g);
        }
        for (Entity e : extras) {
            e.setDead();
        }
        extras.clear();
        clearStrays();
        cells = null;
        ++round;
        if (round * PER_ROUND >= heroes.size()) {
            finish();
        }
    }

    /** Items, projectiles and anything else a round left behind near spawn. */
    private void clearStrays() {
        for (Object o : new ArrayList<Object>(world.loadedEntityList)) {
            Entity e = (Entity) o;
            if (!(e instanceof EntityPlayer) && (e instanceof EntityFiskHero || !(e instanceof EntityLiving))) {
                e.setDead();
            }
        }
    }

    private void finish() {
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        active = null;
        String summary = String
            .format("SELFTEST DONE: %d hero iterations, %d cells with a missing suit", heroes.size(), problems);
        report.add(0, summary);
        FiskCompanions.LOG.info("[selftest] {}", summary);
        File out = server.getFile("fiskcompanions-selftest.txt");
        try {
            Files.write(out.toPath(), report, StandardCharsets.UTF_8);
        } catch (IOException e) {
            FiskCompanions.LOG.error("[selftest] could not write {}", out, e);
        }
        if (stopWhenDone) {
            if (server.isDedicatedServer()) {
                server.initiateShutdown();
            } else {
                FMLCommonHandler.instance()
                    .exitJava(0, false); // integrated test world: the report is written, close the dev client
            }
        }
    }
}
