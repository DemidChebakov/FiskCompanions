package com.dchebakov.fiskcompanions.entity;

import java.util.UUID;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAIHurtByTarget;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAINearestAttackableTarget;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StatCollector;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.World;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.ai.AIFollowOwner;
import com.dchebakov.fiskcompanions.entity.ai.AIGuardOwner;
import com.dchebakov.fiskcompanions.entity.ai.AIOwnerHurtByTarget;
import com.dchebakov.fiskcompanions.entity.ai.AIOwnerHurtTarget;
import com.dchebakov.fiskcompanions.entity.ai.FlightController;
import com.dchebakov.fiskcompanions.entity.ai.PowerController;
import com.dchebakov.fiskcompanions.item.ItemHeroConfigurator;
import com.fiskmods.heroes.common.data.IDataHolder;
import com.fiskmods.heroes.common.data.var.DataContainer;
import com.fiskmods.heroes.common.data.var.DataSyncer;
import com.fiskmods.heroes.common.entity.IHeroTrackedEntity;
import com.fiskmods.heroes.common.entity.INonBreathing;
import com.fiskmods.heroes.common.entity.attribute.ArmorAttribute;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * A mob that wears any Fisk's Heroes suit. Modelled on Fisk's own {@code EntityIronMan}: it is an
 * {@link IHeroTrackedEntity} + {@link IDataHolder}, so Fisk ticks the suit's modifiers and syncs its vars.
 */
public class EntityFiskHero extends EntityCreature implements IHeroTrackedEntity, IDataHolder, INonBreathing {

    private static final int DW_HERO = 16;
    private static final int DW_ALIGNMENT = 17;
    private static final int DW_OWNER = 18;
    private static final int DW_SITTING = 19;

    public final DataContainer data = new DataContainer(DataSyncer.PLAYER);
    private HeroIteration wornSuit;
    private final PowerController powers = new PowerController(this);
    private final FlightController flight = new FlightController(this);

    public EntityFiskHero(World world) {
        super(world);
        setSize(0.6F, 1.8F);
        getNavigator().setCanSwim(true);
        getNavigator().setAvoidsWater(false);
        setCanPickUpLoot(false);
        for (int i = 0; i < equipmentDropChances.length; ++i) {
            equipmentDropChances[i] = 0.0F; // suits never drop
        }

        tasks.addTask(1, new EntityAISwimming(this));
        tasks.addTask(2, new EntityAIAttackOnCollide(this, 1.2D, true));
        tasks.addTask(3, new AIFollowOwner(this, 1.1D));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(7, new EntityAILookIdle(this));

        // Each target task is gated by alignment, so switching alignment needs no task rebuild.
        targetTasks.addTask(1, new AIOwnerHurtByTarget(this));
        targetTasks.addTask(2, new AIOwnerHurtTarget(this));
        targetTasks.addTask(3, new EntityAIHurtByTarget(this, false));
        targetTasks.addTask(
            4,
            new EntityAINearestAttackableTarget(
                this,
                EntityFiskHero.class,
                0,
                true,
                false,
                e -> e instanceof EntityFiskHero && ((EntityFiskHero) e).getAlignment() != getAlignment()));
        targetTasks.addTask(5, new AIGuardOwner(this, 12.0D));
        targetTasks.addTask(6, villainTarget(EntityPlayer.class));
        targetTasks.addTask(7, villainTarget(EntityVillager.class));
        targetTasks.addTask(7, villainTarget(EntityGolem.class));
        targetTasks.addTask(8, villainTarget(EntityAnimal.class));
        targetTasks.addTask(
            9,
            new EntityAINearestAttackableTarget(
                this,
                EntityCreature.class,
                0,
                true,
                false,
                e -> e instanceof IMob && getAlignment() == Alignment.HERO && getOwner() == null));
    }

