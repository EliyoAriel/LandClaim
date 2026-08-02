package com.landclaim;

import com.landclaim.boundary.BoundaryManager;
import com.landclaim.commands.ClaimAdminCommand;
import com.landclaim.commands.ClaimCommand;
import com.landclaim.config.ConfigManager;
import com.landclaim.api.LandClaimAPI;
import com.landclaim.data.ClaimRepository;
import com.landclaim.data.DatabaseManager;
import com.landclaim.data.TaxManager;
import com.landclaim.economy.EconomyManager;
import com.landclaim.gui.GuiInputHandler;
import com.landclaim.gui.GuiManager;
import com.landclaim.integration.RewindHook;
import com.landclaim.listeners.EntityProtectionListener;
import com.landclaim.listeners.EnvironmentProtectionListener;
import com.landclaim.listeners.GreetingListener;
import com.landclaim.listeners.PlayerProtectionListener;
import com.landclaim.listeners.PluginEnableListener;
import com.landclaim.protection.ClaimAccess;
import com.landclaim.service.ClaimService;
import org.bukkit.plugin.java.JavaPlugin;

public class LandClaimPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private ClaimRepository claimRepository;
    private EconomyManager economyManager;
    private TaxManager taxManager;
    private ClaimAccess claimAccess;
    private ClaimService claimService;
    private GuiManager guiManager;
    private BoundaryManager boundaryManager;
    private ClaimCommand claimCommand;
    private ClaimAdminCommand claimAdminCommand;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.databaseManager = new DatabaseManager(this);
        this.claimRepository = new ClaimRepository(databaseManager, configManager);
        this.economyManager = new EconomyManager(this);
        this.taxManager = new TaxManager(this, claimRepository);
        this.claimAccess = new ClaimAccess(this, claimRepository, configManager);
        this.claimService = new ClaimService(this, claimRepository, configManager, economyManager, taxManager, claimAccess);
        this.guiManager = new GuiManager(this);
        this.boundaryManager = new BoundaryManager(this);
        this.claimCommand = new ClaimCommand(this, claimRepository, economyManager, configManager, claimAccess, claimService);
        this.claimAdminCommand = new ClaimAdminCommand(this, claimRepository, configManager, claimAccess);

        databaseManager.initialize();
        claimRepository.loadAllClaims();
        economyManager.setup();

        LandClaimAPI.init(this);
        RewindHook.init();
        if (RewindHook.isEnabled()) {
            RewindHook.rebuild(claimRepository.getAllClaims());
        }

        var claimCmd = getCommand("claim");
        if (claimCmd != null) {
            claimCmd.setExecutor(claimCommand);
            claimCmd.setTabCompleter(claimCommand);
        }

        getServer().getPluginManager().registerEvents(new PlayerProtectionListener(claimAccess), this);
        getServer().getPluginManager().registerEvents(new EntityProtectionListener(claimAccess), this);
        getServer().getPluginManager().registerEvents(new EnvironmentProtectionListener(claimAccess), this);
        getServer().getPluginManager().registerEvents(new GreetingListener(this, claimRepository, configManager), this);
        getServer().getPluginManager().registerEvents(guiManager, this);
        getServer().getPluginManager().registerEvents(new GuiInputHandler(guiManager), this);
        getServer().getPluginManager().registerEvents(boundaryManager, this);
        getServer().getPluginManager().registerEvents(new PluginEnableListener(), this);

        boundaryManager.start();
        taxManager.scheduleTaxCheck();

        getLogger().info("LandClaim enabled.");
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.shutdown();
        }
        if (boundaryManager != null) {
            boundaryManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
    }

    public ConfigManager getConfigManager() { return configManager; }
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ClaimRepository getClaimRepository() { return claimRepository; }
    public EconomyManager getEconomyManager() { return economyManager; }
    public TaxManager getTaxManager() { return taxManager; }
    public ClaimAccess getClaimAccess() { return claimAccess; }
    public ClaimService getClaimService() { return claimService; }
    public GuiManager getGuiManager() { return guiManager; }
    public BoundaryManager getBoundaryManager() { return boundaryManager; }
    public ClaimCommand getClaimCommand() { return claimCommand; }
    public ClaimAdminCommand getClaimAdminCommand() { return claimAdminCommand; }
}
