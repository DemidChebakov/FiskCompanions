package com.dchebakov.fiskcompanions.client.render;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

import com.fiskmods.heroes.common.data.var.Vars;

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

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return STEVE;
    }
}
