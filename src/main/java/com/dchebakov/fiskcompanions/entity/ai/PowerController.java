package com.dchebakov.fiskcompanions.entity.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MathHelper;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.compat.fisk.ability.Abilities;
import com.dchebakov.fiskcompanions.compat.fisk.ability.ShotAbility;
import com.dchebakov.fiskcompanions.compat.fisk.ability.ToggleAbility;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.DataVar;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.Hero;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;

/**
 * Server-side "key presses" for the mob: flips toggle powers, fires one-shot powers at the attack target, and heals a
 * suited owner. Runs every tick from {@link EntityFiskHero#onUpdate()}, outside the AI task system so it never fights
 * movement tasks for mutex bits.
 */
public class PowerController {

    private static final int GLOBAL_COOLDOWN = 10;
    private static final int RECENT_HURT_TICKS = 60;
    private static final int HEAL_COOLDOWN = 400;

    private final EntityFiskHero hero;
    private final Map<Modifier, Integer> cooldowns = new HashMap<>();
    /** Modifiers whose port threw on this mob; skipped so one broken pack power doesn't spam the log. */
    private final Set<Modifier> broken = new HashSet<>();
    private int globalCooldown;
    private int healCooldown;
    private int lastHurtTick = -RECENT_HURT_TICKS;
    /** How often each power was used; read by the self-test and handy when debugging a pack hero. */
    private final Map<String, Integer> usage = new TreeMap<>();

    public PowerController(EntityFiskHero hero) {
        this.hero = hero;
    }

    public void record(String what) {
        usage.merge(what, 1, Integer::sum);
    }

    public Map<String, Integer> getUsage() {
        return usage;
    }

    public void onHurt() {
        lastHurtTick = hero.ticksExisted;
    }

    /** Switches every power off, e.g. after the hero or alignment changes. */
    public void reset() {
        for (ToggleAbility toggle : Abilities.TOGGLES) {
            setVar(toggle.var, false);
        }
        setVar(Vars.AIMING, false);
        cooldowns.clear();
        broken.clear();
    }

    public void update() {
        cooldowns.replaceAll((m, t) -> t - 1);
        cooldowns.values()
            .removeIf(t -> t <= 0);
        if (globalCooldown > 0) --globalCooldown;
        if (healCooldown > 0) --healCooldown;

        Hero h = FiskBridge.hero(hero);
        if (h == null) {
            return;
        }
        EntityLivingBase target = hero.getAttackTarget();
        if (target != null && !target.isEntityAlive()) {
            target = null;
        }
        double dist = target != null ? hero.getDistanceToEntity(target) : Double.MAX_VALUE;
        boolean sees = target != null && hero.getEntitySenses()
            .canSee(target);

        // Many heroes only enable their blasts while aiming, so raise the pose before asking what is enabled.
        boolean ranged = h.getModifiers()
            .stream()
            .anyMatch(Abilities::isRanged);
        setVar(Vars.AIMING, sees && ranged && dist > 3.0D);

        boolean beaming = updateToggles(h, target, dist, sees);
        if (beaming) {
            face(target);
        }
        if (sees && globalCooldown <= 0) {
            fireShot(h, target, dist);
        }
        healOwner();
    }

    private boolean updateToggles(Hero h, EntityLivingBase target, double dist, boolean sees) {
        boolean beaming = false;
        for (ToggleAbility toggle : Abilities.TOGGLES) {
            if (!h.getModifiers()
                .contains(toggle.modifier)) {
                continue;
            }
            ModifierEntry entry = h.getEnabledModifier(hero, toggle.modifier);
            boolean want = false;
            if (entry != null && target != null) {
                switch (toggle.mode) {
                    case BEAM:
                        want = sees && dist <= toggle.range(entry);
                        break;
                    case MELEE:
                        want = dist <= toggle.range(entry);
                        break;
                    case DEFENSIVE:
                        want = hero.ticksExisted - lastHurtTick < RECENT_HURT_TICKS;
                        break;
                }
            }
            if (setVar(toggle.var, want) && want) {
                record("toggle:" + toggle.modifier.getName());
            }
            beaming |= want && toggle.mode == ToggleAbility.Mode.BEAM;
        }
        return beaming;
    }

    private void fireShot(Hero h, EntityLivingBase target, double dist) {
        for (ShotAbility shot : Abilities.SHOTS) {
            if (cooldowns.containsKey(shot.modifier) || broken.contains(shot.modifier)) {
                continue;
            }
            ModifierEntry entry = h.getEnabledModifier(hero, shot.modifier);
            if (entry == null || dist > shot.range(entry)) {
                continue;
            }
            face(target);
            try {
                shot.fire(hero, target, entry);
            } catch (RuntimeException e) {
                broken.add(shot.modifier);
                FiskCompanions.LOG.warn(
                    "Power {} failed for hero {}; disabled on this mob",
                    shot.modifier.getName(),
                    hero.getHeroKey(),
                    e);
                continue;
            }
            record("shot:" + shot.modifier.getName());
            cooldowns.put(shot.modifier, shot.cooldown(entry));
            globalCooldown = GLOBAL_COOLDOWN;
            return;
        }
    }

    private void healOwner() {
        if (healCooldown > 0 || hero.getAlignment() != Alignment.HERO) {
            return;
        }
        EntityPlayer owner = hero.getOwner();
        if (owner != null && FiskBridge.isSuited(owner)
            && owner.getHealth() < owner.getMaxHealth() * 0.6F
            && hero.getDistanceSqToEntity(owner) < 64.0D) {
            owner.addPotionEffect(new PotionEffect(Potion.regeneration.id, 100, 1));
            hero.worldObj.playSoundAtEntity(owner, "random.orb", 0.6F, 1.5F);
            healCooldown = HEAL_COOLDOWN;
        }
    }

    /** Fisk aims beams and projectiles along the look vector, so turn body, head and pitch to the target. */
    private void face(EntityLivingBase target) {
        double dx = target.posX - hero.posX;
        double dy = target.boundingBox.minY + target.height * 0.6D - (hero.posY + hero.getEyeHeight());
        double dz = target.posZ - hero.posZ;
        double horiz = MathHelper.sqrt_double(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        float pitch = (float) -(Math.atan2(dy, horiz) * 180.0D / Math.PI);
        hero.rotationYaw = hero.rotationYawHead = hero.renderYawOffset = yaw;
        hero.rotationPitch = pitch;
    }

    /** Returns true if the value changed. */
    private boolean setVar(DataVar<Boolean> var, boolean value) {
        if (var.get(hero) == value) {
            return false;
        }
        var.set(hero, value)
            .sync();
        return true;
    }
}
