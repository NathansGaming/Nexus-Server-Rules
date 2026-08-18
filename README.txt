NexusDimensions-0_1_0.jar — patched

WHAT CHANGED
------------
Only the plugin's bundled default config.yml was edited:
  datapackPackFormat: 61   ->   datapackPackFormat: 107

61 is the data pack format for Minecraft 1.21.4. Your server is on
26.2, which needs 107. That mismatch is why iron_giant_world (and any
other Tier 2 preset) never activated, no matter how many restarts you
did. Nothing else in the jar was touched — same classes, same presets,
same everything.

IMPORTANT — this only affects a FRESH install
-----------------------------------------------
Bukkit/Paper plugins only copy config.yml out of the jar the very
first time they start with no existing plugins/NexusDimensions/
folder. If you already have a plugins/NexusDimensions/config.yml on
your server from before, this new jar's bundled default won't
overwrite it — you still need to hand-edit that existing file (or
delete it and let the plugin regenerate it fresh from this jar).

INSTALL STEPS
-------------
1. Stop the server.
2. Replace your old NexusDimensions jar in the plugins/ folder with
   this one.
3. Open plugins/NexusDimensions/config.yml (the one already on your
   server, if it exists) and change datapackPackFormat to 107.
4. Delete plugins/NexusDimensions/../<world>/datapacks/nexus_iron_giant_world
   (the stale, incompatible generated pack) so it gets rebuilt clean.
5. Start the server, then run:
     /nexusdim create <worldName> iron_giant_world
6. Restart the server one more time — Tier 2 dimensions only activate
   on the boot after their datapack is (re)written.
