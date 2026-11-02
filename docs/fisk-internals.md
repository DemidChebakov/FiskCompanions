# Fisk's Heroes internals (2.4.0) — Phase 0 findings

Source: CFR decompile of `libs/FiskHeroes-1.7.10-2.4.0.jar` (read-only reference, not committed).

## Verdict
Approach **A (native)** works. Toggle powers are fully native. One-shot powers are ported (≈10–30 lines each)
using the hero's real `ModifierEntry` properties. Flight movement is ours.

## How a non-player entity becomes a "hero"
Fisk already has one: `com.fiskmods.heroes.common.entity.EntityIronMan`. The recipe:
- implement `IHeroTrackedEntity` (`HeroIteration getWornSuit()`): `HeroTracker.iter/get/has(entity)` then
  treats it like a suited player.
- implement `IDataHolder` with `new DataContainer(DataSyncer.PLAYER)`: gives the entity Fisk `Vars`
  (FLYING, AIMING, HEAT_VISION, SHIELD…). `DataEventHandler` syncs any `IDataHolder` to tracking
  players and ticks `DataVar.onUpdate` for non-players. No custom packets needed.
- each tick: `wornSuit = HeroTracker.getHeroIter(SHHelper.getEquipment(this), null)`, then
  `FiskHeroes.proxy.tickSuitEntity(this, wornSuit, START)` before `super.onUpdate()` and `END` after.
  This runs `CommonSuitHandler` (all modifiers' `onUpdate`) and, client-side, `ClientSuitHandler`
  (effects/beams).
- render: `RenderBiped(new ModelBiped(), 0.5f)` with texture `fiskheroes:textures/null.png`. The suit is
  drawn by `ItemHeroArmor`'s armor model, so every pack hero renders.
- equip: `HeroIteration.createArmorStacks()` → `[helmet, chest, legs, boots]` (null for pieces the hero
  lacks) → `setCurrentItemOrArmor(4 - i, stack)`.

## Registry
- `Hero.REGISTRY.getValues()`, `hero.getIterations()`, `iter.getName()` (full key, `domain:hero[/iter]`).
- `HeroIteration.lookup(key)` resolves either a hero key (default iteration) or `hero/iter`.
- `hero.getDomain()` is the pack domain, used for GUI grouping.

## Powers
Player keys are read client-side by `Interaction` classes (registered in `InteractionHandler`):
- **Toggles** (`KeyPressToggleOptional(Modifier, KEY, Var)`): the modifier's `onUpdate` acts when the var is
  true, for any `EntityLivingBase`. Verified in `ModifierHeatVision`. The mob just sets the var server-side:
  SHIELD→`Vars.SHIELD`, BLADE→`BLADE`, INVISIBILITY→`INVISIBLE`, CHARGED_PUNCH→`PUNCHMODE`,
  SLOW_MOTION, WEB_SWINGING, ENERGY_MANIPULATION→`ENERGY_CHARGING`, ENERGY_PROJECTION,
  GRAVITY_MANIPULATION→`GRAVITY_MANIP`, HEAT_VISION, SONIC_WAVES, CRYO_CHARGE→`CRYO_CHARGING`.
- **One-shots** (`Interaction*.receive(EntityPlayer …)`): player-typed, so the server bodies are ported into
  `compat/fisk/ability/*` with `EntityLivingBase`. `InteractionRepulsor.shoot(EntityLivingBase)` is
  already public and is called directly.
- `ModifierEntry.get(PowerProperty)` returns null when a modifier doesn't declare that property, so
  null-check everything.
- Shot direction comes from `getLook()` (yaw head + pitch), so face the target before firing.

## Player-only code (fallback C / own logic)
Modifiers with `instanceof EntityPlayer` guards: Archery, ControlledFlight, EterniumWeakness, Flight,
Gliding, MetalSkin, PropelledFlight, SentryMode, SizeManipulation, SuperSpeed, Teleportation,
Tentacles, SonicWaves (partially), WallCrawling, Transformation. Flight: set `Vars.FLYING` for the
animation and drive motion ourselves, as `EntityIronMan` does.

## Added after testing with SecretHeroes 5.1.6
- `ModifierEntry.get(prop)` throws for undeclared properties — check `entry.modifier.getProperties()` first.
- Suited mobs must register `ArmorAttribute.STEP_HEIGHT` (Fisk's suit tick writes to it).
- Fisk cancels vanilla melee from suited non-players and re-applies it via `SHHelper.attackEntityMelee` with the
  hero's punchDamage. Vanilla `EntityAIAttackOnCollide` needs a path, and there is none to a flier, so we use
  `AIMeleeAttack`.
- `TRANSFORMATION` (toggle + optional timer): the timer advances for any entity once the toggle is set. Meaning
  differs per hero (suit on vs. suit off), so we keep a transformation only if enabled modifiers or punch damage go up.
- `SIZE_MANIPULATION` is player-only, and so are Fisk's render/hitbox scaling (`RenderPlayer`, `PlayerSize`). Ported in
  `SizeController`; `EntityFiskHero.applyScale` and `RenderFiskHero.preRenderCallback` apply `Vars.SCALE`. Without
  it Hulk stays in his TRANSFORMING profile (scale < 1.4) — stuck mid-animation with near-zero speed.
- `CHARGED_BEAM`: setting `Vars.BEAM_CHARGING` is enough; the modifier charges, fires and clears it.
- Flight: aim feet at the target's feet, or two fliers out-climb each other.

## Not yet supported (logged)
TELEKINESIS, TENTACLES, SHAPE_SHIFTING, TELEPORTATION,
SPELLCASTING, EARTHQUAKE, LIGHTNING, CACTUS/SPIKES.

## How the powers work in Fisk Companions
- Toggle powers (heat vision, energy projection, sonic waves, gravity manipulation, blade, charged punch, shield) run
  through Fisk's own modifier code. Held beams fire in bursts.
- One-shot powers (repulsor, energy blast, energy bolt, fireball, cryoball, icicles, ground smash) are server-side
  ports of Fisk's handlers and use the hero's own pack-defined properties.
- Charged beams (e.g. Hulk's Thunderclap) charge and fire at the target, with at least 5 s between beams.
- Flight works for any suit that has a flight modifier. Fisk only applies its flight body poses in `RenderPlayer`, so
  `RenderFiskHero.rotateCorpse` calls the same `ModelHelper` rotations; flying fast counts as sprinting, which gives the
  boost pose. Targeting searches 16 blocks up and down (vanilla: 4), so fliers find each other.
- Suits with a toggle transformation are switched into their suited form. A transformation is kept only if it unlocks
  more powers or stronger punches, so "suit off" toggles (e.g. SecretHeroes speedster suits) are left alone. It is
  judged with the transformation's timer at 1, since many pack suits enable powers from the timer, not the toggle.
  A kept transformation that something switches off (Fisk's `cooldown` modifier: Mk50/Mk85 nanites run out after
  6000 ticks) is switched on again once no cooldown is recharging. Size-changing heroes (Hulk, Ant-Man's giant mode)
  grow to full size.
- The menu preview's stand-in runs `DataVar.onUpdate` (local only; `DataContainer.onUpdate` syncs over the network)
  and gets both the current and previous value of interpolated timers, because renderers read interpolated timers.
- Targets: vanilla `EntityLiving.canAttackClass` rejects creepers and ghasts and every target task checks it, so
  `EntityFiskHero` overrides it to allow any class (sides are decided by the tasks and `isOnSameTeam`).
- Targets: vanilla 1.7.10 `EntityAITarget.isSuitableTarget` ignores `isOnSameTeam`, so revenge targeting would hit
  players regardless of the friendly-fire settings. `EntityFiskHero.setAttackTarget` refuses same-side targets.
- Flight speed scales with the suit's flight modifier: `speed` (default 0.1) when cruising and `boostSpeed` (default 0.2)
  when boosting; when no flight modifier is currently enabled, the suit's declared one is used.
- Super speed: the mob sets `Vars.SPEEDING` and `Vars.SPEED` (up to `SpeedsterHelper.getMaxSpeedSetting`); Fisk's
  `SHAttributes` then raises movement speed for any entity. Over 12 blocks it runs at full level, half level over
  4 blocks, off in melee. Fisk only draws lightning trails for players, so `SpeedTrailRenderer` draws them from Fisk's
  recorded trail points (`TrailHandler`) in the suit's trail colour.
- Gadgets (`fiskheroes:equipment`): `Equipment.onUsed` spawns the projectile for any entity; the mob keeps its own
  per-gadget cooldowns. Rapid webs call `EquipmentRapidWebs.shoot` directly.
- Bow: Fisk's bow only fires from a player's draw; the mob fires `ArrowType.shoot` (any shooter) at full draw with
  a trick arrow from a combat set, aiming a little high for the drop.
- Shield throw (`shield_throwing`): Fisk only returns a thrown shield to a player and drops it as an item otherwise,
  so the mob spawns Fisk's `EntityThrownShield`, steers it back after a short flight, and removes it without a drop.
- Weapons (the suit's primary equipment): the melee weapon is held up close (Fisk's punch system applies weapon
  damage) and guns at range, fired through Fisk's protected `ItemGun.trigger` via reflection with infinite ammo.
- A body is drawn under the suit, chosen per character (`BaseSkin`): the spawning player's skin (default; their
  already-downloaded skin when they're in the world, else Steve), Steve, or none. Heropacks are built for different
  bodies, so this is a player choice rather than a guess.

## Shields (v0.2.0)
- Fisk's `ModifierShield` reads keys only for the client player: `SHIELD_BLOCKING` = use-item key (toggle shields) or
  the SHIELD key (`isToggle: false`, e.g. Mk50). The mob sets `SHIELD` + `SHIELD_BLOCKING` itself (`ShieldController`).
- Blocking happens in Fisk's `CommonEntityHandler.onLivingAttack` for any entity: `ShieldHelper.getShieldInfo` (suit
  shield while `SHIELD_BLOCKING`, else the held item) → `canShieldBlock` (coverage relative to the look direction,
  `SHIELD_COOLDOWN`) → `Shield.hit` (shield health, breaks), knockback on melee/fall/explosion, `onShieldHit`, cancel.
- `canShieldBlock` lets item shields (Captain America's) block only for an `EntityPlayer` that is using the item. The
  mob runs the same steps itself in `FiskBridge.blockWithItem`, passing the item's `ShieldInfo` as a SUIT type. The
  blocking state is synced through datawatcher 22.
- Blocking pose: Fisk's ASM hook on `ModelBiped.setRotationAngles` (`ModelHelper.setRotationAngles`) poses every biped
  model, body and suit layers alike, from the held item's `IAnimatedItem.applyItemAnimations`. Captain America's
  shield does it only for a player using it (right arm X -= 1.1424779, Y = -0.9). While a mob blocks,
  `RenderFiskHero` swaps in the never-obtainable `ItemShieldPose` (same arm angles, any entity) around the body
  model render and each suit-layer render (`PosedModel` wrapper on `renderPassModel`), then restores the shield. The
  shield itself is drawn on the torso in front of the hand (translate (0.1, 0.375, -0.45), 90° about X), because
  Fisk's shield renderer only raises it for players (and reads the local player for other entities).
- Some suits disable their shield while aiming or flying (Mk85), so the mob drops `AIMING` while shielding, and
  only raises a suit shield while the modifier is enabled.

## Nether, End and flight (v0.2.1)
- The Ender Dragon ignores `attackEntityFrom`. `attackEntityFromPart` damages only for a player or explosion source,
  and non-head parts take `dmg/4 + 1`. Fisk projectiles hit `EntityDragonPart`s, which are not living, so a mob's
  hits did nothing. `FiskBridge.damageDragon` calls `attackEntityFromPart` with an explosion-flagged
  `EntityDamageSource` from the mob:
  - melee hits the head when the hero's box (grown by 1/1.5/1) touches it, otherwise the body;
  - shots and beam ticks hit the body, with the modifier's `DAMAGE_PROFILE` damage.
- The dragon is `EntityLiving` with `IBossDisplayData`, not `EntityCreature`, so hostile targeting scans
  `EntityLiving`. `BossRange` raises the target distance to 96 for bosses. `OwnerHits` (an `AttackEntityEvent`
  subscriber) remembers the owner's last hit, with dragon parts mapped to the dragon, because a part is never set as
  `getLastAttacker`.
- Flight pose: Fisk ramps `FLIGHT_TIMER` (10 ticks) from `Vars.FLYING`, and `FLIGHT_BOOST_TIMER` (8 ticks) from
  flying + sprinting. It also resets `FLYING` near the ground, so the mob re-asserts it every tick while airborne.
  Take-off needs 10 ticks of wanting to fly, and boost uses on/off thresholds, so neither timer reverses mid-ramp.
