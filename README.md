# Fisk Companions

[![Release](https://img.shields.io/github/v/release/DemidChebakov/FiskCompanions?include_prereleases&label=release)](https://github.com/DemidChebakov/FiskCompanions/releases/latest)
[![Minecraft 1.7.10](https://img.shields.io/badge/Minecraft-1.7.10-62b47a)](https://github.com/DemidChebakov/FiskCompanions#requirements)
[![Build](https://github.com/DemidChebakov/FiskCompanions/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/DemidChebakov/FiskCompanions/actions/workflows/build-and-test.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue)](LICENSE)

A Minecraft **1.7.10** add-on for [Fisk's Superheroes](https://www.curseforge.com/minecraft/mc-mods/fiskheroes).
It adds superhero and supervillain characters that walk, fly and fight on their own. Each one wears **any** suit
from Fisk's Heroes or from any heropack you have installed, and uses that suit's powers.

- **Hero**: your partner. It protects you, follows you and fights alongside you.
- **Villain**: an enemy. It attacks players, villagers, golems and animals.
- Heroes and villains fight each other on sight.

![Characters wearing SecretHeroes suits](docs/images/heroes-secretheroes.png)

> **Beta.** v0.1.1 is the current version (v0.1.0 was the first public one). It has been tested in singleplayer; dedicated-server play is untested.

## Contents
- [Download](#download)
- [Requirements](#requirements)
- [Installation](#installation)
- [Usage](#usage)
- [What the characters do](#what-the-characters-do)
- [Troubleshooting](#troubleshooting)
- [Known limitations](#known-limitations)
- [Versioning and roadmap](#versioning-and-roadmap)
- [For developers](#for-developers)
- [Credits and license](#credits-and-license)

---

## Download
Get `fiskcompanions-v0.1.1.jar` from the **[latest release](https://github.com/DemidChebakov/FiskCompanions/releases/latest)**.

## Requirements
| Thing | Version |
|---|---|
| Minecraft | 1.7.10 |
| Forge | 10.13.4.1614 |
| Fisk's Superheroes | 2.4.0 ([Modrinth](https://modrinth.com/mod/fiskheroes) / [CurseForge](https://www.curseforge.com/minecraft/mc-mods/fiskheroes)) |
| Java | 8 (Prism Launcher sets this up for you) |
| Heropacks | optional, any number (e.g. SecretHeroes) |

## Installation
**Prism Launcher**
1. Select your **1.7.10 Forge** instance and click **Edit**.
2. Open the **Mods** page and click **Add file**. Add `FiskHeroes-1.7.10-2.4.0.jar` (if you don't have it yet) and
   `fiskcompanions-<version>.jar`.
3. *(Optional)* Heropacks are `.zip` files. Don't unzip them. In the same Edit window click **Open folder**
   (the `minecraft` folder) and drop the zip into the **`fiskheroes`** folder there. You can also add them in game
   under **Options → HeroPacks**.
4. Start the game. On the main menu, **Mods** should list **Fisk Companions**.

**Other launchers:** put both jars in `mods/` and heropack zips in `fiskheroes/`.

**Multiplayer:** install the same jars and heropacks on the **server and on every player's game**.

---

## Usage

### The Hero Configurator
The Hero Configurator (a gold star icon) is the only tool you need. Right-click with it to open the Hero Spawner menu,
or right-click a character with it to change that character.
- **Creative mode:** it's in the **Tools** tab.
- **Survival mode:** there's no crafting recipe yet. Use a command (cheats must be on):
  ```
  /give @p fiskcompanions:hero_configurator
  ```

### The Hero Spawner
Hold the Configurator and **right-click** into the air or at the ground. The **Hero Spawner** menu opens with three
tabs: **Spawn**, **Squads** and **Settings**. **Done** closes it.

**Remove all** (top bar) removes your characters from the world. Click it, then click again within 3 seconds to confirm.
In singleplayer or Creative it removes every character; on a server it removes yours and unowned ones.

#### Spawn tab: one character, or several of the same
![Spawn tab](docs/images/spawner-spawn.png)

1. **Search box**: type part of a name, for example `hulk`, `iron` or `flash`.
2. **List**: scroll with the mouse wheel and **click a suit**. The grey word in front is the heropack it comes from.
3. **Preview** (left): the selected suit on a 3D figure, already transformed (Hulk green and big, Iron Man nanites
   on). **Hold the left mouse button on it and drag** to turn it.
4. **< Skin >** (under the preview): the body drawn under the suit. Heropacks are made for different bodies, so you
   choose what looks right for each character:
   - **Player** (default): your own skin, exactly like you'd look wearing the suit.
   - **Steve**: the classic Steve body.
   - **None**: no body, see-through. Best for robots and creatures (e.g. Ultron), whose suits have their own shape.

   The choice is used when you spawn, and it's saved with the character when you add it to a squad.
5. **Hero / Villain**: which side the character is on.
6. **Bind to me**: Heroes you spawn belong to you (they follow and protect you).
7. **- Count +**: how many to spawn at once (1-10).
8. **Random hero**: picks a random suit.
9. **Spawn**: the characters appear in front of you.
10. **< Squad >**: the arrows choose which squad **Add to squad** puts the selected suit (with its side) into.
    If that character is already in the squad, a warning appears: **Add** (add it again), **Remove** (take the
    existing one out) or **x** (do nothing).

#### Squads tab: spawn a whole team with one button
![Squads tab](docs/images/spawner-squads.png)

- Three squads are ready to use, all made of Fisk's Superheroes characters (no heropack needed):
  - **Avengers**: Iron Man, Captain America, Ant-Man, Black Panther, Vision, Falcon, Spider-Man, Doctor Strange
  - **Justice League**: Batman, Shazam, Martian Manhunter, The Flash, Black Canary, Green Arrow, The Atom
  - **Villains**: Reverse-Flash, Zoom, Deathstroke, Captain Cold, Doctor Octopus, Crossbones, Savitar
- Click a squad on the left, then **Spawn squad** spawns everyone in it at once.
- **New** makes an empty squad; **Delete** removes the selected one. Type in the box under the list to **rename** it.
- Click a member on the right, then **Remove member**, **Hero / Villain** to switch its side, or **Skin** to switch
  its body (Player / Steve / None). Each member shows its skin and side.
- To add members, go to the **Spawn** tab, pick a suit and side, and press **Add to squad**.
- Squads are saved on your computer (`config/fiskcompanions-squads.json`), so they work in every world. Members from
  a heropack you don't have installed are shown greyed out and skipped.

#### Settings tab: friendly fire
![Settings tab](docs/images/spawner-settings.png)

| Setting | OFF (default) | ON |
|---|---|---|
| **Characters fight back against players** | You can hit your heroes, but they never turn on you | A character you hit fights you for 30 seconds |
| **Friendly fire between characters** | Stray hits between characters on the same side (a missed web, a blast) do nothing | They hurt each other and fight back |

The settings are saved with the world. In singleplayer you can always change them; on a server only operators can.

### Change a character that's already there
Hold the Configurator and **right-click the character**. A smaller window opens where you pick a new suit, its skin
(Player / Steve / None), switch Hero/Villain and bind it to you, then press **Apply**. Only the owner (or a player in
Creative) can change a character.

![Hero Configurator](docs/images/configurator.png)

### Give orders
| What | How |
|---|---|
| Stay here / follow me | **Sneak + right-click** your Hero with an **empty hand**. A chat message confirms it |
| Change suit or side | Right-click the character with the Configurator |
| Spawn characters or a squad | Right-click anywhere else with the Configurator |
| Get rid of one | Defeat it, or use **Remove all**. Characters never despawn on their own, and their suits are never dropped |

---

## What the characters do

### Heroes and villains
**A Hero (your partner):**
| Situation | What it does |
|---|---|
| You are **not** wearing a suit | Stays close to you (bodyguard), attacks monsters within about 12 blocks of you and anything that hurts you |
| You **are** wearing a full suit | Follows you at a short distance and attacks whatever you attack. When you drop below 60% health it gives you Regeneration (at most once every 20 seconds) |
| You walk far away | Flies to you if its suit can fly, otherwise teleports to you when it's more than 24 blocks away |
| Always | Never attacks players, and fights every Villain it sees |

**A Villain (your enemy):**
- Attacks every player (suited or not), plus villagers, iron/snow golems and animals.
- Fights Heroes first when one is nearby.
- Does nothing on **Peaceful** difficulty. Players in Creative are never attacked.

### Powers
Characters use their suit's real powers: flight, blasts and beams, fireballs, ice, heat vision, shields, blades,
ground smash, Hulk's Thunderclap, and more. Suits that transform first (Hulk, Moon Knight, Iron Man Mk50 nanites)
transform by themselves. Strength, healing factor and resistances work just like on a player.

![Iron Man suits from Fisk's Heroes](docs/images/heroes-iron-man.png)

### Flying
Suits that can fly take off when their target or owner is far away or up in the air, and fly with Fisk's own flight
animations: leaning forward when moving, lying flat and speeding when far away (boost), and hovering when close. They
land again once they're near their target on the ground.

Each suit flies at **its own speed**, taken from the suit's flight settings. The Atom is slow, Iron Man and Martian
Manhunter are fast, and the Anti-Monitor is very fast. Flying far away is faster than flying close (like sprinting).

![Flying heroes](docs/images/flight.png)

### Speedsters
Characters with super speed (The Flash, Reverse-Flash, Zoom, Impulse, Godspeed, any heropack speedster) run at their
suit's top speed level when their target or owner is far away, leaving lightning trails in the suit's colours. They
slow down as they get close and fight at normal speed. Faster suits have more speed levels: in a 28-block race Zoom
arrives in about half a second, while a normal hero needs more than two seconds.

![Speedsters racing](docs/images/speedsters.png)

### Weapons and gadgets
| Character | What they use |
|---|---|
| Spider-Man (every version) | Web shooters: sticky, impact, ricochet and rapid webs |
| Batman | Batarangs, smoke pellets, freeze grenades |
| Deadpool | Desert Eagles at range (dual), katana up close |
| Captain America | Throws his shield at targets (it flies back to him), and uses it as a melee weapon |
| Green Arrow | Compound bow with trick arrows: normal, explosive, triple, boxing glove, fire charge |
| Any heropack suit | Its own gadgets and weapons, if it defines them |

They throw or shoot at targets a few blocks away and switch to melee weapons up close. Characters never run out of ammo.

---

## Troubleshooting
| Problem | Fix |
|---|---|
| "No Fisk's Heroes suits are loaded" | Fisk's Heroes isn't installed, or the game needs a restart |
| A heropack's heroes aren't in the list | The zip must be in the instance's `fiskheroes` folder (not `mods`), not unzipped; restart the game |
| "Only this character's owner can reconfigure it" | Someone else owns it. Ask them, or use Creative |
| My Hero doesn't follow | It's set to stay. Sneak + right-click it with an empty hand |
| A Villain ignores me | You're in Creative, or the difficulty is Peaceful |
| My heroes keep hurting each other | Turn **Friendly fire between characters** OFF on the Settings tab |
| The Settings buttons are grey | On a server only operators can change them |

Found a bug? [Open an issue](https://github.com/DemidChebakov/FiskCompanions/issues) with your heropack list and
the game log.

## Known limitations
- Some powers aren't used yet: telekinesis, tentacles, teleport, spells, earthquake, lightning, web-swinging/web-zip.
- Transformations a heropack triggers **only** with its own scripted key (not Fisk's standard transformation) can't run
  on a mob. The character then stays in its untransformed look.
- Ground smash damages and knocks back but doesn't break blocks.
- A few heropack suits have scripts that only work on players (tested with 15 heropacks / 783 suits: one,
  `sind:tony`). The character keeps its looks and fights, but that suit's own per-tick effects are switched off; the
  game log names the suit once instead of crashing.
- Melee characters facing strong knockback powers (sonic scream, energy beams) may struggle to get close. That's the
  power doing its job.
- Dedicated-server play has not been tested yet.

## Versioning and roadmap
Versions follow [semantic versioning](https://semver.org) and come from git tags (`vMAJOR.MINOR.PATCH`); the build
reads the tag, so the jar name and the in-game version match it.
- **v0.1.0** is the first version, a beta. Earlier `v0.0.x` tags are development history.
- **v0.1.1** (hotfix): heroes no longer fight back against a player who hits them while "fight back" is off; nanite
  and transform-to-suit heroes (Iron Man Mk50/Mk85, pack suits like sind's Iron Spider 17A) suit up when spawned and
  in the menu preview, and suit up again after their nanite cooldown runs out.
- New features bump the minor number (`0.2.0`), fixes bump the patch (`0.1.1`).
- **1.0.0** is planned once the mod has been tested on a dedicated server and the Hero Configurator has a crafting
  recipe.

---

## For developers

### Building from source
Requirements: JDK 17+ to run Gradle (point `JAVA_HOME` at it). The build downloads its own Java 8 toolchain.

```sh
# Fisk's Heroes is not redistributed; fetch it once into libs/
curl -L -o libs/FiskHeroes-1.7.10-2.4.0.jar \
  https://cdn.modrinth.com/data/eFDDpt8w/versions/DdwTKPte/FiskHeroes-1.7.10-2.4.0.jar

./gradlew spotlessApply build   # jar in build/libs/
./gradlew runClient             # dev client with Fisk's Heroes
./gradlew runServer             # dev dedicated server
```

Heropack zips for dev runs go in `run/client/fiskheroes/`.

### Self-test
```sh
./gradlew runClient -PfcSelftest
```
Takes about 3-4 minutes. It opens a superflat creative world by itself, checks the configurator GUI, flight, the
speedster race, bow and shield throwing (and that no item drops), the spawner menu (squad spawn, duplicate warning,
Remove all), the four friendly-fire rules, and then runs every loaded hero through villain-vs-hero,
villain-vs-animals and guard scenarios (`run/client/fiskcompanions-selftest.txt`), then quits. Keep only a couple of
heropacks in `run/client/fiskheroes/` to stay within that time.

Options:
- `-PfcGallery` also saves a screenshot gallery of every hero, five per image (`run/client/screenshots/`, index in
  `run/client/fiskcompanions-gallery.txt`). Slow with many heropacks.
- `-PfcOnly=iron_man_mk50,secretheroes:hulk` limits the run to heroes whose key contains one of those parts.
- `-PfcTrace=<hero key part>` logs that mob's AI state every second.

In any world, ops can also run `/fcselftest`.

### How it works
[docs/fisk-internals.md](docs/fisk-internals.md) covers how a non-player entity becomes a Fisk hero and how each
power, gadget and weapon is driven on a mob.

### Releasing
Push a `vX.Y.Z` tag. The **Release tagged build** workflow builds the jar and attaches it to the GitHub release.

---

## Credits and license
- [MIT License](LICENSE).
- Built from the GTNH ExampleMod 1.7.10 template (MIT).
- Fisk's Superheroes is by FiskFille. This add-on is not affiliated with or endorsed by Fisk's Superheroes, and it
  does not bundle Fisk's Heroes or its assets; you need to install Fisk's Heroes separately.
