package com.dchebakov.fiskcompanions.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.client.gui.GuiHeroConfigurator;
import com.dchebakov.fiskcompanions.client.gui.GuiHeroSpawner;
import com.dchebakov.fiskcompanions.client.render.BaseBody;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Dev-only: with {@code -Dfiskcompanions.selftest=true} the client leaves the main menu for a fresh creative superflat
 * world, so {@link SelfTest} runs on the integrated server. Waits for the menu to be on screen for a moment, because
 * the first menu opens before the game has finished initialising.
 */
@SideOnly(Side.CLIENT)
public class ClientSelfTestLauncher {

    private boolean launched;
    private int guiTicks = -1;
    private int spawnerTicks = -1;
    private int removeAllTicks = -1;
    private int menuTicks;

    /** Opens the real configurator for the requested mob, screenshots it, then applies a choice through the GUI. */
    private void driveGuiTest(Minecraft mc) {
        int id = Gallery.takeGuiRequest();
        if (id >= 0) {
            Entity entity = mc.theWorld.getEntityByID(id);
            if (entity instanceof EntityFiskHero) {
                FiskCompanions.proxy.openConfigurator((EntityFiskHero) entity);
                guiTicks = 0;
            }
            return;
        }
        if (guiTicks < 0 || !(mc.currentScreen instanceof GuiHeroConfigurator)) {
            return;
        }
        ++guiTicks;
        GuiHeroConfigurator gui = (GuiHeroConfigurator) mc.currentScreen;
        if (guiTicks == 20) {
            ScreenShotHelper.saveScreenshot(
                mc.mcDataDir,
                "fc-configurator.png",
                mc.displayWidth,
                mc.displayHeight,
                mc.getFramebuffer());
            gui.select(Gallery.guiPreviewKey());
        } else if (guiTicks == 32) {
            ScreenShotHelper.saveScreenshot(
                mc.mcDataDir,
                "fc-configurator-preview.png",
                mc.displayWidth,
                mc.displayHeight,
                mc.getFramebuffer());
        } else if (guiTicks == 40) {
            gui.selectAndApply(Gallery.guiTestKey(), Alignment.VILLAIN, false);
            guiTicks = -1;
        }
    }

    /** Opens the real spawner menu, screenshots each tab, then spawns the Avengers squad through it. */
    private void driveSpawnerTest(Minecraft mc) {
        if (Gallery.takeRemoveAllRequest()) {
            FiskCompanions.proxy.openSpawner();
            removeAllTicks = 0;
        }
        if (removeAllTicks >= 0 && mc.currentScreen instanceof GuiHeroSpawner && ++removeAllTicks == 5) {
            shot(mc, "fc-spawner-before-remove.png");
            ((GuiHeroSpawner) mc.currentScreen).removeAllForTest();
            removeAllTicks = -1;
        }
        if (Gallery.takeSpawnerRequest()) {
            FiskCompanions.proxy.openSpawner();
            spawnerTicks = 0;
            return;
        }
        if (spawnerTicks < 0 || !(mc.currentScreen instanceof GuiHeroSpawner)) {
            return;
        }
        GuiHeroSpawner gui = (GuiHeroSpawner) mc.currentScreen;
        ++spawnerTicks;
        if (spawnerTicks == 2) {
            gui.showTab(GuiHeroSpawner.TAB_SPAWN);
            gui.selectSquadNamed("Avengers");
            boolean warned = gui.addToSquadForTest("fiskheroes:iron_man"); // already in the Avengers
            FiskCompanions.LOG.info("[gallery] client duplicate warning test: {}", warned ? "PASS" : "FAIL");
        } else if (spawnerTicks == 15) {
            shot(mc, "fc-spawner-spawn.png");
            gui.cancelDuplicateForTest();
            gui.showTab(GuiHeroSpawner.TAB_SQUADS);
        } else if (spawnerTicks == 22) {
            shot(mc, "fc-spawner-squads.png");
            gui.showTab(GuiHeroSpawner.TAB_SETTINGS);
        } else if (spawnerTicks == 29) {
            shot(mc, "fc-spawner-settings.png");
            gui.showTab(GuiHeroSpawner.TAB_SQUADS);
        } else if (spawnerTicks == 34) {
            gui.spawnSquadNamed("Avengers");
            spawnerTicks = -1;
        }
    }

    private static void shot(Minecraft mc, String name) {
        ScreenShotHelper.saveScreenshot(mc.mcDataDir, name, mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        String shot = event.phase == TickEvent.Phase.END ? Gallery.takeShotRequest() : null;
        if (shot != null && mc.theWorld != null) {
            mc.gameSettings.hideGUI = true;
            for (Object o : mc.theWorld.loadedEntityList) {
                if (o instanceof EntityFiskHero) {
                    EntityFiskHero mob = (EntityFiskHero) o;
                    FiskCompanions.LOG.info(
                        "[gallery] client {} base={} {}",
                        mob.getHeroKey(),
                        BaseBody.show(mob) ? "steve" : "none",
                        FiskBridge.debugState(mob));
                }
            }
            ScreenShotHelper
                .saveScreenshot(mc.mcDataDir, shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        }
        if (event.phase == TickEvent.Phase.END && mc.theWorld != null) {
            driveGuiTest(mc);
            driveSpawnerTest(mc);
        }
        if (launched || event.phase != TickEvent.Phase.END || !(mc.currentScreen instanceof GuiMainMenu)) {
            return;
        }
        if (++menuTicks < 40) {
            return;
        }
        launched = true;
        String name = "fc-selftest-" + System.currentTimeMillis();
        WorldSettings settings = new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
        mc.gameSettings.hideGUI = true; // clean gallery screenshots from the first one
        mc.launchIntegratedServer(name, name, settings);
    }
}
