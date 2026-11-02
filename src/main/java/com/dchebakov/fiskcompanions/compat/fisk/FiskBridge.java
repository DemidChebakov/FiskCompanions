package com.dchebakov.fiskcompanions.compat.fisk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.fiskmods.heroes.FiskHeroes;
import com.fiskmods.heroes.common.data.var.DataVar;
import com.fiskmods.heroes.common.data.var.DataVarInterp;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.entity.attribute.ArmorAttribute;
import com.fiskmods.heroes.common.hero.Hero;
import com.fiskmods.heroes.common.hero.HeroIteration;
import com.fiskmods.heroes.common.hero.HeroTracker;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;
import com.fiskmods.heroes.common.hero.power.prop.Cooldown;
import com.fiskmods.heroes.common.hero.power.prop.Transformation;
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

    /** One-line dump of the transformation and size state of a suited entity, for dev diagnostics. */
    public static String debugState(EntityLivingBase entity) {
        Hero hero = HeroTracker.get(entity);
        StringBuilder sb = new StringBuilder();
        sb.append("scale=")
            .append(Vars.SCALE.get(entity))
            .append(" sizeState=")
            .append(Vars.SIZE_STATE.get(entity))
            .append(" speeding=")
            .append(Vars.SPEEDING.get(entity))
            .append(" speed=")
            .append(Vars.SPEED.get(entity))
            .append(" sprinting=")
            .append(entity.isSprinting())
            .append(" moving=")
            .append(Vars.MOVING.get(entity))
            .append(" trail=")
            .append(
                com.fiskmods.heroes.common.hero.speedster.TrailHandler.getTrail(entity)
                    .size());
        if (hero != null) {
            hero.getModifierEntries()
                .filter(e -> e.modifier == Modifier.TRANSFORMATION)
                .forEach(e -> {
                    Transformation t = prop(e, PowerProperty.TRANSFORMATION, null);
                    if (t != null && t.toggle.hasResult()) {
                        sb.append(" transform[enabled=")
                            .append(hero.isModifierEnabled(entity, e))
                            .append(' ')
                            .append(
                                t.toggle.get()
                                    .get(entity));
                        if (t.timer.hasResult()) {
                            sb.append(" timer=")
                                .append(
                                    t.timer.get()
                                        .get(entity));
                        }
                        sb.append(']');
                    }
                });
        }
        return sb.toString();
    }

    public enum SuitUp {
        /** Already on, or not a toggle transformation. */
        NONE,
        /** Switched on: it unlocks more powers or stronger punches. */
        KEPT,
        /** Switched back off: for this hero it means "suit off" (e.g. SecretHeroes speedster suits). */
        REVERTED
    }

    public static Transformation transformation(ModifierEntry entry) {
        return prop(entry, PowerProperty.TRANSFORMATION, null);
    }

    /**
     * Enabled, toggle-able TRANSFORMATION entries of the hero (collected, since trying them changes what's enabled).
     */
    public static List<ModifierEntry> toggleTransformations(EntityLivingBase entity, Hero hero) {
        List<ModifierEntry> list = new ArrayList<>();
        hero.getEnabledModifiers(entity, Modifier.TRANSFORMATION, PowerProperty.IS_TOGGLE.isValue(true))
            .forEach(list::add);
        return list;
    }

    /**
     * Turns the transformation's toggle on the way a player's transform key would, and keeps it only if the hero ends
     * up with more enabled powers or stronger punches; otherwise it is turned back off. Does not sync.
     */
    public static SuitUp trySuitUp(EntityLivingBase entity, Hero hero, ModifierEntry entry) {
        Transformation t = transformation(entry);
        if (t == null || !t.toggle.hasResult()) {
            return SuitUp.NONE;
        }
        DataVar<Boolean> toggle = t.toggle.get();
        if (toggle.get(entity)) {
            return SuitUp.NONE;
        }
        int before = hero.getEnabledModifiers(entity)
            .size();
        float punchBefore = ArmorAttribute.PUNCH_DAMAGE.get(entity, hero, 1.0F);
        if (!toggle.set(entity, true)
            .success()) {
            traceSuitUp(entity, entry, "set-failed", before, -1, punchBefore, -1.0F);
            return SuitUp.NONE;
        }
        // Many pack suits (e.g. sind's Iron Spider) enable their powers only once the transformation's timer has run
        // to 1, not when the toggle flips. Judge the finished state, then put the timer back so it animates as usual.
        DataVar<Float> timer = t.timer.hasResult() ? t.timer.get() : null;
        float timerBefore = timer != null ? timer.get(entity) : 0.0F;
        if (timer != null) {
            timer.set(entity, 1.0F);
        }
        int after = hero.getEnabledModifiers(entity)
            .size();
        float punchAfter = ArmorAttribute.PUNCH_DAMAGE.get(entity, hero, 1.0F);
        if (timer != null) {
            timer.set(entity, timerBefore);
        }
        if (after <= before && punchAfter <= punchBefore) {
            toggle.set(entity, false);
            traceSuitUp(entity, entry, "REVERTED", before, after, punchBefore, punchAfter);
            return SuitUp.REVERTED;
        }
        traceSuitUp(entity, entry, "KEPT", before, after, punchBefore, punchAfter);
        return SuitUp.KEPT;
    }

    /**
     * Switches a transformation that was kept earlier back on after something turned it off, e.g. Fisk's cooldown
     * modifier (Iron Man Mk50/Mk85 nanites run out after 5 minutes). Waits while any of the hero's cooldowns is still
     * recharging, or Fisk would switch it straight off again. Returns true if it was switched on (not synced).
     */
    public static boolean reArm(EntityLivingBase entity, Hero hero, ModifierEntry entry) {
        Transformation t = transformation(entry);
        if (t == null || !t.toggle.hasResult()
            || t.toggle.get()
                .get(entity)) {
            return false;
        }
        boolean recharging = hero.getModifierEntries()
            .filter(e -> e.modifier == Modifier.COOLDOWN)
            .anyMatch(e -> {
                Cooldown c = prop(e, PowerProperty.COOLDOWN, null);
                return c != null && c.cooldown.hasResult()
                    && c.toggle.hasResult()
                    && !c.toggle.get(entity)
                    && c.cooldown.get()
                        .get(entity) > 0.0F;
            });
        return !recharging && t.toggle.get()
            .set(entity, true)
            .success();
    }

    private static final boolean TRACE_SUITUP = System.getProperty("fiskcompanions.trace") != null
        || Boolean.getBoolean("fiskcompanions.selftest");

    private static void traceSuitUp(EntityLivingBase entity, ModifierEntry entry, String result, int before, int after,
        float punchBefore, float punchAfter) {
        if (TRACE_SUITUP) {
            com.dchebakov.fiskcompanions.FiskCompanions.LOG.info(
                "[suitup] {} {} {} {}: modifiers {} -> {}, punch {} -> {}",
                entity.worldObj.isRemote ? "client" : "server",
                keyOf(HeroTracker.iter(entity)),
                entry.id,
                result,
                before,
                after,
                punchBefore,
                punchAfter);
        }
    }

    /**
     * Client-only preview: shows the hero fully suited, i.e. kept transformations with their timers finished and
     * size-changing heroes at full size. Nothing is synced.
     */
    public static void previewSuitUp(EntityLivingBase entity) {
        Hero hero = HeroTracker.get(entity);
        if (hero == null) {
            return;
        }
        boolean transformed = false;
        for (ModifierEntry entry : toggleTransformations(entity, hero)) {
            if (trySuitUp(entity, hero, entry) == SuitUp.KEPT) {
                transformed = true;
                Transformation t = transformation(entry);
                if (t.timer.hasResult()) {
                    DataVar<Float> timer = t.timer.get();
                    timer.set(entity, 1.0F);
                    if (timer instanceof DataVarInterp) {
                        // Renderers read the interpolated value: finish the previous tick's value too.
                        ((DataVarInterp<Float>) timer).getPrevData()
                            .set(entity, 1.0F);
                    }
                }
            }
        }
        ModifierEntry size = hero.getEnabledModifier(entity, Modifier.SIZE_MANIPULATION);
        if (transformed && size != null) {
            Vars.SCALE.set(entity, prop(size, PowerProperty.MAX_SIZE, 1.0F));
        }
    }

    /** True if the hero has a flight modifier (mobs fly with any of them). */
    public static boolean canFly(HeroIteration iter) {
        return iter != null && (iter.hero.getModifiers()
            .contains(Modifier.FLIGHT)
            || iter.hero.getModifiers()
                .contains(Modifier.CONTROLLED_FLIGHT)
            || iter.hero.getModifiers()
                .contains(Modifier.PROPELLED_FLIGHT));
    }

    /** True if the hero's set includes a helmet, so the mob's head is covered by the suit. */
    public static boolean hasHelmet(HeroIteration iter) {
        return iter != null && iter.hero.hasPieceOfSet(0);
    }

    /**
     * {@link #allIterations()} narrowed by the dev property {@code fiskcompanions.selftest.only} (comma-separated key
     * substrings), used by the self-test and gallery.
     */
    public static List<HeroIteration> testIterations() {
        List<HeroIteration> list = allIterations();
        String only = System.getProperty("fiskcompanions.selftest.only", "");
        if (!only.isEmpty()) {
            String[] parts = only.split(",");
            list.removeIf(i -> {
                for (String p : parts) {
                    if (keyOf(i).contains(p.trim())) return false;
                }
                return true;
            });
        }
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

    /** Fisk's per-tick data update (interpolation, resets) without any syncing; used by the client-only preview. */
    public static void updateDataLocally(Entity entity) {
        DataVar.onUpdate(entity);
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

    /** First entry the hero's suit declares for any of the modifiers, enabled or not. */
    public static ModifierEntry declared(EntityLivingBase entity, Modifier... modifiers) {
        Hero hero = HeroTracker.get(entity);
        if (hero == null) {
            return null;
        }
        for (Modifier modifier : modifiers) {
            ModifierEntry entry = hero.getModifierEntries()
                .filter(e -> e.modifier == modifier)
                .findFirst()
                .orElse(null);
            if (entry != null) {
                return entry;
            }
        }
        return null;
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
