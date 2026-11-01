package com.dchebakov.fiskcompanions.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.dchebakov.fiskcompanions.CommonProxy;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.network.PacketConfigureHero;
import com.fiskmods.heroes.common.data.var.Vars;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Pick any loaded hero (Fisk's and every heropack), set Hero/Villain, optionally bind to yourself. A 3D preview of the
 * selected suit sits left of the list; it turns slowly until the player drags it to rotate.
 */
@SideOnly(Side.CLIENT)
public class GuiHeroConfigurator extends GuiScreen {

    private static final int PANEL_W = 260;
    private static final int PREVIEW_W = 100;
    private static final int GAP = 6;
    private static final int ROW_H = 11;
    private static final int ROWS = 14;
    private static final int BTN_ALIGN = 0, BTN_BIND = 1, BTN_APPLY = 2, BTN_CANCEL = 3;

    private final EntityFiskHero hero;
    private final List<HeroIteration> all = FiskBridge.allIterations();
    private final List<HeroIteration> filtered = new ArrayList<>();
    private GuiTextField search;
    private GuiButton alignButton;
    private GuiButton bindButton;
    private String selectedKey;
    private Alignment alignment;
    private boolean bind;
    private int scroll;
    private int left, top, listTop, previewLeft;
    /** Client-side stand-in wearing the selected suit; never spawned. */
    private EntityFiskHero preview;
    private float previewYaw = 20.0F;
    private boolean dragging, userRotated;
    private int dragX;

    public GuiHeroConfigurator(EntityFiskHero hero) {
        this.hero = hero;
        this.selectedKey = hero.getHeroKey();
        this.alignment = hero.getAlignment();
        this.bind = hero.getOwnerId() == null;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        previewLeft = (width - (PREVIEW_W + GAP + PANEL_W)) / 2;
        left = previewLeft + PREVIEW_W + GAP;
        top = (height - (ROWS * ROW_H + 80)) / 2;
        listTop = top + 34;
        search = new GuiTextField(fontRendererObj, left, top + 16, PANEL_W, 14);
        search.setFocused(true);
        int by = listTop + ROWS * ROW_H + 6;
        int half = PANEL_W / 2 - 2;
        buttonList.clear();
        buttonList.add(alignButton = new GuiButton(BTN_ALIGN, left, by, half, 20, ""));
        buttonList.add(bindButton = new GuiButton(BTN_BIND, left + half + 4, by, half, 20, ""));
        buttonList.add(new GuiButton(BTN_APPLY, left, by + 24, half, 20, tr("gui.fiskcompanions.apply")));
        buttonList.add(new GuiButton(BTN_CANCEL, left + half + 4, by + 24, half, 20, tr("gui.cancel")));
        refreshButtons();
        applyFilter();
        scrollToSelected();
        updatePreview();
    }

    private void updatePreview() {
        HeroIteration iter = FiskBridge.lookup(selectedKey);
        if (iter == null || mc.theWorld == null) {
            preview = null;
            return;
        }
        preview = new EntityFiskHero(mc.theWorld); // fresh per suit, so no transformation state carries over
        preview.showSuit(iter);
        FiskBridge.previewSuitUp(preview);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    private static String tr(String key) {
        return StatCollector.translateToLocal(key);
    }

    private void refreshButtons() {
        alignButton.displayString = alignment.color + tr(alignment.translationKey());
        bindButton.displayString = tr(bind ? "gui.fiskcompanions.bind_on" : "gui.fiskcompanions.bind_off");
        bindButton.enabled = alignment == Alignment.HERO;
    }

    private void applyFilter() {
        String q = search.getText()
            .toLowerCase(Locale.ROOT);
        filtered.clear();
        for (HeroIteration iter : all) {
            if (q.isEmpty() || FiskBridge.displayName(iter)
                .toLowerCase(Locale.ROOT)
                .contains(q)
                || FiskBridge.domain(iter)
                    .toLowerCase(Locale.ROOT)
                    .contains(q)) {
                filtered.add(iter);
            }
        }
        scroll = 0;
    }

    /** Opens with the mob's current hero in view. */
    private void scrollToSelected() {
        for (int i = 0; i < filtered.size(); ++i) {
            if (FiskBridge.keyOf(filtered.get(i))
                .equals(selectedKey)) {
                scroll = Math.max(0, Math.min(i - ROWS / 2, filtered.size() - ROWS));
                return;
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case BTN_ALIGN:
                alignment = alignment.opposite();
                refreshButtons();
                break;
            case BTN_BIND:
                bind = !bind;
                refreshButtons();
                break;
            case BTN_APPLY:
                apply();
                break;
            case BTN_CANCEL:
                mc.displayGuiScreen(null);
                break;
        }
    }

    private void apply() {
        CommonProxy.network
            .sendToServer(new PacketConfigureHero(hero, selectedKey, alignment, bind && alignment == Alignment.HERO));
        mc.displayGuiScreen(null);
    }

    /** Dev self-test hook: clicks a hero in the list (updates the preview). */
    public void select(String heroKey) {
        selectedKey = heroKey;
        updatePreview();
    }

    /** Dev self-test hook: makes the same choices a player would click, then presses Apply. */
    public void selectAndApply(String heroKey, Alignment newAlignment, boolean bindToMe) {
        selectedKey = heroKey;
        alignment = newAlignment;
        bind = bindToMe;
        apply();
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (search.textboxKeyTyped(c, key)) {
            applyFilter();
        } else {
            super.keyTyped(c, key);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        search.mouseClicked(x, y, button);
        if (x >= left && x < left + PANEL_W && y >= listTop && y < listTop + ROWS * ROW_H) {
            int index = scroll + (y - listTop) / ROW_H;
            if (index < filtered.size()) {
                selectedKey = FiskBridge.keyOf(filtered.get(index));
                updatePreview();
            }
        }
        if (button == 0 && inPreview(x, y)) {
            dragging = true;
            dragX = x;
        }
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        super.mouseClickMove(x, y, button, time);
        if (dragging) {
            previewYaw -= (x - dragX) * 2.5F;
            dragX = x;
            userRotated = true;
        }
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int which) {
        super.mouseMovedOrUp(x, y, which);
        if (which == 0) {
            dragging = false;
        }
    }

    private boolean inPreview(int x, int y) {
        return x >= previewLeft && x < previewLeft + PREVIEW_W && y >= listTop && y < listTop + ROWS * ROW_H;
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int max = Math.max(0, filtered.size() - ROWS);
            scroll = Math.max(0, Math.min(max, scroll + (wheel > 0 ? -1 : 1)));
        }
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        if (preview != null) {
            ++preview.ticksExisted; // drives suit renderer animations
        }
        if (!userRotated && !dragging) {
            previewYaw += 1.5F;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, tr("gui.fiskcompanions.title"), width / 2, top + 2, 0xFFFFFF);
        drawPreview(partialTicks);
        search.drawTextBox();

        drawRect(left - 1, listTop - 1, left + PANEL_W + 1, listTop + ROWS * ROW_H + 1, 0xFF000000);
        drawRect(left, listTop, left + PANEL_W, listTop + ROWS * ROW_H, 0xFF202020);
        for (int i = 0; i < ROWS && scroll + i < filtered.size(); ++i) {
            HeroIteration iter = filtered.get(scroll + i);
            int y = listTop + i * ROW_H;
            boolean selected = FiskBridge.keyOf(iter)
                .equals(selectedKey);
            boolean hover = mouseX >= left && mouseX < left + PANEL_W && mouseY >= y && mouseY < y + ROW_H;
            if (selected || hover) {
                drawRect(left, y, left + PANEL_W, y + ROW_H, selected ? 0xFF3A5A8A : 0xFF333333);
            }
            String label = EnumChatFormatting.GRAY + FiskBridge.domain(iter)
                + " "
                + EnumChatFormatting.RESET
                + FiskBridge.displayName(iter);
            drawString(
                fontRendererObj,
                fontRendererObj.trimStringToWidth(label, PANEL_W - 6),
                left + 3,
                y + 2,
                0xFFFFFF);
        }
        if (filtered.isEmpty()) {
            drawCenteredString(fontRendererObj, tr("gui.fiskcompanions.no_match"), width / 2, listTop + 4, 0x999999);
        }
        String count = filtered.size() + " / " + all.size();
        drawString(fontRendererObj, count, left + PANEL_W - fontRendererObj.getStringWidth(count), top + 4, 0x999999);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPreview(float partialTicks) {
        int bottom = listTop + ROWS * ROW_H;
        drawRect(previewLeft - 1, listTop - 1, previewLeft + PREVIEW_W + 1, bottom + 1, 0xFF000000);
        drawRect(previewLeft, listTop, previewLeft + PREVIEW_W, bottom, 0xFF2A2A34);
        drawCenteredString(
            fontRendererObj,
            tr("gui.fiskcompanions.drag_rotate"),
            previewLeft + PREVIEW_W / 2,
            bottom + 6,
            0x888888);
        if (preview != null) {
            float yaw = previewYaw + (userRotated || dragging ? 0.0F : 1.5F * partialTicks);
            float size = Math.max(1.0F, Vars.SCALE.get(preview));
            drawEntity(previewLeft + PREVIEW_W / 2, bottom - 12, (int) (52 / size), yaw, preview);
        }
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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
