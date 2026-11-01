package com.dchebakov.fiskcompanions.entity;

/**
 * The body drawn under a character's suit. Heropack suits are made for different bodies, so the player picks per
 * character: their own skin (as a player wearing the suit would look), Steve, or none (see-through gaps).
 */
public enum BaseSkin {

    PLAYER,
    STEVE,
    NONE;

    public static BaseSkin byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : PLAYER;
    }

    /** Lenient parse for saved squads (older files have no skin: Player). */
    public static BaseSkin byName(String name) {
        for (BaseSkin skin : values()) {
            if (skin.name()
                .equals(name)) {
                return skin;
            }
        }
        return PLAYER;
    }

    public BaseSkin next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public BaseSkin previous() {
        return values()[(ordinal() + values().length - 1) % values().length];
    }

    public String translationKey() {
        return "fiskcompanions.skin." + name().toLowerCase();
    }
}
