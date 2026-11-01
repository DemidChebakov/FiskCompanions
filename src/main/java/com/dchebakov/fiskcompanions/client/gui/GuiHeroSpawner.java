package com.dchebakov.fiskcompanions.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import com.dchebakov.fiskcompanions.CommonProxy;
import com.dchebakov.fiskcompanions.client.Squads;
import com.dchebakov.fiskcompanions.compat.fisk.FiskBridge;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.dchebakov.fiskcompanions.entity.BaseSkin;
import com.dchebakov.fiskcompanions.network.PacketRemoveAll;
import com.dchebakov.fiskcompanions.network.PacketSettingsSync;
import com.dchebakov.fiskcompanions.network.PacketSpawnHeroes;
import com.dchebakov.fiskcompanions.network.PacketUpdateSettings;
import com.dchebakov.fiskcompanions.world.Spawning;
import com.fiskmods.heroes.common.hero.HeroIteration;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * The Hero Configurator's main menu (right-click with the tool). Three tabs:
 * <ul>
 * <li>Spawn: pick a hero (searchable list + 3D preview), side, owner and count; spawn it or add it to a squad.</li>
 * <li>Squads: saved profiles (e.g. Avengers) spawned with one button; create, rename, delete, edit members.</li>
 * <li>Settings: the world's friendly-fire rules (singleplayer host or ops).</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class GuiHeroSpawner extends GuiScreen {

    public static final int TAB_SPAWN = 0, TAB_SQUADS = 1, TAB_SETTINGS = 2;

    private static final int W = 404;
    private static final int H = 226;
    private static final int SQUAD_ROWS = 10;
    private static final int MAX_COUNT = 10;

    private static final int BTN_TAB = 0; // 0..2
    private static final int BTN_DONE = 3, BTN_REMOVE_ALL = 4;
    private static final int BTN_SIDE = 10, BTN_BIND = 11, BTN_LESS = 12, BTN_MORE = 13, BTN_RANDOM = 14,
        BTN_SPAWN = 15, BTN_ADD = 16, BTN_SQUAD_PREV = 17, BTN_SQUAD_NEXT = 18, BTN_DUP_ADD = 19;
    private static final int BTN_DUP_REMOVE = 25, BTN_DUP_CANCEL = 26, BTN_SKIN_PREV = 27, BTN_SKIN_NEXT = 28,
        BTN_MEMBER_SKIN = 29;
    private static final int CONFIRM_TICKS = 60;
    private static final int STATUS_TICKS = 60;
    private static final int BTN_NEW = 20, BTN_DELETE = 21, BTN_REMOVE = 22, BTN_SWITCH = 23, BTN_SPAWN_SQUAD = 24;
    private static final int BTN_FIGHT_BACK = 30, BTN_FRIENDLY_FIRE = 31;

    // State that survives reopening the menu during a session.
    private static int tab = TAB_SPAWN;
    private static String lastHero = "";
    private static Alignment side = Alignment.HERO;
    private static boolean bind = true;
    private static int count = 1;
    private static int selectedSquad;
    private static BaseSkin skin = BaseSkin.PLAYER;

    private final HeroListWidget list = new HeroListWidget();
    private final HeroPreview preview = new HeroPreview();
    private GuiTextField search;
    private GuiTextField squadName;
    private final List<GuiButton> spawnButtons = new ArrayList<>();
    private final List<GuiButton> squadButtons = new ArrayList<>();
    private final List<GuiButton> settingsButtons = new ArrayList<>();
    private GuiButton sideButton, bindButton, fightBackButton, friendlyFireButton;
    private int left, top, contentTop;
    private int selectedMember = -1;
    private int squadScroll, memberScroll;
    private GuiButton removeAllButton, addButton;
    /** Ticks left to click Remove all a second time. */
    private int removeAllArmed;
    /** Hero waiting for the duplicate decision (Add again / Remove it / Cancel), or null. */
    private String duplicateKey;
    private String status = "";
    private int statusTicks;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        left = (width - W) / 2;
        top = Math.max(2, (height - H) / 2);
        contentTop = top + 32;
        buttonList.clear();
        spawnButtons.clear();
        squadButtons.clear();
        settingsButtons.clear();

        String[] tabs = { "gui.fiskcompanions.tab.spawn", "gui.fiskcompanions.tab.squads",
            "gui.fiskcompanions.tab.settings" };
        for (int i = 0; i < tabs.length; ++i) {
            buttonList.add(new SizedButton(BTN_TAB + i, left + i * 84, top + 12, 80, 16, tr(tabs[i])));
        }
        buttonList.add(new SizedButton(BTN_DONE, left + W - 56, top + 12, 56, 16, tr("gui.done")));
        buttonList.add(removeAllButton = new SizedButton(BTN_REMOVE_ALL, left + 254, top + 12, 88, 16, ""));

        // Spawn tab: preview | search + list | options column
        if (lastHero.isEmpty() && !list.all.isEmpty()) {
            lastHero = FiskBridge.keyOf(list.all.get(0));
        }
        list.selectedKey = lastHero;
        preview.setBounds(left, contentTop, 80, 128);
        preview.setSkin(skin);
        preview.addSkinButtons(spawnButtons, BTN_SKIN_PREV, BTN_SKIN_NEXT);
        search = new GuiTextField(fontRendererObj, left + 86, contentTop, 196, 12);
        list.setBounds(left + 86, contentTop + 16, 196, 12);
        list.setFilter("");
        list.scrollToSelected();
        preview.show(lastHero);
        int cx = left + 288;
        int cw = W - 288;
        spawnButtons.add(sideButton = new SizedButton(BTN_SIDE, cx, contentTop, cw, 18, ""));
        spawnButtons.add(bindButton = new SizedButton(BTN_BIND, cx, contentTop + 21, cw, 18, ""));
        spawnButtons.add(new SizedButton(BTN_LESS, cx, contentTop + 42, 20, 18, "-"));
        spawnButtons.add(new SizedButton(BTN_MORE, cx + cw - 20, contentTop + 42, 20, 18, "+"));
        spawnButtons.add(new SizedButton(BTN_RANDOM, cx, contentTop + 63, cw, 18, tr("gui.fiskcompanions.random")));
        spawnButtons.add(new SizedButton(BTN_SPAWN, cx, contentTop + 90, cw, 20, tr("gui.fiskcompanions.spawn")));
        // "< Squad >": which squad Add to squad goes to
        spawnButtons.add(new SizedButton(BTN_SQUAD_PREV, cx, contentTop + 114, 18, 18, "<"));
        spawnButtons.add(new SizedButton(BTN_SQUAD_NEXT, cx + cw - 18, contentTop + 114, 18, 18, ">"));
        spawnButtons.add(
            addButton = new SizedButton(BTN_ADD, cx, contentTop + 135, cw, 18, tr("gui.fiskcompanions.add_to_squad")));
        // Duplicate warning answers: [Add] [Remove] [x], replacing Add to squad while the warning is shown.
        int half = (cw - 18 - 4) / 2;
        spawnButtons
            .add(new SizedButton(BTN_DUP_ADD, cx, contentTop + 135, half, 18, tr("gui.fiskcompanions.dup_add")));
        spawnButtons.add(
            new SizedButton(
                BTN_DUP_REMOVE,
                cx + half + 2,
                contentTop + 135,
                half,
                18,
                tr("gui.fiskcompanions.dup_remove")));
        spawnButtons.add(new SizedButton(BTN_DUP_CANCEL, cx + cw - 18, contentTop + 135, 18, 18, "x"));

        // Squads tab: squad list | members
        squadName = new GuiTextField(fontRendererObj, left, contentTop + 138, 130, 12);
        squadButtons.add(new SizedButton(BTN_NEW, left, contentTop + 116, 63, 18, tr("gui.fiskcompanions.new_squad")));
        squadButtons
            .add(new SizedButton(BTN_DELETE, left + 67, contentTop + 116, 63, 18, tr("gui.fiskcompanions.delete")));
        int mx = left + 136;
        squadButtons.add(new SizedButton(BTN_REMOVE, mx, contentTop + 116, 88, 18, tr("gui.fiskcompanions.remove")));
        squadButtons
            .add(new SizedButton(BTN_SWITCH, mx + 90, contentTop + 116, 88, 18, tr("gui.fiskcompanions.switch_side")));
        squadButtons.add(
            new SizedButton(BTN_MEMBER_SKIN, mx + 180, contentTop + 116, 88, 18, tr("gui.fiskcompanions.member_skin")));
        squadButtons.add(new SizedButton(BTN_SPAWN_SQUAD, mx, contentTop + 138, 268, 20, ""));

        // Settings tab
        settingsButtons.add(fightBackButton = new SizedButton(BTN_FIGHT_BACK, left + 52, contentTop + 6, 300, 20, ""));
        settingsButtons
            .add(friendlyFireButton = new SizedButton(BTN_FRIENDLY_FIRE, left + 52, contentTop + 66, 300, 20, ""));

        buttonList.addAll(spawnButtons);
        buttonList.addAll(squadButtons);
        buttonList.addAll(settingsButtons);
        selectSquad(selectedSquad);
        refresh();
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
        Squads.save();
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }

    private List<Squads.Squad> squads() {
        return Squads.all();
    }

    private Squads.Squad currentSquad() {
        List<Squads.Squad> all = squads();
        return selectedSquad >= 0 && selectedSquad < all.size() ? all.get(selectedSquad) : null;
    }

    private void selectSquad(int index) {
        selectedSquad = Math.max(0, Math.min(index, squads().size() - 1));
        selectedMember = -1;
        memberScroll = 0;
        Squads.Squad squad = currentSquad();
        squadName.setText(squad != null ? squad.name : "");
    }

    /** Shows the active tab's controls and updates button labels. */
    private void refresh() {
        for (GuiButton b : spawnButtons) b.visible = tab == TAB_SPAWN;
        for (GuiButton b : squadButtons) b.visible = tab == TAB_SQUADS;
        for (GuiButton b : settingsButtons) b.visible = tab == TAB_SETTINGS;
        for (int i = 0; i < 3; ++i) {
            ((GuiButton) buttonList.get(BTN_TAB + i)).enabled = tab != i;
        }
        sideButton.displayString = side.color + tr(side.translationKey());
        bindButton.displayString = tr(bind ? "gui.fiskcompanions.bind_on" : "gui.fiskcompanions.bind_off");
        bindButton.enabled = side == Alignment.HERO;
        Squads.Squad squad = currentSquad();
        for (GuiButton b : squadButtons) {
            if (b.id == BTN_SPAWN_SQUAD) {
                b.displayString = tr("gui.fiskcompanions.spawn_squad", squad != null ? squad.name : "-");
                b.enabled = squad != null && !squad.members.isEmpty();
            } else if (b.id == BTN_REMOVE || b.id == BTN_SWITCH || b.id == BTN_MEMBER_SKIN) {
                b.enabled = squad != null && selectedMember >= 0 && selectedMember < squad.members.size();
            } else if (b.id == BTN_DELETE) {
                b.enabled = squad != null;
            }
        }
        for (GuiButton b : spawnButtons) {
            if (b.id == BTN_ADD) {
                b.enabled = squad != null;
                b.visible &= duplicateKey == null;
            } else if (b.id == BTN_DUP_ADD || b.id == BTN_DUP_REMOVE || b.id == BTN_DUP_CANCEL) {
                b.visible &= duplicateKey != null;
            } else if (b.id == BTN_SQUAD_PREV || b.id == BTN_SQUAD_NEXT) {
                b.enabled = squads().size() > 1;
            }
        }
        removeAllButton.displayString = removeAllArmed > 0
            ? EnumChatFormatting.RED + tr("gui.fiskcompanions.remove_all_confirm")
            : tr("gui.fiskcompanions.remove_all");
        boolean canEdit = PacketSettingsSync.clientCanEdit;
        fightBackButton.displayString = tr(
            PacketSettingsSync.clientFightBackPlayers ? "gui.fiskcompanions.fight_back_on"
                : "gui.fiskcompanions.fight_back_off");
        friendlyFireButton.displayString = tr(
            PacketSettingsSync.clientAllyFriendlyFire ? "gui.fiskcompanions.friendly_fire_on"
                : "gui.fiskcompanions.friendly_fire_off");
        fightBackButton.enabled = friendlyFireButton.enabled = canEdit;
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        Squads.Squad squad = currentSquad();
        switch (button.id) {
            case BTN_TAB:
            case BTN_TAB + 1:
            case BTN_TAB + 2:
                tab = button.id - BTN_TAB;
                break;
            case BTN_DONE:
                mc.displayGuiScreen(null);
                return;
            case BTN_SIDE:
                side = side.opposite();
                break;
            case BTN_BIND:
                bind = !bind;
                break;
            case BTN_LESS:
                count = Math.max(1, count - 1);
                break;
            case BTN_MORE:
                count = Math.min(MAX_COUNT, count + 1);
                break;
            case BTN_RANDOM:
                pickRandom();
                break;
            case BTN_SPAWN:
                spawnSelected();
                return;
            case BTN_ADD:
                addToSquad(squad, list.selectedKey);
                break;
            case BTN_DUP_ADD:
                if (squad != null && duplicateKey != null) {
                    squad.members.add(new Squads.Member(duplicateKey, side, skin));
                    Squads.save();
                    setStatus(tr("gui.fiskcompanions.added", squad.name));
                }
                duplicateKey = null;
                break;
            case BTN_DUP_REMOVE:
                if (squad != null && duplicateKey != null) {
                    for (int i = 0; i < squad.members.size(); ++i) {
                        if (squad.members.get(i).hero.equals(duplicateKey)) {
                            squad.members.remove(i);
                            break;
                        }
                    }
                    Squads.save();
                    setStatus(tr("gui.fiskcompanions.removed_from", squad.name));
                }
                duplicateKey = null;
                break;
            case BTN_DUP_CANCEL:
                duplicateKey = null;
                break;
            case BTN_SQUAD_PREV:
            case BTN_SQUAD_NEXT:
                if (!squads().isEmpty()) {
                    int n = squads().size();
                    selectSquad((selectedSquad + (button.id == BTN_SQUAD_NEXT ? 1 : n - 1)) % n);
                }
                duplicateKey = null;
                break;
            case BTN_REMOVE_ALL:
                if (removeAllArmed > 0) {
                    CommonProxy.network.sendToServer(new PacketRemoveAll());
                    mc.displayGuiScreen(null);
                    return;
                }
                removeAllArmed = CONFIRM_TICKS; // second click within 3 s confirms
                break;
            case BTN_NEW:
                Squads.Squad created = new Squads.Squad();
                created.name = tr("gui.fiskcompanions.new_squad_name", squads().size() + 1);
                squads().add(created);
                Squads.save();
                selectSquad(squads().size() - 1);
                break;
            case BTN_DELETE:
                if (squad != null) {
                    squads().remove(squad);
                    Squads.save();
                    selectSquad(selectedSquad);
                }
                break;
            case BTN_REMOVE:
                if (squad != null && selectedMember >= 0 && selectedMember < squad.members.size()) {
                    squad.members.remove(selectedMember);
                    selectedMember = -1;
                    Squads.save();
                }
                break;
            case BTN_SWITCH:
                if (squad != null && selectedMember >= 0 && selectedMember < squad.members.size()) {
                    Squads.Member m = squad.members.get(selectedMember);
                    m.side = m.alignment()
                        .opposite()
                        .name();
                    Squads.save();
                }
                break;
            case BTN_MEMBER_SKIN:
                if (squad != null && selectedMember >= 0 && selectedMember < squad.members.size()) {
                    Squads.Member m = squad.members.get(selectedMember);
                    m.skin = m.baseSkin()
                        .next()
                        .name();
                    Squads.save();
                }
                break;
            case BTN_SKIN_PREV:
            case BTN_SKIN_NEXT:
                preview.actionPerformed(button);
                skin = preview.getSkin();
                break;
            case BTN_SPAWN_SQUAD:
                spawnSquad(squad);
                return;
            case BTN_FIGHT_BACK:
                PacketSettingsSync.clientFightBackPlayers = !PacketSettingsSync.clientFightBackPlayers;
                sendSettings();
                break;
            case BTN_FRIENDLY_FIRE:
                PacketSettingsSync.clientAllyFriendlyFire = !PacketSettingsSync.clientAllyFriendlyFire;
                sendSettings();
                break;
        }
        refresh();
    }

    /** Adds the hero to the squad, or asks first if it's already in it. */
    private void addToSquad(Squads.Squad squad, String heroKey) {
        if (squad == null || FiskBridge.lookup(heroKey) == null) {
            return;
        }
        for (Squads.Member m : squad.members) {
            if (m.hero.equals(heroKey)) {
                duplicateKey = heroKey;
                return;
            }
        }
        squad.members.add(new Squads.Member(heroKey, side, skin));
        Squads.save();
        setStatus(tr("gui.fiskcompanions.added", squad.name));
    }

    private void setStatus(String text) {
        status = text;
        statusTicks = STATUS_TICKS;
    }

    private void sendSettings() {
        CommonProxy.network.sendToServer(
            new PacketUpdateSettings(
                PacketSettingsSync.clientFightBackPlayers,
                PacketSettingsSync.clientAllyFriendlyFire));
    }

    private void pickRandom() {
        List<HeroIteration> pool = new ArrayList<>();
        for (HeroIteration iter : list.all) {
            if (FiskBridge.hasHelmet(iter)) pool.add(iter);
        }
        if (pool.isEmpty()) pool.addAll(list.all);
        if (!pool.isEmpty()) {
            select(FiskBridge.keyOf(pool.get(mc.theWorld.rand.nextInt(pool.size()))));
        }
    }

    private void select(String heroKey) {
        list.selectedKey = lastHero = heroKey;
        search.setText("");
        list.setFilter("");
        list.scrollToSelected();
        preview.show(heroKey);
    }

    private void spawnSelected() {
        if (FiskBridge.lookup(list.selectedKey) == null) {
            return;
        }
        List<Spawning.Entry> entries = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            entries.add(new Spawning.Entry(list.selectedKey, side, skin));
        }
        CommonProxy.network.sendToServer(new PacketSpawnHeroes(entries, bind));
        mc.displayGuiScreen(null);
    }

    private void spawnSquad(Squads.Squad squad) {
        if (squad == null) {
            return;
        }
        List<Spawning.Entry> entries = new ArrayList<>();
        for (Squads.Member m : squad.members) {
            if (FiskBridge.lookup(m.hero) != null) {
                entries.add(new Spawning.Entry(m.hero, m.alignment(), m.baseSkin()));
            }
        }
        if (!entries.isEmpty()) {
            CommonProxy.network.sendToServer(new PacketSpawnHeroes(entries, bind));
        }
        mc.displayGuiScreen(null);
    }

    // ---- dev self-test hooks ----

    public void showTab(int newTab) {
        tab = newTab;
        refresh();
    }

    /** Switches the skin selector under the preview, as clicking its arrows would. */
    public void setSkinForTest(BaseSkin newSkin) {
        skin = newSkin;
        preview.setSkin(newSkin);
    }

    /** Selects a hero and presses Add to squad; returns true if the duplicate warning came up. */
    public boolean addToSquadForTest(String heroKey) {
        list.selectedKey = lastHero = heroKey;
        addToSquad(currentSquad(), heroKey);
        refresh();
        return duplicateKey != null;
    }

    /** Answers the duplicate warning with the close (x) button. */
    public void cancelDuplicateForTest() {
        actionPerformed(buttonById(BTN_DUP_CANCEL));
    }

    /** Picks a squad in the "< Squad >" selector. */
    public void selectSquadNamed(String name) {
        for (int i = 0; i < squads().size(); ++i) {
            if (squads().get(i).name.equals(name)) {
                selectSquad(i);
                refresh();
                return;
            }
        }
    }

    /** Clicks Remove all twice, as a player confirming it. */
    public void removeAllForTest() {
        actionPerformed(removeAllButton);
        actionPerformed(removeAllButton);
    }

    private GuiButton buttonById(int id) {
        for (Object o : buttonList) {
            if (((GuiButton) o).id == id) return (GuiButton) o;
        }
        return null;
    }

    /** Selects the squad with this name and presses its Spawn button, as a player would. */
    public void spawnSquadNamed(String name) {
        for (int i = 0; i < squads().size(); ++i) {
            if (squads().get(i).name.equals(name)) {
                selectSquad(i);
                spawnSquad(currentSquad());
                return;
            }
        }
    }

    // ---- input ----

    @Override
    protected void keyTyped(char c, int key) {
        if (tab == TAB_SPAWN && search.textboxKeyTyped(c, key)) {
            list.setFilter(search.getText());
        } else if (tab == TAB_SQUADS && squadName.textboxKeyTyped(c, key)) {
            Squads.Squad squad = currentSquad();
            if (squad != null && !squadName.getText()
                .trim()
                .isEmpty()) {
                squad.name = squadName.getText()
                    .trim();
                refresh();
            }
        } else {
            super.keyTyped(c, key);
        }
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (tab == TAB_SPAWN) {
            search.mouseClicked(x, y, button);
            if (list.mouseClicked(x, y)) {
                lastHero = list.selectedKey;
                preview.show(lastHero);
                duplicateKey = null;
                refresh();
            }
            preview.mouseClicked(x, y, button);
        } else if (tab == TAB_SQUADS) {
            squadName.mouseClicked(x, y, button);
            int row = rowAt(left, 130, x, y);
            if (row >= 0 && squadScroll + row < squads().size()) {
                selectSquad(squadScroll + row);
                refresh();
            }
            row = rowAt(left + 136, 268, x, y);
            Squads.Squad squad = currentSquad();
            if (row >= 0 && squad != null && memberScroll + row < squad.members.size()) {
                selectedMember = memberScroll + row;
                refresh();
            }
        }
    }

    private int rowAt(int x0, int w, int mx, int my) {
        if (mx < x0 || mx >= x0 + w || my < contentTop || my >= contentTop + SQUAD_ROWS * HeroListWidget.ROW_H) {
            return -1;
        }
        return (my - contentTop) / HeroListWidget.ROW_H;
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
        if (wheel == 0) {
            return;
        }
        int dir = wheel > 0 ? -1 : 1;
        if (tab == TAB_SPAWN) {
            list.scroll(wheel);
        } else if (tab == TAB_SQUADS) {
            int mx = Mouse.getEventX() * width / mc.displayWidth;
            Squads.Squad squad = currentSquad();
            if (mx < left + 133) {
                squadScroll = Math.max(0, Math.min(Math.max(0, squads().size() - SQUAD_ROWS), squadScroll + dir));
            } else if (squad != null) {
                memberScroll = Math
                    .max(0, Math.min(Math.max(0, squad.members.size() - SQUAD_ROWS), memberScroll + dir));
            }
        }
    }

    @Override
    public void updateScreen() {
        search.updateCursorCounter();
        squadName.updateCursorCounter();
        preview.tick();
        if (statusTicks > 0) --statusTicks;
        if (removeAllArmed > 0 && --removeAllArmed == 0) {
            refresh();
        }
        if (tab == TAB_SETTINGS) {
            refresh(); // the server's answer may arrive after the menu opened
        }
    }

    // ---- drawing ----

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRendererObj, tr("gui.fiskcompanions.spawner_title"), width / 2, top + 2, 0xFFFFFF);
        if (tab == TAB_SPAWN) {
            drawSpawnTab(mouseX, mouseY, partialTicks);
        } else if (tab == TAB_SQUADS) {
            drawSquadsTab(mouseX, mouseY);
        } else {
            drawSettingsTab();
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSpawnTab(int mouseX, int mouseY, float partialTicks) {
        preview.draw(fontRendererObj, partialTicks);
        search.drawTextBox();
        list.draw(fontRendererObj, mouseX, mouseY);
        int cx = left + 288;
        String c = tr("gui.fiskcompanions.count", count);
        drawCenteredString(fontRendererObj, c, cx + (W - 288) / 2, contentTop + 47, 0xFFFFFF);
        Squads.Squad squad = currentSquad();
        String squadLabel = squad != null ? squad.name : "-";
        drawCenteredString(
            fontRendererObj,
            fontRendererObj.trimStringToWidth(squadLabel, W - 288 - 40),
            cx + (W - 288) / 2,
            contentTop + 119,
            0xFFFFFF);
        int ty = contentTop + 157;
        if (duplicateKey != null) {
            HeroIteration dup = FiskBridge.lookup(duplicateKey);
            String warning = tr(
                "gui.fiskcompanions.duplicate",
                dup != null ? FiskBridge.displayName(dup) : duplicateKey,
                squadLabel);
            for (Object line : fontRendererObj.listFormattedStringToWidth(warning, W - 288)) {
                drawString(fontRendererObj, (String) line, cx, ty, 0xFFAA33);
                ty += 10;
            }
        } else if (statusTicks > 0) {
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(status, W - 288), cx, ty, 0x77DD77);
        } else {
            HeroIteration iter = FiskBridge.lookup(list.selectedKey);
            String name = iter != null ? FiskBridge.displayName(iter) : "-";
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(name, W - 288), cx, ty, 0xFFFF55);
        }
    }

    private void drawSquadsTab(int mouseX, int mouseY) {
        int rowsH = SQUAD_ROWS * HeroListWidget.ROW_H;
        drawRect(left - 1, contentTop - 1, left + 131, contentTop + rowsH + 1, 0xFF000000);
        drawRect(left, contentTop, left + 130, contentTop + rowsH, 0xFF202020);
        List<Squads.Squad> all = squads();
        for (int i = 0; i < SQUAD_ROWS && squadScroll + i < all.size(); ++i) {
            int idx = squadScroll + i;
            int ry = contentTop + i * HeroListWidget.ROW_H;
            if (idx == selectedSquad) drawRect(left, ry, left + 130, ry + HeroListWidget.ROW_H, 0xFF3A5A8A);
            Squads.Squad s = all.get(idx);
            String label = s.name + EnumChatFormatting.GRAY + " (" + s.members.size() + ")";
            drawString(fontRendererObj, fontRendererObj.trimStringToWidth(label, 124), left + 3, ry + 2, 0xFFFFFF);
        }
        int mx = left + 136;
        drawRect(mx - 1, contentTop - 1, mx + 269, contentTop + rowsH + 1, 0xFF000000);
        drawRect(mx, contentTop, mx + 268, contentTop + rowsH, 0xFF202020);
        Squads.Squad squad = currentSquad();
        if (squad != null) {
            for (int i = 0; i < SQUAD_ROWS && memberScroll + i < squad.members.size(); ++i) {
                int idx = memberScroll + i;
                int ry = contentTop + i * HeroListWidget.ROW_H;
                if (idx == selectedMember) drawRect(mx, ry, mx + 268, ry + HeroListWidget.ROW_H, 0xFF3A5A8A);
                Squads.Member m = squad.members.get(idx);
                HeroIteration iter = FiskBridge.lookup(m.hero);
                String name = iter != null ? FiskBridge.displayName(iter)
                    : EnumChatFormatting.DARK_GRAY + m.hero + " " + tr("gui.fiskcompanions.not_installed");
                Alignment a = m.alignment();
                String sideLabel = EnumChatFormatting.GRAY + tr(
                    m.baseSkin()
                        .translationKey())
                    + "  "
                    + a.color
                    + tr(a.translationKey());
                drawString(fontRendererObj, fontRendererObj.trimStringToWidth(name, 170), mx + 3, ry + 2, 0xFFFFFF);
                drawString(
                    fontRendererObj,
                    sideLabel,
                    mx + 265 - fontRendererObj.getStringWidth(sideLabel),
                    ry + 2,
                    0xFFFFFF);
            }
            if (squad.members.isEmpty()) {
                drawCenteredString(
                    fontRendererObj,
                    tr("gui.fiskcompanions.squad_empty"),
                    mx + 134,
                    contentTop + 40,
                    0x999999);
            }
        }
        squadName.drawTextBox();
        drawString(fontRendererObj, tr("gui.fiskcompanions.squad_name_hint"), left, contentTop + 154, 0x777777);
        drawString(
            fontRendererObj,
            tr(bind ? "gui.fiskcompanions.squad_bind_on" : "gui.fiskcompanions.squad_bind_off"),
            mx,
            contentTop + 162,
            0x999999);
    }

    private void drawSettingsTab() {
        int x = left + 52;
        drawString(fontRendererObj, tr("gui.fiskcompanions.fight_back_desc1"), x, contentTop + 30, 0xAAAAAA);
        drawString(fontRendererObj, tr("gui.fiskcompanions.fight_back_desc2"), x, contentTop + 41, 0xAAAAAA);
        drawString(fontRendererObj, tr("gui.fiskcompanions.friendly_fire_desc1"), x, contentTop + 90, 0xAAAAAA);
        drawString(fontRendererObj, tr("gui.fiskcompanions.friendly_fire_desc2"), x, contentTop + 101, 0xAAAAAA);
        drawString(
            fontRendererObj,
            tr(
                PacketSettingsSync.clientCanEdit ? "gui.fiskcompanions.settings_world"
                    : "gui.fiskcompanions.settings_locked"),
            x,
            contentTop + 130,
            PacketSettingsSync.clientCanEdit ? 0x77AA77 : 0xCC7777);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
