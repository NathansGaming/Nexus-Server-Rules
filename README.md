# NexusMorality v0.1.0

First plugin in the new batch of "crazy ideas" for the Nexus ecosystem, and the
first one that's a genuine narrative/choice mechanic rather than another
survival stat. A rare, scripted moment: you find an injured survivor NPC out
in the world, and you have to decide what to do about them.

## What it does

- **Rare survivor encounters spawn near online players** - a low-chance roll
  every `check-interval-seconds` (default every 30s, ~3% chance per eligible
  player per roll). Deliberately uncommon; this is meant to feel like a real
  moment, not a recurring mob type. A player who just had one spawn near them
  won't roll again for `player-cooldown-seconds` (default 15 minutes), and a
  hard `max-concurrent` cap (default 3) keeps the whole server from ever
  being flooded with them at once.
- **Help them**: right-click the survivor with food (anything
  `Material#isEdible()` already covers) or a water bottle, then stay within
  `help-radius-blocks` for `help-hold-seconds` straight. Wander off before
  that and the hold just resets - you can walk back and finish it, nothing is
  lost. Succeed and the survivor thanks you, drops a small token, and heads
  off; you gain karma.
- **Loot and leave**: hit the survivor at all - melee or a projectile you
  fired - and the encounter resolves immediately, on that first hit, not on
  an eventual kill. They drop their "meager supplies" (a bit more generous
  than the thank-you drop, on purpose - the dark path is supposed to be a
  real temptation) and you lose karma, more than the help path grants, so
  the two choices aren't just mirror images of each other.
- **Ignore it entirely**: if nobody interacts within `despawn-seconds`
  (default 5 minutes), it just quietly disappears. No karma change either
  way - not helping isn't punished, only actively looting is.
- **Karma persists** per player across restarts (`karma.yml`, written
  immediately on every change, same pattern NexusServerRules uses for
  `restrictions.yml`).

## Why this exists / how it fits the bigger picture

This is step one of an 8-plugin roadmap for the "crazy ideas" batch (dream
mechanics, infected structures, a Discord-bridged ARG layer, sabotage-able
defenses, elected town governance, an AI-director difficulty system, and
finally a cross-plugin reputation passport that ties all of it together).
Karma tracked here is meant to feed that last one eventually - which is why
`api/NexusMoralityApi.java` exists and is registered with Bukkit's
`ServicesManager` on enable, exactly the way NexusRealms exposes
`NexusRealmsApi` for NexusServerRules to consume. A future NexusReputation
plugin looks this up via reflection (`Class.forName` +
`Bukkit.getServicesManager().getRegistration(...)`), the same soft-dependency
pattern already proven out in `NexusServerRules/integration/LandTrustBridge.java`
- so NexusMorality never needs to become a compile-time dependency of
anything, and keeps working completely standalone (karma just goes unused by
anything else) if nothing downstream is installed yet.

The API surface is deliberately tiny right now - one method,
`getKarma(UUID) -> int` - since it only needs to survive being called
reflectively by a plugin that doesn't exist yet.

## Commands

- `/nexusmorality trigger [player]` - force-spawn an encounter near yourself
  or a target, bypassing the chance roll and cooldown (still respects
  `max-concurrent` and the safe-location search). Useful for testing without
  waiting on the RNG.
- `/nexusmorality karma <player>` - check anyone's current karma total.
- `/nexusmorality list` - see every currently active, unresolved encounter
  and where it is.
- `/nexusmorality reload` - reload `config.yml`. Already-active encounters
  keep whatever settings they spawned with; only new ones pick up changes.

All behind `nexusmorality.admin` (default: op), same convention as the rest
of the Nexus plugins.

## Honest status - same caveat as always

**This has not been compiled or run.** Like every other Nexus plugin drop,
this sandbox has no route to the PaperMC Maven repository, so this has never
been through a real `javac`/Paper API build, let alone an actual server. I
read every file for internal consistency (types, method signatures, the
Villager/Mob API surface I'm relying on) but that's not the same guarantee as
a green build - run `mvn clean package` and fix whatever the compiler flags
before trusting this on a live server.

**Nothing has been playtested.** The spawn-chance/cooldown/max-concurrent
numbers are a starting guess, not tuned against real player counts or world
size - expect to adjust `config.yml` once you've watched it run for a while.
The safe-location search (10 attempts, checking for solid non-liquid ground
with two blocks of headroom, minimum distance from world spawn) is meant to
avoid spawning a survivor in a wall, underwater, or right on top of someone,
but it hasn't been tested against a real, chunk-loaded world with actual
terrain variety.

**Encounters are not persisted across a restart, on purpose.** This is meant
to be a short-lived, in-the-moment mechanic, not state staff need to audit
later (karma is the thing that persists - the encounter itself doesn't need
to). `onDisable` explicitly removes every currently-spawned survivor entity
so a restart can't accumulate untracked, orphaned NPCs over time.

**No land-claim awareness yet.** An encounter can currently spawn inside
someone's claimed base if the safe-location roll happens to land there -
there's no NexusRealms integration checking claim ownership before spawning.
Worth adding once we're further into the roadmap and have NexusRealms'
source available to build a soft-dependency bridge against, the same way
NexusServerRules did for griefing detection.

**Villager-based, not a real "wounded" visual.** The survivor is a
`Profession.NONE` Villager with Slowness and a custom name - not a distinct
model or a Citizens-plugin NPC. No new dependency required, but it doesn't
look dramatically different from any other villager beyond the name tag and
slow movement. Worth revisiting with a resource pack or a proper NPC library
if the flat visual doesn't sell the moment enough in practice.

## Building it

Same as every other Nexus plugin - this sandbox can't reach Maven Central or
the PaperMC repository:

```bash
mvn clean package
```

Jar lands in `target/NexusMorality-0.1.0.jar`. Drop it in `plugins/`, start
once to generate `config.yml` and the data folder, tune the spawn numbers to
taste, and try `/nexusmorality trigger` to see one without waiting on RNG.
