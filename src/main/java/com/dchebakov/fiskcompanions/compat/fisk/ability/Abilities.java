package com.dchebakov.fiskcompanions.compat.fisk.ability;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import com.dchebakov.fiskcompanions.compat.fisk.ability.ToggleAbility.Mode;
import com.fiskmods.heroes.client.pack.json.sound.SoundTrigger;
import com.fiskmods.heroes.common.damage.DamageProfile;
import com.fiskmods.heroes.common.damage.ModDamageSources;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.entity.effect.EntityEnergyBlast;
import com.fiskmods.heroes.common.entity.projectile.EntityColdBall;
import com.fiskmods.heroes.common.entity.projectile.EntityEnergyBolt;
import com.fiskmods.heroes.common.entity.projectile.EntityFireBlast;
import com.fiskmods.heroes.common.entity.projectile.EntityIcicle;
import com.fiskmods.heroes.common.hero.HeroIteration;
import com.fiskmods.heroes.common.hero.HeroTracker;
import com.fiskmods.heroes.common.hero.modifier.Modifier;
import com.fiskmods.heroes.common.hero.power.ModifierEntry;
import com.fiskmods.heroes.common.hero.power.PowerProperty;
import com.fiskmods.heroes.common.interaction.InteractionRepulsor;
import com.fiskmods.heroes.util.FiskMath;
import com.fiskmods.heroes.util.Raytrace;
import com.fiskmods.heroes.util.SHHelper;
import com.fiskmods.heroes.util.Vectors;
import com.google.common.collect.ImmutableList;

/** The power types the mob can use. See docs/fisk-internals.md for what is not covered yet. */
public final class Abilities {

    private Abilities() {}

    public static final List<ToggleAbility> TOGGLES = ImmutableList.of(
        new ToggleAbility(Modifier.HEAT_VISION, Vars.HEAT_VISION, Mode.BEAM, 32.0F),
        new ToggleAbility(Modifier.ENERGY_PROJECTION, Vars.ENERGY_PROJECTION, Mode.BEAM, 10.0F),
        new ToggleAbility(Modifier.SONIC_WAVES, Vars.SONIC_WAVES, Mode.BEAM, 12.0F),
        new ToggleAbility(Modifier.GRAVITY_MANIPULATION, Vars.GRAVITY_MANIP, Mode.BEAM, 24.0F),
        new ToggleAbility(Modifier.BLADE, Vars.BLADE, Mode.MELEE, 6.0F),
        new ToggleAbility(Modifier.CHARGED_PUNCH, Vars.PUNCHMODE, Mode.MELEE, 6.0F),
        new ToggleAbility(Modifier.SHIELD, Vars.SHIELD, Mode.DEFENSIVE, 0.0F));

    /** In priority order: the first usable shot wins each decision. */
    public static final List<ShotAbility> SHOTS = ImmutableList.of(
        new ShotAbility(Modifier.GROUND_SMASH, 3.0F, true, Abilities::groundSmash),
        new ShotAbility(Modifier.REPULSOR_BLAST, 32.0F, false, (s, t, e) -> InteractionRepulsor.shoot(s)),
        new ShotAbility(Modifier.ENERGY_BLAST, 32.0F, false, Abilities::energyBlast),
        new ShotAbility(Modifier.ENERGY_BOLT, 32.0F, false, Abilities::energyBolt),
        new ShotAbility(Modifier.FIREBALL, 24.0F, false, Abilities::fireball),
        new ShotAbility(Modifier.CRYOBALL, 20.0F, false, Abilities::coldBall),
        new ShotAbility(Modifier.ICICLES, 20.0F, false, Abilities::icicles));

    /** True if the hero has any power that needs line-of-sight aiming (used to raise Fisk's AIMING pose). */
    public static boolean isRanged(Modifier modifier) {
        for (ShotAbility shot : SHOTS) {
            if (shot.modifier == modifier && modifier != Modifier.GROUND_SMASH) {
                return true;
            }
        }
        for (ToggleAbility toggle : TOGGLES) {
            if (toggle.modifier == modifier && toggle.mode == Mode.BEAM) {
                return true;
            }
        }
        return false;
    }

    // ---- ports of Interaction*.receive (server side) ----

    /** InteractionEnergyBlast */
    private static void energyBlast(EntityLivingBase s, EntityLivingBase t, ModifierEntry e) {
        float range = e.get(PowerProperty.RANGE);
        DamageProfile profile = e.get(PowerProperty.DAMAGE_PROFILE);
        MovingObjectPosition mop = Raytrace.rayTrace(s, range, 4, 1.0F);
        if (mop != null && mop.entityHit != null) {
            profile.apply(mop.entityHit, s, ModDamageSources.ENERGY_BLAST, false);
        }
        if (mop == null) {
            mop = new MovingObjectPosition(0, 0, 0, 0, Vectors.getOffsetCoords(s, 0.0D, 0.0D, range), false);
        }
        s.worldObj.spawnEntityInWorld(new EntityEnergyBlast(s.worldObj, s, mop.hitVec));
        e.dispatchSoundAtEntity(s, SoundTrigger.SHOOT);
    }

