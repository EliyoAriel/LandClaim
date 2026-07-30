package com.landclaim.economy;

import com.landclaim.LandClaimPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyManager {

    private final LandClaimPlugin plugin;
    private Economy economy;

    public EconomyManager(LandClaimPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().info("Vault not found — economy features disabled.");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().warning("No economy provider found.");
            return;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("Hooked into " + economy.getName());
    }

    public boolean hasEconomy() { return economy != null; }

    public boolean hasBalance(Player player, double amount) {
        if (economy == null) return true;
        return economy.has(player, amount);
    }

    public void withdraw(Player player, double amount) {
        if (economy != null) {
            economy.withdrawPlayer(player, amount);
        }
    }

    public void deposit(Player player, double amount) {
        if (economy != null) {
            economy.depositPlayer(player, amount);
        }
    }

    public String format(double amount) {
        if (economy != null) return economy.format(amount);
        return String.format("%.2f", amount);
    }
}
