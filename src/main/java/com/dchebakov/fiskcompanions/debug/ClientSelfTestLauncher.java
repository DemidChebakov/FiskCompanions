package com.dchebakov.fiskcompanions.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.entity.Entity;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.client.gui.GuiHeroConfigurator;
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

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        String shot = event.phase == TickEvent.Phase.END ? Gallery.takeShotRequest() : null;
        if (shot != null && mc.theWorld != null) {
            mc.gameSettings.hideGUI = true;
            for (Object o : mc.theWorld.loadedEntityList) {
                if (o instanceof EntityFiskHero) {
                    EntityFiskHero mob = (EntityFiskHero) o;
                    FiskCompanions.LOG.info("[gallery] client {} {}", mob.getHeroKey(), FiskBridge.debugState(mob));
                }
            }
            ScreenShotHelper
                .saveScreenshot(mc.mcDataDir, shot + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
        }
        if (event.phase == TickEvent.Phase.END && mc.theWorld != null) {
            driveGuiTest(mc);
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
