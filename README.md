# GlowFusion

A Paper plugin (Minecraft **26.2** / Java Edition) with six features:

1. **Dual-color slab fusion** - right-click the top of a placed (bottom-half)
   slab while holding a *different* slab material to fuse the two together
   into one block space, instead of vanilla's "different slabs can't combine"
   rule getting in the way.
2. **Glowing buttons** - any button emits real block light while it's
   pressed/powered, and the light disappears the instant it stops being
   powered.
3. **Lever pop-outs** - bind a lever to two saved builds of the same space
   (a stowed look and a deployed look) so flipping the lever instantly swaps
   between them - RV slide-outs, drop-down ramps/tailgates, awnings, and
   anything else that should just "appear" and "disappear" without wiring up
   redstone contraptions or pistons.
4. **Vertical slabs** - crouch + right-click a placed slab with an empty
   hand to stand it up against a wall (decorative trim, paneling, accents),
   cycling through the four compass facings and back to lying flat.
5. **Mini-block mosaics** - opt in with `/glowfusion mini`, then right-click
   a spot on the face of any plain, non-interactive block while holding a
   colored/archetype block to paint a small, full-size-cube tile there in
   its real color, on a 4x4 grid per face - stripes, pixel art, and signage
   made of tiny blocks, all on one real block's face. Off by default so it
   never gets in the way of normal building.
6. **Bigger item stacks** - raises every normally-stackable item's max
   stack size from vanilla's usual 64 (or less - snowballs/eggs/ender
   pearls default to 16) up to 99, vanilla Minecraft's own hard ceiling for
   this. Applies server-wide automatically, no toggle or command needed.

## How each feature actually works (and its limits)

Paper plugins can't add new textures, models, or blockstates to the game -
that requires a resource pack (server-side "mods" like Forge/Fabric can, but
a Paper *plugin* only manipulates vanilla blocks/entities the client already
knows how to render). Both features below are built entirely out of vanilla
building blocks so no resource pack is needed and it works for any vanilla
client that can join the server.

### Dual-color slabs

Vanilla only lets two slabs of the *same* material merge into a real
"double slab" blockstate. To show two *different* materials occupying one
block space, GlowFusion keeps the real block as the bottom slab (so
collision, mining, and drops all behave normally) and renders the second
slab's top half using a `BlockDisplay` entity - a vanilla display entity
that can render any existing block state. It's positioned to sit exactly in
the top half of that block, with no hitbox of its own, so it reads as one
fused block.

- Right-click the top face of a bottom slab with a *different* slab type in
  hand to fuse them.
- Breaking the base block removes the fused display and drops both slab
  items.
- The display entity is invulnerable and ignores direct damage so players
  can't "pop" the illusion by attacking it - you have to mine the real
  (bottom) block.
- **Known limitation:** pistons pushing/pulling the base block will move the
  real slab but won't drag the display entity along in the same tick chain;
  avoid piston contraptions on fused slabs for now.

### Glowing buttons

Vanilla buttons never emit light, and a plugin can't retroactively change a
vanilla block's built-in luminance. Instead, the instant a button becomes
powered, GlowFusion places an invisible `minecraft:light` block (a vanilla
block meant for mapmakers - adjustable 0-15 light level, no texture, no
hitbox) in the empty air space directly in front of the button, and removes
it the moment the button stops being powered. The mapping of
button-location to light-location is written to `plugins/GlowFusion/lights.yml`
so it survives restarts, and a periodic background check (every 5 seconds by
default) cleans up any light left behind if a button/its wall was removed by
means that don't fire a normal break event (e.g. world-edit tools).

- Configurable light level (`glowing-buttons.light-level`, default 15).
- Only triggers if the space in front of the button is empty air, so it
  never overwrites another block.

### Lever pop-outs

This is the tool for RV slide-outs, awnings, drop-down ramps, tailgates -
anything that should just appear/disappear as a fixed result, with no
animation and no redstone contraption behind it. It works by capturing a
*complete* block-by-block snapshot of a region twice - once for how it looks
stowed, once for how it looks deployed - and swapping the entire region to
the matching snapshot the instant a bound lever's redstone state changes.
Because every block in the region is recorded in both snapshots (including
air), the swap is always exact: nothing is left behind, and nothing is
guessed at.

