package com.dchebakov.fiskcompanions.client.render;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;

import org.lwjgl.opengl.GL11;

import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.client.animation.GLProxy;
import com.fiskmods.heroes.client.model.ModelHelper;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.util.SHClientUtils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Like Fisk's {@code RenderIronMan}, the suit itself is drawn by the {@code ItemHeroArmor} pieces, so any heropack
 * suit renders with its own model and JS renderer. Underneath is a Steve body, the same base a player has: many
 * suits are drawn for a player skin and leave parts transparent (open helmets, visors, gaps), which would otherwise
 * show straight through the mob.
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

    /** The Steve body under the suit; left out for suits that build their own body from custom models. */
    @Override
    protected void renderModel(EntityLivingBase entity, float limbSwing, float limbSwingAmount, float ageInTicks,
        float headYaw, float headPitch, float scale) {
        if (!(entity instanceof EntityFiskHero) || BaseBody.show((EntityFiskHero) entity)) {
            super.renderModel(entity, limbSwing, limbSwingAmount, ageInTicks, headYaw, headPitch, scale);
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return STEVE;
    }
}
