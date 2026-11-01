package com.dchebakov.fiskcompanions.world;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.BaseSkin;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.hero.HeroIteration;

/** Spawns characters for a player: in an arc a few blocks in front, each on a free spot. Server side only. */
public final class Spawning {

    /** One character to spawn. */
    public static final class Entry {

        public final String heroKey;
        public final Alignment alignment;
        public final BaseSkin skin;

        public Entry(String heroKey, Alignment alignment, BaseSkin skin) {
            this.heroKey = heroKey;
            this.alignment = alignment;
            this.skin = skin;
        }
    }

    public static final int MAX_PER_REQUEST = 30;

    private Spawning() {}

    /** Returns how many characters were spawned (unknown hero keys are skipped). */
    public static int spawn(EntityPlayer player, List<Entry> entries, boolean bindToPlayer) {
        World world = player.worldObj;
        int count = Math.min(entries.size(), MAX_PER_REQUEST);
        int spawned = 0;
        for (int i = 0; i < count; ++i) {
            Entry entry = entries.get(i);
            HeroIteration iter = FiskBridge.lookup(entry.heroKey);
            if (iter == null) {
                continue;
            }
            // Spread over an arc 3-5 blocks in front of the player, wider for bigger groups.
            double spread = Math.min(160.0D, 25.0D * (count - 1));
            double angle = player.rotationYaw + (count == 1 ? 0.0D : -spread / 2.0D + spread * i / (count - 1));
            double dist = 3.0D + (i % 2) * 1.5D;
            double rad = Math.toRadians(angle);
            int x = MathHelper.floor_double(player.posX - Math.sin(rad) * dist);
            int z = MathHelper.floor_double(player.posZ + Math.cos(rad) * dist);
            int y = findFloor(world, x, MathHelper.floor_double(player.boundingBox.minY), z);
            EntityFiskHero hero = new EntityFiskHero(world);
            hero.setLocationAndAngles(x + 0.5D, y, z + 0.5D, player.rotationYaw + 180.0F, 0.0F);
            hero.setAlignment(entry.alignment);
            if (bindToPlayer && entry.alignment == Alignment.HERO) {
                hero.setOwnerId(player.getUniqueID());
            }
            hero.setSkin(entry.skin, player.getCommandSenderName());
            hero.setHero(iter);
            world.spawnEntityInWorld(hero);
            ++spawned;
        }
        return spawned;
    }

    /** Nearest spot within a few blocks of {@code y} with two blocks of air above solid ground. */
    private static int findFloor(World world, int x, int y, int z) {
        for (int d = 0; d <= 4; ++d) {
            for (int dy : new int[] { d, -d }) {
                int yy = y + dy;
                if (World.doesBlockHaveSolidTopSurface(world, x, yy - 1, z) && !world.getBlock(x, yy, z)
                    .isNormalCube()
                    && !world.getBlock(x, yy + 1, z)
                        .isNormalCube()) {
                    return yy;
                }
            }
        }
        return y;
    }
}
