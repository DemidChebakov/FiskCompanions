package com.dchebakov.fiskcompanions.entity.ai;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.client.pack.json.sound.SoundTrigger;
import com.fiskmods.heroes.common.arrowtype.ArrowType;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.entity.projectile.EntityThrownShield;
import com.fiskmods.heroes.common.hero.HeroIteration;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;
import com.fiskmods.heroes.common.item.ModItems;
import com.fiskmods.heroes.common.item.weapon.IGun;
import com.fiskmods.heroes.common.item.weapon.IReloadableWeapon;
import com.fiskmods.heroes.common.item.weapon.ItemCapsShield;
import com.fiskmods.heroes.common.item.weapon.ItemCompoundBow;
import com.fiskmods.heroes.common.item.weapon.ItemGun;
import com.fiskmods.heroes.util.FiskServerUtils;

/**
 * The suit's own hand weapons (its "primary equipment": Deadpool's katana and Desert Eagle, Captain America's shield,
 * …). Holds the melee weapon up close and switches to the gun at range; melee damage then comes from Fisk's punch
 * system with the held weapon. Guns fire through Fisk's {@code ItemGun.trigger}, which works for any entity but is
 * protected (Fisk's own method name, so reflection is safe in production), with infinite ammo and the gun's cooldown.
 */
public class WeaponController {

    private static final double GUN_MIN_RANGE = 4.0D;
    private static final Method TRIGGER = findTrigger();
    private static final Field SHIELD_ITEM = findShieldItem();
    private static final int BOW_COOLDOWN = 30;
    private static final double SHIELD_RANGE = 20.0D;
    private static final int SHIELD_OUT_TICKS = 12;
    private static final int SHIELD_MAX_TICKS = 60;
    private static final int ARROW_NORMAL = 0, ARROW_EXPLOSIVE = 1, ARROW_TRIPLE = 3, ARROW_FIRE_CHARGE = 6,
        ARROW_BOXING_GLOVE = 8;

    private static Field findShieldItem() {
        try {
            Field f = EntityThrownShield.class.getDeclaredField("shieldItem"); // Fisk's own name
            f.setAccessible(true);
            return f;
        } catch (ReflectiveOperationException | RuntimeException e) {
            FiskCompanions.LOG.warn("Thrown shield item field not found; characters won't throw shields", e);
            return null;
        }
    }

    private final EntityFiskHero hero;
    private String loadedFor = "";
    private ItemStack melee;
    private ItemStack gun;
    private int gunCooldown;
    private boolean gunBroken;
    private ItemStack bow;
    private int bowCooldown;
    private boolean bowBroken;
    private EntityThrownShield thrownShield;
    private ItemStack thrownShieldStack;
    private int shieldTicks;
    private int shieldCooldown;
    private boolean shieldBroken;

    public WeaponController(EntityFiskHero hero) {
        this.hero = hero;
    }

    private static Method findTrigger() {
        try {
            Method m = ItemGun.class.getDeclaredMethod(
                "trigger",
                ItemStack.class,
                EntityLivingBase.class,
                HeroIteration.class,
                int.class,
                boolean.class);
            m.setAccessible(true);
            return m;
        } catch (ReflectiveOperationException | RuntimeException e) {
            FiskCompanions.LOG.warn("Fisk gun trigger not found; characters won't fire guns", e);
            return null;
        }
    }

    /** Captain America's shield in hand (not in flight). */
    public boolean holdsShield() {
        ItemStack held = hero.getHeldItem();
        return thrownShield == null && held != null && held.getItem() instanceof ItemCapsShield;
    }

    public boolean holdsGun() {
        ItemStack held = hero.getHeldItem();
        return held != null && held.getItem() instanceof IGun;
    }

