package com.dchebakov.fiskcompanions.client;

import java.io.File;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;

import com.dchebakov.fiskcompanions.FiskCompanions;
import com.dchebakov.fiskcompanions.entity.Alignment;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Saved squads ("profiles") for the spawner: named lists of characters, spawned with one button. Stored on the
 * player's computer in {@code config/fiskcompanions-squads.json}; presets are written the first time.
 */
@SideOnly(Side.CLIENT)
public final class Squads {

    public static final class Member {

        public String hero;
        public String side = Alignment.HERO.name();

        public Member() {}

        public Member(String hero, Alignment side) {
            this.hero = hero;
            this.side = side.name();
        }

        public Alignment alignment() {
            return Alignment.VILLAIN.name()
                .equals(side) ? Alignment.VILLAIN : Alignment.HERO;
        }
    }

    public static final class Squad {

        public String name;
        public List<Member> members = new ArrayList<>();

        public Squad() {}

        Squad(String name, Alignment side, String... heroes) {
            this.name = name;
            for (String hero : heroes) {
                members.add(new Member(hero, side));
            }
        }
    }

    private static final class Data {

        /** Bumped when the presets change, so older files get the same fixes once. */
        int presetsVersion;
        List<Squad> squads = new ArrayList<>();
    }

    /** 2: presets use only Fisk's Heroes characters (no heropack heroes). */
    private static final int PRESETS_VERSION = 2;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting()
        .create();
    private static Data data;

    private Squads() {}

    private static File file() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/fiskcompanions-squads.json");
    }

    public static List<Squad> all() {
        if (data == null) {
            load();
        }
        return data.squads;
    }

    private static void load() {
        File f = file();
        if (f.isFile()) {
            try (Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8)) {
                data = GSON.fromJson(r, Data.class);
            } catch (Exception e) {
                FiskCompanions.LOG.warn("Could not read {}; starting with the preset squads", f, e);
            }
        }
        if (data != null && data.squads != null && data.presetsVersion < PRESETS_VERSION) {
            migrate();
        }
        if (data == null || data.squads == null) {
            data = new Data();
            data.presetsVersion = PRESETS_VERSION;
            data.squads.add(
                new Squad(
                    "Avengers",
                    Alignment.HERO,
                    "fiskheroes:iron_man",
                    "fiskheroes:captain_america",
                    "fiskheroes:ant_man",
                    "fiskheroes:black_panther",
                    "fiskheroes:vision",
                    "fiskheroes:falcon",
                    "fiskheroes:spider_man",
                    "fiskheroes:doctor_strange"));
            data.squads.add(
                new Squad(
                    "Justice League",
                    Alignment.HERO,
                    "fiskheroes:batman_dceu",
                    "fiskheroes:shazam_dceu",
                    "fiskheroes:martian_manhunter",
                    "fiskheroes:the_flash",
                    "fiskheroes:black_canary",
                    "fiskheroes:green_arrow",
                    "fiskheroes:atom"));
            data.squads.add(
                new Squad(
                    "Villains",
                    Alignment.VILLAIN,
                    "fiskheroes:reverse_flash",
                    "fiskheroes:zoom",
                    "fiskheroes:deathstroke_dceu",
                    "fiskheroes:captain_cold",
                    "fiskheroes:doctor_octopus",
                    "fiskheroes:crossbones",
                    "fiskheroes:savitar"));
            save();
        }
    }

    /** Version 1 presets used two heropack heroes; swap just those for Fisk's own, keep the player's edits. */
    private static void migrate() {
        for (Squad squad : data.squads) {
            for (Member m : squad.members) {
                if ("Avengers".equals(squad.name) && "secretheroes:hulk".equals(m.hero)) {
                    m.hero = "fiskheroes:ant_man";
                } else if ("Villains".equals(squad.name) && "secretheroes:homelander".equals(m.hero)) {
                    m.hero = "fiskheroes:savitar";
                }
            }
        }
        data.presetsVersion = PRESETS_VERSION;
        save();
    }

    public static void save() {
        File f = file();
        try {
            f.getParentFile()
                .mkdirs();
            try (Writer w = Files.newBufferedWriter(f.toPath(), StandardCharsets.UTF_8)) {
                GSON.toJson(data, w);
            }
        } catch (Exception e) {
            FiskCompanions.LOG.warn("Could not save {}", f, e);
        }
    }
}
