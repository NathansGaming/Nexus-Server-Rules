package com.nexusuniverse.realms.guidebook;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Content lives as a list of short SECTIONS (one per topic), each broken into its own list of
 * short paragraphs -- BookPaginator handles fitting those onto real pages without cutting
 * anything off, splitting a section across multiple pages if it needs to rather than truncating.
 * Keep each paragraph here short and topic-focused; BookPaginator does the actual wrapping, but
 * a paragraph that tries to cram an entire feature into one dense block is still harder to read
 * than several short ones, even once it's correctly wrapped.
 */
public class GuidebookItem {

    public ItemStack create() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.setTitle("The Nexus Realms Handbook");
        meta.setAuthor("Nexus Universe");

        List<List<String>> sections = List.of(
                List.of(
                        "Welcome to Nexus Realms.",
                        "This book covers every command you have access to. Keep it -- you won't be given another automatically."
                ),
                List.of(
                        "== Countries ==",
                        "/team create <name> founds a country. You're its leader.",
                        "/team invite <player> and /team kick <player> need at least officer rank.",
                        "/team promote, /team demote, and /team transfer are leader-only.",
                        "/team settings doors|containers|pvp on|off controls what outsiders can do on your land."
                ),
                List.of(
                        "== Claiming Land ==",
                        "/realms claim (officer+) claims the chunk you're standing in for your country.",
                        "/realms unclaim releases it. /realms info shows what you own."
                ),
                List.of(
                        "== Personal Plots ==",
                        "Any current member of a country can stake a personal plot inside its territory with /pclaim create [label].",
                        "/pclaim trust <player> visitor|builder lets someone specific into just that plot -- they don't need to be on your team at all.",
                        "/pclaim untrust <player> revokes it. /pclaim list shows your plots."
                ),
                List.of(
                        "== Homes ==",
                        "/sethome [name] saves a home. /home [name] warps to it.",
                        "/delhome [name] removes one. /homes lists them all.",
                        "Your limit depends on your permissions -- ask an admin if you need more."
                ),
                List.of(
                        "== Region Editing ==",
                        "/redit wand gives you a WorldEdit Wand (a named, glowing item -- easy to spot in your hotbar).",
                        "Left-click (attack) a block: sets position 1. Right-click (use) a block: sets position 2.",
                        "Controller: your attack/break button sets position 1, your use/place button sets position 2 -- same as breaking and placing a block normally.",
                        "/redit set, replace, walls, and outline all need a material name typed in chat, e.g. /redit set stone.",
                        "/redit undo reverts your last edit.",
                        "Regular players can only edit inside their own personal plots -- the whole selection has to fit inside one."
                ),
                List.of(
                        "That's everything. Good luck out there."
                )
        );

        List<String> allParagraphs = new ArrayList<>();
        for (List<String> section : sections) {
            allParagraphs.addAll(section);
        }

        meta.setPages(BookPaginator.paginate(allParagraphs));
        book.setItemMeta(meta);
        return book;
    }
}
