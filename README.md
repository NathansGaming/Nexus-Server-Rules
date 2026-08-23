# NexusChroma v0.1.0

A from-scratch Paper 1.21 plugin: place a block into an item frame and,
instead of the frame showing that block's actual item icon, the whole
frame turns into a flat square of that block's color -- a stone block
becomes a flat gray square, red wool becomes a flat red square, and so
on. Built for pixel-art walls, color-coded signage, map-style displays,
anything where you want a clean solid tile rather than a floating
3D-ish item icon.

## The trick that makes this possible

Paper has no API for changing how an item renders inside a frame.
What it does have: item frames render a **filled map** completely flat
and edge-to-edge, unlike a block item, which renders as a small
floating isometric cube. So when you place a supported block into a
frame, NexusChroma:

1. Cancels the normal placement.
2. Takes one of that block from your hand.
3. Generates a filled map whose only content is one solid color,
   matching that block, and puts *that* into the frame instead.
4. Tags the map (via a `PersistentDataContainer` key, not anything
   visible) with which real block it stands in for.

Taking the square back out reads that tag and hands you back the real
block -- not a map. The whole illusion is self-contained; nothing about
this needs a resource pack or any client-side install.

## Building & installing

Same flow as NexusMechanica:

```
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
mvn clean package
```

Output: `target/NexusChroma-0.1.0.jar` -> upload to `plugins/`, restart
(or `/reload`) the server.

## Using it

No special items needed -- just place a supported block into an empty
item frame like you normally would (right-click the frame while
holding the block). If it's in the color table, it's replaced with the
flat-color square automatically. If it's not in the table, nothing
changes -- the block goes into the frame as a normal item, exactly like
vanilla.

Taking it back out (right-click an occupied frame with an empty hand,
or just breaking the frame) hands you the original block back, not a
map.

## The color table (`colors.yml`)

The first time NexusChroma starts, it writes
`plugins/NexusChroma/colors.yml` with a default table of about 120
blocks:

- ~70 hand-picked stone, dirt, wood, and ore/mineral blocks, eyeballed
  against their real texture as closely as I could without a running
  client to sample pixels from.
- All 16 `*_WOOL` colors, using Mojang's actual published dye hex
  values -- the entries I'm most confident are accurate.
- All 16 `*_CONCRETE` and all 16 `*_TERRACOTTA` colors, which I
  **didn't** hand-type -- I don't trust my memory of those exact hexes
  enough to present them as authoritative. Instead they're derived
  programmatically from the wool value of the same color name
  (`ColorMath.concreteFromDye` / `terracottaFromDye`: concrete pushes
  more saturated and a touch darker, terracotta pushes desaturated,
  warmer, and a touch darker). They'll be in the right neighborhood,
  not pixel-verified.

Every entry lives in that one file after first launch -- editing
`DefaultColors.java` and rebuilding has **no effect** on a server that's
already generated its `colors.yml`. Retune or extend it live instead:

```
/nexuschroma add oak_log 6E5636
/nexuschroma remove oak_log
/nexuschroma list
/nexuschroma list 2
/nexuschroma reload
/nexuschroma info
```

`add` and `remove` write straight back to `colors.yml`, so they survive
a restart with no extra step. `reload` re-reads the file from disk
(useful if you hand-edited it directly). Aliases: `/chroma`, `/nc`.
Admin subcommands (`add`/`remove`/`reload`) need `nexuschroma.admin`
(default: op). The core place/remove conversion needs
`nexuschroma.use` (default: **true** -- on for everyone out of the box;
set it to `false` for specific players/groups if you want to restrict
who can make chroma squares).

## Honest risk flags -- I don't have a live server to test against

Same situation as NexusMechanica's first pass: this is written against
Paper's documented API, but hasn't been through a real compile-and-run
cycle yet. Two spots specifically worth double-checking on first test:

- **`PlayerItemFrameChangeEvent` timing.** This Paper event is
  `Cancellable`, and I'm relying on cancelling it to fully prevent (or
  cleanly revert) whatever vanilla was about to do to the frame and
  the player's held item. I wrote the hand-item consumption logic to
  work either way (it checks which hand still holds a matching item
  *after* cancelling, rather than assuming a specific before/after
  ordering) -- but if something looks off on first test (e.g. the
  block gets duplicated or not consumed), this is the first place to
  look.
- **Map color snapping.** `MapPalette.matchColor()` snaps any RGB you
  give it to Minecraft's built-in map color palette, which is a fixed
  set of a few hundred colors, not arbitrary RGB. Since that palette
  is itself derived from real block colors, most entries should snap
  cleanly, but a few of the hand-picked or derived hexes might land on
  a visibly different shade than expected. `/nexuschroma list` shows
  you the hex NexusChroma is *asking* for; the in-game square is
  whatever the palette actually had closest to it.

## What's explicitly not built

- **No resource pack / custom textures.** Everything here is achieved
  with vanilla filled maps -- the tradeoff is you get flat solid
  color, not a mini version of the block's actual texture pattern
  (stone's speckles, wood's grain, etc.).
- **No support for player-crafted/vanilla filled maps accidentally
  matching this system.** A real map a player fills in and frames
  behaves exactly as vanilla -- NexusChroma only acts on maps it
  tagged itself.
- **No stairs/slabs/other non-full-block shapes in the color table by
  default.** The default table sticks to full blocks; partial shapes
  would still work as a color source (add them with `/nexuschroma
  add`), the square just won't hint at the shape either way, since
  it's always a flat square regardless of what block it represents.
- **No per-face/partial coloring.** One block in, one flat color out --
  there's no gradient, pattern, or multi-color rendering.
