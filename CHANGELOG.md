# Changelog

All notable changes to Fisk Companions. Versions follow [semantic versioning](https://semver.org); every version is
a beta until 1.0.0. Downloads are on the [releases page](https://github.com/DemidChebakov/FiskCompanions/releases).

## [0.2.1] - 2026-11-03
Fixes for the Nether, the End and flight. Includes everything from 0.2.0.

### Fixed
- **Ender Dragon:** characters now really fight it. Before, it took no damage from them (vanilla only lets players
  and explosions hurt it), and heroes rarely picked it as a target.
  - Melee hits land. A hero close to the head hits the head for full damage; elsewhere it's a body hit.
  - Blasts and beams count as body hits (a quarter of the damage + 1), the same as a player's arrow.
  - Unowned heroes target the dragon and ghasts, and look for bosses up to 96 blocks away.
  - A hero follows its owner's attack even when the owner hits a dragon part.
- **One power over and over:** characters with several ranged powers now rotate them, firing the one used longest
  ago. Before, Hulkbuster fired only its repulsors.
- **Stuck in walls (Nether):**
  - Characters that end up inside blocks move to the nearest free spot after half a second, or to their owner.
  - Teleporting to the owner checks that the character's full (scaled) size fits.
  - Growing characters (Hulk, Hulkbuster, Ant-Man) don't grow when there's no room.
  - Fliers don't take off under a low ceiling. They land and walk when flight keeps hitting walls, or when the owner
    can be reached on foot through a tunnel.
- **Flight animation:**
  - Fliers no longer take off and land again every time the owner jumps.
  - They take off only when they need to: the owner is flying, or is really high up or far away.
  - Their pose no longer flickers between flying and standing.
  - The boost pose has a dead zone, so it doesn't switch on and off.
  - Turning and pitch are smoothed, and hovering is steady about a block above the ground.

### Developers
- New self-tests in the Nether and the End, and a flight probe in all three dimensions. `-PfcDimOnly` runs only those.

## [0.2.0] - 2026-11-02
### Added
- **Characters defend with shields.** They raise their shield when:
  - a projectile flies at them;
  - an opponent swings at them up close;
  - someone has just hit them.

  They block in short bursts and turn to face the threat, so they still fight back. They don't attack while the shield
  is up.
  - Suit shields and protective fields: Iron Man Mk50/Mk85 nanite shields, Doctor Strange's shield, wide energy
    fields, and any heropack suit with a shield power.
  - Captain America's shield, held like a player blocks: right arm across the chest, shield over the body.
  - Fisk's rules apply: only hits from the covered side are stopped, shields take damage and can break, and melee
    attackers are knocked back.

### Fixed
- Heroes now attack creepers and ghasts. A vanilla rule for ordinary mobs had made them ignore them.

## [0.1.1] - 2026-11-02
### Fixed
- A hero no longer attacks a player who hits it while **"fight back players"** is off, including its owner.
- Nanite and transform-to-suit heroes (Iron Man Mk50/Mk85, sind's Iron Spider 17A) suit up when spawned. They also
  suit up again once their nanite cooldown recharges.
- Nanite suits show fully built in the Hero Spawner and Configurator preview.
- A player skin's hat layer (hair, headphones) no longer draws over the suit's helmet.

## [0.1.0] - 2026-11-02
First public version.
- Hero and villain characters that wear any Fisk's Heroes or heropack suit and use its powers.
- The Hero Spawner menu: search, 3D preview, Hero/Villain choice, spawn up to 10 at once, a random hero button and
  Remove all.
- Squads: Avengers, Justice League and Villains come built in, and you can save your own.
- Choose the body under the suit: your skin, Steve or none.
- Friendly-fire settings, saved per world.
- Flight with Fisk's animations at each suit's own speed. Speedsters leave lightning trails.
- Gadgets and weapons: web shooters, batarangs, grenades, Deadpool's guns and katana, Captain America's shield throw,
  Green Arrow's trick arrows.

[0.2.1]: https://github.com/DemidChebakov/FiskCompanions/releases/tag/v0.2.1
[0.2.0]: https://github.com/DemidChebakov/FiskCompanions/releases/tag/v0.2.0
[0.1.1]: https://github.com/DemidChebakov/FiskCompanions/releases/tag/v0.1.1
[0.1.0]: https://github.com/DemidChebakov/FiskCompanions/releases/tag/v0.1.0
