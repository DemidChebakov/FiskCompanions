package com.dchebakov.fiskcompanions.debug;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;

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
    private int menuTicks;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (launched || event.phase != TickEvent.Phase.END || !(mc.currentScreen instanceof GuiMainMenu)) {
            return;
        }
        if (++menuTicks < 40) {
            return;
        }
        launched = true;
        String name = "fc-selftest-" + System.currentTimeMillis();
        WorldSettings settings = new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT);
        mc.launchIntegratedServer(name, name, settings);
    }
}