**Workflow** (all via the `/popout` command, needs the `glowfusion.popout.admin`
permission, default op):

1. Build the awning/ramp/pop-out in its **stowed** (off) look.
2. Look at one corner of that space and run `/popout pos1`. Look at the
   opposite corner and run `/popout pos2`. (Think of it like a WorldEdit
   selection - it can be a thin 1-block-tall slice or a full 3D box.)
3. Run `/popout saveoff <name>` to capture that stowed look.
4. Now rebuild the *same* space to look **deployed** (the awning extended,
   the ramp lowered, the slide-out popped) - same corners, same region.
5. Run `/popout saveon <name>` to capture that deployed look.
6. Place a lever next to it, look at it, and run `/popout bind <name>`.
7. Flip the lever: **on = deployed**, **off = stowed**. That's it - no
   animation, but the result appears/disappears instantly, and it's whatever
   you built, so it can be a slab awning, a ramp made of stairs and slabs
   sloping to the ground, a slide-out room, anything.

Other commands: `/popout list` (see what's saved and whether both states are
captured), `/popout unbind` (look at a lever to detach it), `/popout remove
<name>` (delete a saved design and unbind any levers using it), `/popout
info` (see your current corner selections).

**Notes and limits:**

- The off/on captures for the same name must use the *exact* same two
  corners - GlowFusion checks this and will tell you if they don't match.
- A region is capped at `popouts.max-blocks` in `config.yml` (default
  20,000) so an accidental huge selection can't hang the server for a tick.
- Levers were chosen deliberately over buttons for this: a lever stays
  flipped until you flip it back, matching "press it once to deploy, press
  it again to stow" - a vanilla button always auto-pops back on its own
  after about a second, which doesn't fit a sustained "deployed" state.
- Each pop-out is captured fresh per lever - there's no shared/reusable
  template system (yet). If you build several RVs with the same awning,
  you'll capture and bind it separately on each one.
- Breaking the bound lever automatically unbinds it (the saved design itself
  isn't deleted, so you can rebind a new lever to it later with
  `/popout bind <name>`).
- Blocks are swapped with physics updates suppressed, so things like sand,
  gravel, or attached blocks won't cascade/react to the sudden change - the
  swap is a clean, instant overwrite.

### Vertical slabs

Vanilla has no "vertical slab" blockstate at all - mods that add one ship a
whole new block model + resource pack, which a Paper plugin can't do. This
feature is **decorative only, by design**: it makes a slab visually stand up
against a wall (trim, paneling, accent strips), but it does not - and can't,
without a resource pack - give that standing panel real half-depth
collision. Players and mobs walk straight through it, same as the fused
slab tops in feature #1.

**How to use it:** crouch (sneak) and right-click a placed slab with an
*empty* hand. It stands up facing north. Crouch-right-click it again to
rotate it to face east, then south, then west, then back to lying flat -
cycling through all five states. Attacking it (left-click, like breaking a
block) removes it entirely and drops the slab item, the same as mining a
normal slab.

Under the hood: while standing, the real block becomes air (so there's
nothing solid left to collide with) and a rotated `BlockDisplay` renders
the slab's own texture as a thin panel. Since right-clicking air doesn't
fire the same event a right-click on a real block does, a paired invisible
`Interaction` entity is what actually catches your follow-up clicks -
this is the same standard technique custom-furniture plugins use. The
original slab's material is remembered (`plugins/GlowFusion/vertical-slabs.yml`)
so flipping back to flat always restores the exact block you started with.

**Notes and limits:**

- This is deliberately decorative-only - see the "Do vertical slabs need to
  actually block movement" trade-off above. If you need a *real* solid
  vertical wall segment, that's a resource-pack or full-mod job, not
  something a Paper plugin can do.