    private EntityAINearestAttackableTarget villainTarget(Class<? extends Entity> type) {
        IEntitySelector selector = e -> getAlignment() == Alignment.VILLAIN
            && worldObj.difficultySetting != EnumDifficulty.PEACEFUL;
        return new EntityAINearestAttackableTarget(this, type, 0, true, false, selector);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_HERO, "");
        dataWatcher.addObject(DW_ALIGNMENT, (byte) Alignment.HERO.ordinal());
        dataWatcher.addObject(DW_OWNER, "");
        dataWatcher.addObject(DW_SITTING, (byte) 0);
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getAttributeMap().registerAttribute(SharedMonsterAttributes.attackDamage)
            .setBaseValue(3.0D);
        // Fisk's suit tick writes step-height modifiers to this instance (EntityIronMan registers it too).
        getAttributeMap().registerAttribute((IAttribute) ArmorAttribute.STEP_HEIGHT);
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(40.0D);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(0.3D);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(32.0D);
    }

    @Override
    public boolean isAIEnabled() {
        return true;
    }

    // ---- Fisk integration ----

    @Override
    public DataContainer getData() {
        return data;
    }

    @Override
    public HeroIteration getWornSuit() {
        return wornSuit;
    }

    @Override
    public void onUpdate() {
        if (isEntityAlive()) {
            wornSuit = FiskBridge.computeWornSuit(this);
            FiskBridge.tickSuit(this, wornSuit, TickEvent.Phase.START);
        }
        super.onUpdate();
        if (!isEntityAlive()) {
            return;
        }
        data.onUpdate(this);
        if (!worldObj.isRemote) {
            restoreSuitIfMissing();
            flight.update();
            powers.update();
        }
        FiskBridge.tickSuit(this, wornSuit, TickEvent.Phase.END);
    }

    /** Re-equips the configured suit if a piece broke or the hero key changed while unloaded. */
    private void restoreSuitIfMissing() {
        if (wornSuit == null && ticksExisted % 20 == 0) {
            HeroIteration iter = FiskBridge.lookup(getHeroKey());
            if (iter != null) {
                FiskBridge.equip(this, iter);
            }
        }
    }

    // ---- configuration ----

    public String getHeroKey() {
        return dataWatcher.getWatchableObjectString(DW_HERO);
    }

    public void setHero(HeroIteration iter) {
        dataWatcher.updateObject(DW_HERO, FiskBridge.keyOf(iter));
        FiskBridge.equip(this, iter);
        wornSuit = FiskBridge.computeWornSuit(this);
        powers.reset();
        setHealth(getMaxHealth());
    }

    public Alignment getAlignment() {
        return Alignment.byId(dataWatcher.getWatchableObjectByte(DW_ALIGNMENT));
    }

    public void setAlignment(Alignment alignment) {
        if (alignment != getAlignment()) {
            dataWatcher.updateObject(DW_ALIGNMENT, (byte) alignment.ordinal());
            setAttackTarget(null);
            setRevengeTarget(null);
            powers.reset();
        }
    }

    public UUID getOwnerId() {
        String s = dataWatcher.getWatchableObjectString(DW_OWNER);
        try {
            return s.isEmpty() ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setOwnerId(UUID id) {
        dataWatcher.updateObject(DW_OWNER, id != null ? id.toString() : "");
    }

    /** The owning player if a HERO and the owner is in this world, else null. Villains have no owner. */
    public EntityPlayer getOwner() {
        UUID id = getOwnerId();
        if (id == null || getAlignment() != Alignment.HERO) {
            return null;
        }
        for (Object o : worldObj.playerEntities) {
            EntityPlayer player = (EntityPlayer) o;
            if (id.equals(player.getUniqueID())) {
                return player;
            }
        }
        return null;
    }

    public boolean isSitting() {
        return dataWatcher.getWatchableObjectByte(DW_SITTING) != 0;
    }

    public void setSitting(boolean sitting) {
        dataWatcher.updateObject(DW_SITTING, (byte) (sitting ? 1 : 0));
        if (sitting) {
            getNavigator().clearPathEntity();
        }
    }

    // ---- interaction ----

    @Override
    protected boolean interact(EntityPlayer player) {
        ItemStack held = player.getCurrentEquippedItem();
        if (held != null && held.getItem() instanceof ItemHeroConfigurator) {
            if (worldObj.isRemote) {
                FiskCompanions.proxy.openConfigurator(this);
            }
            return true;
        }
        if (held == null && player.isSneaking()
            && player.getUniqueID()
                .equals(getOwnerId())
            && getAlignment() == Alignment.HERO) {
            if (!worldObj.isRemote) {
                setSitting(!isSitting());
                player.addChatMessage(
                    new ChatComponentTranslation(
                        isSitting() ? "fiskcompanions.msg.stay" : "fiskcompanions.msg.follow",
                        getCommandSenderName()));
            }
            return true;
        }
        return super.interact(player);
    }

    // ---- combat ----

    @Override
    public boolean attackEntityAsMob(Entity target) {
        // Base damage only: for a suited non-player attacker Fisk cancels this hit and re-applies it through its own
        // punch system (SHHelper.attackEntityMelee, with the hero's punchDamage), so `hit` is false even when it lands.
        float damage = (float) getEntityAttribute(SharedMonsterAttributes.attackDamage).getAttributeValue();
        swingItem();
        powers.record("melee");
        boolean hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), damage);
        if (hit) {
            float yaw = rotationYaw * (float) Math.PI / 180.0F;
            target.addVelocity(-MathHelper.sin(yaw) * 0.4D, 0.1D, MathHelper.cos(yaw) * 0.4D);
        }
        return hit;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        if (!super.attackEntityFrom(source, amount)) {
            return false;
        }
        powers.onHurt();
        return true;
    }

    @Override
    public boolean isOnSameTeam(EntityLivingBase other) {
        if (other instanceof EntityFiskHero) {
            return ((EntityFiskHero) other).getAlignment() == getAlignment();
        }
        if (getAlignment() == Alignment.HERO && other instanceof EntityPlayer) {
            return true; // heroes never fight players
        }
        return super.isOnSameTeam(other);
    }

    @Override
    protected void fall(float distance) {
        if (!flight.isFlying()) {
            super.fall(distance * 0.25F); // suited characters shrug off most falls
        }
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public String getCommandSenderName() {
        if (hasCustomNameTag()) {
            return getCustomNameTag();
        }
        HeroIteration iter = FiskBridge.lookup(getHeroKey());
        return iter != null ? FiskBridge.displayName(iter)
            : StatCollector.translateToLocal("entity.fiskcompanions.FiskHero.name");
    }

    public PowerController getPowers() {
        return powers;
    }

    public FlightController getFlight() {
        return flight;
    }

    // ---- persistence ----

    @Override
    public void writeEntityToNBT(NBTTagCompound nbt) {
        super.writeEntityToNBT(nbt);
        nbt.setTag("FiskData", data.writeToNBT(new NBTTagCompound()));
        nbt.setString("Hero", getHeroKey());
        nbt.setByte("Alignment", (byte) getAlignment().ordinal());
        nbt.setString("Owner", dataWatcher.getWatchableObjectString(DW_OWNER));
        nbt.setBoolean("Sitting", isSitting());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        data.readFromNBT(nbt.getCompoundTag("FiskData"));
        dataWatcher.updateObject(DW_HERO, nbt.getString("Hero"));
        dataWatcher.updateObject(DW_ALIGNMENT, nbt.getByte("Alignment"));
        dataWatcher.updateObject(DW_OWNER, nbt.getString("Owner"));
        setSitting(nbt.getBoolean("Sitting"));
    }
}
