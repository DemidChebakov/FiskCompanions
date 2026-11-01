package com.dchebakov.fiskcompanions.debug;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

import com.dchebakov.fiskcompanions.CommonProxy;
import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.network.PacketSettingsSync;
import com.dchebakov.fiskcompanions.world.CompanionSettings;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Dev-only visual check (singleplayer): lines up {@link #PER_PAGE} heroes in front of the player, page by page, and
 * asks
 * the client to screenshot each page ({@link #takeShotRequest}). Heropack heroes come first. Writes
 * {@code fiskcompanions-gallery.txt} mapping screenshot names to hero keys, then runs {@code onDone}.
 */
public final class Gallery {

    private static final int PER_PAGE = 5;
    private static final int PAGE_TICKS = 80;
    private static final int SHOT_AT = 70;

    /** Screenshot name the client should save next; set on the server thread, cleared on the client thread. */
    private static volatile String shotRequest;
    /** Configurator test: entity the client should open the GUI for, and the hero key it should apply. */
    private static volatile int guiEntity = -1;
    private static volatile String guiKey;
    private static volatile String guiPreviewKey;
    private final List<EntityFiskHero> fliers = new ArrayList<>();
    private int flightTicks = -1;
    private EntityFiskHero flightDummy;
    private final List<EntityFiskHero> racers = new ArrayList<>();
    private final java.util.Map<EntityFiskHero, Integer> arrivals = new java.util.HashMap<>();
    private EntityFiskHero raceDummy;
    private int raceTicks = -1;
    private static volatile boolean spawnerRequest;
    private static volatile boolean removeAllRequest;
    private int removeAllTicks = -1;
    private int spawnerTicks = -1;
    private int spawnerBefore;

    private EntityFiskHero guiMob;
    private String guiExpected;
    private int guiTicks = -1;

    private final MinecraftServer server;
    private final WorldServer world;
    private final List<HeroIteration> heroes;
    private final Runnable onDone;
    private final List<String> index = new ArrayList<>();
    private final List<EntityFiskHero> shown = new ArrayList<>();
    private EntityPlayerMP player;
    private int waitTicks;
    private int page = -1;
    private int ticks;
    private double cx, cy, cz;

    private Gallery(MinecraftServer server, Runnable onDone) {
        this.server = server;
        this.world = server.worldServerForDimension(0);
        this.onDone = onDone;
        this.heroes = FiskBridge.testIterations();
        heroes.sort(Comparator.comparing((HeroIteration i) -> "fiskheroes".equals(FiskBridge.domain(i))));
    }

    public static void start(MinecraftServer server, Runnable onDone) {
        FMLCommonHandler.instance()
            .bus()
            .register(new Gallery(server, onDone));
    }

    /** Client side: entity id to open the configurator for, or -1. */
    public static int takeGuiRequest() {
        int id = guiEntity;
        guiEntity = -1;
        return id;
    }

    public static String guiTestKey() {
        return guiKey;
    }

    /** Suit the client selects in the configurator to check that the preview follows the selection. */
    public static String guiPreviewKey() {
        return guiPreviewKey;
    }

    /** Client side: true once when the client should drive the spawner menu test. */
    public static boolean takeRemoveAllRequest() {
        boolean b = removeAllRequest;
        removeAllRequest = false;
        return b;
    }

    public static boolean takeSpawnerRequest() {
        boolean b = spawnerRequest;
        spawnerRequest = false;
        return b;
    }

    public static String takeShotRequest() {
        String s = shotRequest;
        shotRequest = null;
        return s;
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (player == null) {
            if (world.playerEntities.isEmpty() || ++waitTicks < 60) {
                return;
            }
            setUp((EntityPlayerMP) world.playerEntities.get(0));
            return;
        }
        if (guiTicks >= 0) {
            tickGuiTest();
            return;
        }
        if (flightTicks >= 0) {
            tickFlightTest();
            return;
        }
        if (raceTicks >= 0) {
            tickRace();
            return;
        }
        if (spawnerTicks >= 0) {
            tickSpawnerTest();
            return;
        }
        if (removeAllTicks >= 0) {
            tickRemoveAllTest();
            return;
        }
        if (page < 0 || ++ticks >= PAGE_TICKS) {
            nextPage();
            return;
        }
        pinHeroes();
        if (ticks == SHOT_AT) {
            shotRequest = String.format("fc-gallery-%03d", page);
            for (EntityFiskHero mob : shown) {
                FiskCompanions.LOG.info("[gallery] server {} {}", mob.getHeroKey(), FiskBridge.debugState(mob));
            }
        }
    }

    private void setUp(EntityPlayerMP p) {
        player = p;
        ChunkCoordinates spawn = world.getSpawnPoint();
        cx = spawn.posX + 0.5D;
        cz = spawn.posZ + 0.5D;
        cy = world.getTopSolidOrLiquidBlock(spawn.posX, spawn.posZ);
        world.setWorldTime(6000L);
        world.getGameRules()
            .setOrCreateGameRule("doDaylightCycle", "false");
        world.getGameRules()
            .setOrCreateGameRule("doMobSpawning", "false");
        FiskCompanions.LOG.info("[gallery] {} hero iterations, {} per screenshot", heroes.size(), PER_PAGE);
    }

    private void nextPage() {
        for (EntityFiskHero mob : shown) {
            mob.setDead();
        }
        shown.clear();
        ticks = 0;
        ++page;
        int from = page * PER_PAGE;
        if (from >= heroes.size()) {
            startGuiTest();
            return;
        }
        // Camera 6 blocks south of the row, looking north at it.
        player.playerNetServerHandler.setPlayerLocation(cx, cy, cz + 6.0D, 180.0F, 8.0F);
        StringBuilder line = new StringBuilder(String.format("fc-gallery-%03d.png:", page));
        for (int i = 0; i < PER_PAGE && from + i < heroes.size(); ++i) {
            HeroIteration iter = heroes.get(from + i);
            EntityFiskHero mob = new EntityFiskHero(world);
            mob.setLocationAndAngles(slotX(i), cy, cz, 0.0F, 0.0F);
            mob.setAlignment(Alignment.HERO);
            mob.setSitting(true);
            mob.setHero(iter);
            world.spawnEntityInWorld(mob);
            shown.add(mob);
            line.append(' ')
                .append(FiskBridge.keyOf(iter));
        }
        index.add(line.toString());
    }

    private double slotX(int i) {
        return cx + (i - (PER_PAGE - 1) / 2.0D) * 1.6D;
    }

    /** Hold every hero in place, facing the camera, so the screenshot is a clean line-up. */
    private void pinHeroes() {
        for (int i = 0; i < shown.size(); ++i) {
            EntityFiskHero mob = shown.get(i);
            mob.setAttackTarget(null);
            mob.motionX = mob.motionY = mob.motionZ = 0.0D;
            mob.setPositionAndRotation(slotX(i), cy, cz, 0.0F, 0.0F);
            mob.rotationYawHead = mob.renderYawOffset = 0.0F;
        }
    }

    /** Spawns a hero, hands the player the configurator and asks the client to drive the real GUI. */
    private void startGuiTest() {
        player.playerNetServerHandler.setPlayerLocation(cx, cy, cz + 4.0D, 180.0F, 8.0F);
        player.inventory
            .setInventorySlotContents(player.inventory.currentItem, new ItemStack(CommonProxy.configurator));
        player.inventoryContainer.detectAndSendChanges();
        guiMob = new EntityFiskHero(world);
        guiMob.setLocationAndAngles(cx, cy, cz, 0.0F, 0.0F);
        guiMob.setAlignment(Alignment.HERO);
        guiMob.setSitting(true);
        guiMob.setHero(heroes.get(0));
        world.spawnEntityInWorld(guiMob);
        guiExpected = FiskBridge.keyOf(heroes.get(heroes.size() - 1));
        guiKey = guiExpected;
        guiPreviewKey = firstKeyContaining("iron_man_mk85", "iron_man", "");
        guiTicks = 0;
    }

    private void tickGuiTest() {
        ++guiTicks;
        if (guiTicks == 20) {
            guiEntity = guiMob.getEntityId(); // client opens the GUI, screenshots it, applies after a moment
        } else if (guiTicks > 40) {
            boolean pass = guiExpected.equals(guiMob.getHeroKey()) && guiMob.getAlignment() == Alignment.VILLAIN
                && guiMob.getWornSuit() != null;
            // Wait for the client's Apply (its timing depends on when the screen opened), up to 10 seconds.
            if (!pass && guiTicks < 220) {
                return;
            }
            String result = String.format(
                "configurator GUI test: %s (hero=%s alignment=%s suit=%s, expected %s VILLAIN)",
                pass ? "PASS" : "FAIL",
                guiMob.getHeroKey(),
                guiMob.getAlignment(),
                guiMob.getWornSuit() != null,
                guiExpected);
            FiskCompanions.LOG.info("[gallery] {}", result);
            index.add(result);
            guiMob.setDead();
            guiTicks = -1;
            startFlightTest();
        }
    }

    private String firstKeyContaining(String... parts) {
        for (String part : parts) {
            for (HeroIteration iter : heroes) {
                if (FiskBridge.keyOf(iter)
                    .contains(part)) {
                    return FiskBridge.keyOf(iter);
                }
            }
        }
        return FiskBridge.keyOf(heroes.get(0));
    }

    /**
     * Flight pose check, seen side-on: a villain dummy is held in the air in front of the camera and flying heroes
     * take off to one side and fly across the view to attack it (boost pose on the way, hover-fighting on arrival).
     */
    private void startFlightTest() {
        player.capabilities.isFlying = true;
        player.sendPlayerAbilities();
        flightDummy = new EntityFiskHero(world);
        flightDummy.setLocationAndAngles(cx - 2.0D, cy + 6.0D, cz, 0.0F, 0.0F);
        flightDummy.setAlignment(Alignment.VILLAIN);
        flightDummy.setSitting(true);
        flightDummy.setHero(heroes.get(0));
        world.spawnEntityInWorld(flightDummy);
        String[] wanted = { "iron_man_mk85", "secretheroes:homelander", "martian_manhunter", "vision",
            "fiskheroes:atom", "anti_monitor" };
        int i = 0;
        for (String part : wanted) {
            for (HeroIteration iter : heroes) {
                if (FiskBridge.keyOf(iter)
                    .contains(part) && FiskBridge.canFly(iter)) {
                    EntityFiskHero mob = new EntityFiskHero(world);
                    mob.setLocationAndAngles(cx - 5.0D + i * 1.5D, cy, cz - 22.0D, 0.0F, 0.0F);
                    mob.setAlignment(Alignment.HERO);
                    mob.setHero(iter);
                    world.spawnEntityInWorld(mob);
                    fliers.add(mob);
                    ++i;
                    break;
                }
            }
        }
        index.add("flight test: " + fliers.size() + " fliers");
        flightTicks = 0;
    }

    private void tickFlightTest() {
        ++flightTicks;
        // Camera 10 blocks east of the dummy at the same height, looking west: the fliers cross the view from the
        // right.
        player.playerNetServerHandler.setPlayerLocation(cx + 8.0D, cy + 5.0D, cz, 90.0F, 5.0F);
        flightDummy.setPositionAndRotation(cx - 2.0D, cy + 6.0D, cz, 90.0F, 0.0F);
        flightDummy.motionX = flightDummy.motionY = flightDummy.motionZ = 0.0D;
        flightDummy.setHealth(flightDummy.getMaxHealth());
        flightDummy.setAttackTarget(null);
        if (flightTicks == 25 || flightTicks == 35 || flightTicks == 45) {
            shotRequest = "fc-flight-" + flightTicks;
            for (EntityFiskHero mob : fliers) {
                FiskCompanions.LOG.info(
                    "[gallery] flight {} flying={} sprinting={} y={} distToTarget={} speed={} factor={}/{} scale={}",
                    mob.getHeroKey(),
                    mob.getFlight()
                        .isFlying(),
                    mob.isSprinting(),
                    String.format("%.1f", mob.posY),
                    String.format("%.1f", mob.getDistanceToEntity(flightDummy)),
                    String.format("%.2f", Math.sqrt(mob.motionX * mob.motionX + mob.motionZ * mob.motionZ)),
                    String.format(
                        "%.2f",
                        mob.getFlight()
                            .speedFactor(false)),
                    String.format(
                        "%.2f",
                        mob.getFlight()
                            .speedFactor(true)),
                    String.format("%.2f", Vars.SCALE.get(mob)));
            }
        } else if (flightTicks == 100) {
            shotRequest = "fc-flight-arrived";
        } else if (flightTicks >= 120) {
            for (EntityFiskHero mob : fliers) {
                mob.setDead();
            }
            fliers.clear();
            flightDummy.setDead();
            flightTicks = -1;
            startRace();
        }
    }

    /**
     * Super speed check: speedsters and one normal hero race 60 blocks to a villain dummy pinned on the ground. Logs
     * each racer's top speed level and arrival tick; screenshots from the side catch the speed trails.
     */
    private void startRace() {
        raceDummy = new EntityFiskHero(world);
        raceDummy.setLocationAndAngles(cx, cy, cz - 18.0D, 0.0F, 0.0F);
        raceDummy.setAlignment(Alignment.VILLAIN);
        raceDummy.setSitting(true);
        raceDummy.setHero(heroes.get(0));
        world.spawnEntityInWorld(raceDummy);
        String[] wanted = { "fiskheroes:the_flash", "fiskheroes:reverse_flash", "fiskheroes:zoom",
            "secretheroes:impulse", "fiskheroes:godspeed", "fiskheroes:captain_america" };
        int i = 0;
        for (String part : wanted) {
            for (HeroIteration iter : heroes) {
                if (FiskBridge.keyOf(iter)
                    .startsWith(part)) {
                    EntityFiskHero mob = new EntityFiskHero(world);
                    mob.setLocationAndAngles(cx - 5.0D + i * 2.0D, cy, cz + 10.0D, 180.0F, 0.0F);
                    mob.setAlignment(Alignment.HERO);
                    mob.setHero(iter);
                    world.spawnEntityInWorld(mob);
                    racers.add(mob);
                    ++i;
                    break;
                }
            }
        }
        raceTicks = 0;
    }

    private void tickRace() {
        ++raceTicks;
        player.playerNetServerHandler.setPlayerLocation(cx + 14.0D, cy + 3.0D, cz - 4.0D, 90.0F, 10.0F);
        raceDummy.setPositionAndRotation(cx, cy, cz - 18.0D, 0.0F, 0.0F);
        raceDummy.motionX = raceDummy.motionY = raceDummy.motionZ = 0.0D;
        raceDummy.setHealth(raceDummy.getMaxHealth());
        raceDummy.setAttackTarget(null);
        for (EntityFiskHero mob : racers) {
            if (!arrivals.containsKey(mob) && mob.getDistanceToEntity(raceDummy) <= 3.5D) {
                arrivals.put(mob, raceTicks);
            }
        }
        if (raceTicks == 8 || raceTicks == 14) {
            shotRequest = "fc-race-" + raceTicks;
        }
        if (raceTicks % 5 == 0 && raceTicks <= 30) {
            for (EntityFiskHero mob : racers) {
                FiskCompanions.LOG.info(
                    "[gallery] race t={} {} speeding={} level={}/{} pos=({}, {}, {}) dist={} moveSpeed={}",
                    raceTicks,
                    mob.getHeroKey(),
                    mob.getSpeed()
                        .isSpeeding(),
                    Vars.SPEED.get(mob),
                    mob.getSpeed()
                        .maxLevel(),
                    String.format("%.1f", mob.posX),
                    String.format("%.1f", mob.posY),
                    String.format("%.1f", mob.posZ),
                    String.format("%.1f", mob.getDistanceToEntity(raceDummy)),
                    String.format(
                        "%.3f",
                        mob.getEntityAttribute(net.minecraft.entity.SharedMonsterAttributes.movementSpeed)
                            .getAttributeValue()));
            }
        }
        if (raceTicks >= 300 || arrivals.size() == racers.size()) {
            for (EntityFiskHero mob : racers) {
                String line = String.format(
                    "race: %s arrived after %s ticks",
                    mob.getHeroKey(),
                    arrivals.containsKey(mob) ? String.valueOf(arrivals.get(mob)) : "never");
                FiskCompanions.LOG.info("[gallery] {}", line);
                index.add(line);
                mob.setDead();
            }
            racers.clear();
            raceDummy.setDead();
            raceTicks = -1;
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
            startSpawnerTest();
        }
    }

    /**
     * Spawner menu check: the client opens the real menu, screenshots its tabs and presses "Spawn squad: Avengers";
     * the server then counts the characters that appeared.
     */
    private void startSpawnerTest() {
        player.playerNetServerHandler.setPlayerLocation(cx, cy, cz + 6.0D, 180.0F, 15.0F);
        player.inventory
            .setInventorySlotContents(player.inventory.currentItem, new ItemStack(CommonProxy.configurator));
        player.inventoryContainer.detectAndSendChanges();
        CommonProxy.network.sendTo(PacketSettingsSync.of(player), player);
        spawnerBefore = countCharacters();
        spawnerRequest = true;
        spawnerTicks = 0;
    }

    private int countCharacters() {
        int n = 0;
        for (Object o : world.loadedEntityList) {
            if (o instanceof EntityFiskHero && ((EntityFiskHero) o).isEntityAlive()) ++n;
        }
        return n;
    }

    private void tickSpawnerTest() {
        if (++spawnerTicks < 120) {
            return;
        }
        int expected = 0;
        for (String key : AVENGERS) {
            if (FiskBridge.lookup(key) != null) ++expected;
        }
        int spawned = countCharacters() - spawnerBefore;
        String result = String.format(
            "spawner squad test: %s (spawned %d, Avengers members installed %d)",
            spawned == expected && expected > 0 ? "PASS" : "FAIL",
            spawned,
            expected);
        FiskCompanions.LOG.info("[gallery] {}", result);
        index.add(result);
        spawnerTicks = -1;
        // Next: Remove all, pressed (twice, to confirm) in the real menu.
        removeAllRequest = true;
        removeAllTicks = 0;
    }

    private void tickRemoveAllTest() {
        if (++removeAllTicks < 60) {
            return;
        }
        int left = countCharacters();
        String result = String.format("remove all test: %s (%d characters left)", left == 0 ? "PASS" : "FAIL", left);
        FiskCompanions.LOG.info("[gallery] {}", result);
        index.add(result);
        for (Object o : new ArrayList<Object>(world.loadedEntityList)) {
            if (o instanceof EntityFiskHero) ((EntityFiskHero) o).setDead();
        }
        removeAllTicks = -1;
        friendlyFireTest();
        finish();
    }

    private static final String[] AVENGERS = { "fiskheroes:iron_man", "fiskheroes:captain_america",
        "fiskheroes:ant_man", "fiskheroes:black_panther", "fiskheroes:vision", "fiskheroes:falcon",
        "fiskheroes:spider_man", "fiskheroes:doctor_strange" };

    /** All four friendly-fire rule combinations, checked by direct hits on the server. */
    private void friendlyFireTest() {
        CompanionSettings rules = CompanionSettings.get(world);
        boolean savedFight = rules.fightBackPlayers, savedAlly = rules.allyFriendlyFire;
        player.setGameType(net.minecraft.world.WorldSettings.GameType.SURVIVAL);
        String[] names = { "ally fire OFF: no damage", "ally fire ON: damage + retaliation",
            "fight back OFF: no retaliation", "fight back ON: retaliation" };
        for (int c = 0; c < 4; ++c) {
            rules.set(c == 3, c == 1);
            EntityFiskHero a = testMob(-2.0D), b = testMob(2.0D);
            float before = b.getHealth();
            if (c < 2) {
                b.hurtResistantTime = 0;
                b.attackEntityFrom(DamageSource.causeMobDamage(a), 4.0F);
            } else {
                b.hurtResistantTime = 0;
                b.attackEntityFrom(DamageSource.causePlayerDamage(player), 4.0F);
            }
            boolean hurt = b.getHealth() < before;
            Entity target = b.getAttackTarget();
            boolean pass;
            switch (c) {
                case 0:
                    pass = !hurt && target == null;
                    break;
                case 1:
                    pass = hurt && target == a;
                    break;
                case 2:
                    pass = hurt && target != player;
                    break;
                default:
                    pass = hurt && target == player;
                    break;
            }
            String result = String.format(
                "friendly fire test [%s]: %s (hurt=%s target=%s)",
                names[c],
                pass ? "PASS" : "FAIL",
                hurt,
                target == null ? "none" : target == player ? "player" : target == a ? "ally" : "other");
            FiskCompanions.LOG.info("[gallery] {}", result);
            index.add(result);
            a.setDead();
            b.setDead();
        }
        rules.set(savedFight, savedAlly);
        player.setGameType(net.minecraft.world.WorldSettings.GameType.CREATIVE);
    }

    private EntityFiskHero testMob(double dx) {
        EntityFiskHero mob = new EntityFiskHero(world);
        mob.setLocationAndAngles(cx + dx, cy, cz - 30.0D, 0.0F, 0.0F);
        mob.setAlignment(Alignment.HERO);
        mob.setSitting(true);
        mob.setHero(heroes.get(0));
        world.spawnEntityInWorld(mob);
        return mob;
    }

    private void finish() {
        FMLCommonHandler.instance()
            .bus()
            .unregister(this);
        File out = server.getFile("fiskcompanions-gallery.txt");
        try {
            Files.write(out.toPath(), index, StandardCharsets.UTF_8);
        } catch (IOException e) {
            FiskCompanions.LOG.error("[gallery] could not write {}", out, e);
        }
        FiskCompanions.LOG.info("[gallery] done: {} pages", page);
        onDone.run();
    }
}
