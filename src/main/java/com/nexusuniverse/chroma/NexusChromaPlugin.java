package com.nexusuniverse.chroma;

import com.nexusuniverse.chroma.color.ColorTable;
import com.nexusuniverse.chroma.command.NexusChromaCommand;
import com.nexusuniverse.chroma.item.ChromaItemFactory;
import com.nexusuniverse.chroma.listener.FrameBreakListener;
import com.nexusuniverse.chroma.listener.FrameListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusChromaPlugin extends JavaPlugin {

    private ColorTable colorTable;

    @Override
    public void onEnable() {
        colorTable = new ColorTable(this);
        colorTable.load();

        ChromaItemFactory itemFactory = new ChromaItemFactory(this);

        getServer().getPluginManager().registerEvents(new FrameListener(colorTable, itemFactory), this);
        getServer().getPluginManager().registerEvents(new FrameBreakListener(itemFactory), this);

        PluginCommand command = getCommand("nexuschroma");
        if (command != null) {
            command.setExecutor(new NexusChromaCommand(colorTable));
        }

        getLogger().info("NexusChroma enabled -- " + colorTable.all().size() + " block color(s) ready.");
    }

    @Override
    public void onDisable() {
        getLogger().info("NexusChroma disabled.");
    }
}
