package com.nexusuniverse.realms.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Thin wrapper around the Vault Economy service, used for the chunk-cap upgrade purchases
 * (see LandCommand's /realms upgrade).
 *
 * Deliberately does NOT look up and cache the Economy provider once at startup. Bukkit's
 * softdepend only guarantees a load-order relationship with plugins actually listed in THIS
 * plugin's plugin.yml -- a real Vault-backed economy plugin (e.g. NexusEconomy) typically
 * registers its Economy service inside its OWN onEnable(), gated on the real "Vault" plugin
 * being present. If that other plugin's onEnable() happens to run after this one's -- which
 * Bukkit does not prevent unless it's explicitly named as a (soft)depend here too -- a
 * one-time lookup at this plugin's startup would find nothing and stay permanently "not
 * ready" for the rest of the server's uptime, even though the economy is actually live and
 * working a moment later. Looking the provider up fresh on every call costs nothing
 * (ServicesManager lookups are a simple map read) and makes this immune to load-order
 * ordering entirely, including a provider plugin enabled later via /reload or a plugin
 * manager.
 */
public class VaultHook {

    private final JavaPlugin plugin;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private Economy resolve() {
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);
        return rsp != null ? rsp.getProvider() : null;
    }

    public boolean isReady() {
        return resolve() != null;
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        Economy economy = resolve();
        if (economy == null) return false;
        if (economy.getBalance(player) < amount) return false;
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    public String format(double amount) {
        Economy economy = resolve();
        if (economy != null) {
            return economy.format(amount);
        }
        return String.format("%.2f", amount);
    }
}
