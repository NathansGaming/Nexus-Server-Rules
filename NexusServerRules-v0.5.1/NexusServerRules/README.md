# NexusServerRules v0.5.1

Automatic chat-toxicity and griefing-burst detection and punishment, plus a
hard explosion block and an integrated ban command, for Paper 1.21.x.

## Honest status - is this actually done?

Asked directly, so answering directly: **no, not "done" in the sense of
"deploy blind and walk away."** Here's exactly where it stands.

**Solid and internally consistent:**
- Every feature (chat filter, griefing detection, explosion block, ban,
  appeal, review queue + GUI) is wired through the same plugin, shares
  the same data where it should, and there's no leftover stub code -
  grepped for TODO/FIXME/placeholder markers, found none.
- Fixed a real efficiency bug this pass: `/ban`, `/nexusrules clear`,
  and `/nexusrules info` used to call `Bukkit.getOfflinePlayer(String)`,
  which can silently **block the main thread** on a web request to
  Mojang for any name the server hasn't seen before - a real lag-spike
  risk on a live server. Replaced with a local name→UUID cache
  (`storage/PlayerNameCache.java`) populated on join, so those commands
  now resolve instantly with zero network calls. Trade-off: they can no
  longer resolve a name that has *never* joined this server at all.

**Still genuinely unverified - this is the honest part:**
- **This has never been compiled.** This sandbox can't reach Maven
  Central or the PaperMC repository, so nothing here has gone through
  an actual `javac`/Paper API compile, let alone run on a real server.
  I've read every file for consistency (matching types, signatures,
  imports), but that is not the same guarantee as a green build. Run
  `mvn clean package` and fix whatever surfaces before trusting this on
  a live server with real players.
- **Nothing has been playtested.** No confirmation the griefing
  threshold, the chat fuzzy-matcher's false-positive rate, or the
  explosion guard's event-cancellation actually feel right in practice
  - only that the logic is sound on paper.
- The slur/profanity/threat/sexual-content lists are starter sets, not
  exhaustive - expect to tune `patterns.yml` after real use.
- The griefing detector is a heuristic with no concept of land claims -
  expect some false positives until tuned or tied into a real claim
  system.

**Bottom line:** the architecture is genuinely solid and everything
that's supposed to talk to everything else does. "Will work exactly
right the first time with zero adjustment" isn't a real bar for
software this scope reaches without a test server - budget an evening
of actually running it, reading the logs, and tuning `config.yml`
before treating it as load-bearing for real moderation.

## New in v0.5.0

- **`/ban <player> [reason]`** (`commands/BanCommand.java`), operators only
  (`nexusrules.ban`, default op). Worth knowing: **Paper already ships a
  working `/ban` command with no plugin at all** - operators could already
  do this before this change. What this adds is overriding that vanilla
  command with one that goes through the same audit trail as everything
  else here: a persisted `bans-log.txt`, plus a live in-game alert to
  other online staff (`nexusrules.notify`) - the vanilla command gives
  you neither of those. Bans still go into the real Bukkit ban list, so
  they work exactly like vanilla bans (survive restarts, block rejoining,
  visible via `/banlist`) - this doesn't replace that, it just adds
  logging on top and kicks the player immediately if they're online.

## New in v0.4.0 — audit / hardening pass

You asked for a full pass to confirm no cuss words, vulgar language,
racial slurs, or derogatory terms exist anywhere in the plugin, and that
the filter actually catches all of it. Results:

- **Codebase itself: clean, confirmed.** Grepped every `.java`/`.yml`/`.md`
  file for common profanity - zero matches. All moderation content lives
  in `patterns.yml` as data, never in code, comments, or log strings.
- **Real gap found and fixed: `slurs:` used to ship empty.** v0.1.0 shipped
  that list empty on purpose (staff-owned), which meant out of the box
  the plugin caught *no* slurs at all until someone manually populated
  it - contradicting "this plugin will catch all of it." It now ships
  with a real starter list of common racial/ethnic/homophobic/ableist
  slur roots. Still not exhaustive - add more as needed - but it
  functions immediately.
- **New `PROFANITY` category added.** There was no general cuss-word
  category before this pass, only slurs/sexual-content/threats/
  harassment - a `damn`/`fuck`/`shit`-type message wouldn't have been
  caught at all. `patterns.yml` now has a `profanity:` section, and
  `ViolationCategory.PROFANITY` is wired through detection, queue, GUI,
  and staff commands exactly like every other category.
- **Fixed a false-positive risk the above changes exposed:** the fuzzy
  matcher's edit-distance tolerance, applied to a short root like
  `"damn"`, will also match `"dawn"` (one letter off). Short common
  words are exactly where fuzzy matching backfires - `"bitch"` vs
  `"witch"` is the same problem. Fixed by disabling fuzzy tolerance for
  roots of 5 characters or fewer (exact substring match only, which
  still catches leetspeak/spacing obfuscation via normalization - it
  only gives up catching missing-letter truncation on very short words,
  where the false-positive cost outweighed the benefit). Longer roots
  (6+) keep fuzzy matching as before.

## New in v0.3.0

