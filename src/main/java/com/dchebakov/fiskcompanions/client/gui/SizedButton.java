package com.dchebakov.fiskcompanions.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.OpenGlHelper;

import org.lwjgl.opengl.GL11;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A vanilla-looking button that keeps its border at any height. {@link GuiButton} draws the top {@code height} rows
 * of its 20 px texture, so shorter buttons lose their bottom edge; this draws the body and then the real 2 px edge.
 */
@SideOnly(Side.CLIENT)
public class SizedButton extends GuiButton {

    private static final int EDGE = 2;

    public SizedButton(int id, int x, int y, int width, int height, String text) {
        super(id, x, y, width, height, text);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        FontRenderer font = mc.fontRenderer;
        mc.getTextureManager()
            .bindTexture(buttonTextures);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        field_146123_n = mouseX >= xPosition && mouseY >= yPosition
            && mouseX < xPosition + width
            && mouseY < yPosition + height;
        int v = 46 + getHoverState(field_146123_n) * 20;
        GL11.glEnable(GL11.GL_BLEND);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        int left = width / 2;
        int right = width - left;
        int body = Math.max(0, height - EDGE);
        drawTexturedModalRect(xPosition, yPosition, 0, v, left, body);
        drawTexturedModalRect(xPosition + left, yPosition, 200 - right, v, right, body);
        drawTexturedModalRect(xPosition, yPosition + body, 0, v + 20 - EDGE, left, EDGE);
        drawTexturedModalRect(xPosition + left, yPosition + body, 200 - right, v + 20 - EDGE, right, EDGE);
        mouseDragged(mc, mouseX, mouseY);
        int color = 0xE0E0E0;
        if (packedFGColour != 0) {
            color = packedFGColour;
        } else if (!enabled) {
            color = 0xA0A0A0;
        } else if (field_146123_n) {
            color = 0xFFFFA0;
        }
        drawCenteredString(font, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, color);
    }
}
