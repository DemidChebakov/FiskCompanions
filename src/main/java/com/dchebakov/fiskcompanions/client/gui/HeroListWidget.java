package com.dchebakov.fiskcompanions.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.EnumChatFormatting;

import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Scrollable, filterable list of every loaded hero (Fisk's and all heropacks), grouped by pack. */
@SideOnly(Side.CLIENT)
public class HeroListWidget {

    public static final int ROW_H = 11;

    public final List<HeroIteration> all = FiskBridge.allIterations();
    private final List<HeroIteration> filtered = new ArrayList<>();
    public int x, y, w, rows;
    public String selectedKey = "";
    private int scroll;

    public HeroListWidget() {
        setFilter("");
    }

    public void setBounds(int x, int y, int w, int rows) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.rows = rows;
    }

    public int height() {
        return rows * ROW_H;
    }

    public int size() {
        return filtered.size();
    }

    public void setFilter(String text) {
        String q = text.toLowerCase(Locale.ROOT);
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

    public void scrollToSelected() {
        for (int i = 0; i < filtered.size(); ++i) {
            if (FiskBridge.keyOf(filtered.get(i))
                .equals(selectedKey)) {
                scroll = Math.max(0, Math.min(i - rows / 2, filtered.size() - rows));
                return;
            }
        }
    }

    public boolean contains(int mx, int my) {
        return mx >= x && mx < x + w && my >= y && my < y + height();
    }

    /** Returns true if the click selected a hero. */
    public boolean mouseClicked(int mx, int my) {
        if (!contains(mx, my)) {
            return false;
        }
        int index = scroll + (my - y) / ROW_H;
        if (index < filtered.size()) {
            selectedKey = FiskBridge.keyOf(filtered.get(index));
            return true;
        }
        return false;
    }

    public void scroll(int wheel) {
        int max = Math.max(0, filtered.size() - rows);
        scroll = Math.max(0, Math.min(max, scroll + (wheel > 0 ? -1 : 1)));
    }

    public void draw(FontRenderer font, int mx, int my) {
        Gui.drawRect(x - 1, y - 1, x + w + 1, y + height() + 1, 0xFF000000);
        Gui.drawRect(x, y, x + w, y + height(), 0xFF202020);
        for (int i = 0; i < rows && scroll + i < filtered.size(); ++i) {
            HeroIteration iter = filtered.get(scroll + i);
            int ry = y + i * ROW_H;
            boolean selected = FiskBridge.keyOf(iter)
                .equals(selectedKey);
            boolean hover = mx >= x && mx < x + w && my >= ry && my < ry + ROW_H;
            if (selected || hover) {
                Gui.drawRect(x, ry, x + w, ry + ROW_H, selected ? 0xFF3A5A8A : 0xFF333333);
            }
            String label = EnumChatFormatting.GRAY + FiskBridge.domain(iter)
                + " "
                + EnumChatFormatting.RESET
                + FiskBridge.displayName(iter);
            font.drawStringWithShadow(font.trimStringToWidth(label, w - 6), x + 3, ry + 2, 0xFFFFFF);
        }
    }
}