- Won't trigger on a slab that's already fused (feature #1) - break the
  fusion first if you want to stand up a plain slab there instead.
- Something else can't be built into the hollowed-out air space while a
  slab is standing there - GlowFusion blocks that placement attempt.
- The rotation math is geometrically derived (not guessed), but I couldn't
  visually test all four facings in the environment this was built in - if
  any orientation looks mirrored or off, tell me which one and I'll adjust
  the numbers.

### Mini-block mosaics

The same "vanilla can't do this without a resource pack" gap as the other
features, applied to fine detail: there's no way to give a single block
space a genuinely subdivided texture. This feature fakes it with a small
grid of `BlockDisplay` entities layered just outside one face of an
existing block, instead of replacing the block itself - which is the one
real difference from vertical slabs above: nothing about the real block
ever changes here, so it keeps its own full collision the whole time, and a
mosaic'd wall is still just as solid as it always was.

**How to turn it on:** run `/glowfusion mini`. This is a per-player, opt-in
toggle - it's off for everyone by default, specifically so that holding
wool, stone, planks, and the like and right-clicking a wall still places
the block normally unless you've explicitly switched your own paint mode
on. Run `/glowfusion mini` again any time to switch it back off.

**How to use it (once paint mode is on):** hold an eligible material (see
`mini-blocks.plain-block-materials` in `config.yml`, plus every color of
wool/concrete/concrete powder/terracotta/glazed terracotta/stained glass
automatically) and right-click the exact spot on a face where you want
that color - Paper reports the precise point you clicked, which is used
to work out which of the 16 cells in that face's 4x4 grid you meant. Each
tile is a true little cube sized to exactly 1/4 of the real block in
every dimension (not a thin decal), rendered at full brightness so its
real color always shows, even at night or indoors. Right-clicking an
already-painted cell replaces it with whatever you're currently holding.
Sneak + right-click an already-painted cell with an *empty* hand erases
just that one tile and drops the item. Breaking the real block clears
every tile on every face of it at once, same as mining anything else.

**Notes and limits:**

- Off by default, per player, for the whole session - toggle with
  `/glowfusion mini`. Nothing here ever changes vanilla block-placement
  behavior for a player who hasn't opted in.
- Only works on blocks `Material#isInteractable()` reports as *not*
  interactable, so it never hijacks a chest/furnace/door/crafting table/
  etc.'s own right-click. That check is documented by the Spigot/Paper
  community as occasionally over-inclusive (a few harmless block types
  like stairs or fences also get excluded) - an acceptable trade for never
  breaking a real GUI block's normal use.
- The palette is intentionally an explicit list, not an automatic "is this
  a plain block" detector - Bukkit has no single reliable test that lands
  on exactly "the archetypes and their colors, not the ones with
  functions." The 16-color families are generated in code and can't miss a
  color; the rest is `mini-blocks.plain-block-materials` in config.yml,
  which starts with a broad but not exhaustive default list - add more
  material names there freely.
- No YAML store and no crash-desync risk the way vertical slabs has -
  since the real block is never modified, there's nothing about it that
  can fall out of sync. The tiles themselves are ordinary entities
  Minecraft already saves/restores with the chunk on its own.
- Decorative only, same as every other display-entity feature here -
  there's no fine collision matching the pattern.

### Bigger item stacks

Unlike everything above, this one isn't a visual trick built out of
display entities - it uses a real, built-in Minecraft mechanism. Since
1.20.5, an item's max stack size is one of the game's own "data
components" (the same system behind a custom name or an enchantment), and
`ItemMeta` exposes it directly: `setMaxStackSize(Integer)`. GlowFusion just
sets that component to `bigger-stacks.max-stack-size` (99 by default) on
every eligible item.

**The 99 ceiling is real, not a GlowFusion choice.** Both the vanilla
client and server hard-clamp the `max_stack_size` component to the range
1-99 - there is no way, from any plugin, to push a normal item's stack any
higher. Tools, weapons, and armor are untouched: vanilla already reports a
max stack size of 1 for anything with durability (it refuses to combine a
stack above 1 with a `max_damage` component), and this feature respects
that rather than fighting it.

**Why it needs so many hooks instead of one setting:** stack size lives on
the individual item stack, not on the material as a whole, so every place
a fresh item can enter the game needs to get the same stamp, or two
stacks of the same material holding different (or no) stamp won't merge
together in an inventory - that would look like a bug even though nothing
is actually broken. GlowFusion stamps items as they're crafted (before
the result is even taken), spawned as a dropped item (block breaks, mob
drops, dispensers), picked up, moved by a hopper, and generated as chest
or fishing loot, normalizes a player's whole inventory and ender chest the
moment they join, and normalizes any container's contents the moment a
player stops looking at it. On top of all of that, a periodic sweep (every
`bigger-stacks.resync-interval-seconds`, 10 by default) re-checks every
online player's inventory and ender chest as a catch-all for anything that
arrived some other way - another plugin's shop, an admin's `/give`, and
the like.