    public void update() {
        HeroIteration iter = hero.getWornSuit();
        if (iter == null) {
            return;
        }
        String key = FiskBridge.keyOf(iter);
        if (!key.equals(loadedFor)) {
            loadWeapons(iter);
            loadedFor = key;
        }
        if (gunCooldown > 0) --gunCooldown;
        if (bowCooldown > 0) --bowCooldown;
        if (shieldCooldown > 0) --shieldCooldown;
        if (thrownShield != null) {
            steerThrownShield(); // the hand stays empty until the shield is back
            return;
        }
        EntityLivingBase target = hero.getAttackTarget();
        if (target != null && !target.isEntityAlive()) {
            target = null;
        }
        double dist = target != null ? hero.getDistanceToEntity(target) : 0.0D;
        ItemStack wanted;
        if (target != null && gun != null && !gunBroken && (dist > GUN_MIN_RANGE || melee == null)) {
            wanted = gun;
        } else
            if (bow != null && !bowBroken && (target == null ? melee == null : dist > GUN_MIN_RANGE || melee == null)) {
                wanted = bow;
            } else {
                wanted = melee != null ? melee : gun;
            }
        hold(wanted);
        boolean sees = target != null && hero.getEntitySenses()
            .canSee(target)
            && !hero.getShield()
                .isBlocking(); // no shots from behind a raised shield
        if (sees && holdsBow() && !bowBroken && bowCooldown <= 0 && dist > GUN_MIN_RANGE) {
            shootArrow(target, dist);
        }
        if (sees && !shieldBroken && shieldCooldown <= 0 && dist > GUN_MIN_RANGE && dist < SHIELD_RANGE) {
            throwShield(iter, target);
        }
        if (sees && holdsGun()
            && !gunBroken
            && gunCooldown <= 0
            && hero.getEntitySenses()
                .canSee(target)) {
            fire(iter, target, dist);
        }
    }

    private void loadWeapons(HeroIteration iter) {
        melee = null;
        gun = null;
        gunBroken = false;
        bow = null;
        bowBroken = false;
        // Green Arrow carries his bow as regular (not primary) equipment.
        for (ItemStack stack : iter.hero.getEquipmentStacks()) {
            if (stack != null && stack.getItem() instanceof ItemCompoundBow && bow == null) {
                bow = stack.copy();
            }
        }
        for (ItemStack stack : iter.hero.getWeaponStacks()) {
            if (stack == null) {
                continue;
            }
            if (stack.getItem() instanceof ItemGun) {
                if (gun == null) gun = stack.copy();
            } else if (melee == null && FiskServerUtils.isWeapon(stack)) {
                melee = stack.copy();
            }
        }
        hold(melee != null ? melee : gun != null ? gun : bow);
    }

    private boolean holdsBow() {
        ItemStack held = hero.getHeldItem();
        return held != null && held.getItem() instanceof ItemCompoundBow;
    }

    /**
     * Fisk's bow only fires from a player's draw-and-release; the arrow itself is {@code ArrowType.shoot}, which takes
     * any shooter. Full draw, a trick arrow picked from the combat set, aimed a little high for the arrow's drop.
     */
    private void shootArrow(EntityLivingBase target, double dist) {
        int roll = hero.getRNG()
            .nextInt(100);
        int arrowId = roll < 50 ? ARROW_NORMAL
            : roll < 65 ? ARROW_EXPLOSIVE
                : roll < 80 ? ARROW_TRIPLE : roll < 90 ? ARROW_BOXING_GLOVE : ARROW_FIRE_CHARGE;
        ArrowType<?> type = ArrowType.getArrowById(arrowId);
        if (type == null) {
            type = ArrowType.getArrowById(ARROW_NORMAL);
        }
        hero.faceEntity(target, dist * 0.06D);
        try {
            type.shoot(
                hero.worldObj,
                hero,
                null,
                hero.getHeldItem(),
                new ItemStack(ModItems.trickArrow, 1, arrowId),
                1.0F);
        } catch (RuntimeException e) {
            bowBroken = true;
            FiskCompanions.LOG.warn("Bow failed for hero {}; disabled on this mob", hero.getHeroKey(), e);
            return;
        }
        hero.worldObj.playSoundAtEntity(
            hero,
            "random.bow",
            1.0F,
            1.0F / (hero.getRNG()
                .nextFloat() * 0.4F + 1.2F) + 0.5F);
        hero.swingItem();
        bowCooldown = BOW_COOLDOWN;
        hero.getPowers()
            .record("bow:" + ArrowType.getNameForArrow(type));
    }

