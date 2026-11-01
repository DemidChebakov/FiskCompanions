package com.dchebakov.fiskcompanions.client.render;

import java.util.LinkedList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;

import org.lwjgl.opengl.GL11;

import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.client.pack.json.trail.JsonTrail;
import com.fiskmods.heroes.client.pack.json.trail.JsonTrailLightning;
import com.fiskmods.heroes.common.entity.effect.EntitySpeedBlur;
import com.fiskmods.heroes.common.hero.speedster.SpeedsterHelper;
import com.fiskmods.heroes.common.hero.speedster.TrailHandler;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Lightning speed trails for running speedster mobs. Fisk records the trail points ({@link TrailHandler}) for any
 * entity but only draws the lightning for players, so this draws it from the same data: one jagged line per trail
 * strand through the recent positions, in the suit's trail colour, fading towards the tail.
 */
@SideOnly(Side.CLIENT)
public class SpeedTrailRenderer {

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            return;
        }
        for (Object o : mc.theWorld.loadedEntityList) {
            if (o instanceof EntityFiskHero) {
                draw((EntityFiskHero) o, event.partialTicks);
            }
        }
    }

    private static void draw(EntityFiskHero mob, float partialTicks) {
        LinkedList<EntitySpeedBlur> points = TrailHandler.getTrail(mob);
        if (points.size() < 2) {
            return;
        }
        JsonTrail trail = SpeedsterHelper.getJsonTrail(mob);
        if (trail == null || trail.lightning == null) {
            return;
        }
        JsonTrailLightning lightning = trail.lightning.get();
        Vec3 color = trail.lightning.getVecColor(mob);
        int strands = Math.max(1, lightning.getDensity());
        float jitter = lightning.getDiffer() * mob.height / 1.8F;
        float opacity = lightning.getOpacity();
        double ox = RenderManager.renderPosX, oy = RenderManager.renderPosY, oz = RenderManager.renderPosZ;
        double mx = mob.lastTickPosX + (mob.posX - mob.lastTickPosX) * partialTicks;
        double my = mob.lastTickPosY + (mob.posY - mob.lastTickPosY) * partialTicks - mob.yOffset;
        double mz = mob.lastTickPosZ + (mob.posZ - mob.lastTickPosZ) * partialTicks;

        GL11.glPushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDepthMask(false);
        GL11.glLineWidth(3.0F);
        int n = points.size();
        for (int s = 0; s < strands; ++s) {
            float y = mob.height * (s + 0.5F) / strands;
            GL11.glBegin(GL11.GL_LINE_STRIP);
            int k = 0;
            for (EntitySpeedBlur p : points) {
                float a = opacity * (k + 1) / (float) n;
                float[] f = p.lightningFactor;
                // Each point's random factors, picked per strand and per point, zig-zag the line like lightning.
                float jx = f != null && f.length > 0 ? f[(s * 2 + k) % f.length] * jitter * 2.0F : 0.0F;
                float jy = f != null && f.length > 0 ? f[(s * 2 + k + 1) % f.length] * jitter * 2.0F : 0.0F;
                GL11.glColor4f((float) color.xCoord, (float) color.yCoord, (float) color.zCoord, a);
                GL11.glVertex3d(p.posX - ox + jx, p.posY - oy + y + jy, p.posZ - oz - jx);
                ++k;
            }
            GL11.glColor4f((float) color.xCoord, (float) color.yCoord, (float) color.zCoord, opacity);
            GL11.glVertex3d(mx - ox, my - oy + y, mz - oz);
            GL11.glEnd();
        }
        GL11.glDepthMask(true);
        GL11.glPopAttrib();
        GL11.glPopMatrix();
    }
}
