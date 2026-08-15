package com.nexusuniverse.realms;

import com.nexusuniverse.realms.economy.VaultHook;
import com.nexusuniverse.realms.guidebook.GuidebookCommand;
import com.nexusuniverse.realms.guidebook.GuidebookItem;
import com.nexusuniverse.realms.guidebook.GuidebookListener;
import com.nexusuniverse.realms.guidebook.GuidebookManager;
import com.nexusuniverse.realms.hints.HintManager;
import com.nexusuniverse.realms.home.HomeCommand;
import com.nexusuniverse.realms.home.HomeManager;
import com.nexusuniverse.realms.land.AdminBypassManager;
import com.nexusuniverse.realms.land.BorderNotifyListener;
import com.nexusuniverse.realms.land.LandClaimManager;
import com.nexusuniverse.realms.land.LandCommand;
import com.nexusuniverse.realms.land.PersonalClaimCommand;
import com.nexusuniverse.realms.land.PersonalClaimManager;
import com.nexusuniverse.realms.land.ProtectionListener;
import com.nexusuniverse.realms.protect.BlockChangeListener;
import com.nexusuniverse.realms.protect.InventoryLogListener;
import com.nexusuniverse.realms.protect.ProtectCommand;
import com.nexusuniverse.realms.protect.ProtectionDatabase;
import com.nexusuniverse.realms.protect.RollbackManager;
import com.nexusuniverse.realms.protect.WatchlistManager;
import com.nexusuniverse.realms.team.TeamCommand;
import com.nexusuniverse.realms.team.TeamManager;
import com.nexusuniverse.realms.worldedit.ClipboardManager;
import com.nexusuniverse.realms.worldedit.EditCommand;
import com.nexusuniverse.realms.worldedit.EditExecutor;
import com.nexusuniverse.realms.worldedit.EditHistoryManager;
import com.nexusuniverse.realms.worldedit.EditPermissionChecker;
import com.nexusuniverse.realms.worldedit.SelectionManager;
import com.nexusuniverse.realms.worldedit.WandItemFactory;
import com.nexusuniverse.realms.worldedit.WandListener;
import org.bukkit.plugin.java.JavaPlugin;

public class NexusRealmsPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        RealmsConfig config = new RealmsConfig(this);

        VaultHook economy = new VaultHook(this);
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            // this only checks whether the Vault bridge plugin itself is installed at all --
            // NOT whether an economy has registered with it yet, since that can legitimately
            // still be a few lines of code away in another plugin's own onEnable(). See
            // VaultHook's class doc for why we don't gate anything on a startup-time check.
            getLogger().warning("The Vault plugin was not found. Chunk cap upgrades need an "
                    + "installed Vault-compatible economy plugin (e.g. NexusEconomy) to work.");
        }

        TeamManager teams = new TeamManager(this, config);
        LandClaimManager land = new LandClaimManager(this);
        PersonalClaimManager personalClaims = new PersonalClaimManager(this, land);
        HomeManager homes = new HomeManager(this);
        HintManager hints = new HintManager(this);
        this.hintManager = hints;
        hints.start();

        AdminBypassManager bypass = new AdminBypassManager();
        getServer().getPluginManager().registerEvents(new ProtectionListener(teams, land, personalClaims, config, bypass), this);
        getServer().getPluginManager().registerEvents(new BorderNotifyListener(teams, land, config), this);

        getCommand("team").setExecutor(new TeamCommand(teams, hints));
        getCommand("realms").setExecutor(new LandCommand(teams, land, config, hints, economy, bypass));
        getCommand("pclaim").setExecutor(new PersonalClaimCommand(teams, personalClaims, config, hints));

        HomeCommand homeCommand = new HomeCommand(homes, config, hints);
        getCommand("sethome").setExecutor(homeCommand);
        getCommand("home").setExecutor(homeCommand);
        getCommand("delhome").setExecutor(homeCommand);
        getCommand("homes").setExecutor(homeCommand);

        this.protectionDatabase = new ProtectionDatabase(this);
        protectionDatabase.start();
        WatchlistManager watchlist = new WatchlistManager(this);
        RollbackManager rollbackManager = new RollbackManager(this, protectionDatabase);
        getServer().getPluginManager().registerEvents(new BlockChangeListener(protectionDatabase), this);
        getServer().getPluginManager().registerEvents(new InventoryLogListener(protectionDatabase, watchlist), this);
        getCommand("protect").setExecutor(new ProtectCommand(rollbackManager, watchlist));

        SelectionManager selections = new SelectionManager();
        EditPermissionChecker editPermissions = new EditPermissionChecker(personalClaims);
        EditHistoryManager editHistory = new EditHistoryManager(config.worldEditUndoDepth());
        EditExecutor editExecutor = new EditExecutor(editHistory);
        WandItemFactory wandItems = new WandItemFactory(this);
        ClipboardManager clipboards = new ClipboardManager();
        getServer().getPluginManager().registerEvents(new WandListener(selections, config, wandItems), this);
        getCommand("redit").setExecutor(new EditCommand(selections, editPermissions, editExecutor, config, wandItems, clipboards));

        GuidebookManager guidebookManager = new GuidebookManager(this);
        GuidebookItem guidebookItem = new GuidebookItem();
        getServer().getPluginManager().registerEvents(new GuidebookListener(guidebookManager, guidebookItem), this);
        getCommand("nexusguide").setExecutor(new GuidebookCommand(guidebookItem));

        getLogger().info("NexusRealms enabled -- homes: " + config.homesMaxDefault() + " default / "
                + config.homesMaxAdmin() + " admin. Personal claims: radius " + config.personalClaimRadius()
                + ", " + config.personalClaimMaxPerMember() + " per member. Protection/rollback log is live.");
    }

    @Override
    public void onDisable() {
        if (protectionDatabase != null) protectionDatabase.stop();
        if (hintManager != null) hintManager.stop();
    }

    private ProtectionDatabase protectionDatabase;
    private HintManager hintManager;
}
