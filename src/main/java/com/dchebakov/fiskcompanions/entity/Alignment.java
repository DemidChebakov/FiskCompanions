package com.dchebakov.fiskcompanions.entity;

import net.minecraft.util.EnumChatFormatting;

public enum Alignment {

    HERO(EnumChatFormatting.AQUA),
    VILLAIN(EnumChatFormatting.RED);

    public final EnumChatFormatting color;

    Alignment(EnumChatFormatting color) {
        this.color = color;
    }

    public static Alignment byId(int id) {
        return id == VILLAIN.ordinal() ? VILLAIN : HERO;
    }

    public Alignment opposite() {
        return this == HERO ? VILLAIN : HERO;
    }

    public String translationKey() {
        return "fiskcompanions.alignment." + name().toLowerCase();
    }
}