**Notes and limits:**

- Applies server-wide the instant the plugin loads - there's no per-player
  toggle or permission for the stack size itself, since two players
  holding the same material at different stack sizes would just be a way
  to accidentally break stacking, not a real feature.
- Purely a number on existing vanilla items - no textures, models, or
  resource pack involved, so there's nothing here for a resource pack to
  conflict with.

## Troubleshooting

**Bedrock players can't see fused/vertical slabs or mini-block mosaics.**
All three features render their custom look with a `BlockDisplay` entity
(see above) - a Java Edition mechanism. If your server accepts Bedrock
connections through Geyser, Geyser currently does not render Display
entities at all (a long-standing, still-open upstream issue - no ETA from
the Geyser team as of this writing). Interaction entities (the invisible
hitboxes vertical slabs use to catch clicks) do at least spawn for Bedrock
players, though they have their own minor known quirks. There's nothing to
fix in GlowFusion itself here; if you want Bedrock players to see this
content, look into a third-party Geyser extension that adds Display-entity
rendering (e.g. GeyserDisplayEntity or GeyserExtras), installed on the
Geyser/proxy side, not as part of this plugin. Otherwise, treat dual-slab
fusion, vertical slabs, and mini-block mosaics as Java-only for now.

**Bigger item stacks are a different situation from the above, and should
work for Bedrock players too.** This feature doesn't touch Display
entities or anything else Java-only - it's just a number (the
`max_stack_size` item component) attached to ordinary inventory data, and
Bedrock's own inventory system already handles arbitrary stack counts the
server tells it about; there's no Bedrock-client rendering capability
being asked for here the way there is with the other three features. I
haven't been able to verify this hands-on against a real Xbox/Bedrock
client in this environment, so treat it as likely-fine rather than
guaranteed - if a Bedrock player ever sees stacks silently cap back at 64,
that would be a genuine Geyser-side gap worth reporting upstream, not
something to look for in GlowFusion's own code first.

**A slab is "glitched" - can't be broken, and placing anything there says the
space is already occupied.** This is a desync between GlowFusion's own
bookkeeping and the entities/blocks actually in the world, and it's a real
risk with this design: GlowFusion's YAML stores (`vertical-slabs.yml`, etc.)
save immediately every time something changes, but the entities Minecraft
uses to render the illusion only get written to disk on the world's own
autosave schedule (or a clean shutdown). If the server crashes, gets force-
killed, or restarts before the next autosave, you can end up with GlowFusion
still remembering a spot as "occupied" while the entity that was supposed to
be there is gone. Two things handle this:

- Automatically: the next time a player tries to stand up a slab there, or
  place a block there, and GlowFusion notices there's no matching entity to
  back up its own record, it clears the stale entry itself and lets the
  action proceed.
- On demand: run `/glowfusion unstick` while looking directly at the glitched
  block (within 8 blocks). It force-clears any GlowFusion entity and store
  entry at that exact spot and restores the original block if one was on
  record, so you don't have to wait for the automatic path to kick in.

If you hit a stuck block that `/glowfusion unstick` doesn't resolve, that's a
new pattern worth reporting back with what you were doing right before it
happened (a nearby explosion, a piston, another plugin, a server crash, etc.).

## Building

**Requires JDK 25+ to compile** (a JDK, not just a JRE) - and this is a hard
requirement, not just a recommendation. Paper 26.2's own `paper-api` jar is
itself compiled to Java 25 bytecode, and `javac` cannot even *read* a
dependency jar compiled for a newer Java version than the JDK running it -
that's true no matter what `--release`/`maven.compiler.release` you set in
`pom.xml`, since that setting only controls the bytecode *you* emit, not
what your compiler is able to parse on its classpath. If you build this
with an older JDK (e.g. JDK 21, which is what many default dev
containers/Codespaces ship with), you'll get errors like:

