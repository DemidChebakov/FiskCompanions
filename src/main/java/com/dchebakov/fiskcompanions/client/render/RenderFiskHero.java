package com.dchebakov.fiskcompanions.client.render;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Same approach as Fisk's {@code RenderIronMan}: an invisible biped body; the hero suit itself is drawn by the
 * {@code ItemHeroArmor} pieces, so any heropack suit renders with its own model and JS renderer.
 */
@SideOnly(Side.CLIENT)
public class RenderFiskHero extends RenderBiped {

    private static final ResourceLocation NULL_TEXTURE = new ResourceLocation("fiskheroes", "textures/null.png");

    public RenderFiskHero() {
        super(new ModelBiped(), 0.5F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return NULL_TEXTURE;
    }
}
