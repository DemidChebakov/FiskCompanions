package com.dchebakov.fiskcompanions.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.dchebakov.fiskcompanions.CommonProxy;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.BaseSkin;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.network.PacketConfigureHero;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Edit one existing character (right-click it with the Hero Configurator): pick any loaded hero, set Hero/Villain,
 * optionally bind to yourself. A 3D preview of the selected suit sits left of the list.
 */
@SideOnly(Side.CLIENT)
public class GuiHeroConfigurator extends GuiScreen {

    private static final int PANEL_W = 260;
    private static final int PREVIEW_W = 100;
    private static final int GAP = 6;
    private static final int ROWS = 14;
    private static final int BTN_ALIGN = 0, BTN_BIND = 1, BTN_APPLY = 2, BTN_CANCEL = 3, BTN_SKIN_PREV = 4,
        BTN_SKIN_NEXT = 5;

    private final EntityFiskHero hero;
    private final HeroListWidget list = new HeroListWidget();
    private final HeroPreview preview = new HeroPreview();
    private GuiTextField search;
    private GuiButton alignButton;
    private GuiButton bindButton;
    private Alignment alignment;
    private boolean bind;
    private BaseSkin skin;
    private int left, top;

    public GuiHeroConfigurator(EntityFiskHero hero) {
        this.hero = hero;
        this.list.selectedKey = hero.getHeroKey();
        this.alignment = hero.getAlignment();
        this.bind = hero.getOwnerId() == null;
        this.skin = hero.getSkin();
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        int previewLeft = (width - (PREVIEW_W + GAP + PANEL_W)) / 2;
        left = previewLeft + PREVIEW_W + GAP;
        top = (height - (ROWS * HeroListWidget.ROW_H + 80)) / 2;
        int listTop = top + 34;
        search = new GuiTextField(fontRendererObj, left, top + 16, PANEL_W, 14);
        search.setFocused(true);
        list.setBounds(left, listTop, PANEL_W, ROWS);
        preview.setBounds(previewLeft, listTop, PREVIEW_W, list.height());
        preview.setSkin(skin);
        int by = listTop + list.height() + 6;
        int half = PANEL_W / 2 - 2;
        buttonList.clear();
        buttonList.add(alignButton = new GuiButton(BTN_ALIGN, left, by, half, 20, ""));
        buttonList.add(bindButton = new GuiButton(BTN_BIND, left + half + 4, by, half, 20, ""));
        buttonList.add(new GuiButton(BTN_APPLY, left, by + 24, half, 20, tr("gui.fiskcompanions.apply")));
        buttonList.add(new GuiButton(BTN_CANCEL, left + half + 4, by + 24, half, 20, tr("gui.cancel")));
        preview.addSkinButtons(buttonList, BTN_SKIN_PREV, BTN_SKIN_NEXT);
        refreshButtons();
        list.setFilter(search.getText());
        list.scrollToSelected();
        preview.show(list.selectedKey);
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

    @Override
    protected void actionPerformed(GuiButton button) {
        if (preview.actionPerformed(button)) {
            skin = preview.getSkin();
            return;
        }
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
        CommonProxy.network.sendToServer(
            new PacketConfigureHero(hero, list.selectedKey, alignment, bind && alignment == Alignment.HERO, skin));
        mc.displayGuiScreen(null);
    }

    /** Dev self-test hook: clicks a hero in the list (updates the preview). */
    public void select(String heroKey) {
        list.selectedKey = heroKey;
        preview.show(heroKey);
    }

    /** Dev self-test hook: makes the same choices a player would click, then presses Apply. */
    public void selectAndApply(String heroKey, Alignment newAlignment, boolean bindToMe, BaseSkin newSkin) {
        list.selectedKey = heroKey;
        skin = newSkin;
        alignment = newAlignment;
        bind = bindToMe;
        apply();
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (search.textboxKeyTyped(c, key)) {
            list.setFilter(search.getText());
        } else {
            super.keyTyped(c, key);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        search.mouseClicked(x, y, button);
        if (list.mouseClicked(x, y)) {
            preview.show(list.selectedKey);
        }
        preview.mouseClicked(x, y, button);
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        super.mouseClickMove(x, y, button, time);
        preview.mouseDragged(x);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int which) {
        super.mouseMovedOrUp(x, y, which);
        if (which == 0) {
            preview.mouseReleased();
        }
    }

    @Override
    public void handleMouseInput() {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            list.scroll(wheel);
        }
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        preview.tick();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, tr("gui.fiskcompanions.title"), width / 2, top + 2, 0xFFFFFF);
        preview.draw(fontRendererObj, partialTicks);
        search.drawTextBox();
        list.draw(fontRendererObj, mouseX, mouseY);
        if (list.size() == 0) {
            drawCenteredString(
                fontRendererObj,
                tr("gui.fiskcompanions.no_match"),
                left + PANEL_W / 2,
                list.y + 4,
                0x999999);
        }
        String count = list.size() + " / " + list.all.size();
        drawString(fontRendererObj, count, left + PANEL_W - fontRendererObj.getStringWidth(count), top + 4, 0x999999);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
