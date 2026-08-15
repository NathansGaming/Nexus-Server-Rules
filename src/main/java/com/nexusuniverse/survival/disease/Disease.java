package com.nexusuniverse.survival.disease;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Set;

/**
 * Each disease has a display name, a short flavor description, a set of
 * symptom effects re-applied periodically while infected, and now also:
 *
 *  - a contagion radius/chance pair (how close, how likely, per check --
 *    tuned per disease instead of one global number, so something like
 *    Breath Fever can spread further than an ordinary cough)
 *  - optional source blocks/biomes/mobs: real in-world triggers that can
 *    infect a healthy player on contact, independent of contagion. A
 *    disease can have any combination of these, or none at all (in which
 *    case it only ever spreads person-to-person, or via the raw-water
 *    contamination roll).
 *
 * Cures are matched to these by name in DiseaseItems / DiseaseManager (one
 * dedicated cure item per disease, no cross-curing).
 */
public enum Disease {

    RATTLING_COUGH(
            "Rattling Cough",
            "§7A wet, rattling cough. Mild but persistent.",
            List.of(
                    new PotionEffect(PotionEffectType.NAUSEA, 100, 0, true, false),
                    new PotionEffect(PotionEffectType.WEAKNESS, 100, 0, true, false)
            )
    ),
    FEVER_ROT(
            "Fever Rot",
            "§7Burns through food reserves and slows the hands. Common among the undead-touched.",
            List.of(
                    new PotionEffect(PotionEffectType.HUNGER, 100, 1, true, false),
                    new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 0, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(), biomes(), mobs(EntityType.ZOMBIE, EntityType.HUSK, EntityType.DROWNED)
    ),
    GLOWSICKNESS(
            "Glowsickness",
            "§7Radiation exposure gone wrong -- you glow, and everyone can see it.",
            List.of(
                    new PotionEffect(PotionEffectType.GLOWING, 100, 0, true, false),
                    new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(Material.GLOWSTONE, Material.SEA_PICKLE, Material.GLOW_LICHEN), biomes(), mobs()
    ),
    BONE_CHILL(
            "Bone Chill",
            "§7A deep cold that won't leave. Slows and disorients.",
            List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, true, false),
                    new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(), biomes(Biome.SNOWY_PLAINS, Biome.SNOWY_TAIGA, Biome.ICE_SPIKES,
                    Biome.FROZEN_OCEAN, Biome.FROZEN_RIVER, Biome.FROZEN_PEAKS, Biome.SNOWY_SLOPES), mobs()
    ),
    SWAMP_FEVER(
            "Swamp Fever",
            "§7A muddy, feverish illness caught from stagnant water.",
            List.of(
                    new PotionEffect(PotionEffectType.HUNGER, 100, 0, true, false),
                    new PotionEffect(PotionEffectType.NAUSEA, 100, 0, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(), biomes(Biome.SWAMP, Biome.MANGROVE_SWAMP), mobs()
    ),
    IRON_LUNG(
            "Iron Lung",
            "§7Dust and grit settled deep in the lungs from too much time underground.",
            List.of(
                    new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 1, true, false),
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 0, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(), biomes(Biome.DRIPSTONE_CAVES, Biome.LUSH_CAVES, Biome.DEEP_DARK), mobs()
    ),
    STATIC_SHOCK(
            "Static Shock",
            "§7Lingering electrical burns that won't stop twitching.",
            List.of(
                    new PotionEffect(PotionEffectType.NAUSEA, 100, 1, true, false),
                    new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(Material.REDSTONE_BLOCK, Material.REDSTONE_WIRE, Material.REDSTONE_TORCH, Material.LIGHTNING_ROD),
            biomes(), mobs()
    ),
    WITHERING_PLAGUE(
            "Withering Plague",
            "§7A severe, wasting sickness -- the worst thing you can catch out here.",
            List.of(
                    new PotionEffect(PotionEffectType.WITHER, 100, 0, true, false),
                    new PotionEffect(PotionEffectType.WEAKNESS, 100, 1, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(), biomes(), mobs(EntityType.WITHER_SKELETON, EntityType.WITHER)
    ),
    BREATH_FEVER(
            "Breath Fever",
            "§7Spreads fast in close quarters. Keep your distance -- six blocks, they say.",
            List.of(
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 0, true, false),
                    new PotionEffect(PotionEffectType.HUNGER, 100, 0, true, false)
            ),
            6.0, 0.16,
            blocks(), biomes(), mobs()
    ),
    BUSH_HAZE(
            "Bush Haze",
            "§7A dizzying reaction from disturbing the wrong bush.",
            List.of(
                    new PotionEffect(PotionEffectType.NAUSEA, 100, 1, true, false),
                    new PotionEffect(PotionEffectType.SLOWNESS, 80, 0, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(Material.SWEET_BERRY_BUSH), biomes(), mobs()
    ),
    NECROTIC_BLIGHT(
            "Necrotic Blight",
            "§7Something in the wither rose has taken root in you.",
            List.of(
                    new PotionEffect(PotionEffectType.WITHER, 100, 0, true, false),
                    new PotionEffect(PotionEffectType.POISON, 100, 0, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(Material.WITHER_ROSE), biomes(), mobs()
    ),
    DEEP_ROT(
            "Deep Rot",
            "§7The deep dark left something behind. It doesn't want to leave.",
            List.of(
                    new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false),
                    new PotionEffect(PotionEffectType.SLOWNESS, 100, 1, true, false)
            ),
            defaultRadius(), defaultChance(),
            blocks(Material.SCULK, Material.SCULK_CATALYST, Material.SCULK_SHRIEKER), biomes(), mobs()
    );

    private static double defaultRadius() {
        return 3.0;
    }

    private static double defaultChance() {
        return 0.12;
    }

    private final String displayName;
    private final String description;
    private final List<PotionEffect> symptoms;
    private final double contagionRadius;
    private final double contagionChance;
    private final Set<Material> sourceBlocks;
    private final Set<Biome> sourceBiomes;
    private final Set<EntityType> sourceMobs;

    /** Contagion-only disease, no environmental source -- uses the default radius/chance. */
    Disease(String displayName, String description, List<PotionEffect> symptoms) {
        this(displayName, description, symptoms, defaultRadius(), defaultChance(),
                Set.of(), Set.of(), Set.of());
    }

    Disease(String displayName, String description, List<PotionEffect> symptoms,
            double contagionRadius, double contagionChance,
            Set<Material> sourceBlocks, Set<Biome> sourceBiomes, Set<EntityType> sourceMobs) {
        this.displayName = displayName;
        this.description = description;
        this.symptoms = symptoms;
        this.contagionRadius = contagionRadius;
        this.contagionChance = contagionChance;
        this.sourceBlocks = sourceBlocks;
        this.sourceBiomes = sourceBiomes;
        this.sourceMobs = sourceMobs;
    }

    private static Set<Material> blocks(Material... materials) {
        return Set.of(materials);
    }

    private static Set<Biome> biomes(Biome... values) {
        return Set.of(values);
    }

    private static Set<EntityType> mobs(EntityType... types) {
        return Set.of(types);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public List<PotionEffect> getSymptoms() {
        return symptoms;
    }

    public double contagionRadius() {
        return contagionRadius;
    }

    public double contagionChance() {
        return contagionChance;
    }

    public Set<Material> sourceBlocks() {
        return sourceBlocks;
    }

    public Set<Biome> sourceBiomes() {
        return sourceBiomes;
    }

    public Set<EntityType> sourceMobs() {
        return sourceMobs;
    }
}
