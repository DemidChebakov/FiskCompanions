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
import com.fiskmods.heroes.client.pack.json.sound.SoundTrigger;
import com.fiskmods.heroes.common.data.var.DataVar;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.Hero;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;

/**
 * Server-side "key presses" for the mob: flips toggle powers, fires one-shot powers at the attack target, and heals a
 * suited owner. Runs every tick from {@link EntityFiskHero#onUpdate()}, outside the AI task system so it never fights
 * movement tasks for mutex bits.
 */
public class PowerController {

    private static final int GLOBAL_COOLDOWN = 10;
    private static final int RECENT_HURT_TICKS = 60;
    private static final int HEAL_COOLDOWN = 400;
    private static final int BEAM_BURST_TICKS = 60;
    private static final int BEAM_REST_TICKS = 40;

    private final EntityFiskHero hero;
    private final Map<Modifier, Integer> cooldowns = new HashMap<>();
    private final Map<Modifier, Integer> beamTicks = new HashMap<>();
    /** Modifiers whose port threw on this mob; skipped so one broken pack power doesn't spam the log. */
    private final Set<Modifier> broken = new HashSet<>();
    private int globalCooldown;
    private int healCooldown;
    private int lastHurtTick = -RECENT_HURT_TICKS;
    /** How often each power was used; read by the self-test and handy when debugging a pack hero. */
    private final Map<String, Integer> usage = new TreeMap<>();
    /** Transformation entries already decided by {@link #suitUp}, so each is evaluated once per hero. */
    private final Set<String> triedTransforms = new HashSet<>();

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
        beamTicks.clear();
        broken.clear();
        triedTransforms.clear();
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
        if (hero.ticksExisted % 20 == 1 && !Boolean.getBoolean("fiskcompanions.nosuitup")) {
            suitUp(h);
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
        beaming |= updateChargedBeam(h, target, dist, sees);
        if (beaming) {
            face(target);
        }
        if (sees && globalCooldown <= 0) {
            fireShot(h, target, dist);
        }
        healOwner();
    }

    /**
     * Switches toggle-able TRANSFORMATION modifiers the way a player would with the transform key, but only those that
     * actually suit the hero up. "Transformation on" means suited for some heroes (Iron Man Mk50's nanites, Hulk, Moon
     * Knight) and unsuited for others (SecretHeroes' speedster suits dematerialise), so each one is tried once: it is
     * kept if it enables more of the hero's powers or makes its punches stronger, otherwise reverted. Pack JS key
     * functions are player-only and not
     * covered.
     */
    private void suitUp(Hero h) {
        for (ModifierEntry entry : FiskBridge.toggleTransformations(hero, h)) {
            if (!triedTransforms.add(entry.id)) {
                continue;
            }
            switch (FiskBridge.trySuitUp(hero, h, entry)) {
                case KEPT:
                    entry.broadcastSoundAtEntity(hero, SoundTrigger.ENABLE);
                    FiskBridge.transformation(entry).toggle.get()
                        .sync(hero);
                    hero.getSize()
                        .grow(); // size-changing transformations (Hulk) grow, as their key does for a player
                    record("transform");
                    break;
                case REVERTED:
                    record("transform-skipped"); // never synced, so clients see no change
                    break;
                default:
                    break;
            }
        }
    }

    /**
     * CHARGED_BEAM: a player holds the key; Fisk's modifier then charges, fires for its duration and clears
     * {@code BEAM_CHARGING} itself, for any entity. So start a charge when the target is visible and in range, and keep
     * facing it until the beam is done. Returns true while charging or firing.
     */
    private boolean updateChargedBeam(Hero h, EntityLivingBase target, double dist, boolean sees) {
        ModifierEntry entry = h.getEnabledModifier(hero, Modifier.CHARGED_BEAM);
        boolean charging = Vars.BEAM_CHARGING.get(hero);
        if (entry == null || target == null) {
            if (charging) setVar(Vars.BEAM_CHARGING, false);
            return false;
        }
        if (charging) {
            return true;
        }
        if (sees && dist <= FiskBridge.prop(entry, PowerProperty.RANGE, 32.0F)
            && Vars.BEAM_CHARGE.get(hero) <= 0.0F
            && !cooldowns.containsKey(Modifier.CHARGED_BEAM)) {
            setVar(Vars.BEAM_CHARGING, true);
            // At least 5 s between beams: a charged beam with big knockback (Hulk's Thunderclap) would otherwise keep
            // every
            // opponent blasted out of melee range.
            cooldowns
                .put(Modifier.CHARGED_BEAM, Math.max(100, FiskBridge.prop(entry, PowerProperty.COOLDOWN_TIME, 40)));
            record("beam:" + Modifier.CHARGED_BEAM.getName());
            return true;
        }
        return false;
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
                        want = sees && dist <= toggle.range(entry) && !cooldowns.containsKey(toggle.modifier);
                        break;
                    case MELEE:
                        want = dist <= toggle.range(entry);
                        break;
                    case DEFENSIVE:
                        want = hero.ticksExisted - lastHurtTick < RECENT_HURT_TICKS;
                        break;
                }
            }
            if (toggle.mode == ToggleAbility.Mode.BEAM) {
                // Bursts, not a held beam: a beam with knockback would otherwise juggle its target out of reach
                // forever.
                int on = want ? beamTicks.merge(toggle.modifier, 1, Integer::sum) : 0;
                if (!want) {
                    beamTicks.remove(toggle.modifier);
                } else if (on > BEAM_BURST_TICKS) {
                    want = false;
                    beamTicks.remove(toggle.modifier);
                    cooldowns.put(toggle.modifier, BEAM_REST_TICKS);
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
