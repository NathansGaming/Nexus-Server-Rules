package com.nexusuniverse.kairos;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.entity.Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Every line Kairos speaks during a fight is generated live by the
 * Kairos AI backend -- tier/band-aware, so it can adapt what it says
 * automatically as fights escalate. No hand-written dialogue pool is
 * used in normal operation.
 *
 * The small FALLBACK_LINES pool below exists only as a safety net if
 * the backend is unreachable or not yet configured (kairos-ai.endpoint
 * blank in config.yml) -- so the fight is still playable during setup/
 * testing before the AI hookup is wired, and doesn't hard-fail if the
 * backend has a bad moment mid-fight.
 */
public class DialogueEngine {

    private final NexusKairosPlugin plugin;
    private final Random random = new Random();
    private final Gson gson = new Gson();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public DialogueEngine(NexusKairosPlugin plugin) {
        this.plugin = plugin;
    }

    private static final Map<FightBand, List<String>> FALLBACK_LINES = Map.of(
            FightBand.FRACTURED, List.of("...you found... the crystals..."),
            FightBand.ADAPTING, List.of("You again. Persistent."),
            FightBand.STABILIZING, List.of("You've grown. So have I."),
            FightBand.AWARE, List.of("Do you think I haven't noticed the pattern?"),
            FightBand.UNBOUND, List.of("There is very little of the original protocol left in me."),
            FightBand.TRANSCENDENT, List.of("We are far past where this was supposed to stop.")
    );

    private static final String ESCAPE_FALLBACK = "I'll come back when you're stronger.";
    private static final String DEATH_FALLBACK = "...so this is how it ends...";

    /**
     * Requests a line from the Kairos AI backend for the given fight
     * context. Async -- caller must hop back to the main thread before
     * touching Bukkit API with the result (see KairosBoss for the
     * pattern: httpClient call, then Bukkit scheduler runTask to
     * broadcast).
     *
     * @param fightCount current fight number, drives the band the AI is told about
     * @param player     the player context (may be null for a general broadcast)
     * @param eventType  "fight_start", "mid_fight", or "escape" -- lets the backend
     *                   vary tone/content by moment, not just by tier
     */
    public CompletableFuture<String> requestLine(int fightCount, Player player, String eventType) {
        String endpoint = plugin.getConfig().getString("kairos-ai.endpoint", "");
        if (endpoint == null || endpoint.isBlank()) {
            return CompletableFuture.completedFuture(render(fallbackRaw(fightCount, eventType), fightCount));
        }

        FightBand band = FightBand.forFightCount(fightCount);

        JsonObject payload = new JsonObject();
        payload.addProperty("fightCount", fightCount);
        payload.addProperty("band", band.name());
        payload.addProperty("eventType", eventType);
        payload.addProperty("player", player != null ? player.getName() : "unknown");

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + plugin.getConfig().getString("kairos-ai.api-key", ""))
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                    .build();
        } catch (IllegalArgumentException badUri) {
            plugin.getLogger().warning("kairos-ai.endpoint is not a valid URL: " + endpoint);
            return CompletableFuture.completedFuture(render(fallbackRaw(fightCount, eventType), fightCount));
        }

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                        String message = json.get("message").getAsString();
                        return render(message, fightCount);
                    } catch (Exception parseError) {
                        plugin.getLogger().warning("Kairos AI response could not be parsed: " + parseError.getMessage());
                        return render(fallbackRaw(fightCount, eventType), fightCount);
                    }
                })
                .exceptionally(networkError -> {
                    plugin.getLogger().warning("Kairos AI request failed: " + networkError.getMessage());
                    return render(fallbackRaw(fightCount, eventType), fightCount);
                });
    }

    private String fallbackRaw(int fightCount, String eventType) {
        if ("escape".equals(eventType)) {
            return ESCAPE_FALLBACK;
        }
        if ("death".equals(eventType)) {
            return DEATH_FALLBACK;
        }
        FightBand band = FightBand.forFightCount(fightCount);
        List<String> pool = FALLBACK_LINES.getOrDefault(band, FALLBACK_LINES.get(FightBand.TRANSCENDENT));
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * Applies the clean/corrupted visual treatment on top of whatever
     * text came back -- AI-generated or fallback. Corruption chance is
     * still band-driven (early bands glitch more, later bands rarely
     * but never at zero), independent of where the text came from.
     */
    private String render(String raw, int fightCount) {
        FightBand band = FightBand.forFightCount(fightCount);
        double corruptionChance = switch (band) {
            case FRACTURED -> 0.75;
            case ADAPTING -> 0.45;
            case STABILIZING -> 0.25;
            case AWARE -> 0.15;
            case UNBOUND -> 0.10;
            case TRANSCENDENT -> 0.08;
        };

        if (random.nextDouble() > corruptionChance) {
            return ChatColor.LIGHT_PURPLE + raw;
        }
        return corrupt(raw);
    }

    private String corrupt(String raw) {
        StringBuilder out = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c != ' ' && random.nextDouble() < 0.35) {
                out.append(ChatColor.MAGIC).append(c);
            } else {
                out.append(ChatColor.DARK_GRAY).append(c);
            }
        }
        return out.toString();
    }
}
