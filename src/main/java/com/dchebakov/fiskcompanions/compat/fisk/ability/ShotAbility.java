package com.dchebakov.fiskcompanions.compat.fisk.ability;

import net.minecraft.entity.EntityLivingBase;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;

/**
 * A one-shot power (Fisk's {@code Interaction*} classes). Those take {@code EntityPlayer}, so each {@link Fire} is a
 * port of the interaction's server branch that works for any {@link EntityLivingBase}, using the hero's own modifier
 * properties (damage profile, radius, range) so heropack tuning is kept.
 */
public final class ShotAbility {

    @FunctionalInterface
    public interface Fire {

        /** Caller has already turned the shooter to face {@code target}. */
        void fire(EntityLivingBase shooter, EntityLivingBase target, ModifierEntry entry);
    }

    public final Modifier modifier;
    private final float defaultRange;
    private final boolean usesRadiusAsRange;
    private final Fire fire;

    public ShotAbility(Modifier modifier, float defaultRange, boolean usesRadiusAsRange, Fire fire) {
        this.modifier = modifier;
        this.defaultRange = defaultRange;
        this.usesRadiusAsRange = usesRadiusAsRange;
        this.fire = fire;
    }

    public float range(ModifierEntry entry) {
        return FiskBridge.prop(entry, usesRadiusAsRange ? PowerProperty.RADIUS : PowerProperty.RANGE, defaultRange);
    }

    /** The modifier's own cooldown, never faster than twice a second. */
    public int cooldown(ModifierEntry entry) {
        return Math.max(10, FiskBridge.prop(entry, PowerProperty.COOLDOWN_TIME, 20));
    }

    public void fire(EntityLivingBase shooter, EntityLivingBase target, ModifierEntry entry) {
        fire.fire(shooter, target, entry);
    }
}
