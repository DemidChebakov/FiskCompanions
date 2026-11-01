# Fisk Companions

Forge 1.7.10 add-on for [Fisk's Superheroes](https://www.curseforge.com/minecraft/mc-mods/fiskheroes).
It adds a suited character mob that can wear **any** hero from Fisk's or any installed heropack and use that hero's powers.

- **Hero**: protects its owner. With an unsuited owner it stays close as a bodyguard. With a suited owner it follows, heals the owner (Regeneration) and joins the owner's fights.
- **Villain**: attacks every player, suited or not, plus villagers, golems and passive animals.
- Heroes and villains fight each other on sight.

## Using it in game
1. Get the **Hero Configurator** from the Tools creative tab.
2. **Sneak + right-click a block** to summon a hero bound to you (random suit).
3. **Right-click the mob** with the configurator. Pick a hero from the searchable list, set Hero or Villain, and choose whether to bind it to you.
4. As the owner, **sneak + right-click with an empty hand** to toggle stay or follow.

## Powers
See [docs/fisk-internals.md](docs/fisk-internals.md).
- Toggle powers (heat vision, energy projection, sonic waves, gravity manipulation, blade, charged punch, shield) run through Fisk's own modifier code.
- One-shot powers (repulsor, energy blast, energy bolt, fireball, cryoball, icicles, ground smash) are server-side ports of Fisk's handlers. They use the hero's own pack-defined properties.
- Flight works for any suit that has a flight modifier.
- Suits with a toggle transformation are switched into their suited form (Iron Man Mk50's nanites, Hulk, Moon Knight).
  A transformation is kept only if it unlocks more powers or stronger punches, so "suit off" toggles (e.g. SecretHeroes
  speedster suits) are left alone. Size-changing heroes (Hulk) grow to full size.
- Charged beams (e.g. Hulk's Thunderclap) charge and fire at the target; held beams fire in bursts.
- A Steve body is drawn under the suit, so partial or see-through suits look like they do on a player.
- Passive powers (healing factor, resistances, immunities, strength via punch damage) apply natively.

## Building
Requirements: JDK 17+ to run Gradle. The build downloads its own Java 8 toolchain.

```sh
# Fisk's Heroes is not redistributed; fetch it once into libs/
curl -L -o libs/FiskHeroes-1.7.10-2.4.0.jar \
  https://cdn.modrinth.com/data/eFDDpt8w/versions/DdwTKPte/FiskHeroes-1.7.10-2.4.0.jar

JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew build      # jar in build/libs/
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew runClient  # dev client with Fisk's Heroes
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew runServer  # dev dedicated server
```

Behaviour self-test (opens a superflat creative world by itself, runs every loaded hero through villain-vs-hero,
villain-vs-animals and guard scenarios, writes `run/client/fiskcompanions-selftest.txt`, then quits):

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew runClient -PfcSelftest
```

It first saves a screenshot gallery of every hero (five per image, `run/client/screenshots/`, index in
`run/client/fiskcompanions-gallery.txt`). Limit it to some heroes with `-PfcOnly=iron_man_mk50,secretheroes:hulk`. It also drives the real configurator GUI
(screenshot `fc-configurator.png`) and checks the change reaches the server. `-PfcTrace=<hero key part>` logs that
mob's AI state every second.

In any world, ops can also run `/fcselftest`.

Heropack zips for dev runs go in `run/client/fiskheroes/` (or open Options → HeroPacks in game).

## Install
Copy the built jar and Fisk's Heroes 2.4.0 into `mods/` of a Forge 1.7.10 (10.13.4.1614) instance, on both client and server.

Built from the GTNH ExampleMod 1.7.10 template (MIT).
