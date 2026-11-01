package com.dchebakov.fiskcompanions.compat.fisk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.fiskmods.heroes.FiskHeroes;
import com.fiskmods.heroes.common.hero.Hero;
import com.fiskmods.heroes.common.hero.HeroIteration;
import com.fiskmods.heroes.common.hero.HeroTracker;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;
import com.fiskmods.heroes.util.SHHelper;

import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Hero registry, suit and tick access. Ability ports live in {@code compat.fisk.ability}; everything else in the mod
 * goes through here so a Fisk update only breaks this package.
 */
public final class FiskBridge {

    private FiskBridge() {}

    public static HeroIteration lookup(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            return HeroIteration.lookup(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public static String keyOf(HeroIteration iter) {
        return iter != null ? iter.getName() : "";
    }

    /** Every loaded hero iteration (Fisk's own and all heropacks), grouped by pack domain. */
    public static List<HeroIteration> allIterations() {
        List<HeroIteration> list = new ArrayList<>();
        for (Hero hero : Hero.REGISTRY.getValues()) {
            list.addAll(hero.getIterations());
        }
        list.sort(
            Comparator.comparing((HeroIteration i) -> i.hero.getDomain())
                .thenComparing(FiskBridge::displayName));
        return list;
    }

    public static String displayName(HeroIteration iter) {
        if (iter == null) {
            return "";
        }
        String name = iter.hero.getLocalizedName();
        return iter.hasName() ? name + " (" + iter.getLocalizedIterName() + ")" : name;
    }

    public static String domain(HeroIteration iter) {
        return iter != null ? iter.hero.getDomain() : "";
    }

    /** Puts the full suit on the entity; slots the hero has no piece for are cleared. */
    public static void equip(EntityLivingBase entity, HeroIteration iter) {
        ItemStack[] stacks = iter.createArmorStacks();
        for (int i = 0; i < 4; ++i) {
            entity.setCurrentItemOrArmor(4 - i, stacks[i]);
        }
    }

    public static HeroIteration computeWornSuit(EntityLivingBase entity) {
        return HeroTracker.getHeroIter(SHHelper.getEquipment(entity), null);
    }

    public static void tickSuit(EntityLivingBase entity, HeroIteration iter, TickEvent.Phase phase) {
        FiskHeroes.proxy.tickSuitEntity(entity, iter, phase);
    }

    /** True if the entity (player or tracked mob) wears a complete Fisk suit. */
    public static boolean isSuited(Entity entity) {
        return HeroTracker.has(entity);
    }

    public static Hero hero(Entity entity) {
        return HeroTracker.get(entity);
    }

    public static ModifierEntry enabled(EntityLivingBase entity, Modifier modifier) {
        Hero hero = HeroTracker.get(entity);
        return hero != null ? hero.getEnabledModifier(entity, modifier) : null;
    }

    public static boolean has(EntityLivingBase entity, Modifier modifier) {
        return enabled(entity, modifier) != null;
    }

    /**
     * Property value from a modifier entry, or {@code fallback} when the modifier doesn't declare it.
     * {@code ModifierEntry.get} throws for undeclared properties, so check the modifier's declared set first.
     */
    public static <T> T prop(ModifierEntry entry, PowerProperty<T> property, T fallback) {
        if (!entry.modifier.getProperties()
            .containsKey(property)) {
            return fallback;
        }
        T value = entry.get(property);
        return value != null ? value : fallback;
    }
}
