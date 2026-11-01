package com.dchebakov.fiskcompanions;

import net.minecraft.server.MinecraftServer;

import com.dchebakov.fiskcompanions.debug.CommandSelfTest;
import com.dchebakov.fiskcompanions.debug.SelfTest;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.item.ItemHeroConfigurator;
import com.dchebakov.fiskcompanions.network.PacketConfigureHero;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.registry.EntityRegistry;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;

public class CommonProxy {

    public static SimpleNetworkWrapper network;
    public static ItemHeroConfigurator configurator;

    public void preInit(FMLPreInitializationEvent event) {
        configurator = new ItemHeroConfigurator();
        GameRegistry.registerItem(configurator, "hero_configurator");

        EntityRegistry.registerModEntity(EntityFiskHero.class, "FiskHero", 0, FiskCompanions.instance, 80, 1, true);

        network = NetworkRegistry.INSTANCE.newSimpleChannel(FiskCompanions.MODID);
        network.registerMessage(PacketConfigureHero.Handler.class, PacketConfigureHero.class, 0, Side.SERVER);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandSelfTest());
    }

    /** {@code -Dfiskcompanions.selftest=true} runs the self-test on startup and stops the server when done. */
    public void serverStarted(FMLServerStartedEvent event) {
        if (Boolean.getBoolean("fiskcompanions.selftest")) {
            SelfTest.start(MinecraftServer.getServer(), true);
        }
    }

    /** Client-only: opens the configurator screen. No-op on a dedicated server. */
    public void openConfigurator(EntityFiskHero hero) {}
}
