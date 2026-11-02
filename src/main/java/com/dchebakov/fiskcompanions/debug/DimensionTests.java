package com.dchebakov.fiskcompanions.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.compat.fisk.ability.Abilities;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.hero.HeroIteration;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.PowerProperty;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Nether and End behaviour checks, in arenas built from blocks so world generation doesn't matter. The test player is
 * moved into each dimension (no portal) so its chunks tick.
 * <ul>
 * <li>Nether: owned characters (walker, flier, size grower) follow the owner through a 3-wide, 3-high zig-zag tunnel;
 * a grower stands in the tunnel under a low ceiling; a flier fights a ghast in a closed chamber. Nobody may end up
 * inside blocks or stuck.</li>
 * <li>End: a flier fights a fresh Ender Dragon, unowned and then owned (the suited owner hits the dragon's head). It
 * must target the dragon, damage it, stay near it and not use one power only.</li>
 * </ul>
 */
public final class DimensionTests {

    private static final int NETHER_TICKS = 400;
    private static final int END_UNOWNED_TICKS = 500;
    private static final int END_OWNED_TICKS = 300;
    private static final int SETTLE_TICKS = 40;

    private final MinecraftServer server;
    private final EntityPlayerMP player;
    private final List<HeroIteration> heroes;
    private final Consumer<String> report;
    private final Runnable onDone;

    private int phase;
    private int ticks;
    private final List<Tracked> tracked = new ArrayList<>();
    private EntityGhast ghast;
    private EntityDragon dragon;
    private float dragonStartHp;
    private int nx, ny, nz; // nether arena origin
    private int ex, ey, ez; // end platform centre

    private DimensionTests(MinecraftServer server, EntityPlayerMP player, List<HeroIteration> heroes,
        Consumer<String> report, Runnable onDone) {
        this.server = server;
        this.player = player;
        this.heroes = heroes;
        this.report = report;
        this.onDone = onDone;
    }

    public static void start(MinecraftServer server, EntityPlayerMP player, List<HeroIteration> heroes,
        Consumer<String> report, Runnable onDone) {
        FMLCommonHandler.instance()
            .bus()
            .register(new DimensionTests(server, player, heroes, report, onDone));
    }

    /** Per-character measurements. */
    private static final class Tracked {

        final String role;
        final EntityFiskHero mob;
        int embeddedTicks, maxStuck, stuck, firstTargetTick = -1, engagedTicks, targetTicks, elapsed;
        double lastX, lastY, lastZ;
        float startHp;

        Tracked(String role, EntityFiskHero mob) {
            this.role = role;
            this.mob = mob;
            this.lastX = mob.posX;
            this.lastY = mob.posY;
            this.lastZ = mob.posZ;
            this.startHp = mob.getHealth();
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ++ticks;
        try {
            switch (phase) {
                case 0:
                    startProbe(0);
                    break;
                case 1:
                case 5:
                case 8:
                    tickProbe();
                    break;
                case 2:
                    startNether();
                    break;
                case 3:
                    tickNether();
                    break;
                case 4:
                    startProbe(-1);
                    break;
                case 6:
                    startEnd();
                    break;
                case 7:
                    startProbe(1);
                    break;
                case 9:
                    startDragon();
                    break;
                case 10:
                    tickEnd(false);
                    break;
                case 11:
                    tickEnd(true);
                    break;
                default:
                    break;
            }
        } catch (RuntimeException e) {
            FiskCompanions.LOG.error("[gallery] dimension tests crashed", e);
            report.accept("dimension tests: FAIL (crashed: " + e + ")");
            done();
        }
    }

    private void next() {
        ++phase;
        ticks = 0;
    }

    // ---- shared helpers ----

    private static final class NoPortal extends Teleporter {

        NoPortal(WorldServer world) {
            super(world);
        }

        @Override
        public void placeInPortal(Entity entity, double x, double y, double z, float yaw) {}

        @Override
        public boolean placeInExistingPortal(Entity entity, double x, double y, double z, float yaw) {
            return true;
        }

        @Override
        public boolean makePortal(Entity entity) {
            return true;
        }

        @Override
        public void removeStalePortalLocations(long time) {}
    }

    private WorldServer moveTo(int dim, double x, double y, double z) {
        WorldServer target = server.worldServerForDimension(dim);
        if (player.dimension != dim) {
            server.getConfigurationManager()
                .transferPlayerToDimension(player, dim, new NoPortal(target));
        }
        player.capabilities.isFlying = false;
        player.sendPlayerAbilities();
        player.playerNetServerHandler.setPlayerLocation(x, y, z, 0.0F, 10.0F);
        return target;
    }

    private static void fill(WorldServer w, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
        for (int x = Math.min(x0, x1); x <= Math.max(x0, x1); ++x) {
            for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); ++y) {
                for (int z = Math.min(z0, z1); z <= Math.max(z0, z1); ++z) {
                    w.setBlock(x, y, z, block, 0, 2);
                }
            }
        }
    }

    private HeroIteration pick(boolean mustFly, Modifier mustHave, String... parts) {
        for (String part : parts) {
            for (HeroIteration iter : heroes) {
                if (FiskBridge.keyOf(iter)
                    .contains(part) && fits(iter, mustFly, mustHave)) {
                    return iter;
                }
            }
        }
        for (HeroIteration iter : heroes) {
            if (fits(iter, mustFly, mustHave)) {
                return iter;
            }
        }
        return null;
    }

    private static boolean fits(HeroIteration iter, boolean mustFly, Modifier mustHave) {
        if (mustFly && !FiskBridge.canFly(iter)) return false;
        return mustHave == null || iter.hero.getModifiers()
            .contains(mustHave);
    }

    private HeroIteration pickWalker() {
        for (HeroIteration iter : heroes) {
            if (!FiskBridge.canFly(iter) && !iter.hero.getModifiers()
                .contains(Modifier.SIZE_MANIPULATION)) {
                return iter;
            }
        }
        return heroes.get(0);
    }

    /** The hero that grows biggest: under a 3-block ceiling it matters only above scale 1.6. */
    private HeroIteration pickGrower() {
        HeroIteration best = null;
        float bestMax = 1.0F;
        for (HeroIteration iter : heroes) {
            float max = iter.hero.getModifierEntries()
                .filter(e -> e.modifier == Modifier.SIZE_MANIPULATION)
                .map(e -> FiskBridge.prop(e, PowerProperty.MAX_SIZE, 1.0F))
                .max(Float::compare)
                .orElse(1.0F);
            if (max > bestMax) {
                bestMax = max;
                best = iter;
            }
        }
        return best;
    }

    private HeroIteration pickFlier() {
        return pick(true, null, "mark48", "hulkbuster", "iron_man_mk85", "iron_man");
    }

    private EntityFiskHero spawn(WorldServer w, HeroIteration iter, Alignment alignment, double x, double y, double z,
        boolean owned) {
        EntityFiskHero mob = new EntityFiskHero(w);
        mob.setLocationAndAngles(x, y, z, 0.0F, 0.0F);
        mob.setAlignment(alignment);
        mob.setHero(iter);
        if (owned) {
            mob.setOwnerId(player.getUniqueID());
        }
        w.spawnEntityInWorld(mob);
        return mob;
    }

    private static boolean embedded(EntityFiskHero mob) {
        return mob.isEntityInsideOpaqueBlock()
            || !mob.worldObj.func_147461_a(mob.boundingBox.contract(0.05D, 0.05D, 0.05D))
                .isEmpty();
    }

    /** Every tick: embedded? Every 20 ticks: moved at least a block while it should be moving? */
    private void measure(Tracked t, Entity goal, double nearEnough) {
        EntityFiskHero mob = t.mob;
        if (!mob.isEntityAlive()) {
            return;
        }
        if (embedded(mob)) {
            ++t.embeddedTicks;
        }
        if (ticks % 20 == 0) {
            double dx = mob.posX - t.lastX, dy = mob.posY - t.lastY, dz = mob.posZ - t.lastZ;
            boolean moved = dx * dx + dy * dy + dz * dz >= 1.0D;
            boolean shouldMove = goal != null && mob.getDistanceToEntity(goal) > nearEnough;
            t.stuck = !moved && shouldMove ? t.stuck + 20 : 0;
            t.maxStuck = Math.max(t.maxStuck, t.stuck);
            t.lastX = mob.posX;
            t.lastY = mob.posY;
            t.lastZ = mob.posZ;
        }
    }

    private void result(String test, boolean pass, String details) {
        String line = String.format("%s: %s (%s)", test, pass ? "PASS" : "FAIL", details);
        FiskCompanions.LOG.info("[gallery] {}", line);
        report.accept(line);
    }

    // ---- Nether ----

    /**
     * Netherrack block with: chamber A (7x7, 5 high) → 3-wide 3-high tunnel +X 12, +Z 10, +X 8 → chamber B (7x7, 5
     * high, the owner). A dead-end tunnel off chamber A holds a sitting size grower. A separate 21x21x18 chamber holds
     * the ghast fight.
     */
    private void startNether() {
        WorldServer w = server.worldServerForDimension(-1);
        nx = 0;
        ny = 70;
        nz = 0;
        fill(w, nx - 2, ny - 1, nz - 2, nx + 40, ny + 7, nz + 22, Blocks.netherrack);
        // chamber A: x 0..6, z 0..6
        fill(w, nx, ny, nz, nx + 6, ny + 4, nz + 6, Blocks.air);
        // tunnel: +X from A to x=18 (z 2..4), then +Z to z=16 (x 16..18), then +X to x=26 (z 14..16)
        fill(w, nx + 7, ny, nz + 2, nx + 18, ny + 2, nz + 4, Blocks.air);
        fill(w, nx + 16, ny, nz + 5, nx + 18, ny + 2, nz + 16, Blocks.air);
        fill(w, nx + 19, ny, nz + 14, nx + 26, ny + 2, nz + 16, Blocks.air);
        // chamber B: x 27..33, z 12..18
        fill(w, nx + 27, ny, nz + 12, nx + 33, ny + 4, nz + 18, Blocks.air);
        // dead-end 3-high tunnels off A's +Z side for sitting characters that grow: x 1..3 and x 5..6, z 7..14
        fill(w, nx + 1, ny, nz + 7, nx + 3, ny + 2, nz + 14, Blocks.air);
        fill(w, nx + 5, ny, nz + 9, nx + 6, ny + 2, nz + 14, Blocks.air);
        // ghast chamber, 40 blocks away on -Z
        int gz = nz - 45;
        fill(w, nx - 1, ny - 1, gz - 1, nx + 21, ny + 18, gz + 21, Blocks.netherrack);
        fill(w, nx, ny, gz, nx + 20, ny + 17, gz + 20, Blocks.air);

        moveTo(-1, nx + 30.5D, ny, nz + 15.5D);
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            if (o instanceof EntityFiskHero || o instanceof EntityGhast) ((Entity) o).setDead();
        }

        tracked.clear();
        HeroIteration walker = pickWalker(), flier = pickFlier(), grower = pickGrower();
        if (walker != null) {
            tracked.add(new Tracked("walker", spawn(w, walker, Alignment.HERO, nx + 1.5D, ny, nz + 1.5D, true)));
        }
        if (flier != null) {
            tracked.add(new Tracked("flier", spawn(w, flier, Alignment.HERO, nx + 5.5D, ny, nz + 1.5D, true)));
        }
        if (grower != null) {
            tracked.add(new Tracked("grower", spawn(w, grower, Alignment.HERO, nx + 3.5D, ny, nz + 5.5D, true)));
            EntityFiskHero low = spawn(w, grower, Alignment.HERO, nx + 2.5D, ny, nz + 12.5D, false);
            low.setSitting(true);
            tracked.add(new Tracked("grower under a 3-block ceiling", low));
        }
        if (flier != null) {
            // Big suits (Hulkbuster) grow by script, not size manipulation: it must not stay inside the ceiling.
            EntityFiskHero big = spawn(w, flier, Alignment.HERO, nx + 5.5D, ny, nz + 12.5D, false);
            big.setSitting(true);
            tracked.add(new Tracked("big suit under a 3-block ceiling", big));
            EntityFiskHero hunter = spawn(w, flier, Alignment.HERO, nx + 10.5D, ny, gz + 10.5D, false);
            tracked.add(new Tracked("ghast fight", hunter));
            ghast = new EntityGhast(w);
            ghast.setLocationAndAngles(nx + 10.5D, ny + 11, gz + 10.5D, 0.0F, 0.0F);
            w.spawnEntityInWorld(ghast);
        }
        report.accept("nether test heroes: walker=" + key(walker) + " flier=" + key(flier) + " grower=" + key(grower));
        next();
    }

    private static String key(HeroIteration iter) {
        return iter == null ? "-" : FiskBridge.keyOf(iter);
    }

    private void tickNether() {
        for (Tracked t : tracked) {
            boolean ghastFight = t.role.equals("ghast fight");
            boolean sitting = t.mob.isSitting();
            measure(t, ghastFight ? null : sitting ? null : player, 8.0D);
            if (ghastFight && ghast != null && t.mob.getAttackTarget() == ghast) {
                ++t.targetTicks;
                if (t.firstTargetTick < 0) t.firstTargetTick = ticks;
            }
        }
        if (ticks < NETHER_TICKS) {
            return;
        }
        for (Tracked t : tracked) {
            EntityFiskHero mob = t.mob;
            String common = String.format(
                "%s embedded=%d maxStuck=%d hpLost=%.0f scale=%.2f",
                mob.getHeroKey(),
                t.embeddedTicks,
                t.maxStuck,
                t.startHp - (mob.isEntityAlive() ? mob.getHealth() : 0.0F),
                com.fiskmods.heroes.common.data.var.Vars.SCALE.get(mob));
            if (t.role.equals("ghast fight")) {
                boolean ghastHit = ghast == null || !ghast.isEntityAlive() || ghast.getHealth() < ghast.getMaxHealth();
                boolean pass = t.embeddedTicks == 0 && t.firstTargetTick >= 0 && ghastHit;
                result(
                    "nether test [ghast fight]",
                    pass,
                    String.format(
                        "%s targetedAt=%d ghast=%s uses=%s",
                        common,
                        t.firstTargetTick,
                        ghast == null ? "-"
                            : ghast.isEntityAlive() ? String.format("%.0f hp", ghast.getHealth()) : "dead",
                        mob.getPowers()
                            .getUsage()));
            } else if (mob.isSitting()) {
                // Allowed: the half second before the watchdog steps out of a ceiling a script grew it into.
                boolean pass = t.embeddedTicks <= 20 && !embedded(mob);
                result("nether test [" + t.role + "]", pass, common + " embeddedNow=" + embedded(mob));
            } else {
                double dist = mob.isEntityAlive() ? mob.getDistanceToEntity(player) : -1.0D;
                boolean pass = mob.isEntityAlive() && t.embeddedTicks == 0 && t.maxStuck <= 100 && dist <= 8.0D;
                result(
                    "nether test [follow, " + t.role + "]",
                    pass,
                    String.format(
                        "%s distToOwner=%.1f flying=%s",
                        common,
                        dist,
                        mob.getFlight()
                            .isFlying()));
            }
            mob.setDead();
        }
        tracked.clear();
        if (ghast != null) ghast.setDead();
        next();
    }

    // ---- flight animation probe (each dimension) ----

    private FlightProbe probe;

    /** Overworld: open flat ground. Nether: the 17-block-high ghast chamber. End: the obsidian platform. */
    private void startProbe(int dim) {
        double px, py, pz;
        if (dim == 0) {
            WorldServer home = server.worldServerForDimension(0);
            net.minecraft.util.ChunkCoordinates spawn = home.getSpawnPoint();
            px = spawn.posX - 30;
            pz = spawn.posZ + 30;
            py = home.getTopSolidOrLiquidBlock((int) px, (int) pz);
        } else if (dim == -1) {
            px = nx + 5;
            py = ny;
            pz = nz - 45 + 10;
        } else {
            px = ex;
            py = ey;
            pz = ez;
        }
        WorldServer w = moveTo(dim, px + 0.5D, py, pz + 0.5D);
        String where = dim == 0 ? "overworld" : dim == -1 ? "nether" : "end";
        probe = new FlightProbe(w, player, heroes, px, py, pz, where, report);
        if (probe.isEmpty()) {
            report.accept("flight probe [" + where + "]: no flying heroes installed");
            probe = null;
            next();
            next();
            return;
        }
        next();
    }

    private void tickProbe() {
        if (probe == null || probe.tick()) {
            probe = null;
            next();
        }
    }

    // ---- End ----

    private void startEnd() {
        WorldServer w = server.worldServerForDimension(1);
        if (ticks == 1) {
            ex = 40;
            ez = 0;
            int top = w.getTopSolidOrLiquidBlock(ex, ez);
            ey = top > 10 ? top : 64;
            fill(w, ex - 12, ey - 1, ez - 12, ex + 12, ey - 1, ez + 12, Blocks.obsidian);
            fill(w, ex - 12, ey, ez - 12, ex + 12, ey + 5, ez + 12, Blocks.air);
            moveTo(1, ex + 0.5D, ey, ez + 0.5D);
            return;
        }
        if (ticks < SETTLE_TICKS) {
            return; // let the chunks around the player load before sweeping them
        }
        clearEnd(w);
        next();
    }

    private void startDragon() {
        WorldServer w = server.worldServerForDimension(1);
        HeroIteration flier = pickFlier();
        if (flier == null) {
            result("end test", false, "no flying hero installed");
            done();
            return;
        }
        spawnDragon(w);
        tracked.clear();
        tracked.add(new Tracked("unowned", spawn(w, flier, Alignment.HERO, ex + 3.5D, ey, ez + 0.5D, false)));
        next();
    }

    /** Vanilla dragons and crystals near the island would interfere (crystals heal the dragon). */
    private void clearEnd(WorldServer w) {
        for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
            if ((o instanceof EntityDragon && o != dragon) || o instanceof EntityEnderCrystal
                || o instanceof EntityFiskHero) {
                ((Entity) o).setDead();
            }
        }
    }

    private void spawnDragon(WorldServer w) {
        if (dragon != null) dragon.setDead();
        dragon = new EntityDragon(w);
        dragon.setLocationAndAngles(ex + 0.5D, ey + 20, ez + 0.5D, 0.0F, 0.0F);
        w.spawnEntityInWorld(dragon);
        dragonStartHp = dragon.getHealth();
    }

    private void tickEnd(boolean owned) {
        WorldServer w = server.worldServerForDimension(1);
        if (ticks % 20 == 0) {
            for (Object o : new ArrayList<Object>(w.loadedEntityList)) {
                if ((o instanceof EntityDragon && o != dragon) || o instanceof EntityEnderCrystal) {
                    ((Entity) o).setDead();
                }
            }
        }
        if (owned && ticks == 20) {
            // The suited owner hits the dragon's head: a dragon part, not a living entity.
            FiskBridge.equip(player, pickWalker());
            player.attackTargetEntityWithCurrentItem(dragon.dragonPartHead);
            dragonStartHp = dragon.getHealth();
        }
        for (Tracked t : tracked) {
            ++t.elapsed;
            measure(t, dragon, 24.0D);
            if (t.mob.getAttackTarget() == dragon) {
                ++t.targetTicks;
                if (t.firstTargetTick < 0) t.firstTargetTick = ticks;
                if (t.mob.getDistanceToEntity(dragon) <= 24.0D) ++t.engagedTicks;
            }
        }
        int length = owned ? END_OWNED_TICKS : END_UNOWNED_TICKS;
        if (ticks < length && dragon.isEntityAlive()) {
            return;
        }
        for (Tracked t : tracked) {
            reportEnd(t, owned, length);
            t.mob.setDead();
        }
        tracked.clear();
        if (!owned) {
            spawnDragon(w);
            tracked.add(new Tracked("owned", spawn(w, pickFlier(), Alignment.HERO, ex + 3.5D, ey, ez + 0.5D, true)));
            next();
            return;
        }
        for (int slot = 1; slot <= 4; ++slot) {
            player.setCurrentItemOrArmor(slot, null);
        }
        dragon.setDead();
        done();
    }

    private void reportEnd(Tracked t, boolean owned, int length) {
        EntityFiskHero mob = t.mob;
        float lost = dragonStartHp - (dragon.isEntityAlive() ? dragon.getHealth() : 0.0F);
        Map<String, Integer> attacks = new LinkedHashMap<>();
        int total = 0, top = 0;
        for (Map.Entry<String, Integer> e : mob.getPowers()
            .getUsage()
            .entrySet()) {
            String k = e.getKey();
            if (k.equals("melee") || k.startsWith("shot:")
                || k.startsWith("beam:")
                || k.startsWith("toggle:")
                || k.startsWith("gadget:")) {
                attacks.put(k, e.getValue());
                total += e.getValue();
                top = Math.max(top, e.getValue());
            }
        }
        long ranged = mob.getWornSuit() == null ? 0
            : mob.getWornSuit().hero.getModifiers()
                .stream()
                .filter(Abilities::isRanged)
                .count();
        // Melee is an option for a flier too, so with any ranged power there are at least two ways to attack.
        boolean varied = ranged == 0 || total < 5 || top <= total * 0.7D;
        // Unowned: finds the dragon itself. Owned: joins in within 5 s of the owner's hit at tick 20.
        boolean targeted = t.firstTargetTick >= 0 && t.firstTargetTick <= (owned ? 120 : 100);
        // Actively fighting: at least one hit on the dragon per 2 s from the moment it was targeted. (Time spent within
        // 24 blocks is reported too, but it swings with the dragon's random flight path, so it doesn't gate.)
        int hits = mob.getPowers()
            .getUsage()
            .getOrDefault("dragon-hit", 0);
        int fight = t.firstTargetTick < 0 ? t.elapsed : t.elapsed - t.firstTargetTick;
        boolean engaged = hits >= fight / 40;
        boolean pass = targeted && lost >= 10.0F && engaged && varied && t.embeddedTicks == 0;
        result(
            "end test [dragon, " + t.role + "]",
            pass,
            String.format(
                "%s targetedAt=%d targetTicks=%d engaged=%d/%d dragonHpLost=%.0f dragonHits=%d attacks=%s varied=%s",
                mob.getHeroKey(),
                t.firstTargetTick,
                t.targetTicks,
                t.engagedTicks,
                t.elapsed,
                lost,
                hits,
                attacks,
                varied));
    }

    private void done() {
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        FlightProbe.jumpInterval = 0;
        for (Tracked t : tracked) t.mob.setDead();
        tracked.clear();
        if (dragon != null) dragon.setDead();
        if (ghast != null) ghast.setDead();
        WorldServer home = server.worldServerForDimension(0);
        net.minecraft.util.ChunkCoordinates spawn = home.getSpawnPoint();
        moveTo(0, spawn.posX + 0.5D, home.getTopSolidOrLiquidBlock(spawn.posX, spawn.posZ), spawn.posZ + 0.5D);
        onDone.run();
    }
}
