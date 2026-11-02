package com.dchebakov.fiskcompanions.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.dchebakov.fiskcompanions.entity.BaseSkin;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.client.animation.GLProxy;
import com.fiskmods.heroes.client.model.ModelHelper;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.util.SHClientUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Like Fisk's {@code RenderIronMan}, the suit itself is drawn by the {@code ItemHeroArmor} pieces, so any heropack
 * suit renders with its own model and JS renderer. Underneath is the character's base skin ({@link BaseSkin}): the
 * spawning player's skin (as a player wearing the suit looks), Steve, or none for suits with their own body shape.
 */
@SideOnly(Side.CLIENT)
public class RenderFiskHero extends RenderBiped {

    private static final ResourceLocation STEVE = new ResourceLocation("textures/entity/steve.png");

    public RenderFiskHero() {
        super(new ModelBiped(), 0.5F);
    }

    /** Fisk only scales players in their renderer; apply the synced scale (size manipulation, e.g. Hulk). */
    @Override
    protected void preRenderCallback(EntityLivingBase entity, float partialTicks) {
        float scale = Vars.SCALE.interpolate(entity);
        if (scale != 1.0F) {
            GL11.glScalef(scale, scale, scale);
        }
    }

    /**
     * Fisk tilts the body for flight, gliding and other poses from {@code RenderPlayer} only; do the same for the mob
     * so
     * suits use their flight animations (horizontal boost pose, hover lean, …).
     */
    @Override
    protected void rotateCorpse(EntityLivingBase entity, float ageInTicks, float rotationYaw, float partialTicks) {
        super.rotateCorpse(entity, ageInTicks, rotationYaw, partialTicks);
        Vec3 motion = SHClientUtils.getMotion(entity);
        ModelHelper.rotateGliding(entity, motion, GLProxy.RENDER, partialTicks);
        ModelHelper.applyRotations(entity, motion, GLProxy.RENDER, partialTicks);
    }

    /** The body under the suit, unless the character's base skin is set to None (see-through). */
    @Override
    protected void renderModel(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float ageInTicks,
        float headYaw, float headPitch, float scale) {
        if (!(entity instanceof EntityFiskHero) || ((EntityFiskHero) entity).getSkin() != BaseSkin.NONE) {
            // A skin's hat layer (hair, headphones, ...) sits outside the head and would poke through the suit's
            // helmet, so it's only drawn when the suit leaves the head bare.
            modelBipedMain.bipedHeadwear.showModel = !coversHead(entity);
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
            modelBipedMain.bipedHeadwear.showModel = true;
        }
    }

    /** A helmet piece, or a chest-only suit (nanite suits like Mk50/Mk85 build their helmet from the chestplate). */
    private static boolean coversHead(EntityLivingBase entity) {
        return entity.getEquipmentInSlot(4) != null
            || entity.getEquipmentInSlot(3) != null && entity.getEquipmentInSlot(2) == null
                && entity.getEquipmentInSlot(1) == null;
    }

    /**
     * Skin of the named player if they're in this world (their real, already-downloaded skin), else Steve. In
     * singleplayer that's always your own skin.
     */
    public static ResourceLocation playerSkin(String name) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld != null && name != null && !name.isEmpty()) {
            EntityPlayer player = mc.theWorld.getPlayerEntityByName(name);
            if (player instanceof AbstractClientPlayer) {
                return ((AbstractClientPlayer) player).getLocationSkin();
            }
        }
        if (mc.thePlayer != null && (name == null || name.isEmpty())) {
            return mc.thePlayer.getLocationSkin();
        }
        return STEVE;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        if (entity instanceof EntityFiskHero && ((EntityFiskHero) entity).getSkin() == BaseSkin.PLAYER) {
            return playerSkin(((EntityFiskHero) entity).getSkinPlayer());
        }
        return STEVE;
    }
}
