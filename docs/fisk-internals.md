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
