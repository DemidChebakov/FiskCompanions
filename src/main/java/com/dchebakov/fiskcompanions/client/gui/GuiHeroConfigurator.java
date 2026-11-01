package com.dchebakov.fiskcompanions.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.dchebakov.fiskcompanions.CommonProxy;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.EntityFiskHero;
import com.dchebakov.fiskcompanions.network.PacketConfigureHero;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pick any loaded hero (Fisk's and every heropack), set Hero/Villain, optionally bind to yourself. */
@SideOnly(Side.CLIENT)
public class GuiHeroConfigurator extends GuiScreen {

    private static final int PANEL_W = 260;
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
    private int left, top, listTop;

    public GuiHeroConfigurator(EntityFiskHero hero) {
        this.hero = hero;
        this.selectedKey = hero.getHeroKey();
        this.alignment = hero.getAlignment();
        this.bind = hero.getOwnerId() == null;
    }

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        left = (width - PANEL_W) / 2;
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
            }
        }
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
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, tr("gui.fiskcompanions.title"), width / 2, top + 2, 0xFFFFFF);
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

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
