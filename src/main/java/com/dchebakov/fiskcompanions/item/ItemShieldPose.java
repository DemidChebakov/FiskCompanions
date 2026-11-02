package com.dchebakov.fiskcompanions.item;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;

import com.fiskmods.heroes.client.animation.DefaultAnimation;
import com.fiskmods.heroes.client.model.IAnimatedItem;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Never obtainable: a stand-in "held item" that only exists while a blocking character's body and suit models are
 * posed. Fisk poses every biped model (base body and suit alike) from the held item's
 * {@link IAnimatedItem#applyItemAnimations}. Captain America's shield does that only for a player using it, so
 * {@code RenderFiskHero} briefly swaps this in, which raises the right arm across the chest exactly as Fisk does for a
 * blocking player.
 */
public class ItemShieldPose extends Item implements IAnimatedItem {

    public ItemShieldPose() {
        setUnlocalizedName("fiskcompanions.shield_pose");
        setMaxStackSize(1);
        setCreativeTab(null);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public DefaultAnimation getAnimationType(ItemStack stack) {
        return DefaultAnimation.CAPS_SHIELD;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void applyItemAnimations(ModelBiped model, EntityLivingBase entity, ItemStack stack, Vec3 motion,
        Vec3 prevMotion, float limbSwing, float limbSwingAmount, float ageInTicks, float headYaw, float headPitch,
        float scale, float partialTicks) {
        // ItemCapsShield.applyItemAnimations for a blocking player.
        model.bipedRightArm.rotateAngleX -= 1.1424779F;
        model.bipedRightArm.rotateAngleY = -0.9F;
    }
}