- **Explosion prevention** (`explosives/ExplosionGuard.java`): TNT and
  respawn anchors are hard-blocked from ever exploding, for anyone,
  anywhere on the server. This is **not permission-gated** - there's no
  bypass node to grant, which is what makes it hold even for operators.
  Three layers, all cancelling by default:
  1. `BlockIgniteEvent` - stops TNT being lit at all (flint & steel,
     fire spread, lava, chain-reaction from another explosion).
  2. `EntityExplodeEvent` - backstop that catches primed TNT / TNT
     minecarts made any other way, including `/summon primed_tnt` from
     the console or an OP - this is the layer that makes it actually
     un-bypassable, since even a directly-summoned entity still has to
     pass through this event to do anything.
  3. `BlockExplodeEvent` - covers block-sourced explosions with no
     entity involved, which is how a respawn anchor's "charged past
     max and used outside the Nether" explosion is delivered.
  Config-driven (`explosion-prevention.banned-items` in `config.yml`) -
  ships with `TNT` and `RESPAWN_ANCHOR`, and more can be added later.
  Note: this blocks the *explosion*, not placing/holding the item -
  players can still place a TNT block or a respawn anchor, it just
  will never detonate. Say if you'd rather it also block placement
  entirely.

## New in v0.2.0

- **Griefing-burst detection** (`listeners/GriefListener.java`): tracks which
  player placed which block this session (`grief/PlacedBlockRegistry.java`,
  in-memory only). If a player rapidly breaks blocks someone ELSE placed
  (not natural terrain, not their own build) past a threshold within a
  short window, it routes through the exact same restriction pipeline as
  chat violations — same `restrictions.yml` entry, same queue, same
  manual-clear-only rule, same `/appeal`. This is a heuristic (no claim
  or ownership system) — see the false-positive note in the code and
  README section below.
- **In-game GUI review queue** (`gui/`): `/nexusrules gui` opens an
  inventory of player heads, one per restricted player. Left-click to
  clear (`nexusrules.clear`), right-click for full case detail in chat.
  Built directly off the same data as `/nexusrules queue` and `info`, so
  it can't drift out of sync.
- **`strict-mode` is now actually wired up** (`config.yml`): flipping
  `chat-detection.strict-mode: false` means only EXACT pattern matches
  auto-restrict; FUZZY-only hits still get blocked and queued for staff
  review, but don't restrict the player. Useful for watching the fuzzy
  matcher's real false-positive rate before trusting it fully. Default
  stays `true`, matching what you asked for originally.

## What's built (v0.1.0 base)

- **Detection engine** (`detection/`): normalizes chat (leetspeak, spacing,
  punctuation, stretched letters, diacritics) then fuzzy-matches against
  `patterns.yml` roots with a small edit-distance tolerance — catches
  missing/extra letters and simple substitutions without needing every
  variant spelled out.
- **Punishment** (`punishment/`): instant blindness, Slowness VII, forced
  adventure mode, and chat mute on any match. State is **not** timer-based —
  it persists in `restrictions.yml` across relogin and server restarts,
  and only clears via staff command.
- **Staff review** (`commands/NexusRulesCommand.java`): `/nexusrules queue`,
  `/nexusrules info <player>`, `/nexusrules clear <player>`,
  `/nexusrules reload`. Every incident is also independently logged to
  `violations-log.txt` regardless of the in-memory queue.
- **Appeal channel** (`commands/AppealCommand.java`): `/appeal <message>`
  works even while muted, pings all online staff, and writes to
  `appeals-log.txt` — the safety valve for false positives, since you
  chose strict auto-action on ambiguous matches and manual-only clearing.
- **Rejoin handling** (`listeners/JoinListener.java`): re-applies the
  restriction on every login by checking the on-disk store, not client
  state, so logging out never quietly ends a punishment.

## NOT built (came up in design, deliberately left out)

Locking the client's disconnect/pause-menu buttons — that's client-side UI
a server plugin has no hook into, and I wasn't going to build a "trap
someone in the app" mechanic even if it were technically possible. The
persistence-across-relogin above gets you the same "can't just log out to
escape it" outcome without that.

## Still open (good next steps)

- The `slurs:` and `profanity:` starter lists are functional but not
  exhaustive - review and expand them for your community, especially
  slang/terms specific to your playerbase that a generic list won't
  anticipate.
- Griefing detection is heuristic and has no idea about land claims —
  tying it into a claim system (like NexusRealms) instead of/alongside
  the raw "someone else placed it" check would cut false positives a
  lot on a server with real land ownership.
- No config toggle yet for whether griefing-burst restrictions require
  the *exact* triggering block, or should also flag a rolling pattern
  across multiple separate short bursts (currently each burst's counter
  resets after it fires).

## A real limitation worth knowing about the grief detector

It only knows "someone else placed this block recently, and you just
broke a lot of those, fast." It does NOT know about land claims,
permissions, or intent. Two players mining through shared public
scaffolding, or a builder tearing down and redoing their own team's
structure right after a teammate placed part of it, can trip this. The
`window-seconds` / `block-threshold` values in `config.yml` are a
starting point, not tuned to your server — expect to adjust them (and
expect a few false positives while you do), and lean on `/appeal` and
manual review as the actual safety net, same as with chat detection.

## Building it

This sandbox can't reach Maven Central or the PaperMC repository, so I
couldn't compile-verify this pass (unlike some of your other Nexus
plugins). On your own machine, with Maven and internet access:

```bash
mvn clean package
```

The jar lands in `target/NexusServerRules-0.1.0.jar`. Drop it in
`plugins/`, start the server once to generate `patterns.yml` and the
data folder, fill in `slurs:`, then `/nexusrules reload`.

Please build and test this against a real server before relying on it —
I'd treat this pass as a strong first draft, not something to trust
blind on live chat with real players.
