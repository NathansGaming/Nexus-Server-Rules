# VoidRescue

A small, standalone Java/Maven Paper plugin with one job: get players out of the void, automatically,
even when they're stuck in a state a normal `/tp` or `/kill` can't fix.

This is a completely separate project from Nexus Dimensions - it doesn't depend on it, doesn't touch
its files, and can be installed on any Paper server on its own.

## The problem this fixes

Some players - so far confirmed specifically on **Bedrock clients connecting through Geyser** - can end
up in a state where:

- They've fallen below the world and are stuck in the void.
- They're at a sliver of health (often reported as "half a heart") and are effectively dead server-side
  (`Player#isDead()` is true).
- A normal `/tp` doesn't visibly move them, and `/kill` doesn't finish them off or let them respawn.
- Restarting the server does not fix already-stuck players.

This matches several still-open, unresolved upstream reports (see Sources below) - there is no official
fix for this as of this writing, at either the Paper or Geyser level. VoidRescue works around it entirely
from the Paper side instead of waiting on one.

## How it works

Two independent detection paths, both feeding into the same rescue logic:

1. **A background watchdog** (`VoidWatchdogTask`) scans every online player on a timer (default every
   10 ticks / 0.5s). This is the important one for the "stuck and not moving" case - a genuinely frozen
   player may never fire a movement event, so relying on movement alone isn't enough. If a player has been
   `isDead()` for more than `stuckDeadTicks` (default 40 ticks / ~2s) in a row, they're force-rescued
   regardless of position - this is the direct fix for "technically dead, can't teleport, can't kill."
2. **A move-event fast path** (`RescueListener`) catches a player crossing below the void threshold
   immediately, faster than waiting for the next watchdog scan.

A rescue does all of the following:

1. If the player is dead, calls `player.spigot().respawn()` to force-complete a stuck client-side
   death/respawn screen (the documented Spigot/Paper API for exactly this situation).
2. Waits a short, configurable delay (`respawnDelayTicks`, default 4) before applying health/state changes.
   This is deliberate: one of the upstream Paper reports on this exact class of bug traces it to
   attribute/health changes applied in the same tick as the death/respawn transition, and says delaying by
   a few ticks fixed it for them. See Sources.
3. Teleports the player to world spawn (or a configured per-world override), clears fall distance/fire/
   freeze ticks, and heals them to a configured amount.
4. Teleports them **again**, one tick later. A single teleport has been reported to sometimes not register
   client-side on an already-desynced Bedrock/Geyser client; a second teleport shortly after forces the
   client to re-acknowledge the new position.
5. Sends the player a message, logs it, and optionally notifies staff online.

The void threshold itself is computed from `world.getMinHeight()`, not a hardcoded Y coordinate, so it's
correct on any world - including custom-height dimensions from other plugins.

## The immediate fix for players stuck right now

This is the part you probably need first. As soon as the plugin is installed and the server has restarted
once, run:

```
/voidrescue <playerName>
```

This **unconditionally** force-rescues that one player - it bypasses cooldowns and every other check, and
runs the exact same respawn-force + delayed-heal + double-teleport sequence described above. This is the
direct override for someone stuck right now that plain `/tp`/`/kill` can't reach.

Other forms:

- `/voidrescue all` - force-rescues every online player who is currently dead or below the void threshold,
  and reports how many it rescued.
- `/voidrescue reload` - reloads `config.yml` without a restart.

Requires the `voidrescue.admin` permission (default: `op`). The command also has the alias `/vr`.

## Installation

1. Build the jar (see Building, below) or use a prebuilt one.
2. Drop `VoidRescue-1.0.0.jar` into your server's `plugins/` folder.
3. Restart the server once.
4. If you have players stuck right now, run `/voidrescue <playerName>` for each of them (or `/voidrescue
   all`) immediately after the restart - don't wait for the watchdog, though it will also pick them up
   automatically within a couple seconds.

No other setup is required. Everything is configurable afterward via `plugins/VoidRescue/config.yml`
(see the comments in that file for what each setting does) plus `/voidrescue reload`.

## Building

This is a Maven project targeting Java 21 and the Paper API.

```
mvn package
```

The finished jar is written to `target/VoidRescue-1.0.0.jar`. There's no bundled Maven Wrapper; if you
want one, run `mvn -N wrapper:wrapper` once inside this project - it's a couple of small files that don't
change how the project builds, they just let you run `./mvnw package` without a local Maven install.

Requires network access to Maven Central and `https://repo.papermc.io/repository/maven-public/` (Paper's
own API isn't published to Central) the first time you build, to download the Paper API and build plugins.

**Verified two ways** in the environment this was built in (which has no outbound network access, so a
full `mvn package` couldn't be run end-to-end there):

- `mvn -B validate` - confirms the POM itself is well-formed. Returns `BUILD SUCCESS`.
- `mvn -B compile` and a plain `javac` compile of every `.java` file with no Paper API on the classpath -
  both reach exactly the expected point (a network call for a dependency/plugin) and fail there, with the
  `javac` run producing only the expected cascade of "package org.bukkit does not exist" /
  "cannot find symbol" errors for Bukkit/Paper/Adventure API types - not one error referencing a typo in
  this project's own classes or methods.

If you have real network access (any normal dev machine or CI runner), `mvn package` should complete
cleanly.

## Configuration

See the heavily-commented `src/main/resources/config.yml` for every setting and what it's for. Once
installed, the live copy is at `plugins/VoidRescue/config.yml`. Key ones:

- `scanIntervalTicks` - how often the watchdog scans (default 10 = every 0.5s).
- `voidMarginBelowMinHeight` - how far below the world's real min height counts as "in the void" (default 4).
- `stuckDeadTicks` - how long `isDead()` in a row before a forced rescue kicks in (default 40 = ~2s).
- `respawnDelayTicks` - delay before applying health/teleport changes after forcing a respawn (default 4).
- `rescueCooldownSeconds` - minimum gap between two *automatic* rescues of the same player (default 5;
  doesn't apply to `/voidrescue` or the stuck-dead watchdog path, which always fire immediately).
- `rescueHealth`, `notifyStaff`, `worldSpawns` (per-world spawn overrides).

## Sources

The Geyser/void-death desync this plugin targets matches these upstream reports, none of which have an
official fix as of this writing:

- [GeyserMC/Geyser#4724](https://github.com/GeyserMC/Geyser/issues/4724) - Bedrock players falling into
  the void after login/death, stuck at low health, teleport not registering client-side. Matches the
  reported symptoms closely; no resolution documented.
- [PaperMC/Paper#13451](https://github.com/PaperMC/Paper/issues/13451) - player desync with the server
  after death; reporter found delaying post-death changes by a few ticks fixed it for them (the basis for
  this plugin's `respawnDelayTicks`). Also notes teleportation can work even when a kill/respawn doesn't,
  which is why this plugin doesn't rely on `/kill` at all.
- [PaperMC/Paper#11038](https://github.com/PaperMC/Paper/issues/11038) - players unable to respawn after
  death, sometimes teleported into the void instead, requiring a rejoin to fix. Closed as a duplicate with
  no plugin-side workaround documented in that specific issue, which is part of why this plugin exists.
