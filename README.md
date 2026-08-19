# NexusKairos v0.2.0 -- the boss fight

Continuous stat scaling across unlimited fight counts, band-gated
mechanics/dialogue so content stays fresh at real milestones, the
escape-instead-of-death mechanic, a complete summon ritual (crystals +
sacrifice), real arena hazards, and an automatic true-ending trigger.

## The core idea, in short
- **Every fight count is unique** -- health/damage/speed scale off the
  raw fight number (`TierCalculator`), so fight #47 and fight #48 are
  never identical. This is what actually delivers "hundreds of tiers"
  without hand-writing hundreds of configs.
- **Bands gate content, not numbers** -- `FightBand` splits the fight
  count into 6 wide story bands (Fractured -> Transcendent). Each band
  unlocks new mechanics (mob waves, teleport strikes, arena hazards) and
  a different dialogue pool/tone. Ranges widen as they go.
- **Normal fights end in escape, not death** -- when Kairos's health
  drops below `fight.escape-health-percent` (12% by default), he
  teleports out with the "I'll come back when you're stronger" line
  instead of dying. The fight count still increments -- every attempt
  still counts as progress toward later bands.
- **The true ending arms itself automatically** -- once the server-wide
  fight count crosses `fight.true-ending-unlock-count` (default 50),
  the next summon skips the escape mechanic entirely and Kairos can
  actually die. The whole server gets a broadcast the moment it arms.

## Commands
- `/kairos summon` -- attempts the full ritual: be within 15 blocks of
  the arena, carry 10 Fractured Crystals, and have sacrificed enough
  pigs/endermen in the End (see below)
- `/kairos status` -- fight count, current band, active/inactive, true
  ending armed state
- `/kairos forcetier <count>` (admin) -- jump the fight count for
  testing, so you don't have to grind 40 real fights to see the
  Stabilizing band
- `/kairos armtrueending` (admin) -- manually arms the real kill for
  testing, without grinding to fight #50
- `/kairos givecrystal [player] [amount]` (admin) -- hands out real
  Fractured Crystals

## What's now real (this pass closed these gaps)

1. **The crystal item is real, not a placeholder.** `CrystalItems`
   creates a tagged "Fractured Crystal" -- visually still an Amethyst
   Shard, but marked via PersistentDataContainer so it can't be faked
   by mining a real amethyst geode. Distribute it with:
   ```
   /kairos givecrystal [player] [amount]
   ```
   (admin only; defaults to yourself, 1 crystal, if you omit args).

2. **The ritual is complete**, per your call: location + 10 Fractured
   Crystals + a sacrifice. No clearance-level gate (explicitly skipped
   per your instruction) and no time-window requirement (not
   requested -- easy to add later if you want one).

   The sacrifice: kill pigs or endermen while in the End.
   `SacrificeTracker` counts either toward the requirement
   (`fight.sacrifice-required`, default 5) -- endermen since they
   spawn there naturally, pigs since getting one into the End is its
   own small challenge. Progress persists per-player until a
   successful summon consumes it; a failed attempt doesn't reset it.

3. **The true ending now has a real, automatic trigger.** Once the
   server-wide fight count reaches `fight.true-ending-unlock-count`
   (default 50), the next summon automatically arms the real kill --
   no more manual admin command needed in normal play (though
   `/kairos armtrueending` still exists for testing without grinding
   50 real fights). When it auto-arms, the whole server gets a
   broadcast: *"Something is different now. Kairos will not run
   again."* -- a real narrative beat, not a silent flag flip.

4. **Arena hazards are real mechanics now**, not just a flag nothing
   used. Once unlocked (Adapting band onward), every ~12.5 seconds
   during the ACTIVE phase, either:
   - **Floor collapse** -- a 3x3 patch of ground vanishes under a
     random nearby player for 3 seconds, telegraphed with particles/
     sound first
   - **Fire burst** -- flame particles + a burn effect on anyone still
     standing in a small radius around a random nearby player a moment
     later

   Both are new code (`ArenaHazards.java`), not previously built.

## What's still genuinely open
- **What happens after a true-ending kill** -- cutscene, rewards, which
  story flags update in your broader F.R.A.C.T.U.R.E./NexusBridge
  system. This is intentionally left as a `TODO` in `KairosBoss.die()`
  since it depends on systems outside this plugin (your mission/
  artifact/WorldSpawn chest flow) that I don't have the specifics for --
  tell me what that sequence should actually do and I'll wire it in.
- **Live AI dialogue endpoint is still unconfirmed** against your real
  backend -- see the request/response shape documented further down.
  This is the one piece that needs YOUR backend to actually test
  against, not something I can finish alone.
- **Time-window ritual requirement** -- not built, wasn't requested
  this pass. Say the word if you want it added.

