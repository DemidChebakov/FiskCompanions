package com.dchebakov.fiskcompanions.entity;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.command.IEntitySelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWander;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.monster.EntityGolem;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
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
import com.dchebakov.fiskcompanions.entity.ai.AIHurtByTarget;
import com.dchebakov.fiskcompanions.entity.ai.AIMeleeAttack;
import com.dchebakov.fiskcompanions.entity.ai.AINearestTarget;
import com.dchebakov.fiskcompanions.entity.ai.AIOwnerHurtByTarget;
import com.dchebakov.fiskcompanions.entity.ai.AIOwnerHurtTarget;
import com.dchebakov.fiskcompanions.entity.ai.FlightController;
import com.dchebakov.fiskcompanions.entity.ai.PowerController;
import com.dchebakov.fiskcompanions.entity.ai.ShieldController;
import com.dchebakov.fiskcompanions.entity.ai.SizeController;
import com.dchebakov.fiskcompanions.entity.ai.SpeedController;
import com.dchebakov.fiskcompanions.entity.ai.WeaponController;
import com.dchebakov.fiskcompanions.item.ItemHeroConfigurator;
import com.dchebakov.fiskcompanions.world.CompanionSettings;
import com.fiskmods.heroes.common.data.IDataHolder;
import com.fiskmods.heroes.common.data.var.DataContainer;
import com.fiskmods.heroes.common.data.var.DataSyncer;
import com.fiskmods.heroes.common.data.var.Vars;
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

    /** Dev: {@code -Dfiskcompanions.trace=<hero key part>} logs that mob's AI state every second. */
    private static final String TRACE = System.getProperty("fiskcompanions.trace");
    private static final int DW_HERO = 16;
    private static final int DW_ALIGNMENT = 17;
    private static final int DW_OWNER = 18;
    private static final int DW_SITTING = 19;
    private static final int DW_SKIN = 20;
    private static final int DW_SKIN_PLAYER = 21;
    private static final int DW_BLOCKING = 22;

    public final DataContainer data = new DataContainer(DataSyncer.PLAYER);
    private HeroIteration wornSuit;
    private final PowerController powers = new PowerController(this);
    private final FlightController flight = new FlightController(this);
    private final SizeController size = new SizeController(this);
    private final WeaponController weapons = new WeaponController(this);
    private final SpeedController speed = new SpeedController(this);
    private final ShieldController shield = new ShieldController(this);
    private float appliedScale = 1.0F;

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
        tasks.addTask(2, new AIMeleeAttack(this, 1.2D));
        tasks.addTask(3, new AIFollowOwner(this, 1.1D));
        tasks.addTask(5, new EntityAIWander(this, 0.8D));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        tasks.addTask(7, new EntityAILookIdle(this));

        // Each target task is gated by alignment, so switching alignment needs no task rebuild.
        targetTasks.addTask(1, new AIOwnerHurtByTarget(this));
        targetTasks.addTask(2, new AIOwnerHurtTarget(this));
        targetTasks.addTask(3, new AIHurtByTarget(this));
        targetTasks.addTask(
            4,
            new AINearestTarget(
                this,
                EntityFiskHero.class,
                e -> e instanceof EntityFiskHero && ((EntityFiskHero) e).getAlignment() != getAlignment()));
        targetTasks.addTask(5, new AIGuardOwner(this, 12.0D));
        targetTasks.addTask(6, villainTarget(EntityPlayer.class));
        targetTasks.addTask(7, villainTarget(EntityVillager.class));
        targetTasks.addTask(7, villainTarget(EntityGolem.class));
        targetTasks.addTask(8, villainTarget(EntityAnimal.class));
        // EntityLiving, not EntityCreature: ghasts, slimes and the Ender Dragon are hostile but not creatures.
        targetTasks.addTask(
            9,
            new AINearestTarget(
                this,
                EntityLiving.class,
                e -> e instanceof IMob && getAlignment() == Alignment.HERO && getOwner() == null));
    }

    private AINearestTarget villainTarget(Class<? extends Entity> type) {
        IEntitySelector selector = e -> getAlignment() == Alignment.VILLAIN
            && worldObj.difficultySetting != EnumDifficulty.PEACEFUL;
        return new AINearestTarget(this, type, selector);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_HERO, "");
        dataWatcher.addObject(DW_ALIGNMENT, (byte) Alignment.HERO.ordinal());
        dataWatcher.addObject(DW_OWNER, "");
        dataWatcher.addObject(DW_SITTING, (byte) 0);
        dataWatcher.addObject(DW_SKIN, (byte) BaseSkin.PLAYER.ordinal());
        dataWatcher.addObject(DW_SKIN_PLAYER, "");
        dataWatcher.addObject(DW_BLOCKING, (byte) 0);
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

    private double safeX, safeY, safeZ;
    /** Suits whose script already failed on a character (logged once each). */
    private static final Set<String> REPORTED_SCRIPT_ERRORS = ConcurrentHashMap.newKeySet();
    private boolean suitScriptBroken, controllersBroken;

    /**
     * Fisk's suit tick runs the heropack's scripts; some assume a player wearer and throw on a mob. Rather than crash
     * the game, the suit's per-tick scripts are switched off for this character (it keeps its looks and fights on).
     */
    private void tickSuitSafely(TickEvent.Phase phase) {
        if (suitScriptBroken) {
            return;
        }
        try {
            FiskBridge.tickSuit(this, wornSuit, phase);
        } catch (RuntimeException e) {
            suitScriptBroken = true;
            reportScriptError("suit tick", e);
        }
    }

    private void reportScriptError(String where, RuntimeException e) {
        powers.record("script-error");
        if (REPORTED_SCRIPT_ERRORS.add(getHeroKey() + "|" + where)) {
            FiskCompanions.LOG.warn(
                "Suit {} threw in its {} script on a character (it probably expects a player); turned off for characters: {}",
                getHeroKey(),
                where,
                e.toString());
        }
    }

    @Override
    public void onUpdate() {
        // A NaN motion from any pack power (e.g. a zero-strength knockback) would send the mob out of the world.
        if (Double.isNaN(posX) || Double.isNaN(
            posY) || Double.isNaN(posZ) || Double.isNaN(motionX) || Double.isNaN(motionY) || Double.isNaN(motionZ)) {
            motionX = motionY = motionZ = 0.0D;
            setPosition(safeX, safeY, safeZ);
        } else {
            safeX = posX;
            safeY = posY;
            safeZ = posZ;
        }
        if (isEntityAlive()) {
            wornSuit = FiskBridge.computeWornSuit(this);
            tickSuitSafely(TickEvent.Phase.START);
        }
        super.onUpdate();
        if (!isEntityAlive()) {
            return;
        }
        data.onUpdate(this);
        if (!worldObj.isRemote) {
            restoreSuitIfMissing();
            unstickFromBlocks();
            if (!controllersBroken) {
                try {
                    size.update();
                    flight.update();
                    speed.update();
                    weapons.update();
                    shield.update();
                    powers.update();
                } catch (RuntimeException e) {
                    controllersBroken = true; // usually a heropack script that only works on players
                    reportScriptError("powers", e);
                }
            }
            if (TRACE != null && ticksExisted % 20 == 0 && getHeroKey().contains(TRACE)) {
                EntityLivingBase t = getAttackTarget();
                FiskCompanions.LOG.info(
                    "[trace] {} {} pos=({}, {}, {}) mot=({}, {}, {}) flying={} onGround={} target={} dist={} path={}",
                    getHeroKey(),
                    getAlignment(),
                    String.format("%.1f", posX),
                    String.format("%.1f", posY),
                    String.format("%.1f", posZ),
                    String.format("%.2f", motionX),
                    String.format("%.2f", motionY),
                    String.format("%.2f", motionZ),
                    flight.isFlying(),
                    onGround,
                    t == null ? "-" : t.getCommandSenderName() + "@" + String.format("%.1f", t.posY),
                    t == null ? "-" : String.format("%.1f", getDistanceToEntity(t)),
                    !getNavigator().noPath());
            }
        }
        applyScale();
        tickSuitSafely(TickEvent.Phase.END);
    }

    /**
     * Fisk aims beams, projectiles, gadgets and guns along the look vector: turn body, head and pitch to the target.
     */
    public void faceEntity(Entity target) {
        faceEntity(target, 0.0D);
    }

    /** As {@link #faceEntity(Entity)}, aiming {@code extraY} blocks higher (e.g. for an arrow's drop). */
    public void faceEntity(Entity target, double extraY) {
        double dx = target.posX - posX;
        double dy = target.boundingBox.minY + target.height * 0.6D + extraY - (posY + getEyeHeight());
        double dz = target.posZ - posZ;
        double horiz = MathHelper.sqrt_double(dx * dx + dz * dz);
        float yaw = (float) (Math.atan2(dz, dx) * 180.0D / Math.PI) - 90.0F;
        rotationYaw = rotationYawHead = renderYawOffset = yaw;
        rotationPitch = (float) -(Math.atan2(dy, horiz) * 180.0D / Math.PI);
    }

    /** Fisk only resizes players, so scale this mob's hitbox from the synced scale var on both sides. */
    private void applyScale() {
        float scale = Vars.SCALE.get(this);
        if (scale != appliedScale) {
            appliedScale = scale;
            setSize(0.6F * scale, 1.8F * scale);
        }
    }

    public SpeedController getSpeed() {
        return speed;
    }

    public ShieldController getShield() {
        return shield;
    }

    /** Synced for the renderer: blocking with an item shield (Captain America's raised-shield arm pose). */
    public boolean isBlockingWithItem() {
        return dataWatcher.getWatchableObjectByte(DW_BLOCKING) != 0;
    }

    public void setBlockingFlag(boolean blocking) {
        if (isBlockingWithItem() != blocking) {
            dataWatcher.updateObject(DW_BLOCKING, (byte) (blocking ? 1 : 0));
        }
    }

    public WeaponController getWeapons() {
        return weapons;
    }

    public SizeController getSize() {
        return size;
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
        shield.reset();
        setHealth(getMaxHealth());
    }

    /** Client-only preview (configurator GUI): wear the suit without syncing anything or touching the AI. */
    public void showSuit(HeroIteration iter) {
        FiskBridge.equip(this, iter);
        wornSuit = FiskBridge.computeWornSuit(this);
    }

    /**
     * Client-only preview tick for the never-spawned stand-in: advances Fisk's data vars locally (each interpolated
     * timer's previous value catches up), as Fisk does for real non-player entities. Without it, renderers that read
     * interpolated transformation timers (Mk50/Mk85 nanites) draw the suit half-built. Deliberately not the suit tick
     * or {@code data.onUpdate}: those can sync over the network, and this entity's id means nothing to the server.
     */
    public void tickPreview() {
        ++ticksExisted;
        if (suitScriptBroken) {
            return;
        }
        try {
            wornSuit = FiskBridge.computeWornSuit(this);
            FiskBridge.updateDataLocally(this);
        } catch (RuntimeException e) {
            suitScriptBroken = true;
            reportScriptError("preview", e);
        }
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

    public BaseSkin getSkin() {
        return BaseSkin.byId(dataWatcher.getWatchableObjectByte(DW_SKIN));
    }

    /** Name of the player whose skin {@link BaseSkin#PLAYER} shows (whoever spawned or last configured it). */
    public String getSkinPlayer() {
        return dataWatcher.getWatchableObjectString(DW_SKIN_PLAYER);
    }

    public void setSkin(BaseSkin skin, String playerName) {
        dataWatcher.updateObject(DW_SKIN, (byte) skin.ordinal());
        if (playerName != null) {
            dataWatcher.updateObject(DW_SKIN_PLAYER, playerName);
        }
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
        EntityDragon dragon = FiskBridge.dragonOf(target);
        if (dragon != null) {
            // The dragon ignores mob hits, and Fisk's punch re-application never sees it: deal the punch ourselves.
            boolean head = boundingBox.expand(1.0D, 1.5D, 1.0D)
                .intersectsWith(dragon.dragonPartHead.boundingBox);
            boolean landed = FiskBridge.damageDragon(dragon, this, FiskBridge.punchDamage(this, damage), head);
            if (landed) {
                powers.record("dragon-hit");
            }
            return landed;
        }
        boolean hit;
        try {
            hit = target.attackEntityFrom(DamageSource.causeMobDamage(this), damage);
        } catch (RuntimeException e) {
            reportScriptError("melee", e); // a suit script in Fisk's damage handlers
            return false;
        }
        if (hit) {
            float yaw = rotationYaw * (float) Math.PI / 180.0F;
            target.addVelocity(-MathHelper.sin(yaw) * 0.4D, 0.1D, MathHelper.cos(yaw) * 0.4D);
        }
        return hit;
    }

    private static final int GRUDGE_TICKS = 600;
    /** Someone this character fights back against despite being "on the same team" (see the friendly-fire rules). */
    private UUID grudgeId;
    private int grudgeUntil;

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        boolean grudge = false;
        if (!worldObj.isRemote && attacker instanceof EntityLivingBase && attacker != this) {
            CompanionSettings rules = CompanionSettings.get(worldObj);
            if (attacker instanceof EntityFiskHero && ((EntityFiskHero) attacker).getAlignment() == getAlignment()) {
                if (!rules.allyFriendlyFire) {
                    return false; // an ally's stray hit or blast: no damage, no infighting
                }
                grudge = true;
            } else if (attacker instanceof EntityPlayer && rules.fightBackPlayers
                && !((EntityPlayer) attacker).capabilities.isCreativeMode) {
                    grudge = true;
                }
        }
        if (!worldObj.isRemote) {
            shield.onAttacked(source);
            if (shield.isBlockingWithItem() && getHeldItem() != null) {
                FiskBridge.Block block = FiskBridge.blockWithItem(this, source, amount, getHeldItem());
                if (block == FiskBridge.Block.BLOCKED) {
                    if (grudge) {
                        holdGrudge(attacker);
                    }
                    return false;
                }
            }
        }
        try {
            if (!super.attackEntityFrom(source, amount)) {
                if (grudge && shield.isBlocking()) {
                    holdGrudge(attacker); // a suit shield blocked it: still counts as an attack
                }
                return false;
            }
        } catch (RuntimeException e) {
            reportScriptError("damage", e); // a suit script in Fisk's damage handlers
            return false;
        }
        powers.onHurt();
        if (grudge) {
            holdGrudge(attacker);
        }
        return true;
    }

    private void holdGrudge(Entity attacker) {
        grudgeId = attacker.getUniqueID();
        grudgeUntil = ticksExisted + GRUDGE_TICKS;
        setAttackTarget((EntityLivingBase) attacker);
    }

    private boolean holdsGrudge(Entity other) {
        return grudgeId != null && ticksExisted < grudgeUntil && grudgeId.equals(other.getUniqueID());
    }

    /**
     * Vanilla mobs never target creepers or ghasts ({@code EntityLiving.canAttackClass}), so heroes ignored them.
     * Who is an enemy is decided by the target tasks and {@link #isOnSameTeam}; any class may be fought.
     */
    @Override
    public boolean canAttackClass(Class<? extends Entity> type) {
        return true;
    }

    /**
     * Every target source (AI tasks, owner assist, guard, Fisk code) ends here: never target someone on our side
     * unless they earned a grudge under the friendly-fire settings.
     */
    @Override
    public void setAttackTarget(EntityLivingBase target) {
        super.setAttackTarget(target != null && isOnSameTeam(target) ? null : target);
    }

    @Override
    public boolean isOnSameTeam(EntityLivingBase other) {
        if (holdsGrudge(other)) {
            return false;
        }
        if (other instanceof EntityFiskHero) {
            return ((EntityFiskHero) other).getAlignment() == getAlignment();
        }
        if (getAlignment() == Alignment.HERO && other instanceof EntityPlayer) {
            return true; // heroes never fight players
        }
        return super.isOnSameTeam(other);
    }

    // ---- staying out of walls ----

    private static final int STUCK_TICKS = 10;
    private static final int UNSTUCK_RADIUS = 8;
    private int embeddedTicks;

    /** True if this character's hitbox, at its current size, overlaps solid blocks. */
    public boolean isEmbedded() {
        return !worldObj.func_147461_a(boundingBox.contract(0.05D, 0.05D, 0.05D))
            .isEmpty();
    }

    /** True if this character's hitbox, at its current size, fits with its feet at (x, y, z). */
    public boolean fitsAt(double x, double y, double z) {
        double hw = width / 2.0D;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - hw, y, z - hw, x + hw, y + height, z + hw)
            .contract(0.05D, 0.05D, 0.05D);
        return worldObj.func_147461_a(box)
            .isEmpty() && !worldObj.isAnyLiquid(box);
    }

    private boolean standable(int x, int y, int z) {
        return World.doesBlockHaveSolidTopSurface(worldObj, x, y - 1, z) && fitsAt(x + 0.5D, y, z + 0.5D);
    }

    /**
     * Moves next to {@code anchor} on a spot where the whole (possibly grown) hitbox fits, nearest first and not right
     * on top of it. Returns false if there is none within 3 blocks.
     */
    public boolean teleportNear(Entity anchor) {
        int ax = MathHelper.floor_double(anchor.posX);
        int ay = MathHelper.floor_double(anchor.boundingBox.minY);
        int az = MathHelper.floor_double(anchor.posZ);
        for (int r = 2; r <= 3; ++r) {
            for (int dy : new int[] { 0, 1, -1, 2 }) {
                for (int dx = -r; dx <= r; ++dx) {
                    for (int dz = -r; dz <= r; ++dz) {
                        if ((Math.abs(dx) == r || Math.abs(dz) == r) && standable(ax + dx, ay + dy, az + dz)) {
                            moveTo(ax + dx + 0.5D, ay + dy, az + dz + 0.5D);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    private void moveTo(double x, double y, double z) {
        setLocationAndAngles(x, y, z, rotationYaw, rotationPitch);
        motionX = motionY = motionZ = 0.0D;
        getNavigator().clearPathEntity();
    }

    /**
     * Vanilla never pushes a mob out of blocks. A character can end up inside them by growing (Hulk, Hulkbuster) or a
     * size script under a low ceiling, or by being knocked into a wall: after half a second, step to the nearest spot
     * where the hitbox fits, or back to the owner.
     */
    private void unstickFromBlocks() {
        if (!isEmbedded()) {
            embeddedTicks = 0;
            return;
        }
        if (++embeddedTicks < STUCK_TICKS) {
            return;
        }
        embeddedTicks = 0;
        int bx = MathHelper.floor_double(posX);
        int by = MathHelper.floor_double(boundingBox.minY);
        int bz = MathHelper.floor_double(posZ);
        for (int r = 1; r <= UNSTUCK_RADIUS; ++r) {
            for (int dy = -1; dy <= Math.min(r, 4); ++dy) {
                for (int dx = -r; dx <= r; ++dx) {
                    for (int dz = -r; dz <= r; ++dz) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r && Math.abs(dy) != r) continue;
                        int x = bx + dx, y = by + dy, z = bz + dz;
                        boolean ok = flight.isFlying() ? fitsAt(x + 0.5D, y, z + 0.5D) : standable(x, y, z);
                        if (ok) {
                            moveTo(x + 0.5D, y, z + 0.5D);
                            powers.record("unstuck");
                            return;
                        }
                    }
                }
            }
        }
        EntityPlayer owner = getOwner();
        if (owner != null && teleportNear(owner)) {
            powers.record("unstuck");
        }
    }

    @Override
    protected void fall(float distance) {
        if (!flight.isFlying()) {
            super.fall(distance * 0.25F); // suited characters shrug off most falls
        }
    }

    @Override
    public void setDead() {
        if (!worldObj.isRemote) {
            weapons.discardThrownShield(); // a shield in flight must not drop as a free item
        }
        super.setDead();
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
        nbt.setString("Skin", getSkin().name());
        nbt.setString("SkinPlayer", getSkinPlayer());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound nbt) {
        super.readEntityFromNBT(nbt);
        data.readFromNBT(nbt.getCompoundTag("FiskData"));
        dataWatcher.updateObject(DW_HERO, nbt.getString("Hero"));
        dataWatcher.updateObject(DW_ALIGNMENT, nbt.getByte("Alignment"));
        dataWatcher.updateObject(DW_OWNER, nbt.getString("Owner"));
        setSitting(nbt.getBoolean("Sitting"));
        setSkin(BaseSkin.byName(nbt.getString("Skin")), nbt.getString("SkinPlayer"));
    }
}
