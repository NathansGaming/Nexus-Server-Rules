# NexusSurvival v0.3.0

Four survival systems built together on one shared tick loop: thirst,
radiation zones (with an oxygen bar), armor hygiene, and disease/cure.

## Newest: the Canteen -- a reusable, multi-sip water bottle

A regular Water Bottle is one-and-done: drink it, it's gone. The
**Canteen** is the same idea but reusable -- it holds 6 sips instead of
1, restoring thirst a bit at a time instead of all at once (6 sips *
30 thirst each = a full bar, same total a fresh Water Bottle-and-a-half
would give you, just stretched out).

- **Right-click to drink** like any other water item -- costs one
  charge per sip instead of consuming the whole item. Lore on the item
  shows sips remaining.
- **Refills at any water source** -- right-click a lake, river,
  cauldron, or waterlogged block while holding a Canteen that isn't
  full and it tops back up to 6/6 on the spot, no glass bottle or
  furnace trip required. (If it's already full, right-clicking water
  just drinks a sip as normal.)
- **Runs dry, doesn't disappear** -- at 0 sips it becomes an "Empty
  Canteen" rather than degrading into a plain glass bottle, so it's
  still recognizable as yours and still works with the water-source
  refill above.
- Give it out with `/nexussurvival give canteen`. No crafting recipe
  yet -- same "give-command only for now" state as gas masks, wands,
  and summon items below.

## New this round: a water purity system

Building on last round's fix (any water bottle should actually do
something, not just ones from `/give`) -- this goes a step further
instead of just auto-upgrading a filled bottle into the clean one.

