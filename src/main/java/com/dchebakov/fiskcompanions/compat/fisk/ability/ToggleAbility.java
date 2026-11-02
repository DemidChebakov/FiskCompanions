package com.dchebakov.fiskcompanions.compat.fisk.ability;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.fiskmods.heroes.common.data.var.DataVar;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;

/**
 * A power a player switches with a key ({@code KeyPressToggleOptional} in Fisk). Fisk's modifier does the work while
 * {@link #var} is true, for any entity, so the mob only decides when to flip it.
 */
public final class ToggleAbility {

    public enum Mode {
        /** On while the target is visible and within the modifier's range (beams). */
        BEAM,
        /** On while fighting at close range (blades, charged fists). */
        MELEE
    }

    public final Modifier modifier;
    public final DataVar<Boolean> var;
    public final Mode mode;
    private final float defaultRange;

    public ToggleAbility(Modifier modifier, DataVar<Boolean> var, Mode mode, float defaultRange) {
        this.modifier = modifier;
        this.var = var;
        this.mode = mode;
        this.defaultRange = defaultRange;
    }

    public float range(ModifierEntry entry) {
        return FiskBridge.prop(entry, PowerProperty.RANGE, defaultRange);
    }
}