    /**
     * Fisk's shield throw (the {@code shield_throwing} power) only returns the shield to a player and otherwise drops
     * it as an item when it dies. So the mob throws Fisk's real thrown shield, steers it back itself after a short
     * flight, and removes it without a drop before re-arming.
     */
    private void throwShield(HeroIteration iter, EntityLivingBase target) {
        ItemStack held = hero.getHeldItem();
        ModifierEntry entry = iter.hero.getEnabledModifier(hero, Modifier.SHIELD_THROWING);
        if (entry == null || held == null || !(held.getItem() instanceof ItemCapsShield) || SHIELD_ITEM == null) {
            return;
        }
        hero.faceEntity(target);
        try {
            thrownShield = new EntityThrownShield(hero.worldObj, hero, iter, held.copy(), false);
            hero.worldObj.spawnEntityInWorld(thrownShield);
        } catch (RuntimeException e) {
            shieldBroken = true;
            thrownShield = null;
            FiskCompanions.LOG.warn("Shield throw failed for hero {}; disabled on this mob", hero.getHeroKey(), e);
            return;
        }
        thrownShieldStack = held.copy();
        hero.setCurrentItemOrArmor(0, null);
        entry.broadcastSoundAtEntity(hero, SoundTrigger.THROW);
        hero.swingItem();
        shieldTicks = 0;
        shieldCooldown = Math.max(40, FiskBridge.prop(entry, PowerProperty.COOLDOWN_TIME, 40));
        hero.getPowers()
            .record("shield_throw");
    }

    private void steerThrownShield() {
        ++shieldTicks;
        if (thrownShield.isEntityAlive() && shieldTicks > SHIELD_OUT_TICKS) {
            double dx = hero.posX - thrownShield.posX;
            double dy = hero.posY + hero.height * 0.6D - thrownShield.posY;
            double dz = hero.posZ - thrownShield.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > 1.8D && shieldTicks < SHIELD_MAX_TICKS) {
                thrownShield.motionX = dx / d * 1.4D;
                thrownShield.motionY = dy / d * 1.4D;
                thrownShield.motionZ = dz / d * 1.4D;
                thrownShield.noClip = true;
                return;
            }
        } else if (thrownShield.isEntityAlive()) {
            return; // still flying out
        }
        discardThrownShield();
        hold(thrownShieldStack); // caught: back in hand
        thrownShieldStack = null;
    }

    /** Removes an in-flight shield without letting it drop as an item (also called when the character dies). */
    public void discardThrownShield() {
        if (thrownShield != null) {
            try {
                SHIELD_ITEM.set(thrownShield, null);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // nothing better to do; the shield may drop
            }
            thrownShield.setDead();
            thrownShield = null;
        }
    }

    private void hold(ItemStack wanted) {
        ItemStack held = hero.getHeldItem();
        boolean same = held == null ? wanted == null : wanted != null && held.getItem() == wanted.getItem();
        if (!same) {
            hero.setCurrentItemOrArmor(0, wanted != null ? wanted.copy() : null);
        }
    }

    private void fire(HeroIteration iter, EntityLivingBase target, double dist) {
        ItemStack stack = hero.getHeldItem();
        ItemGun item = (ItemGun) stack.getItem();
        if (TRIGGER == null || dist > item.getRange(stack, hero)) {
            return;
        }
        hero.faceEntity(target);
        if (IReloadableWeapon.getAmmo(stack) <= 0) {
            IReloadableWeapon.setAmmo(stack, 32); // characters don't reload
        }
        // Fisk counts ticks since the last shot for players only; mark the gun as ready, our cooldown paces it.
        Vars.TICKS_SINCE_GUNSHOT.set(hero, Short.MAX_VALUE);
        boolean dual = stack.hasTagCompound() && stack.getTagCompound()
            .getBoolean("Dual");
        int cooldown = item.getCooldownTime(stack, hero, iter.hero);
        try {
            TRIGGER.invoke(item, stack, hero, iter, cooldown, dual);
        } catch (ReflectiveOperationException | RuntimeException e) {
            gunBroken = true;
            FiskCompanions.LOG.warn("Gun {} failed for hero {}; disabled on this mob", stack, hero.getHeroKey(), e);
            return;
        }
        gunCooldown = Math.max(8, cooldown);
        hero.getPowers()
            .record("gun:" + stack.getUnlocalizedName());
    }
}