- **Filling a bottle from a lake, river, cauldron, or waterlogged
  block now gives you Raw Water**, not an instantly-clean bottle.
  Detected the same way as before (a tick after the fill, once
  vanilla's own bottle-to-potion swap has happened).
- **Raw Water is drinkable in a pinch** -- restores half the thirst a
  real Water Bottle does, and carries a real 15% chance of catching a
  random disease from the existing disease system. Reuses
  `DiseaseManager.infectRandom()` directly, so this plugs straight into
  the disease/cure system you already have rather than being a
  separate mechanic bolted on.
- **Boil Raw Water in a furnace to purify it** -- a real furnace
  recipe (same cook time as standard vanilla smelting), turning it into
  a proper Water Bottle: full thirst restore, no illness risk. This is
  the first actual crafting/smelting recipe in this plugin -- everything
  before this was give-command items only.

This is genre-standard "boil your water" survival logic, now tied
directly into mechanics you'd already built (disease/cure) instead of
introducing a whole separate purity stat.

## What's built

- **Thirst** -- a 0-20 bar (shown as a boss bar) that decays over time.
  Water Bottles fully restore it; Raw Water (fresh from a lake/river/
  cauldron) restores half and risks illness until purified. Hits 0 ->
  periodic damage + Nausea until you drink again.
- **Radiation zones** -- define cuboid zones with a wand tool. Players
  inside one drain a separate "Rad-O2" bar (also a boss bar, only shown
  while relevant) unless wearing a Hazmat Mask. Hits 0 -> periodic
  suffocation damage.
- **Armor hygiene** -- wearing any armor slowly raises a hidden
  "dirtiness" stat. At 100, each check has a chance to infect you with a
  random disease. Sneak + right-click a water cauldron to wash it back to 0.
- **Disease + cures** -- 4 diseases (Rattling Cough, Fever Rot,
  Glowsickness, Bone Chill), each with its own symptom effects and its
  own dedicated cure potion. Wrong cure = no effect, not an error. Now
  also reachable via drinking contaminated Raw Water, not just dirty armor.

## Commands
- `/nexussurvival give waterbottle` -- clean, safe water bottle
- `/nexussurvival give canteen` -- reusable Canteen, 6 sips before it
  needs a refill from a water source
- `/nexussurvival give rawwater` -- untreated water, for testing the
  purify-in-a-furnace flow without needing to find a lake
- `/nexussurvival give gasmask` -- Hazmat Mask (blocks radiation drain)
- `/nexussurvival give wand` -- radiation zone wand
- `/nexussurvival give cure <DISEASE>` -- e.g. `RATTLING_COUGH`,
  `FEVER_ROT`, `GLOWSICKNESS`, `BONE_CHILL`
- `/nexussurvival radiation wand` -- same as give wand
- `/nexussurvival radiation create <name>` -- after setting both wand
  corners (left-click = corner 1, right-click = corner 2)
- `/nexussurvival radiation remove <name>`
- `/nexussurvival radiation list`
- `/nexussurvival status` -- see your own thirst/rad-O2/dirtiness/infection
- `/nexussurvival resetme` -- panic button for your own stats
- `/nexussurvival removeall` -- wipes all tracked player state server-wide

All subcommands sit behind `nexussurvival.admin` (default: op) for now,
same as the other Nexus plugins during testing -- see rough edges below.

## Setup (same flow as before)
1. Push this folder as its own repo (or folder) -- keep it a separate
   plugin/jar from NexusMechanica and NexusDrones.
2. Open in a Codespace, confirm Java 21 (`java -version` /
   `sdk use java 21.0.11-amzn` if needed).
3. `mvn clean package`
4. Grab `target/NexusSurvival-0.3.0.jar`, drop it in `plugins/`, restart.

## Known rough edges to expect (first pass, un-tested in-game)
- **This has not been through a real compile/run cycle yet** -- same
  disclaimer as every other first drop in this family. Send me the
  `mvn clean package` output and I'll fix whatever it flags, same loop
  as the ghost door / getServer() fixes.
- **The water-fill detection only fires on `RIGHT_CLICK_BLOCK`** --
  filling from a dispenser, or any path that doesn't involve the player
  directly right-clicking a water source with a glass bottle in hand,
  won't be caught by this. Those bottles would still need `/give` (or a
  small follow-up fix once you know exactly what setup you're using).
- **If a freshly-filled bottle merges into an existing stack of
  already-tagged Raw Water**, the tagging check runs on whatever's in
  that hand slot a tick later, which should be fine for the normal
  case -- just flagging that stacking/merging edge cases weren't
  exhaustively tested.
- **The furnace recipe match is exact-item-based** (`RecipeChoice.ExactChoice`
  against Raw Water's specific name/lore/tag) -- this is the right call
  so vanilla's own plain water bottles can't accidentally "purify," but
  it does mean if Raw Water's look ever changes in a future update, old
  Raw Water items already in players' inventories from before that
  change might stop matching the recipe until re-obtained. Not a
  concern for a first release with no players yet, worth remembering
  later.
- **No cooldown or limit on how often Raw Water can make you sick** --
  every single drink rolls the 15% chance independently. Chain-drinking
  Raw Water is a real (intentional) way to gamble with your health, not
  a bug, but worth knowing before you set that percentage.
- **No persistence** -- thirst, rad-O2, dirtiness, infection, and
  radiation zones all reset on server restart. Zones especially will
  want saving to config.yml once the mechanics feel right.
- **All commands are admin-gated** -- there's no real crafting recipe
  for water bottles, canteens, gas masks, or cures yet; everything is
  handed out via `/nexussurvival give` for testing. Wiring up actual
  recipes/loot is a natural next step once you've playtested the balance.
- **Canteen relies on Paper's `PlayerItemConsumeEvent#setReplacement`**
  (not vanilla Bukkit, which has no way to leave anything but a glass
  bottle behind after drinking a potion) -- this is why the plugin
  targets `paper-api` specifically and won't build against plain Spigot.
  Like everything else in this drop, it hasn't been through a real
  compile/run cycle yet -- send me the `mvn clean package` output and
  in-game behavior and I'll fix whatever's off, especially around the
  water-source refill interaction (right-click timing on cauldrons vs.
  open water hasn't been separately verified).
- **Hygiene is a flat timer, not activity-based** -- dirtiness climbs
  just from wearing armor, not from what you're doing (mining, fighting,
  swimming in mud, etc.). Fine for a first pass; easy to make more
  granular later.
- **Washing doesn't consume the cauldron's water level** -- infinite
  reuse for now. One-line fix if you want it to drain a level per wash.
- **No team/ally distinction anywhere** -- not relevant here the way it
  was for drones/turrets, just flagging that these systems don't check
  factions either.
- **Radiation zones can overlap** -- if they do, a player just counts
  as "in radiation," drain doesn't stack. No visual boundary/warning
  when approaching a zone edge yet.
- **One disease at a time** -- you can't catch a second disease while
  already infected with one. Simpler to reason about for v0.1; a
  "compounding infections" version is a bigger design conversation.

Deploy it, break it, send me the console output or in-game behavior
that looks wrong -- same loop as always.
