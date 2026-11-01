package com.dchebakov.fiskcompanions;

import net.minecraft.client.Minecraft;

import com.dchebakov.fiskcompanions.client.gui.GuiHeroConfigurator;
import com.dchebakov.fiskcompanions.client.render.RenderFiskHero;
import com.dchebakov.fiskcompanions.debug.ClientSelfTestLauncher;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        RenderingRegistry.registerEntityRenderingHandler(EntityFiskHero.class, new RenderFiskHero());
        if (Boolean.getBoolean("fiskcompanions.selftest")) {
            FMLCommonHandler.instance()
                .bus()
                .register(new ClientSelfTestLauncher());
        }
    }

    @Override
    public void openConfigurator(EntityFiskHero hero) {
        Minecraft.getMinecraft()
            .displayGuiScreen(new GuiHeroConfigurator(hero));
    }
}
