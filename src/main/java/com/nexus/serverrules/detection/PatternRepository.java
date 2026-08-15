package com.nexus.serverrules.detection;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Loads patterns.yml from the plugin's data folder. As of v0.4.0 the
 * "slurs" and "profanity" sections both ship with a real functional
 * starter list (previously "slurs" shipped empty for staff to own -
 * changed on request so the plugin actually catches racist/derogatory
 * language and vulgar/cuss words out of the box, not just after manual
 * setup). "sexual-content" and "threats" also ship with a starter set.
 * All lists are still config-driven and meant to be expanded by staff -
 * none of these are exhaustive.
 */
public final class PatternRepository {

    private final JavaPlugin plugin;
    private final List<PatternEntry> entries = new ArrayList<>();

    public PatternRepository(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        entries.clear();
        File file = new File(plugin.getDataFolder(), "patterns.yml");
        if (!file.exists()) {
            plugin.saveResource("patterns.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        loadSection(yaml, "slurs", ViolationCategory.SLUR);
        loadSection(yaml, "profanity", ViolationCategory.PROFANITY);
        loadSection(yaml, "sexual-content", ViolationCategory.SEXUAL_CONTENT);
        loadSection(yaml, "threats", ViolationCategory.THREAT);
        loadSection(yaml, "harassment", ViolationCategory.HARASSMENT);

        plugin.getLogger().info("[NexusServerRules] Loaded " + entries.size() + " pattern entries from patterns.yml");
        if (entries.stream().noneMatch(e -> e.category() == ViolationCategory.SLUR)) {
            plugin.getLogger().warning("[NexusServerRules] No entries under 'slurs:' in patterns.yml - "
                    + "that category will never trigger until staff populate it. See patterns.yml comments.");
        }
    }

    private void loadSection(YamlConfiguration yaml, String key, ViolationCategory category) {
        List<String> list = yaml.getStringList(key);
        for (String term : list) {
            if (term == null || term.isBlank()) continue;
            entries.add(PatternEntry.of(term.trim(), category));
        }
    }

    public List<PatternEntry> entries() {
        return entries;
    }

    /** Hot-reload support for /nexusrules reload. */
    public void reload() {
        load();
    }
}
