package com.dchebakov.fiskcompanions.entity.ai;

import java.lang.reflect.Method;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.HeroIteration;
import com.fiskmods.heroes.common.item.weapon.IGun;
import com.fiskmods.heroes.common.item.weapon.IReloadableWeapon;
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

    private final EntityFiskHero hero;
    private String loadedFor = "";
    private ItemStack melee;
    private ItemStack gun;
    private int gunCooldown;
    private boolean gunBroken;

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
        if (gunCooldown > 0) {
            --gunCooldown;
        }
        EntityLivingBase target = hero.getAttackTarget();
        if (target != null && !target.isEntityAlive()) {
            target = null;
        }
        double dist = target != null ? hero.getDistanceToEntity(target) : 0.0D;
        ItemStack wanted;
        if (target != null && gun != null && !gunBroken && (dist > GUN_MIN_RANGE || melee == null)) {
            wanted = gun;
        } else {
            wanted = melee != null ? melee : gun;
        }
        hold(wanted);
        if (target != null && holdsGun()
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
        hold(melee != null ? melee : gun);
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
