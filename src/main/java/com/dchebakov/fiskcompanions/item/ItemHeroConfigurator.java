package com.dchebakov.fiskcompanions.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.dchebakov.fiskcompanions.CommonProxy;
import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.network.PacketSettingsSync;

/**
 * Right-click anywhere (air or ground) to open the spawner menu; right-click a character to open its configurator
 * (handled in {@link EntityFiskHero#interact}, which runs first).
 */
public class ItemHeroConfigurator extends Item {

    public ItemHeroConfigurator() {
        setUnlocalizedName("fiskcompanions.hero_configurator");
        setTextureName("fiskcompanions:hero_configurator");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        openSpawner(world, player);
        return stack;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        openSpawner(world, player); // aiming at a block: open the menu too instead of doing nothing
        return true;
    }

    private static void openSpawner(World world, EntityPlayer player) {
        if (world.isRemote) {
            FiskCompanions.proxy.openSpawner();
        } else if (player instanceof EntityPlayerMP) {
            // So the Settings tab shows this world's friendly-fire rules and whether the player may change them.
            CommonProxy.network.sendTo(PacketSettingsSync.of((EntityPlayerMP) player), (EntityPlayerMP) player);
        }
    }
}
