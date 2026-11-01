package com.dchebakov.fiskcompanions.item;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.Facing;
import net.minecraft.world.World;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.hero.HeroIteration;

/**
 * Right-click a hero mob to open the configurator (handled in {@link EntityFiskHero#interact}). Sneak + right-click a
 * block to summon a new hero bound to you.
 */
public class ItemHeroConfigurator extends Item {

    public ItemHeroConfigurator() {
        setUnlocalizedName("fiskcompanions.hero_configurator");
        setTextureName("fiskcompanions:hero_configurator");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(1);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
        float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        List<HeroIteration> heroes = FiskBridge.allIterations();
        if (heroes.isEmpty()) {
            player.addChatMessage(new ChatComponentTranslation("fiskcompanions.msg.no_heroes"));
            return true;
        }
        // Prefer suits with a helmet for random summons; heroes without one show the bare Steve head.
        List<HeroIteration> helmeted = new ArrayList<>();
        for (HeroIteration iter : heroes) {
            if (FiskBridge.hasHelmet(iter)) {
                helmeted.add(iter);
            }
        }
        if (!helmeted.isEmpty()) {
            heroes = helmeted;
        }
        EntityFiskHero hero = new EntityFiskHero(world);
        hero.setLocationAndAngles(
            x + Facing.offsetsXForSide[side] + 0.5D,
            y + Facing.offsetsYForSide[side],
            z + Facing.offsetsZForSide[side] + 0.5D,
            player.rotationYaw + 180.0F,
            0.0F);
        hero.setAlignment(Alignment.HERO);
        hero.setOwnerId(player.getUniqueID());
        hero.setHero(heroes.get(world.rand.nextInt(heroes.size())));
        world.spawnEntityInWorld(hero);
        return true;
    }
}