    /** InteractionEnergyBolt */
    private static void energyBolt(EntityLivingBase s, EntityLivingBase t, ModifierEntry e) {
        HeroIteration iter = HeroTracker.iter(s);
        Vec3 src = Vectors.getOffsetCoords(s, -0.3D, -0.3D, 0.6D);
        EntityEnergyBolt bolt = new EntityEnergyBolt(s.worldObj, s, e.get(PowerProperty.DAMAGE_PROFILE), iter, false);
        bolt.setPosition(src.xCoord, src.yCoord, src.zCoord);
        if (Boolean.TRUE.equals(e.get(PowerProperty.IS_EXPLOSIVE))) {
            bolt.setExplosive(true);
        }
        s.worldObj.spawnEntityInWorld(bolt);
        e.dispatchSoundAtEntity(s, SoundTrigger.SHOOT);
    }

    /** InteractionFireball */
    private static void fireball(EntityLivingBase s, EntityLivingBase t, ModifierEntry e) {
        s.worldObj.spawnEntityInWorld(
            new EntityFireBlast(s.worldObj, s, e.get(PowerProperty.DAMAGE_PROFILE), e.get(PowerProperty.RADIUS)));
        s.swingItem();
        e.dispatchSoundAtEntity(s, SoundTrigger.SHOOT);
    }

    /** InteractionColdBall */
    private static void coldBall(EntityLivingBase s, EntityLivingBase t, ModifierEntry e) {
        s.worldObj.spawnEntityInWorld(
            new EntityColdBall(s.worldObj, s, e.get(PowerProperty.DAMAGE_PROFILE), e.get(PowerProperty.RADIUS)));
        s.swingItem();
        e.dispatchSoundAtEntity(s, SoundTrigger.SHOOT);
    }

    /** InteractionIcicleShoot */
    private static void icicles(EntityLivingBase s, EntityLivingBase t, ModifierEntry e) {
        DamageProfile profile = e.get(PowerProperty.DAMAGE_PROFILE);
        float charge = Vars.CRYO_CHARGE.get(s);
        float spread = e.get(PowerProperty.SPREAD);
        int count = (int) (charge * e.get(PowerProperty.QUANTITY));
        for (int i = 0; i <= count; ++i) {
            EntityIcicle icicle = new EntityIcicle(s.worldObj, s, profile);
            if (i > 0) {
                icicle.motionX += (Math.random() - 0.5D) * spread;
                icicle.motionY += (Math.random() - 0.5D) * spread;
                icicle.motionZ += (Math.random() - 0.5D) * spread;
            }
            s.worldObj.spawnEntityInWorld(icicle);
        }
        Vars.CRYO_CHARGE.set(s, 0.0F)
            .sync();
        s.swingItem();
        e.dispatchSoundAtEntity(s, SoundTrigger.SHOOT);
    }

    /**
     * InteractionGroundSmash, centred on the shooter. The block-throwing part is left out (no griefing yet), and allies
     * of the shooter are not hit.
     */
    private static void groundSmash(EntityLivingBase s, EntityLivingBase t, ModifierEntry e) {
        DamageProfile profile = e.get(PowerProperty.DAMAGE_PROFILE);
        float radius = e.get(PowerProperty.RADIUS);
        float knockback = e.get(PowerProperty.KNOCKBACK);
        Vec3 center = Vec3.createVectorHelper(s.posX, s.boundingBox.minY, s.posZ);
        for (EntityLivingBase entity : Vectors
            .getEntitiesNear(EntityLivingBase.class, s.worldObj, center.xCoord, center.yCoord, center.zCoord, radius)) {
            if (entity == s || s.isOnSameTeam(entity)) {
                continue;
            }
            float mult = FiskMath.getScaledDistance(center, Vectors.centerOf(entity), radius);
            profile.apply(entity, s, ModDamageSources.EXPLOSION.apply((Entity) entity), mult, false);
            if (knockback * mult > 0.0F) {
                // A zero amount reaches vanilla knockBack as a 0/0 and turns the victim's position into NaN.
                SHHelper.knockbackWithoutNotify(entity, s, knockback * mult);
            }
        }
        s.worldObj.playSoundEffect(center.xCoord, center.yCoord, center.zCoord, "random.explode", 2.0F, 0.8F);
        s.swingItem();
    }
}
