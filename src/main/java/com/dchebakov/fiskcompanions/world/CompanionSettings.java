package com.dchebakov.fiskcompanions.world;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Per-world friendly-fire rules, saved with the world. Changed from the spawner's Settings tab (singleplayer host or
 * ops) and synced to every client so the GUI can show them.
 */
public class CompanionSettings extends WorldSavedData {

    private static final String NAME = "fiskcompanions";

    /** When a player hits a character, the character fights back (heroes normally never target players). */
    public boolean fightBackPlayers;
    /** Characters on the same side are hurt by, and retaliate against, each other's (accidental) hits. */
    public boolean allyFriendlyFire;

    public CompanionSettings() {
        this(NAME);
    }

    public CompanionSettings(String name) {
        super(name);
    }

    public static CompanionSettings get(World world) {
        CompanionSettings data = (CompanionSettings) world.mapStorage.loadData(CompanionSettings.class, NAME);
        if (data == null) {
            data = new CompanionSettings();
            world.mapStorage.setData(NAME, data);
        }
        return data;
    }

    public void set(boolean fightBackPlayers, boolean allyFriendlyFire) {
        this.fightBackPlayers = fightBackPlayers;
        this.allyFriendlyFire = allyFriendlyFire;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        fightBackPlayers = nbt.getBoolean("FightBackPlayers");
        allyFriendlyFire = nbt.getBoolean("AllyFriendlyFire");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("FightBackPlayers", fightBackPlayers);
        nbt.setBoolean("AllyFriendlyFire", allyFriendlyFire);
    }
}
