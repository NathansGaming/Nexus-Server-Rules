# NexusDreams v0.1.0

Second plugin in the "crazy ideas" batch. Sleeping through the night now
means something - the dream you have reflects the shape you were actually
in when you went to bed.

## What it does

Hooks `TimeSkipEvent` with `SkipReason.NIGHT_SKIP` - the moment enough
players sleeping actually causes the server to skip to morning, not every
individual bed enter/leave (see the javadoc on `SleepListener` for why that
distinction matters). Every player still asleep at that moment gets a dream:

- **Peaceful Dream** (thriving going into the night): a title, flavor text,
  and a real reward - Regeneration for a bit.
- **Restless Sleep** (middling): flavor text only, no mechanical effect.
- **Troubled Dream** (rough shape): flavor text plus a short Nausea
  "hangover."
- **Nightmare** (critical): flavor text, Darkness + Weakness, and a sound
  cue. A genuinely rough start to the day.

The mechanical severity comes from an averaged "strain" score across every
available signal. The **flavor text** comes from whichever single signal was
*worst* - so a player who's otherwise fine but critically irradiated gets a
radiation-themed nightmare even if their average is only "troubled." Both
the thresholds and every line of flavor text are in `config.yml` and easy to
expand.

## Two signal tiers - this is the part worth understanding

**With NexusSurvival installed** (soft-dependency, not required): dreams
read real thirst, rad-oxygen, hygiene, and infection severity via a new
`NexusSurvivalApi` class I added to NexusSurvival as a companion change to
this plugin - it didn't expose a public API before this. Registered with
Bukkit's `ServicesManager`, looked up via reflection in
`integration/SurvivalBridge.java`, the exact same soft-dependency pattern
NexusServerRules already uses for NexusRealms
(`integration/LandTrustBridge.java`). **You need to rebuild and redeploy
NexusSurvival with this change for the rich version of dreams to work** -
grab the updated NexusSurvival delivered alongside this.

**Without NexusSurvival**: dreams fall back to vanilla-only signals -
current health%, hunger%, and whether any conventionally-bad potion effect
(poison, wither, hunger, nausea, blindness, weakness, slowness, darkness) is
currently active. Still a real mechanic on a server that doesn't run
NexusSurvival at all, just a less specific one - the "worst signal" flavor
category becomes generic `vanilla` lines instead of thirst/radiation/
hygiene/disease-specific ones.

Either way, if NexusSurvival isn't installed or its API can't be reached for
any reason, this degrades gracefully to the fallback tier rather than
throwing or blocking anyone's sleep - same defensive posture as every other
cross-plugin bridge in this ecosystem.

## Commands

- `/nexusdreams trigger [player]` - force a dream right now, without
  needing to actually sleep through a night or wait out the cooldown.
  Useful for tuning flavor text and effect strength.
- `/nexusdreams reload` - reload `config.yml`.

Behind `nexusdreams.admin` (default: op).

## Honest status

**Not compiled or run** - same standing caveat as every Nexus plugin out of
this sandbox. No route to the PaperMC repository here, so this is
read-for-consistency (types, method signatures, the Adventure Title API
surface it uses for on-screen text), not compiler-verified. Run
`mvn clean package` and fix whatever surfaces.

**Not playtested** - the strain thresholds (0.25/0.50/0.75) and which
vanilla potion effects count as "bad" for the fallback tier are reasonable
starting guesses, not tuned against real play. The `cooldown-seconds`
safety net (default 60s) exists in case something triggers repeated
NIGHT_SKIP events back-to-back (a plugin or command forcing time forward
several times quickly) - normal sleep is nowhere near that fast, so it
shouldn't matter in ordinary play, but it's untested against anything that
manipulates world time aggressively.

**No land-claim or "safe to sleep" awareness.** A dream fires for anyone
asleep when the skip happens, same as vanilla lets anyone sleep anywhere a
bed is safe to place. Not a gap unique to this plugin, just worth knowing
it doesn't add any restriction on top of vanilla's own bed rules.

**Visual is server-side only.** No client resource pack or terrain
distortion - the "dream" is a title/subtitle, a couple of potion effects,
and (for nightmares) a sound cue. Real terrain hallucination effects would
need a resource pack or heavier packet work; this is the achievable version
without adding that dependency.

## Building it

```bash
mvn clean package
```

Jar lands in `target/NexusDreams-0.1.0.jar`. Drop it in `plugins/` alongside
the updated NexusSurvival jar if you want the rich signal tier, or on its
own for the vanilla-fallback version. Try `/nexusdreams trigger` to see one
without waiting on a real night.
