package com.dchebakov.fiskcompanions;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;

import com.dchebakov.fiskcompanions.debug.CommandSelfTest;
import com.dchebakov.fiskcompanions.debug.Gallery;
import com.dchebakov.fiskcompanions.debug.SelfTest;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.entity.OwnerHits;
import com.dchebakov.fiskcompanions.item.ItemHeroConfigurator;
import com.dchebakov.fiskcompanions.item.ItemShieldPose;
import com.dchebakov.fiskcompanions.network.PacketConfigureHero;
import com.dchebakov.fiskcompanions.network.PacketRemoveAll;
import com.dchebakov.fiskcompanions.network.PacketSettingsSync;
import com.dchebakov.fiskcompanions.network.PacketSpawnHeroes;
import com.dchebakov.fiskcompanions.network.PacketUpdateSettings;

import cpw.mods.fml.common.FMLCommonHandler;
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
    /** Render-only stand-in for a blocking character's arm pose; never in an inventory. */
    public static ItemShieldPose shieldPose;

    public void preInit(FMLPreInitializationEvent event) {
        configurator = new ItemHeroConfigurator();
        GameRegistry.registerItem(configurator, "hero_configurator");
        shieldPose = new ItemShieldPose();
        GameRegistry.registerItem(shieldPose, "shield_pose");

        MinecraftForge.EVENT_BUS.register(new OwnerHits());
        EntityRegistry.registerModEntity(EntityFiskHero.class, "FiskHero", 0, FiskCompanions.instance, 80, 1, true);

        network = NetworkRegistry.INSTANCE.newSimpleChannel(FiskCompanions.MODID);
        network.registerMessage(PacketConfigureHero.Handler.class, PacketConfigureHero.class, 0, Side.SERVER);
        network.registerMessage(PacketSpawnHeroes.Handler.class, PacketSpawnHeroes.class, 1, Side.SERVER);
        network.registerMessage(PacketUpdateSettings.Handler.class, PacketUpdateSettings.class, 2, Side.SERVER);
        network.registerMessage(PacketSettingsSync.Handler.class, PacketSettingsSync.class, 3, Side.CLIENT);
        network.registerMessage(PacketRemoveAll.Handler.class, PacketRemoveAll.class, 4, Side.SERVER);
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandSelfTest());
    }

    /** {@code -Dfiskcompanions.selftest=true} runs the self-test on startup and stops the server when done. */
    public void serverStarted(FMLServerStartedEvent event) {
        if (Boolean.getBoolean("fiskcompanions.selftest")) {
            MinecraftServer server = MinecraftServer.getServer();
            if (server.isDedicatedServer()) {
                SelfTest.start(server, true);
            } else {
                Gallery.start(server, () -> {
                    if (Gallery.DIM_ONLY) {
                        FMLCommonHandler.instance()
                            .exitJava(0, false);
                    } else {
                        SelfTest.start(server, true); // screenshots first, then behaviour
                    }
                });
            }
        }
    }

    /** Client-only: opens the configurator screen. No-op on a dedicated server. */
    public void openConfigurator(EntityFiskHero hero) {}

    /** Client-only: opens the spawner menu. No-op on a dedicated server. */
    public void openSpawner() {}
}