## Live AI dialogue -- primary path, not a stub
`DialogueEngine.requestLine()` makes a real async HTTP POST to
   whatever URL you put in `config.yml` under `kairos-ai.endpoint`,
   with a `kairos-ai.api-key` sent as a Bearer token. Every fight-start,
   mid-fight, and escape line goes through this now -- the small local
   pool only fires if the endpoint is blank, the request fails, or the
   response can't be parsed, so the fight never hard-breaks if your
   backend has a bad moment.

   **What your backend needs to accept/return**, so the AI can actually
   be tier-aware:

   Request (JSON POST body):
   ```json
   {
     "fightCount": 47,
     "band": "STABILIZING",
     "eventType": "mid_fight",
     "player": "SomePlayerName"
   }
   ```
   `eventType` is one of `fight_start`, `mid_fight`, `escape`, or
   `death` -- gives the AI the moment, not just the tier, so it can vary
   tone accordingly (an escape line should probably read differently
   than a fight-start taunt, even at the same fight count; `death` only
   fires on the true-ending kill).

   Expected response (JSON):
   ```json
   { "message": "You again. Persistent." }
   ```
   Plain text, no color codes needed -- the plugin applies its own
   clean/corrupted visual rendering on top of whatever text comes back,
   so the backend can just focus on writing good lines and let the
   glitch effect be a presentation-layer thing on our end.

   If your backend's actual contract looks different (different auth
   style, different payload shape, streaming response, etc.), tell me
   the real shape and I'll adjust `DialogueEngine` to match -- what's
   here is a reasonable guess, not something I've confirmed against
   your Flask app.

## The intro/outro sequence
Kairos no longer just appears. When summoned:
1. He spawns immediately but **invisible and invulnerable** -- for 5
   seconds, a ring of portal particles (`REVERSE_PORTAL` + `END_ROD`)
   builds up and expands at the arena center, with portal spawn sounds.
2. At the end of the buildup, a flash + big particle burst reveals him
   to everyone nearby, his AI turns on, invulnerability drops, and *then*
   the fight-start dialogue line fires -- so the line lands right as
   he's revealed, not before he's even visible.

When he escapes (or, on the true ending, actually dies):
1. Combat freezes (AI off, invulnerable) the instant the trigger fires.
2. The escape/death dialogue line fires immediately.
3. Over 4 seconds, the portal ring closes in around him (shrinking
   radius) while he drifts slightly upward, then a final flash +
   particle burst plays and he's removed.

This is all driven by a small phase state machine in `KairosBoss`
(`INTRO -> ACTIVE -> OUTRO_ESCAPE/OUTRO_DEATH -> DONE`) rather than
being tacked onto the old instant-spawn/instant-remove logic, so mob
waves, teleport strikes, and the escape-health check are all correctly
paused during the intro and outro instead of firing while he's supposed
to be a special-effects sequence, not a fightable target.

**Untested specifics worth flagging:** the intro/outro timings (5s in,
4s out) and portal visual (a simple particle ring, not a real portal
block structure) are a first guess at "badass," not something I've
seen in-game. If it reads as too slow, too fast, or the particle shape
looks wrong once you actually see it, those are quick numeric/visual
tweaks in `KairosBoss.java` (`INTRO_TICKS`, `OUTRO_TICKS`,
`drawPortalRing()`), not a redesign.


- **Vanilla dragon AI still drives most of the actual combat feel** --
  we're layering scaling/mobs/strikes/dialogue on top of a real
  EnderDragon entity, not replacing its base flight/attack AI. If it
  doesn't feel "boss enough," full custom AI control is a bigger lift
  we can scope separately.
- **Teleport strikes are simple** -- teleport near a random player,
  particle burst, direct damage. No wind-up telegraph yet, so it might
  feel cheap/unfair in testing -- flag it and I'll add a warning phase.
- **Arena hazards are new and untested** -- floor collapse and fire
  burst (`ArenaHazards.java`) are freshly built this pass. Haven't been
  seen in a live fight; flag it if the restore delay, radius, or
  frequency feels off once you've dodged a few.
- **No boss bar UI beyond the dragon's built-in one** -- fight count/
  band aren't shown on-screen during the fight, only via `/kairos
  status`. Easy add if you want it visible live.
- **Single global fight, not per-player/per-party** -- if multiple
  groups want to fight Kairos independently, this version doesn't
  support that yet (one global `activeBoss`).

## Setup (same flow as the others)
1. New repo, upload this folder's contents.
2. Codespace, `sdk use java 21.0.11-amzn` if needed.
3. `mvn clean package`
4. Jar into `plugins/`, restart.
5. `/kairos givecrystal <yourname> 10` to get your 10 Fractured
   Crystals, kill 5 pigs or endermen while in the End (mix and match --
   any combination counts), stand near 1961, 65, -7, then
   `/kairos summon`.

This closed the four gaps we identified (real crystal item, complete
ritual, automatic true-ending trigger, real arena hazards) -- but like
every pass before it, none of this has been watched happen in an actual
live fight yet. Test it, and send me chat logs / behavior that's off
exactly like we did with the guide book and the wrench command.
