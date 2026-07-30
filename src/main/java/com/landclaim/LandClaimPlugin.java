package com.landclaim;

import com.landclaim.commands.ClaimAdminCommand;
import com.landclaim.commands.ClaimCommand;
import com.landclaim.config.ConfigManager;
import com.landclaim.data.ClaimRepository;
import com.landclaim.data.DatabaseManager;
import com.landclaim.data.TaxManager;
import com.landclaim.economy.EconomyManager;
import com.landclaim.listeners.ProtectionListener;
import org.bukkit.plugin.java.JavaPlugin;

public class LandClaimPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ClaimRepository claimRepository;
    private EconomyManager economyManager;
    private TaxManager taxManager;
    private ClaimCommand claimCommand;
    private ClaimAdminCommand claimAdminCommand;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.claimRepository = new ClaimRepository(databaseManager);
        this.economyManager = new EconomyManager(this);
        this.taxManager = new TaxManager(this, claimRepository);
        this.claimCommand = new ClaimCommand(this, claimRepository, economyManager, taxManager, configManager);
        this.claimAdminCommand = new ClaimAdminCommand(this, claimRepository, configManager);

        databaseManager.initialize();
        claimRepository.loadAllClaims();
        economyManager.setup();

        var claimCmd = getCommand("claim");
        if (claimCmd != null) {
            claimCmd.setExecutor(claimCommand);
            claimCmd.setTabCompleter(claimCommand);
        }

        getServer().getPluginManager().registerEvents(new ProtectionListener(this, claimRepository, configManager), this);

        taxManager.scheduleTaxCheck();

        getLogger().info("LandClaim enabled.");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ClaimRepository getClaimRepository() { return claimRepository; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public TaxManager getTaxManager() { return taxManager; }
    public ClaimCommand getClaimCommand() { return claimCommand; }
    public ClaimAdminCommand getClaimAdminCommand() { return claimAdminCommand; }
}
