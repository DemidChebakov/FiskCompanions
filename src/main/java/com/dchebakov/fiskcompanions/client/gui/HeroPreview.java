package com.dchebakov.fiskcompanions.client.gui;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.StatCollector;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.BaseSkin;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 3D preview of a hero suit for GUIs: the suit fully transformed on a client-side stand-in that is never spawned.
 * Turns slowly until the player drags it with the left mouse button.
 */
@SideOnly(Side.CLIENT)
public class HeroPreview {

    public int x, y, w, h;
    private EntityFiskHero entity;
    private String shownKey;
    private float yaw = 20.0F;
    private boolean dragging, userRotated;
    private int dragX;
    private BaseSkin skin = BaseSkin.PLAYER;
    private GuiButton skinPrev, skinNext;

    public void setBounds(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public void show(String heroKey) {
        if (heroKey == null || heroKey.equals(shownKey)) {
            return;
        }
        shownKey = heroKey;
        HeroIteration iter = FiskBridge.lookup(heroKey);
        Minecraft mc = Minecraft.getMinecraft();
        if (iter == null || mc.theWorld == null) {
            entity = null;
            return;
        }
        entity = new EntityFiskHero(mc.theWorld); // fresh per suit, so no transformation state carries over
        entity.setSkin(skin, "");
        entity.showSuit(iter);
        FiskBridge.previewSuitUp(entity);
    }

    public BaseSkin getSkin() {
        return skin;
    }

    public void setSkin(BaseSkin skin) {
        this.skin = skin;
        if (entity != null) {
            entity.setSkin(skin, ""); // "" = the viewing player's own skin
        }
    }

    /** The "< Skin >" arrows under the preview; the screen adds them to its button list. */
    public void addSkinButtons(List<GuiButton> buttons, int prevId, int nextId) {
        buttons.add(skinPrev = new SizedButton(prevId, x, y + h + 2, 14, 14, "<"));
        buttons.add(skinNext = new SizedButton(nextId, x + w - 14, y + h + 2, 14, 14, ">"));
    }

    /** Returns true if the button was one of the skin arrows (and switches the skin). */
    public boolean actionPerformed(GuiButton button) {
        if (button == skinPrev) {
            setSkin(skin.previous());
            return true;
        }
        if (button == skinNext) {
            setSkin(skin.next());
            return true;
        }
        return false;
    }

    public void tick() {
        if (entity != null) {
            entity.tickPreview(); // drives transformation timers and suit renderer animations
        }
        if (!userRotated && !dragging) {
            yaw += 1.5F;
        }
    }

    public boolean mouseClicked(int mx, int my, int button) {
        if (button == 0 && mx >= x && mx < x + w && my >= y && my < y + h) {
            dragging = true;
            dragX = mx;
            return true;
        }
        return false;
    }

    public void mouseDragged(int mx) {
        if (dragging) {
            yaw -= (mx - dragX) * 2.5F;
            dragX = mx;
            userRotated = true;
        }
    }

    public void mouseReleased() {
        dragging = false;
    }

    public void draw(FontRenderer font, float partialTicks) {
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);
        Gui.drawRect(x, y, x + w, y + h, 0xFF2A2A34);
        drawSmall(
            font,
            StatCollector.translateToLocal("gui.fiskcompanions.drag_rotate"),
            x,
            y + h - 7,
            w,
            0x777777,
            true);
        if (skinPrev != null && skinPrev.visible) {
            String label = StatCollector.translateToLocal(skin.translationKey());
            font.drawStringWithShadow(
                font.trimStringToWidth(label, w - 30),
                x + (w - Math.min(font.getStringWidth(label), w - 30)) / 2,
                y + h + 5,
                0xFFFFFF);
            drawSmall(
                font,
                StatCollector.translateToLocal("gui.fiskcompanions.skin_notice"),
                x,
                y + h + 19,
                w,
                0x999999,
                false);
        }
        if (entity != null) {
            float size = Math.max(1.0F, Vars.SCALE.get(entity));
            float shownYaw = yaw + (userRotated || dragging ? 0.0F : 1.5F * partialTicks);
            drawEntity(x + w / 2, y + h - 10, (int) (h * 0.36F / size), shownYaw, entity);
        }
    }

    /** Text at 3/4 size, wrapped to the given width (centred lines if {@code center}). */
    private static void drawSmall(FontRenderer font, String text, int x, int y, int w, int color, boolean center) {
        float s = 0.75F;
        GL11.glPushMatrix();
        GL11.glScalef(s, s, 1.0F);
        int lineY = (int) (y / s);
        for (Object o : font.listFormattedStringToWidth(text, (int) (w / s))) {
            String line = (String) o;
            int lx = center ? (int) ((x + (w - font.getStringWidth(line) * s) / 2) / s) : (int) (x / s);
            font.drawStringWithShadow(line, lx, lineY, color);
            lineY += 9;
        }
        GL11.glPopMatrix();
    }

    /** Same setup as vanilla {@code GuiInventory.func_147046_a}, with a free yaw instead of following the mouse. */
    private static void drawEntity(int x, int y, int scale, float yaw, EntityFiskHero entity) {
        GL11.glEnable(GL11.GL_COLOR_MATERIAL);
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 50.0F);
        GL11.glScalef(-scale, scale, scale);
        GL11.glRotatef(180.0F, 0.0F, 0.0F, 1.0F);
        GL11.glRotatef(135.0F, 0.0F, 1.0F, 0.0F);
        RenderHelper.enableStandardItemLighting();
        GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
        GL11.glRotatef(-10.0F, 1.0F, 0.0F, 0.0F);
        entity.renderYawOffset = entity.prevRenderYawOffset = yaw;
        entity.rotationYaw = entity.prevRotationYaw = yaw;
        entity.rotationYawHead = entity.prevRotationYawHead = yaw;
        entity.rotationPitch = entity.prevRotationPitch = 0.0F;
        GL11.glTranslatef(0.0F, entity.yOffset, 0.0F);
        RenderManager.instance.playerViewY = 180.0F;
        RenderManager.instance.renderEntityWithPosYaw(entity, 0.0D, 0.0D, 0.0D, 0.0F, 1.0F);
        GL11.glPopMatrix();
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        OpenGlHelper.setActiveTexture(OpenGlHelper.lightmapTexUnit);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        OpenGlHelper.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
}
