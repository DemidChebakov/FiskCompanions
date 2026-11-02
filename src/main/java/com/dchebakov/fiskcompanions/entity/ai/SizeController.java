package com.dchebakov.fiskcompanions.entity.ai;

import net.minecraft.util.AxisAlignedBB;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;

/**
 * Port of Fisk's {@code ModifierSizeManipulation}, which only runs for players: moves {@code Vars.SCALE} toward the
 * modifier's min or max size following {@code Vars.SIZE_STATE}, keeping health proportional. Without it a hero like
 * Hulk is left half-transformed (his script counts as transformed only from scale 1.4 up). Server side; the scale var
 * is synced and the entity applies it to its hitbox and renderer. Growth waits while the bigger hitbox wouldn't fit
 * (low ceilings, tunnels), so a character never grows into the walls.
 */
public class SizeController {

    private final EntityFiskHero hero;

    public SizeController(EntityFiskHero hero) {
        this.hero = hero;
    }

    /** Starts growing to the modifier's max size, as a size-changing transformation key does for a player. */
    public void grow() {
        if (FiskBridge.enabled(hero, Modifier.SIZE_MANIPULATION) != null && Vars.SIZE_STATE.get(hero) <= 0) {
            Vars.SIZE_STATE.set(hero, (byte) 1)
                .sync();
        }
    }

    public void update() {
        ModifierEntry entry = FiskBridge.enabled(hero, Modifier.SIZE_MANIPULATION);
        if (entry == null) {
            return;
        }
        float min = FiskBridge.prop(entry, PowerProperty.MIN_SIZE, 1.0F);
        float max = FiskBridge.prop(entry, PowerProperty.MAX_SIZE, 1.0F);
        boolean instant = FiskBridge.prop(entry, PowerProperty.IS_INSTANT, false);
        float increment = instant ? (max - min) / 7.0F : (max - min) / 60.0F;
        float scale = Vars.SCALE.get(hero);
        byte state = Vars.SIZE_STATE.get(hero);
        if (!instant && state <= 0) {
            increment *= 2.0F;
        }
        float next = scale;
        if (state > 0) {
            next = Math.min(max, scale + increment);
            if (next > scale && !roomFor(next)) {
                return; // no room to grow (low ceiling, tunnel): hold this size and try again next tick
            }
        } else if (state < 0) {
            next = Math.max(min, scale - increment);
        }
        next = Math.max(min, Math.min(max, next));
        if (next != scale) {
            hero.setHealth(hero.getHealth() * (float) (healthScale(next) / healthScale(scale)));
            Vars.SCALE.set(hero, next)
                .sync();
        } else if (state != 0) {
            Vars.SIZE_STATE.set(hero, (byte) 0)
                .sync(); // reached the target size
        }
    }

    /** The hitbox at {@code scale} (as {@code EntityFiskHero.applyScale} sets it) would not overlap any block. */
    private boolean roomFor(float scale) {
        double hw = 0.6D * scale / 2.0D;
        AxisAlignedBB box = AxisAlignedBB
            .getBoundingBox(
                hero.posX - hw,
                hero.boundingBox.minY,
                hero.posZ - hw,
                hero.posX + hw,
                hero.boundingBox.minY + 1.8D * scale,
                hero.posZ + hw)
            .contract(0.05D, 0.05D, 0.05D);
        return hero.worldObj.func_147461_a(box)
            .isEmpty();
    }

    private static double healthScale(float scale) {
        return Math.pow(Math.max(scale, 1.0F), 1.0D / 3.0D);
    }
}