```
cannot access org.bukkit.plugin.java.JavaPlugin
bad class file: .../paper-api-26.2.build.112-stable.jar(/org/bukkit/plugin/java/JavaPlugin.class)
  class file has wrong version 69.0, should be 65.0
```

(Class file version 69 = Java 25, 65 = Java 21 - so that error is really
just "your JDK is too old to read this dependency.") The fix is to install
and switch to a JDK 25+ before building, not to change anything in the
project.

**If you're on a Debian/Ubuntu-based dev container or Codespace**, the
easiest path is usually [SDKMAN](https://sdkman.io/):

```
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25-tem
sdk use java 25-tem
mvn -version   # confirm it now reports Java 25
```

Or, if your distro's package manager already has it:

```
sudo apt update && sudo apt install -y openjdk-25-jdk
sudo update-alternatives --config java   # pick the JDK 25 entry
sudo update-alternatives --config javac  # pick the JDK 25 entry
```

Once `java -version` / `javac -version` report 25, build normally:

```
mvn package
```

The finished jar is written to `target/GlowFusion-1.0.0.jar`. Drop it in
your Paper server's `plugins/` folder and restart.

If a newer Paper 26.2 build has been published, bump the `<paper.version>`
property in `pom.xml` - check
https://repo.papermc.io/service/rest/repository/browse/maven-public/io/papermc/paper/paper-api/
for the latest `26.2.build.NNN-stable` string.

## Configuration

See `src/main/resources/config.yml` (copied to
`plugins/GlowFusion/config.yml` on first run). All six features can be
toggled independently, and permission-gating can be turned off if you want
every player to have access regardless of permissions plugin setup.

- `glowfusion.fuse` - lets a player fuse slabs (default: granted to everyone).
- `glowfusion.glow` - reserved for future per-player gating of the glow
  effect (currently the effect just follows redstone state for everyone).
- `glowfusion.admin` - lets a player run `/glowfusion reload` and
  `/glowfusion unstick` (default: op).
- `glowfusion.popout.admin` - lets a player use `/popout` to select, capture,
  bind, and remove pop-outs (default: op - this is a building/setup
  permission, not something you'd hand out to every player, since anyone who
  can bind a lever could redecorate any lever on the server).
- `glowfusion.vertical` - lets a player stand slabs up vertically (default:
  granted to everyone).
- `glowfusion.miniblocks` - lets a player toggle mini-block paint mode with
  `/glowfusion mini` and paint/erase mosaic tiles once it's on (default:
  granted to everyone; the mode itself still defaults to OFF per player
  until they run the command).

## Project layout

```
src/main/java/com/realsociety/glowfusion/
  GlowFusionPlugin.java        - plugin entry point, config, /glowfusion command
  slabs/DualSlabListener.java  - the dual-color slab fusion feature
  buttons/GlowingButtonListener.java - the glowing button feature
  buttons/LightStore.java      - persists button -> light-block mapping
  popouts/Region.java          - a normalized 3D block region
  popouts/PopoutDefinition.java - a named region + its off/on block snapshots
  popouts/PopoutStore.java     - persists popout definitions + lever bindings
  popouts/PopoutCommand.java   - the /popout command (select, capture, bind)
  popouts/PopoutListener.java  - swaps a region's blocks when a bound lever flips
  vertical/VerticalOrientation.java - the FLAT/N/E/S/W state enum
  vertical/VerticalSlabStore.java   - persists original block + current facing
  vertical/VerticalSlabListener.java - the crouch-right-click stand-up feature
  miniblocks/MiniBlockPalette.java  - which materials are eligible to paint with
  miniblocks/MiniBlockListener.java - the mini-block mosaic feature
  stacks/StackSizeManager.java  - stamps the configured max stack size onto an item
  stacks/StackSizeListener.java - every event a fresh item stack can enter play through
src/main/resources/
  plugin.yml                   - plugin metadata, permissions, commands
  config.yml                   - default settings
```
