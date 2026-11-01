package com.dchebakov.fiskcompanions.debug;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;

import com.dchebakov.fiskcompanions.CommonProxy;
import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
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
        } else if (guiTicks == 120) {
            boolean pass = guiExpected.equals(guiMob.getHeroKey()) && guiMob.getAlignment() == Alignment.VILLAIN
                && guiMob.getWornSuit() != null;
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
            player.capabilities.isFlying = false;
            player.sendPlayerAbilities();
            finish();
        }
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
